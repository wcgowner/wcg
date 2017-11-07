// #leopard#

package wcg;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import wcg.crypto.Crypto;
import wcg.db.DbIterator;
import wcg.util.Convert;
import wcg.util.Listeners;
import wcg.util.Logger;

public class InterestManager {
  
  public static class AccountRecord {
		public long			account_id;
    public int			payment_number;
		public int			begin;
    public int			end;
    public boolean	next;
		public long			balance;
  }
	
	public static class PaymentRecord {
    public int	id;
    public int	height;
		public long transaction_id;
		public int	transaction_height;
		public long amount;
		public int  accounts_number;
  }
  
	public static class Balance {
		public int	height;
		public long amount;
	}
  
	private static final Listeners<Account,Account.Event> listeners = new Listeners<>();
  
  public static boolean isUpdating = false;
  
  public static List<String> excludedAccounts;
  
  public static String excludedAccountsSQL;
  
  public static int period;

  public static long minimunThreshold;
  
  private static String forgerSecretPhrase = "";
  
  private static Connection connection;
  
  private static String payerAccountRS = "WCG-Z9B5-PJAW-8LD5-67HDJ"; 
  
  private static Account payerAccount;
	
	public static double interestPercentage = 0.03; 
	
	public static long CalculateAverageBalance(long accountId, int begin, int end) throws SQLException {
		
		try (PreparedStatement pstmt = connection.prepareStatement( "SELECT height, balance FROM interest_balance WHERE account_id=? AND height>=? AND height<? ORDER BY height ASC")) {
			int i = 0;
      pstmt.setLong(++i, accountId);
      pstmt.setInt(++i, begin);
			pstmt.setInt(++i, end);
      
			try (ResultSet rs = pstmt.executeQuery()) {
				List<Balance> balances = new ArrayList<Balance>();
				
        while (rs.next()) {
					Balance balance = new Balance();
					
					balance.height = rs.getInt("height");
          balance.amount = rs.getLong("balance");

          if (rs.isFirst()) {
						if (balance.height!=begin) {
							Balance firstBalance = new Balance();
							
							firstBalance.amount = InterestManager.GetLastBalance(accountId, begin);
							firstBalance.height = begin;
							
							balances.add(firstBalance);
						}
					}
					
					balances.add(balance);
        }
				
				if (balances.isEmpty()) {
					return 0;
				}
				
				if (balances.size()==1) {
					return balances.get(0).amount;
				}
				
				BigInteger sum = BigInteger.valueOf(0);
				for (int index=0; index<balances.size(); index++) {
					int h2;
					if (index==balances.size()-1) {
						h2 = end;
					}
					else {
						h2 = balances.get(index+1).height;
					}
					
					Balance balance_1 = balances.get(index);
					
					sum = sum.add(BigInteger.valueOf(balance_1.amount).multiply(BigInteger.valueOf(h2-balance_1.height)));
				}
				
				return sum.divide(BigInteger.valueOf(end-begin)).longValue();
			}
		}
	}
	
	public static void CreatePayment(int height) throws SQLException {
		if (height<=0) {
			return;
		}
		
		List<AccountRecord> accounts = InterestManager.GetPaymentAccounts(height);
						
		if (accounts.isEmpty()) {
			return;
		}

		// insert payment
		int paymentId = InterestManager.InsertPayment(Wcg.getBlockchain().getHeight(), Wcg.getEpochTime());
		
		long amount = 0;
		
		for (int index=0; index<accounts.size(); index++) {
			try {
				AccountRecord account = accounts.get(index);
				
				Long averageBalance = InterestManager.CalculateAverageBalance(account.account_id, account.begin, account.end);
				
				InterestManager.UpdateAccount(account.account_id, account.begin, account.end, paymentId, averageBalance);
				
				amount += averageBalance;
			}
			catch (SQLException e) {
				Logger.logInfoMessage("exception " + e.toString());
			}
		}
		
		InterestManager.UpdatePayment(paymentId, amount, accounts.size());
	}
  
