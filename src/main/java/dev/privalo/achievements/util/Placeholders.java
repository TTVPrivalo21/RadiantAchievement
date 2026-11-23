package dev.privalo.achievements.util;

import dev.privalo.achievements.model.Achievement;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Builds the MiniMessage placeholders shared by designs, chat formats, rewards and the GUI.
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * {@code <player> <achievement_name> <achievement_description> <achievement_id> <category>
     * <progress> <target> <percent> <design> <bar>}
     */
    public static TagResolver achievement(Achievement a, String playerName, long progress, String designId) {
        long target = a.target();
        double ratio = target <= 0 ? 1 : Math.min(1d, (double) progress / target);
        return TagResolver.resolver(
                Placeholder.unparsed("player", playerName == null ? "" : playerName),
                Placeholder.parsed("achievement_name", a.name()),
                Placeholder.parsed("achievement_description", a.description()),
                Placeholder.unparsed("achievement_id", a.id()),
                Placeholder.unparsed("category", a.category()),
                Placeholder.unparsed("progress", String.valueOf(Math.min(progress, target))),
                Placeholder.unparsed("target", String.valueOf(target)),
                Placeholder.unparsed("percent", String.valueOf((int) Math.floor(ratio * 100))),
                Placeholder.unparsed("design", designId == null ? a.designId() : designId),
                Placeholder.parsed("bar", Text.bar(ratio, 20)));
    }

    /** Placeholders for free-form notifications sent through the API. */
    public static TagResolver custom(String playerName, String title, String subtitle, String description) {
        return TagResolver.resolver(
                Placeholder.unparsed("player", playerName == null ? "" : playerName),
                Placeholder.parsed("achievement_name", title == null ? "" : title),
                Placeholder.parsed("achievement_description", description == null ? "" : description),
                Placeholder.parsed("custom_subtitle", subtitle == null ? "" : subtitle),
                Placeholder.unparsed("achievement_id", "custom"),
                Placeholder.unparsed("category", "custom"),
                Placeholder.unparsed("progress", "1"),
                Placeholder.unparsed("target", "1"),
                Placeholder.unparsed("percent", "100"),
                Placeholder.unparsed("design", "custom"),
                Placeholder.parsed("bar", Text.bar(1, 20)));
    }
}
