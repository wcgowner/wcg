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

import wcg.WcgException;
import wcg.TaggedData;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetDataTagCount extends APIServlet.APIRequestHandler {

    static final GetDataTagCount instance = new GetDataTagCount();

    private GetDataTagCount() {
        super(new APITag[] {APITag.DATA});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws WcgException {
        JSONObject response = new JSONObject();
        response.put("numberOfDataTags", TaggedData.Tag.getTagCount());
        return response;
    }

}
