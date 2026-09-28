package com.earthpol.epmcapi.utils;

import java.util.function.Supplier;

public final class ResponseSnapshot {
    private final long ttlMillis;
    private volatile String cachedResponse;
    private volatile long expiresAtMillis;

    public ResponseSnapshot(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String get(Supplier<String> builder) {
        if (ttlMillis <= 0) {
            return builder.get();
        }

        long now = System.currentTimeMillis();
        String response = cachedResponse;
        if (response != null && now < expiresAtMillis) {
            return response;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (cachedResponse == null || now >= expiresAtMillis) {
                cachedResponse = builder.get();
                expiresAtMillis = now + ttlMillis;
            }
            return cachedResponse;
        }
    }

    public void invalidate() {
        cachedResponse = null;
        expiresAtMillis = 0L;
    }
}
