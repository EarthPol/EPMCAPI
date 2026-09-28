package com.earthpol.epmcapi.integration;

import com.earthpol.epmcapi.exception.HttpResponseException;
import net.luckperms.api.LuckPermsProvider;
import java.util.*;
import java.util.concurrent.*;

/** Permission storage I/O is performed only by HTTP workers, never a tick thread. */
public final class PremiumPermissions {
    private record Entry(boolean premium, long expires) {}
    private final ConcurrentMap<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final long ttl;
    private final long timeout;

    public PremiumPermissions(long ttl, long timeout) {
        this.ttl = Math.max(0, ttl);
        this.timeout = Math.max(1, timeout);
    }

    public Map<UUID, Boolean> resolve(List<UUID> ids) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        Map<UUID, Boolean> result = new HashMap<>();
        var manager = LuckPermsProvider.get().getUserManager();
        cache.entrySet().removeIf(entry -> entry.getValue().expires() < System.currentTimeMillis());
        for (UUID id : ids) {
            Entry cached = cache.get(id);
            if (cached != null && cached.expires() > System.currentTimeMillis()) {
                result.put(id, cached.premium());
                continue;
            }
            var user = manager.getUser(id);
            boolean loaded = user == null;
            try {
                if (loaded) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new TimeoutException();
                    var pending = manager.loadUser(id);
                    try { user = pending.get(remaining, TimeUnit.NANOSECONDS); }
                    catch (TimeoutException | InterruptedException e) {
                        pending.thenAccept(manager::cleanupUser);
                        throw e;
                    }
                }
                boolean premium = user.getCachedData().getPermissionData().checkPermission("group.premium").asBoolean();
                result.put(id, premium);
                if (ttl > 0 && cache.size() < 10000) cache.put(id, new Entry(premium, System.currentTimeMillis() + ttl));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpResponseException(503, "Permission lookup interrupted");
            } catch (ExecutionException | TimeoutException e) {
                throw new HttpResponseException(503, "Permissions are temporarily unavailable");
            } finally {
                if (loaded && user != null) manager.cleanupUser(user);
            }
        }
        return result;
    }
}
