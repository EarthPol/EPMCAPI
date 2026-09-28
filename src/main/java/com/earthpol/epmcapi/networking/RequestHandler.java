package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.endpoints.*;
import com.earthpol.epmcapi.endpoints.chat.ChatEndpoint;
import com.earthpol.epmcapi.endpoints.discord.DiscordEndpoint;
import com.earthpol.epmcapi.endpoints.quickshop.*;
import com.earthpol.epmcapi.endpoints.siegewar.SiegeEndpoint;
import com.earthpol.epmcapi.endpoints.towny.*;
import com.earthpol.epmcapi.endpoints.towny.list.*;
import com.earthpol.epmcapi.endpoints.voting.VotingEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.exception.HttpResponseException;
import com.google.gson.*;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

public final class RequestHandler extends HttpRouter {
    private final EPMCAPI plugin;
    private final String base;
    private final int maxItems;
    private final int maxPageSize;

    public RequestHandler(EPMCAPI plugin, ScheduledExecutorService deadlines) {
        super(new Settings(plugin.getConfig().getInt("networking.max_body_bytes", 65536),
                plugin.getConfig().getLong("networking.request_timeout_ms", 15000),
                plugin.getConfig().getBoolean("networking.cors.enabled", true),
                plugin.getConfig().getStringList("networking.cors.allowed_origins"),
                plugin.getConfig().getBoolean("networking.cors.allow_credentials", false),
                plugin.getConfig().getBoolean("networking.compression.enabled", true),
                plugin.getConfig().getInt("networking.compression.min_bytes", 1024)), deadlines, plugin.getLogger());
        this.plugin = plugin;
        this.base = plugin.basePath();
        this.maxItems = plugin.getConfig().getInt("networking.max_query_items", 100);
        this.maxPageSize = plugin.getConfig().getInt("networking.max_page_size", 100);
        ServerEndpoint server = new ServerEndpoint();
        register(base, "GET", ignored -> GameThread.read(server::lookup));
        addTownyRoutes();
        addIntegrationRoutes();
        CapabilitiesEndpoint capabilities = new CapabilitiesEndpoint(plugin, paths());
        register(base + "/capabilities", "GET", ignored -> GameThread.read(capabilities::lookup));
    }

    private void addTownyRoutes() {
        TownListEndpoint towns = new TownListEndpoint();
        TownsEndpoint town = new TownsEndpoint();
        list("towns", towns);
        asynchronous("towns", "towns", "POST", request -> TownySearch.towns(
                RequestParser.sorted(request.body(), maxItems, maxPageSize, TownySearch.SORTS), town));
        NationsListEndpoint nations = new NationsListEndpoint();
        NationsEndpoint nation = new NationsEndpoint();
        list("nations", nations);
        asynchronous("nations", "nations", "POST", request -> TownySearch.nations(
                RequestParser.sorted(request.body(), maxItems, maxPageSize, TownySearch.SORTS), nation));
        PlayersListEndpoint players = new PlayersListEndpoint();
        PlayersEndpoint player = new PlayersEndpoint();
        list("players", players);
        asynchronous("players", "players", "POST", request -> {
            RequestParser.Query query = RequestParser.parse(request.body(), maxItems, maxPageSize);
            return player.handleQuery(query.query(), query.options());
        });
        LocationEndpoint location = new LocationEndpoint();
        batch("location", location);
    }

