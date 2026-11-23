package dev.privalo.achievements.hook;

import dev.privalo.achievements.RadiantAchievements;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Oraxen bridge: icons from Oraxen item ids and access to Oraxen's pack folder for asset merging.
 */
public final class OraxenHook {

    private final RadiantAchievements plugin;

    OraxenHook(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /** @return the built Oraxen item or null if the id does not exist. */
    public ItemStack item(String id) {
        if (!OraxenItems.exists(id)) {
            plugin.getLogger().warning("Oraxen item '" + id + "' does not exist.");
            return null;
        }
        ItemBuilder builder = OraxenItems.getItemById(id);
        return builder == null ? null : builder.build();
    }

    /** plugins/Oraxen/pack */
    public File packFolder() {
        Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        File base = oraxen != null ? oraxen.getDataFolder() : new File(plugin.getDataFolder().getParentFile(), "Oraxen");
        return new File(base, "pack");
    }

    /** Asks Oraxen to rebuild and resend its pack. */
    public void reloadPack() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "oraxen reload pack");
    }
}
