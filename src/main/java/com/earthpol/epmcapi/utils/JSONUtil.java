package com.earthpol.epmcapi.utils;

import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;

public class JSONUtil {

    public static JsonObject getJsonObjectFromString(String string) {
        try {
            return JsonParser.parseString(string).getAsJsonObject();
        } catch (Exception e) {
            throw new BadRequestResponse("Invalid JSON body provided");
        }
    }

    public static String getJsonElementAsStringOrNull(JsonElement element) {
        if (element == null) return null;

        if (!element.isJsonPrimitive()) return null;

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) return null;

        return primitive.getAsString();
    }

    public static Integer getJsonElementAsIntegerOrNull(JsonElement element) {
        if (element == null) return null;

        if (!element.isJsonPrimitive()) return null;

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) return null;

        try { return primitive.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) {
            throw new BadRequestResponse("Expected an integer in the signed 32-bit range");
        }
    }

    public static JsonArray getJsonElementAsJsonArrayOrNull(JsonElement element) {
        if (element == null) return null;

        if (!element.isJsonArray()) return null;
        return element.getAsJsonArray();
    }

    public static JsonObject getJsonElementAsJsonObjectOrNull(JsonElement element) {
        if (element == null) return null;

        if (!element.isJsonObject()) return null;
        return element.getAsJsonObject();
    }

    public static List<String> getStringListOrNull(JsonObject obj, String memberName) {
        if (obj == null || !obj.has(memberName)) return null;
        JsonElement elem = obj.get(memberName);
        if (elem == null || elem.isJsonNull()) return null;
        if (!elem.isJsonArray()) {
            throw new BadRequestResponse("Field '" + memberName + "' must be a JSON array of strings");
        }
        JsonArray array = elem.getAsJsonArray();
        List<String> list = new ArrayList<>();
        for (JsonElement item : array) {
            String value = getJsonElementAsStringOrNull(item);
            if (value == null) {
                throw new BadRequestResponse("Field '" + memberName + "' must contain only string values");
            }
            list.add(value);
        }
        return list;
    }
}