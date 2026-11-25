package dev.privalo.achievements.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.privalo.achievements.RadiantAchievements;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous update checker (GitHub Releases or SpigotMC through Spiget).
 * <p>
 * Runs off the main thread with a 5 s timeout and a User-Agent header. Any network, HTTP (404 = no releases
 * yet, private repo...) or parse problem is ignored silently: only a FINE log line, never a stack trace.
 * Config values are read without explicit defaults so an old config.yml falls back to the jar's defaults.
 */
public final class UpdateChecker implements Listener {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String ADMIN_PERMISSION = "radiantachievements.admin";

    /** What the last successful check found. */
    public record Result(String installed, String latest, String url, String source) {
    }

    private final RadiantAchievements plugin;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Result available;

    public UpdateChecker(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /** Update found by the last check, or null when up to date / unknown. */
    public Result available() {
        return available;
    }

    /** Starts a new asynchronous check (on enable and on /ach reload). */
    public void check() {
        int run = generation.incrementAndGet();
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("update-checker.enabled")) {
            available = null;
            return;
        }
        String source = text(config.getString("update-checker.source"), "github").toLowerCase(Locale.ROOT);
        String installed = plugin.getPluginMeta().getVersion();
        String userAgent = "RadiantAchievements/" + installed + " (update-checker; +https://github.com/TTVPrivalo21/RadiantAchievements)";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        CompletableFuture<Result> future;
        if (source.equals("spigot")) {
            int id = config.getInt("update-checker.spigot-resource-id");
            if (id <= 0) {
                plugin.getLogger().fine("update-checker: source=spigot but spigot-resource-id is 0; check skipped.");
                return;
            }
            URI api = URI.create("https://api.spiget.org/v2/resources/" + id + "/versions/latest");
            String download = "https://www.spigotmc.org/resources/" + id + "/";
            future = fetch(client, api, userAgent, "application/json").thenApply(json -> {
                String name = string(json, "name");
                return name == null ? null : new Result(installed, Versions.normalize(name), download, "SpigotMC");
            });
        } else {
            String repo = text(config.getString("update-checker.github-repo"), "");
            if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                plugin.getLogger().fine("update-checker: invalid github-repo '" + repo + "'; check skipped.");
                return;
            }
            URI api = URI.create("https://api.github.com/repos/" + repo + "/releases/latest");
            String fallback = "https://github.com/" + repo + "/releases/latest";
            future = fetch(client, api, userAgent, "application/vnd.github+json").thenApply(json -> {
                String tag = string(json, "tag_name");
                String url = string(json, "html_url");
                return tag == null ? null : new Result(installed, Versions.normalize(tag),
                        url != null && url.startsWith("https://") ? url : fallback, "GitHub");
            });
        }
        future.whenComplete((result, error) -> {
            if (run != generation.get()) {
                return;
            }
            if (error != null || result == null) {
                plugin.getLogger().fine("update-checker: could not check ("
                        + (error == null ? "no version in answer" : rootMessage(error)) + ").");
                return;
            }
            if (!Versions.isNewer(result.latest(), installed)) {
                available = null;
                plugin.getLogger().fine("update-checker: " + installed + " is up to date (latest " + result.latest() + ").");
                return;
            }
            available = result;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> announce(result));
            }
        });
    }

    private static String text(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim();
    }

    /** GET with timeout + User-Agent; completes exceptionally for any non-200 answer. */
    private static CompletableFuture<JsonElement> fetch(HttpClient client, URI uri, String userAgent, String accept) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(TIMEOUT.toSeconds() * 2, TimeUnit.SECONDS)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    return JsonParser.parseString(response.body());
                });
    }

    private static String string(JsonElement json, String key) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private void announce(Result result) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getLogger().warning("============================================================");
        plugin.getLogger().warning(" ¡Hay una nueva versión de RadiantAchievements disponible! (" + result.source() + ")");
        plugin.getLogger().warning("   Versión instalada : " + result.installed());
        plugin.getLogger().warning("   Nueva versión     : " + result.latest());
        plugin.getLogger().warning("   Descarga          : " + result.url());
        if (result.source().equals("SpigotMC")) {
            plugin.getLogger().warning("   Autor             : " + text(plugin.getConfig().getString("update-checker.spigot-author-url"),
                    "https://www.spigotmc.org/resources/authors/thekillerv2.1619961/"));
        }
        plugin.getLogger().warning("============================================================");
        if (plugin.getConfig().getBoolean("update-checker.notify-admins-on-join")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                notify(player, result);
            }
        }
    }

    private void notify(Player player, Result result) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        TagResolver ctx = TagResolver.resolver(
                Placeholder.unparsed("installed", result.installed()),
                Placeholder.unparsed("latest", result.latest()),
                Placeholder.unparsed("source", result.source()),
                Placeholder.unparsed("url", result.url()),
                Placeholder.styling("download", ClickEvent.openUrl(result.url())));
        plugin.settings().send(player, "update-available", ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Result result = available;
        FileConfiguration config = plugin.getConfig();
        if (result == null || !config.getBoolean("update-checker.enabled")
                || !config.getBoolean("update-checker.notify-admins-on-join")) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                notify(player, result);
            }
        }, 60L);
    }
}
