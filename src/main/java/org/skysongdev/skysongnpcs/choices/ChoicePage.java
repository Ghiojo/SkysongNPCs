package org.skysongdev.skysongnpcs.choices;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.UUID;

/**
 * One kind of pending choice in the choice menu (the Assign points button and
 * {@code /snpc choose}), such as stat points. Pages are listed in the order they were added
 * to the {@link ChoiceMenu}.
 */
public interface ChoicePage {
    /** A short id: lowercase letters, digits and -. */
    String id();

    /** How many choices of this kind the NPC still has to make. */
    int pending(UUID npc);

    /** Its icon in the choice menu, usually showing {@link #pending}. */
    ItemStack icon(NpcRecord npc);

    /** Opens the page; {@code back} returns to the choice menu. */
    void open(Player player, NpcRecord npc, Runnable back);

    /**
     * Gives back the choices of this kind the NPC made, for a respec: the ones paid for by
     * {@code source}'s grants, or every one when {@code source} is null.
     *
     * @return how many were given back
     */
    default int respec(UUID npc, @Nullable String source) {
        return 0;
    }
}
