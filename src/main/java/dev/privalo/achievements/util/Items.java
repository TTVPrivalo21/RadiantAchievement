package dev.privalo.achievements.util;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.IconSpec;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds item stacks from {@link IconSpec}s, applying custom textures in a version tolerant way.
 */
public final class Items {

    private Items() {
    }

    public static Material material(String raw, Material def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Material m = Material.matchMaterial(raw.trim());
        return m == null || !m.isItem() ? def : m;
    }

    public static ItemStack build(RadiantAchievements plugin, IconSpec icon, int amount) {
        ItemStack stack = null;
        if (!icon.oraxenItem().isEmpty() && plugin.hooks().oraxen() != null) {
            stack = plugin.hooks().oraxen().item(icon.oraxenItem());
        }
        if (stack == null) {
            stack = new ItemStack(material(icon.material(), Material.PAPER));
        }
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (icon.customModelData() > 0) {
            meta.setCustomModelData(icon.customModelData());
        }
        if (icon.hasItemModel()) {
            applyItemModel(meta, icon.itemModel());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** item_model component exists since 1.21.2; ignored silently on older servers. */
    public static void applyItemModel(ItemMeta meta, String model) {
        if (!ServerVersion.isAtLeast(1, 21, 2)) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(model.toLowerCase(java.util.Locale.ROOT));
        if (key == null) {
            return;
        }
        try {
            meta.setItemModel(key);
        } catch (NoSuchMethodError ignored) {
            // API not available on this server build
        }
    }

    public static void hideExtras(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
    }

    public static void glint(ItemMeta meta, boolean glint) {
        try {
            meta.setEnchantmentGlintOverride(glint);
        } catch (NoSuchMethodError ignored) {
            // pre 1.20.5
        }
    }
}
