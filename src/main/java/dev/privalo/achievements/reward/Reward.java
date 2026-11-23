package dev.privalo.achievements.reward;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.IconSpec;
import dev.privalo.achievements.model.SoundSpec;
import dev.privalo.achievements.util.Items;
import dev.privalo.achievements.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A reward given when an achievement is unlocked.
 */
public sealed interface Reward permits Reward.Command, Reward.Money, Reward.Experience, Reward.Item,
        Reward.Message, Reward.Effect, Reward.Fireworks {

    /**
     * Gives the reward. Always called on the main thread with an online player.
     */
    void give(RadiantAchievements plugin, Player player, TagResolver context);

    /**
     * Parses a reward map from achievements.yml.
     *
     * @throws IllegalArgumentException if the type is unknown or a value is invalid
     */
    static Reward parse(Map<?, ?> map) {
        String type = str(map, "type", "").toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (type) {
            case "console-command", "console", "command" -> new Command(true, str(map, "command", ""));
            case "player-command" -> new Command(false, str(map, "command", ""));
            case "money", "vault" -> new Money(num(map, "amount", 0));
            case "xp", "exp", "experience" -> new Experience((int) num(map, "amount", 0),
                    Boolean.parseBoolean(str(map, "levels", "false")));
            case "item" -> new Item(IconSpec.fromMap(map), (int) num(map, "amount", 1), str(map, "name", ""),
                    stringList(map.get("lore")));
            case "message" -> new Message(str(map, "message", ""), false);
            case "broadcast" -> new Message(str(map, "message", ""), true);
            case "effect", "potion" -> new Effect(str(map, "effect", "speed"), (int) num(map, "duration-ticks", 200),
                    (int) num(map, "amplifier", 0));
            case "firework", "fireworks" -> new Fireworks(str(map, "color", "#FFD36B"), (int) num(map, "power", 1));
            default -> throw new IllegalArgumentException("unknown reward type '" + type + "'");
        };
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object o = map.get(key);
        return o == null ? def : o.toString();
    }

    private static double num(Map<?, ?> map, String key, double def) {
        Object o = map.get(key);
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'" + key + "' must be a number, got '" + o + "'");
        }
    }

    private static List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object e : list) {
                out.add(String.valueOf(e));
            }
        } else if (o != null) {
            out.add(o.toString());
        }
        return out;
    }

    /** Replaces {@code <player>} textually (commands are not MiniMessage) and applies PlaceholderAPI. */
    private static String fillCommand(Player player, String command) {
        String cmd = command.replace("<player>", player.getName()).replace("<uuid>", player.getUniqueId().toString());
        cmd = Text.applyPlaceholders(player, cmd);
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    record Command(boolean console, String command) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            if (command.isBlank()) {
                return;
            }
            String cmd = fillCommand(player, command);
            if (console) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else {
                player.performCommand(cmd);
            }
        }
    }

    record Money(double amount) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            if (plugin.hooks().vault() == null) {
                plugin.getLogger().warning("Money reward of " + amount + " skipped for " + player.getName()
                        + ": Vault/economy not available.");
                return;
            }
            plugin.hooks().vault().deposit(player, amount);
        }
    }

    record Experience(int amount, boolean levels) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            if (levels) {
                player.giveExpLevels(amount);
            } else {
                player.giveExp(amount);
            }
        }
    }

    record Item(IconSpec icon, int amount, String name, List<String> lore) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            int left = Math.max(1, amount);
            while (left > 0) {
                ItemStack stack = Items.build(plugin, icon, left);
                int given = stack.getAmount();
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (!name.isBlank()) {
                        meta.displayName(Text.item(player, name, context));
                    }
                    if (!lore.isEmpty()) {
                        meta.lore(Text.items(player, lore, context));
                    }
                    stack.setItemMeta(meta);
                }
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                left -= given;
            }
        }
    }

    record Message(String message, boolean broadcast) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            Component component = Text.parse(player, message, context);
            if (broadcast) {
                Bukkit.getServer().sendMessage(component);
            } else {
                player.sendMessage(component);
            }
        }
    }

    record Effect(String effect, int durationTicks, int amplifier) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            NamespacedKey key = NamespacedKey.fromString(SoundSpec.normalizeKey(effect).replace('.', '_'));
            PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect '" + effect + "' in a reward.");
                return;
            }
            player.addPotionEffect(new PotionEffect(type, Math.max(1, durationTicks), Math.max(0, amplifier)));
        }
    }

    record Fireworks(String color, int power) implements Reward {
        @Override
        public void give(RadiantAchievements plugin, Player player, TagResolver context) {
            Color c;
            try {
                c = Color.fromRGB(Integer.parseInt(color.replace("#", ""), 16) & 0xFFFFFF);
            } catch (NumberFormatException ex) {
                c = Color.YELLOW;
            }
            Color finalColor = c;
            player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class, fw -> {
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(finalColor)
                        .withFade(Color.WHITE).trail(true).flicker(true).build());
                meta.setPower(Math.max(0, Math.min(3, power)));
                fw.setFireworkMeta(meta);
                fw.getPersistentDataContainer().set(plugin.fireworkKey(), PersistentDataType.BYTE, (byte) 1);
            });
        }
    }
}
