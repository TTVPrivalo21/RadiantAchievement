package dev.privalo.achievements.hook;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.TriggerType;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * NPC_INTERACT trigger: filter values can be NPC ids or names (colors stripped).
 */
public final class CitizensHook implements Listener {

    private final RadiantAchievements plugin;

    CitizensHook(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        String name = ChatColor.stripColor(npc.getName());
        plugin.manager().handle(event.getClicker(), TriggerType.NPC_INTERACT,
                List.of(String.valueOf(npc.getId()), name == null ? "" : name), 1);
    }
}