    private void addIntegrationRoutes() {
        // These classes are constructed only after the supplying plugin is available.
        if (plugin.hasPlugin("Quarters")) {
            QuartersListEndpoint quarters = new QuartersListEndpoint();
            QuartersEndpoint quarter = new QuartersEndpoint();
            list("quarters", quarters);
            asynchronous("quarters", "quarters", "POST", request -> {
                RequestParser.Query query = RequestParser.parse(request.body(), maxItems, maxPageSize);
                return quarter.handleQuery(query.query(), query.options());
            });
        } else unavailable("quarters", "quarters", "GET", "POST");
        if (plugin.hasPlugin("QuickShop-Hikari")) {
            ShopListEndpoint shops = new ShopListEndpoint();
            ShopEndpoint shop = new ShopEndpoint();
            asynchronous("shops", "shops", "GET", ignored -> shops.lookup());
            shopSearch(shops, shop);
        } else unavailable("shops", "shops", "GET", "POST");
        if (plugin.hasPlugin("PlaceholderAPI")) {
            VotingEndpoint voting = new VotingEndpoint();
            game("voting", "voting", "GET", ignored -> voting.lookup());
        } else unavailable("voting", "voting", "GET");
        SiegeEndpoint sieges = new SiegeEndpoint();
        asynchronous("siegewar", "sieges", "GET", ignored -> sieges.lookup());
        ChatEndpoint chat = new ChatEndpoint();
        asynchronous("chat", "chat", "POST", request -> {
            JsonElement query = query(request.body());
            if (!query.isJsonObject()) throw new BadRequestResponse("Chat query must be an object");
            return chat.getJsonElement(chat.getObjectOrNull(query)).toString();
        });
        DiscordEndpoint discord = new DiscordEndpoint();
        asynchronous("discord", "discord", "POST", request -> {
            JsonArray query = RequestParser.array(query(request.body()));
            if (query.size() != 1) throw new BadRequestResponse("Discord query must contain exactly one identifier");
            return discord.getJsonElement(discord.getObjectOrNull(query.get(0))).toString();
        });
    }

    private JsonElement query(String body) { return RequestParser.query(body, maxItems); }

    private void shopSearch(ShopListEndpoint shops, ShopEndpoint shop) {
        asynchronous("shops", "shops", "POST", request -> {
            RequestParser.SortedQuery query = RequestParser.sorted(request.body(), maxItems, maxPageSize, ShopSearch.SORTS);
            if (query.request().query().isJsonArray()) {
                if (query.sort() != null) throw new BadRequestResponse("Shop sorting requires an object 'query'");
                return shop.handleQuery(query.request().query().getAsJsonArray(), query.request().options());
            }
            ShopSearch search = ShopSearch.parse(query);
            String snapshot = shops.lookup();
            return search.response(snapshot);
        });
    }

    private void list(String name, GetEndpoint endpoint) {
        game(name, name, "GET", ignored -> endpoint.lookup());
    }

    private <T> void batch(String name, PostEndpoint<T> endpoint) {
        if (!plugin.getConfig().getBoolean("endpoints." + name, false)) return;
        register(base + "/" + name, "POST", request -> {
            RequestParser.Query query = RequestParser.parse(request.body(), maxItems, maxPageSize);
            JsonArray identifiers = RequestParser.array(query.query());
            return GameThread.read(() -> {
                requireAvailable(name);
                return batch(identifiers, query.options(), endpoint);
            });
        });
    }

    private void game(String endpoint, String path, String method, Function<Request, String> action) {
        if (!plugin.getConfig().getBoolean("endpoints." + endpoint, false)) return;
        register(base + "/" + path, method, body -> GameThread.read(() -> {
            requireAvailable(endpoint);
            return action.apply(body);
        }));
    }

    private void asynchronous(String endpoint, String path, String method, Function<Request, String> action) {
        if (!plugin.getConfig().getBoolean("endpoints." + endpoint, false)) return;
        register(base + "/" + path, method, body -> {
            GameThread.read(() -> { requireAvailable(endpoint); return true; });
            return action.apply(body);
        });
    }

    private void unavailable(String endpoint, String path, String... methods) {
        if (!plugin.getConfig().getBoolean("endpoints." + endpoint, false)) return;
        for (String method : methods) register(base + "/" + path, method, ignored -> {
            throw new HttpResponseException(503, "Integration unavailable: " + endpoint);
        });
    }

    private void requireAvailable(String endpoint) {
        if (!plugin.endpointAvailable(endpoint)) throw new HttpResponseException(503, "Integration unavailable: " + endpoint);
    }

    private static <T> String batch(JsonArray identifiers, QueryOptions options, PostEndpoint<T> endpoint) {
        List<T> matches = new ArrayList<>();
        for (JsonElement identifier : identifiers) {
            T object = endpoint.getObjectOrNull(identifier);
            if (object != null) matches.add(object);
        }
        JsonArray results = new JsonArray();
        for (T object : options.page(matches)) results.add(endpoint.getJsonElement(object, options.fields()));
        return options.response(results, matches.size());
    }
}
