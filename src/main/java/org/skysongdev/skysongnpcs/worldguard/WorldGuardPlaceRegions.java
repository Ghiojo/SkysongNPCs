package org.skysongdev.skysongnpcs.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.items.PlaceRegions;

import java.util.logging.Logger;

/**
 * The {@code npc-place} region flag, checked like building: outside any region placing is
 * allowed; inside one, region members may place, others only where {@code npc-place} is set to
 * allow, and WorldGuard's region bypass always may. Only touch this class when WorldGuard is
 * installed. The same rules SkysongWardrobe uses for its mannequin flags.
 */
public final class WorldGuardPlaceRegions implements PlaceRegions {
    public static final String FLAG_NAME = "npc-place";

    private static StateFlag flag;

    /** Registers the flag. WorldGuard only takes new flags while plugins load, so call it from onLoad. */
    public static void registerFlag(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag created = new StateFlag(FLAG_NAME, false);
            registry.register(created);
            flag = created;
        } catch (FlagConflictException taken) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag state) {
                flag = state;
            } else {
                logger.warning("Another plugin owns the WorldGuard flag " + FLAG_NAME
                        + ", so regions can only allow placing NPCs to their members.");
            }
        }
    }

    @Override
    public boolean allows(Player player, Location location) {
        com.sk89q.worldedit.util.Location at = BukkitAdapter.adapt(location);
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(at);
        if (regions.size() == 0) {
            return true;
        }
        LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, local.getWorld())) {
            return true;
        }
        return flag == null ? query.testBuild(at, local) : query.testBuild(at, local, flag);
    }
}
