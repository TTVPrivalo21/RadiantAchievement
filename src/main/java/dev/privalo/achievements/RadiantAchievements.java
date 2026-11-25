package dev.privalo.achievements;

import dev.privalo.achievements.api.AchievementAPI;
import dev.privalo.achievements.command.AchievementsCommand;
import dev.privalo.achievements.config.AchievementRegistry;
import dev.privalo.achievements.config.DesignRegistry;
import dev.privalo.achievements.config.Settings;
import dev.privalo.achievements.core.AchievementManager;
import dev.privalo.achievements.data.PlayerDataManager;
import dev.privalo.achievements.display.ChatAnnouncer;
import dev.privalo.achievements.display.NotificationService;
import dev.privalo.achievements.gui.MenuListener;
import dev.privalo.achievements.hook.HookManager;
import dev.privalo.achievements.listener.ConnectionListener;
import dev.privalo.achievements.pack.PackHttpServer;
import dev.privalo.achievements.pack.PackSender;
import dev.privalo.achievements.pack.ResourcePackGenerator;
import dev.privalo.achievements.storage.SqlStorage;
import dev.privalo.achievements.storage.Storage;
import dev.privalo.achievements.storage.YamlStorage;
import dev.privalo.achievements.trigger.TrackingTasks;
import dev.privalo.achievements.trigger.TriggerListener;
import dev.privalo.achievements.util.ServerVersion;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import dev.privalo.achievements.update.UpdateChecker;
import dev.privalo.achievements.update.Versions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;

/**
 * RadiantAchievements main class.
 */
public final class RadiantAchievements extends JavaPlugin {

    /** Bundled config.yml / designs.yml version; older files are backed up and replaced. */
    private static final int CONFIG_VERSION = 2;

    private Settings settings;
    private DesignRegistry designs;
    private AchievementRegistry achievements;
    private HookManager hooks;
    private Storage storage;
    private PlayerDataManager data;
    private AchievementManager manager;
    private NotificationService notifications;
    private ChatAnnouncer announcer;
    private ResourcePackGenerator packGenerator;
    private PackHttpServer packServer;
    private PackSender packSender;
    private UpdateChecker updates;
    private TriggerListener triggers;
    private TrackingTasks tracking;
    private NamespacedKey fireworkKey;
    private boolean ready;

