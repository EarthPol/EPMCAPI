package com.earthpol.epmcapi.endpoints.towny;

import au.lupine.quarters.api.manager.QuarterManager;
import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.earthpol.epmcapi.integration.SiegeWarIntegration;
import com.earthpol.epmcapi.networking.FieldSelection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.permissions.TownyPerms;

import java.util.UUID;

public class TownsEndpoint extends PostEndpoint<Town> {

    @Override
    public Town getObjectOrNull(JsonElement element) {
        String identifier = JSONUtil.getJsonElementAsStringOrNull(element);
        if (identifier == null) {
            throw new BadRequestResponse("Your query contains a value that is not a string");
        }

        Town town;
        try {
            town = TownyAPI.getInstance().getTown(UUID.fromString(identifier));
        } catch (IllegalArgumentException e) {
            town = TownyAPI.getInstance().getTown(identifier);
        }
        return town;
    }

    @Override
    public JsonElement getJsonElement(Town town) {
        return getJsonElement(town, FieldSelection.all());
    }

    @Override
    public JsonElement getJsonElement(Town town, FieldSelection fields) {
        JsonObject townObject = new JsonObject();

        townObject.addProperty("name", town.getName());
        townObject.addProperty("uuid", town.getUUID().toString());
        townObject.addProperty("board", town.getBoard().isEmpty() ? null : town.getBoard());
        townObject.addProperty("founder", town.getFounder());

        townObject.add("mayor", EndpointUtils.getResidentJsonObject(town.getMayor()));
        townObject.add("nation", EndpointUtils.getNationJsonObject(town.getNationOrNull()));

        JsonObject timestampsObject = new JsonObject();
        timestampsObject.addProperty("registered", town.getRegistered());
        timestampsObject.addProperty("joinedNationAt", town.hasNation() ? town.getJoinedNationAt() : null);
        timestampsObject.addProperty("ruinedAt", town.isRuined() ? town.getRuinedTime() : null);
        townObject.add("timestamps", timestampsObject);

        JsonObject statusObject = new JsonObject();
        statusObject.addProperty("isPublic", town.isPublic());
        statusObject.addProperty("isOpen", town.isOpen());
        if (fields.includes("status.isNeutral")) {
            if (EPMCAPI.getInstance().endpointAvailable("siegewar")) {
                statusObject.addProperty("isNeutral", SiegeWarIntegration.isTownPeaceful(town));
            } else {
                statusObject.addProperty("isNeutral", town.isNeutral());
            }
        }
        statusObject.addProperty("isCapital", town.isCapital());
        statusObject.addProperty("isOverClaimed", town.isOverClaimed());
        statusObject.addProperty("isRuined", town.isRuined());
        statusObject.addProperty("isForSale", town.isForSale());
        statusObject.addProperty("hasNation", town.hasNation());
        townObject.add("status", statusObject);

        JsonObject statsObject = new JsonObject();
        statsObject.addProperty("numTownBlocks", town.getNumTownBlocks());
        statsObject.addProperty("maxTownBlocks", town.getMaxTownBlocks());
        statsObject.addProperty("bonusBlocks", town.getBonusBlocks());
        statsObject.addProperty("numResidents", town.getNumResidents());
        statsObject.addProperty("numTrusted", town.getTrustedResidents().size());
        statsObject.addProperty("numOutlaws", town.getOutlaws().size());
        if (fields.includes("stats.balance")) statsObject.addProperty("balance", TownyEconomyHandler.isActive() ? town.getAccount().getHoldingBalance() : 0);
        statsObject.addProperty("forSalePrice", !town.isForSale() ? null : town.getForSalePrice());
        if(town.isForSale()) {
            statsObject.addProperty("forSaleTime", town.getForSaleTime());
        } else {
            statsObject.addProperty("forSaleTime", 0);
        }
        townObject.add("stats", statsObject);

        if (fields.includes("perms")) townObject.add("perms", EndpointUtils.getPermsObject(town.getPermissions()));

        JsonObject coordinatesObject = EndpointUtils.getCoordinatesObject(town.getSpawnOrNull());
        JsonArray homeBlockArray = new JsonArray();
        TownBlock homeBlock = town.getHomeBlockOrNull();
        homeBlockArray.add(homeBlock == null ? null : homeBlock.getX());
        homeBlockArray.add(homeBlock == null ? null : homeBlock.getZ());
        coordinatesObject.add("homeBlock", homeBlockArray);

        if (fields.includes("coordinates.townBlocks")) {
            JsonArray townBlocksArray = new JsonArray();
            for (TownBlock townBlock : town.getTownBlocks()) {
                JsonArray townBlockArray = new JsonArray();
                townBlockArray.add(townBlock.getX());
                townBlockArray.add(townBlock.getZ());

                townBlocksArray.add(townBlockArray);
            }
            coordinatesObject.add("townBlocks", townBlocksArray);
        }

        townObject.add("coordinates", coordinatesObject);

        if (fields.includes("residents")) townObject.add("residents", EndpointUtils.getResidentArray(town.getResidents()));
        if (fields.includes("trusted")) townObject.add("trusted", EndpointUtils.getResidentArray(town.getTrustedResidents().stream().toList()));
        if (fields.includes("outlaws")) townObject.add("outlaws", EndpointUtils.getResidentArray(town.getOutlaws().stream().toList()));

        if (fields.includes("quarters")) {
            JsonArray quartersArray = EPMCAPI.getInstance().hasPlugin("Quarters") ? EndpointUtils.getQuarterArray(QuarterManager.getInstance().getQuarters(town)) : new JsonArray();
            townObject.add("quarters", quartersArray);
        }

        if (fields.includes("ranks")) {
            JsonObject ranksObject = new JsonObject();
            for (String rank : TownyPerms.getTownRanks()) {
                if (fields.includes("ranks." + rank)) ranksObject.add(rank, EndpointUtils.getResidentArray(town.getRank(rank)));
            }
            townObject.add("ranks", ranksObject);
        }

        return fields.project(townObject);
    }
}
