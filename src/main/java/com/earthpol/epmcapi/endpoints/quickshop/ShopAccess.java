package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.networking.GameThread;
import com.earthpol.epmcapi.exception.HttpResponseException;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import com.google.gson.JsonElement;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Function;

/** Pass shop identities/locations between schedulers, and return only detached JSON. */
final class ShopAccess {
    private ShopAccess() {}

    record Target(long id, Location location, UUID expectedOwner) {}

    static Target target(Shop shop, UUID expectedOwner) {
        Location location = shop.getLocation();
        if (location == null || location.getWorld() == null)
            throw new HttpResponseException(503, "Shop world is unavailable");
        return new Target(shop.getShopId(), location.clone(), expectedOwner);
    }

    static List<JsonElement> read(List<Target> targets, Function<Shop, JsonElement> serialize) {
        List<Future<JsonElement>> pending = new ArrayList<>();
        try {
            for (Target target : targets) {
                pending.add(GameThread.at(target.location(), () -> {
                    // Resolve again on the owning region; a shop may have been removed or replaced.
                    Shop shop = QuickShopAPI.getInstance().getShopManager().getShop(target.location(), true);
                    if (shop == null || shop.getShopId() != target.id()) return null;
                    if (target.expectedOwner() != null && (shop.getOwner() == null
                            || !target.expectedOwner().equals(shop.getOwner().getUniqueId()))) return null;
                    return serialize.apply(shop);
                }));
            }
            return GameThread.awaitAll(pending);
        } finally {
            // Also cancel tasks when scheduling fails partway through a batch.
            pending.forEach(future -> future.cancel(false));
        }
    }
}
