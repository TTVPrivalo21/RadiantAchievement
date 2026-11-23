package dev.privalo.achievements.model;

import java.util.Locale;

/**
 * Everything that can make an achievement progress.
 */
public enum TriggerType {
    JOIN,
    BLOCK_BREAK,
    BLOCK_PLACE,
    KILL_MOB,
    KILL_PLAYER,
    CRAFT,
    FISH,
    CONSUME,
    /** Progress unit: minutes played. */
    PLAYTIME,
    /** Progress unit: blocks travelled. */
    DISTANCE,
    LOCATION,
    /** WorldGuard region entered. */
    REGION_ENTER,
    COMMAND,
    PERMISSION,
    /** Progress = highest experience level reached. */
    LEVEL_REACH,
    DEATH,
    /** MythicMobs mob killed. */
    MYTHIC_KILL,
    /** Citizens NPC right clicked. */
    NPC_INTERACT,
    /** Only through commands or the API. */
    MANUAL;

    public static TriggerType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
