package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.networking.QueryOptions;
import com.earthpol.epmcapi.networking.RequestParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Searches detached shop snapshots on HTTP workers without reading game objects. */
public record ShopSearch(QueryOptions options, Filter filter, String sort, boolean descending) {
    public static final List<String> FILTERS = List.of("owner", "world", "material", "name", "enchantment", "minEnchantmentLevel",
            "enchantmentSource", "type", "minPrice", "maxPrice", "inStock", "currency", "defaultCurrency");
    public static final List<String> SORTS = List.of("price", "unitPrice");

    public static ShopSearch parse(RequestParser.SortedQuery query) {
        if (!query.request().query().isJsonObject()) throw new BadRequestResponse("'query' must be an array or object");
        JsonObject values = query.request().query().getAsJsonObject();
        for (String key : values.keySet()) {
            if (!FILTERS.contains(key)) throw new BadRequestResponse("Unsupported shop filter: " + key);
        }
        String owner = filterString(values, "owner"), world = filterString(values, "world");
        if (owner != null) {
            try {
                if (!UUID.fromString(owner).toString().equalsIgnoreCase(owner)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new BadRequestResponse("'owner' must be a UUID"); }
        }
        String material = key(values, "material"), name = filterString(values, "name"), enchantment = key(values, "enchantment");
        String source = choice(values, "enchantmentSource", List.of("any", "applied", "stored"));
        int level = positiveInteger(values, "minEnchantmentLevel", 1);
        if (enchantment == null && (source != null || values.has("minEnchantmentLevel")))
            throw new BadRequestResponse("Enchantment level and source require 'enchantment'");
        String type = choice(values, "type", List.of("buying", "selling", "frozen"));
        Double minPrice = price(values, "minPrice"), maxPrice = price(values, "maxPrice");
        if (minPrice != null && maxPrice != null && minPrice > maxPrice)
            throw new BadRequestResponse("'minPrice' cannot exceed 'maxPrice'");
        Boolean inStock = bool(values, "inStock"), defaultCurrency = bool(values, "defaultCurrency");
        String currency = filterString(values, "currency");
        if (currency != null && defaultCurrency != null)
            throw new BadRequestResponse("Use either 'currency' or 'defaultCurrency'");
        Filter filter = new Filter(owner, world, material, name == null ? null : name.toLowerCase(Locale.ROOT),
                enchantment, level, source == null ? "any" : source, type, minPrice, maxPrice, inStock, currency, defaultCurrency);
        return new ShopSearch(query.request().options(), filter, query.sort(), query.descending());
    }

    public String response(String snapshot) {
        if (!options.paginated() && options.fields().isAll() && sort == null && filter.isEmpty()) return snapshot;
        List<JsonObject> matches = new ArrayList<>();
        for (JsonElement value : JsonParser.parseString(snapshot).getAsJsonArray()) {
            JsonObject shop = value.getAsJsonObject();
            if (filter.matches(shop)) matches.add(shop);
        }
        if (sort != null) {
            Set<String> currencies = new HashSet<>();
            for (JsonObject shop : matches) currencies.add(string(shop, "currency"));
            if (currencies.size() > 1)
                throw new BadRequestResponse("Price sorting requires one currency; set 'currency' or 'defaultCurrency'");
            Comparator<Double> prices = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
            matches.sort(Comparator.<JsonObject, Double>comparing(shop -> number(shop, sort), Comparator.nullsLast(prices))
                    .thenComparingLong(shop -> shop.get("id").getAsLong()));
        } else if (options.paginated()) {
            matches.sort(Comparator.comparingLong(shop -> shop.get("id").getAsLong()));
        }
        JsonArray result = new JsonArray();
        for (JsonObject shop : options.page(matches)) result.add(options.fields().project(shop));
        return options.response(result, matches.size());
    }

    public record Filter(String owner, String world, String material, String name, String enchantment, int level,
                         String source, String type, Double minPrice, Double maxPrice, Boolean inStock,
                         String currency, Boolean defaultCurrency) {
        boolean isEmpty() {
            return owner == null && world == null && material == null && name == null && enchantment == null
                    && type == null && minPrice == null && maxPrice == null && inStock == null && currency == null && defaultCurrency == null;
        }

        boolean matches(JsonObject shop) {
            if (owner != null && !owner.equalsIgnoreCase(string(shop, "owner"))) return false;
            if (world != null && !world.equals(string(object(shop, "location"), "world"))) return false;
            if (type != null && !type.equalsIgnoreCase(string(shop, "type"))) return false;
            if (currency != null && !currency.equals(string(shop, "currency"))) return false;
            if (defaultCurrency != null && defaultCurrency != (string(shop, "currency") == null)) return false;
            if (inStock != null && inStock != shop.get("inStock").getAsBoolean()) return false;
            if (minPrice != null || maxPrice != null) {
                Double price = number(shop, "price");
                if (price == null || minPrice != null && price < minPrice || maxPrice != null && price > maxPrice) return false;
            }
            JsonObject item = object(shop, "item");
            if (material != null && !material.equals(string(item, "material"))) return false;
            if (name != null) {
                String displayName = string(item, "displayName");
                if (displayName == null || !displayName.toLowerCase(Locale.ROOT).contains(name)) return false;
            }
            return enchantment == null || item != null
                    && (!source.equals("stored") && enchanted(item, "enchantments", enchantment, level)
                    || !source.equals("applied") && enchanted(item, "storedEnchantments", enchantment, level));
        }
    }

    private static boolean enchanted(JsonObject item, String field, String enchantment, int minimum) {
        JsonElement entries = item.get(field);
        if (entries == null || !entries.isJsonArray()) return false;
        for (JsonElement value : entries.getAsJsonArray()) {
            JsonObject entry = value.getAsJsonObject();
            if (enchantment.equals(string(entry, "id")) && entry.get("level").getAsInt() >= minimum) return true;
        }
        return false;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static Double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        double number = value.getAsDouble();
        return Double.isFinite(number) ? number : null;
    }

    private static String filterString(JsonObject values, String key) {
        if (!values.has(key)) return null;
        JsonElement value = values.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank() || value.getAsString().length() > 200)
            throw new BadRequestResponse("'" + key + "' must be a string of 1 to 200 characters");
        return value.getAsString();
    }

