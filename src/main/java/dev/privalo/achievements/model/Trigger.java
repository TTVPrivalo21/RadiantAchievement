package dev.privalo.achievements.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How an achievement is obtained.
 *
 * @param type       trigger type
 * @param target     amount required (1 = instant)
 * @param filter     normalized filter values (empty = anything)
 * @param worlds     lowercase world names where progress counts (empty = all)
 * @param permission permission for {@link TriggerType#PERMISSION}
 * @param location   area for {@link TriggerType#LOCATION}
 */
public record Trigger(TriggerType type, long target, Set<String> filter, Set<String> worlds, String permission,
                      LocationSpec location) {

    public static Trigger fromSection(ConfigurationSection s) {
        if (s == null) {
            return new Trigger(TriggerType.MANUAL, 1, Set.of(), Set.of(), "", null);
        }
        TriggerType type = TriggerType.parse(s.getString("type"));
        if (type == null) {
            throw new IllegalArgumentException("unknown trigger type '" + s.getString("type") + "'");
        }
        long target = Math.max(1, s.getLong("target", 1));
        Set<String> filter = new LinkedHashSet<>();
        for (String raw : s.getStringList("filter")) {
            filter.add(type == TriggerType.COMMAND ? normalizeCommand(raw) : normalize(raw));
        }
        Set<String> worlds = new LinkedHashSet<>();
        for (String w : s.getStringList("worlds")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        LocationSpec loc = null;
        ConfigurationSection ls = s.getConfigurationSection("location");
        if (ls != null) {
            loc = new LocationSpec(ls.getString("world", "world"), ls.getDouble("x"), ls.getDouble("y"),
                    ls.getDouble("z"), Math.max(0.5, ls.getDouble("radius", 3)), ls.getBoolean("ignore-y", false));
        } else if (type == TriggerType.LOCATION) {
            throw new IllegalArgumentException("LOCATION trigger requires a 'location' section");
        }
        String permission = s.getString("permission", "");
        if (type == TriggerType.PERMISSION && permission.isBlank()) {
            throw new IllegalArgumentException("PERMISSION trigger requires 'permission'");
        }
        return new Trigger(type, target, Set.copyOf(filter), Set.copyOf(worlds), permission, loc);
    }

    /** Lowercase, without "minecraft:" namespace. */
    public static String normalize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("minecraft:") ? v.substring(10) : v;
    }

    /** Lowercase command with leading slash. */
    public static String normalizeCommand(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("/") ? v : "/" + v;
    }

    /**
     * @return true when the filter is empty or contains any of the given keys.
     */
    public boolean matches(Collection<String> keys) {
        if (filter.isEmpty()) {
            return true;
        }
        if (type == TriggerType.COMMAND) {
            for (String key : keys) {
                String cmd = normalizeCommand(key);
                for (String f : filter) {
                    if (cmd.equals(f) || cmd.startsWith(f + " ")) {
                        return true;
                    }
                }
            }
            return false;
        }
        for (String key : keys) {
            if (filter.contains(normalize(key))) {
                return true;
            }
        }
        return false;
    }

    public boolean worldAllowed(String world) {
        return worlds.isEmpty() || worlds.contains(world.toLowerCase(Locale.ROOT));
    }

    public List<String> filterList() {
        return List.copyOf(filter);
    }

    /**
     * Spherical (or cylindrical when ignoreY) area.
     */
    public record LocationSpec(String world, double x, double y, double z, double radius, boolean ignoreY) {

        public boolean contains(Location loc) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(world)) {
                return false;
            }
            double dx = loc.getX() - x;
            double dz = loc.getZ() - z;
            double dy = ignoreY ? 0 : loc.getY() - y;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }
}
