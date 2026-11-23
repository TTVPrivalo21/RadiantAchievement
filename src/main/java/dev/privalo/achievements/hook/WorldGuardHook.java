package dev.privalo.achievements.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * WorldGuard region lookups for REGION_ENTER achievements.
 */
public final class WorldGuardHook {

    /** Lowercase ids of every region containing the location. */
    public Set<String> regionsAt(Location location) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
        Set<String> ids = new HashSet<>();
        for (ProtectedRegion region : set.getRegions()) {
            ids.add(region.getId().toLowerCase(Locale.ROOT));
        }
        return ids;
    }
}
