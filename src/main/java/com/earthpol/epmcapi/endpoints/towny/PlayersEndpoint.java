package com.earthpol.epmcapi.endpoints.towny;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.utils.EndpointUtils;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import com.earthpol.epmcapi.networking.FieldSelection;
import com.earthpol.epmcapi.networking.QueryOptions;
import com.earthpol.epmcapi.networking.GameThread;
import com.earthpol.epmcapi.exception.HttpResponseException;
import com.earthpol.epmcapi.integration.EconomyIntegration;
import com.earthpol.epmcapi.integration.PremiumPermissions;

public class PlayersEndpoint extends PostEndpoint<Resident> {
    private final PremiumPermissions permissions;

    public PlayersEndpoint() {
        var plugin = EPMCAPI.getInstance();
        permissions = plugin.hasPlugin("LuckPerms") ? new PremiumPermissions(
                plugin.getConfig().getLong("players-endpoint.premium_cache_ms", 30000),
                plugin.getConfig().getLong("players-endpoint.permission_timeout_ms", 3000)) : null;
    }

    @Override
    public Resident getObjectOrNull(JsonElement element) {
        String string = JSONUtil.getJsonElementAsStringOrNull(element);
        if (string == null) throw new BadRequestResponse("Your query contains a value that is not a string");

        Resident resident;
        try {
            resident = TownyAPI.getInstance().getResident(UUID.fromString(string));
        } catch (IllegalArgumentException e) {
            resident = TownyAPI.getInstance().getResident(string);
        }

        return resident;
    }

    @Override
    public JsonElement getJsonElement(Resident resident) {
        return getJsonElement(resident, null, FieldSelection.all());
    }

    @Override
    public JsonElement getJsonElement(Resident resident, FieldSelection fields) {
        return getJsonElement(resident, null, fields);
    }

    private JsonElement getJsonElement(Resident resident, Boolean isPremium, FieldSelection fields) {
        JsonObject playerObject = new JsonObject();

        playerObject.addProperty("name", resident.getName());
        playerObject.addProperty("uuid", resident.getUUID().toString());
        playerObject.addProperty("title", resident.getTitle().isEmpty() ? null : resident.getTitle());
        playerObject.addProperty("surname", resident.getSurname().isEmpty() ? null : resident.getSurname());
        playerObject.addProperty("formattedName", resident.getFormattedName());
        playerObject.addProperty("about", resident.getAbout().isEmpty() ? null : resident.getAbout());
        playerObject.addProperty("premium", isPremium);
        playerObject.addProperty("isPremium", isPremium);

        playerObject.add("town", EndpointUtils.getTownJsonObject(resident.getTownOrNull()));
        playerObject.add("nation", EndpointUtils.getNationJsonObject(resident.getNationOrNull()));

        JsonObject timestampsObject = new JsonObject();
        timestampsObject.addProperty("registered", resident.getRegistered());
        timestampsObject.addProperty("joinedTownAt", resident.hasTown() ? resident.getJoinedTownAt() : null);
        timestampsObject.addProperty("lastOnline", resident.getLastOnline() != 0 ? resident.getLastOnline() : null);
        playerObject.add("timestamps", timestampsObject);

        JsonObject statusObject = new JsonObject();
        statusObject.addProperty("isOnline", resident.isOnline());
        statusObject.addProperty("isNPC", resident.isNPC());
        statusObject.addProperty("isMayor", resident.isMayor());
        statusObject.addProperty("isKing", resident.isKing());
        statusObject.addProperty("hasTown", resident.hasTown());
        statusObject.addProperty("hasNation", resident.hasNation());
        playerObject.add("status", statusObject);

        JsonObject statsObject = new JsonObject();
        if (fields.includes("stats.balance")) statsObject.addProperty("balance", EPMCAPI.getInstance().hasPlugin("Vault") ? EconomyIntegration.balance(resident.getUUID()) : null);
        statsObject.addProperty("numFriends", resident.getFriends().size());
        playerObject.add("stats", statsObject);

        if (fields.includes("perms")) playerObject.add("perms", EndpointUtils.getPermsObject(resident.getPermissions()));

        if (fields.includes("ranks")) {
            JsonObject ranksObject = new JsonObject();
            ranksObject.add("townRanks", getRankArray(resident.getTownRanks()));
            ranksObject.add("nationRanks", getRankArray(resident.getNationRanks()));
            playerObject.add("ranks", ranksObject);
        }

        if (fields.includes("friends")) playerObject.add("friends", EndpointUtils.getResidentArray(resident.getFriends()));

        return fields.project(playerObject);
    }

    private JsonArray getRankArray(List<String> ranks) {
        JsonArray jsonArray = new JsonArray();

        for (String rank : ranks) {
            jsonArray.add(rank);
        }

        return jsonArray;
    }

