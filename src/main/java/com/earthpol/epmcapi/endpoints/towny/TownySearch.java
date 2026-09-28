package com.earthpol.epmcapi.endpoints.towny;

import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.networking.GameThread;
import com.earthpol.epmcapi.networking.RequestParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Identifiable;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownyObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Filtering and sorting happen before detail serialization, on the game thread. */
public final class TownySearch {
    public static final List<String> TOWN_FILTERS = List.of("name", "nation", "mayor", "isOpen", "isPublic", "isForSale", "minResidents", "maxResidents");
    public static final List<String> NATION_FILTERS = List.of("name", "capital", "king", "minResidents", "maxResidents", "minTowns", "maxTowns");
    public static final List<String> SORTS = List.of("name", "population", "registered");

    private TownySearch() {}

    public static String towns(RequestParser.SortedQuery query, TownsEndpoint endpoint) {
        JsonObject filter = filter(query, TOWN_FILTERS);
        Predicate<Town> matches = filter == null ? null : townFilter(filter);
        return search(query, endpoint, () -> TownyAPI.getInstance().getTowns(), matches, Town::getNumResidents);
    }

    public static String nations(RequestParser.SortedQuery query, NationsEndpoint endpoint) {
        JsonObject filter = filter(query, NATION_FILTERS);
        Predicate<Nation> matches = filter == null ? null : nationFilter(filter);
        return search(query, endpoint, () -> TownyAPI.getInstance().getNations(), matches, Nation::getNumResidents);
    }

    private static JsonObject filter(RequestParser.SortedQuery query, List<String> supported) {
        JsonElement value = query.request().query();
        if (value.isJsonArray()) return null;
        if (!value.isJsonObject()) throw new BadRequestResponse("'query' must be an array or object");
        JsonObject filter = value.getAsJsonObject();
        for (String key : filter.keySet()) {
            if (!supported.contains(key)) throw new BadRequestResponse("Unsupported filter: " + key);
        }
        // An empty filter supports requests such as the largest 25 towns.
        return filter;
    }

    private static <T extends Government> String search(RequestParser.SortedQuery query, PostEndpoint<T> endpoint,
            Supplier<? extends Collection<T>> all, Predicate<T> filter, ToIntFunction<T> population) {
        return GameThread.read(() -> {
            List<T> matches = new ArrayList<>();
            if (filter != null) {
                for (T value : all.get()) if (filter.test(value)) matches.add(value);
            } else {
                for (JsonElement identifier : query.request().query().getAsJsonArray()) {
                    T value = endpoint.getObjectOrNull(identifier);
                    if (value != null) matches.add(value);
                }
            }
            var options = query.request().options();
            if (query.sort() != null) {
                Comparator<T> comparator = switch (query.sort()) {
                    case "name" -> Comparator.comparing(Government::getName, String.CASE_INSENSITIVE_ORDER);
                    case "population" -> Comparator.comparingInt(population);
                    case "registered" -> Comparator.comparingLong(Government::getRegistered);
                    default -> throw new BadRequestResponse("Unsupported sort field");
                };
                if (query.descending()) comparator = comparator.reversed();
                matches.sort(comparator.thenComparing(Government::getUUID));
            } else if (filter != null && options.paginated()) {
                matches.sort(Comparator.comparing(Government::getUUID));
            }
            JsonArray results = new JsonArray();
            for (T value : options.page(matches)) results.add(endpoint.getJsonElement(value, options.fields()));
            return options.response(results, matches.size());
        });
    }

    private static Predicate<Town> townFilter(JsonObject filter) {
        String name = string(filter, "name"), nation = string(filter, "nation"), mayor = string(filter, "mayor");
        String searchName = name == null ? null : name.toLowerCase(Locale.ROOT);
        Boolean open = bool(filter, "isOpen"), publicTown = bool(filter, "isPublic"), forSale = bool(filter, "isForSale");
        Range residents = range(filter, "minResidents", "maxResidents");
        return town -> (searchName == null || town.getName().toLowerCase(Locale.ROOT).contains(searchName))
                && (open == null || open == town.isOpen())
                && (publicTown == null || publicTown == town.isPublic())
                && (forSale == null || forSale == town.isForSale())
                && identity(nation, town.getNationOrNull()) && identity(mayor, town.getMayor())
                && residents.matches(town.getNumResidents());
    }

    private static Predicate<Nation> nationFilter(JsonObject filter) {
        String name = string(filter, "name"), capital = string(filter, "capital"), king = string(filter, "king");
        String searchName = name == null ? null : name.toLowerCase(Locale.ROOT);
        Range residents = range(filter, "minResidents", "maxResidents");
        Range towns = range(filter, "minTowns", "maxTowns");
        return nation -> (searchName == null || nation.getName().toLowerCase(Locale.ROOT).contains(searchName))
                && identity(capital, nation.getCapital()) && identity(king, nation.getKing())
                && residents.matches(nation.getNumResidents()) && towns.matches(nation.getNumTowns());
    }

    private static boolean identity(String expected, TownyObject actual) {
        if (expected == null) return true;
        if (actual == null) return false;
        return expected.equalsIgnoreCase(actual.getName())
                || actual instanceof Identifiable identified && identified.getUUID() != null
                && expected.equalsIgnoreCase(identified.getUUID().toString());
    }

    private static String string(JsonObject filter, String key) {
        if (!filter.has(key)) return null;
        JsonElement value = filter.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank() || value.getAsString().length() > 200)
            throw new BadRequestResponse("'" + key + "' must be a string of 1 to 200 characters");
        return value.getAsString();
    }

    private static Boolean bool(JsonObject filter, String key) {
        if (!filter.has(key)) return null;
        JsonElement value = filter.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new BadRequestResponse("'" + key + "' must be a boolean");
        return value.getAsBoolean();
    }

    private record Range(int min, int max) {
        boolean matches(int value) { return value >= min && value <= max; }
    }

    private static Range range(JsonObject filter, String minimum, String maximum) {
        int min = integer(filter, minimum, 0), max = integer(filter, maximum, Integer.MAX_VALUE);
        if (min > max) throw new BadRequestResponse("'" + minimum + "' cannot exceed '" + maximum + "'");
        return new Range(min, max);
    }

    private static int integer(JsonObject filter, String key, int fallback) {
        if (!filter.has(key)) return fallback;
        JsonElement value = filter.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsString().matches("[0-9]+")) {
            try { return Integer.parseInt(value.getAsString()); }
            catch (NumberFormatException ignored) { }
        }
        throw new BadRequestResponse("'" + key + "' must be an integer between 0 and " + Integer.MAX_VALUE);
    }
}
