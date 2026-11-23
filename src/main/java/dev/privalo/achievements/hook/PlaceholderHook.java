package dev.privalo.achievements.hook;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.data.PlayerData;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.util.Text;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PlaceholderAPI expansion: {@code %radiantachievements_<placeholder>%}.
 *
 * <ul>
 *   <li>unlocked, total, locked, percent, latest</li>
 *   <li>has_&lt;id&gt;, progress_&lt;id&gt;, target_&lt;id&gt;, percent_&lt;id&gt;, bar_&lt;id&gt;, name_&lt;id&gt;, date_&lt;id&gt;</li>
 *   <li>unlocked_category_&lt;category&gt;, total_category_&lt;category&gt;</li>
 * </ul>
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private static PlaceholderHook instance;
    private final RadiantAchievements plugin;

    private PlaceholderHook(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    static void registerHook(RadiantAchievements plugin) {
        instance = new PlaceholderHook(plugin);
        instance.register();
        Text.setPlaceholderProcessor(PlaceholderAPI::setPlaceholders);
    }

    static void unregisterHook() {
        if (instance != null) {
            instance.unregister();
            instance = null;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "radiantachievements";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase(Locale.ROOT);
        int total = plugin.achievements().size();
        if (p.equals("total")) {
            return String.valueOf(total);
        }
        PlayerData data = player == null ? null : plugin.data().get(player.getUniqueId());
        if (data == null) {
            return p.startsWith("has_") ? "false" : "0";
        }
        switch (p) {
            case "unlocked":
                return String.valueOf(plugin.manager().unlockedCount(data));
            case "locked":
                return String.valueOf(total - plugin.manager().unlockedCount(data));
            case "percent":
                return String.format(Locale.ROOT, "%.1f", plugin.manager().completion(data));
            case "latest": {
                Achievement last = plugin.achievements().get(data.lastUnlocked());
                return last == null ? "" : Text.legacy(Text.parse(last.name()));
            }
            default:
                break;
        }
        if (p.startsWith("unlocked_category_") || p.startsWith("total_category_")) {
            boolean onlyUnlocked = p.startsWith("unlocked_");
            String category = p.substring(onlyUnlocked ? 18 : 15);
            int count = 0;
            for (Achievement a : plugin.achievements().all()) {
                if (a.category().equalsIgnoreCase(category) && (!onlyUnlocked || data.isUnlocked(a.id()))) {
                    count++;
                }
            }
            return String.valueOf(count);
        }
        int underscore = p.indexOf('_');
        if (underscore < 0) {
            return null;
        }
        String kind = p.substring(0, underscore);
        Achievement a = plugin.achievements().get(p.substring(underscore + 1));
        if (a == null) {
            return null;
        }
        long progress = data.isUnlocked(a.id()) ? a.target() : data.progress(a.id());
        return switch (kind) {
            case "has" -> String.valueOf(data.isUnlocked(a.id()));
            case "progress" -> String.valueOf(progress);
            case "target" -> String.valueOf(a.target());
            case "percent" -> String.valueOf((int) (Math.min(1d, (double) progress / a.target()) * 100));
            case "bar" -> Text.legacy(Text.parse(Text.bar((double) progress / a.target(), 20)));
            case "name" -> Text.legacy(Text.parse(a.name()));
            case "date" -> data.isUnlocked(a.id())
                    ? new SimpleDateFormat(plugin.getConfig().getString("gui.date-format", "dd/MM/yyyy HH:mm"))
                    .format(new Date(data.unlockedAt(a.id()))) : "";
            default -> null;
        };
    }
}
