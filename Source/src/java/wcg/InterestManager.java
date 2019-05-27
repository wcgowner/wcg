package wcg;

import wcg.crypto.Crypto;
import wcg.util.Convert;
import wcg.util.Listeners;
import wcg.util.Logger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InterestManager {

	public static class AccountRecord {
		public String   account_rs;
		public long			account_id;
		public int			payment_height;
		public int			begin;
		public int			end;
		public boolean			next;
		public long			balance;
		public long			incorrect_balance;
		public int			check_height;
		public long			first_balance;
		public int			type;
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

	private static long mergeBalanceTime;

	private static long deleteBalanceTime = 0L;

	private static final Listeners<Account,Account.Event> listeners = new Listeners<>();

	public static boolean isUpdating = false;

	public static List<String> excludedAccounts;

	public static String excludedAccountsSQL;

	public static int period;

	private static String forgerSecretPhrase = "";

	private static Connection connection;

	private static String payerAccountRS = "WCG-D62N-AA2Y-8S3U-4JKBR";

	private static Account payerAccount = null;

	public static double interestPercentage = 0.03;

	public static final int STOP_HEIGHT_FOR_INTEREST = 1024220 + 12960;

	public static long CalculateAverageBalance(long accountId, int begin, int end, long accountFirstBalance) throws SQLException {

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

							firstBalance.amount = accountFirstBalance;
							firstBalance.height = begin;

							balances.add(firstBalance);
						}
					}

					balances.add(balance);
				}

				if (balances.isEmpty()) {
					return accountFirstBalance;
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

	public static boolean CorrectInterests(int height) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE wrong_balance<>0 AND type=2 ORDER BY check_height, begin, account_id ASC LIMIT 100")) {
			try (ResultSet rs = pstmtSelect.executeQuery()) {
				Account payerAccount = InterestManager.GetPayerAccount();

				while (rs.next()) {
					Account account = Account.getAccount(rs.getLong("account_id"));

					BigInteger accountSpendableBalance = BigInteger.valueOf(account.getUnconfirmedBalanceNQT());

					if (accountSpendableBalance.compareTo(BigInteger.valueOf(1))<=0) {
						InterestManager.SetAccountCheckHeight(rs.getLong("account_id"), rs.getInt("payment_id"), height);

						continue;
					}

					Long transactionId = InterestManager.GetPaymentTransactionId(rs.getLong("payment_id"));

					Long wrongBalance = rs.getLong("wrong_balance");

					BigInteger wrongInterest = BigDecimal.valueOf(wrongBalance).multiply(BigDecimal.valueOf(InterestManager.interestPercentage)).toBigInteger();

					BigInteger delta = accountSpendableBalance.subtract(wrongInterest);

					if (delta.compareTo(BigInteger.ZERO)<0) {
						account.addToBalanceAndUnconfirmedBalanceNQT(AccountLedger.LedgerEvent.INTEREST_PAYMENT_CORRECTION, transactionId, - (accountSpendableBalance.longValue() - 1));

						payerAccount.addToBalanceAndUnconfirmedBalanceNQT(AccountLedger.LedgerEvent.INTEREST_PAYMENT_CORRECTION, transactionId, accountSpendableBalance.longValue() - 1);

						// set new wrong balance
						BigInteger newIncorrectBalance = BigInteger.valueOf(wrongBalance).subtract(BigDecimal.valueOf(accountSpendableBalance.longValue() - 1).divide(BigDecimal.valueOf(InterestManager.interestPercentage), 0).toBigInteger());

						InterestManager.SetAccountIncorrectBalance(rs.getLong("account_id"), rs.getInt("payment_id"), newIncorrectBalance.longValue(), height);
					}
					else {
						account.addToBalanceAndUnconfirmedBalanceNQT(AccountLedger.LedgerEvent.INTEREST_PAYMENT_CORRECTION, transactionId, -wrongInterest.longValue());

						payerAccount.addToBalanceAndUnconfirmedBalanceNQT(AccountLedger.LedgerEvent.INTEREST_PAYMENT_CORRECTION, transactionId, wrongInterest.longValue());

						InterestManager.SetAccountIncorrectBalance(rs.getLong("account_id"), rs.getInt("payment_id"), 0, height);
					}

					InterestManager.SetAccountCheckHeight(rs.getLong("account_id"), rs.getInt("payment_id"), height);
				}

				return true;
			}
		}
	}

	public static void CreatePayment(int height) throws SQLException {
		if (height<=720) {
			return;
		}

		List<AccountRecord> accounts;

		if (height<=357120) {
			accounts = InterestManager.GetPaymentAccounts(height-720);
		}
		else {
			accounts = InterestManager.GetPaymentAccounts2(height-720);
		}

		if (accounts.isEmpty()) {
			return;
		}

		// insert payment
		int paymentHeight = height;
		int paymentId = InterestManager.InsertPayment(paymentHeight);

		long amount = 0;

		for (int index=0; index<accounts.size(); index++) {
			try {
				AccountRecord account = accounts.get(index);

				Long averageBalance = InterestManager.CalculateAverageBalance(account.account_id, account.begin, account.end, account.first_balance);

				InterestManager.UpdateAccount(account.account_id, account.begin, account.end, paymentId, averageBalance, paymentHeight, account.type);
				//InterestManager.DeleteBalances(account.account_id, account.begin);

				amount += averageBalance;
			}
			catch (SQLException e) {
				Logger.logInfoMessage("Create payment exception " + e.toString());
			}
		}

		InterestManager.UpdatePayment(paymentHeight, amount, accounts.size());
	}

	public static void CreateTransaction () {
		PaymentRecord payment = null;

		try {
			payment = InterestManager.GetNextPaymentRecord();
			if (payment==null) {
				return;
			}

			payment.amount = BigDecimal.valueOf(payment.amount).multiply(BigDecimal.valueOf(InterestManager.interestPercentage)).longValue();
		}
		catch (Exception e) {
			Logger.logInfoMessage("Create transaction exception " + e);
		}

		Attachment.InterestPayment attachment = new Attachment.InterestPayment(payment.height, payment.accounts_number, payment.amount, payment.id);

		try {
			Transaction.Builder builder = Wcg.newTransactionBuilder(Crypto.getPublicKey(InterestManager.GetForgerSecretPhrase()), 0, Constants.ONE_WCG/Constants.REDUCTOR_FEE, (short)72, attachment);

			builder.timestamp(Wcg.getBlockchain().getLastBlockTimestamp());
			Transaction transaction = builder.build(InterestManager.GetForgerSecretPhrase());

			Wcg.getTransactionProcessor().broadcast(transaction);
		}
		catch (WcgException.ValidationException e) {
			Logger.logErrorMessage("Fatal error submitting interest payment transaction", e);
		}
	}

	public static void CreateTransaction2 () {
		PaymentRecord payment = null;

		try {
			payment = InterestManager.GetNextPaymentRecord();
			if (payment==null) {
				return;
			}

			payment.amount = BigDecimal.valueOf(payment.amount).multiply(BigDecimal.valueOf(InterestManager.interestPercentage)).longValue();
		}
		catch (Exception e) {
			Logger.logInfoMessage("Create transaction exception " + e);
		}

		Attachment.InterestPayment2 attachment = new Attachment.InterestPayment2(payment.height, payment.accounts_number, payment.amount, payment.id);

		try {
			Transaction.Builder builder = Wcg.newTransactionBuilder(Crypto.getPublicKey(InterestManager.GetForgerSecretPhrase()), 0, Constants.ONE_WCG/Constants.REDUCTOR_FEE, (short)72, attachment);

			builder.timestamp(Wcg.getBlockchain().getLastBlockTimestamp());
			Transaction transaction = builder.build(InterestManager.GetForgerSecretPhrase());

			Wcg.getTransactionProcessor().broadcast(transaction);
		}
		catch (WcgException.ValidationException e) {
			Logger.logErrorMessage("Fatal error submitting interest payment transaction", e);
		}
	}

	public static void DeleteAccount(long account_id, int begin) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_account WHERE account_id=? AND begin>=? AND type<>0")) {
			int i = 0;
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, begin);
			pstmt.executeUpdate();
		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static void DeleteAccount2(long account_id, int begin) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_account WHERE account_id=? AND begin>=? AND type=0")) {
			int i = 0;
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, begin);
			pstmt.executeUpdate();
		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static void DeleteAccountsAtHeight(int begin) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_account WHERE begin>=?")) {
			int i = 0;
			pstmt.setInt(++i, begin);
			pstmt.executeUpdate();
		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static void DeleteBalancesAtHeight(int height) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_balance WHERE height>=?")) {
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.executeUpdate();
		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static void DeleteBalances(long account_id, int height) throws SQLException {

		try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_balance WHERE account_id=? AND height<=? LIMIT "+Constants.BATCH_COMMIT_SIZE)) {
			int i = 0;
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, height);

			int count;

			do {
				count = pstmt.executeUpdate();
			} while (count >= Constants.BATCH_COMMIT_SIZE);

		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static void DeletePaymentsAtHeight(int height) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "DELETE FROM interest_payment WHERE height>=?")) {
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.executeUpdate();
		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET payment_id=NULL, payment_height=NULL WHERE payment_height>=?")) {
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.executeUpdate();
		}
		catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static AccountRecord GetAccountRecord(long account_id, int height) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE account_id=? AND begin<=? AND end>=? AND type<>0")) {
			pstmtSelect.setLong(1, account_id);
			pstmtSelect.setInt(2, height);
			pstmtSelect.setInt(3, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				if (rs.next()) {
					AccountRecord account = new AccountRecord();
					account.begin = rs.getInt("begin");
					account.end = rs.getInt("end");
					account.next = rs.getBoolean("next");

					return account;
				}
				return null;
			}
		}
	}

	public static AccountRecord GetAccountRecord2(long account_id, int height) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE account_id=? AND begin<=? AND end>=? AND type<>2")) {
			pstmtSelect.setLong(1, account_id);
			pstmtSelect.setInt(2, height);
			pstmtSelect.setInt(3, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				if (rs.next()) {
					AccountRecord account = new AccountRecord();
					account.begin = rs.getInt("begin");
					account.end = rs.getInt("end");
					account.next = rs.getBoolean("next");

					return account;
				}
				return null;
			}
		}
	}

	public static List<AccountRecord> GetAccounts(int paymentId) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT account_id, balance FROM interest_account WHERE payment_id=?")) {
			pstmtSelect.setInt(1, paymentId);

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

	public static long GetBalanceAtHeight(long account_id, int height) throws SQLException {
		try (
				PreparedStatement pstmtSelect = connection.prepareStatement("SELECT balance FROM interest_balance WHERE account_id=? AND height<=? ORDER BY height DESC LIMIT 1")) {
			pstmtSelect.setLong(1, account_id);
			pstmtSelect.setInt(2, height);

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

	public static AccountRecord GetLastAccountRecord(long account_id) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE account_id=? AND type<>0 ORDER BY begin DESC LIMIT 1")) {
			pstmtSelect.setLong(1, account_id);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				if (rs.next()) {
					AccountRecord account = new AccountRecord();
					account.begin = rs.getInt("begin");
					account.end = rs.getInt("end");
					account.next = rs.getBoolean("next");

					return account;
				}
				return null;
			}
		}
	}

	public static AccountRecord GetLastAccountRecord2(long account_id) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_account WHERE account_id=? AND type<>2 ORDER BY begin DESC LIMIT 1")) {
			pstmtSelect.setLong(1, account_id);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				if (rs.next()) {
					AccountRecord account = new AccountRecord();
					account.begin = rs.getInt("begin");
					account.end = rs.getInt("end");
					account.next = rs.getBoolean("next");

					return account;
				}
				return null;
			}
		}
	}

	public static long GetMinimumThreshold(int height, int periodBegin) {
		if (height<183769) {
			return 100;
		}

		if (height<=336729 && periodBegin>0 && periodBegin<183769) {
			return 100;
		}

		return 100*Constants.ONE_WCG;
	}

	public static PaymentRecord GetNextPaymentRecord() throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT * FROM interest_payment WHERE transaction_id IS NULL ORDER BY id ASC LIMIT 1")) {

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

	public static List<AccountRecord> GetPaidAccountRecords(long account_id, int firstIndex, String searchAccountRS, String searchPaymentHeight) throws SQLException {

		if (account_id==InterestManager.GetPayerAccount().getId()) {
			String sql = "SELECT * FROM interest_account WHERE payment_id IS NOT NULL";

			if (!searchAccountRS.isEmpty()) {
				sql += " AND account_id="+Convert.parseAccountId(searchAccountRS);
			}

			if (!searchPaymentHeight.isEmpty()) {
				sql += " AND payment_height="+searchPaymentHeight;
			}

			sql += " ORDER BY payment_height LIMIT ?, 16";

			try (PreparedStatement pstmtSelect = connection.prepareStatement(sql)) {
				pstmtSelect.setInt(1, firstIndex);
				try (ResultSet rs = pstmtSelect.executeQuery()) {
					List<AccountRecord> accounts = new ArrayList<>();

					while (rs.next()) {
						AccountRecord account = new AccountRecord();

						account.account_rs = Convert.rsAccount(rs.getLong("account_id"));
						account.payment_height = rs.getInt("payment_height");
						account.begin = rs.getInt("begin");
						account.end = rs.getInt("end");
						account.next = rs.getBoolean("next");
						account.balance = rs.getLong("balance");
						account.incorrect_balance = rs.getLong("wrong_balance");
						account.check_height = rs.getInt("check_height");

						accounts.add(account);
					}

					return accounts;
				}
			}
		}
		else {
			String sql = "SELECT * FROM interest_account WHERE account_id=? AND payment_id IS NOT NULL";

			if (!searchPaymentHeight.isEmpty()) {
				sql += " AND payment_height="+searchPaymentHeight;
			}

			try (PreparedStatement pstmtSelect = connection.prepareStatement(sql)) {
				pstmtSelect.setLong(1, account_id);

				try (ResultSet rs = pstmtSelect.executeQuery()) {
					List<AccountRecord> accounts = new ArrayList<>();

					if (rs.next()) {
						AccountRecord account = new AccountRecord();

						account.payment_height = rs.getInt("payment_height");
						account.begin = rs.getInt("begin");
						account.end = rs.getInt("end");
						account.next = rs.getBoolean("next");
						account.balance = rs.getLong("balance");
						account.incorrect_balance = rs.getLong("wrong_balance");
						account.check_height = rs.getInt("check_height");

						accounts.add(account);
					}
					return accounts;
				}
			}
		}
	}

	public static Account GetPayerAccount() {
		if (payerAccount==null) {
			payerAccount = Account.getAccount(Convert.parseAccountId(payerAccountRS));
		}

		return payerAccount;
	}

	public static long GetPaymentTransactionId(long paymentId)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "SELECT transaction_id FROM interest_payment WHERE id=? AND transaction_id IS NOT NULL")) {
			int i = 0;
			pstmt.setLong(++i, paymentId);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return rs.getLong("transaction_id");
				}
				return 0;
			}
		}
	}

	public static List<AccountRecord> GetPaymentAccounts(int height) throws SQLException {
		try (
				PreparedStatement pstmtSelect = connection.prepareStatement(	"SELECT account_id, begin, end, type, first_balance FROM interest_account"
						+ " WHERE end<?"
						+ "  AND payment_id IS NULL"
						+ "  AND type<>0")) {
			pstmtSelect.setInt(1, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				List<AccountRecord> accounts = new ArrayList<>();

				while (rs.next()) {
					AccountRecord account = new AccountRecord();

					account.account_id = rs.getLong("account_id");
					account.begin = rs.getInt("begin");
					account.end = rs.getInt("end");
					account.type = rs.getInt("type");
					account.first_balance = rs.getLong("first_balance");

					accounts.add(account);
				}
				return accounts;
			}
		}
	}

	public static List<AccountRecord> GetPaymentAccounts2(int height) throws SQLException {
		try (
				PreparedStatement pstmtSelect = connection.prepareStatement(	"SELECT account_id, begin, end, type, first_balance FROM interest_account"
						+ " WHERE end<?"
						+ "  AND payment_id IS NULL")) {
			pstmtSelect.setInt(1, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				List<AccountRecord> accounts = new ArrayList<>();

				while (rs.next()) {
					AccountRecord account = new AccountRecord();

					account.account_id = rs.getLong("account_id");
					account.begin = rs.getInt("begin");
					account.end = rs.getInt("end");
					account.type = rs.getInt("type");
					account.first_balance = rs.getLong("first_balance");

					accounts.add(account);
				}
				return accounts;
			}
		}
	}

	public static long GetTransactionGeneratorId(long transaction_id) throws SQLException {
		try (
				PreparedStatement pstmtSelect = connection.prepareStatement("SELECT generator_id FROM transaction t, block b WHERE t.id=? AND b.id=t.block_id")) {
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
			Logger.logInfoMessage("Interest manager init exception " + e);
		}

		period = 172800;

		excludedAccounts = Wcg.getStringListProperty("wcg.interestExcludedAccounts", "3145254449822666772;-9128296992220251419;5977473349849294368;-1246335457790138311;7359284269271563671;7971533403673683675;2103030737530415949;-743446844806740485;-2881129103809383820;-6475845696648806297;-2461721788666564439;3690340317253181336;-7425202993905651001;3571454402745187992;5947863505463986435;-749187646861774489;4468076176986210766;3601447615317990179;7946451654626588351;3656291099097719233;9094040562910503463;-7211220690307824610;8457522211461559677;4296213944053612947;-677881220224165773;-8312935186537134532;3791148784704871638;4533367924778183250;1609802962710175117;8958192434388600228;-7633406546024938862;4868720552757198380;-4098980433027046045;5664781255241704056;2351500103851672483;9078704076565620152;1801406999511562855;5201065671878745379;-8313395224807156625;2390885803903812660;-7568353984704024507;7093369689011242749;8560745808460021533;-5871348417086311436;5139052429375015576;7496203581116464383;-1682022355339196366;1430675043231783375;1528068372797352573");

		excludedAccountsSQL = String.join(",", excludedAccounts);


		Account.addListener(account -> {

					int currentHeight = Wcg.getBlockchain().getHeight();

					if(currentHeight >= STOP_HEIGHT_FOR_INTEREST) {
						InterestManager.logInterestStopDebug(currentHeight,"InterestManagdr.Init().Account.addListener for Account.Event.BALANCE, not adding listeners. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

					} else {
                        InterestManager.logInterestBeforeStopDebug(currentHeight,"InterestManagdr.Init().Account.addListener for Account.Event.BALANCE, running as usual. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

						if (InterestManager.excludedAccounts.contains(String.valueOf(account.getId()))) {
							return;
						}

						try (PreparedStatement pstmt = connection.prepareStatement( "MERGE INTO interest_balance" +
								"  (account_id, height, balance) KEY(account_id, height)" +
								" VALUES" +
								" (?, ?, ?)")) {
							int i = 0;
							pstmt.setLong(++i, account.getId());
							pstmt.setInt(++i, Wcg.getBlockchain().getHeight());
							pstmt.setLong(++i, account.getBalanceNQT());
							pstmt.executeUpdate();
						}
						catch (SQLException e) {
							Logger.logInfoMessage("Merge balance exception " + e);
						}
					}
				},
				Account.Event.BALANCE);


		Wcg.getBlockchainProcessor().addListener(block -> {

					int currentHeight = Wcg.getBlockchain().getHeight();

					if(currentHeight >= STOP_HEIGHT_FOR_INTEREST) {
						InterestManager.logInterestStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.AFTER_BLOCK_APPLY, not adding listeners. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

					} else {
                        InterestManager.logInterestBeforeStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.AFTER_BLOCK_APPLY, running as usual. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

						try {
							int height = Wcg.getBlockchain().getHeight();

							if (height<=(183769+172800)) {
								InterestManager.UpdateTableAccount(height);
							}

							InterestManager.UpdateTableAccount2(height);
						}
						catch (SQLException e) {
							Logger.logInfoMessage("Update table account exception " + e);
						}

						//
						// CREATE PAYMENT
						//
						try {
							if (block.getHeight()<=357120 && block.getHeight()%720==0) {
								InterestManager.CreatePayment(block.getHeight());
							}
							else if (block.getHeight()>357120 && block.getHeight()%20==1) {
								InterestManager.CreatePayment(block.getHeight());
							}
						}
						catch (SQLException e) {
							Logger.logInfoMessage("Create payment exception : " + e);
						}
					}
				},
				BlockchainProcessor.Event.AFTER_BLOCK_APPLY);


		Wcg.getBlockchainProcessor().addListener(block -> {

					int currentHeight = Wcg.getBlockchain().getHeight();

					if(currentHeight >= STOP_HEIGHT_FOR_INTEREST) {
						InterestManager.logInterestStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.AFTER_BLOCK_APPLY, not adding listeners. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

					} else {
                        InterestManager.logInterestBeforeStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.AFTER_BLOCK_APPLY, running as usual. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

						if (block.getHeight()>493300 && block.getHeight()<740900) {
							try {
								InterestManager.CorrectInterests(block.getHeight());
							}
							catch (SQLException e) {
								Logger.logInfoMessage("Correct interests exception : " + e);
							}
						}
					}
				},
				BlockchainProcessor.Event.AFTER_BLOCK_APPLY);


		Wcg.getBlockchainProcessor().addListener(block -> {

					int currentHeight = Wcg.getBlockchain().getHeight();

					if(currentHeight >= STOP_HEIGHT_FOR_INTEREST) {
						InterestManager.logInterestStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.BLOCK_GENERATED, not adding listeners. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

					} else {
						InterestManager.logInterestBeforeStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.BLOCK_GENERATED, running as usual. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

						if (block.getHeight()>494740 && !InterestManager.GetForgerSecretPhrase().isEmpty() && !Wcg.getBlockchainProcessor().isDownloading() && (block.getHeight()%20==8 || block.getHeight()%20==14)) {
							InterestManager.CreateTransaction2();
						}
					}
				},
				BlockchainProcessor.Event.BLOCK_GENERATED);


		Wcg.getBlockchainProcessor().addListener(block -> {

					int currentHeight = Wcg.getBlockchain().getHeight();

					if(currentHeight >= STOP_HEIGHT_FOR_INTEREST) {
						InterestManager.logInterestStopDebug(currentHeight,"InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.BLOCK_POPPED, not adding listeners. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

					} else {
						Logger.logDebugMessage("InterestManagdr.Init().Wcg.getBlockchainProcessor().addListener for BlockchainProcessor.Event.BLOCK_POPPED, running as usual. Current height: "+currentHeight+", Stop height: "+STOP_HEIGHT_FOR_INTEREST);

						try {
							InterestManager.DeleteBalancesAtHeight(block.getHeight());
							InterestManager.DeleteAccountsAtHeight(block.getHeight());
							InterestManager.DeletePaymentsAtHeight(block.getHeight());
							InterestManager.ResetPaymentTransaction(block.getHeight());
						}
						catch (SQLException e) {
							Logger.logInfoMessage("exception " + e);
						}
					}
				},
				BlockchainProcessor.Event.BLOCK_POPPED);

	}

	public static int IsInPeriod(long account_id, int height) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT begin FROM interest_account WHERE account_id=? AND begin<=? AND end>=? AND type<>0")) {
			pstmtSelect.setLong(1, account_id);
			pstmtSelect.setInt(2, height);
			pstmtSelect.setInt(3, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("begin");
				}
				return 0;
			}
		}

	}

	public static int IsInPeriod2(long account_id, int height) throws SQLException {
		try (PreparedStatement pstmtSelect = connection.prepareStatement("SELECT begin FROM interest_account WHERE account_id=? AND begin<=? AND end>=? AND type<>2")) {
			pstmtSelect.setLong(1, account_id);
			pstmtSelect.setInt(2, height);
			pstmtSelect.setInt(3, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("begin");
				}
				return 0;
			}
		}
	}

	public static void InsertAccount(long account_id, int begin, int end, long firstBalance, int type) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "INSERT INTO interest_account" +
				"  (account_id, begin, end, first_balance, type)" +
				" VALUES" +
				" (?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, begin);
			pstmt.setInt(++i, end);
			pstmt.setLong(++i, firstBalance);
			pstmt.setInt(++i, type);
			pstmt.executeUpdate();
		}
	}

	public static int InsertPayment(int height) throws SQLException {

		int id = 0;

		String sql = "SELECT MAX(id) AS id FROM interest_payment";

		try (PreparedStatement pstmtSelect = connection.prepareStatement(sql)) {

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				rs.next();

				id = rs.getInt("id");

				if (id==201) {
					id = 220;
				}
				else if (id==221) {
					id = 252;
				}
				else {
					++id;
				}
			}
		}

		try (PreparedStatement pstmt = connection.prepareStatement( "INSERT INTO interest_payment" +
				"  (id,height)" +
				" VALUES" +
				" (?,?)")) {
			int i = 0;
			pstmt.setInt(++i, id);
			pstmt.setInt(++i, height);
			pstmt.executeUpdate();

			return id;
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

	public static void SetAccountCheckHeight(long account_id, int payment_id, int height) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET check_height=? WHERE account_id=? AND payment_id=?")) {
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, payment_id);
			pstmt.executeUpdate();
		}
	}

	public static void SetAccountIncorrectBalance(long account_id, int payment_id, long incorrect_balance, int height) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET wrong_balance=?, check_height=? WHERE account_id=? AND payment_id=?")) {
			int i = 0;
			pstmt.setLong(++i, incorrect_balance);
			pstmt.setInt(++i, height);
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, payment_id);
			pstmt.executeUpdate();
		}
	}

	public static void SetAccountNext(long account_id, int begin, boolean next) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET next=?" +
				"  WHERE account_id=? AND begin=? AND type<>0")) {
			int i = 0;
			pstmt.setBoolean(++i, next);
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, begin);
			pstmt.executeUpdate();
		}
	}

	public static void SetAccountType(long account_id, int begin, int type) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET type=?" +
				"  WHERE account_id=? AND begin=? AND type<>0")) {
			int i = 0;
			pstmt.setInt(++i, type);
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, begin);
			pstmt.executeUpdate();
		}
	}

	public static void SetAccountNext2(long account_id, int begin, boolean next) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET next=?" +
				"  WHERE account_id=? AND begin=? AND type=0")) {
			int i = 0;
			pstmt.setBoolean(++i, next);
			pstmt.setLong(++i, account_id);
			pstmt.setInt(++i, begin);
			pstmt.executeUpdate();
		}
	}

	public static void SetForgerSecretPhrase(String secretPhrase) {
		forgerSecretPhrase = secretPhrase;
	}

	public static void UpdateAccount(long accountId, int begin, int end, int paymentId, long averageBalance, int paymentHeight, int type)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_account SET payment_id=?, balance=?, payment_height=?, wrong_balance=?" +
				"  WHERE account_id=? AND begin=? AND end=?")) {
			int i = 0;
			pstmt.setInt(++i, paymentId);
			pstmt.setLong(++i, averageBalance);
			pstmt.setInt(++i, paymentHeight);
			pstmt.setLong(++i, (type==2) ? averageBalance : 0L);
			pstmt.setLong(++i, accountId);
			pstmt.setInt(++i, begin);
			pstmt.setInt(++i, end);
			pstmt.executeUpdate();
		}
	}

	public static void UpdatePayment(int paymentHeight, long amount, int accountsNumber)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_payment SET amount=?, accounts_number=?" +
				"  WHERE height=?")) {
			int i = 0;
			pstmt.setLong(++i, amount);
			pstmt.setInt(++i, accountsNumber);
			pstmt.setInt(++i, paymentHeight);
			pstmt.executeUpdate();
		}
	}

	public static void UpdatePaymentTransaction(long transactionId, int transactionHeight, int paymentId)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "UPDATE interest_payment SET transaction_id=?, transaction_height=? WHERE id=?")) {
			int i = 0;
			pstmt.setLong(++i, transactionId);
			pstmt.setInt(++i, transactionHeight);
			pstmt.setInt(++i, paymentId);
			pstmt.executeUpdate();
		}
	}

	public static void UpdateTableAccount(int height) throws SQLException {
		String sql = "SELECT * FROM interest_balance WHERE height=?";
		try (PreparedStatement pstmtSelect = connection.prepareStatement(sql)) {
			pstmtSelect.setInt(1, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				int begin;
				int end;
				int isInPeriod;
				AccountRecord account;
				long firstBalance;
				int accountType = 0;

				while (rs.next()) {

					if (!InterestManager.excludedAccounts.isEmpty() && InterestManager.excludedAccounts.contains(String.valueOf(rs.getLong("account_id")))) {
						continue;
					}

					isInPeriod = InterestManager.IsInPeriod(rs.getLong("account_id"), height);

					//
					// over threshold
					//
					if (rs.getLong("balance")>InterestManager.GetMinimumThreshold(height, isInPeriod)) {

						// not in
						if (isInPeriod==0) {
							account = InterestManager.GetLastAccountRecord(rs.getLong("account_id"));

							// if no record
							if (account==null) {
								begin = height;
								end = height + InterestManager.period;
								firstBalance = rs.getLong("balance");
							}
							else {
								continue;
							}

							accountType = 1;
							if (rs.getLong("balance")<=100*Constants.ONE_WCG) {
								accountType = 2;
							}

							InterestManager.InsertAccount(rs.getLong("account_id"), begin, end, firstBalance, accountType);
						}
						else {
							if (rs.getLong("balance")<=100*Constants.ONE_WCG) {
								InterestManager.SetAccountType(rs.getLong("account_id"), isInPeriod, 2);
							}
						}
					}

					//
					// under threshold
					//
					else {
						// not in period
						if (isInPeriod==0) {
							account = InterestManager.GetLastAccountRecord(rs.getLong("account_id"));

							if (account!=null) {
								// set next to 0
								InterestManager.SetAccountNext(rs.getLong("account_id"), account.begin, false);
							}
						}
						// in period
						else {
							account = InterestManager.GetAccountRecord(rs.getLong("account_id"), height);

							InterestManager.DeleteAccount(rs.getLong("account_id"), account.begin);

							account = InterestManager.GetLastAccountRecord(rs.getLong("account_id"));

							// update next
							if (account!=null) {
								// set next to 0
								InterestManager.SetAccountNext(rs.getLong("account_id"), account.begin, false);
							}
						}
					}
				}
			}
		}
	}

	public static void UpdateTableAccount2(int height) throws SQLException {
		String sql = "SELECT * FROM interest_balance WHERE height=?";

		try (PreparedStatement pstmtSelect = connection.prepareStatement(sql)) {
			pstmtSelect.setInt(1, height);

			try (ResultSet rs = pstmtSelect.executeQuery()) {
				int begin;
				int end;
				int isInPeriod;
				AccountRecord account;
				long firstBalance;

				while (rs.next()) {
					if (!InterestManager.excludedAccounts.isEmpty() && InterestManager.excludedAccounts.contains(String.valueOf(rs.getLong("account_id")))) {
						continue;
					}

					isInPeriod = InterestManager.IsInPeriod2(rs.getLong("account_id"), height);

					//
					// over threshold
					//
					if (rs.getLong("balance")>100*Constants.ONE_WCG) {
						// not in
						if (isInPeriod==0) {
							account = InterestManager.GetLastAccountRecord2(rs.getLong("account_id"));

							// if no record
							if (account==null) {
								begin = height;
								end = height + InterestManager.period;
								firstBalance = rs.getLong("balance");
							}
							else {
								if (account.next) {
									begin = account.end;
									end = begin + InterestManager.period;
								}
								else {
									begin = height;
									end = height + InterestManager.period;
								}

								firstBalance = InterestManager.GetBalanceAtHeight(rs.getLong("account_id"), begin);
							}

							InterestManager.InsertAccount(rs.getLong("account_id"), begin, end, firstBalance, 0);
						}
					}

					//
					// under threshold
					//
					else {
						// not in period
						if (isInPeriod==0) {
							account = InterestManager.GetLastAccountRecord2(rs.getLong("account_id"));

							if (account!=null) {
								// set next to 0
								InterestManager.SetAccountNext2(rs.getLong("account_id"), account.begin, false);
							}
						}
						// in period
						else {
							account = InterestManager.GetAccountRecord2(rs.getLong("account_id"), height);

							InterestManager.DeleteAccount2(rs.getLong("account_id"), account.begin);

							account = InterestManager.GetLastAccountRecord2(rs.getLong("account_id"));

							// update next
							if (account!=null) {
								// set next to 0
								InterestManager.SetAccountNext2(rs.getLong("account_id"), account.begin, false);
							}
						}
					}
				}
			}
		}
	}

	public static boolean VerifyPayment(int paymentHeight)  throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement( "SELECT * FROM interest_payment WHERE height=? AND transaction_id IS NOT NULL")) {
			int i = 0;
			pstmt.setInt(++i, paymentHeight);

			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next();

			}
		}
	}

	/**
	 * Only logs the debug message on the first 20 stop height
	 * @param height the current height
	 */
	private static void logInterestStopDebug(int height, String message) {

	    if (Logger.isDebugEnabled()) {
            if (height <= STOP_HEIGHT_FOR_INTEREST + 20) {
                Logger.logDebugMessage(message);
            }
        }

	}


    /**
     * Only logs the debug message on the message 100 blocks before the stop height
     * @param height the current height
     */
    private static void logInterestBeforeStopDebug(int height, String message) {

        if (Logger.isDebugEnabled()) {
            if (height >= STOP_HEIGHT_FOR_INTEREST -100 && height <= STOP_HEIGHT_FOR_INTEREST + 20) {
                Logger.logDebugMessage(message);
            }
        }

    }

}

