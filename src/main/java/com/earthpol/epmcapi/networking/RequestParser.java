package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.google.gson.*;

import java.util.List;

public final class RequestParser {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final List<String> QUERY_FIELDS = List.of("query");
    private static final List<String> PAGED_FIELDS = List.of("query", "limit", "offset", "fields");
    private static final List<String> SORTED_FIELDS = List.of("query", "limit", "offset", "fields", "sort", "order");
    private RequestParser() {}

    public static JsonElement query(String body, int maxItems) {
        return query(body(body, QUERY_FIELDS), maxItems);
    }

    public record Query(JsonElement query, QueryOptions options) {}

    public record SortedQuery(Query request, String sort, boolean descending) {}

    public static SortedQuery sorted(String body, int maxItems, int maxPageSize, List<String> supportedSorts) {
        JsonObject root = body(body, SORTED_FIELDS);
        String sort = optionString(root, "sort");
        String order = optionString(root, "order");
        if (sort != null && !supportedSorts.contains(sort))
            throw new BadRequestResponse("Unsupported 'sort' value '" + sort + "'; supported values: " + String.join(", ", supportedSorts));
        if (order != null && sort == null) throw new BadRequestResponse("'order' requires 'sort'");
        if (order != null && !order.equals("asc") && !order.equals("desc"))
            throw new BadRequestResponse("'order' must be 'asc' or 'desc'");
        return new SortedQuery(new Query(query(root, maxItems), QueryOptions.parse(root, maxPageSize)), sort, "desc".equals(order));
    }

    private static String optionString(JsonObject root, String key) {
        if (!root.has(key)) return null;
        JsonElement value = root.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new BadRequestResponse("'" + key + "' must be a string");
        return value.getAsString();
    }

    public static Query parse(String body, int maxItems, int maxPageSize) {
        JsonObject root = body(body, PAGED_FIELDS);
        return new Query(query(root, maxItems), QueryOptions.parse(root, maxPageSize));
    }

    private static JsonObject body(String body, List<String> supportedFields) {
        JsonElement root;
        try { root = GSON.fromJson(body, JsonElement.class); }
        catch (JsonParseException e) { throw new BadRequestResponse("Invalid JSON body"); }
        if (root == null || !root.isJsonObject()) throw new BadRequestResponse("Request body must be an object");
        JsonObject request = root.getAsJsonObject();
        for (String field : request.keySet()) {
            if (!supportedFields.contains(field))
                throw new BadRequestResponse("Unsupported request field '" + field + "'; allowed fields: " + String.join(", ", supportedFields));
        }
        return request;
    }

    private static JsonElement query(JsonObject root, int maxItems) {
        JsonElement query = root.get("query");
        if (query == null || query.isJsonNull()) throw new BadRequestResponse("Missing 'query' field");
        if (query.isJsonArray() && query.getAsJsonArray().size() > maxItems)
            throw new BadRequestResponse("Query exceeds maximum of " + maxItems + " items");
        return query;
    }

    public static JsonArray array(JsonElement query) {
        if (!query.isJsonArray()) throw new BadRequestResponse("'query' must be an array");
        return query.getAsJsonArray();
    }
}
