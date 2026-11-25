package dev.privalo.achievements.config;

import dev.privalo.achievements.model.AnnounceMode;
import dev.privalo.achievements.model.AnnounceScope;
import dev.privalo.achievements.model.DisplayStyle;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Typed view of config.yml.
 */
public final class Settings {

    private final FileConfiguration config;

    public final String prefix;
    public final String storageType;
    public final int autosaveMinutes;
    public final int playtimeIntervalSeconds;
    public final int permissionCheckSeconds;
    public final boolean ignoreCreative;
    public final Set<String> disabledWorlds;
    public final String defaultDesign;
    public final int queueGapTicks;
    public final DisplayStyle toastFallback;
    public final boolean progressActionbar;
    public final int progressStepPercent;
    public final String progressFormat;
    public final AnnounceMode defaultAnnounceMode;
    public final AnnounceScope defaultAnnounceScope;
    public final boolean packEnabled;
    public final String packNamespace;
    public final boolean packGenerateOnStartup;
    public final boolean packSendOnJoin;
    /** Direct download URL of an externally hosted pack, or empty to use the built-in HTTP server. */
    public final String packExternalUrl;
    public final boolean packRequired;
    public final String packPrompt;
    public final boolean oraxenMerge;
    /** Font that holds glyphs and spaces ("minecraft:default" unless Oraxen MERGE forces our own namespace). */
    public final String packFont;
    public final String httpBind;
    public final int httpPort;
    public final String httpPublicAddress;
    /** Boss bar colour made transparent by the pack (empty = none). */
    public final String transparentBossBarColor;

    public Settings(FileConfiguration config) {
        this.config = config;
        this.prefix = config.getString("prefix", "");
        this.storageType = config.getString("storage.type", "YAML").toUpperCase(Locale.ROOT);
        this.autosaveMinutes = Math.max(1, config.getInt("storage.autosave-minutes", 5));
        this.playtimeIntervalSeconds = Math.max(5, config.getInt("tracking.playtime-interval-seconds", 60));
        this.permissionCheckSeconds = Math.max(5, config.getInt("tracking.permission-check-seconds", 30));
        this.ignoreCreative = config.getBoolean("tracking.ignore-creative", true);
        Set<String> worlds = new HashSet<>();
        for (String w : config.getStringList("tracking.disabled-worlds")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        this.disabledWorlds = Set.copyOf(worlds);
        this.defaultDesign = config.getString("notifications.default-design", "default");
        this.queueGapTicks = Math.max(0, config.getInt("notifications.queue-gap-ticks", 10));
        this.toastFallback = DisplayStyle.parse(config.getString("notifications.toast-fallback"), DisplayStyle.TITLE);
        this.progressActionbar = config.getBoolean("notifications.progress-actionbar.enabled", true);
        this.progressStepPercent = Math.max(1, Math.min(100, config.getInt("notifications.progress-actionbar.step-percent", 25)));
        this.progressFormat = config.getString("notifications.progress-actionbar.format", "<achievement_name> <progress>/<target>");
        this.defaultAnnounceMode = AnnounceMode.parse(config.getString("announcements.mode"), AnnounceMode.DESIGNED);
        this.defaultAnnounceScope = AnnounceScope.parse(config.getString("announcements.scope"), AnnounceScope.PUBLIC);
        this.packEnabled = config.getBoolean("resource-pack.enabled", true);
        this.packNamespace = config.getString("resource-pack.namespace", "radiant").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        this.packGenerateOnStartup = config.getBoolean("resource-pack.generate-on-startup", true);
        this.packSendOnJoin = config.getBoolean("resource-pack.send-on-join", true);
        String host = config.getString("resource-pack.host", "auto").trim();
        String legacyUrl = config.getString("resource-pack.url", "").trim();
        this.packExternalUrl = host.regionMatches(true, 0, "http", 0, 4) ? host : legacyUrl;
        this.packRequired = config.getBoolean("resource-pack.required", true);
        this.packPrompt = config.getString("resource-pack.prompt", "");
        this.oraxenMerge = "MERGE".equalsIgnoreCase(config.getString("resource-pack.oraxen.mode", "STANDALONE"));
        String font = config.getString("resource-pack.font", "minecraft:default").toLowerCase(Locale.ROOT).trim();
        if (!font.contains(":")) {
            font = "minecraft:" + font;
        }
        // Oraxen ships its own minecraft:default font; never overwrite it when merging.
        this.packFont = oraxenMerge && font.equals("minecraft:default") ? packNamespace + ":achievements" : font;
        this.httpBind = config.getString("resource-pack.http.bind", "0.0.0.0");
        this.httpPort = config.getInt("resource-pack.http.port", 8164);
        this.httpPublicAddress = config.getString("resource-pack.http.public-address", "").trim();
        this.transparentBossBarColor = config.getString("resource-pack.transparent-bossbar-color", "WHITE")
                .trim().toUpperCase(Locale.ROOT);
    }

    /** True when the pack is served by the built-in HTTP server. */
    public boolean usesBuiltInHost() {
        return packExternalUrl.isEmpty();
    }

    public FileConfiguration raw() {
        return config;
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection s = config.getConfigurationSection(path);
        return s != null ? s : config.createSection(path);
    }

    public String message(String key) {
        // No explicit default: keys missing from an old config.yml fall back to the jar's config.yml.
        String value = config.getString("messages." + key);
        return value != null ? value : "<red>Missing message: " + key;
    }

    public List<String> messageList(String key) {
        return config.getStringList("messages." + key);
    }

    public Component format(CommandSender sender, String key, TagResolver... resolvers) {
        Player player = sender instanceof Player p ? p : null;
        return Text.parse(player, prefix + message(key), resolvers);
    }

    /** Sends a prefixed message from messages.&lt;key&gt;. */
    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(format(sender, key, resolvers));
    }
}
