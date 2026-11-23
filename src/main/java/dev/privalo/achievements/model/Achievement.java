package dev.privalo.achievements.model;

import dev.privalo.achievements.reward.Reward;

import java.util.List;

/**
 * An achievement loaded from achievements.yml (or registered through the API).
 *
 * @param id               unique lowercase id
 * @param name             MiniMessage name
 * @param description      MiniMessage description
 * @param designId         design id from designs.yml
 * @param category         free category name
 * @param secret           hidden in the GUI until unlocked
 * @param order            GUI order
 * @param requires         achievement ids that must be unlocked first
 * @param icon             icon override (null = design icon)
 * @param trigger          how it is obtained
 * @param announceOverride chat override merged on top of the design ({@link AnnounceSpec#EMPTY} = none)
 * @param soundOverride    sounds that replace the design sounds (empty = design sounds)
 * @param rewards          rewards given on unlock
 */
public record Achievement(
        String id,
        String name,
        String description,
        String designId,
        String category,
        boolean secret,
        int order,
        List<String> requires,
        IconSpec icon,
        Trigger trigger,
        AnnounceSpec announceOverride,
        List<SoundSpec> soundOverride,
        List<Reward> rewards) {

    public long target() {
        return trigger.target();
    }

    public boolean isCumulative() {
        return trigger.target() > 1;
    }
}
