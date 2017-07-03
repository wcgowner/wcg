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
import wcg.Constants;
import wcg.DigitalGoodsStore;
import wcg.WcgException;
import wcg.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static wcg.http.JSONResponses.DUPLICATE_REFUND;
import static wcg.http.JSONResponses.GOODS_NOT_DELIVERED;
import static wcg.http.JSONResponses.INCORRECT_DGS_REFUND;
import static wcg.http.JSONResponses.INCORRECT_PURCHASE;

public final class DGSRefund extends CreateTransaction {

    static final DGSRefund instance = new DGSRefund();

    private DGSRefund() {
        super(new APITag[] {APITag.DGS, APITag.CREATE_TRANSACTION},
                "purchase", "refundNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws WcgException {

        Account sellerAccount = ParameterParser.getSenderAccount(req);
        DigitalGoodsStore.Purchase purchase = ParameterParser.getPurchase(req);
        if (sellerAccount.getId() != purchase.getSellerId()) {
            return INCORRECT_PURCHASE;
        }
        if (purchase.getRefundNote() != null) {
            return DUPLICATE_REFUND;
        }
        if (purchase.getEncryptedGoods() == null) {
            return GOODS_NOT_DELIVERED;
        }

        String refundValueNQT = Convert.emptyToNull(req.getParameter("refundNQT"));
        long refundNQT = 0;
        try {
            if (refundValueNQT != null) {
                refundNQT = Long.parseLong(refundValueNQT);
            }
        } catch (RuntimeException e) {
            return INCORRECT_DGS_REFUND;
        }
        if (refundNQT < 0 || refundNQT > Constants.MAX_BALANCE_NQT) {
            return INCORRECT_DGS_REFUND;
        }

        Account buyerAccount = Account.getAccount(purchase.getBuyerId());

        Attachment attachment = new Attachment.DigitalGoodsRefund(purchase.getId(), refundNQT);
        return createTransaction(req, sellerAccount, buyerAccount.getId(), 0, attachment);

    }

}
