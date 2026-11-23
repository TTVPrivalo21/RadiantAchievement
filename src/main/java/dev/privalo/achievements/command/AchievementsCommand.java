package dev.privalo.achievements.command;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.api.event.AchievementUnlockEvent;
import dev.privalo.achievements.data.PlayerData;
import dev.privalo.achievements.gui.AchievementMenu;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.SoundSpec;
import dev.privalo.achievements.pack.ResourcePackGenerator;
import dev.privalo.achievements.util.Placeholders;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * /achievements (aliases /ach, /logros, /radiantachievements).
 */
public final class AchievementsCommand implements TabExecutor {

    private static final String ADMIN = "radiantachievements.admin";
    private static final String PREVIEW = "radiantachievements.preview";
    private static final String RELOAD = "radiantachievements.reload";
    private static final String OTHERS = "radiantachievements.progress.others";

    private final RadiantAchievements plugin;

    public AchievementsCommand(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                help(sender);
                return true;
            }
            PlayerData data = plugin.data().get(player);
            if (data != null) {
                new AchievementMenu(plugin, player, data).open();
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "progress" -> progress(sender, args);
            case "menu", "gui" -> menu(sender, args);
            case "give" -> give(sender, args);
            case "take", "revoke" -> take(sender, args);
            case "reset" -> reset(sender, args);
            case "setprogress", "addprogress" -> changeProgress(sender, args, sub.equals("addprogress"));
            case "preview" -> preview(sender, args);
            case "sound" -> sound(sender, args);
            case "pack" -> pack(sender);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Player p = sender instanceof Player pl ? pl : null;
        for (String line : plugin.settings().messageList("help")) {
            sender.sendMessage(Text.parse(p, line));
        }
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.settings().send(sender, "no-permission");
        return false;
    }

    private Achievement achievement(CommandSender sender, String id) {
        Achievement a = plugin.achievements().get(id);
        if (a == null) {
            plugin.settings().send(sender, "achievement-not-found", Placeholder.unparsed("id", id));
        }
        return a;
    }

