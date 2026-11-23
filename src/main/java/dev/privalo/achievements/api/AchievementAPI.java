package dev.privalo.achievements.api;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.api.event.AchievementProgressEvent;
import dev.privalo.achievements.api.event.AchievementUnlockEvent;
import dev.privalo.achievements.data.PlayerData;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.model.SoundSpec;
import dev.privalo.achievements.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Public API of RadiantAchievements.
 *
 * <pre>{@code
 * AchievementAPI api = AchievementAPI.get();
 * api.addProgress(player, "miner_100", 5);
 * api.onUnlock(myPlugin, event -> getLogger().info(event.getPlayer().getName() + " unlocked " + event.getAchievement().id()));
 * }</pre>
 *
 * Methods that change state or show things must be called on the main thread.
 */
public final class AchievementAPI {

    private static AchievementAPI instance;
    private final RadiantAchievements plugin;

    public AchievementAPI(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /**
     * @throws IllegalStateException if RadiantAchievements is not enabled
     */
    public static @NotNull AchievementAPI get() {
        if (instance == null) {
            throw new IllegalStateException("RadiantAchievements is not enabled");
        }
        return instance;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    /** Internal: called by the plugin on enable/disable. */
    public static void register(RadiantAchievements plugin, AchievementAPI api) {
        instance = api;
        if (api != null) {
            Bukkit.getServicesManager().register(AchievementAPI.class, api, plugin, ServicePriority.Normal);
        } else {
            Bukkit.getServicesManager().unregisterAll(plugin);
        }
    }

    private static void ensureMain() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("AchievementAPI state changes must be called on the main thread");
        }
    }

    private Achievement require(String id) {
        Achievement a = plugin.achievements().get(id);
        if (a == null) {
            throw new IllegalArgumentException("Unknown achievement '" + id + "'");
        }
        return a;
    }

    // ── Achievements ───────────────────────────────────────────────

    public Optional<Achievement> getAchievement(@NotNull String id) {
        return Optional.ofNullable(plugin.achievements().get(id));
    }

    public Collection<Achievement> getAchievements() {
        return plugin.achievements().all();
    }

    /**
     * Registers an achievement from code (kept across /ach reload). Use {@code MANUAL} triggers for fully custom logic.
     */
    public void registerAchievement(@NotNull Achievement achievement) {
        plugin.achievements().register(achievement);
    }

    public boolean unregisterAchievement(@NotNull String id) {
        return plugin.achievements().unregister(id);
    }

    // ── State changes (main thread) ────────────────────────────────

    /**
     * Unlocks an achievement (rewards, notification, announcement).
     *
     * @return true if it was unlocked now
     */
    public boolean unlock(@NotNull Player player, @NotNull String id) {
        ensureMain();
        return plugin.manager().unlock(player, require(id), AchievementUnlockEvent.Cause.API);
    }

    /** Removes an unlocked achievement and its progress. */
    public boolean revoke(@NotNull Player player, @NotNull String id) {
        ensureMain();
        PlayerData data = plugin.data().get(player);
        return data != null && data.revoke(require(id).id());
    }

    /** @return resulting progress */
    public long addProgress(@NotNull Player player, @NotNull String id, long amount) {
        ensureMain();
        return plugin.manager().addProgress(player, require(id), amount);
    }

    /** @return resulting progress */
    public long setProgress(@NotNull Player player, @NotNull String id, long value) {
        ensureMain();
        return plugin.manager().setProgress(player, require(id), value);
    }

    // ── Queries (any thread) ───────────────────────────────────────

    public boolean hasUnlocked(@NotNull Player player, @NotNull String id) {
        PlayerData data = plugin.data().get(player);
        return data != null && data.isUnlocked(id.toLowerCase(java.util.Locale.ROOT));
    }

    public long getProgress(@NotNull Player player, @NotNull String id) {
        PlayerData data = plugin.data().get(player);
        Achievement a = plugin.achievements().get(id);
        if (data == null || a == null) {
            return 0;
        }
        return data.isUnlocked(a.id()) ? a.target() : data.progress(a.id());
    }

    public int getUnlockedCount(@NotNull Player player) {
        PlayerData data = plugin.data().get(player);
        return data == null ? 0 : plugin.manager().unlockedCount(data);
    }

    public int getTotalCount() {
        return plugin.achievements().size();
    }

    /** 0..100 */
    public double getCompletionPercent(@NotNull Player player) {
        PlayerData data = plugin.data().get(player);
        return data == null ? 0 : plugin.manager().completion(data);
    }

    // ── Visuals (main thread) ──────────────────────────────────────

    /** Shows the unlock notification of an achievement without unlocking it. */
    public void previewAchievement(@NotNull Player player, @NotNull String id) {
        ensureMain();
        plugin.manager().preview(player, require(id));
    }

    /**
     * Shows a custom notification using a design from designs.yml.
     * In the design texts {@code <achievement_name>} = title, {@code <achievement_description>} = description and
     * {@code <custom_subtitle>} = subtitle.
     *
     * @param icon  optional icon (toast), null = design icon
     * @param sound optional sounds, null = design sounds
     */
    public void showNotification(@NotNull Player player, @NotNull String designId, @NotNull String title,
                                 @Nullable String subtitle, @Nullable String description,
                                 @Nullable IconSpec icon, @Nullable List<SoundSpec> sound) {
        ensureMain();
        Design design = plugin.designs().get(designId);
        plugin.notifications().show(player, design, Placeholders.custom(player.getName(), title, subtitle, description),
                icon, sound);
    }

    public void showNotification(@NotNull Player player, @NotNull String designId, @NotNull String title,
                                 @Nullable String description) {
        showNotification(player, designId, title, null, description, null, null);
    }

    /** Plays a vanilla or resource pack sound ("namespace:key"). */
    public void playSound(@NotNull Player player, @NotNull String sound, float volume, float pitch) {
        plugin.notifications().sounds().playNow(player, new SoundSpec(sound, volume, pitch, 0));
    }

    // ── Listeners ──────────────────────────────────────────────────

    /**
     * Registers a handler for any event class (including {@link AchievementUnlockEvent} and
     * {@link AchievementProgressEvent}). Returns the listener so it can be unregistered with
     * {@link HandlerList#unregisterAll(Listener)}.
     */
    public <T extends Event> Listener registerListener(@NotNull Plugin owner, @NotNull Class<T> eventClass,
                                                       @NotNull EventPriority priority, boolean ignoreCancelled,
                                                       @NotNull Consumer<T> handler) {
        Listener listener = new Listener() {
        };
        Bukkit.getPluginManager().registerEvent(eventClass, listener, priority, (l, event) -> {
            if (eventClass.isInstance(event)) {
                handler.accept(eventClass.cast(event));
            }
        }, owner, ignoreCancelled);
        return listener;
    }

    public Listener onUnlock(@NotNull Plugin owner, @NotNull Consumer<AchievementUnlockEvent> handler) {
        return registerListener(owner, AchievementUnlockEvent.class, EventPriority.NORMAL, true, handler);
    }

    public Listener onProgress(@NotNull Plugin owner, @NotNull Consumer<AchievementProgressEvent> handler) {
        return registerListener(owner, AchievementProgressEvent.class, EventPriority.NORMAL, true, handler);
    }
}
