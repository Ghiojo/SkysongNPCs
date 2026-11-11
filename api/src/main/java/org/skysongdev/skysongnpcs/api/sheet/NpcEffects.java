package org.skysongdev.skysongnpcs.api.sheet;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.Injury;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Injuries, conditions and ancestry on managed NPCs' stat sheets: what {@code /snpc injury},
 * {@code /snpc condition} and {@code /snpc ancestry} do. Each call goes to SkysongStats'
 * own API on the NPC's character ({@link NpcSheets#character(UUID)}), so it behaves exactly
 * as it does for players: saved straight away, the stats follow, and SkysongStats' events
 * fire with {@code ChangeCause.API}. Injuries, conditions and ancestries are named by their
 * ids in SkysongStats' injuries.yml, conditions.yml and ancestries.yml, ignoring case.
 *
 * <p>Every method throws {@link IllegalArgumentException} when the NPC is not managed or has
 * no stat sheet, and {@link IllegalStateException} off the main thread. Nothing checks
 * permissions or a template's ancestry list; the caller decides who may do what.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.11.0
 */
@ApiStatus.NonExtendable
public interface NpcEffects {
    /**
     * @param npc the NPC's Citizens UUID
     * @return its current injuries, oldest first
     */
    @NotNull List<Injury> injuries(@NotNull UUID npc);

    /**
     * Gives the NPC an injury and teaches it, like {@code /injury award}.
     *
     * @return false when injuries.yml has no such injury
     */
    boolean awardInjury(@NotNull UUID npc, @NotNull String injury);

    /**
     * Takes an injury away, with its conditions and effects.
     *
     * @return false when the NPC did not have it
     */
    boolean removeInjury(@NotNull UUID npc, @NotNull String injury);

    /**
     * Teaches the NPC an injury without giving it.
     *
     * @return false when it knew it already, or there is no such injury
     */
    boolean discoverInjury(@NotNull UUID npc, @NotNull String injury);

    /**
     * @param npc the NPC's Citizens UUID
     * @return its current conditions: given directly, or by its injuries and conditions
     */
    @NotNull List<ActiveCondition> conditions(@NotNull UUID npc);

    /**
     * Gives the NPC a condition directly; giving it again changes the level.
     *
     * @param level 1 or more
     * @return the condition as the NPC has it now; empty when conditions.yml has no such condition
     * @throws IllegalArgumentException also when the level is under 1
     */
    @NotNull Optional<ActiveCondition> awardCondition(@NotNull UUID npc, @NotNull String condition, int level);

    /**
     * Takes back a direct award. A condition an injury gives stays until the injury goes.
     *
     * @return false when the NPC had no direct award of it
     */
    boolean removeCondition(@NotNull UUID npc, @NotNull String condition);

    /**
     * @param npc the NPC's Citizens UUID
     * @return its ancestry and what it gave; empty when it has none
     */
    @NotNull Optional<CharacterAncestry> ancestry(@NotNull UUID npc);

    /**
     * Gives the NPC an ancestry with its choices made, taking off what the old one gave.
     * Use {@link AncestryPicks#none()} for an ancestry without choices.
     *
     * @param ancestry the ancestry's id or name
     * @return false when ancestries.yml has no such ancestry
     * @throws IllegalArgumentException also when the picks don't fit the ancestry, naming
     *                                  every problem; nothing changes then
     */
    boolean setAncestry(@NotNull UUID npc, @NotNull String ancestry, @NotNull AncestryPicks picks);

    /**
     * Takes off everything the NPC's ancestry gave. For an animal or creature ("Other" in the
     * setup), an NPC simply has no ancestry.
     *
     * @return false when it had none
     */
    boolean removeAncestry(@NotNull UUID npc);
}
