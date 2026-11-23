package dev.privalo.achievements.trigger;

import dev.privalo.achievements.RadiantAchievements;
import dev.privalo.achievements.model.Achievement;
import dev.privalo.achievements.model.TriggerType;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit events → achievement triggers.
 */
public final class TriggerListener implements Listener {

    /** Runtime (non persistent) tracking state per player. */
    private static final class State {
        double distance;
        Set<String> regions = Set.of();
        final Set<String> insideLocations = new HashSet<>();
    }

    private final RadiantAchievements plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public TriggerListener(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), u -> new State());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.manager().handle(event.getPlayer(), TriggerType.BLOCK_BREAK, event.getBlock().getType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        plugin.manager().handle(event.getPlayer(), TriggerType.BLOCK_PLACE, event.getBlockPlaced().getType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        if (entity instanceof Player victim) {
            if (!victim.getUniqueId().equals(killer.getUniqueId())) {
                plugin.manager().handle(killer, TriggerType.KILL_PLAYER, victim.getName(), 1);
            }
        } else {
            plugin.manager().handle(killer, TriggerType.KILL_MOB, entity.getType().getKey().getKey(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String cause = player.getLastDamageCause() == null ? "unknown"
                : player.getLastDamageCause().getCause().name();
        plugin.manager().handle(player, TriggerType.DEATH, cause, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();
        if (event.isShiftClick()) {
            // Shift click crafts as many times as the smallest ingredient stack allows.
            int crafts = Integer.MAX_VALUE;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient != null && !ingredient.getType().isAir()) {
                    crafts = Math.min(crafts, ingredient.getAmount());
                }
            }
            if (crafts != Integer.MAX_VALUE) {
                amount *= crafts;
            }
        }
        plugin.manager().handle(player, TriggerType.CRAFT, result.getType().getKey().getKey(), amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Entity caught = event.getCaught();
        String key = caught instanceof Item item ? item.getItemStack().getType().getKey().getKey() : "any";
        plugin.manager().handle(event.getPlayer(), TriggerType.FISH, key, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        plugin.manager().handle(event.getPlayer(), TriggerType.CONSUME, event.getItem().getType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        plugin.manager().handleReach(event.getPlayer(), TriggerType.LEVEL_REACH, event.getNewLevel());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        plugin.manager().handle(event.getPlayer(), TriggerType.COMMAND, event.getMessage(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        Player player = event.getPlayer();
        if (plugin.achievements().hasTrigger(TriggerType.DISTANCE) && from.getWorld() == to.getWorld()) {
            double moved = from.distance(to);
            if (moved > 0 && moved < 10) {
                State state = state(player);
                state.distance += moved;
                if (state.distance >= 1) {
                    long blocks = (long) state.distance;
                    state.distance -= blocks;
                    plugin.manager().handle(player, TriggerType.DISTANCE, "any", blocks);
                }
            }
        }
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ() || from.getWorld() != to.getWorld()) {
            checkPosition(player, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkPosition(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        checkPosition(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    /**
     * LOCATION and REGION_ENTER checks. Progress is only added when the player enters the area.
     */
    public void checkPosition(Player player, Location to) {
        if (to == null) {
            return;
        }
        State state = state(player);
        if (plugin.achievements().hasTrigger(TriggerType.LOCATION)) {
            for (Achievement a : plugin.achievements().byTrigger(TriggerType.LOCATION)) {
                boolean inside = a.trigger().location() != null && a.trigger().location().contains(to);
                if (inside && state.insideLocations.add(a.id())) {
                    plugin.manager().handleSingle(player, a, 1);
                } else if (!inside) {
                    state.insideLocations.remove(a.id());
                }
            }
        }
        if (plugin.hooks().worldGuard() != null && plugin.achievements().hasTrigger(TriggerType.REGION_ENTER)) {
            Set<String> now = plugin.hooks().worldGuard().regionsAt(to);
            Set<String> entered = new HashSet<>(now);
            entered.removeAll(state.regions);
            state.regions = now;
            if (!entered.isEmpty()) {
                plugin.manager().handle(player, TriggerType.REGION_ENTER, entered, 1);
            }
        }
    }
}
