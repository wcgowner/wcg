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

import java.math.BigDecimal;
import java.sql.SQLException;
import wcg.AccountLedger;
import wcg.AccountLedger.LedgerEntry;
import wcg.AccountLedger.LedgerEvent;
import wcg.AccountLedger.LedgerHolding;
import wcg.WcgException;
import wcg.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import wcg.InterestManager;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import wcg.Account;
import wcg.util.Logger;


public class GetAccountInterest extends APIServlet.APIRequestHandler {

    /** GetAccountLedger instance */
    static final GetAccountInterest instance = new GetAccountInterest();

    /**
     * Create the GetAccountLedger instance
     */
    private GetAccountInterest() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "firstIndex", "lastIndex", "eventType", "event", "holdingType", "holding", "includeTransactions", "includeHoldingInfo", "search_account_rs", "search_payment_height");
    }

    /**
     * Process the GetAccountLedger API request
     *
     * @param   req                 API request
     * @return                      API response
     * @throws  WcgException        Invalid request
     */
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws WcgException {
			
			//
			// Process the request parameters
			//
			long accountId = ParameterParser.getAccountId(req, "account", false);
			int firstIndex = ParameterParser.getFirstIndex(req);
			int lastIndex = ParameterParser.getLastIndex(req);
			
			String searchAccountRS = req.getParameter("search_account_rs");
			String searchPaymentHeight = req.getParameter("search_payment_height");

			JSONArray responseEntries = new JSONArray();

			//
			// Get the account interest entries
			//
			try {
				List<InterestManager.AccountRecord> accounts = InterestManager.GetPaidAccountRecords(accountId, firstIndex, searchAccountRS, searchPaymentHeight);

				if (accounts.size()>0) {
					for (int index=0; index<accounts.size(); index++) {
						InterestManager.AccountRecord accountRecord = accounts.get(index);

						JSONObject responseEntry = new JSONObject();

						Long incorrectBalance = BigDecimal.valueOf(accountRecord.incorrect_balance).multiply(BigDecimal.valueOf(InterestManager.interestPercentage)).longValue();

						Long balance = BigDecimal.valueOf(accountRecord.balance).multiply(BigDecimal.valueOf(InterestManager.interestPercentage)).longValue();

						responseEntry.put("account_rs", accountRecord.account_rs);
						responseEntry.put("payment_height", accountRecord.payment_height);
						responseEntry.put("begin", accountRecord.begin);
						responseEntry.put("end", accountRecord.end);
						responseEntry.put("balance", String.valueOf(balance));
						responseEntry.put("incorrect_balance", String.valueOf(incorrectBalance));
						responseEntry.put("check_height", accountRecord.check_height);

						responseEntries.add(responseEntry);
					}
				}
				else {
					JSONObject responseEntry = new JSONObject();

					responseEntry.put("account_rs", "");
					responseEntry.put("payment_height", "0");
					responseEntry.put("begin", "0");
					responseEntry.put("end", "0");
					responseEntry.put("balance", "0");
					responseEntry.put("incorrect_balance", "0");
					responseEntry.put("check_height", "0");

					responseEntries.add(responseEntry);
				}
			}
			catch (SQLException e) {
				Logger.logInfoMessage("GetAccountInterest exception " + e);
			}

			JSONObject response = new JSONObject();
			response.put("entries", responseEntries);
			return response;
			
    }
}
