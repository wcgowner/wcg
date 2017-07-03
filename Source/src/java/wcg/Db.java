/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package wcg;

import wcg.db.BasicDb;
import wcg.db.TransactionalDb;

public final class Db {

    public static final String PREFIX = Constants.isTestnet ? "wcg.testDb" : "wcg.db";
    public static final TransactionalDb db = new TransactionalDb(new BasicDb.DbProperties()
            .maxCacheSize(Wcg.getIntProperty("wcg.dbCacheKB"))
            .dbUrl(Wcg.getStringProperty(PREFIX + "Url"))
            .dbType(Wcg.getStringProperty(PREFIX + "Type"))
            .dbDir(Wcg.getStringProperty(PREFIX + "Dir"))
            .dbParams(Wcg.getStringProperty(PREFIX + "Params"))
            .dbUsername(Wcg.getStringProperty(PREFIX + "Username"))
            .dbPassword(Wcg.getStringProperty(PREFIX + "Password", null, true))
            .maxConnections(Wcg.getIntProperty("wcg.maxDbConnections"))
            .loginTimeout(Wcg.getIntProperty("wcg.dbLoginTimeout"))
            .defaultLockTimeout(Wcg.getIntProperty("wcg.dbDefaultLockTimeout") * 1000)
            .maxMemoryRows(Wcg.getIntProperty("wcg.dbMaxMemoryRows"))
    );

    static void init() {
        db.init(new WcgDbVersion());
    }

    static void shutdown() {
        db.shutdown();
    }

    private Db() {} // never

}
