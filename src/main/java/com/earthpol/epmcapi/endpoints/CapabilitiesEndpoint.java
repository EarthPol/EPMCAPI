package com.earthpol.epmcapi.endpoints;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.endpoints.chat.ChatEndpoint;
import com.earthpol.epmcapi.endpoints.towny.TownySearch;
import com.earthpol.epmcapi.endpoints.quickshop.ShopSearch;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reports configured request support and availability, without probing external services. */
public final class CapabilitiesEndpoint extends GetEndpoint {
    private static final List<String> PLUGINS = List.of("Towny", "Quarters", "Vault", "LuckPerms", "QuickShop-Hikari", "PlaceholderAPI", "SiegeWar");
    private final EPMCAPI plugin;
    private final Set<String> registeredPaths;
    private final Set<String> initializedPlugins = new HashSet<>();

    public CapabilitiesEndpoint(EPMCAPI plugin, Set<String> registeredPaths) {
        this.plugin = plugin;
        this.registeredPaths = Set.copyOf(registeredPaths);
        for (String name : PLUGINS) if (plugin.hasPlugin(name)) initializedPlugins.add(name);
    }

    @Override
    public String lookup() { return getJsonElement().toString(); }

    @Override
    public JsonObject getJsonElement() {
        JsonObject result = new JsonObject();
        result.addProperty("apiVersion", plugin.getPluginMeta().getVersion());
        result.addProperty("basePath", plugin.basePath());
        result.addProperty("searchMethod", "POST");
        result.addProperty("searchOptionsLocation", "body");

        JsonObject limits = new JsonObject();
        limits.addProperty("maxBodyBytes", plugin.getConfig().getInt("networking.max_body_bytes", 65536));
        limits.addProperty("maxQueryItems", plugin.getConfig().getInt("networking.max_query_items", 100));
        limits.addProperty("maxPageSize", plugin.getConfig().getInt("networking.max_page_size", 100));
        result.add("limits", limits);

        JsonObject integrations = new JsonObject();
        for (String name : PLUGINS) {
            JsonObject integration = new JsonObject();
            integration.addProperty("enabled", plugin.hasPlugin(name));
            integrations.add(name, integration);
        }
        result.add("integrations", integrations);

        JsonObject endpoints = new JsonObject();
        JsonObject server = endpoint(null, "", null);
        method(server, "GET", false, false, List.of(), List.of(), List.of());
        endpoints.add("server", server);
        JsonObject capabilities = endpoint(null, "capabilities", null);
        method(capabilities, "GET", false, false, List.of(), List.of(), List.of());
        endpoints.add("capabilities", capabilities);

        JsonObject towns = listEndpoint("towns", null);
        method(towns, "POST", true, true, List.of("array", "object"), TownySearch.TOWN_FILTERS, TownySearch.SORTS);
        endpoints.add("towns", towns);
        JsonObject nations = listEndpoint("nations", null);
        method(nations, "POST", true, true, List.of("array", "object"), TownySearch.NATION_FILTERS, TownySearch.SORTS);
        endpoints.add("nations", nations);

        JsonObject players = listEndpoint("players", null);
        List<String> playerFilters = new ArrayList<>(List.of("name", "isOnline", "hasTown", "hasNation", "town", "nation"));
        boolean premiumAvailable = initializedPlugins.contains("LuckPerms") && plugin.hasPlugin("LuckPerms");
        if (premiumAvailable) playerFilters.add("isPremium");
        players.addProperty("premiumLookupAvailable", premiumAvailable);
        method(players, "POST", true, true, List.of("array", "object"), playerFilters, List.of());
        endpoints.add("players", players);

        JsonObject quarters = listEndpoint("quarters", "Quarters");
        method(quarters, "POST", true, true, List.of("array", "object"),
                List.of("name", "owner", "town", "nation", "type", "isForSale"), List.of());
        endpoints.add("quarters", quarters);
        JsonObject shops = listEndpoint("shops", "QuickShop-Hikari");
        shops.addProperty("structuredItems", true);
        shops.addProperty("priceRangeBasis", "trade");
        shops.addProperty("defaultCurrencyIsNull", true);
        shops.addProperty("priceSortRequiresSingleCurrency", true);
        shops.addProperty("priceSortRequiresObjectQuery", true);
        shops.add("enchantmentSources", array(List.of("any", "applied", "stored")));
        method(shops, "POST", true, true, List.of("array", "object"), ShopSearch.FILTERS, ShopSearch.SORTS);
        endpoints.add("shops", shops);

        JsonObject location = endpoint("location", "location", null);
        method(location, "POST", true, true, List.of("array"), List.of(), List.of());
        endpoints.add("location", location);
        JsonObject chat = endpoint("chat", "chat", null);
        method(chat, "POST", false, false, List.of("object"), ChatEndpoint.FILTERS, List.of());
        endpoints.add("chat", chat);
        JsonObject discord = endpoint("discord", "discord", null);
        method(discord, "POST", false, false, List.of("array"), List.of(), List.of());
        endpoints.add("discord", discord);
        JsonObject voting = endpoint("voting", "voting", "PlaceholderAPI");
        method(voting, "GET", false, false, List.of(), List.of(), List.of());
        endpoints.add("voting", voting);
        JsonObject sieges = endpoint("siegewar", "sieges", "SiegeWar");
        method(sieges, "GET", false, false, List.of(), List.of(), List.of());
        endpoints.add("sieges", sieges);
        result.add("endpoints", endpoints);
        return result;
    }

    private JsonObject listEndpoint(String name, String dependency) {
        JsonObject endpoint = endpoint(name, name, dependency);
        method(endpoint, "GET", false, false, List.of(), List.of(), List.of());
        return endpoint;
    }

    private JsonObject endpoint(String configName, String path, String dependency) {
        JsonObject endpoint = new JsonObject();
        boolean enabled = configName == null || plugin.getConfig().getBoolean("endpoints." + configName, false);
        boolean available = enabled && (configName == null || plugin.endpointAvailable(configName));
        // These routes have unavailable handlers when their plugin was absent during API startup.
        String fullPath = plugin.basePath() + (path.isEmpty() ? "" : "/" + path);
        boolean needsRestart = enabled && (configName != null && !registeredPaths.contains(fullPath)
                || dependency != null && !dependency.equals("SiegeWar")
                && !initializedPlugins.contains(dependency) && plugin.hasPlugin(dependency));
        if (needsRestart) available = false;
        endpoint.addProperty("path", fullPath);
        endpoint.addProperty("enabled", enabled);
        endpoint.addProperty("available", available);
        endpoint.addProperty("status", !enabled ? "disabled" : needsRestart ? "restart_required" : available ? "available" : "requirements_unavailable");
        if (dependency != null) endpoint.addProperty("requiredPlugin", dependency);
        endpoint.add("methods", new JsonObject());
        return endpoint;
    }

    private static void method(JsonObject endpoint, String method, boolean pagination, boolean fields,
            List<String> queryTypes, List<String> filters, List<String> sorts) {
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("pagination", pagination);
        capabilities.addProperty("fieldSelection", fields);
        capabilities.add("queryTypes", array(queryTypes));
        capabilities.add("filters", array(filters));
        capabilities.add("sortFields", array(sorts));
        capabilities.add("sortOrders", array(sorts.isEmpty() ? List.of() : List.of("asc", "desc")));
        endpoint.getAsJsonObject("methods").add(method, capabilities);
    }

    private static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
