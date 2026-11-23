package dev.privalo.achievements.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A sound to play: vanilla ("minecraft:entity.player.levelup") or resource pack ("radiant:achievement.epic").
 */
public record SoundSpec(String key, float volume, float pitch, int delayTicks) {

    public SoundSpec {
        key = normalizeKey(key);
        volume = Math.max(0f, volume);
        pitch = Math.max(0.5f, Math.min(2f, pitch));
        delayTicks = Math.max(0, delayTicks);
    }

    /**
     * Accepts "minecraft:x.y", "x.y" and legacy enum style "ENTITY_PLAYER_LEVELUP" (best effort).
     */
    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "minecraft:ui.toast.challenge_complete";
        }
        String key = raw.trim();
        if (!key.contains(":") && !key.contains(".") && key.equals(key.toUpperCase(Locale.ROOT))) {
            key = key.toLowerCase(Locale.ROOT).replace('_', '.');
        }
        key = key.toLowerCase(Locale.ROOT);
        return key.contains(":") ? key : "minecraft:" + key;
    }

    /**
     * Parses a "sound" value that can be a section, a list of maps or a plain string.
     */
    public static List<SoundSpec> parseList(Object raw) {
        List<SoundSpec> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof ConfigurationSection section) {
            out.add(fromSection(section));
        } else if (raw instanceof Map<?, ?> map) {
            out.add(fromMap(map));
        } else if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add(fromMap(map));
                } else if (entry instanceof ConfigurationSection section) {
                    out.add(fromSection(section));
                } else if (entry != null) {
                    out.add(new SoundSpec(entry.toString(), 1f, 1f, 0));
                }
            }
        } else {
            out.add(new SoundSpec(raw.toString(), 1f, 1f, 0));
        }
        return out;
    }

    private static SoundSpec fromSection(ConfigurationSection s) {
        return new SoundSpec(s.getString("sound"), (float) s.getDouble("volume", 1), (float) s.getDouble("pitch", 1),
                s.getInt("delay-ticks", 0));
    }

    private static SoundSpec fromMap(Map<?, ?> map) {
        return new SoundSpec(String.valueOf(map.get("sound")), (float) num(map.get("volume"), 1),
                (float) num(map.get("pitch"), 1), (int) num(map.get("delay-ticks"), 0));
    }

    static double num(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
