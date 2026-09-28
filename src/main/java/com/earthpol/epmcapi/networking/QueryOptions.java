package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.google.gson.*;

import java.util.List;

public record QueryOptions(FieldSelection fields, boolean paginated, int offset, int limit) {
    public static QueryOptions parse(JsonObject request, int maxPageSize) {
        boolean paginated = request.has("offset") || request.has("limit");
        int offset = integer(request, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = integer(request, "limit", maxPageSize, 1, maxPageSize);
        return new QueryOptions(FieldSelection.parse(request.get("fields")), paginated, offset, limit);
    }

    private static int integer(JsonObject request, String key, int fallback, int min, int max) {
        if (!request.has(key)) return fallback;
        JsonElement value = request.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && value.getAsString().matches("[0-9]+")) {
            try {
                int number = Integer.parseInt(value.getAsString());
                if (number >= min && number <= max) return number;
            } catch (NumberFormatException ignored) { }
        }
        throw new BadRequestResponse("'" + key + "' must be an integer between " + min + " and " + max);
    }

    public <T> List<T> page(List<T> values) {
        if (!paginated) return values;
        int start = Math.min(offset, values.size());
        int end = (int) Math.min((long) start + limit, values.size());
        return values.subList(start, end);
    }

    /** Data has already been paged and projected; total is the count before paging. */
    public String response(JsonArray data, int total) {
        if (!paginated) return data.toString();
        JsonObject pagination = new JsonObject();
        boolean hasMore = (long) offset + limit < total;
        pagination.addProperty("offset", offset);
        pagination.addProperty("limit", limit);
        pagination.addProperty("total", total);
        pagination.addProperty("hasMore", hasMore);
        pagination.addProperty("nextOffset", hasMore ? (long) offset + limit : null);
        JsonObject result = new JsonObject();
        result.add("data", data);
        result.add("pagination", pagination);
        return result.toString();
    }
}
