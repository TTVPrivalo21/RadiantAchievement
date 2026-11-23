package dev.privalo.achievements.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.util.Items;
import dev.privalo.achievements.util.ServerVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Shows real advancement toasts by registering a temporary hidden advancement, granting it and removing it.
 */
public final class ToastSender {

    private final RadiantAchievements plugin;
    private final Set<NamespacedKey> active = ConcurrentHashMap.newKeySet();
    private final PacketToast packet = new PacketToast();
    private boolean broken;
    private boolean packetBroken;

    public ToastSender(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * @return false when toasts are not supported on this server (caller should fall back).
     */
    public boolean send(Player player, IconSpec icon, Component title, Component description, String frame) {
        String json = buildJson(icon, title, description, frame);
        String path = "toast/" + UUID.randomUUID().toString().replace("-", "");
        // 1) per-player packet: instant, nothing registered server side, no advancement reload for others
        if (!packetBroken && packet.available()) {
            try {
                packet.show(player, plugin.getName().toLowerCase(java.util.Locale.ROOT), path, json);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        try {
                            packet.remove(player, plugin.getName().toLowerCase(java.util.Locale.ROOT), path);
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            // the client drops unknown advancements on its next full sync anyway
                        }
                    }
                }, 5L);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                packetBroken = true;
                plugin.getLogger().log(Level.WARNING, "Packet toasts failed on " + ServerVersion.asString()
                        + ", using the Bukkit advancement API instead.", ex);
            }
        }
        // 2) Bukkit API fallback (registers a temporary advancement; Paper resyncs advancements)
        if (broken) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, path);
        try {
            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
            if (advancement == null) {
                broken = true;
                return false;
            }
            active.add(key);
            player.getAdvancementProgress(advancement).awardCriteria("trigger");
            Bukkit.getScheduler().runTaskLater(plugin, () -> remove(player, key), 40L);
            return true;
        } catch (Throwable ex) {
            broken = true;
            plugin.getLogger().log(Level.WARNING, "Advancement toasts are not supported on this server ("
                    + ServerVersion.asString() + "), falling back to " + plugin.settings().toastFallback + ".", ex);
            remove(player, key);
            return false;
        }
    }

    private void remove(Player player, NamespacedKey key) {
        try {
            Advancement adv = Bukkit.getAdvancement(key);
            if (adv != null && player.isOnline()) {
                player.getAdvancementProgress(adv).revokeCriteria("trigger");
            }
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Throwable ignored) {
            // already gone
        } finally {
            active.remove(key);
        }
    }

    /** Removes every temporary advancement (plugin disable). */
    public void cleanup() {
        for (NamespacedKey key : Set.copyOf(active)) {
            try {
                Bukkit.getUnsafe().removeAdvancement(key);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        active.clear();
    }

    private String buildJson(IconSpec icon, Component title, Component description, String frame) {
        Material material = Items.material(icon.material(), Material.GOLD_INGOT);
        JsonObject iconJson = new JsonObject();
        iconJson.addProperty("id", material.getKey().toString());
        JsonObject components = new JsonObject();
        if (icon.customModelData() > 0) {
            if (ServerVersion.isAtLeast(1, 21, 4)) {
                JsonObject cmd = new JsonObject();
                JsonArray floats = new JsonArray();
                floats.add((float) icon.customModelData());
                cmd.add("floats", floats);
                components.add("minecraft:custom_model_data", cmd);
            } else {
                components.addProperty("minecraft:custom_model_data", icon.customModelData());
            }
        }
        if (icon.hasItemModel() && ServerVersion.isAtLeast(1, 21, 2)) {
            components.addProperty("minecraft:item_model", icon.itemModel().toLowerCase(Locale.ROOT));
        }
        if (!components.isEmpty()) {
            iconJson.add("components", components);
        }

        JsonObject display = new JsonObject();
        display.add("icon", iconJson);
        display.add("title", JsonParser.parseString(GsonComponentSerializer.gson().serialize(title)));
        display.add("description", JsonParser.parseString(GsonComponentSerializer.gson().serialize(description)));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", true);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("trigger", trigger);

        JsonObject root = new JsonObject();
        root.add("criteria", criteria);
        root.add("display", display);
        return root.toString();
    }
}
