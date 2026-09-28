package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.exception.HttpResponseException;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Global data and location-owned data use separate schedulers on both Paper and Folia. */
public final class GameThread {
    private static final Set<ReadTask<?>> pending = ConcurrentHashMap.newKeySet();
    private static volatile Plugin owner;
    private static volatile long timeoutMillis;
    private static volatile boolean stopping = true;

    private GameThread() {}

    public static void initialize(Plugin plugin, long timeout) {
        if (timeout < 1) throw new IllegalArgumentException("Invalid game data timeout");
        owner = plugin;
        timeoutMillis = timeout;
        stopping = false;
    }

    /** Global/plugin metadata only. Return detached JSON/scalars, never live game objects. */
    public static <T> T read(Supplier<T> operation) {
        Plugin plugin = requireRunning();
        if (Bukkit.isGlobalTickThread()) return operation.get();
        return await(submit(task -> Bukkit.getGlobalRegionScheduler().run(plugin, task), operation));
    }

    /** Schedule block/inventory access on the region that owns this fixed location. Not for entities. */
    public static <T> Future<T> at(Location location, Supplier<T> operation) {
        Plugin plugin = requireRunning();
        if (location == null || location.getWorld() == null)
            throw new HttpResponseException(503, "Shop world is unavailable");
        if (Bukkit.isOwnedByCurrentRegion(location)) return CompletableFuture.completedFuture(operation.get());
        return submit(task -> Bukkit.getRegionScheduler().run(plugin, location, task), operation);
    }

    /** Only HTTP workers may wait for tick work; every task in a batch shares one deadline. */
    public static <T> List<T> awaitAll(List<? extends Future<T>> futures) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) results.add(awaitUntil(future, deadline));
            return results;
        } finally {
            futures.forEach(future -> future.cancel(false));
        }
    }

    private static <T> T await(Future<T> future) {
        try { return awaitUntil(future, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)); }
        finally { future.cancel(false); }
    }

    private static <T> T awaitUntil(Future<T> future, long deadline) {
        if (!future.isDone() && Bukkit.isPrimaryThread())
            throw new IllegalStateException("A tick thread must not wait for another region");
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpResponseException(503, "Request interrupted");
        } catch (TimeoutException | CancellationException e) {
            throw new HttpResponseException(503, "Game data is temporarily unavailable");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            if (e.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("Game data collection failed", e.getCause());
        }
    }

    private static <T> Future<T> submit(Function<Consumer<ScheduledTask>, ScheduledTask> scheduler, Supplier<T> operation) {
        ReadTask<T> future = new ReadTask<>(operation);
        pending.add(future);
        try {
            requireRunning();
            future.attach(scheduler.apply(ignored -> future.run()));
            return future;
        } catch (RuntimeException e) {
            future.cancel(false);
            throw new HttpResponseException(503, "Game scheduler is unavailable");
        }
    }

    private static Plugin requireRunning() {
        Plugin plugin = owner;
        if (stopping || plugin == null || !plugin.isEnabled()) throw new HttpResponseException(503, "API is stopping");
        return plugin;
    }

    public static void shutdown() {
        stopping = true;
        pending.forEach(future -> future.cancel(false));
        Plugin plugin = owner;
        if (plugin != null) Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    private static final class ReadTask<T> extends FutureTask<T> {
        private volatile ScheduledTask task;

        ReadTask(Supplier<T> operation) { super(operation::get); }

        void attach(ScheduledTask scheduled) {
            task = scheduled;
            // Shutdown/timeout can win the race with scheduler registration.
            if (isDone()) scheduled.cancel();
        }

        @Override protected void done() {
            pending.remove(this);
            ScheduledTask scheduled = task;
            if (isCancelled() && scheduled != null) scheduled.cancel();
        }
    }
}
