package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.skysongdev.skysongnpcs.movement.NpcMover;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Moves NPCs through Citizens' teleport, its FollowTrait and its LookClose trait. */
public final class CitizensMover implements NpcMover {
    /**
     * Who each NPC was last told to follow. FollowTrait only finds the entity on its next run,
     * so until then this answers {@link #following(UUID)}.
     */
    private final Map<UUID, UUID> followed = new HashMap<>();

    @Override
    public Optional<Location> location(UUID npc) {
        NPC found = npc(npc);
        return found == null || !found.isSpawned() ? Optional.empty() : Optional.of(found.getStoredLocation());
    }

    @Override
    public boolean teleport(UUID npc, Location to) {
        NPC found = npc(npc);
        if (found == null || !found.isSpawned()) {
            return false;
        }
        found.teleport(to, PlayerTeleportEvent.TeleportCause.COMMAND);
        return true;
    }

    @Override
    public void follow(UUID npc, Entity target, double distance) {
        NPC found = npc(npc);
        if (found == null) {
            return;
        }
        FollowTrait follow = found.getOrAddTrait(FollowTrait.class);
        if (target != null) {
            follow.setFollowingMargin(distance);
            followed.put(npc, target.getUniqueId());
        } else {
            followed.remove(npc);
        }
        follow.follow(target);
    }

    @Override
    public Optional<UUID> following(UUID npc) {
        NPC found = npc(npc);
        if (found == null || !found.hasTrait(FollowTrait.class)) {
            return Optional.empty();
        }
        Entity entity = found.getOrAddTrait(FollowTrait.class).getFollowing();
        return entity != null ? Optional.of(entity.getUniqueId()) : Optional.ofNullable(followed.get(npc));
    }

    @Override
    public void look(UUID npc, boolean on, double range) {
        NPC found = npc(npc);
        if (found == null) {
            return;
        }
        LookClose look = found.getOrAddTrait(LookClose.class);
        if (on) {
            look.setRange(range);
        }
        look.lookClose(on);
    }

    @Override
    public boolean looking(UUID npc) {
        NPC found = npc(npc);
        return found != null && found.hasTrait(LookClose.class) && found.getOrAddTrait(LookClose.class).isEnabled();
    }

    private static NPC npc(UUID npc) {
        return CitizensAPI.getNPCRegistry().getByUniqueId(npc);
    }
}
