package com.earthpol.epmcapi.endpoints.quickshop;

import com.earthpol.epmcapi.networking.FieldSelection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.text.PaperComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Read-only item descriptions; called on the game thread by the shop serializer. */
final class ShopItemJson {
    private static final PlainTextComponentSerializer TEXT = PlainTextComponentSerializer.builder()
            .flattener(PaperComponents.flattener()).build();

    private ShopItemJson() {}

    static JsonElement serialize(ItemStack item, FieldSelection fields) {
        if (item == null) return JsonNull.INSTANCE;
        JsonObject json = new JsonObject();
        json.addProperty("material", item.getType().getKey().toString());
        json.addProperty("amount", item.getAmount());
        if (fields.includes("item.translationKey")) json.addProperty("translationKey", item.translationKey());
        if (fields.includes("item.displayName") || fields.includes("item.displayNameComponent")) {
            Component name = item.effectiveName();
            if (fields.includes("item.displayName")) json.addProperty("displayName", TEXT.serialize(name));
            if (fields.includes("item.displayNameComponent")) json.add("displayNameComponent", component(name));
        }

        ItemMeta meta = item.getItemMeta();
        if (fields.includes("item.lore") || fields.includes("item.loreComponents")) {
            List<Component> lore = meta == null ? null : meta.lore();
            JsonArray text = new JsonArray(), components = new JsonArray();
            if (lore != null) for (Component line : lore) {
                if (fields.includes("item.lore")) text.add(TEXT.serialize(line));
                if (fields.includes("item.loreComponents")) components.add(component(line));
            }
            json.add("lore", text);
            json.add("loreComponents", components);
        }
        if (fields.includes("item.enchantments"))
            json.add("enchantments", enchantments(meta == null ? Map.of() : meta.getEnchants()));
        if (fields.includes("item.storedEnchantments"))
            json.add("storedEnchantments", enchantments(meta instanceof EnchantmentStorageMeta book ? book.getStoredEnchants() : Map.of()));
        if (fields.includes("item.durability")) json.add("durability", durability(item, meta));
        if (fields.includes("item.unbreakable")) json.addProperty("unbreakable", meta != null && meta.isUnbreakable());
        if (fields.includes("item.glintOverride"))
            json.addProperty("glintOverride", meta != null && meta.hasEnchantmentGlintOverride() ? meta.getEnchantmentGlintOverride() : null);
        if (fields.includes("item.customModelData")) json.add("customModelData", customModelData(meta));
        if (fields.includes("item.potion")) json.add("potion", meta instanceof PotionMeta potion ? potion(potion) : JsonNull.INSTANCE);
        return json;
    }

    private static JsonElement component(Component component) {
        return JsonParser.parseString(GsonComponentSerializer.gson().serialize(component));
    }

    private static JsonArray enchantments(Map<Enchantment, Integer> enchantments) {
        JsonArray result = new JsonArray();
        enchantments.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString())).forEach(entry -> {
            Enchantment enchantment = entry.getKey();
            JsonObject json = new JsonObject();
            json.addProperty("id", enchantment.getKey().toString());
            json.addProperty("name", TEXT.serialize(enchantment.description()));
            json.addProperty("level", entry.getValue());
            json.addProperty("displayName", TEXT.serialize(enchantment.displayName(entry.getValue())));
            result.add(json);
        });
        return result;
    }

    private static JsonElement durability(ItemStack item, ItemMeta meta) {
        if (!(meta instanceof Damageable damageable)) return JsonNull.INSTANCE;
        int maximum = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (maximum <= 0) return JsonNull.INSTANCE;
        int damage = damageable.getDamage();
        JsonObject json = new JsonObject();
        json.addProperty("damage", damage);
        json.addProperty("max", maximum);
        json.addProperty("remaining", Math.max(0, maximum - damage));
        return json;
    }

    private static JsonElement customModelData(ItemMeta meta) {
        if (meta == null) return JsonNull.INSTANCE;
        var data = meta.getCustomModelDataComponent();
        if (data.getFloats().isEmpty() && data.getFlags().isEmpty() && data.getStrings().isEmpty() && data.getColors().isEmpty())
            return JsonNull.INSTANCE;
        JsonObject json = new JsonObject();
        JsonArray floats = new JsonArray(), flags = new JsonArray(), strings = new JsonArray(), colors = new JsonArray();
        data.getFloats().forEach(floats::add);
        data.getFlags().forEach(flags::add);
        data.getStrings().forEach(strings::add);
        data.getColors().forEach(color -> colors.add(color.asRGB()));
        json.add("floats", floats);
        json.add("flags", flags);
        json.add("strings", strings);
        json.add("colors", colors);
        return json;
    }

    private static JsonObject potion(PotionMeta meta) {
        JsonObject json = new JsonObject();
        var base = meta.getBasePotionType();
        json.addProperty("baseType", base == null ? null : base.getKey().toString());
        json.add("baseEffects", effects(base == null ? List.of() : base.getPotionEffects()));
        json.add("customEffects", effects(meta.getCustomEffects()));
        json.addProperty("color", meta.hasColor() ? meta.getColor().asRGB() : null);
        return json;
    }

    private static JsonArray effects(List<PotionEffect> effects) {
        JsonArray result = new JsonArray();
        for (PotionEffect effect : effects) {
            JsonObject json = new JsonObject();
            json.addProperty("id", effect.getType().getKey().toString());
            json.addProperty("name", TEXT.serialize(Component.translatable(effect.getType())));
            json.addProperty("level", effect.getAmplifier() + 1);
            json.addProperty("durationTicks", effect.getDuration());
            json.addProperty("ambient", effect.isAmbient());
            json.addProperty("particles", effect.hasParticles());
            json.addProperty("icon", effect.hasIcon());
            result.add(json);
        }
        return result;
    }
}
