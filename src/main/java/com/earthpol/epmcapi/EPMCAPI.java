package com.earthpol.epmcapi;

import com.earthpol.epmcapi.db.Database;
import com.earthpol.epmcapi.db.DiscordDatabase;
import com.earthpol.epmcapi.networking.RequestHandler;
import com.earthpol.epmcapi.networking.GameThread;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.concurrent.*;

public class EPMCAPI extends JavaPlugin {
    private static EPMCAPI instance;
    private HttpServer server;
    private ThreadPoolExecutor workers;
    private ScheduledThreadPoolExecutor deadlines;

    public static EPMCAPI getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        try {
            int workerCount = positive("networking.workers", 5, 64);
            int queueCapacity = positive("networking.queue_capacity", 25, 1000);
            positive("networking.max_query_items", 100, 1000);
            positive("networking.max_page_size", 100, 1000);
            GameThread.initialize(this, positive("networking.game_thread_timeout_ms", 2000, 30000));
            positive("database.query_timeout_seconds", 5, 60);
            workers = new ThreadPoolExecutor(workerCount, workerCount, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity), Thread.ofPlatform().name("EPMCAPI-http-", 0).factory(),
                    new ThreadPoolExecutor.AbortPolicy());
            deadlines = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("EPMCAPI-deadline-", 0).factory());
            deadlines.setRemoveOnCancelPolicy(true);
            initializeDatabase("chat", "mysql-chat", () -> Database.init(db("mysql-chat", "host"), db("mysql-chat", "port"), db("mysql-chat", "database"), db("mysql-chat", "user"), db("mysql-chat", "pass")));
            initializeDatabase("discord", "mysql-discord", () -> DiscordDatabase.init(db("mysql-discord", "host"), db("mysql-discord", "port"), db("mysql-discord", "database"), db("mysql-discord", "user"), db("mysql-discord", "pass")));
            String host = getConfig().getString("networking.host", "127.0.0.1");
            server = HttpServer.create(new InetSocketAddress(host, getConfig().getInt("networking.port", 8080)), queueCapacity);
            server.createContext(basePath(), new RequestHandler(this, deadlines));
            server.setExecutor(workers);
            server.start();
            getLogger().info("HTTP API listening on " + host + ":" + server.getAddress().getPort() + basePath());
        } catch (Exception | LinkageError e) {
            getLogger().severe("API startup failed (" + e.getClass().getSimpleName() + "). Check dependencies, configuration and port availability.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private int positive(String path, int fallback, int max) {
        int value = getConfig().getInt(path, fallback);
        if (value < 1 || value > max) throw new IllegalArgumentException("Invalid setting: " + path);
        return value;
    }

    private String db(String section, String key) { return getConfig().getString(section + "." + key, ""); }

    private void initializeDatabase(String endpoint, String section, Runnable initialize) {
        if (!getConfig().getBoolean("endpoints." + endpoint, false) || db(section, "database").isBlank() || db(section, "user").isBlank()) return;
        try { initialize.run(); }
        catch (RuntimeException e) { getLogger().warning(endpoint + " database unavailable; other endpoints remain enabled (" + e.getClass().getSimpleName() + ")."); }
    }

    public String basePath() {
        String path = getConfig().getString("networking.url_path", "astra").replaceAll("^/+|/+$", "");
        if (!path.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")) throw new IllegalArgumentException("Invalid networking.url_path");
        return "/" + path;
    }

    public boolean hasPlugin(String name) { return getServer().getPluginManager().isPluginEnabled(name); }

    public boolean endpointAvailable(String name) {
        if (!getConfig().getBoolean("endpoints." + name, false)) return false;
        return switch (name) {
            case "quarters" -> hasPlugin("Quarters");
            case "shops" -> hasPlugin("QuickShop-Hikari");
            case "voting" -> hasPlugin("PlaceholderAPI");
            case "siegewar" -> hasPlugin("SiegeWar");
            case "chat" -> Database.isReady() && !getConfig().getStringList("chat-endpoint.allowed-channels").isEmpty();
            case "discord" -> DiscordDatabase.isReady();
            default -> true;
        };
    }

    @Override
    public void onDisable() {
        GameThread.shutdown();
        if (server != null) { server.stop(0); server = null; }
        if (workers != null) workers.shutdownNow();
        if (deadlines != null) deadlines.shutdownNow();
        Database.close();
        DiscordDatabase.close();
    }
}
