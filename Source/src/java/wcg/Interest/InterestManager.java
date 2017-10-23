// #leopard#

package wcg.interest;

import java.sql.Connection;
import wcg.Wcg;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import wcg.Account;
import wcg.BlockchainProcessor;
import wcg.Db;
import wcg.util.Convert;
import wcg.util.Listeners;
import wcg.util.Logger;

public class InterestManager {
  
  public static class AccountRecord {
    public int payment_number;
    public int end;
    public boolean next;
  }
  
  private static final Listeners<Account,Account.Event> listeners = new Listeners<>();
  
  public static boolean isUpdating = false;
  
  public static List<String> excludedAccounts;
  
  public static String excludedAccountsSQL;
  
  public static List<String> payingAccounts;
  
  public static int period;

  public static int verifyPeriod;

  public static long minimunThreshold;
  
  private static String forgerSecretPhrase = "";
  
  private static Connection connection;
  
  private static String payerAccountRS = "WCG-MY8A-RK6G-KEDU-GVY3Z"; 
  
  private static Account payerAccount;
  
  public static int GetBalanceHeight(Connection connection) throws SQLException {
    try (
      PreparedStatement pstmtSelect = connection.prepareStatement("SELECT MAX(height) AS max_height FROM interest_balance");) {
        try (ResultSet rs = pstmtSelect.executeQuery()) {
          if (rs.next()) {
            return rs.getInt("max_height");
          }
          return 0;
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
  
  public static long GetLastBalanceChange(Connection connection, long account_id) throws SQLException {
    try (
      PreparedStatement pstmtSelect = connection.prepareStatement("SELECT balance FROM interest_balance_change WHERE account_id=? ORDER BY db_id DESC LIMIT 1");) {
        pstmtSelect.setLong(1, account_id);

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
  
  public static void SetForgerSecretPhrase(String secretPhrase) {
    forgerSecretPhrase = secretPhrase;
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
  
  public static Account GetPayerAccount() {
    return payerAccount;
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

    verifyPeriod = Wcg.getIntProperty("wcg.interestVerifyPeriod", 100);
     
    minimunThreshold = Wcg.getIntProperty("wcg.interestMinimunThreshold", 100);
    
    excludedAccounts = Wcg.getStringListProperty("wcg.interestExcludedAccounts", "-9128296992220251419;5977473349849294368;-1246335457790138311;7359284269271563671;7971533403673683675;2103030737530415949;-743446844806740485;-2881129103809383820;-6475845696648806297;-2461721788666564439;3690340317253181336;-7425202993905651001;3571454402745187992;5947863505463986435;-749187646861774489;4468076176986210766;3601447615317990179;7946451654626588351;3656291099097719233;9094040562910503463;-7211220690307824610;8457522211461559677;4296213944053612947;-677881220224165773;-8312935186537134532;3791148784704871638;4533367924778183250;1609802962710175117;8958192434388600228;-7633406546024938862;4868720552757198380;-4098980433027046045;5664781255241704056;2351500103851672483;9078704076565620152;1801406999511562855;5201065671878745379;-8313395224807156625");
    
    excludedAccountsSQL = String.join(",", excludedAccounts);
    
    excludedAccounts = Wcg.getStringListProperty("wcg.interestExcludedAccounts");
    
    payingAccounts = Wcg.getStringListProperty("wcg.interestExcludedAccounts", "5977473349849294368;-1246335457790138311;7359284269271563671;7971533403673683675;2103030737530415949;-743446844806740485;-2881129103809383820;-6475845696648806297;4868720552757198380;-2461721788666564439;3690340317253181336;-7425202993905651001;3571454402745187992;5947863505463986435;-749187646861774489;4468076176986210766;3601447615317990179;7946451654626588351;3656291099097719233;9094040562910503463;-7211220690307824610;-7633406546024938862;8457522211461559677;4296213944053612947;-677881220224165773;-8312935186537134532;-4098980433027046045;2351500103851672483;3791148784704871638;5664781255241704056");
    
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
        if (Wcg.getBlockchain().getHeight()%InterestManager.verifyPeriod==0 && !InterestManager.GetForgerSecretPhrase().isEmpty()) {
        }
      },
      BlockchainProcessor.Event.BLOCK_PUSHED);
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
  
  public static void InsertBalanceChange(Connection connection, long account_id, long transaction_id, int height, long amount, long balance, int timestamp) throws SQLException {
    
    String sql =  "INSERT INTO interest_balance_change" +
                  "  (account_id, transaction_id, height, amount, balance, timestamp)" +
                  " VALUES" +
                  " (?, ?, ?, ?, ?, ?)";
    
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      int i = 0;
      pstmt.setLong(++i, account_id);
      pstmt.setLong(++i, transaction_id);
      pstmt.setInt(++i, height);
      pstmt.setLong(++i, amount);
      pstmt.setLong(++i, balance);
      pstmt.setInt(++i, timestamp);
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

  public static void UpdateTables(Connection connection, int fromHeight) throws SQLException {
    isUpdating = true;
    
    try (PreparedStatement pstmtSelectBlocks = connection.prepareStatement("SELECT b.height FROM block b, transaction t WHERE b.height>? AND b.id=t.block_id group by b.height ORDER BY b.height LIMIT 100")) {
      pstmtSelectBlocks.setInt(1, fromHeight);
      
      try (ResultSet rsBlock = pstmtSelectBlocks.executeQuery()) {
        while (rsBlock.next()) {
          try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM transaction WHERE height=?")) {
            pstmtSelect.setInt(1, rsBlock.getInt("height"));
            
            try (ResultSet rs = pstmtSelect.executeQuery()) {
              while (rs.next()) {
                long transaction_id = rs.getLong("id");
                long forger_account_id = InterestManager.GetTransactionGeneratorId(connection, transaction_id);
                long recipient_account_id = rs.getLong("recipient_id");
                long amount = rs.getLong("amount");
                long fee = rs.getLong("fee");
                int height = rs.getInt("height");
                long sender_account_id = rs.getLong("sender_id");

                int timestamp = Wcg.getEpochTime();

                UpdateTablesBySingleTransaction(connection, recipient_account_id, sender_account_id, forger_account_id, height, transaction_id, amount, fee, timestamp);
              }
            }
          }
        }
        
        isUpdating = false;
      }
       
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
                  begin = account.end + 1;
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
  
  public static void UpdateTablesBySingleTransaction(Connection connection, long recipient_account_id, long sender_account_id, long forger_account_id, int height, long transaction_id, long amount, long fee, int timestamp) throws SQLException {
    long balance;
    
    // recipient
    if (recipient_account_id!=0 && amount!=0) {
      balance = amount + InterestManager.GetLastBalance(connection, recipient_account_id);
      
      MergeBalance(connection, recipient_account_id, height, balance, timestamp);
    }
     
    // sender
    balance = -amount - fee + InterestManager.GetLastBalance(connection, sender_account_id);

    MergeBalance(connection, sender_account_id, height, balance, timestamp);
      
    // forger
    balance = fee + InterestManager.GetLastBalance(connection, forger_account_id);
    
    MergeBalance(connection, forger_account_id, height, balance, timestamp);
  }
  
}
