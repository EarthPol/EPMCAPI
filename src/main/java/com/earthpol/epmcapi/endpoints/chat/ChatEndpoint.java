package com.earthpol.epmcapi.endpoints.chat;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.db.Database;
import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ChatEndpoint extends PostEndpoint<Collection<ChatEntry>> {
    public static final List<String> FILTERS = List.of("uuid", "username", "nickname", "message", "startTimestamp", "endTimestamp");

    // Timestamp format
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Safety limits
    private static final int MAX_FILTER_ITEMS   = EPMCAPI.getInstance().getConfig().getInt("chat-endpoint.max-filter-items", 20);
    private static final int MAX_STRING_LENGTH = EPMCAPI.getInstance().getConfig().getInt("chat-endpoint.max-string-length", 200);

    @Override
    public Collection<ChatEntry> getObjectOrNull(JsonElement element) {
        // element is the inner "query" JsonObject
        JsonObject q = element.getAsJsonObject();
        for (String field : q.keySet()) {
            if (!FILTERS.contains(field)) throw new BadRequestResponse("Unsupported chat filter 'query." + field + "'");
            if (q.get(field).isJsonNull()) throw new BadRequestResponse("'query." + field + "' must not be null");
        }

        // 1) Parse filters
        List<String> uuids     = JSONUtil.getStringListOrNull(q, "uuid");
        List<String> usernames = JSONUtil.getStringListOrNull(q, "username");
        List<String> nicknames = JSONUtil.getStringListOrNull(q, "nickname");
        List<String> messages  = JSONUtil.getStringListOrNull(q, "message");
        String startTs         = timestampString(q, "startTimestamp");
        String endTs           = timestampString(q, "endTimestamp");

        // 2) Validate sizes & lengths
        validateList(uuids,     "uuid");
        validateList(usernames, "username");
        validateList(nicknames, "nickname");
        validateList(messages,  "message");
        validateTimestamp(startTs, "startTimestamp");
        validateTimestamp(endTs,   "endTimestamp");

        // 3) Build dynamic SQL + params
        StringBuilder sql = new StringBuilder(
                "SELECT entry_id, user_uuid, username, nickname, message, channel, timestamp " +
                        "FROM chat_logs WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();
        List<String> channels = EPMCAPI.getInstance().getConfig().getStringList("chat-endpoint.allowed-channels");
        if (channels.isEmpty()) throw new com.earthpol.epmcapi.exception.HttpResponseException(503, "No public chat channels configured");
        sql.append(" AND channel IN (").append(String.join(",", Collections.nCopies(channels.size(), "?"))).append(")");
        params.addAll(channels);

        // a) uuid OR username
        if (uuids != null || usernames != null) {
            sql.append(" AND (");
            boolean first = true;
            if (uuids != null) {
                for (String u : uuids) {
                    if (!first) sql.append(" OR ");
                    // validate UUID
                    try { UUID.fromString(u); }
                    catch (IllegalArgumentException ex) {
                        throw new BadRequestResponse("Invalid UUID: " + u);
                    }
                    sql.append("user_uuid = ?");
                    params.add(u);
                    first = false;
                }
            }
            if (usernames != null) {
                for (String name : usernames) {
                    if (!first) sql.append(" OR ");
                    sql.append("username = ?");
                    params.add(name);
                    first = false;
                }
            }
            sql.append(")");
        }

        // b) nickname IN (...)
        if (nicknames != null) {
            sql.append(" AND nickname IN (")
                    .append(String.join(",", Collections.nCopies(nicknames.size(), "?")))
                    .append(")");
            params.addAll(nicknames);
        }

        // c) message FULLTEXT search
        if (messages != null && !messages.isEmpty()) {
            sql.append(" AND MATCH(message) AGAINST(? IN BOOLEAN MODE)");
            params.add(buildFullTextQuery(messages));
        }

        // d) timestamp range
        if (startTs != null) {
            LocalDateTime s = parseTs(startTs, "startTimestamp");
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.valueOf(s));
        }
        if (endTs != null) {
            LocalDateTime e = parseTs(endTs, "endTimestamp");
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.valueOf(e));
        }

        // e) sort & limit
        sql.append(" ORDER BY timestamp DESC");
        int limit = EPMCAPI.getInstance().getConfig().getInt("chat-endpoint.max-results", 100);
        sql.append(" LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 1000)));

        // 4) Execute
        List<ChatEntry> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setQueryTimeout(EPMCAPI.getInstance().getConfig().getInt("database.query_timeout_seconds", 5));
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ChatEntry(
                            rs.getInt("entry_id"),
                            UUID.fromString(rs.getString("user_uuid")),
                            rs.getString("username"),
                            rs.getString("nickname"),
                            rs.getString("message"),
                            rs.getString("channel"),
                            rs.getTimestamp("timestamp").toInstant()
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new com.earthpol.epmcapi.exception.HttpResponseException(503, "Chat data temporarily unavailable");
        }

        return out;
    }

    private static String buildFullTextQuery(List<String> terms) {
        // prepend '+' and append '*' to each term for boolean prefix search
        return terms.stream()
                .map(t -> "+" + t.replaceAll("[^\\w]", "") + "*")
                .collect(Collectors.joining(" "));
    }

    @Override
    public JsonElement getJsonElement(Collection<ChatEntry> entries) {
        JsonArray arr = new JsonArray();
        for (ChatEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("entry_id", e.getEntryId());
            o.addProperty("user_uuid", e.getUserUuid().toString());
            o.addProperty("username", e.getUsername());
            o.addProperty("nickname", e.getNickname());
            o.addProperty("message", e.getMessage());
            o.addProperty("channel", e.getChannel());
            o.addProperty("timestamp", e.getTimestamp().toString());
            arr.add(o);
        }
        return arr;
    }

    // —— Helpers —— //

    private static String timestampString(JsonObject query, String field) {
        if (!query.has(field)) return null;
        String value = JSONUtil.getJsonElementAsStringOrNull(query.get(field));
        if (value == null) throw new BadRequestResponse("'query." + field + "' must be a string");
        return value;
    }

    private static void validateList(List<String> list, String field) {
        if (list == null) return;
        if (list.isEmpty()) throw new BadRequestResponse(field + " must not be an empty array");
        if (list.size() > MAX_FILTER_ITEMS) {
            throw new BadRequestResponse(
                    field + " array too large (max " + MAX_FILTER_ITEMS + ")"
            );
        }
        for (String s : list) {
            if (s.length() > MAX_STRING_LENGTH) {
                throw new BadRequestResponse(
                        field + " values must be <= " + MAX_STRING_LENGTH + " chars"
                );
            }
        }
    }

    private static void validateTimestamp(String ts, String field) {
        if (ts == null) return;
        try {
            LocalDateTime.parse(ts, TS_FMT);
        } catch (Exception e) {
            throw new BadRequestResponse(
                    field + " must be in format yyyy-MM-dd HH:mm:ss"
            );
        }
    }

    private static LocalDateTime parseTs(String raw, String field) {
        try {
            return LocalDateTime.parse(raw, TS_FMT);
        } catch (Exception ex) {
            throw new BadRequestResponse(
                    field + " must be yyyy-MM-dd HH:mm:ss"
            );
        }
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
