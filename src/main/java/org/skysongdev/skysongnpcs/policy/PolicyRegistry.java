package org.skysongdev.skysongnpcs.policy;

import org.skysongdev.skysongnpcs.access.PolicyGrants;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.AccessPolicy;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;
import org.skysongdev.skysongnpcs.storage.PolicyStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every access policy and which NPC follows which, kept in memory and written through to the
 * {@link PolicyStore}. It does not check that an NPC is managed or who may change a policy;
 * its callers do. {@link Changes} hears about every change but deletions and copies of NPCs.
 */
public final class PolicyRegistry implements PolicyGrants {
    private final PolicyStore store;
    private final Changes changes;
    private final Map<String, AccessPolicy> policies = new LinkedHashMap<>();
    private final Map<UUID, String> members = new HashMap<>();

    public PolicyRegistry(PolicyStore store, Changes changes) {
        this.store = Objects.requireNonNull(store, "store");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    /** Reads everything from the store, replacing what is in memory. Called once on enable. */
    public void load() {
        policies.clear();
        members.clear();
        PolicyStore.Snapshot snapshot = store.loadAll();
        for (PolicyStore.StoredPolicy stored : snapshot.policies()) {
            List<AccessGrant> grants = new ArrayList<>();
            stored.grants().forEach((group, actions) ->
                    grants.add(new AccessGrant(group, stored.owner().source(), actions)));
            policies.put(stored.id(), new AccessPolicy(stored.id(), stored.owner(), grants));
        }
        members.putAll(snapshot.members());
    }

    public Optional<AccessPolicy> get(String id) {
        return Optional.ofNullable(policies.get(AccessPolicy.normalizeId(id)));
    }

    /** Every policy, in the order they were made. */
    public Collection<AccessPolicy> all() {
        return List.copyOf(policies.values());
    }

    public List<AccessPolicy> ownedBy(PolicyOwner owner) {
        return policies.values().stream().filter(policy -> policy.owner().equals(owner)).toList();
    }

    /**
     * @throws IllegalArgumentException when the id is not valid
     * @throws IllegalStateException    when the id is taken
     */
    public AccessPolicy create(String id, PolicyOwner owner) {
        Objects.requireNonNull(owner, "owner");
        String key = AccessPolicy.normalizeId(id);
        if (policies.containsKey(key)) {
            throw new IllegalStateException("There is already a policy called " + key);
        }
        AccessPolicy policy = new AccessPolicy(key, owner, List.of());
        policies.put(key, policy);
        store.savePolicy(key, owner);
        return policy;
    }

    /** Deletes a policy; its NPCs follow none from now on. */
    public boolean delete(String id) {
        String key = AccessPolicy.normalizeId(id);
        if (policies.remove(key) == null) {
            return false;
        }
        List<UUID> left = members.entrySet().stream()
                .filter(entry -> entry.getValue().equals(key)).map(Map.Entry::getKey).toList();
        left.forEach(members::remove);
        store.deletePolicy(key);
        left.forEach(npc -> changes.membershipChanged(npc, key, null));
        return true;
    }

    /**
     * Sets what one group's grant in a policy allows: makes it, replaces its actions, or deletes
     * it when {@code actions} is empty. Nothing is saved or announced when it already allows
     * exactly that.
     *
     * @throws IllegalArgumentException when there is no such policy
     */
    public AccessPolicy setGrant(String id, AccessGroup group, Set<NpcAction> actions) {
        Objects.requireNonNull(group, "group");
        AccessPolicy policy = existing(id);
        Optional<AccessGrant> current = find(policy, group);
        Set<NpcAction> previous = current.map(AccessGrant::actions).orElse(Set.of());
        if (previous.equals(actions)) {
            return policy;
        }
        List<AccessGrant> grants = new ArrayList<>(policy.grants());
        if (actions.isEmpty()) {
            grants.remove(current.orElseThrow());
            store.deleteGrant(policy.id(), group);
        } else {
            AccessGrant changed = new AccessGrant(group, policy.owner().source(), actions);
            int at = current.map(grants::indexOf).orElse(-1);
            if (at < 0) {
                grants.add(changed);
            } else {
                // Keeps its place, so the list stays in the order grants were first made.
                grants.set(at, changed);
            }
            store.saveGrant(policy.id(), group, actions);
        }
        AccessPolicy result = new AccessPolicy(policy.id(), policy.owner(), grants);
        policies.put(policy.id(), result);
        changes.grantChanged(policy.id(), group, previous, actions);
        return result;
    }

    public Optional<AccessGrant> find(AccessPolicy policy, AccessGroup group) {
        return policy.grants().stream().filter(grant -> grant.group().equals(group)).findFirst();
    }

    public Optional<AccessPolicy> policyOf(UUID npc) {
        return Optional.ofNullable(members.get(npc)).map(policies::get);
    }

    /**
     * Makes an NPC follow a policy, instead of the one it followed before.
     *
     * @throws IllegalArgumentException when there is no such policy
     */
    public void assign(UUID npc, String id) {
        Objects.requireNonNull(npc, "npc");
        AccessPolicy policy = existing(id);
        String previous = members.put(npc, policy.id());
        if (policy.id().equals(previous)) {
            return;
        }
        store.setMember(npc, policy.id());
        changes.membershipChanged(npc, previous, policy.id());
    }

    /** Makes an NPC follow no policy. */
    public boolean unassign(UUID npc) {
        String previous = members.remove(npc);
        if (previous == null) {
            return false;
        }
        store.removeMember(npc);
        changes.membershipChanged(npc, previous, null);
        return true;
    }

    public Set<UUID> members(String id) {
        String key = AccessPolicy.normalizeId(id);
        return members.entrySet().stream().filter(entry -> entry.getValue().equals(key))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /** An NPC whose record is gone stops following its policy. Not announced. */
    public void forget(UUID npc) {
        if (members.remove(npc) != null) {
            store.removeMember(npc);
        }
    }

    /** A copied NPC follows the same policy as its original. Not announced. */
    public void copy(UUID original, UUID copy) {
        String policy = members.get(original);
        if (policy != null) {
            members.put(copy, policy);
            store.setMember(copy, policy);
        }
    }

    @Override
    public List<AccessGrant> grants(UUID npc) {
        return policyOf(npc).map(AccessPolicy::grants).orElse(List.of());
    }

    private AccessPolicy existing(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("There is no policy called " + id));
    }

    /** Hears about changes after they are made; the plugin fires its events here. */
    public interface Changes {
        void grantChanged(String policy, AccessGroup group, Set<NpcAction> previous, Set<NpcAction> current);

        /** @param previous the policy followed before, or null; @param current the one now, or null */
        void membershipChanged(UUID npc, String previous, String current);
    }
}
