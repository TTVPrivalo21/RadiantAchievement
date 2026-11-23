package dev.privalo.achievements.display;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.DisplayStyle;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.model.SoundSpec;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders designs on screen. Notifications of the same player are queued so they never overlap.
 */
public final class NotificationService {

    /** A notification waiting to be shown. */
    public record Notification(Design design, TagResolver context, IconSpec icon, List<SoundSpec> sounds) {
    }

    private final RadiantAchievements plugin;
    private final ToastSender toasts;
    private final SoundPlayer sounds;
    private final Map<UUID, Deque<Notification>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> running = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public NotificationService(RadiantAchievements plugin) {
        this.plugin = plugin;
        this.toasts = new ToastSender(plugin);
        this.sounds = new SoundPlayer(plugin);
    }

    public SoundPlayer sounds() {
        return sounds;
    }

    /**
     * Queues a notification for the player (main thread).
     *
     * @param icon   icon override, null = design icon
     * @param sounds sound override, null or empty = design sounds
     */
    public void show(Player player, Design design, TagResolver context, IconSpec icon, List<SoundSpec> sounds) {
        IconSpec finalIcon = icon != null ? icon : design.icon();
        List<SoundSpec> finalSounds = sounds != null && !sounds.isEmpty() ? sounds : design.sounds();
        Deque<Notification> queue = queues.computeIfAbsent(player.getUniqueId(), u -> new ArrayDeque<>());
        queue.add(new Notification(design, context, finalIcon, finalSounds));
        if (!running.containsKey(player.getUniqueId())) {
            next(player);
        }
    }

    private void next(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<Notification> queue = queues.get(uuid);
        Notification n = queue == null ? null : queue.poll();
        if (n == null || !player.isOnline()) {
            running.remove(uuid);
            if (!player.isOnline()) {
                queues.remove(uuid);
            }
            return;
        }
        render(player, n);
        long wait = (n.design().style() == DisplayStyle.TOAST ? 40L : n.design().durationTicks())
                + plugin.settings().queueGapTicks;
        running.put(uuid, Bukkit.getScheduler().runTaskLater(plugin, () -> next(player), Math.max(1L, wait)));
    }

    private void render(Player player, Notification n) {
        Design d = n.design();
        TagResolver ctx = n.context();
        Component title = Text.parse(player, d.title(), ctx);
        Component subtitle = Text.parse(player, d.subtitle(), ctx);
        Component description = Text.parse(player, d.description(), ctx);
        sounds.play(player, n.sounds());
        DisplayStyle style = d.style();
        if (style == DisplayStyle.TOAST) {
            if (toasts.send(player, n.icon(), title, description, d.frame())) {
                return;
            }
            style = plugin.settings().toastFallback == DisplayStyle.TOAST ? DisplayStyle.TITLE : plugin.settings().toastFallback;
        }
        switch (style) {
            case BOSSBAR_OVERLAY -> bossBar(player, d, title, description);
            case ACTIONBAR -> actionBar(player, join(title, subtitle), d.durationTicks());
            case TITLE -> player.showTitle(Title.title(title, subtitle, times(10, d.durationTicks(), 20)));
            case CENTER_BANNER -> {
                player.showTitle(Title.title(title, subtitle, times(5, d.durationTicks(), 10)));
                actionBar(player, description, d.durationTicks());
            }
            default -> player.showTitle(Title.title(title, subtitle, times(10, d.durationTicks(), 20)));
        }
    }

    private static Component join(Component a, Component b) {
        if (Text.plain(b).isEmpty()) {
            return a;
        }
        return a.append(Component.space()).append(b);
    }

    private static Title.Times times(int fadeIn, int stay, int fadeOut) {
        return Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L));
    }

    /**
     * Compact card: [card glyph][back to start][icon][title · description][padding to card width], so the
     * client centres the card itself. With hide-bar the bar uses the pack's transparent colour.
     */
    private Component bossBarContent(Design d, Component title, Component description) {
        if (d.bossBarCard().isEmpty() || !Text.hasGlyph(d.bossBarCard())) {
            return title;
        }
        int cardWidth = Text.glyphAdvance(d.bossBarCard());
        Component inner = Text.space(4);
        if (!d.bossBarIcon().isEmpty() && Text.hasGlyph(d.bossBarIcon())) {
            inner = inner.append(Text.glyph(d.bossBarIcon()));
        }
        inner = inner.append(Text.space(5)).append(title);
        if (!Text.plain(description).isEmpty()) {
            inner = inner.append(Component.text("  ")).append(description);
        }
        int innerWidth = Text.width(inner);
        return Component.empty()
                .append(Text.glyph(d.bossBarCard()))
                .append(Text.space(-cardWidth))
                .append(inner)
                .append(Text.space(Math.max(0, cardWidth - innerWidth)));
    }

    private BossBar.Color bossBarColor(Design d) {
        if (d.bossBarHide()) {
            try {
                return BossBar.Color.valueOf(plugin.settings().transparentBossBarColor);
            } catch (IllegalArgumentException ignored) {
                return d.bossBarColor();
            }
        }
        return d.bossBarColor();
    }

    private void bossBar(Player player, Design d, Component title, Component description) {
        BossBar previous = bossBars.remove(player.getUniqueId());
        if (previous != null) {
            player.hideBossBar(previous);
        }
        boolean hidden = d.bossBarHide();
        BossBar bar = BossBar.bossBar(bossBarContent(d, title, description), hidden ? 0f : 1f, bossBarColor(d),
                hidden ? BossBar.Overlay.PROGRESS : d.bossBarOverlay());
        bossBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
        int total = d.durationTicks();
        int[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            elapsed[0] += 2;
            if (!player.isOnline() || elapsed[0] >= total) {
                player.hideBossBar(bar);
                bossBars.remove(player.getUniqueId(), bar);
                task.cancel();
                return;
            }
            if (d.bossBarAnimate() && !hidden) {
                bar.progress(Math.max(0f, 1f - (float) elapsed[0] / total));
            }
        }, 2L, 2L);
    }

    private void actionBar(Player player, Component text, int durationTicks) {
        player.sendActionBar(text);
        int[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            elapsed[0] += 20;
            if (!player.isOnline() || elapsed[0] >= durationTicks) {
                task.cancel();
                return;
            }
            player.sendActionBar(text);
        }, 20L, 20L);
    }

    /** Clears the queue of a player that left. */
    public void clear(UUID uuid) {
        queues.remove(uuid);
        BukkitTask task = running.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        bossBars.remove(uuid);
    }

    /** Hides everything (plugin disable). */
    public void shutdown() {
        running.values().forEach(BukkitTask::cancel);
        running.clear();
        queues.clear();
        for (Map.Entry<UUID, BossBar> e : bossBars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bossBars.clear();
        toasts.cleanup();
    }
}
