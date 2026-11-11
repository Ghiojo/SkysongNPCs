package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.npc.NpcLookup;

import java.util.Optional;
import java.util.UUID;

/** Answers {@link NpcLookup} from Citizens' main NPC registry. */
public final class CitizensNpcLookup implements NpcLookup {
    /** How far away, in blocks, a player can look at an NPC to target it. */
    static final int TARGET_RANGE = 6;

    @Override
    public boolean exists(UUID npc) {
        return registry().getByUniqueId(npc) != null;
    }

    @Override
    public Optional<UUID> target(CommandSender sender) {
        if (sender instanceof Player player) {
            Entity looked = player.getTargetEntity(TARGET_RANGE);
            NPC npc = looked == null ? null : registry().getNPC(looked);
            if (npc != null) {
                return Optional.of(npc.getUniqueId());
            }
        }
        NPC selected = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        // Only NPCs of the main registry can be managed; other plugins' registries are left alone.
        if (selected != null && registry().getByUniqueId(selected.getUniqueId()) != null) {
            return Optional.of(selected.getUniqueId());
        }
        return Optional.empty();
    }

    @Override
    public String name(UUID npc) {
        NPC found = registry().getByUniqueId(npc);
        return found == null ? npc.toString() : found.getName();
    }

    private static NPCRegistry registry() {
        return CitizensAPI.getNPCRegistry();
    }
}