    public String handleQuery(JsonElement query, QueryOptions options) {
        if (!query.isJsonObject() && !query.isJsonArray())
            throw new BadRequestResponse("'query' must be an array or object");
        PlayerFilter filter = query.isJsonObject() ? PlayerFilter.parse(query.getAsJsonObject()) : null;
        Boolean premiumFilter = filter == null ? null : filter.isPremium();
        boolean hasPermissions = permissions != null && EPMCAPI.getInstance().hasPlugin("LuckPerms");
        if (premiumFilter != null && !hasPermissions)
            throw new HttpResponseException(503, "Premium filtering requires LuckPerms");
        boolean online = query.isJsonArray() && query.getAsJsonArray().size() == 1
                && "online".equalsIgnoreCase(JSONUtil.getJsonElementAsStringOrNull(query.getAsJsonArray().get(0)));

        List<UUID> ids = GameThread.read(() -> {
            List<UUID> selected = new ArrayList<>();
            if (filter != null) {
                var residents = TownyAPI.getInstance().getResidents();
                int max = EPMCAPI.getInstance().getConfig().getInt("players-endpoint.max_filter_candidates", 10000);
                if (residents.size() > max) throw new HttpResponseException(503, "Player filter exceeds configured candidate limit");
                for (Resident resident : residents) {
                    if (filter.matches(resident)) selected.add(resident.getUUID());
                }
            } else if (online) {
                Bukkit.getOnlinePlayers().forEach(player -> {
                    if (TownyAPI.getInstance().getResident(player.getUniqueId()) != null) selected.add(player.getUniqueId());
                });
            } else {
                for (JsonElement id : query.getAsJsonArray()) {
                    Resident resident = getObjectOrNull(id);
                    if (resident != null) selected.add(resident.getUUID());
                }
            }
            return selected;
        });
        if (options.paginated() && (filter != null || online)) ids.sort(UUID::compareTo);

        List<UUID> matches = ids;
        Map<UUID, Boolean> premium;
        boolean needsPremium = options.fields().includes("premium") || options.fields().includes("isPremium");
        if (premiumFilter != null) {
            premium = permissions.resolve(ids);
            matches = ids.stream().filter(id -> premiumFilter.equals(premium.get(id))).toList();
        } else {
            premium = hasPermissions && needsPremium ? permissions.resolve(options.page(ids)) : Map.of();
        }
        int total = matches.size();
        List<UUID> page = options.page(matches);
        return GameThread.read(() -> {
            JsonArray result = new JsonArray();
            for (UUID id : page) {
                Resident resident = TownyAPI.getInstance().getResident(id);
                if (resident != null) result.add(getJsonElement(resident, premium.get(id), options.fields()));
            }
            return options.response(result, total);
        });
    }

    private record PlayerFilter(String name, Boolean isOnline, Boolean hasTown, Boolean hasNation,
                                Boolean isPremium, String town, String nation) {
        private static final Set<String> KEYS = Set.of("name", "isOnline", "hasTown", "hasNation", "isPremium", "town", "nation");

        static PlayerFilter parse(JsonObject query) {
            for (String key : query.keySet()) {
                if (!KEYS.contains(key)) throw new BadRequestResponse("Unsupported player filter: " + key);
            }
            String name = string(query, "name");
            return new PlayerFilter(name == null ? null : name.toLowerCase(Locale.ROOT), bool(query, "isOnline"),
                    bool(query, "hasTown"), bool(query, "hasNation"), bool(query, "isPremium"),
                    string(query, "town"), string(query, "nation"));
        }

        boolean matches(Resident resident) {
            if (name != null && !resident.getName().toLowerCase(Locale.ROOT).contains(name)) return false;
            if (isOnline != null && isOnline != resident.isOnline()) return false;
            if (hasTown != null && hasTown != resident.hasTown()) return false;
            if (hasNation != null && hasNation != resident.hasNation()) return false;
            var residentTown = resident.getTownOrNull();
            if (town != null && (residentTown == null || !identifier(town, residentTown.getName(), residentTown.getUUID()))) return false;
            var residentNation = resident.getNationOrNull();
            return nation == null || residentNation != null && identifier(nation, residentNation.getName(), residentNation.getUUID());
        }

        private static boolean identifier(String filter, String name, UUID id) {
            return filter.equalsIgnoreCase(name) || filter.equalsIgnoreCase(id.toString());
        }

        private static String string(JsonObject query, String key) {
            if (!query.has(key)) return null;
            JsonElement value = query.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || value.getAsString().isBlank() || value.getAsString().length() > 200)
                throw new BadRequestResponse("'" + key + "' must be a string of 1 to 200 characters");
            return value.getAsString();
        }

        private static Boolean bool(JsonObject query, String key) {
            if (!query.has(key)) return null;
            JsonElement value = query.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                throw new BadRequestResponse("'" + key + "' must be a boolean");
            return value.getAsBoolean();
        }
    }
}
