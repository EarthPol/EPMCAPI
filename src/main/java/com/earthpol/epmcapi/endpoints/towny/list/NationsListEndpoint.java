package com.earthpol.epmcapi.endpoints.towny.list;

import com.earthpol.epmcapi.endpoints.GetEndpoint;
import com.earthpol.epmcapi.utils.EndpointCacheSettings;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.ResponseSnapshot;
import com.google.gson.JsonArray;
import com.palmergames.bukkit.towny.TownyAPI;

public class NationsListEndpoint extends GetEndpoint {
    private final ResponseSnapshot lookupSnapshot = new ResponseSnapshot(EndpointCacheSettings.getListSnapshotTtlMs());

    @Override
    public String lookup() {
        return lookupSnapshot.get(this::buildLookupResponse);
    }

    private String buildLookupResponse() {
        return getJsonElement().toString();
    }

    @Override
    public JsonArray getJsonElement() {
        return EndpointUtils.getNationArray(TownyAPI.getInstance().getNations());
    }
}
