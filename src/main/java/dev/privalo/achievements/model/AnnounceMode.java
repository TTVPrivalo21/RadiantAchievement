package dev.privalo.achievements.model;

import java.util.Locale;

/**
 * Chat announcement format.
 */
public enum AnnounceMode {
    /** Rich multi-line banner (hover, gradients...). */
    DESIGNED,
    /** One clean line. */
    COMPACT,
    /** No chat message. */
    NONE;

    public static AnnounceMode parse(String raw, AnnounceMode def) {
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
