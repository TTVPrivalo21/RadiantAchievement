package dev.privalo.achievements.listener;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Data loading/saving, JOIN trigger, resource pack delivery/diagnostics and firework reward protection.
 */
public final class ConnectionListener implements Listener {

    private final RadiantAchievements plugin;

    public ConnectionListener(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.data().preload(event.getUniqueId(), event.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.data().ensureLoaded(player, () -> {
            plugin.manager().handle(player, TriggerType.JOIN, "any", 1);
            plugin.tracking().checkPermissions(player);
            plugin.triggers().checkPosition(player, player.getLocation());
        });
        // Small delay: some clients ignore pack requests sent in the very first tick of the play phase.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.packSender().send(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.notifications().clear(uuid);
        plugin.tracking().forget(uuid);
        plugin.data().unload(uuid);
    }

    /** Explains download problems in the console (usually a closed port or wrong public-address). */
    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> plugin.getLogger().warning(event.getPlayer().getName()
                    + " could not load the resource pack (" + event.getStatus() + ") from "
                    + plugin.packSender().urlFor(event.getPlayer())
                    + " — check that the HTTP port is reachable or set resource-pack.http.public-address.");
            case DECLINED -> plugin.getLogger().info(event.getPlayer().getName() + " declined the resource pack.");
            default -> {
                // ACCEPTED, DOWNLOADED, SUCCESSFULLY_LOADED, DISCARDED: nothing to report
            }
        }
    }

    /** Reward fireworks never hurt anyone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().getPersistentDataContainer().has(plugin.fireworkKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
