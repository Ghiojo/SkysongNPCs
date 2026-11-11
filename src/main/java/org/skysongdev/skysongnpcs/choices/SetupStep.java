package org.skysongdev.skysongnpcs.choices;

import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

/**
 * One step of an NPC's first setup, such as picking its ancestry or spending its points.
 * Steps run in the order they were added to the {@link SetupFlow}; a step tells the flow
 * when it is done with {@link SetupFlow#stepDone}.
 */
public interface SetupStep {
    /** A short id, saved with the NPC when the step is done: lowercase letters, digits and -. */
    String id();

    /** Whether this NPC has anything to do in this step; a step that does not apply is skipped. */
    boolean applies(NpcRecord npc);

    /** Shows the step to the player. */
    void open(Player player, NpcRecord npc, SetupFlow flow);
}
