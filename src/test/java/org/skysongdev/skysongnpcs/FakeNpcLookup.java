package org.skysongdev.skysongnpcs;

import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.npc.NpcLookup;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Citizens for tests: the NPCs that exist, and which one every sender targets. */
public final class FakeNpcLookup implements NpcLookup {
    private final Map<UUID, String> npcs = new HashMap<>();
    private UUID target;

    /** Adds an NPC called {@code name} and returns its UUID. */
    public UUID add(String name) {
        UUID npc = UUID.randomUUID();
        npcs.put(npc, name);
        return npc;
    }

    /** Every sender targets this NPC from now on; null for none. */
    public void target(UUID npc) {
        target = npc;
    }

    @Override
    public boolean exists(UUID npc) {
        return npcs.containsKey(npc);
    }

    @Override
    public Optional<UUID> target(CommandSender sender) {
        return Optional.ofNullable(target);
    }

    @Override
    public String name(UUID npc) {
        return npcs.getOrDefault(npc, npc.toString());
    }
}
