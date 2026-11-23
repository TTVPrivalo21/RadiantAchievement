package dev.privalo.achievements.core;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.api.event.AchievementProgressEvent;
import dev.privalo.achievements.api.event.AchievementUnlockEvent;
import dev.privalo.achievements.data.PlayerData;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.AnnounceScope;
import dev.privalo.achievements.model.AnnounceSpec;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.TriggerType;
import dev.privalo.achievements.reward.Reward;
import dev.privalo.achievements.util.Placeholders;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Core logic: trigger dispatching, progress, unlocking, rewards and notifications.
 * Every method must be called on the main thread.
 */
public final class AchievementManager {

    private final RadiantAchievements plugin;

    public AchievementManager(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    // ── Trigger dispatching ─────────────────────────────────────────

    public void handle(Player player, TriggerType type, String key, long amount) {
        handle(player, type, List.of(key), amount);
    }

    /**
     * Adds {@code amount} to every locked achievement of {@code type} whose filter matches any key.
     */
    public void handle(Player player, TriggerType type, Collection<String> keys, long amount) {
        List<Achievement> candidates = plugin.achievements().byTrigger(type);
        if (candidates.isEmpty() || amount <= 0 || !environmentAllows(player, type)) {
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        for (Achievement a : candidates) {
            if (data.isUnlocked(a.id()) || !a.trigger().worldAllowed(player.getWorld().getName())
                    || !a.trigger().matches(keys)) {
                continue;
            }
            addProgress(player, a, amount);
        }
    }

    /**
     * Adds progress to one specific achievement, applying the same world/gamemode checks as triggers.
     */
    public void handleSingle(Player player, Achievement a, long amount) {
        PlayerData data = plugin.data().get(player);
        if (data == null || data.isUnlocked(a.id()) || !environmentAllows(player, a.trigger().type())
                || !a.trigger().worldAllowed(player.getWorld().getName())) {
            return;
        }
        addProgress(player, a, amount);
    }

    /**
     * For "reach a value" triggers (LEVEL_REACH): progress becomes max(current, value).
     */
    public void handleReach(Player player, TriggerType type, long value) {
        PlayerData data = plugin.data().get(player);
        if (data == null || !environmentAllows(player, type)) {
            return;
        }
        for (Achievement a : plugin.achievements().byTrigger(type)) {
            if (!data.isUnlocked(a.id()) && a.trigger().worldAllowed(player.getWorld().getName())
                    && value > data.progress(a.id())) {
                setProgress(player, a, value);
            }
        }
    }

    private boolean environmentAllows(Player player, TriggerType type) {
        if (type == TriggerType.MANUAL || type == TriggerType.JOIN || type == TriggerType.PERMISSION) {
            return true;
        }
        if (plugin.settings().disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return !plugin.settings().ignoreCreative || (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR);
    }

    public boolean requirementsMet(PlayerData data, Achievement a) {
        for (String req : a.requires()) {
            if (!data.isUnlocked(req)) {
                return false;
            }
        }
        return true;
    }

    // ── Progress ────────────────────────────────────────────────────

    /**
     * @return the resulting progress
     */
    public long addProgress(Player player, Achievement a, long amount) {
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return 0;
        }
        return setProgress(player, a, data.progress(a.id()) + amount);
    }

    /**
     * Sets the progress of an online player, firing {@link AchievementProgressEvent} and unlocking at the target.
     *
     * @return the resulting progress
     */
    public long setProgress(Player player, Achievement a, long value) {
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return 0;
        }
        long previous = data.progress(a.id());
        if (data.isUnlocked(a.id())) {
            return a.target();
        }
        long target = a.target();
        long next = Math.max(0, Math.min(value, target));
        if (next == previous) {
            return previous;
        }
        AchievementProgressEvent event = new AchievementProgressEvent(player, a, previous, next);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return previous;
        }
        next = Math.max(0, Math.min(event.getNewProgress(), target));
        data.setProgress(a.id(), next);
        if (next >= target) {
            // Progress is kept at the target; if requirements are missing it unlocks once they are met.
            if (requirementsMet(data, a)) {
                unlock(player, a, AchievementUnlockEvent.Cause.PROGRESS);
            }
        } else {
            milestone(player, a, previous, next);
        }
        return data.progress(a.id());
    }