    @Override
    public void onEnable() {
        if (!adventurePresent()) {
            getLogger().severe("RadiantAchievements requires Paper (or a Paper fork such as Purpur) "
                    + "because it uses the native Adventure/MiniMessage API. Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        fireworkKey = new NamespacedKey(this, "reward_firework");
        checkVersionChange();

        saveOrUpgrade("config.yml", CONFIG_VERSION);
        saveOrUpgrade("designs.yml", CONFIG_VERSION);
        saveResourceIfMissing("achievements.yml");
        reloadConfig();

        settings = new Settings(getConfig());
        hooks = new HookManager(this);
        hooks.setup();

        designs = new DesignRegistry(getLogger());
        achievements = new AchievementRegistry(getLogger());
        loadContent();

        try {
            storage = createStorage();
            storage.init();
        } catch (Throwable ex) {
            getLogger().log(Level.SEVERE, "Could not initialise " + settings.storageType + " storage, falling back to YAML.", ex);
            storage = new YamlStorage(getDataFolder());
            try {
                storage.init();
            } catch (Exception fatal) {
                getLogger().log(Level.SEVERE, "YAML storage failed too. Disabling.", fatal);
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }
        data = new PlayerDataManager(this, storage);
        manager = new AchievementManager(this);
        notifications = new NotificationService(this);
        announcer = new ChatAnnouncer(this);
        packGenerator = new ResourcePackGenerator(this);
        packServer = new PackHttpServer(this);
        packSender = new PackSender(this);
        triggers = new TriggerListener(this);
        tracking = new TrackingTasks(this);

        generatePackOnStartup();
        startPackServer();

        AchievementAPI.register(this, new AchievementAPI(this));

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(triggers, this);
        pm.registerEvents(new MenuListener(), this);
        updates = new UpdateChecker(this);
        pm.registerEvents(updates, this);

        PluginCommand command = getCommand("achievements");
        if (command != null) {
            AchievementsCommand executor = new AchievementsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        tracking.start();
        data.loadOnlinePlayers();
        ready = true;
        getLogger().info("Enabled v" + getPluginMeta().getVersion() + " on Minecraft " + ServerVersion.asString() + " — "
                + achievements.size() + " achievements, " + designs.designs().size() + " designs, storage "
                + storage.name() + ".");
        updates.check();
    }

    /**
     * Compares the jar version with plugins/RadiantAchievements/.last-version and prints a notice when it changed
     * ("previous -> new", downgrade, or first install) before rewriting the file.
     */
    private void checkVersionChange() {
        String current = getPluginMeta().getVersion();
        Path file = getDataFolder().toPath().resolve(".last-version");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.isRegularFile(file)) {
                getLogger().info("RadiantAchievements " + current + " instalado por primera vez.");
            } else {
                String previous = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (previous.equals(current)) {
                    return;
                }
                String kind = !previous.isEmpty() && Versions.compare(previous, current) > 0 ? "DOWNGRADE" : "ACTUALIZACIÓN";
                getLogger().warning("============================================================");
                getLogger().warning(" RadiantAchievements " + kind + " detectada: "
                        + (previous.isEmpty() ? "desconocida" : previous) + " -> " + current);
                getLogger().warning(" Revisa el README (novedades). Las claves nuevas de config.yml");
                getLogger().warning(" usan el valor por defecto del .jar hasta que las añadas.");
                getLogger().warning("============================================================");
            }
            Files.writeString(file, current, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            getLogger().warning("No se pudo leer/escribir .last-version: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (!ready) {
            return;
        }
        ready = false;
        tracking.stop();
        packServer.stop();
        notifications.shutdown();
        data.shutdown();
        storage.close();
        hooks.shutdown();
        AchievementAPI.register(this, null);
    }

    /** Reloads config.yml, designs.yml and achievements.yml (storage type changes need a restart). */
    public void reload() {
        reloadConfig();
        int oldPort = settings.httpPort;
        String oldBind = settings.httpBind;
        settings = new Settings(getConfig());
        loadContent();
        tracking.start();
        if (oldPort != settings.httpPort || !oldBind.equals(settings.httpBind) || !packServer.running()) {
            startPackServer();
        }
        updates.check();
    }

    private void loadContent() {
        designs.load(new File(getDataFolder(), "designs.yml"), settings.defaultDesign);
        achievements.load(new File(getDataFolder(), "achievements.yml"), designs);
        applyGlyphMetrics();
    }

    /** (Re)configures the text tags with the glyph widths measured during the last pack generation. */
    public void applyGlyphMetrics() {
        Text.configure(designs.glyphs(), Key.key(settings.packFont),
                packGenerator == null ? Map.of() : packGenerator.glyphAdvances());
    }

    /** Starts the built-in HTTP server when the pack is hosted locally. */
    private void startPackServer() {
        if (settings.packEnabled && settings.packSendOnJoin && settings.usesBuiltInHost() && !settings.oraxenMerge) {
            packServer.start(settings.httpBind, settings.httpPort);
        } else {
            packServer.stop();
        }
    }

    private Storage createStorage() {
        return switch (settings.storageType) {
            case "SQLITE" -> new SqlStorage(SqlStorage.Dialect.SQLITE, getDataFolder(), settings.section("storage"));
            case "MYSQL", "MARIADB" -> new SqlStorage(SqlStorage.Dialect.MYSQL, getDataFolder(), settings.section("storage"));
            default -> new YamlStorage(getDataFolder());
        };
    }

    private void generatePackOnStartup() {
        try {
            packGenerator.prepareSourceFolders();
            // The built-in host serves the in-memory zip, so it always needs a fresh generation.
            boolean hosted = settings.packSendOnJoin && settings.usesBuiltInHost();
            if (settings.packEnabled && (settings.packGenerateOnStartup || hosted)) {
                ResourcePackGenerator.Result result = packGenerator.generate();
                applyGlyphMetrics();
                getLogger().info("Resource pack generated: " + result.glyphs() + " glyphs, " + result.itemModels()
                        + " item models, " + result.sounds() + " sounds" + (result.sha1().isEmpty() ? "" : " (SHA-1 " + result.sha1() + ")"));
                for (String warning : result.warnings()) {
                    getLogger().info(" - " + warning);
                }
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Resource pack generation failed", ex);
        }
    }

    /**
     * Writes the bundled file when missing, or when the existing one has an older config-version
     * (the old file is kept as &lt;name&gt;.v&lt;n&gt;.old so nothing is lost).
     */
    private void saveOrUpgrade(String name, int version) {
        File file = new File(getDataFolder(), name);
        if (file.exists()) {
            int current = YamlConfiguration.loadConfiguration(file).getInt("config-version", 1);
            if (current >= version) {
                return;
            }
            File backup = new File(getDataFolder(), name + ".v" + current + ".old");
            if (!file.renameTo(backup)) {
                getLogger().warning("Could not back up outdated " + name + "; keeping it.");
                return;
            }
            getLogger().warning(name + " was outdated (v" + current + " → v" + version + "). Saved the new default; "
                    + "your old file is " + backup.getName());
        }
        saveResource(name, false);
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private static boolean adventurePresent() {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class.forName("org.bukkit.entity.Player").getMethod("sendActionBar", Class.forName("net.kyori.adventure.text.Component"));
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    /** Runs on the main thread (immediately if already there). */
    public void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, runnable);
        }
    }

    public Settings settings() {
        return settings;
    }

    public DesignRegistry designs() {
        return designs;
    }

    public AchievementRegistry achievements() {
        return achievements;
    }

    public HookManager hooks() {
        return hooks;
    }

    public PlayerDataManager data() {
        return data;
    }

    public AchievementManager manager() {
        return manager;
    }

    public NotificationService notifications() {
        return notifications;
    }

    public ChatAnnouncer announcer() {
        return announcer;
    }

    public ResourcePackGenerator packGenerator() {
        return packGenerator;
    }

    public PackHttpServer packServer() {
        return packServer;
    }

    public PackSender packSender() {
        return packSender;
    }

    public UpdateChecker updates() {
        return updates;
    }

    public TriggerListener triggers() {
        return triggers;
    }

    public TrackingTasks tracking() {
        return tracking;
    }

    public NamespacedKey fireworkKey() {
        return fireworkKey;
    }
}
