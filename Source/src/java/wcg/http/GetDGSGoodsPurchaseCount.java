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

import wcg.DigitalGoodsStore;
import wcg.WcgException;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetDGSGoodsPurchaseCount extends APIServlet.APIRequestHandler {

    static final GetDGSGoodsPurchaseCount instance = new GetDGSGoodsPurchaseCount();

    private GetDGSGoodsPurchaseCount() {
        super(new APITag[] {APITag.DGS}, "goods", "withPublicFeedbacksOnly", "completed");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws WcgException {

        long goodsId = ParameterParser.getUnsignedLong(req, "goods", true);
        final boolean withPublicFeedbacksOnly = "true".equalsIgnoreCase(req.getParameter("withPublicFeedbacksOnly"));
        final boolean completed = "true".equalsIgnoreCase(req.getParameter("completed"));

        JSONObject response = new JSONObject();
        response.put("numberOfPurchases", DigitalGoodsStore.Purchase.getGoodsPurchaseCount(goodsId, withPublicFeedbacksOnly, completed));
        return response;

    }

}
