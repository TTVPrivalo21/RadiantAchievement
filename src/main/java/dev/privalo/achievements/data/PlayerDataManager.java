package dev.privalo.achievements.data;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Caches the data of online players and serializes every storage access on one worker thread,
 * so a quick rejoin can never read data older than a pending save.
 */
public final class PlayerDataManager {

    private final RadiantAchievements plugin;
    private final Storage storage;
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    /** Data of players that left and are still waiting to be written. */
    private final Map<UUID, PlayerData> pendingSaves = new ConcurrentHashMap<>();
    private final ExecutorService worker;

    public PlayerDataManager(RadiantAchievements plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RadiantAchievements-Storage");
            t.setDaemon(true);
            return t;
        });
    }

    public Storage storage() {
        return storage;
    }

    /** Online player data or null when not loaded yet. */
    public PlayerData get(UUID uuid) {
        return online.get(uuid);
    }

    public PlayerData get(Player player) {
        return online.get(player.getUniqueId());
    }

    public boolean isLoaded(UUID uuid) {
        return online.containsKey(uuid);
    }

    /**
     * Blocking load, used from AsyncPlayerPreLoginEvent.
     */
    public void preload(UUID uuid, String name) {
        PlayerData pending = pendingSaves.get(uuid);
        if (pending != null) {
            pending.name(name);
            online.put(uuid, pending);
            return;
        }
        try {
            PlayerData data = CompletableFuture.supplyAsync(() -> loadOrEmpty(uuid, name), worker).get(15, TimeUnit.SECONDS);
            online.put(uuid, data);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not load achievements of " + name, ex);
            PlayerData failed = new PlayerData(uuid, name);
            failed.loadFailed(true);
            online.put(uuid, failed);
        }
    }

    /**
     * Ensures the player is loaded (e.g. after /reload) and runs {@code then} on the main thread.
     */
    public void ensureLoaded(Player player, Runnable then) {
        if (online.containsKey(player.getUniqueId())) {
            then.run();
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        CompletableFuture.supplyAsync(() -> loadOrEmpty(uuid, name), worker).thenAccept(data ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        online.putIfAbsent(uuid, data);
                        then.run();
                    }
                }));
    }

    private PlayerData loadOrEmpty(UUID uuid, String name) {
        PlayerData pending = pendingSaves.get(uuid);
        if (pending != null) {
            return pending;
        }
        try {
            PlayerData data = storage.load(uuid, name);
            data.name(name);
            return data;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not load achievements of " + uuid, ex);
            PlayerData failed = new PlayerData(uuid, name);
            failed.loadFailed(true);
            return failed;
        }
    }

    /**
     * Loads data for any player (online data is returned directly). Completes off the main thread.
     */
    public CompletableFuture<PlayerData> loadAny(UUID uuid, String name) {
        PlayerData cached = online.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> loadOrEmpty(uuid, name), worker);
    }

    /** Called when a player quits: removes from cache and saves. */
    public void unload(UUID uuid) {
        PlayerData data = online.remove(uuid);
        if (data != null) {
            pendingSaves.put(uuid, data);
            worker.execute(() -> {
                write(data);
                pendingSaves.remove(uuid, data);
            });
        }
    }

    /** Queues a save (used for offline players modified through commands/API). */
    public CompletableFuture<Void> saveAsync(PlayerData data) {
        return CompletableFuture.runAsync(() -> write(data), worker);
    }

    /** Saves every dirty online player asynchronously. */
    public void autosave() {
        List<PlayerData> dirty = new ArrayList<>();
        for (PlayerData data : online.values()) {
            if (data.isDirty()) {
                dirty.add(data);
            }
        }
        if (!dirty.isEmpty()) {
            worker.execute(() -> dirty.forEach(this::write));
        }
    }

    private void write(PlayerData data) {
        if (data.loadFailed()) {
            return;
        }
        try {
            data.markClean();
            storage.save(data);
        } catch (Exception ex) {
            data.markDirty();
            plugin.getLogger().log(Level.SEVERE, "Could not save achievements of " + data.name(), ex);
        }
    }

    /** Loads players already online (plugin reload). */
    public void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureLoaded(player, () -> {
            });
        }
    }

    /** Flushes everything and stops the worker. Blocks (used on disable). */
    public void shutdown() {
        List<PlayerData> all = new ArrayList<>(online.values());
        all.addAll(pendingSaves.values());
        worker.execute(() -> all.forEach(this::write));
        worker.shutdown();
        try {
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Storage worker did not finish in 30s; some data may not be saved.");
                worker.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        online.clear();
        pendingSaves.clear();
    }

    public Map<UUID, PlayerData> onlineView() {
        return java.util.Collections.unmodifiableMap(online);
    }
}
