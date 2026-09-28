package com.earthpol.epmcapi.endpoints.voting;


import com.earthpol.epmcapi.endpoints.GetEndpoint;
import com.google.gson.JsonObject;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class VotingEndpoint extends GetEndpoint {

    @Override
    public String lookup() {
        // Matches the pattern used in ShopListEndpoint
        return getJsonElement().toString();
    }

    @Override
    public JsonObject getJsonElement() {
        JsonObject root = new JsonObject();

        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            root.addProperty("error", "PlaceholderAPI is not installed or not enabled");
            return root;
        }

        // Global totals
        int globalDailyTotal   = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_globaldailytotal%")
        );

        int globalWeeklyTotal   = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_globalweeklytotal%")
        );

        int globalMonthTotal   = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_globalmonthtotal%")
        );
        int globalAllTimeTotal = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_globalalltimetotal%")
        );

        // VoteParty values
        int votePartyCurrent   = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_votepartyvotescurrent%")
        );
        int votePartyNeeded    = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_votepartyvotesneeded%")
        );
        int votePartyRequired  = parseIntSafe(
                PlaceholderAPI.setPlaceholders(null, "%votingplugin_votepartyvotesrequired%")
        );

        // Main fields you asked for
        root.addProperty("totalVotesAllTime",   globalAllTimeTotal);
        root.addProperty("totalVotesThisMonth", globalMonthTotal);
        root.addProperty("totalVotesThisWeek", globalWeeklyTotal);
        root.addProperty("totalVotesThisDay", globalDailyTotal);

        root.addProperty("votePartyCurrentVotes",   votePartyCurrent);
        root.addProperty("votePartyVotesRequired",  votePartyRequired);
        root.addProperty("votePartyVotesNeeded",    votePartyNeeded);

        // Extra useful info (optional, remove if you do not care)
        String timeUntilMonthReset = PlaceholderAPI.setPlaceholders(
                null, "%votingplugin_timeuntilmonthreset%"
        );
        root.addProperty("timeUntilMonthReset", timeUntilMonthReset);

        return root;
    }

    private int parseIntSafe(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
