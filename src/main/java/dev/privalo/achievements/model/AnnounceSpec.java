package dev.privalo.achievements.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Chat announcement settings. Every field is nullable so achievement overrides can be merged on top of a design.
 */
public record AnnounceSpec(AnnounceMode mode, AnnounceScope scope, String compact, List<String> designed) {

    public static final AnnounceSpec EMPTY = new AnnounceSpec(null, null, null, null);

    public static AnnounceSpec fromSection(ConfigurationSection s) {
        if (s == null) {
            return EMPTY;
        }
        AnnounceMode mode = s.contains("mode") ? AnnounceMode.parse(s.getString("mode"), null) : null;
        AnnounceScope scope = s.contains("scope") ? AnnounceScope.parse(s.getString("scope"), null) : null;
        String compact = s.getString("compact");
        List<String> designed = s.contains("designed") ? s.getStringList("designed") : null;
        return new AnnounceSpec(mode, scope, compact, designed);
    }

    /**
     * @return a copy where every null field of this spec is taken from {@code base}.
     */
    public AnnounceSpec over(AnnounceSpec base) {
        return new AnnounceSpec(
                mode != null ? mode : base.mode,
                scope != null ? scope : base.scope,
                compact != null ? compact : base.compact,
                designed != null ? designed : base.designed);
    }
}
