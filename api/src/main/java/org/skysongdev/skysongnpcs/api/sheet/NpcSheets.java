package org.skysongdev.skysongnpcs.api.sheet;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongstats.api.CharacterId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Managed NPCs' stat sheets. A sheet is a SkysongStats plugin character owned by SkysongNPCs,
 * {@code CharacterId(npcUuid, "npc")}, so everything else about it (stats, modifiers,
 * proficiencies, injuries, conditions) goes through the SkysongStats API with the id from
 * {@link #character(UUID)}. SkysongNPCs keeps the items an NPC carries counted toward its
 * item-tag stats (Arcrot...) by itself.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface NpcSheets {
    /** The profile part of every NPC's SkysongStats character id. */
    String PROFILE = "npc";

    /**
     * @param npc the NPC's Citizens UUID
     * @return whether the NPC has a stat sheet
     */
    boolean hasSheet(@NotNull UUID npc);

    /**
     * The NPC's SkysongStats character, for use with the SkysongStats API.
     *
     * @param npc the NPC's Citizens UUID
     * @return its character id, empty when the NPC has no sheet
     */
    @NotNull Optional<CharacterId> character(@NotNull UUID npc);

    /**
     * Gives a managed NPC a stat sheet.
     *
     * @param npc   the NPC's Citizens UUID
     * @param stats starting base values by SkysongStats stat id or alias; the stats left out
     *              start at their stats.yml start value
     * @return the new sheet's character id
     * @throws IllegalArgumentException when the NPC is not managed or a stat does not exist
     * @throws IllegalStateException    when the NPC has a sheet already
     */
    @NotNull CharacterId create(@NotNull UUID npc, @NotNull Map<String, Integer> stats);

    /**
     * Gives a managed NPC the stat sheet a template describes.
     *
     * @param npc      the NPC's Citizens UUID
     * @param template a template's id, the name of its file in {@code templates/}
     * @return the new sheet's character id
     * @throws IllegalArgumentException when there is no such template, it gives no sheet, or
     *                                  as {@link #create(UUID, Map)}
     * @throws IllegalStateException    when the NPC has a sheet already
     */
    @NotNull CharacterId createFromTemplate(@NotNull UUID npc, @NotNull String template);

    /**
     * Counts the items the NPC carries toward its item-tag stats now. SkysongNPCs also does
     * it on its own every few seconds; call this after changing an NPC's inventory or
     * equipment when the stats must be right straight away.
     *
     * @param npc the NPC's Citizens UUID; nothing happens when it has no sheet
     */
    void recountItems(@NotNull UUID npc);
}
