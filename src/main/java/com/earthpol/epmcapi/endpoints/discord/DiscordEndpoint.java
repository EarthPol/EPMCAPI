package com.earthpol.epmcapi.endpoints.discord;

import com.earthpol.epmcapi.db.DiscordDatabase;
import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

public class DiscordEndpoint extends PostEndpoint<DiscordLink> {

    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d+$");

    @Override
    public DiscordLink getObjectOrNull(JsonElement element) {
        String input = JSONUtil.getJsonElementAsStringOrNull(element);
        if (input == null) {
            throw new BadRequestResponse("Query value must be a string");
        }

        boolean isDiscordId = DISCORD_ID_PATTERN.matcher(input).matches();
        if (!isDiscordId) {
            // validate UUID format
            try {
                UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                throw new BadRequestResponse("Invalid UUID format.");
            }
        }

        String sql = isDiscordId
                ? "SELECT discord, uuid FROM discord_accounts WHERE discord = ?"
                : "SELECT discord, uuid FROM discord_accounts WHERE uuid = ?";

        try (Connection conn = DiscordDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(com.earthpol.epmcapi.EPMCAPI.getInstance().getConfig().getInt("database.query_timeout_seconds", 5));
            ps.setString(1, input);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DiscordLink(
                            rs.getString("discord"),
                            rs.getString("uuid")
                    );
                }
            }
        } catch (SQLException ex) {
            throw new com.earthpol.epmcapi.exception.HttpResponseException(503, "Discord links temporarily unavailable");
        }

        return null;
    }

    @Override
    public JsonElement getJsonElement(DiscordLink link) {
        if (link == null) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "No linking found.");
            return error;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("discord", link.getDiscord());
        obj.addProperty("uuid", link.getUuid());
        return obj;
    }
}