package dev.privalo.achievements.display;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.AnnounceMode;
import dev.privalo.achievements.model.AnnounceScope;
import dev.privalo.achievements.model.AnnounceSpec;
import dev.privalo.achievements.model.Design;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the chat announcement of an unlocked achievement (designed banner or compact line, public or private).
 */
public final class ChatAnnouncer {

    private static final String DEFAULT_COMPACT =
            "<gold>★</gold> <white><player></white> <gray>desbloqueó <hover:show_text:'<achievement_description>'><yellow>[<achievement_name>]</yellow></hover>";
    private static final List<String> DEFAULT_DESIGNED = List.of(
            "",
            " <gold>★</gold> <gradient:#ffd36b:#ff9f1c><b>LOGRO DESBLOQUEADO</b></gradient>",
            " <white><player></white> <gray>→ <hover:show_text:'<achievement_description>'><yellow><achievement_name></yellow></hover>",
            "");

    private final RadiantAchievements plugin;

    public ChatAnnouncer(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /** Resolves achievement override → design → config defaults. */
    public AnnounceSpec resolve(Achievement achievement, Design design) {
        AnnounceSpec defaults = new AnnounceSpec(plugin.settings().defaultAnnounceMode,
                plugin.settings().defaultAnnounceScope, DEFAULT_COMPACT, DEFAULT_DESIGNED);
        AnnounceSpec spec = design.announce().over(defaults);
        return achievement == null ? spec : achievement.announceOverride().over(spec);
    }

    public void announce(Player player, AnnounceSpec spec, TagResolver context) {
        if (spec.mode() == null || spec.mode() == AnnounceMode.NONE) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (spec.mode() == AnnounceMode.DESIGNED) {
            for (String line : spec.designed()) {
                lines.add(Text.parse(player, line, context));
            }
        } else {
            lines.add(Text.parse(player, spec.compact(), context));
        }
        if (spec.scope() == AnnounceScope.PRIVATE) {
            lines.forEach(player::sendMessage);
        } else {
            lines.forEach(line -> Bukkit.getServer().sendMessage(line));
        }
    }
}
