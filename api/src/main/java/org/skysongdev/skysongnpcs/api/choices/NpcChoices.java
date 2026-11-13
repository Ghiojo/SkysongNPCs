package org.skysongdev.skysongnpcs.api.choices;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * An NPC's pending choices and its first setup.
 *
 * <p>Stat points: 1 point = +1 base on an eligible stat, never above the stat's cap. They come
 * in grants, each with a source, so a plugin can take back the unspent points it gave without
 * touching anyone else's. Spent points stay spent until staff respec the NPC.
 *
 * <p>A new NPC from a template starts in {@link org.skysongdev.skysongnpcs.api.npc.NpcState#SETUP}:
 * whoever has the {@code choose} action picks its ancestry, then spends its points, from the
 * menu. When every step is done it becomes PLACED and
 * {@link org.skysongdev.skysongnpcs.api.event.NpcSetupCompleteEvent} fires.
 *
 * <p>Main thread only. Nothing here checks permissions: a plugin acting for a player checks
 * that player's access itself. SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.14.0
 */
@ApiStatus.NonExtendable
public interface NpcChoices {

    /**
     * Gives an NPC stat points.
     *
     * @param npc      the NPC's Citizens UUID
     * @param source   who gives them, such as {@code economy:smithy}; at most 64 characters
     * @param amount   how many, 1 or more
     * @param eligible the stats they may go on, by SkysongStats stat id or alias; empty for
     *                 the NPC's default eligible stats
     * @return the grant
     * @throws IllegalArgumentException when the NPC is not managed, the amount is below 1,
     *                                  the source is blank or too long, or a stat does not exist
     */
    @NotNull PointGrant grantPoints(@NotNull UUID npc, @NotNull String source, int amount,
                                    @NotNull Collection<String> eligible);

    /**
     * Takes back unspent points a source gave, newest grant first. Spent points stay.
     *
     * @param max how many at most
     * @return how many were taken
     */
    int takeUnspentPoints(@NotNull UUID npc, @NotNull String source, int max);

    /** The NPC's point grants, oldest first; empty for an unmanaged NPC. */
    @NotNull List<PointGrant> pointGrants(@NotNull UUID npc);

    /** Every unspent point the NPC has. */
    int unspentPoints(@NotNull UUID npc);

    /** The unspent points one source gave the NPC. */
    int unspentPoints(@NotNull UUID npc, @NotNull String source);

    /**
     * Spends points on a stat, as if a player chose it, and fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent}.
     *
     * @param stat   a SkysongStats stat id or alias
     * @param amount 1 or more
     */
    @NotNull SpendResult spendPoints(@NotNull UUID npc, @NotNull String stat, int amount);

    /**
     * Gives back every spent choice: the stats lose what points added, and the points (and
     * other plugins' choices, such as proficiency picks) are unspent again. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcRespecEvent} (since 0.21.0).
     *
     * @return how many choices were given back; 0 when the NPC is not managed
     */
    int respec(@NotNull UUID npc);

    /**
     * Gives back only the choices one source's grants paid for: the stat points spent from
     * that source's point grants and the proficiencies picked with its pick grants. Every
     * other source's choices stay as they are. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcRespecEvent} with that source.
     *
     * <p>Grants and their unspent points and picks are not touched: take those back with
     * {@link #takeUnspentPoints} and {@link #takeUnspentPicks} after the respec.
     *
     * @param npc    the NPC's Citizens UUID
     * @param source the source, exactly as it was granted with
     * @return how many choices were given back; 0 when the NPC is not managed or that source
     *         paid for none
     * @since 0.21.0
     */
    int respec(@NotNull UUID npc, @NotNull String source);

    /** Whether the NPC is waiting for its first setup. */
    boolean inSetup(@NotNull UUID npc);

    /**
     * Opens the NPC's setup for a player when it is in setup, otherwise its choice menu.
     *
     * @return false when the NPC is not managed
     */
    boolean open(@NotNull Player player, @NotNull UUID npc);

    // M15: proficiency picks

    /**
     * Gives an NPC proficiency picks: it may raise {@code count} proficiencies of a skill to a
     * tier. Picks from the same source, skill and tier are added to the grant it already has.
     *
     * @param npc    the NPC's Citizens UUID
     * @param source who gives them, such as {@code economy:smithy}; at most 64 characters, no spaces
     * @param skill  a SkysongStats skill id or alias
     * @param tier   a SkysongStats tier id
     * @param count  how many, 1 or more
     * @return the grant, new or grown
     * @throws IllegalArgumentException when the NPC is not managed, the count is below 1, the
     *                                  source is not valid, or the skill or tier does not exist
     * @since 0.15.0
     */
    @NotNull ProficiencyPickGrant grantPicks(@NotNull UUID npc, @NotNull String source, @NotNull String skill,
                                            @NotNull String tier, int count);

    /**
     * {@link #grantPicks(UUID, String, String, String, int)} whose picks may also promote: in
     * the picks menu (or with {@link #promote}), {@code promotionCost} of the grant's picks
     * raise a proficiency it picked one tier further, up to {@code promoteTo}. With tier
     * {@code proficient}, promoteTo {@code adept} and cost 1, adept costs two picks spent on
     * the same proficiency. A respec gives promotions back with the picks, and the grant keeps
     * its promotion, so it can promote again afterwards.
     *
     * <p>Picks are added to an existing grant only when its source, skill, tier and promotion
     * are all the same.
     *
     * @param promoteTo     a SkysongStats tier id above {@code tier}
     * @param promotionCost picks per promotion, 1 or more
     * @throws IllegalArgumentException also when {@code promoteTo} does not exist or is not
     *                                  above {@code tier}, or the cost is below 1
     * @since 0.22.0
     */
    @NotNull ProficiencyPickGrant grantPicks(@NotNull UUID npc, @NotNull String source, @NotNull String skill,
                                            @NotNull String tier, int count, @NotNull String promoteTo,
                                            int promotionCost);

    /**
     * Promotes a proficiency as if a player chose it: one tier up, paid with the NPC's oldest
     * grant of its skill that picked it and can promote it. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent} with kind
     * {@link org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent#PROFICIENCY_PICKS}.
     *
     * @param proficiency a SkysongStats proficiency id or alias
     * @return {@link PickResult#DONE} when saved; {@link PickResult#TOO_MANY} when the grant has
     *         fewer picks left than a promotion costs; {@link PickResult#NOT_AN_OPTION} when it
     *         was not picked with a grant that promotes, or is at the promotion's tier already;
     *         {@link PickResult#WRONG_SKILL} when no grant of its skill promotes
     * @since 0.22.0
     */
    @NotNull PickResult promote(@NotNull UUID npc, @NotNull String proficiency);

    /**
     * Takes back unspent picks a source gave, newest grant first. Spent picks stay.
     *
     * @param max how many at most
     * @return how many were taken
     * @since 0.15.0
     */
    int takeUnspentPicks(@NotNull UUID npc, @NotNull String source, int max);

    /**
     * The NPC's proficiency pick grants, oldest first; empty for an unmanaged NPC.
     *
     * @since 0.15.0
     */
    @NotNull List<ProficiencyPickGrant> pickGrants(@NotNull UUID npc);

    /**
     * Every unspent proficiency pick the NPC has.
     *
     * @since 0.15.0
     */
    int unspentPicks(@NotNull UUID npc);

    /**
     * The unspent proficiency picks one source gave the NPC.
     *
     * @since 0.15.0
     */
    int unspentPicks(@NotNull UUID npc, @NotNull String source);

    /**
     * Spends one pick on a proficiency, as if a player chose it, from the NPC's oldest grant of
     * that proficiency's skill that can take it, and fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent} with kind
     * {@link org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent#PROFICIENCY_PICKS}.
     *
     * @param proficiency a SkysongStats proficiency id or alias
     * @since 0.15.0
     */
    @NotNull PickResult spendPick(@NotNull UUID npc, @NotNull String proficiency);
}
