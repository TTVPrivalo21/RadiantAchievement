package dev.privalo.achievements.hook;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.TriggerType;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * MYTHIC_KILL trigger: filter values are MythicMobs internal names.
 */
public final class MythicMobsHook implements Listener {

    private final RadiantAchievements plugin;

    MythicMobsHook(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player player) {
            plugin.manager().handle(player, TriggerType.MYTHIC_KILL, event.getMobType().getInternalName(), 1);
        }
    }
}
