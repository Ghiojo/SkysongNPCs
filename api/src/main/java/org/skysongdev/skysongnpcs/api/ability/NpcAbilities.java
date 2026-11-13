package org.skysongdev.skysongnpcs.api.ability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Managed NPCs' abilities. abilities.yml defines each one: a one-word name, a description, an
 * icon, and its effects on the NPC's stat sheet (stat modifiers, extra stat points, proficiency
 * picks and fixed proficiency tiers). Granting an ability applies its effects; revoking it
 * takes them away, except the stat points and picks the NPC has already spent.
 *
 * <p>Every grant has a source, a short text naming who gave it, as stat point and pick grants
 * do: {@value #STAFF} for staff and for grants made before 0.20.0, {@code template} and
 * {@code blueprint} for a new NPC's starting abilities, and whatever a plugin picks for its own
 * (Economy might use {@code economy:module:<id>}). An NPC has an ability while at least one
 * source holds it, with its effects applied once, so two givers of the same ability never undo
 * each other. Revoking for one source leaves the others' holds alone.
 *
 * <p>When abilities.yml changes and is reloaded, every NPC with an ability is brought in line
 * with the file, and an ability removed from the file is revoked from every NPC.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.16.0
 */
@ApiStatus.NonExtendable
public interface NpcAbilities {

    /**
     * The source of staff's grants ({@code /snpc abilities grant}), of
     * {@link #grant(UUID, String)}, and of every grant made before sources existed.
     *
     * @since 0.20.0
     */
    String STAFF = "staff";

    /**
     * Every ability abilities.yml defines, in the file's order.
     *
     * @return an unmodifiable list
     */
    @NotNull List<NpcAbility> defined();

    /**
     * One ability from abilities.yml.
     *
     * @param ability its id
     * @return the ability, empty when abilities.yml has none with that id
     */
    @NotNull Optional<NpcAbility> find(@NotNull String ability);

    /**
     * An NPC's abilities, in abilities.yml order.
     *
     * @param npc the NPC's Citizens UUID
     * @return an unmodifiable list, empty when the NPC has none or is not managed
     */
    @NotNull List<NpcAbility> of(@NotNull UUID npc);

    /**
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @return whether the NPC has the ability
     */
    boolean has(@NotNull UUID npc, @NotNull String ability);

    /**
     * Gives a managed NPC an ability and applies its effects. Modifiers and proficiency tiers
     * need a stat sheet: an NPC without one gets them when it has one and SkysongNPCs next
     * brings abilities in line (on start, on {@code /snpc reload}, or on the next grant or
     * revoke of any of its abilities).
     *
     * <p>Since 0.20.0 this is {@link #grant(UUID, String, String)} for {@value #STAFF}.
     *
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @return false when staff held the ability already, and nothing changed
     * @throws IllegalArgumentException when the NPC is not managed or abilities.yml has no
     *                                  such ability
     */
    boolean grant(@NotNull UUID npc, @NotNull String ability);

    /**
     * Gives a managed NPC an ability on behalf of {@code source}. When the NPC does not have it
     * yet, its effects are applied as {@link #grant(UUID, String)} describes and
     * {@link org.skysongdev.skysongnpcs.api.event.NpcAbilityChangeEvent} fires. When another
     * source holds it already, nothing changes on the NPC: the source is only added to its
     * holders, and no event fires.
     *
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @param source  who gives it, such as {@code economy:module:trade}
     * @return false when this source held the ability already, and nothing changed
     * @throws IllegalArgumentException when the NPC is not managed, abilities.yml has no such
     *                                  ability, or the source is blank
     * @since 0.20.0
     */
    boolean grant(@NotNull UUID npc, @NotNull String ability, @NotNull String source);

    /**
     * Takes {@code source}'s hold on an ability away. When no other source holds it, the NPC
     * loses the ability with its effects, as {@link #revoke(UUID, String)} describes, and
     * {@link org.skysongdev.skysongnpcs.api.event.NpcAbilityChangeEvent} fires. Otherwise the
     * NPC keeps it unchanged.
     *
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @param source  the source that gave it
     * @return false when this source did not hold the ability, and nothing changed
     * @since 0.20.0
     */
    boolean revoke(@NotNull UUID npc, @NotNull String ability, @NotNull String source);

    /**
     * Who holds one of an NPC's abilities.
     *
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @return the sources, an unmodifiable set; empty when the NPC does not have the ability
     * @since 0.20.0
     */
    @NotNull Set<String> sources(@NotNull UUID npc, @NotNull String ability);

    /**
     * Takes an ability away from an NPC with its effects, whichever sources hold it. Stat
     * points and proficiency picks it gave are taken back only while unspent; spent ones stay.
     * A plugin that gave the ability should use {@link #revoke(UUID, String, String)} instead,
     * so it never takes away a grant it did not make.
     *
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @return false when the NPC did not have the ability
     */
    boolean revoke(@NotNull UUID npc, @NotNull String ability);

    /**
     * Gives an NPC a choice of abilities: it chooses {@code count} of {@code options}, in the
     * choice menu ({@code /snpc choose}), as a step of its first setup after the proficiency
     * picks, or through {@link #choose}. A chosen ability is granted under the source
     * {@code "choice:" + source}. A respec through
     * {@link org.skysongdev.skysongnpcs.api.choices.NpcChoices#respec(UUID, String)} revokes
     * the chosen abilities and makes the choice open again; so does anything else that takes
     * the ability away, such as abilities.yml dropping it. Options abilities.yml no longer
     * defines and abilities the NPC has already are not offered.
     *
     * <p>A choice from the same source with the same options grows by {@code count} instead.
     *
     * @param npc     the NPC's Citizens UUID
     * @param source  who gives it, such as {@code economy:module:trade}; 1 to 64 characters
     *                without spaces
     * @param options abilities.yml ids, each defined now; repeats are dropped
     * @param count   how many it lets the NPC choose, 1 or more
     * @return the choice, new or grown
     * @throws IllegalArgumentException when the NPC is not managed, there is no option, an
     *                                  option is not defined, the count is under 1 or the
     *                                  source is not valid
     * @since 0.23.0
     */
    @NotNull NpcAbilityChoice grantChoice(@NotNull UUID npc, @NotNull String source, @NotNull List<String> options,
                                          int count);

    /**
     * An NPC's ability choices, oldest first, made or not.
     *
     * @param npc the NPC's Citizens UUID
     * @return an unmodifiable list, empty when it has none or is not managed
     * @since 0.23.0
     */
    @NotNull List<NpcAbilityChoice> choices(@NotNull UUID npc);

    /**
     * Makes an ability choice: every ability is granted, or, when one cannot be chosen, none.
     *
     * @param choice    the choice's id, from {@link NpcAbilityChoice#id()}
     * @param abilities abilities.yml ids among its options
     * @return {@link AbilityChoiceResult#DONE} when they were granted, otherwise why not
     * @since 0.23.0
     */
    @NotNull AbilityChoiceResult choose(@NotNull UUID choice, @NotNull List<String> abilities);

    /**
     * Takes back the choices one source gave an NPC that are not made yet. Abilities already
     * chosen stay; respec first to give those back too.
     *
     * @param npc    the NPC's Citizens UUID
     * @param source the source that gave them
     * @return how many choices were taken back
     * @since 0.23.0
     */
    int takeUnspentChoices(@NotNull UUID npc, @NotNull String source);

    /**
     * Opens the NPC's abilities menu for a player, as the Abilities button does. It does not
     * check the player's access: the caller decides who may look.
     *
     * @param viewer the player
     * @param npc    the NPC's Citizens UUID
     * @return false when the NPC is not managed, and nothing opened
     */
    boolean open(@NotNull Player viewer, @NotNull UUID npc);
}
