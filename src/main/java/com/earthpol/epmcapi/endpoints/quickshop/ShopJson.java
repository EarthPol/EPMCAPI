package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.networking.FieldSelection;
import com.ghostchu.quickshop.api.shop.Shop;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.bukkit.Location;

/** One response format for shop lists, IDs, and owner lookups. */
final class ShopJson {
    private ShopJson() {}

    static JsonElement serialize(Shop shop, FieldSelection fields) {
        JsonObject json = new JsonObject();
        json.addProperty("id", shop.getShopId());
        var owner = shop.getOwner();
        json.addProperty("owner", owner == null || owner.getUniqueId() == null ? null : owner.getUniqueId().toString());
        if (fields.includes("item")) json.add("item", ShopItemJson.serialize(shop.getItem(), fields));
        double price = shop.getPrice();
        int tradeAmount = shop.getShopStackingAmount();
        // QuickShop charges price for one trade, which may contain multiple items.
        json.addProperty("price", price);
        json.addProperty("tradeAmount", tradeAmount);
        json.addProperty("unitPrice", tradeAmount > 0 ? price / tradeAmount : null);
        if (fields.includes("currency")) json.addProperty("currency", shop.getCurrency()); // null is the default currency
        json.addProperty("type", shop.shopType().identifier());
        json.addProperty("unlimited", shop.isUnlimited());
        boolean availability = fields.includes("inStock");
        boolean buying = availability && shop.isBuying();
        int space = fields.includes("space") || availability && buying ? shop.getRemainingSpace() : 0;
        int stock = fields.includes("stock") || availability && !buying ? shop.getRemainingStock() : 0;
        if (fields.includes("space")) json.addProperty("space", space);
        if (fields.includes("stock")) json.addProperty("stock", stock);
        if (availability) json.addProperty("inStock", !shop.isFrozen() && (shop.isUnlimited() || (buying ? space : stock) > 0));
        if (fields.includes("location")) {
            Location location = shop.getLocation();
            if (location == null) json.add("location", JsonNull.INSTANCE);
            else {
                JsonObject coordinates = new JsonObject();
                coordinates.addProperty("world", location.getWorld() == null ? null : location.getWorld().getName());
                coordinates.addProperty("x", location.getX());
                coordinates.addProperty("y", location.getY());
                coordinates.addProperty("z", location.getZ());
                json.add("location", coordinates);
            }
        }
        return fields.project(json);
    }
}
