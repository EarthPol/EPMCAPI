package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.EPMCAPI;
import com.earthpol.epmcapi.endpoints.GetEndpoint;
import com.earthpol.epmcapi.networking.FieldSelection;
import com.earthpol.epmcapi.utils.ResponseSnapshot;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import com.google.gson.JsonArray;
import org.bukkit.Location;

import java.util.Set;

public class ShopListEndpoint extends GetEndpoint {
    private final ResponseSnapshot snapshot;
    private final Set<String> allowedWorlds;
    private final double maxPrice;
    private final boolean includeOutOfStock;

    public ShopListEndpoint() {
        var config = EPMCAPI.getInstance().getConfig();
        long cacheMillis = config.getLong("shops-endpoint.list_cache_ms", 5000);
        maxPrice = config.getDouble("shops-endpoint.max_price", -1);
        if (cacheMillis < 0 || !Double.isFinite(maxPrice) || maxPrice < 0 && maxPrice != -1)
            throw new IllegalArgumentException("Invalid shop listing limits");
        snapshot = new ResponseSnapshot(cacheMillis);
        allowedWorlds = Set.copyOf(config.getStringList("shops-endpoint.allowed_worlds"));
        includeOutOfStock = config.getBoolean("shops-endpoint.include_out_of_stock", true);
    }

    @Override
    public String lookup() {
        return snapshot.get(() -> getJsonElement().toString());
    }

    @Override
    public JsonArray getJsonElement() {
        JsonArray array = new JsonArray();
        for (Shop shop : QuickShopAPI.getInstance().getShopManager().getAllShops()) {
            if (maxPrice >= 0 && shop.getPrice() > maxPrice) continue;
            Location location = shop.getLocation();
            if (!allowedWorlds.isEmpty() && (location == null || location.getWorld() == null
                    || !allowedWorlds.contains(location.getWorld().getName()))) continue;
            if (!includeOutOfStock && !shop.isUnlimited()
                    && (shop.isSelling() && shop.getRemainingStock() < 1 || shop.isBuying() && shop.getRemainingSpace() < 1)) continue;
            array.add(ShopJson.serialize(shop, FieldSelection.all()));
        }
        return array;
    }
}
