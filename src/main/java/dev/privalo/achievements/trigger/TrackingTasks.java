package dev.privalo.achievements.trigger;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.data.PlayerData;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repeating tasks: PLAYTIME progress, PERMISSION checks and autosave.
 */
public final class TrackingTasks {

    private final RadiantAchievements plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** Seconds played that did not complete a full minute yet. */
    private final Map<UUID, Integer> pendingSeconds = new ConcurrentHashMap<>();

    public TrackingTasks(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        int playtime = plugin.settings().playtimeIntervalSeconds;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> tickPlaytime(playtime), playtime * 20L, playtime * 20L));
        int perm = plugin.settings().permissionCheckSeconds;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                checkPermissions(p);
            }
        }, perm * 20L, perm * 20L));
        long autosave = plugin.settings().autosaveMinutes * 60L * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.data().autosave(), autosave, autosave));
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private void tickPlaytime(int seconds) {
        if (!plugin.achievements().hasTrigger(TriggerType.PLAYTIME)) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            int total = pendingSeconds.getOrDefault(p.getUniqueId(), 0) + seconds;
            int minutes = total / 60;
            pendingSeconds.put(p.getUniqueId(), total % 60);
            if (minutes > 0) {
                plugin.manager().handle(p, TriggerType.PLAYTIME, "any", minutes);
            }
        }
    }

    public void forget(UUID uuid) {
        pendingSeconds.remove(uuid);
    }

    /** Unlocks PERMISSION achievements whose permission the player has. */
    public void checkPermissions(Player player) {
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        for (Achievement a : plugin.achievements().byTrigger(TriggerType.PERMISSION)) {
            if (!data.isUnlocked(a.id()) && player.hasPermission(a.trigger().permission())) {
                plugin.manager().handleSingle(player, a, a.target());
            }
        }
    }
}