	public static void CreateTransaction () {
		PaymentRecord payment = null;
		
		try {
			payment = InterestManager.GetNextPaymentRecord();
			payment.amount = BigDecimal.valueOf(payment.amount).multiply(BigDecimal.valueOf(InterestManager.interestPercentage)).longValue();
		}
		catch (Exception e) {
			Logger.logInfoMessage("Exception " + e);
		}
		
		Attachment.InterestPayment attachment = new Attachment.InterestPayment(payment.height, payment.accounts_number, payment.amount, payment.id);
		
		try {
			Transaction.Builder builder = Wcg.newTransactionBuilder(Crypto.getPublicKey(InterestManager.GetForgerSecretPhrase()), 0, Constants.ONE_WCG, (short)72, attachment);
			builder.timestamp(Wcg.getBlockchain().getLastBlockTimestamp());
			Transaction transaction = builder.build(InterestManager.GetForgerSecretPhrase());
			TransactionProcessorImpl.getInstance().broadcast(transaction);
		} 
		catch (WcgException.ValidationException e) {
			Logger.logErrorMessage("Fatal error submitting interest payment transaction", e);
		}
	}
	
	public static void DeleteAccount(Connection connection, long account_id, int payment_number) throws SQLException {
    try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_account WHERE account_id=? AND payment_number=?")) {
      int i = 0;
      pstmt.setLong(++i, account_id);
      pstmt.setInt(++i, payment_number);
      pstmt.executeUpdate();
    }catch (SQLException e) {
      throw new RuntimeException(e.toString(), e);
    }
  }
	
	public static AccountRecord GetAccountRecord(Connection connection, long account_id, int height) throws SQLException {
    try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE account_id=? AND begin<=? AND end>=?");) {
      pstmtSelect.setLong(1, account_id);
      pstmtSelect.setInt(2, height);
      pstmtSelect.setInt(3, height);

       try (ResultSet rs = pstmtSelect.executeQuery()) {
        if (rs.next()) {
          AccountRecord account = new AccountRecord();
          account.payment_number = rs.getInt("payment_number");
          account.end = rs.getInt("end");
          account.next = rs.getBoolean("next");
          
          return account;
        }
        return null;
      }
    }
  }
	
	public static List<AccountRecord> GetAccounts(int payment_id) throws SQLException {
    try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT account_id, balance FROM interest_account WHERE payment_id=?");) {
      pstmtSelect.setInt(1, payment_id);

       try (ResultSet rs = pstmtSelect.executeQuery()) {
        List<AccountRecord> accounts = new ArrayList<>();
					
				while (rs.next()) {
					AccountRecord account = new AccountRecord();

					account.account_id = rs.getLong("account_id");
					account.balance = rs.getLong("balance");

					accounts.add(account);
				}
				
				return accounts;
      }
    }
  }
	
  public static long GetLastBalance(Connection connection, long account_id) throws SQLException {
    try (
      PreparedStatement pstmtSelect = connection.prepareStatement("SELECT balance FROM interest_balance WHERE account_id=? ORDER BY height DESC LIMIT 1");) {
        pstmtSelect.setLong(1, account_id);

        try (ResultSet rs = pstmtSelect.executeQuery()) {
          if (rs.next()) {
            return rs.getLong("balance");
          }
          return 0;
        }
    }
  }
	
	public static long GetLastBalance(long account_id, int height) throws SQLException {
    try (
      PreparedStatement pstmtSelect = connection.prepareStatement("SELECT balance FROM interest_balance WHERE account_id=? AND height<=? ORDER BY height DESC LIMIT 1");) {
        pstmtSelect.setLong(1, account_id);
				pstmtSelect.setLong(2, height);

        try (ResultSet rs = pstmtSelect.executeQuery()) {
          if (rs.next()) {
            return rs.getLong("balance");
          }
          return 0;
        }
    }
  }
  
  public static String GetForgerSecretPhrase() {
    return forgerSecretPhrase;
  }
  
	public static AccountRecord GetLastAccountRecord(Connection connection, long account_id) throws SQLException {
    try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE account_id=? ORDER BY payment_number DESC LIMIT 1");) {
      pstmtSelect.setLong(1, account_id);

      try (ResultSet rs = pstmtSelect.executeQuery()) {
        if (rs.next()) {
          AccountRecord account = new AccountRecord();
          account.payment_number = rs.getInt("payment_number");
          account.end = rs.getInt("end");
          account.next = rs.getBoolean("next");
          
          return account;
        }
        return null;
      }
    }
  }
  
	public static PaymentRecord GetLastPaymentRecord() throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_payment ORDER BY id DESC LIMIT 1");) {

      try (ResultSet rs = pstmtSelect.executeQuery()) {
        if (rs.next()) {
          PaymentRecord payment = new PaymentRecord();
          payment.id = rs.getInt("id");
          payment.height = rs.getInt("height");
          payment.transaction_id = rs.getLong("transaction_id");
					payment.transaction_height = rs.getInt("transaction_height");
          
          return payment;
        }
        return null;
      }
    }
	}
	
	public static PaymentRecord GetNextPaymentRecord() throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_payment WHERE transaction_id IS NULL ORDER BY id ASC LIMIT 1");) {

      try (ResultSet rs = pstmtSelect.executeQuery()) {
        if (rs.next()) {
          PaymentRecord payment = new PaymentRecord();
          payment.id = rs.getInt("id");
					payment.amount = rs.getLong("amount");
					payment.accounts_number = rs.getInt("accounts_number");
          payment.height = rs.getInt("height");
          payment.transaction_id = rs.getLong("transaction_id");
					payment.transaction_height = rs.getInt("transaction_height");
          
          return payment;
        }
        return null;
      }
    }
	}
	
  public static Account GetPayerAccount() {
    return payerAccount;
  }
  
	public static List<AccountRecord> GetPaymentAccounts(int height) throws SQLException {
		try (
      PreparedStatement pstmtSelect = connection.prepareStatement("SELECT account_id, begin, end FROM interest_account"
																																	+ " WHERE end<?"
																																	+ "  AND payment_id IS NULL");) {
        pstmtSelect.setInt(1, height);

        try (ResultSet rs = pstmtSelect.executeQuery()) {
					List<AccountRecord> accounts = new ArrayList<>();
					
          while (rs.next()) {
						AccountRecord account = new AccountRecord();
						
						account.account_id = rs.getLong("account_id");
						account.begin = rs.getInt("begin");
						account.end = rs.getInt("end");
					
						accounts.add(account);
          }
          return accounts;
        }
    }
	}
  
	public static long GetTransactionGeneratorId(Connection connection, long transaction_id) throws SQLException {
    try (
      PreparedStatement pstmtSelect = connection.prepareStatement("SELECT generator_id FROM transaction t, block b WHERE t.id=? AND b.id=t.block_id");) {
        pstmtSelect.setLong(1, transaction_id);

        try (ResultSet rs = pstmtSelect.executeQuery()) {
          if (rs.next()) {
            return rs.getLong("generator_id");
          }
          return 0;
        }
    }
  }
  
  public static void Init() throws SQLException {
    try {
      connection = Db.db.getConnection();
    }
    catch (SQLException e) {
      Logger.logInfoMessage("exception " + e);
    }
     
    period = Wcg.getIntProperty("wcg.interestPeriod", 172800);

    minimunThreshold = Wcg.getIntProperty("wcg.interestMinimunThreshold", 100);
    
    excludedAccounts = Wcg.getStringListProperty("wcg.interestExcludedAccounts", "-9128296992220251419;5977473349849294368;-1246335457790138311;7359284269271563671;7971533403673683675;2103030737530415949;-743446844806740485;-2881129103809383820;-6475845696648806297;-2461721788666564439;3690340317253181336;-7425202993905651001;3571454402745187992;5947863505463986435;-749187646861774489;4468076176986210766;3601447615317990179;7946451654626588351;3656291099097719233;9094040562910503463;-7211220690307824610;8457522211461559677;4296213944053612947;-677881220224165773;-8312935186537134532;3791148784704871638;4533367924778183250;1609802962710175117;8958192434388600228;-7633406546024938862;4868720552757198380;-4098980433027046045;5664781255241704056;2351500103851672483;9078704076565620152;1801406999511562855;5201065671878745379;-8313395224807156625");
    
    excludedAccountsSQL = String.join(",", excludedAccounts);
    
    excludedAccounts = Wcg.getStringListProperty("wcg.interestExcludedAccounts");
    
    payerAccount = Account.getAccount(Convert.parseAccountId(payerAccountRS));
    
    Account.addListener(account -> {
        try {
          InterestManager.MergeBalance(connection, account.getId(), Wcg.getBlockchain().getHeight(), account.getBalanceNQT(), Wcg.getEpochTime());
        }
        catch (SQLException e) {
          Logger.logInfoMessage("exception " + e);
        }
      }, 
      Account.Event.BALANCE);
    
    Wcg.getBlockchainProcessor().addListener(block -> {
        try {
          InterestManager.UpdateTableAccount(connection, Wcg.getBlockchain().getHeight());
        }
        catch (SQLException e) {
          Logger.logInfoMessage("exception " + e);
        }
      }, 
      BlockchainProcessor.Event.BLOCK_PUSHED);

    Wcg.getBlockchainProcessor().addListener(block -> {
				if (block.getHeight()%720==0) {
					try {
						InterestManager.CreatePayment(block.getHeight()-720);
					}
					catch (SQLException e) {
						Logger.logInfoMessage("exception " + e);
					}
				}
				
				// create interests transaction
				if (block.getHeight()>228000 && !InterestManager.GetForgerSecretPhrase().isEmpty() && !Wcg.getBlockchainProcessor().isDownloading() && block.getHeight()%144==10) {
					InterestManager.CreateTransaction();
				}
      },
      BlockchainProcessor.Event.BLOCK_PUSHED);
		
		Wcg.getBlockchainProcessor().addListener(block -> {
				try {
					InterestManager.ResetPaymentTransaction(block.getHeight());
				}
				catch (SQLException e) {
					Logger.logInfoMessage("exception " + e);
				}
      },
      BlockchainProcessor.Event.BLOCK_POPPED);
  }
	
	public static boolean IsInPeriod(Connection connection, long account_id, int height) throws SQLException {
    try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT payment_number FROM interest_account WHERE account_id=? AND begin<=? AND end>=?");) {
        pstmtSelect.setLong(1, account_id);
        pstmtSelect.setInt(2, height);
        pstmtSelect.setInt(3, height);

        try (ResultSet rs = pstmtSelect.executeQuery()) {
          if (rs.next()) {
            return true;
          }
          return false;
        }
    }
  }
  
  public static void MergeBalance(Connection connection, long account_id, int height, long balance, int timestamp) throws SQLException {
    try (PreparedStatement pstmt = connection.prepareStatement( "MERGE INTO interest_balance" +
                                                                "  (account_id, height, balance, timestamp)" +
                                                                " VALUES" +
                                                                " (?, ?, ?, ?)")) {
      int i = 0;
      pstmt.setLong(++i, account_id);  
      pstmt.setInt(++i, height);
      pstmt.setLong(++i, balance);
      pstmt.setInt(++i, timestamp);
      pstmt.executeUpdate();
    }
  }
  
  public static void InsertAccount(Connection connection, long account_id, int payment_number, int begin, int end, int timestamp) throws SQLException {
    try (PreparedStatement pstmt = connection.prepareStatement( "INSERT INTO interest_account" +
                                                                "  (account_id, payment_number, begin, end, timestamp)" +
                                                                " VALUES" +
                                                                " (?, ?, ?, ?, ?)")) {
      int i = 0;
      pstmt.setLong(++i, account_id);  
      pstmt.setLong(++i, payment_number);
      pstmt.setInt(++i, begin);
      pstmt.setLong(++i, end);
      pstmt.setInt(++i, timestamp);
      pstmt.executeUpdate();
    }
  }
	
	public static int InsertPayment(int height, int timestamp) throws SQLException {
    try (PreparedStatement pstmt = connection.prepareStatement( "INSERT INTO interest_payment" +
                                                                "  (height, timestamp)" +
                                                                " VALUES" +
                                                                " (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
      int i = 0;
      pstmt.setInt(++i, height);
      pstmt.setInt(++i, timestamp);
      pstmt.executeUpdate();

			try (ResultSet rs = pstmt.getGeneratedKeys()) {
					if (rs.next()) {
							return rs.getInt(1);
					}
					
					return 0;
			}
    }
  }
  
	public static void ResetPaymentTransaction(int height)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_payment SET transaction_id=NULL, transaction_height=NULL" +
																																		"  WHERE transaction_height=?")) {
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.executeUpdate();
		}		
	}
	
  public static void SetAccountNext(Connection connection, long account_id, int payment_number, boolean next) throws SQLException {
     try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET next=?" +
                                                                "  WHERE account_id=? AND payment_number=?")) {
      int i = 0;
      pstmt.setBoolean(++i, next);
      pstmt.setLong(++i, account_id);
      pstmt.setInt(++i, payment_number);
      pstmt.executeUpdate();
    }
  }
	
	public static void SetForgerSecretPhrase(String secretPhrase) {
    forgerSecretPhrase = secretPhrase;
  }
  
	public static void UpdateAccount(long accountId, int begin, int end, int paymentId, long averageBalance)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET payment_id=?, balance=?" +
																																		"  WHERE account_id=? AND begin=? AND end=?")) {
			int i = 0;
			pstmt.setInt(++i, paymentId);
			pstmt.setLong(++i, averageBalance);
			pstmt.setLong(++i, accountId);
			pstmt.setInt(++i, begin);
			pstmt.setInt(++i, end);
			pstmt.executeUpdate();
		}		
	}
	
	public static void UpdatePayment(int paymentId, long amount, int accountsNumber)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_payment SET amount=?, accounts_number=?" +
																																		"  WHERE id=?")) {
			int i = 0;
			pstmt.setLong(++i, amount);
			pstmt.setInt(++i, accountsNumber);
			pstmt.setInt(++i, paymentId);
			pstmt.executeUpdate();
		}		
	}
	
	
					
	public static void UpdatePaymentTransaction(int paymentId, long transactionId, int transactionHeight)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_payment SET transaction_id=?, transaction_height=?" +
																																		"  WHERE id=?")) {
			int i = 0;
			pstmt.setLong(++i, transactionId);
			pstmt.setInt(++i, transactionHeight);
			pstmt.setInt(++i, paymentId);
			pstmt.executeUpdate();
		}		
	}
	
  public static void UpdateTableAccount(Connection connection, int height) throws SQLException {
    String sql = "SELECT * FROM interest_balance WHERE height=?"; 
    
    if (!InterestManager.excludedAccountsSQL.isEmpty()) {
      sql += " AND account_id NOT IN ("+InterestManager.excludedAccountsSQL+")";
    }
    
    try (PreparedStatement pstmtSelect = connection.prepareStatement(sql);) {
      pstmtSelect.setInt(1, height);

      try (ResultSet rs = pstmtSelect.executeQuery()) {
        int payment_number;
        int begin;
        int end;
        boolean isInPeriod;
        AccountRecord account;

        while (rs.next()) {
          isInPeriod = InterestManager.IsInPeriod(connection, rs.getLong("account_id"), height);

          //
          // over threshold
          //
          if (rs.getLong("balance")>InterestManager.minimunThreshold) {
            // not in
            if (!isInPeriod) {
              account = InterestManager.GetLastAccountRecord(connection, rs.getLong("account_id"));

              // if no record
              if (account==null) {
                payment_number = 1;
                begin = height;
                end = height + InterestManager.period;
              }
              else {
                payment_number = account.payment_number + 1;

                if (account.next) {
                  begin = account.end;
                  end = begin + InterestManager.period;
                }
                else {
                  begin = height;
                  end = height + InterestManager.period;
                }
              }

              InterestManager.InsertAccount(connection, rs.getLong("account_id"), payment_number, begin, end, Wcg.getEpochTime());
            }
          }

          //
          // under threshold
          //
          else {
            // not in period
            if (!isInPeriod) {
              account = InterestManager.GetLastAccountRecord(connection, rs.getLong("account_id"));

              if (account!=null) {
                // set next to 0
                InterestManager.SetAccountNext(connection, rs.getLong("account_id"), account.payment_number, false);
              }
            }
            // in period
            else {
              account = InterestManager.GetAccountRecord(connection, rs.getLong("account_id"), height);
              
              // delete record with payment_number >=
              InterestManager.DeleteAccount(connection, rs.getLong("account_id"), account.payment_number);

              account = InterestManager.GetLastAccountRecord(connection, rs.getLong("account_id"));

              // update next
              if (account!=null) {
                // set next to 0
                InterestManager.SetAccountNext(connection, rs.getLong("account_id"), account.payment_number, false);
              }
            }
          }
        }
      }
    }
  }
  
}
