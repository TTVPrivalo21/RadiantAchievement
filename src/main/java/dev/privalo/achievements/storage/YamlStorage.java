package dev.privalo.achievements.storage;

import dev.privalo.achievements.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * One YAML file per player: playerdata/&lt;uuid&gt;.yml.
 */
public final class YamlStorage implements Storage {

    private final File folder;

    public YamlStorage(File dataFolder) {
        this.folder = new File(dataFolder, "playerdata");
    }

    @Override
    public void init() throws IOException {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create " + folder);
        }
    }

    @Override
    public PlayerData load(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (name == null || name.isEmpty()) {
            data.name(yaml.getString("name", ""));
        }
        ConfigurationSection progress = yaml.getConfigurationSection("progress");
        if (progress != null) {
            for (String id : progress.getKeys(false)) {
                data.loadProgress(id, progress.getLong(id));
            }
        }
        ConfigurationSection unlocked = yaml.getConfigurationSection("unlocked");
        if (unlocked != null) {
            for (String id : unlocked.getKeys(false)) {
                data.loadUnlocked(id, unlocked.getLong(id));
            }
        }
        data.markClean();
        return data;
    }

    @Override
    public synchronized void save(PlayerData data) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", data.uuid().toString());
        yaml.set("name", data.name());
        for (Map.Entry<String, Long> e : data.progressView().entrySet()) {
            yaml.set("progress." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Long> e : data.unlockedView().entrySet()) {
            yaml.set("unlocked." + e.getKey(), e.getValue());
        }
        File target = new File(folder, data.uuid() + ".yml");
        File tmp = new File(folder, data.uuid() + ".yml.tmp");
        yaml.save(tmp);
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void close() {
        // Files are written synchronously on save; nothing to release.
    }

    @Override
    public String name() {
        return "YAML";
    }
}
