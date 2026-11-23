package dev.privalo.achievements.display;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.SoundSpec;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Plays vanilla and resource pack sounds (any namespaced key) with optional delay.
 */
public final class SoundPlayer {

    private final RadiantAchievements plugin;

    public SoundPlayer(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, List<SoundSpec> sounds) {
        for (SoundSpec spec : sounds) {
            if (spec.delayTicks() <= 0) {
                playNow(player, spec);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        playNow(player, spec);
                    }
                }, spec.delayTicks());
            }
        }
    }

    /**
     * @return false when the key is not a valid namespaced key.
     */
    public boolean playNow(Player player, SoundSpec spec) {
        Key key;
        try {
            key = Key.key(spec.key());
        } catch (InvalidKeyException ex) {
            plugin.getLogger().warning("Invalid sound key '" + spec.key() + "'.");
            return false;
        }
        player.playSound(Sound.sound(key, Sound.Source.MASTER, spec.volume(), spec.pitch()), Sound.Emitter.self());
        return true;
    }
}
