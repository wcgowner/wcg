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

package wcg.http;

import wcg.Account;
import wcg.Attachment;
import wcg.WcgException;
import wcg.Order;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static wcg.http.JSONResponses.UNKNOWN_ORDER;

public final class CancelBidOrder extends CreateTransaction {

    static final CancelBidOrder instance = new CancelBidOrder();

    private CancelBidOrder() {
        super(new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION}, "order");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws WcgException {
        long orderId = ParameterParser.getUnsignedLong(req, "order", true);
        Account account = ParameterParser.getSenderAccount(req);
        Order.Bid orderData = Order.Bid.getBidOrder(orderId);
        if (orderData == null || orderData.getAccountId() != account.getId()) {
            return UNKNOWN_ORDER;
        }
        Attachment attachment = new Attachment.ColoredCoinsBidOrderCancellation(orderId);
        return createTransaction(req, account, attachment);
    }

}
