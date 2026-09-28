package com.earthpol.epmcapi.integration;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.exception.HttpResponseException;
import com.google.gson.*;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional adapter for upstream and EarthPol SiegeWar APIs; run on the game thread. */
public final class SiegeWarIntegration {
    private SiegeWarIntegration() {}

    private static Class<?> type(String name) {
        var plugin = EPMCAPI.getInstance().getServer().getPluginManager().getPlugin("SiegeWar");
        if (plugin == null || !plugin.isEnabled()) throw new HttpResponseException(503, "SiegeWar unavailable");
        try { return Class.forName(name, false, plugin.getClass().getClassLoader()); }
        catch (ClassNotFoundException e) { return null; }
    }

    // Reflection is confined to this integration: no private fork artifact is required to build EPMCAPI.
    static Object optional(Object target, String name, Object... args) {
        if (target == null) return null;
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean matches = true;
            for (int i = 0; i < args.length; i++)
                if (args[i] != null && !method.getParameterTypes()[i].isInstance(args[i])) matches = false;
            if (!matches) continue;
            try { return method.invoke(target instanceof Class<?> ? null : target, args); }
            catch (IllegalAccessException | InvocationTargetException e) { throw new IllegalStateException("SiegeWar data unavailable", e); }
        }
        return null;
    }

    public static boolean isTownPeaceful(Town town) {
        Object result = optional(type("com.gmail.goosius.siegewar.utils.SiegeWarTownPeacefulnessUtil"), "isTownPeaceful", town);
        return result instanceof Boolean peaceful ? peaceful : town.isNeutral();
    }

    public static JsonObject snapshot() {
        Class<?> api = type("com.gmail.goosius.siegewar.SiegeWarAPI");
        Object result = optional(api, "getSieges");
        if (!(result instanceof Iterable<?> sieges)) throw new HttpResponseException(503, "Unsupported SiegeWar API");
        JsonArray output = new JsonArray();
        for (Object siege : sieges) {
            Object status = optional(siege, "getStatus");
            if (status == null) continue;
            JsonObject entry = new JsonObject();
            Object uuid = optional(api, "getSiegeUUID", siege);
            if (uuid == null) uuid = optional(siege, "getUUID");
            entry.addProperty("uuid", uuid instanceof UUID ? uuid.toString() : null);
            Object defender = name(siege, "getDefender", "getDefenderName");
            put(entry, "name", defender);
            put(entry, "attacker", name(siege, "getAttacker", "getAttackerName"));
            put(entry, "defender", defender);
            put(entry, "type", optional(siege, "getSiegeType"));
            put(entry, "status", optional(status, "getName"));
            put(entry, "isActive", optional(status, "isActive"));
            put(entry, "startedAtMillis", optional(api, "getSiegeStartedAtMillis", siege));
            put(entry, "endedAtMillis", optional(api, "getSiegeEndedAtMillis", siege));
            Object completed = optional(siege, "getNumBattleSessionsCompleted");
            put(entry, "sessionsCompleted", completed);
            // The old value assumed every siege lasted 12 sessions; unsupported counts are unknown.
            put(entry, "sessionsLeft", optional(siege, "getNumBattleSessionsRemaining"));
            Object attackers = optional(siege, "getAttackerBattlePoints");
            Object defenders = optional(siege, "getDefenderBattlePoints");
            put(entry, "attackerPoints", attackers);
            put(entry, "defenderPoints", defenders);
            put(entry, "sessionBalance", attackers instanceof Number a && defenders instanceof Number d ? a.longValue() - d.longValue() : null);
            put(entry, "balance", optional(siege, "getSiegeBalance"));
            if (optional(siege, "getFlagLocation") instanceof Location location) {
                JsonObject banner = new JsonObject();
                banner.addProperty("world", location.getWorld() == null ? null : location.getWorld().getName());
                banner.addProperty("x", location.getBlockX());
                banner.addProperty("y", location.getBlockY());
                banner.addProperty("z", location.getBlockZ());
                entry.add("bannerLocation", banner);
            }
            output.add(entry);
        }
        JsonObject root = new JsonObject();
        root.addProperty("activeSiegeCount", output.size());
        Object session = optional(type("com.gmail.goosius.siegewar.objects.BattleSession"), "getBattleSession");
        Object start = optional(session, "getScheduledStartTime");
        put(root, "battleSessionTimeTill", start instanceof Number time ? Math.max(0L, time.longValue() - System.currentTimeMillis()) : null);
        put(root, "battleSessionTimeRemaining", optional(api, "getBattleSessionTimeRemaining"));
        root.add("sieges", output);
        return root;
    }

    private static Object name(Object siege, String objectMethod, String nameMethod) {
        Object name = optional(optional(siege, objectMethod), "getName");
        return name != null ? name : optional(siege, nameMethod);
    }

    private static void put(JsonObject json, String key, Object value) {
        if (value instanceof Number number) json.addProperty(key, number);
        else if (value instanceof Boolean bool) json.addProperty(key, bool);
        else json.addProperty(key, value == null ? null : value.toString());
    }
}
