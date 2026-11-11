package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps policies in maps, for tests that have no database. */
public class InMemoryPolicyStore implements PolicyStore {
    private final Map<String, PolicyOwner> owners = new LinkedHashMap<>();
    private final Map<String, Map<AccessGroup, Set<NpcAction>>> grants = new HashMap<>();
    private final Map<UUID, String> members = new HashMap<>();

    @Override
    public synchronized Snapshot loadAll() {
        List<StoredPolicy> policies = new ArrayList<>();
        owners.forEach((id, owner) -> policies.add(new StoredPolicy(id, owner,
                new LinkedHashMap<>(grants.getOrDefault(id, Map.of())))));
        return new Snapshot(policies, new HashMap<>(members));
    }

    @Override
    public synchronized void savePolicy(String id, PolicyOwner owner) {
        owners.put(id, owner);
    }

    @Override
    public synchronized void deletePolicy(String id) {
        owners.remove(id);
        grants.remove(id);
        members.values().removeIf(id::equals);
    }

    @Override
    public synchronized void saveGrant(String policy, AccessGroup group, Set<NpcAction> actions) {
        grants.computeIfAbsent(policy, ignored -> new LinkedHashMap<>()).put(group, Set.copyOf(actions));
    }

    @Override
    public synchronized void deleteGrant(String policy, AccessGroup group) {
        Map<AccessGroup, Set<NpcAction>> policyGrants = grants.get(policy);
        if (policyGrants != null) {
            policyGrants.remove(group);
        }
    }

    @Override
    public synchronized void setMember(UUID npc, String policy) {
        members.put(npc, policy);
    }

    @Override
    public synchronized void removeMember(UUID npc) {
        members.remove(npc);
    }

    @Override
    public void close() {
    }
}
