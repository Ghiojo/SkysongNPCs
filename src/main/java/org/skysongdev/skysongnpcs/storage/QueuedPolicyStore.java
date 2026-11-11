package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another policy store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedPolicyStore implements PolicyStore {
    private final PolicyStore store;
    private final BackgroundWriter writer;

    public QueuedPolicyStore(PolicyStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs policy writer", onFailure);
    }

    @Override
    public Snapshot loadAll() {
        return store.loadAll();
    }

    @Override
    public void savePolicy(String id, PolicyOwner owner) {
        writer.submit(() -> store.savePolicy(id, owner));
    }

    @Override
    public void deletePolicy(String id) {
        writer.submit(() -> store.deletePolicy(id));
    }

    @Override
    public void saveGrant(String policy, AccessGroup group, Set<NpcAction> actions) {
        Set<NpcAction> copy = EnumSet.copyOf(actions);
        writer.submit(() -> store.saveGrant(policy, group, copy));
    }

    @Override
    public void deleteGrant(String policy, AccessGroup group) {
        writer.submit(() -> store.deleteGrant(policy, group));
    }

    @Override
    public void setMember(UUID npc, String policy) {
        writer.submit(() -> store.setMember(npc, policy));
    }

    @Override
    public void removeMember(UUID npc) {
        writer.submit(() -> store.removeMember(npc));
    }

    @Override
    public void close() {
        try {
            writer.close();
        } finally {
            store.close();
        }
    }
}
