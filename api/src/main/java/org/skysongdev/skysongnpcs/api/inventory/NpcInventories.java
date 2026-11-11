package org.skysongdev.skysongnpcs.api.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A managed NPC's inventory and equipment, as Citizens keeps them: open them for a player, or
 * read what they hold.
 *
 * <p>Opening checks the viewer's access the same way {@code /snpc inventory} and
 * {@code /snpc equip} do. A player with {@code inventory-edit} (or {@code equipment-edit}) gets
 * Citizens' own editor, which needs the NPC to be placed in the world; a player with only the
 * {@code -view} action gets a read-only copy, which works whether the NPC is placed or not.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.8.0
 */
@ApiStatus.NonExtendable
public interface NpcInventories {

    /** What opening an NPC's inventory or equipment did. */
    enum Opened {
        /** Citizens' editor is open: the viewer can change the items. */
        EDITOR,
        /** A read-only copy is open: the viewer can look but not take or add anything. */
        VIEW,
        /** The viewer may neither change nor see them. Nothing opened. */
        NOT_ALLOWED,
        /** The viewer may change them, but the NPC is not placed in the world. Nothing opened. */
        NOT_PLACED,
        /** The NPC is not a managed NPC, or Citizens no longer has it. Nothing opened. */
        NOT_MANAGED
    }

    /**
     * Opens the NPC's inventory for a player: Citizens' editor when they may change it, else a
     * read-only copy when they may see it.
     *
     * @param viewer   the player
     * @param npc      the NPC's UUID
     * @param viewOnly true to open the read-only copy even for a player who could edit
     * @return what was opened, or why nothing was
     */
    @NotNull Opened openInventory(@NotNull Player viewer, @NotNull UUID npc, boolean viewOnly);

    /**
     * Opens the NPC's equipment for a player: Citizens' equipment editor when they may change
     * it, else a read-only copy when they may see it.
     *
     * @param viewer   the player
     * @param npc      the NPC's UUID
     * @param viewOnly true to open the read-only copy even for a player who could edit
     * @return what was opened, or why nothing was
     */
    @NotNull Opened openEquipment(@NotNull Player viewer, @NotNull UUID npc, boolean viewOnly);

    /**
     * Copies of the items in the NPC's inventory, slot by slot; empty slots are
     * {@link ItemStack#empty()}.
     *
     * @param npc the NPC's UUID
     * @return the items, empty when the NPC is not managed or Citizens no longer has it
     */
    @NotNull Optional<List<ItemStack>> inventory(@NotNull UUID npc);

    /**
     * Copies of the items the NPC wears and holds, by slot; empty slots are left out.
     *
     * @param npc the NPC's UUID
     * @return the items, empty when the NPC is not managed or Citizens no longer has it
     */
    @NotNull Optional<Map<EquipmentSlot, ItemStack>> equipment(@NotNull UUID npc);
}
