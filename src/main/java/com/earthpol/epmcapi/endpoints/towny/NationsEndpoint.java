package com.earthpol.epmcapi.endpoints.towny;

import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.networking.FieldSelection;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.metadata.StringDataField;
import com.palmergames.bukkit.towny.permissions.TownyPerms;

import java.util.UUID;

public class NationsEndpoint extends PostEndpoint<Nation> {

    private static final String DISCORD_INVITE_URL_KEY = "discord_invite_url";
    private static final String DISCORD_INVITE_URL_LABEL = "Discord Invite URL";
    private static final StringDataField discordInviteURLField = new StringDataField(DISCORD_INVITE_URL_KEY, "", DISCORD_INVITE_URL_LABEL);

    @Override
    public Nation getObjectOrNull(JsonElement element) {
        String string = JSONUtil.getJsonElementAsStringOrNull(element);
        if (string == null) throw new BadRequestResponse("Your query contains a value that is not a string");

        Nation nation;
        try {
            nation = TownyAPI.getInstance().getNation(UUID.fromString(string));
        } catch (IllegalArgumentException e) {
            nation = TownyAPI.getInstance().getNation(string);
        }

        return nation;
    }

    @Override
    public JsonElement getJsonElement(Nation nation) {
        return getJsonElement(nation, FieldSelection.all());
    }

    @Override
    public JsonElement getJsonElement(Nation nation, FieldSelection fields) {
        JsonObject nationObject = new JsonObject();

        nationObject.addProperty("name", nation.getName());
        nationObject.addProperty("uuid", nation.getUUID().toString());
        nationObject.addProperty("board", nation.getBoard().isEmpty() ? null : nation.getBoard());

        nationObject.add("king", EndpointUtils.getResidentJsonObject(nation.getKing()));
        nationObject.add("capital", EndpointUtils.getTownJsonObject(nation.getCapital()));

        JsonObject timestampsObject = new JsonObject();
        timestampsObject.addProperty("registered", nation.getRegistered());
        nationObject.add("timestamps", timestampsObject);

        JsonObject statusObject = new JsonObject();
        statusObject.addProperty("isPublic", nation.isPublic());
        statusObject.addProperty("isOpen", nation.isOpen());
        statusObject.addProperty("isNeutral", nation.isNeutral());
        nationObject.add("status", statusObject);

        JsonObject statsObject = new JsonObject();
        statsObject.addProperty("numTownBlocks", nation.getNumTownblocks());
        statsObject.addProperty("numResidents", nation.getNumResidents());
        statsObject.addProperty("numTowns", nation.getNumTowns());
        statsObject.addProperty("numAllies", nation.getAllies().size());
        statsObject.addProperty("numEnemies", nation.getEnemies().size());
        if (fields.includes("stats.balance")) statsObject.addProperty("balance", TownyEconomyHandler.isActive() ? nation.getAccount().getHoldingBalance() : 0);
        nationObject.add("stats", statsObject);

        if (fields.includes("coordinates")) nationObject.add("coordinates", EndpointUtils.getCoordinatesObject(nation.getSpawnOrNull()));
        if (fields.includes("residents")) nationObject.add("residents", EndpointUtils.getResidentArray(nation.getResidents()));
        if (fields.includes("towns")) nationObject.add("towns", EndpointUtils.getTownArray(nation.getTowns()));
        if (fields.includes("allies")) nationObject.add("allies", EndpointUtils.getNationArray(nation.getAllies()));
        if (fields.includes("enemies")) nationObject.add("enemies", EndpointUtils.getNationArray(nation.getEnemies()));
        if (fields.includes("sanctioned")) nationObject.add("sanctioned", EndpointUtils.getTownArray(nation.getSanctionedTowns()));


        StringDataField inviteField = (StringDataField) nation.getMetadata(discordInviteURLField.getKey());
        if (inviteField != null && !inviteField.getValue().isBlank()) {
            nationObject.addProperty("discord", inviteField.getValue());
        }

        if (fields.includes("ranks")) {
            JsonObject ranksObject = new JsonObject();
            for (String rank : TownyPerms.getNationRanks()) {
                if (fields.includes("ranks." + rank)) ranksObject.add(rank, EndpointUtils.getResidentArray(EndpointUtils.getNationRank(nation, rank)));
            }
            nationObject.add("ranks", ranksObject);
        }

        return fields.project(nationObject);
    }
}
