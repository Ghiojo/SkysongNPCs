package org.skysongdev.skysongnpcs.npc;

import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.UUID;

/**
 * What the plugin asks Citizens about NPCs. {@link org.skysongdev.skysongnpcs.citizens.CitizensNpcLookup}
 * is the real one; tests use their own, since Citizens is not on the test classpath.
 */
public interface NpcLookup {

    /** Whether Citizens' main registry has this NPC. */
    boolean exists(UUID npc);

    /**
     * The NPC a command is about: the one the player looks at, or else the one they selected
     * with Citizens' {@code /npc select}.
     */
    Optional<UUID> target(CommandSender sender);

    /** The NPC's name as Citizens shows it, without colours, or its UUID when Citizens has no such NPC. */
    String name(UUID npc);
}
