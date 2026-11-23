package dev.privalo.achievements.config;

import dev.privalo.achievements.model.AnnounceSpec;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.model.DisplayStyle;
import dev.privalo.achievements.model.Glyph;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.model.SoundSpec;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads designs and glyphs from designs.yml.
 */
public final class DesignRegistry {

    private final Logger logger;
    private Map<String, Design> designs = Map.of();
    private Map<String, Glyph> glyphs = Map.of();
    private String defaultId = "default";

    public DesignRegistry(Logger logger) {
        this.logger = logger;
    }

    public void load(File file, String defaultDesign) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Glyph> loadedGlyphs = new LinkedHashMap<>();
        ConfigurationSection gs = yaml.getConfigurationSection("glyphs");
        if (gs != null) {
            for (String key : gs.getKeys(false)) {
                ConfigurationSection g = gs.getConfigurationSection(key);
                if (g == null || g.getString("char", "").isEmpty()) {
                    logger.warning("Glyph '" + key + "' has no 'char', skipped.");
                    continue;
                }
                String id = key.toLowerCase(Locale.ROOT);
                loadedGlyphs.put(id, new Glyph(id, g.getString("char"), g.getString("texture", "font/" + id + ".png"),
                        g.getInt("height", 9), g.getInt("ascent", 8), g.getString("color", "#FFD36B")));
            }
        }
        Map<String, Design> loaded = new LinkedHashMap<>();
        ConfigurationSection ds = yaml.getConfigurationSection("designs");
        if (ds != null) {
            for (String key : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(key);
                if (d == null) {
                    continue;
                }
                String id = key.toLowerCase(Locale.ROOT);
                loaded.put(id, parse(id, d));
            }
        }
        this.glyphs = Collections.unmodifiableMap(loadedGlyphs);
        this.designs = Collections.unmodifiableMap(loaded);
        this.defaultId = defaultDesign.toLowerCase(Locale.ROOT);
        if (!designs.containsKey(defaultId)) {
            logger.warning("Default design '" + defaultId + "' not found in designs.yml, using a built-in fallback.");
        }
    }

    private Design parse(String id, ConfigurationSection d) {
        DisplayStyle style = DisplayStyle.parse(d.getString("style"), DisplayStyle.TOAST);
        String frame = d.getString("frame", "task").toLowerCase(Locale.ROOT);
        if (!frame.equals("task") && !frame.equals("goal") && !frame.equals("challenge")) {
            logger.warning("Design '" + id + "' has invalid frame '" + frame + "', using 'task'.");
            frame = "task";
        }
        BossBar.Color color = enumValue(BossBar.Color.class, d.getString("bossbar.color"), BossBar.Color.YELLOW);
        BossBar.Overlay overlay = enumValue(BossBar.Overlay.class, d.getString("bossbar.overlay"), BossBar.Overlay.PROGRESS);
        return new Design(id, style, Math.max(10, d.getInt("duration-ticks", 100)), frame,
                d.getString("title", "<achievement_name>"), d.getString("subtitle", ""),
                d.getString("description", "<achievement_description>"),
                IconSpec.fromSection(d.getConfigurationSection("icon"), IconSpec.DEFAULT),
                color, overlay, d.getBoolean("bossbar.animate", true),
                d.getString("bossbar.card", "").toLowerCase(Locale.ROOT),
                d.getString("bossbar.icon", "").toLowerCase(Locale.ROOT),
                d.getBoolean("bossbar.hide-bar", false),
                SoundSpec.parseList(d.get("sound")),
                AnnounceSpec.fromSection(d.getConfigurationSection("chat")));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E def) {
        if (raw == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    /**
     * @return the design, the default design, or a built-in fallback. Never null.
     */
    public Design get(String id) {
        Design d = id == null ? null : designs.get(id.toLowerCase(Locale.ROOT));
        if (d != null) {
            return d;
        }
        d = designs.get(defaultId);
        return d != null ? d : Design.fallback(defaultId);
    }

    public boolean exists(String id) {
        return id != null && designs.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Design> designs() {
        return designs;
    }

    public Map<String, Glyph> glyphs() {
        return glyphs;
    }
}
