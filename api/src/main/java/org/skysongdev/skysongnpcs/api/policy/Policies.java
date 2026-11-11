package org.skysongdev.skysongnpcs.api.policy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Access policies: named sets of grants that many NPCs follow at once, such as one per
 * business whose new worker NPCs all join it. What an NPC allows is its policy's grants plus
 * its own ({@link org.skysongdev.skysongnpcs.api.access.Access#grants(UUID)}), and
 * {@link org.skysongdev.skysongnpcs.api.access.Access#may} checks both.
 *
 * <p>The API does not check who owns a policy: a plugin should only change its own, made with
 * {@link PolicyOwner#plugin}, as {@code /snpc policy} does for characters. Grant changes fire
 * {@link org.skysongdev.skysongnpcs.api.event.AccessPolicyChangeEvent}; NPCs joining or
 * leaving a policy fire {@link org.skysongdev.skysongnpcs.api.event.NpcPolicyChangeEvent}.
 * A deleted NPC leaves its policy; a copied one follows the same policy.
 *
 * <p>Must be called on the server's main thread, and throws {@link IllegalStateException}
 * otherwise. Methods that take a policy id throw {@link IllegalArgumentException} when there is
 * no such policy, and methods that take an NPC when it is not managed.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface Policies {

    /**
     * @param id the policy's id, in any case
     * @return the policy, empty when there is none
     */
    @NotNull Optional<AccessPolicy> get(@NotNull String id);

    /**
     * @return every policy, unmodifiable, in the order they were made
     */
    @NotNull Collection<AccessPolicy> all();

    /**
     * @param owner a character or plugin
     * @return its policies, unmodifiable, in the order they were made
     */
    @NotNull List<AccessPolicy> ownedBy(@NotNull PolicyOwner owner);

    /**
     * Makes an empty policy.
     *
     * @param id    its id; see {@link AccessPolicy#id()}
     * @param owner who it belongs to
     * @return the new policy
     * @throws IllegalArgumentException when the id is not valid
     * @throws IllegalStateException    when a policy with that id exists
     */
    @NotNull AccessPolicy create(@NotNull String id, @NotNull PolicyOwner owner);

    /**
     * Deletes a policy. Its NPCs stop following it and keep only their own grants.
     *
     * @param id the policy's id
     * @return true when there was one
     */
    boolean delete(@NotNull String id);

    /**
     * Lets a group do more with every NPC that follows the policy: the actions are added to
     * the group's grant, which is made when it does not exist yet.
     *
     * @param id      the policy's id
     * @param group   who it lets in
     * @param actions what to add; not empty
     * @return the policy as it is now
     */
    @NotNull AccessPolicy grant(@NotNull String id, @NotNull AccessGroup group, @NotNull Set<NpcAction> actions);

    /**
     * Sets exactly what one group's grant in the policy allows, replacing what it allowed before.
     *
     * @param id      the policy's id
     * @param group   whose grant
     * @param actions what it allows from now on; empty deletes the grant
     * @return the policy as it is now
     */
    @NotNull AccessPolicy setGrant(@NotNull String id, @NotNull AccessGroup group, @NotNull Set<NpcAction> actions);

    /**
     * Deletes one group's grant from the policy.
     *
     * @param id    the policy's id
     * @param group whose grant
     * @return true when there was one
     */
    boolean revoke(@NotNull String id, @NotNull AccessGroup group);

    /**
     * @param npc the NPC's Citizens UUID
     * @return the policy the NPC follows, empty when none
     */
    @NotNull Optional<AccessPolicy> policyOf(@NotNull UUID npc);

    /**
     * Makes an NPC follow a policy, instead of the one it followed before, if any.
     *
     * @param npc the NPC's Citizens UUID
     * @param id  the policy's id
     */
    void assign(@NotNull UUID npc, @NotNull String id);

    /**
     * Makes an NPC follow no policy.
     *
     * @param npc the NPC's Citizens UUID
     * @return true when it followed one
     */
    boolean unassign(@NotNull UUID npc);

    /**
     * @param id the policy's id
     * @return the Citizens UUIDs of the NPCs following it, unmodifiable
     */
    @NotNull Set<UUID> members(@NotNull String id);
}
