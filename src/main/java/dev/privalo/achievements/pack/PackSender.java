package dev.privalo.achievements.pack;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.config.Settings;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Sends the generated pack to players (built-in HTTP server or external URL) with its SHA-1 so the client
 * re-downloads only when the content changes.
 */
public final class PackSender {

    private final RadiantAchievements plugin;
    private final UUID packId;

    public PackSender(RadiantAchievements plugin) {
        this.plugin = plugin;
        this.packId = UUID.nameUUIDFromBytes("radiantachievements:pack".getBytes(StandardCharsets.UTF_8));
    }

    /** Whether this plugin is responsible for sending its pack. */
    public boolean active() {
        Settings s = plugin.settings();
        if (!s.packEnabled || !s.packSendOnJoin || s.oraxenMerge) {
            return false;
        }
        return !s.usesBuiltInHost() || plugin.packServer().running();
    }

    /** Sends (or replaces) the pack for one player. */
    public void send(Player player) {
        if (!active() || plugin.packGenerator().lastHash().isEmpty() && plugin.settings().usesBuiltInHost()) {
            return;
        }
        String url = urlFor(player);
        try {
            String hash = plugin.packGenerator().lastHash();
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), hash);
            ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .replace(false)
                    .required(plugin.settings().packRequired);
            if (!plugin.settings().packPrompt.isBlank()) {
                request.prompt(Text.parse(player, plugin.settings().packPrompt));
            }
            player.sendResourcePacks(request.build());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid resource pack URL '" + url + "': " + ex.getMessage());
        }
    }

    /** Resends the pack to everyone online (after /ach pack). */
    public void sendAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p);
        }
    }

    /**
     * External URL, or {@code http://<address>:<port>/radiantachievements/<sha1>.zip} where address is
     * resource-pack.http.public-address, the host the player typed to connect, server-ip or localhost.
     */
    public String urlFor(Player player) {
        Settings s = plugin.settings();
        if (!s.usesBuiltInHost()) {
            return s.packExternalUrl;
        }
        String host = s.httpPublicAddress;
        if (host.isEmpty()) {
            InetSocketAddress virtual = player.getVirtualHost();
            host = virtual != null ? virtual.getHostString() : "";
        }
        if (host.isEmpty()) {
            host = Bukkit.getIp();
        }
        if (host == null || host.isEmpty() || host.equals("0.0.0.0")) {
            host = "localhost";
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + plugin.packServer().port() + PackHttpServer.CONTEXT
                + plugin.packGenerator().lastHash() + ".zip";
    }
}
