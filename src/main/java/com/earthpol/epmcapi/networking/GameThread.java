package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.exception.HttpResponseException;
import org.bukkit.Bukkit;

import java.util.concurrent.*;
import java.util.function.Supplier;

public final class GameThread {
    private GameThread() {}

    /** Return detached JSON/scalars, never mutable game objects, to an HTTP worker. */
    public static <T> T read(Supplier<T> operation) {
        if (Bukkit.isPrimaryThread()) return operation.get();
        EPMCAPI plugin = EPMCAPI.getInstance();
        if (!plugin.isEnabled()) throw new HttpResponseException(503, "API is stopping");
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, operation::get);
        try {
            return future.get(plugin.getConfig().getLong("networking.game_thread_timeout_ms", 2000), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpResponseException(503, "Request interrupted");
        } catch (TimeoutException | CancellationException e) {
            throw new HttpResponseException(503, "Game data is temporarily unavailable");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Game data collection failed", e.getCause());
        } finally {
            future.cancel(false);
        }
    }
}
