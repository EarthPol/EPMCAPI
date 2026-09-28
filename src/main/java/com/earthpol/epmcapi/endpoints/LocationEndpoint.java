package com.earthpol.epmcapi.endpoints;

import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.JSONUtil;

public class LocationEndpoint extends PostEndpoint<LocationEndpoint.Coordinates> {

    public record Coordinates(int x, int z) {}

    @Override
    public LocationEndpoint.Coordinates getObjectOrNull(JsonElement element) {
        JsonArray jsonArray = JSONUtil.getJsonElementAsJsonArrayOrNull(element);
        if (jsonArray == null) throw new BadRequestResponse("Your query contains a value that is not in a JSON array.");

        if (jsonArray.size() != 2) throw new BadRequestResponse("Coordinates must contain exactly two integers");

        int x;
        int z;
        try {
            JsonElement xElement = jsonArray.get(0);
            JsonElement zElement = jsonArray.get(1);

            Integer xInner = JSONUtil.getJsonElementAsIntegerOrNull(xElement);
            Integer zInner = JSONUtil.getJsonElementAsIntegerOrNull(zElement);
            if (xInner == null || zInner == null) throw new BadRequestResponse("Your query contained a value that was not an int.");

            x = xInner;
            z = zInner;
        } catch (IndexOutOfBoundsException oobe) {
            throw new BadRequestResponse("Your query did not contain two values.");
        }

        return new Coordinates(x, z);
    }

    @Override
    public JsonElement getJsonElement(LocationEndpoint.Coordinates pair) {
        int x = pair.x();
        int z = pair.z();

        Location location = new Location(Bukkit.getWorlds().get(0), x, 0, z);
        TownyAPI townyAPI = TownyAPI.getInstance();
        Town town = townyAPI.getTown(location);

        JsonObject jsonObject = new JsonObject();
        JsonObject locationObject = new JsonObject();
        locationObject.addProperty("x", x);
        locationObject.addProperty("z", z);
        jsonObject.add("location", locationObject);

        jsonObject.addProperty("isWilderness", townyAPI.isWilderness(location));

        jsonObject.add("town", EndpointUtils.getTownJsonObject(town));
        jsonObject.add("nation", EndpointUtils.getNationJsonObject(town == null ? null : town.getNationOrNull()));

        return jsonObject;
    }
}