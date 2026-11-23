package dev.privalo.achievements.util;

import org.bukkit.Bukkit;

/**
 * Parses the running Minecraft version (works with both "1.21.4" and the new "26.1" scheme).
 */
public final class ServerVersion {

    private static final int[] VERSION = parse();

    private ServerVersion() {
    }

    private static int[] parse() {
        String raw = Bukkit.getBukkitVersion();
        int dash = raw.indexOf('-');
        if (dash > 0) {
            raw = raw.substring(0, dash);
        }
        String[] parts = raw.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /**
     * @return true when the server version is greater than or equal to the given one.
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (VERSION[0] != major) {
            return VERSION[0] > major;
        }
        if (VERSION[1] != minor) {
            return VERSION[1] > minor;
        }
        return VERSION[2] >= patch;
    }

    public static String asString() {
        return VERSION[0] + "." + VERSION[1] + (VERSION[2] > 0 ? "." + VERSION[2] : "");
    }
}