    /**
     * Resolves a target (online or known offline player) and runs the action with its data on the main thread.
     * {@code online} is null for offline players.
     */
    @SuppressWarnings("deprecation")
    private void withTarget(CommandSender sender, String name, TargetAction action) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerData data = plugin.data().get(online);
            if (data != null) {
                action.run(online, data);
                return;
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            plugin.settings().send(sender, "player-not-found", Placeholder.unparsed("player", name));
            return;
        }
        String realName = offline.getName() == null ? name : offline.getName();
        plugin.data().loadAny(offline.getUniqueId(), realName).thenAccept(data -> plugin.runSync(() -> {
            Player now = Bukkit.getPlayer(offline.getUniqueId());
            PlayerData live = now != null ? plugin.data().get(now) : null;
            if (now != null && live != null) {
                action.run(now, live);
            } else {
                action.run(null, data);
                plugin.data().saveAsync(data);
            }
        }));
    }

    @FunctionalInterface
    private interface TargetAction {
        void run(Player online, PlayerData data);
    }

    private TagResolver ctx(Achievement a, PlayerData data) {
        long progress = data.isUnlocked(a.id()) ? a.target() : data.progress(a.id());
        return Placeholders.achievement(a, data.name(), progress, a.designId());
    }

    private void menu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.settings().send(sender, "player-only");
            return;
        }
        if (args.length >= 2) {
            if (!check(sender, OTHERS)) {
                return;
            }
            withTarget(sender, args[1], (online, data) -> new AchievementMenu(plugin, player, data).open());
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data != null) {
            new AchievementMenu(plugin, player, data).open();
        }
    }

    private void progress(CommandSender sender, String[] args) {
        String name;
        if (args.length >= 2) {
            if (!check(sender, OTHERS)) {
                return;
            }
            name = args[1];
        } else if (sender instanceof Player p) {
            name = p.getName();
        } else {
            plugin.settings().send(sender, "player-only");
            return;
        }
        withTarget(sender, name, (online, data) -> {
            int unlocked = plugin.manager().unlockedCount(data);
            TagResolver summary = TagResolver.resolver(
                    Placeholder.unparsed("player", data.name()),
                    Placeholder.unparsed("unlocked", String.valueOf(unlocked)),
                    Placeholder.unparsed("total", String.valueOf(plugin.achievements().size())),
                    Placeholder.unparsed("percent", String.format(Locale.ROOT, "%.1f", plugin.manager().completion(data))));
            Player viewer = sender instanceof Player p ? p : null;
            sender.sendMessage(Text.parse(viewer, plugin.settings().message("progress-header"), summary));
            for (Achievement a : plugin.achievements().all()) {
                boolean done = data.isUnlocked(a.id());
                if (a.secret() && !done && !sender.hasPermission(ADMIN)) {
                    continue;
                }
                TagResolver line = TagResolver.resolver(ctx(a, data), Placeholder.parsed("status",
                        plugin.settings().message(done ? "progress-unlocked" : "progress-locked")));
                sender.sendMessage(Text.parse(viewer, plugin.settings().message("progress-line"), line));
            }
        });
    }

    private void give(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN) || !usage(sender, args, 3)) {
            return;
        }
        Achievement a = achievement(sender, args[2]);
        if (a == null) {
            return;
        }
        withTarget(sender, args[1], (online, data) -> {
            TagResolver ctx = ctx(a, data);
            if (data.isUnlocked(a.id())) {
                plugin.settings().send(sender, "already-unlocked", ctx);
                return;
            }
            boolean ok = online != null
                    ? plugin.manager().unlock(online, a, AchievementUnlockEvent.Cause.COMMAND)
                    : plugin.manager().unlockSilently(data, a);
            if (ok) {
                plugin.settings().send(sender, "given", ctx);
                if (online == null) {
                    plugin.settings().send(sender, "offline-note");
                }
            }
        });
    }

    private void take(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN) || !usage(sender, args, 3)) {
            return;
        }
        Achievement a = achievement(sender, args[2]);
        if (a == null) {
            return;
        }
        withTarget(sender, args[1], (online, data) -> {
            TagResolver ctx = ctx(a, data);
            plugin.settings().send(sender, data.revoke(a.id()) ? "taken" : "not-unlocked", ctx);
        });
    }

    private void reset(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN) || !usage(sender, args, 2)) {
            return;
        }
        if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
            Achievement a = achievement(sender, args[2]);
            if (a == null) {
                return;
            }
            withTarget(sender, args[1], (online, data) -> {
                data.revoke(a.id());
                plugin.settings().send(sender, "reset-one", ctx(a, data));
            });
            return;
        }
        withTarget(sender, args[1], (online, data) -> {
            data.clear();
            plugin.settings().send(sender, "reset-all", Placeholder.unparsed("player", data.name()));
        });
    }

    private void changeProgress(CommandSender sender, String[] args, boolean add) {
        if (!check(sender, ADMIN) || !usage(sender, args, 4)) {
            return;
        }
        Achievement a = achievement(sender, args[2]);
        if (a == null) {
            return;
        }
        long value;
        try {
            value = Long.parseLong(args[3]);
        } catch (NumberFormatException ex) {
            plugin.settings().send(sender, "invalid-number", Placeholder.unparsed("value", args[3]));
            return;
        }
        withTarget(sender, args[1], (online, data) -> {
            long result;
            if (online != null) {
                result = add ? plugin.manager().addProgress(online, a, value) : plugin.manager().setProgress(online, a, value);
            } else {
                long next = Math.max(0, Math.min(a.target(), add ? data.progress(a.id()) + value : value));
                if (next >= a.target()) {
                    plugin.manager().unlockSilently(data, a);
                } else if (!data.isUnlocked(a.id())) {
                    data.setProgress(a.id(), next);
                }
                result = data.isUnlocked(a.id()) ? a.target() : data.progress(a.id());
            }
            plugin.settings().send(sender, "progress-set",
                    Placeholders.achievement(a, data.name(), result, a.designId()));
            if (online == null) {
                plugin.settings().send(sender, "offline-note");
            }
        });
    }

    private void preview(CommandSender sender, String[] args) {
        if (!check(sender, PREVIEW) || !usage(sender, args, 2)) {
            return;
        }
        Player target = targetOrSelf(sender, args, 2);
        if (target == null) {
            return;
        }
        Achievement a = plugin.achievements().get(args[1]);
        if (a != null) {
            plugin.manager().preview(target, a);
            return;
        }
        if (!plugin.designs().exists(args[1])) {
            plugin.settings().send(sender, "design-not-found", Placeholder.unparsed("design", args[1]));
            return;
        }
        Design design = plugin.designs().get(args[1]);
        TagResolver ctx = Placeholders.custom(target.getName(), "Preview: " + design.id(), "",
                "Estilo <white>" + design.style().name().toLowerCase(Locale.ROOT) + "</white> · "
                        + design.durationTicks() + " ticks");
        plugin.notifications().show(target, design, ctx, null, null);
        var spec = plugin.announcer().resolve(null, design);
        plugin.announcer().announce(target, new dev.privalo.achievements.model.AnnounceSpec(spec.mode(),
                dev.privalo.achievements.model.AnnounceScope.PRIVATE, spec.compact(), spec.designed()), ctx);
    }

    private void sound(CommandSender sender, String[] args) {
        if (!check(sender, PREVIEW) || !usage(sender, args, 2)) {
            return;
        }
        float volume = args.length >= 3 ? parseFloat(args[2], 1f) : 1f;
        float pitch = args.length >= 4 ? parseFloat(args[3], 1f) : 1f;
        Player target = targetOrSelf(sender, args, 4);
        if (target == null) {
            return;
        }
        SoundSpec spec = new SoundSpec(args[1], volume, pitch, 0);
        if (plugin.notifications().sounds().playNow(target, spec)) {
            plugin.settings().send(sender, "preview-sound", Placeholder.unparsed("sound", spec.key()));
        }
    }

    private void pack(CommandSender sender) {
        if (!check(sender, RELOAD)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ResourcePackGenerator.Result result = plugin.packGenerator().generate();
                plugin.runSync(() -> {
                    plugin.applyGlyphMetrics();
                    plugin.packSender().sendAll();
                    plugin.settings().send(sender, "pack-generated",
                            Placeholder.unparsed("path", plugin.getDataFolder().toPath().relativize(
                                    result.zip() != null ? result.zip() : result.folder()).toString()),
                            Placeholder.unparsed("hash", result.sha1().isEmpty() ? "-" : result.sha1()));
                    for (String warning : result.warnings()) {
                        sender.sendMessage(Text.parse("<dark_gray> • <gray>" + warning.replace("<", "\\<")));
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Resource pack generation failed", ex);
                plugin.runSync(() -> plugin.settings().send(sender, "pack-failed",
                        Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            }
        });
    }

    private void reload(CommandSender sender) {
        if (!check(sender, RELOAD)) {
            return;
        }
        plugin.reload();
        plugin.settings().send(sender, "reloaded",
                Placeholder.unparsed("achievements", String.valueOf(plugin.achievements().size())),
                Placeholder.unparsed("designs", String.valueOf(plugin.designs().designs().size())));
    }

    private Player targetOrSelf(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) {
                plugin.settings().send(sender, "player-not-found", Placeholder.unparsed("player", args[index]));
            }
            return p;
        }
        if (sender instanceof Player p) {
            return p;
        }
        plugin.settings().send(sender, "player-only");
        return null;
    }

    private boolean usage(CommandSender sender, String[] args, int min) {
        if (args.length < min) {
            help(sender);
            return false;
        }
        return true;
    }

    private static float parseFloat(String raw, float def) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    // ── Tab completion ─────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        BiConsumer<String, String> add = (perm, value) -> {
            if (perm == null || sender.hasPermission(perm)) {
                out.add(value);
            }
        };
        if (args.length == 1) {
            add.accept(null, "help");
            add.accept(null, "progress");
            add.accept(null, "menu");
            for (String s : new String[]{"give", "take", "reset", "setprogress", "addprogress"}) {
                add.accept(ADMIN, s);
            }
            add.accept(PREVIEW, "preview");
            add.accept(PREVIEW, "sound");
            add.accept(RELOAD, "pack");
            add.accept(RELOAD, "reload");
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give", "take", "revoke", "reset", "setprogress", "addprogress" -> {
                if (!sender.hasPermission(ADMIN)) {
                    return out;
                }
                if (args.length == 2) {
                    return filter(players(), args[1]);
                }
                if (args.length == 3) {
                    out.addAll(plugin.achievements().ids());
                    if (sub.equals("reset")) {
                        out.add("all");
                    }
                    return filter(out, args[2]);
                }
                if (args.length == 4 && sub.endsWith("progress")) {
                    Achievement a = plugin.achievements().get(args[2]);
                    out.add(a == null ? "1" : String.valueOf(a.target()));
                    return out;
                }
            }
            case "progress", "menu", "gui" -> {
                if (args.length == 2 && sender.hasPermission(OTHERS)) {
                    return filter(players(), args[1]);
                }
            }
            case "preview" -> {
                if (!sender.hasPermission(PREVIEW)) {
                    return out;
                }
                if (args.length == 2) {
                    out.addAll(plugin.designs().designs().keySet());
                    out.addAll(plugin.achievements().ids());
                    return filter(out, args[1]);
                }
                if (args.length == 3) {
                    return filter(players(), args[2]);
                }
            }
            case "sound" -> {
                if (!sender.hasPermission(PREVIEW)) {
                    return out;
                }
                if (args.length == 2) {
                    var sounds = plugin.settings().section("resource-pack.sounds");
                    for (String key : sounds.getKeys(false)) {
                        out.add(plugin.settings().packNamespace + ":" + key);
                    }
                    out.add("minecraft:ui.toast.challenge_complete");
                    out.add("minecraft:entity.player.levelup");
                    out.add("minecraft:block.note_block.pling");
                    out.add("minecraft:entity.firework_rocket.twinkle");
                    return filter(out, args[1]);
                }
                if (args.length == 3 || args.length == 4) {
                    out.add("1.0");
                    out.add("0.5");
                    out.add("2.0");
                    return out;
                }
                if (args.length == 5) {
                    return filter(players(), args[4]);
                }
            }
            default -> {
                return out;
            }
        }
        return out;
    }

    private static List<String> players() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(v);
            }
        }
        return out;
    }
}
