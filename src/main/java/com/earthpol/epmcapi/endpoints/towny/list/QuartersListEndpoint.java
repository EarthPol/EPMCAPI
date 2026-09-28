package com.earthpol.epmcapi.endpoints.towny.list;

import au.lupine.quarters.api.manager.QuarterManager;
import com.earthpol.epmcapi.endpoints.GetEndpoint;
import com.earthpol.epmcapi.utils.EndpointCacheSettings;
import com.google.gson.JsonArray;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.ResponseSnapshot;

public class QuartersListEndpoint extends GetEndpoint {
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
        return EndpointUtils.getQuarterArray(QuarterManager.getInstance().getAllQuarters());
    }
}
