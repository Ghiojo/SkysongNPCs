package org.skysongdev.skysongnpcs.api.ability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Managed NPCs' abilities. abilities.yml defines each one: a one-word name, a description, an
 * icon, and its effects on the NPC's stat sheet (stat modifiers, extra stat points, proficiency
 * picks and fixed proficiency tiers). Granting an ability applies its effects; revoking it
 * takes them away, except the stat points and picks the NPC has already spent.
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
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @return false when the NPC had the ability already, and nothing changed
     * @throws IllegalArgumentException when the NPC is not managed or abilities.yml has no
     *                                  such ability
     */
    boolean grant(@NotNull UUID npc, @NotNull String ability);

    /**
     * Takes an ability away from an NPC with its effects. Stat points and proficiency picks it
     * gave are taken back only while unspent; spent ones stay.
     *
     * @param npc     the NPC's Citizens UUID
     * @param ability the ability's id
     * @return false when the NPC did not have the ability
     */
    boolean revoke(@NotNull UUID npc, @NotNull String ability);

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
