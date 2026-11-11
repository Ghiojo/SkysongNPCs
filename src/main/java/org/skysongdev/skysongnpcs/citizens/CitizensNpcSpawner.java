package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.items.NpcSpawner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link NpcSpawner} over Citizens' main NPC registry. */
public final class CitizensNpcSpawner implements NpcSpawner {

    @Override
    public Optional<UUID> spawn(EntityType type, String name, @Nullable String skin, Location location) {
        NPC npc = registry().createNPC(type, name);
        if (skin != null && type == EntityType.PLAYER) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(skin);
        }
        if (!npc.spawn(location)) {
            npc.destroy();
            return Optional.empty();
        }
        return Optional.of(npc.getUniqueId());
    }

    @Override
    public void destroy(UUID npc) {
        NPC found = registry().getByUniqueId(npc);
        if (found != null) {
            found.destroy();
        }
    }

    @Override
    public List<UUID> spawnedIn(Chunk chunk) {
        List<UUID> found = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            NPC npc = registry().getNPC(entity);
            if (npc != null) {
                found.add(npc.getUniqueId());
            }
        }
        return found;
    }

    private static NPCRegistry registry() {
        return CitizensAPI.getNPCRegistry();
    }
}
