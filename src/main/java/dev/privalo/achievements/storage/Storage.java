package dev.privalo.achievements.storage;

import dev.privalo.achievements.data.PlayerData;

import java.util.UUID;

/**
 * Persistence backend. Every method may block and is always called off the main thread
 * (except {@link #init()} and {@link #close()} during enable/disable).
 */
public interface Storage {

    void init() throws Exception;

    /**
     * Loads a player. Returns empty data (never null) when the player has no stored data.
     */
    PlayerData load(UUID uuid, String name) throws Exception;

    void save(PlayerData data) throws Exception;

    void close();

    String name();
}
