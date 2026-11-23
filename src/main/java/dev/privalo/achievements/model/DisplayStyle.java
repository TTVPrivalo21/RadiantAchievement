package dev.privalo.achievements.model;

/**
 * Where/how a notification is rendered on the player's screen.
 */
public enum DisplayStyle {
    /** Real advancement toast (top-right corner) with a custom icon. */
    TOAST,
    /** Boss bar at the top of the screen, optionally draining while visible. */
    BOSSBAR_OVERLAY,
    /** Action bar above the hotbar. */
    ACTIONBAR,
    /** Classic title + subtitle. */
    TITLE,
    /** Center screen banner made of a font sprite (title) + text (subtitle) + description (action bar). */
    CENTER_BANNER;

    public static DisplayStyle parse(String raw, DisplayStyle def) {
        if (raw == null) {
            return def;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
