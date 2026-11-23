package dev.privalo.achievements.api.event;

import dev.privalo.achievements.model.Achievement;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired (main thread) right before an achievement is unlocked for an online player.
 * Cancelling it keeps the achievement locked.
 */
public class AchievementUnlockEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Why the achievement is being unlocked. */
    public enum Cause {
        /** Trigger progress reached the target. */
        PROGRESS,
        /** /achievements give. */
        COMMAND,
        /** {@code AchievementAPI#unlock}. */
        API
    }

    private final Achievement achievement;
    private final Cause cause;
    private boolean cancelled;
    private boolean giveRewards = true;
    private boolean notify = true;
    private boolean announce = true;

    public AchievementUnlockEvent(@NotNull Player player, @NotNull Achievement achievement, @NotNull Cause cause) {
        super(player);
        this.achievement = achievement;
        this.cause = cause;
    }

    public @NotNull Achievement getAchievement() {
        return achievement;
    }

    public @NotNull Cause getCause() {
        return cause;
    }

    public boolean isGiveRewards() {
        return giveRewards;
    }

    public void setGiveRewards(boolean giveRewards) {
        this.giveRewards = giveRewards;
    }

    /** Whether the on-screen notification and sound are shown. */
    public boolean isNotify() {
        return notify;
    }

    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    /** Whether the chat announcement is sent. */
    public boolean isAnnounce() {
        return announce;
    }

    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
