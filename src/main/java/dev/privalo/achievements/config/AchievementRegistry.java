package dev.privalo.achievements.config;

import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.AnnounceSpec;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.model.SoundSpec;
import dev.privalo.achievements.model.Trigger;
import dev.privalo.achievements.model.TriggerType;
import dev.privalo.achievements.reward.Reward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads achievements.yml and keeps an index by trigger type. Achievements can also be added through the API.
 */
public final class AchievementRegistry {

    private final Logger logger;
    private volatile Map<String, Achievement> achievements = Map.of();
    private volatile Map<TriggerType, List<Achievement>> byTrigger = Map.of();
    private final Map<String, Achievement> external = new LinkedHashMap<>();

    public AchievementRegistry(Logger logger) {
        this.logger = logger;
    }

    public void load(File file, DesignRegistry designs) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Achievement> loaded = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("achievements");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                try {
                    Achievement a = parse(key.toLowerCase(Locale.ROOT), s);
                    if (!designs.exists(a.designId())) {
                        logger.warning("Achievement '" + a.id() + "' uses unknown design '" + a.designId()
                                + "', the default design will be used.");
                    }
                    loaded.put(a.id(), a);
                } catch (IllegalArgumentException ex) {
                    logger.warning("Achievement '" + key + "' skipped: " + ex.getMessage());
                }
            }
        }
        synchronized (external) {
            for (Achievement a : external.values()) {
                loaded.putIfAbsent(a.id(), a);
            }
        }
        for (Achievement a : loaded.values()) {
            for (String req : a.requires()) {
                if (!loaded.containsKey(req)) {
                    logger.warning("Achievement '" + a.id() + "' requires unknown achievement '" + req + "'.");
                }
            }
        }
        rebuild(loaded);
    }

    private void rebuild(Map<String, Achievement> source) {
        List<Achievement> sorted = new ArrayList<>(source.values());
        sorted.sort(Comparator.comparingInt(Achievement::order).thenComparing(Achievement::id));
        Map<String, Achievement> ordered = new LinkedHashMap<>();
        Map<TriggerType, List<Achievement>> index = new EnumMap<>(TriggerType.class);
        for (Achievement a : sorted) {
            ordered.put(a.id(), a);
            index.computeIfAbsent(a.trigger().type(), t -> new ArrayList<>()).add(a);
        }
        index.replaceAll((t, list) -> List.copyOf(list));
        this.achievements = Collections.unmodifiableMap(ordered);
        this.byTrigger = Collections.unmodifiableMap(index);
    }

    private Achievement parse(String id, ConfigurationSection s) {
        Trigger trigger = Trigger.fromSection(s.getConfigurationSection("trigger"));
        List<Reward> rewards = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("rewards")) {
            rewards.add(Reward.parse(map));
        }
        List<String> requires = new ArrayList<>();
        for (String r : s.getStringList("requires")) {
            requires.add(r.toLowerCase(Locale.ROOT));
        }
        IconSpec icon = s.isConfigurationSection("icon")
                ? IconSpec.fromSection(s.getConfigurationSection("icon"), IconSpec.DEFAULT) : null;
        return new Achievement(id,
                s.getString("name", id),
                s.getString("description", ""),
                s.getString("design", "").toLowerCase(Locale.ROOT),
                s.getString("category", "general"),
                s.getBoolean("secret", false),
                s.getInt("order", 0),
                List.copyOf(requires),
                icon,
                trigger,
                AnnounceSpec.fromSection(s.getConfigurationSection("announce")),
                List.copyOf(SoundSpec.parseList(s.get("sound"))),
                List.copyOf(rewards));
    }

    /**
     * Registers an achievement from code. It survives reloads until {@link #unregister(String)} is called.
     */
    public void register(Achievement achievement) {
        synchronized (external) {
            external.put(achievement.id(), achievement);
        }
        Map<String, Achievement> copy = new LinkedHashMap<>(achievements);
        copy.put(achievement.id(), achievement);
        rebuild(copy);
    }

    public boolean unregister(String id) {
        boolean removed;
        synchronized (external) {
            removed = external.remove(id) != null;
        }
        if (removed) {
            Map<String, Achievement> copy = new LinkedHashMap<>(achievements);
            copy.remove(id);
            rebuild(copy);
        }
        return removed;
    }

    public Achievement get(String id) {
        return id == null ? null : achievements.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Achievement> all() {
        return achievements.values();
    }

    public List<Achievement> byTrigger(TriggerType type) {
        return byTrigger.getOrDefault(type, List.of());
    }

    public boolean hasTrigger(TriggerType type) {
        return !byTrigger(type).isEmpty();
    }

    public Set<String> ids() {
        return achievements.keySet();
    }

    public Set<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        for (Achievement a : achievements.values()) {
            out.add(a.category());
        }
        return out;
    }

    public int size() {
        return achievements.size();
    }
}
