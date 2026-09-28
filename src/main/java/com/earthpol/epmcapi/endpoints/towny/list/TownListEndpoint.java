package com.earthpol.epmcapi.endpoints.towny.list;

import com.earthpol.epmcapi.endpoints.GetEndpoint;
import com.earthpol.epmcapi.utils.EndpointCacheSettings;
import com.earthpol.epmcapi.utils.ResponseSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;

import java.util.Collection;

public class TownListEndpoint extends GetEndpoint {
    private final ResponseSnapshot lookupSnapshot = new ResponseSnapshot(EndpointCacheSettings.getListSnapshotTtlMs());

    /**
     * Returns a JSON array (as a string) containing all towns,
     * where each town object has the properties "name" and "uuid".
     */
    @Override
    public String lookup() {
        return lookupSnapshot.get(this::buildLookupResponse);
    }

    private String buildLookupResponse() {
        Collection<Town> towns = TownyAPI.getInstance().getTowns();
        JsonArray array = new JsonArray();

        for (Town town : towns) {
            JsonObject townObj = new JsonObject();
            townObj.addProperty("name", town.getName());
            townObj.addProperty("uuid", town.getUUID().toString());
            array.add(townObj);
        }

        return array.toString();
    }

    @Override
    public JsonObject getJsonElement() {
        Collection<Town> towns = TownyAPI.getInstance().getTowns();
        JsonArray array = new JsonArray();

        for (Town town : towns) {
            JsonObject townObj = new JsonObject();
            townObj.addProperty("name", town.getName());
            townObj.addProperty("uuid", town.getUUID().toString());
            array.add(townObj);
        }

        JsonObject wrapper = new JsonObject();
        wrapper.add("towns", array);
        return wrapper;
    }
}