    private static String key(JsonObject values, String name) {
        String value = filterString(values, name);
        if (value == null) return null;
        value = value.toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value;
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new BadRequestResponse("Invalid '" + name + "' identifier");
        return value;
    }

    private static String choice(JsonObject values, String key, List<String> choices) {
        String value = filterString(values, key);
        if (value == null) return null;
        value = value.toLowerCase(Locale.ROOT);
        if (!choices.contains(value)) throw new BadRequestResponse("'" + key + "' must be one of " + String.join(", ", choices));
        return value;
    }

    private static Boolean bool(JsonObject values, String key) {
        if (!values.has(key)) return null;
        JsonElement value = values.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new BadRequestResponse("'" + key + "' must be a boolean");
        return value.getAsBoolean();
    }

    private static int positiveInteger(JsonObject values, String key, int fallback) {
        if (!values.has(key)) return fallback;
        JsonElement value = values.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsString().matches("[0-9]{1,10}")) {
            try {
                int number = Integer.parseInt(value.getAsString());
                if (number > 0) return number;
            } catch (NumberFormatException ignored) { }
        }
        throw new BadRequestResponse("'" + key + "' must be a positive integer");
    }

    private static Double price(JsonObject values, String key) {
        if (!values.has(key)) return null;
        JsonElement value = values.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double price = value.getAsDouble();
            if (Double.isFinite(price) && price >= 0) return price;
        }
        throw new BadRequestResponse("'" + key + "' must be a non-negative finite price");
    }
}
