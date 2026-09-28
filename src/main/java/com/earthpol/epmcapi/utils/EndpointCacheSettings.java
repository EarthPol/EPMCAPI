package com.earthpol.epmcapi.utils;

import com.earthpol.epmcapi.EPMCAPI;

public final class EndpointCacheSettings {
    private static final long DEFAULT_SERVER_SNAPSHOT_TTL_MS = 1_000L;
    private static final long DEFAULT_LIST_SNAPSHOT_TTL_MS = 5_000L;

    private EndpointCacheSettings() {}

    public static long getServerSnapshotTtlMs() {
        return getLong("networking.snapshot_cache_ms.server", DEFAULT_SERVER_SNAPSHOT_TTL_MS);
    }

    public static long getListSnapshotTtlMs() {
        return getLong("networking.snapshot_cache_ms.list", DEFAULT_LIST_SNAPSHOT_TTL_MS);
    }

    private static long getLong(String path, long defaultValue) {
        EPMCAPI plugin = EPMCAPI.getInstance();
        if (plugin == null) {
            return defaultValue;
        }
        return plugin.getConfig().getLong(path, defaultValue);
    }
}