    private void milestone(Player player, Achievement a, long previous, long next) {
        if (!plugin.settings().progressActionbar || a.target() <= 1 || next <= previous) {
            return;
        }
        int step = plugin.settings().progressStepPercent;
        long before = previous * 100 / a.target() / step;
        long after = next * 100 / a.target() / step;
        if (after > before) {
            TagResolver ctx = Placeholders.achievement(a, player.getName(), next, a.designId());
            player.sendActionBar(Text.parse(player, plugin.settings().progressFormat, ctx));
        }
    }

    // ── Unlocking ───────────────────────────────────────────────────

    /**
     * Unlocks an achievement for an online player.
     *
     * @return true if it was unlocked now (false if already unlocked, not loaded or cancelled)
     */
    public boolean unlock(Player player, Achievement a, AchievementUnlockEvent.Cause cause) {
        PlayerData data = plugin.data().get(player);
        if (data == null || data.isUnlocked(a.id())) {
            return false;
        }
        AchievementUnlockEvent event = new AchievementUnlockEvent(player, a, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        if (!data.unlock(a.id(), System.currentTimeMillis())) {
            return false;
        }
        data.setProgress(a.id(), a.target());
        Design design = plugin.designs().get(a.designId());
        TagResolver ctx = Placeholders.achievement(a, player.getName(), a.target(), design.id());
        if (event.isGiveRewards()) {
            giveRewards(player, a, ctx);
        }
        if (event.isNotify()) {
            plugin.notifications().show(player, design, ctx, a.icon(), a.soundOverride());
        }
        if (event.isAnnounce()) {
            plugin.announcer().announce(player, plugin.announcer().resolve(a, design), ctx);
        }
        unlockDependents(player, data);
        return true;
    }

    /** Achievements with target already reached but blocked by "requires" are unlocked once requirements are met. */
    private void unlockDependents(Player player, PlayerData data) {
        for (Achievement other : plugin.achievements().all()) {
            if (!data.isUnlocked(other.id()) && !other.requires().isEmpty()
                    && data.progress(other.id()) >= other.target() && requirementsMet(data, other)) {
                unlock(player, other, AchievementUnlockEvent.Cause.PROGRESS);
            }
        }
    }

    private void giveRewards(Player player, Achievement a, TagResolver ctx) {
        for (Reward reward : a.rewards()) {
            try {
                reward.give(plugin, player, ctx);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Reward " + reward + " of '" + a.id() + "' failed for "
                        + player.getName(), ex);
            }
        }
    }

    /**
     * Unlocks without events, rewards or notifications (offline players).
     */
    public boolean unlockSilently(PlayerData data, Achievement a) {
        if (!data.unlock(a.id(), System.currentTimeMillis())) {
            return false;
        }
        data.setProgress(a.id(), a.target());
        return true;
    }

    /** Shows the unlock notification and announcement of an achievement without unlocking it (preview). */
    public void preview(Player player, Achievement a) {
        Design design = plugin.designs().get(a.designId());
        TagResolver ctx = Placeholders.achievement(a, player.getName(), a.target(), design.id());
        plugin.notifications().show(player, design, ctx, a.icon(), a.soundOverride());
        // Previews never spam public chat: the announcement is forced to PRIVATE.
        AnnounceSpec spec = plugin.announcer().resolve(a, design);
        plugin.announcer().announce(player,
                new AnnounceSpec(spec.mode(), AnnounceScope.PRIVATE, spec.compact(), spec.designed()), ctx);
    }

    // ── Queries ─────────────────────────────────────────────────────

    public double completion(PlayerData data) {
        int total = plugin.achievements().size();
        if (total == 0) {
            return 0;
        }
        int unlocked = 0;
        for (String id : plugin.achievements().ids()) {
            if (data.isUnlocked(id)) {
                unlocked++;
            }
        }
        return unlocked * 100d / total;
    }

    public int unlockedCount(PlayerData data) {
        int unlocked = 0;
        for (String id : plugin.achievements().ids()) {
            if (data.isUnlocked(id)) {
                unlocked++;
            }
        }
        return unlocked;
    }
}
