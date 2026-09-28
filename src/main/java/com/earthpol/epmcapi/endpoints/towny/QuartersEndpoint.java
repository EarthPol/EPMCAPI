package com.earthpol.epmcapi.endpoints.towny;

import au.lupine.quarters.api.manager.QuarterManager;
import au.lupine.quarters.object.entity.Cuboid;
import au.lupine.quarters.object.entity.Quarter;
import au.lupine.quarters.object.state.QuarterType;
import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.networking.FieldSelection;
import com.earthpol.epmcapi.networking.GameThread;
import com.earthpol.epmcapi.networking.QueryOptions;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.Location;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class QuartersEndpoint extends PostEndpoint<Quarter> {

    @Override
    public Quarter getObjectOrNull(JsonElement element) {
        String string = JSONUtil.getJsonElementAsStringOrNull(element);
        if (string == null) throw new BadRequestResponse("Your query contains a value that is not a string");

        UUID uuid;
        try {
            uuid = UUID.fromString(string);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return QuarterManager.getInstance().getQuarter(uuid);
    }

    @Override
    public JsonElement getJsonElement(Quarter quarter) {
        return getJsonElement(quarter, FieldSelection.all());
    }

    @Override
    public JsonElement getJsonElement(Quarter quarter, FieldSelection fields) {
        JsonObject quarterObject = new JsonObject();

        quarterObject.addProperty("name", quarter.getName());
        quarterObject.addProperty("uuid", quarter.getUUID().toString());
        quarterObject.addProperty("type", quarter.getType().toString());
        quarterObject.addProperty("creator", quarter.getCreator().toString());

        if (fields.includes("owner")) {
            JsonObject owner = EndpointUtils.getResidentJsonObject(ownerResident(quarter));
            owner.addProperty("uuid", quarter.getOwner() == null ? null : quarter.getOwner().toString());
            quarterObject.add("owner", owner);
        }
        if (fields.includes("town")) quarterObject.add("town", EndpointUtils.getTownJsonObject(quarter.getTown()));
        if (fields.includes("nation")) quarterObject.add("nation", EndpointUtils.getNationJsonObject(quarter.getNation()));

        JsonObject timestampsObject = new JsonObject();
        timestampsObject.addProperty("registered", quarter.getRegistered());
        timestampsObject.addProperty("claimedAt", quarter.getClaimedAt());
        quarterObject.add("timestamps", timestampsObject);

        JsonObject statusObject = new JsonObject();
        statusObject.addProperty("isEmbassy", quarter.isEmbassy());
        statusObject.addProperty("isForSale", quarter.isForSale());
        quarterObject.add("status", statusObject);

        JsonObject statsObject = new JsonObject();
        statsObject.addProperty("price", quarter.getPrice());
        if (fields.includes("stats.volume")) statsObject.addProperty("volume", quarter.getVolume());
        if (fields.includes("stats.numCuboids")) statsObject.addProperty("numCuboids", quarter.getCuboids().size());
        statsObject.addProperty("particleSize", quarter.getParticleSize());
        quarterObject.add("stats", statsObject);

        if (fields.includes("colour")) {
            JsonArray colourArray = new JsonArray();
            Color colour = quarter.getColour();
            colourArray.add(colour.getRed());
            colourArray.add(colour.getGreen());
            colourArray.add(colour.getBlue());
            colourArray.add(colour.getAlpha());
            quarterObject.add("colour", colourArray);
        }

        if (fields.includes("trusted")) quarterObject.add("trusted", EndpointUtils.getResidentArray(quarter.getTrustedResidents()));

        if (fields.includes("cuboids")) {
            JsonArray cuboidsArray = new JsonArray();
            for (Cuboid cuboid : quarter.getCuboids()) {
                cuboidsArray.add(getCuboidObject(cuboid, fields));
            }
            quarterObject.add("cuboids", cuboidsArray);
        }

        return fields.project(quarterObject);
    }

    private static JsonObject getCuboidObject(Cuboid cuboid, FieldSelection fields) {
        JsonObject cuboidObject = new JsonObject();
        if (fields.includes("cuboids.world"))
            cuboidObject.addProperty("world", cuboid.getWorld() == null ? null : cuboid.getWorld().getName());
        if (fields.includes("cuboids.cornerOne")) cuboidObject.add("cornerOne", coordinates(cuboid.getCornerOne()));
        if (fields.includes("cuboids.cornerTwo")) cuboidObject.add("cornerTwo", coordinates(cuboid.getCornerTwo()));
        return cuboidObject;
    }

    private static JsonArray coordinates(Location location) {
        JsonArray coordinates = new JsonArray();
        coordinates.add(location.getBlockX());
        coordinates.add(location.getBlockY());
        coordinates.add(location.getBlockZ());
        return coordinates;
    }

    private static Resident ownerResident(Quarter quarter) {
        // The pinned Quarters getOwnerResident() looks up the quarter UUID, not the owner UUID.
        UUID owner = quarter.getOwner();
        return owner == null ? null : TownyAPI.getInstance().getResident(owner);
    }

    public String handleQuery(JsonElement query, QueryOptions options) {
        if (!query.isJsonArray() && !query.isJsonObject())
            throw new BadRequestResponse("'query' must be an array or object");
        QuarterFilter filter = query.isJsonObject() ? QuarterFilter.parse(query.getAsJsonObject()) : null;
        return GameThread.read(() -> {
            List<Quarter> matches = new ArrayList<>();
            if (filter != null) {
                for (Quarter quarter : QuarterManager.getInstance().getAllQuarters()) {
                    if (filter.matches(quarter)) matches.add(quarter);
                }
                if (options.paginated()) matches.sort(Comparator.comparing(Quarter::getUUID));
            } else {
                for (JsonElement identifier : query.getAsJsonArray()) {
                    Quarter quarter = getObjectOrNull(identifier);
                    if (quarter != null) matches.add(quarter);
                }
            }
            JsonArray results = new JsonArray();
            for (Quarter quarter : options.page(matches)) results.add(getJsonElement(quarter, options.fields()));
            return options.response(results, matches.size());
        });
    }

    private record QuarterFilter(String name, Identifier owner, Identifier town, Identifier nation,
                                 QuarterType type, Boolean isForSale) {
        private static final Set<String> KEYS = Set.of("name", "owner", "town", "nation", "type", "isForSale");

        static QuarterFilter parse(JsonObject query) {
            for (String key : query.keySet()) {
                if (!KEYS.contains(key)) throw new BadRequestResponse("Unsupported quarter filter: " + key);
            }
            String name = string(query, "name");
            String typeName = string(query, "type");
            QuarterType type = null;
            if (typeName != null) {
                try { type = QuarterType.valueOf(typeName.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { throw new BadRequestResponse("Unknown quarter type"); }
            }
            Boolean isForSale = null;
            if (query.has("isForSale")) {
                JsonElement value = query.get("isForSale");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                    throw new BadRequestResponse("'isForSale' must be a boolean");
                isForSale = value.getAsBoolean();
            }
            return new QuarterFilter(name == null ? null : name.toLowerCase(Locale.ROOT),
                    Identifier.parse(string(query, "owner")), Identifier.parse(string(query, "town")),
                    Identifier.parse(string(query, "nation")), type, isForSale);
        }

        boolean matches(Quarter quarter) {
            if (name != null && (quarter.getName() == null || !quarter.getName().toLowerCase(Locale.ROOT).contains(name))) return false;
            if (type != null && type != quarter.getType()) return false;
            if (isForSale != null && isForSale != quarter.isForSale()) return false;
            if (owner != null) {
                var resident = owner.uuid() == null ? ownerResident(quarter) : null;
                if (!owner.matches(quarter.getOwner(), resident == null ? null : resident.getName())) return false;
            }
            if (town != null) {
                var quarterTown = quarter.getTown();
                if (quarterTown == null || !town.matches(quarterTown.getUUID(), quarterTown.getName())) return false;
            }
            if (nation != null) {
                var quarterNation = quarter.getNation();
                if (quarterNation == null || !nation.matches(quarterNation.getUUID(), quarterNation.getName())) return false;
            }
            return true;
        }

        private static String string(JsonObject query, String key) {
            if (!query.has(key)) return null;
            JsonElement value = query.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || value.getAsString().isBlank() || value.getAsString().length() > 200)
                throw new BadRequestResponse("'" + key + "' must be a string of 1 to 200 characters");
            return value.getAsString();
        }
    }

    private record Identifier(String name, UUID uuid) {
        static Identifier parse(String value) {
            if (value == null) return null;
            try {
                UUID uuid = UUID.fromString(value);
                if (uuid.toString().equalsIgnoreCase(value)) return new Identifier(null, uuid);
            } catch (IllegalArgumentException ignored) { }
            return new Identifier(value, null);
        }

        boolean matches(UUID actualId, String actualName) {
            return uuid != null ? uuid.equals(actualId) : name.equalsIgnoreCase(actualName);
        }
    }
}
