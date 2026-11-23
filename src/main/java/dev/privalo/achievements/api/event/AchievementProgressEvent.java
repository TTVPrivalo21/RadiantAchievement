package dev.privalo.achievements.api.event;

import dev.privalo.achievements.model.Achievement;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired (main thread) before the progress of a locked achievement changes.
 * The new value can be modified; cancelling keeps the previous progress.
 */
public class AchievementProgressEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Achievement achievement;
    private final long previousProgress;
    private long newProgress;
    private boolean cancelled;

    public AchievementProgressEvent(@NotNull Player player, @NotNull Achievement achievement, long previousProgress,
                                    long newProgress) {
        super(player);
        this.achievement = achievement;
        this.previousProgress = previousProgress;
        this.newProgress = newProgress;
    }

    public @NotNull Achievement getAchievement() {
        return achievement;
    }

    public long getPreviousProgress() {
        return previousProgress;
    }

    public long getNewProgress() {
        return newProgress;
    }

    /** Changes the resulting progress (clamped to 0..target). */
    public void setNewProgress(long newProgress) {
        this.newProgress = Math.max(0, newProgress);
    }

    public long getTarget() {
        return achievement.target();
    }

    /** @return true when this change will complete the achievement. */
    public boolean willComplete() {
        return newProgress >= achievement.target();
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
