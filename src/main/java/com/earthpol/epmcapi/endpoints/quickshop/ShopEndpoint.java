package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.networking.GameThread;
import com.earthpol.epmcapi.networking.QueryOptions;
import com.earthpol.epmcapi.utils.JSONUtil;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopEndpoint {
    private static final long DEFAULT_OWNER_LOOKUP_CACHE_TTL_MS = 5_000L;

    private final long ownerLookupCacheTtlMillis;
    private volatile OwnerShopIndex ownerShopIndex = OwnerShopIndex.empty();

    public ShopEndpoint() {
        ownerLookupCacheTtlMillis = EPMCAPI.getInstance()
                .getConfig()
                .getLong("shops-endpoint.owner_lookup_cache_ms", DEFAULT_OWNER_LOOKUP_CACHE_TTL_MS);
    }

    private Object getObjectOrNull(JsonElement element) {
        String input = JSONUtil.getJsonElementAsStringOrNull(element);
        if (input == null) {
            throw new BadRequestResponse("Your query contains a value that is not a string");
        }
        try {
            // First try: interpret the string as a numeric shop id.
            long shopId = Long.parseLong(input);
            Shop shop = QuickShopAPI.getInstance().getShopManager().getShop(shopId);
            if (shop == null) {
                throw new BadRequestResponse("No shop found with id " + shopId);
            }
            return ShopAccess.target(shop, null);
        } catch (NumberFormatException e) {
            // If not numeric, treat it as a UUID (owner UUID)
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(input);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestResponse("Invalid UUID format.");
            }

            List<Long> ownerShopIds = getOwnerShopIndex().shopIdsByOwner().get(ownerUuid);
            if (ownerShopIds == null || ownerShopIds.isEmpty()) {
                throw new BadRequestResponse("No shops found for owner " + ownerUuid);
            }
            return new OwnerShopLookup(ownerUuid, List.copyOf(ownerShopIds));
        }
    }

    /** Resolve identities globally, serialize inventories on their regions, assemble on the HTTP worker. */
    public String handleQuery(JsonArray identifiers, QueryOptions options) {
        Batch batch = GameThread.read(() -> {
            List<Object> matches = new ArrayList<>();
            for (JsonElement identifier : identifiers) matches.add(getObjectOrNull(identifier));
            List<Entry> entries = new ArrayList<>();
            for (Object match : options.page(matches)) {
                if (match instanceof ShopAccess.Target target) {
                    entries.add(new Entry(null, List.of(target)));
                } else if (match instanceof OwnerShopLookup owner) {
                    List<ShopAccess.Target> targets = new ArrayList<>();
                    for (long id : owner.shopIds()) {
                        Shop shop = QuickShopAPI.getInstance().getShopManager().getShop(id);
                        if (shop != null && shop.getOwner() != null && owner.ownerUuid().equals(shop.getOwner().getUniqueId()))
                            targets.add(ShopAccess.target(shop, owner.ownerUuid()));
                    }
                    entries.add(new Entry(owner.ownerUuid(), targets));
                }
            }
            return new Batch(entries, matches.size());
        });
        List<ShopAccess.Target> targets = batch.entries().stream().flatMap(entry -> entry.targets().stream()).toList();
        List<JsonElement> snapshots = ShopAccess.read(targets, shop -> ShopJson.serialize(shop, options.fields()));
        JsonArray results = new JsonArray();
        int cursor = 0;
        for (Entry entry : batch.entries()) {
            if (entry.owner() == null) {
                JsonElement shop = snapshots.get(cursor++);
                if (shop == null) throw new BadRequestResponse("No shop found with id " + entry.targets().get(0).id());
                results.add(shop);
            } else {
                JsonArray ownerShops = new JsonArray();
                for (int i = 0; i < entry.targets().size(); i++) {
                    JsonElement shop = snapshots.get(cursor++);
                    if (shop != null) ownerShops.add(shop);
                }
                if (ownerShops.isEmpty()) throw new BadRequestResponse("No shops found for owner " + entry.owner());
                results.add(ownerShops);
            }
        }
        return options.response(results, batch.total());
    }

    private record Entry(UUID owner, List<ShopAccess.Target> targets) {}
    private record Batch(List<Entry> entries, int total) {}

    private OwnerShopIndex getOwnerShopIndex() {
        if (ownerLookupCacheTtlMillis <= 0) {
            return buildOwnerShopIndex();
        }

        long now = System.currentTimeMillis();
        OwnerShopIndex currentIndex = ownerShopIndex;
        if (currentIndex.isValidAt(now)) {
            return currentIndex;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            currentIndex = ownerShopIndex;
            if (!currentIndex.isValidAt(now)) {
                currentIndex = buildOwnerShopIndex(now + ownerLookupCacheTtlMillis);
                ownerShopIndex = currentIndex;
            }
            return currentIndex;
        }
    }

    private OwnerShopIndex buildOwnerShopIndex() {
        return buildOwnerShopIndex(0L);
    }

    private OwnerShopIndex buildOwnerShopIndex(long expiresAtMillis) {
        Map<UUID, List<Long>> shopIdsByOwner = new HashMap<>();
        Collection<Shop> allShops = QuickShopAPI.getInstance().getShopManager().getAllShops();
        for (Shop shop : allShops) {
            if (shop.getOwner() == null) {
                continue;
            }
            shopIdsByOwner
                    .computeIfAbsent(shop.getOwner().getUniqueId(), ignored -> new ArrayList<>())
                    .add(shop.getShopId());
        }
        return new OwnerShopIndex(shopIdsByOwner, expiresAtMillis);
    }

    private record OwnerShopLookup(UUID ownerUuid, List<Long> shopIds) {}

    private record OwnerShopIndex(Map<UUID, List<Long>> shopIdsByOwner, long expiresAtMillis) {
        private static OwnerShopIndex empty() {
            return new OwnerShopIndex(Collections.emptyMap(), 0L);
        }

        private boolean isValidAt(long currentTimeMillis) {
            return currentTimeMillis < expiresAtMillis;
        }
    }
}
