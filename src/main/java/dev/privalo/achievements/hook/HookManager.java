package dev.privalo.achievements.hook;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Detects optional plugins and wires their integrations. Classes that reference a third party API are only
 * loaded when that plugin is enabled, so missing plugins never cause {@link NoClassDefFoundError}s.
 */
public final class HookManager {

    private final RadiantAchievements plugin;
    private final List<String> active = new ArrayList<>();
    private VaultHook vault;
    private WorldGuardHook worldGuard;
    private OraxenHook oraxen;
    private boolean placeholderApi;

    public HookManager(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        PluginManager pm = Bukkit.getPluginManager();
        var cfg = plugin.getConfig();

        if (cfg.getBoolean("hooks.placeholderapi", true) && pm.isPluginEnabled("PlaceholderAPI")) {
            attempt("PlaceholderAPI", () -> {
                PlaceholderHook.registerHook(plugin);
                placeholderApi = true;
            });
        }
        if (cfg.getBoolean("hooks.vault", true) && pm.isPluginEnabled("Vault")) {
            attempt("Vault", () -> {
                VaultHook hook = new VaultHook();
                if (hook.setup()) {
                    vault = hook;
                } else {
                    throw new IllegalStateException("no economy provider registered");
                }
            });
        }
        if (cfg.getBoolean("hooks.worldguard", true) && pm.isPluginEnabled("WorldGuard")) {
            attempt("WorldGuard", () -> worldGuard = new WorldGuardHook());
        }
        if (cfg.getBoolean("hooks.mythicmobs", true) && pm.isPluginEnabled("MythicMobs")) {
            attempt("MythicMobs", () -> pm.registerEvents(new MythicMobsHook(plugin), plugin));
        }
        if (cfg.getBoolean("hooks.citizens", true) && pm.isPluginEnabled("Citizens")) {
            attempt("Citizens", () -> pm.registerEvents(new CitizensHook(plugin), plugin));
        }
        if (cfg.getBoolean("hooks.oraxen", true) && pm.isPluginEnabled("Oraxen")) {
            attempt("Oraxen", () -> oraxen = new OraxenHook(plugin));
        }
        plugin.getLogger().info(active.isEmpty() ? "No integrations found." : "Integrations: " + String.join(", ", active));
    }

    private void attempt(String name, Runnable action) {
        try {
            action.run();
            active.add(name);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Could not hook into " + name + " (" + ex.getMessage() + ")", ex);
        }
    }

    public void shutdown() {
        if (placeholderApi) {
            try {
                PlaceholderHook.unregisterHook();
            } catch (Throwable ignored) {
                // PlaceholderAPI already disabled
            }
            Text.setPlaceholderProcessor(null);
        }
    }

    public VaultHook vault() {
        return vault;
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public OraxenHook oraxen() {
        return oraxen;
    }

    public boolean placeholderApi() {
        return placeholderApi;
    }

    public List<String> active() {
        return List.copyOf(active);
    }
}
