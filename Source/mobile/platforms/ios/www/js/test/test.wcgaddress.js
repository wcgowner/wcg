/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 * Copyright © 2016 Jelurida IP B.V.                                          *
 *                                                                            *
 * See the LICENSE.txt file at the top-level directory of this distribution   *
 * for licensing information.                                                 *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,*
 * no part of the Nxt software, including this file, may be copied, modified, *
 * propagated, or distributed except according to the terms contained in the  *
 * LICENSE.txt file.                                                          *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

QUnit.module("wcg.address");

QUnit.test("wcgAddress", function (assert) {
    var address = new WcgAddress();
    assert.equal(address.set("WCG-XK4R-7VJU-6EQG-7R335"), true, "valid address");
    assert.equal(address.toString(), "WCG-XK4R-7VJU-6EQG-7R335", "address");
    assert.equal(address.set("WCG-XK4R-7VJU-6EQG-7R336"), false, "invalid address");
});
