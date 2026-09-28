package com.earthpol.epmcapi.endpoints;

import au.lupine.quarters.api.manager.QuarterManager;
import au.lupine.quarters.object.entity.Quarter;
import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.utils.EndpointCacheSettings;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.ResponseSnapshot;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.util.List;

public class ServerEndpoint extends GetEndpoint {

    private final String rootPath = EPMCAPI.getInstance().getConfig().getString("networking.public_url", "http://localhost:8080").replaceAll("/+$", "");
    private final String basePath = EPMCAPI.getInstance().basePath();
    private final ResponseSnapshot lookupSnapshot = new ResponseSnapshot(EndpointCacheSettings.getServerSnapshotTtlMs());

    @Override
    public String lookup() {
        return lookupSnapshot.get(this::buildLookupResponse);
    }

    private String buildLookupResponse() {
        return getJsonElement().toString();
    }

    @Override
    public com.google.gson.JsonObject getJsonElement() {
        JsonObject serverObject = new JsonObject();

        TownyAPI townyAPI = TownyAPI.getInstance();
        World overworld = Bukkit.getWorlds().get(0);

        serverObject.addProperty("version", Bukkit.getMinecraftVersion());
        serverObject.addProperty("moonPhase", overworld.getMoonPhase().toString());

        JsonObject timeObject = new JsonObject();
        // Replace TownySettings with your Towny API call
        timeObject.addProperty("newDayTime", TownySettings.getNewDayTime());
        timeObject.addProperty("serverTimeOfDay", java.time.LocalTime.now().toSecondOfDay());
        timeObject.addProperty("stormDuration", overworld.getWeatherDuration());
        timeObject.addProperty("thunderDuration", overworld.getThunderDuration());
        timeObject.addProperty("time", overworld.getTime());
        timeObject.addProperty("fullTime", overworld.getFullTime());
        serverObject.add("time", timeObject);

        JsonObject statusObject = new JsonObject();
        statusObject.addProperty("hasStorm", overworld.hasStorm());
        statusObject.addProperty("isThundering", overworld.isThundering());
        statusObject.addProperty("daylightCycle", overworld.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
        statusObject.addProperty("mobSpawning", overworld.getGameRuleValue(GameRule.DO_MOB_SPAWNING));
        statusObject.addProperty("keepInventory", overworld.getGameRuleValue(GameRule.KEEP_INVENTORY));
        statusObject.addProperty("randomTickSpeed", overworld.getGameRuleValue(GameRule.RANDOM_TICK_SPEED));
        statusObject.addProperty("viewDistance", overworld.getViewDistance());
        statusObject.addProperty("simulationDistance", overworld.getSimulationDistance());
        serverObject.add("status", statusObject);

        JsonObject statsObject = new JsonObject();
        statsObject.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        statsObject.addProperty("numOnlinePlayers", Bukkit.getOnlinePlayers().size());
        statsObject.addProperty("numOnlineNomads", EndpointUtils.getNumOnlineNomads());
        statsObject.addProperty("numResidents", townyAPI.getResidents().size());
        statsObject.addProperty("numNomads", townyAPI.getResidentsWithoutTown().size());
        statsObject.addProperty("numTowns", townyAPI.getTowns().size());
        statsObject.addProperty("numTownBlocks", townyAPI.getTownBlocks().size());
        statsObject.addProperty("numNations", townyAPI.getNations().size());

        statsObject.addProperty("numQuarters", EPMCAPI.getInstance().hasPlugin("Quarters")
                ? QuarterManager.getInstance().getAllQuarters().size() : null);

        serverObject.add("stats", statsObject);

        JsonObject endpointObject = new JsonObject();
        endpointObject.addProperty("capabilities", rootPath + basePath + "/capabilities");
        if(EPMCAPI.getInstance().endpointAvailable("towns"))
            endpointObject.addProperty("towns", rootPath + basePath + "/towns");
        if(EPMCAPI.getInstance().endpointAvailable("nations"))
            endpointObject.addProperty("nations", rootPath + basePath + "/nations");
        if(EPMCAPI.getInstance().endpointAvailable("players"))
            endpointObject.addProperty("players", rootPath + basePath + "/players");
        if(EPMCAPI.getInstance().endpointAvailable("shops"))
            endpointObject.addProperty("shops", rootPath + basePath + "/shops");
        if(EPMCAPI.getInstance().endpointAvailable("location"))
            endpointObject.addProperty("location", rootPath + basePath + "/location");
        if(EPMCAPI.getInstance().endpointAvailable("discord"))
            endpointObject.addProperty("discord", rootPath + basePath + "/discord");
        if(EPMCAPI.getInstance().endpointAvailable("quarters"))
            endpointObject.addProperty("quarters", rootPath + basePath + "/quarters");
        if(EPMCAPI.getInstance().endpointAvailable("chat"))
            endpointObject.addProperty("chat", rootPath + basePath + "/chat");
        if(EPMCAPI.getInstance().endpointAvailable("voting"))
            endpointObject.addProperty("voting", rootPath + basePath + "/voting");
        if(EPMCAPI.getInstance().endpointAvailable("siegewar"))
            endpointObject.addProperty("sieges", rootPath + basePath + "/sieges");
        serverObject.add("endpoints", endpointObject);

        return serverObject;
    }
}
