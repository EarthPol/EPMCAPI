package com.earthpol.epmcapi.endpoints.siegewar;

import com.earthpol.epmcapi.endpoints.GetEndpoint;
import com.earthpol.epmcapi.integration.SiegeWarIntegration;
import com.earthpol.epmcapi.networking.GameThread;
import com.google.gson.JsonObject;

public class SiegeEndpoint extends GetEndpoint {
    @Override
    public String lookup() {
        return getJsonElement().toString();
    }

    @Override
    public JsonObject getJsonElement() {
        return GameThread.read(SiegeWarIntegration::snapshot);
    }
}
