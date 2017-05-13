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

import wcg.Wcg;
import wcg.WcgException;
import wcg.Poll;
import wcg.Vote;
import wcg.VoteWeighting;
import wcg.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetPollVotes extends APIServlet.APIRequestHandler  {
    static final GetPollVotes instance = new GetPollVotes();

    private GetPollVotes() {
        super(new APITag[] {APITag.VS}, "poll", "firstIndex", "lastIndex", "includeWeights");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws WcgException {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeWeights = "true".equalsIgnoreCase(req.getParameter("includeWeights"));
        Poll poll = ParameterParser.getPoll(req);
        int countHeight;
        JSONData.VoteWeighter weighter = null;
        if (includeWeights && (countHeight = Math.min(poll.getFinishHeight(), Wcg.getBlockchain().getHeight()))
                >= Wcg.getBlockchainProcessor().getMinRollbackHeight()) {
            VoteWeighting voteWeighting = poll.getVoteWeighting();
            VoteWeighting.VotingModel votingModel = voteWeighting.getVotingModel();
            weighter = voterId -> votingModel.calcWeight(voteWeighting, voterId, countHeight);
        }
        JSONArray votesJson = new JSONArray();
        try (DbIterator<Vote> votes = Vote.getVotes(poll.getId(), firstIndex, lastIndex)) {
            for (Vote vote : votes) {
                votesJson.add(JSONData.vote(vote, weighter));
            }
        }
        JSONObject response = new JSONObject();
        response.put("votes", votesJson);
        return response;
    }
}
