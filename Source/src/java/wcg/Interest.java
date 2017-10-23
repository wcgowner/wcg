// #leopard#

package wcg;

import wcg.db.DbClause;
import wcg.db.DbIterator;
import wcg.db.DbKey;
import wcg.db.EntityDbTable;
import wcg.util.Listener;
import wcg.util.Listeners;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Interest {

  public enum Event {
    INTEREST
  }

  private static final Listeners<Interest,Event> listeners = new Listeners<>();

  private static final DbKey.LongKeyFactory<Interest> interestDbKeyFactory = new DbKey.LongKeyFactory<Interest>("id") {

    @Override
    public DbKey newKey(Interest interest) {
      return interest.dbKey;
    }

  };

  private static final EntityDbTable<Interest> interestTable = new EntityDbTable<Interest>("interest", interestDbKeyFactory) {

    @Override
    protected Interest load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
      return new Interest(rs, dbKey);
    }

    @Override
    protected void save(Connection con, Interest interest) throws SQLException {
      interest.save(con);
    }

  };

  public static boolean addListener(Listener<Interest> listener, Event eventType) {
    return listeners.addListener(listener, eventType);
  }

  public static boolean removeListener(Listener<Interest> listener, Event eventType) {
    return listeners.removeListener(listener, eventType);
  }

  public static DbIterator<Interest> getInterests(long assetId, int from, int to) {
    return interestTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to);
  }

  // #TODO# : verify
  public static Interest getLastInterest() {
    /*
    try (DbIterator<Interest> dividends = interestTable.getManyBy(new DbClause.LongClause("asset_id", assetId), 0, 0)) {
      if (dividends.hasNext()) {
        return dividends.next();
      }
    }
    return null;  
    */
    
    return null;
  }

  static Interest addInterest(long transactionId, Attachment.InterestPayment attachment, long totalDividend, long numAccounts) {
    Interest interest = new Interest(transactionId, attachment, totalDividend, numAccounts);
    interestTable.insert(interest);
    listeners.notify(interest, Event.INTEREST);
    return interest;
  }

  static void init() {}

  private final long id;
  private final DbKey dbKey;
  private final long amount;
  private final int interestHeight;
  private final long totalInterest;
  private final long numAccounts;
  private final int timestamp;
  private final int height;

  private Interest(long transactionId, Attachment.InterestPayment attachment, long totalInterest, long numAccounts) {
    this.id = transactionId;
    this.dbKey = interestDbKeyFactory.newKey(this.id);
    this.amount = attachment.getAmount();
    this.interestHeight = attachment.getHeight();
    this.totalInterest = totalInterest;
    this.numAccounts = numAccounts;
    this.timestamp = Wcg.getBlockchain().getLastBlockTimestamp();
    this.height = Wcg.getBlockchain().getHeight(); 
  }

  private Interest(ResultSet rs, DbKey dbKey) throws SQLException {
    this.id = rs.getLong("id");
    this.dbKey = dbKey;
    this.amount = rs.getLong("amount");
    this.interestHeight = rs.getInt("interest_height");
    this.totalInterest = rs.getLong("total_interest");
    this.numAccounts = rs.getLong("num_accounts");
    this.timestamp = rs.getInt("timestamp");
    this.height = rs.getInt("height");
  }

  private void save(Connection con) throws SQLException {
    try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_dividend (id, asset_id, "
                                                      + "amount, dividend_height, total_dividend, num_accounts, timestamp, height) "
                                                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      int i = 0;
      pstmt.setLong(++i, this.id);
      pstmt.setLong(++i, this.amount);
      pstmt.setInt(++i, this.interestHeight);
      pstmt.setLong(++i, this.totalInterest);
      pstmt.setLong(++i, this.numAccounts);
      pstmt.setInt(++i, this.timestamp);
      pstmt.setInt(++i, this.height);
      pstmt.executeUpdate();
    }
  }

  public long getId() {
    return id;
  }

  public long getAmount() {
    return amount;
  }

  public int getInterestHeight() {
    return interestHeight;
  }

  public long getTotalInterest() {
    return totalInterest;
  }

  public long getNumAccounts() {
    return numAccounts;
  }

  public int getTimestamp() {
    return timestamp;
  }

  public int getHeight() {
    return height;
  }

}