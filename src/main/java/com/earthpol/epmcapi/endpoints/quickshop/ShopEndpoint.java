package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.endpoints.PostEndpoint;
import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.earthpol.epmcapi.networking.FieldSelection;
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

public class ShopEndpoint extends PostEndpoint<Object> {
    private static final long DEFAULT_OWNER_LOOKUP_CACHE_TTL_MS = 5_000L;

    private final long ownerLookupCacheTtlMillis;
    private volatile OwnerShopIndex ownerShopIndex = OwnerShopIndex.empty();

    public ShopEndpoint() {
        ownerLookupCacheTtlMillis = EPMCAPI.getInstance()
                .getConfig()
                .getLong("shops-endpoint.owner_lookup_cache_ms", DEFAULT_OWNER_LOOKUP_CACHE_TTL_MS);
    }

    @Override
    public Object getObjectOrNull(JsonElement element) {
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
            return shop;
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

    @Override
    public JsonElement getJsonElement(Object object) {
        return getJsonElement(object, FieldSelection.all());
    }

    @Override
    public JsonElement getJsonElement(Object object, FieldSelection fields) {
        if (object instanceof Shop shop) {
            return ShopJson.serialize(shop, fields);
        } else if (object instanceof OwnerShopLookup ownerLookup) {
            JsonArray array = new JsonArray();
            for (long shopId : ownerLookup.shopIds()) {
                Shop shop = QuickShopAPI.getInstance().getShopManager().getShop(shopId);
                if (shop != null && shop.getOwner() != null && ownerLookup.ownerUuid().equals(shop.getOwner().getUniqueId())) {
                    array.add(ShopJson.serialize(shop, fields));
                }
            }
            if (array.isEmpty()) {
                throw new BadRequestResponse("No shops found for owner " + ownerLookup.ownerUuid());
            }
            return array;
        }
        throw new BadRequestResponse("Invalid object type for JSON serialization.");
    }

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
