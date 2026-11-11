package org.skysongdev.skysongnpcs.groups;

import org.skysongdev.skysongnpcs.access.AccessRegistry;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.policy.AccessPolicy;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;
import org.skysongdev.skysongnpcs.storage.GroupStore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Every player group ({@code group:<id>}), kept in memory by name and written through to the
 * {@link GroupStore}. Deleting a group revokes every grant naming it, NPCs' own and access
 * policies' alike, and a name some grant still names cannot be taken, so a new group never
 * inherits an old one's access.
 */
public final class PlayerGroupRegistry {
    private final GroupStore store;
    private final AccessRegistry grants;
    private final PolicyRegistry policies;
    private final Map<String, PlayerGroup> groups = new TreeMap<>();

    public PlayerGroupRegistry(GroupStore store, AccessRegistry grants, PolicyRegistry policies) {
        this.store = Objects.requireNonNull(store, "store");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    /** Reads every group from the store, replacing what is in memory. Called once on enable. */
    public void load() {
        groups.clear();
        store.loadAll().forEach(group -> groups.put(group.id(), group));
    }

    /** The name as groups are kept: lower case. */
    public static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public Optional<PlayerGroup> get(String id) {
        return Optional.ofNullable(groups.get(normalize(id)));
    }

    /** Every group, by name. */
    public List<PlayerGroup> all() {
        return List.copyOf(groups.values());
    }

    /** The groups one owner has, by name. */
    public List<PlayerGroup> ownedBy(NpcOwner owner) {
        return groups.values().stream().filter(group -> group.owner().equals(owner)).toList();
    }

    /** The groups any character of one player owns, by name. */
    public List<PlayerGroup> ownedByPlayer(UUID player) {
        return groups.values().stream()
                .filter(group -> group.owner() instanceof NpcOwner.CharacterOwner owner && owner.player().equals(player))
                .toList();
    }

    /** Whether a player, playing {@code profile} (null for none), is in the group. False when there is no such group. */
    public boolean contains(String id, UUID player, String profile) {
        PlayerGroup group = groups.get(normalize(id));
        return group != null && group.contains(player, profile);
    }

    /** Why a name cannot be used for a new group, or empty when it can. */
    public Optional<Refusal> refusal(String id) {
        String name = normalize(id);
        if (!PlayerGroup.isValidId(name)) {
            return Optional.of(Refusal.BAD_NAME);
        }
        AccessGroup reference = PlayerGroup.reference(name);
        if (groups.containsKey(name) || !grants.naming(reference).isEmpty() || !policiesNaming(reference).isEmpty()) {
            return Optional.of(Refusal.TAKEN);
        }
        return Optional.empty();
    }

    /**
     * @throws IllegalArgumentException when the name is not valid
     * @throws IllegalStateException    when it is taken, or a grant still names it
     */
    public PlayerGroup create(String id, NpcOwner owner) {
        Objects.requireNonNull(owner, "owner");
        Optional<Refusal> refusal = refusal(id);
        if (refusal.isPresent()) {
            if (refusal.get() == Refusal.BAD_NAME) {
                throw new IllegalArgumentException("Not a group name: " + id);
            }
            throw new IllegalStateException("The group name " + id + " is taken, or a grant still names it");
        }
        return put(new PlayerGroup(normalize(id), owner, Set.of()));
    }

    /**
     * Deletes the group and revokes every grant naming it.
     *
     * @return how many grants were revoked, empty when there was no such group
     */
    public Optional<Integer> delete(String id) {
        PlayerGroup removed = groups.remove(normalize(id));
        if (removed == null) {
            return Optional.empty();
        }
        store.delete(removed.id());
        int revoked = 0;
        AccessGroup reference = removed.reference();
        for (Map.Entry<UUID, List<AccessGrant>> npc : grants.naming(reference).entrySet()) {
            for (AccessGrant grant : npc.getValue()) {
                grants.set(npc.getKey(), grant.source(), reference, Set.of());
                revoked++;
            }
        }
        for (String policy : policiesNaming(reference)) {
            policies.setGrant(policy, reference, Set.of());
            revoked++;
        }
        return Optional.of(revoked);
    }

    /** The access policies with a grant naming a group. */
    private List<String> policiesNaming(AccessGroup group) {
        return policies.all().stream()
                .filter(policy -> policy.grants().stream().anyMatch(grant -> grant.group().equals(group)))
                .map(AccessPolicy::id)
                .toList();
    }

    /**
     * @return the group as it is now
     * @throws IllegalArgumentException when there is no such group
     */
    public PlayerGroup addMember(String id, GroupMember member) {
        Objects.requireNonNull(member, "member");
        PlayerGroup group = existing(id);
        if (group.members().contains(member)) {
            return group;
        }
        Set<GroupMember> members = new LinkedHashSet<>(group.members());
        members.add(member);
        return put(new PlayerGroup(group.id(), group.owner(), members));
    }

    /**
     * @return the group as it is now
     * @throws IllegalArgumentException when there is no such group
     */
    public PlayerGroup removeMember(String id, GroupMember member) {
        Objects.requireNonNull(member, "member");
        PlayerGroup group = existing(id);
        if (!group.members().contains(member)) {
            return group;
        }
        Set<GroupMember> members = new LinkedHashSet<>(group.members());
        members.remove(member);
        return put(new PlayerGroup(group.id(), group.owner(), members));
    }

    /**
     * @return the group as it is now
     * @throws IllegalArgumentException when there is no such group
     */
    public PlayerGroup setOwner(String id, NpcOwner owner) {
        Objects.requireNonNull(owner, "owner");
        PlayerGroup group = existing(id);
        if (group.owner().equals(owner)) {
            return group;
        }
        return put(new PlayerGroup(group.id(), owner, group.members()));
    }

    private PlayerGroup existing(String id) {
        PlayerGroup group = groups.get(normalize(Objects.requireNonNull(id, "id")));
        if (group == null) {
            throw new IllegalArgumentException("There is no group called " + id);
        }
        return group;
    }

    private PlayerGroup put(PlayerGroup group) {
        groups.put(group.id(), group);
        store.save(group);
        return group;
    }

    /** Why a name cannot be used for a new group. */
    public enum Refusal {
        /** Not 1 to 32 lower-case letters, digits, {@code -} and {@code _}. */
        BAD_NAME,
        /** Another group has it, or a grant still names it. */
        TAKEN
    }
}
