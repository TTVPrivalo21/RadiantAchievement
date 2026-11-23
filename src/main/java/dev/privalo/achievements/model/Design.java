package dev.privalo.achievements.model;

import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/**
 * Visual design of an achievement notification (designs.yml).
 *
 * @param bossBarCard glyph drawn behind the boss bar title (compact card), empty = plain title
 * @param bossBarIcon glyph drawn inside the card on the left, empty = none
 * @param bossBarHide make the vanilla bar invisible (uses the transparent colour of the pack)
 */
public record Design(
        String id,
        DisplayStyle style,
        int durationTicks,
        String frame,
        String title,
        String subtitle,
        String description,
        IconSpec icon,
        BossBar.Color bossBarColor,
        BossBar.Overlay bossBarOverlay,
        boolean bossBarAnimate,
        String bossBarCard,
        String bossBarIcon,
        boolean bossBarHide,
        List<SoundSpec> sounds,
        AnnounceSpec announce) {

    public static Design fallback(String id) {
        return new Design(id, DisplayStyle.TITLE, 60, "task",
                "<gold><b><achievement_name></b>", "<gray>¡Logro desbloqueado!", "<gray><achievement_description>",
                IconSpec.DEFAULT, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS, true, "", "", false,
                List.of(new SoundSpec("minecraft:ui.toast.challenge_complete", 1f, 1f, 0)), AnnounceSpec.EMPTY);
    }
}
