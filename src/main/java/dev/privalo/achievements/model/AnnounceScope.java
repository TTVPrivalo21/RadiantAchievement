package dev.privalo.achievements.model;

import java.util.Locale;

/**
 * Who receives a chat announcement.
 */
public enum AnnounceScope {
    /** Every online player and the console. */
    PUBLIC,
    /** Only the player that unlocked the achievement. */
    PRIVATE;

    public static AnnounceScope parse(String raw, AnnounceScope def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
