package dev.privalo.achievements.data;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent achievement data of one player. Thread safe.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private final Map<String, Long> progress = new ConcurrentHashMap<>();
    private final Map<String, Long> unlocked = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    private volatile boolean loadFailed;
    private volatile String lastUnlocked;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            this.dirty = true;
        }
    }

    public long progress(String id) {
        return progress.getOrDefault(id, 0L);
    }

    public void setProgress(String id, long value) {
        if (value <= 0) {
            if (progress.remove(id) != null) {
                dirty = true;
            }
            return;
        }
        Long old = progress.put(id, value);
        if (old == null || old != value) {
            dirty = true;
        }
    }

    public boolean isUnlocked(String id) {
        return unlocked.containsKey(id);
    }

    /** @return unlock timestamp (epoch millis) or 0. */
    public long unlockedAt(String id) {
        return unlocked.getOrDefault(id, 0L);
    }

    public boolean unlock(String id, long timestamp) {
        if (unlocked.putIfAbsent(id, timestamp) == null) {
            dirty = true;
            lastUnlocked = id;
            return true;
        }
        return false;
    }

    public boolean revoke(String id) {
        boolean removed = unlocked.remove(id) != null;
        progress.remove(id);
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    public void clear() {
        progress.clear();
        unlocked.clear();
        lastUnlocked = null;
        dirty = true;
    }

    public Map<String, Long> progressView() {
        return Collections.unmodifiableMap(progress);
    }

    public Map<String, Long> unlockedView() {
        return Collections.unmodifiableMap(unlocked);
    }

    /** Used by storages while loading (does not mark dirty). */
    public void loadProgress(String id, long value) {
        progress.put(id, value);
    }

    /** Used by storages while loading (does not mark dirty). */
    public void loadUnlocked(String id, long at) {
        unlocked.put(id, at);
        if (lastUnlocked == null || unlocked.getOrDefault(lastUnlocked, 0L) < at) {
            lastUnlocked = id;
        }
    }

    public String lastUnlocked() {
        return lastUnlocked;
    }

    public int unlockedCount() {
        return unlocked.size();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    /** When loading failed the data is never saved, to avoid wiping the stored copy. */
    public boolean loadFailed() {
        return loadFailed;
    }

    public void loadFailed(boolean failed) {
        this.loadFailed = failed;
    }
}
