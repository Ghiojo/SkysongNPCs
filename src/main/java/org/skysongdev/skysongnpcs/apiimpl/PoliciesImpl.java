package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.AccessPolicy;
import org.skysongdev.skysongnpcs.api.policy.Policies;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link Policies} over the plugin's {@link PolicyRegistry}. */
final class PoliciesImpl implements Policies {
    private final NpcRegistry npcs;
    private final PolicyRegistry policies;

    PoliciesImpl(NpcRegistry npcs, PolicyRegistry policies) {
        this.npcs = npcs;
        this.policies = policies;
    }

    @Override
    public @NotNull Optional<AccessPolicy> get(@NotNull String id) {
        mainThread();
        Objects.requireNonNull(id, "id");
        try {
            return policies.get(id);
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    @Override
    public @NotNull Collection<AccessPolicy> all() {
        mainThread();
        return policies.all();
    }

    @Override
    public @NotNull List<AccessPolicy> ownedBy(@NotNull PolicyOwner owner) {
        mainThread();
        return policies.ownedBy(Objects.requireNonNull(owner, "owner"));
    }

    @Override
    public @NotNull AccessPolicy create(@NotNull String id, @NotNull PolicyOwner owner) {
        mainThread();
        return policies.create(id, owner);
    }

    @Override
    public boolean delete(@NotNull String id) {
        mainThread();
        return get(id).isPresent() && policies.delete(id);
    }

    @Override
    public @NotNull AccessPolicy grant(@NotNull String id, @NotNull AccessGroup group, @NotNull Set<NpcAction> actions) {
        mainThread();
        AccessPolicy policy = existing(id);
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("No actions to grant");
        }
        Set<NpcAction> merged = EnumSet.copyOf(actions);
        policies.find(policy, group).ifPresent(current -> merged.addAll(current.actions()));
        return policies.setGrant(policy.id(), group, merged);
    }

    @Override
    public @NotNull AccessPolicy setGrant(@NotNull String id, @NotNull AccessGroup group, @NotNull Set<NpcAction> actions) {
        mainThread();
        Objects.requireNonNull(actions, "actions");
        Set<NpcAction> copy = actions.isEmpty() ? Set.of() : EnumSet.copyOf(actions);
        return policies.setGrant(existing(id).id(), group, copy);
    }

    @Override
    public boolean revoke(@NotNull String id, @NotNull AccessGroup group) {
        mainThread();
        AccessPolicy policy = existing(id);
        boolean had = policies.find(policy, Objects.requireNonNull(group, "group")).isPresent();
        policies.setGrant(policy.id(), group, Set.of());
        return had;
    }

    @Override
    public @NotNull Optional<AccessPolicy> policyOf(@NotNull UUID npc) {
        mainThread();
        return policies.policyOf(managed(npc));
    }

    @Override
    public void assign(@NotNull UUID npc, @NotNull String id) {
        mainThread();
        policies.assign(managed(npc), existing(id).id());
    }

    @Override
    public boolean unassign(@NotNull UUID npc) {
        mainThread();
        return policies.unassign(managed(npc));
    }

    @Override
    public @NotNull Set<UUID> members(@NotNull String id) {
        mainThread();
        return policies.members(existing(id).id());
    }

    private AccessPolicy existing(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("There is no policy called " + id));
    }

    private UUID managed(UUID npc) {
        Objects.requireNonNull(npc, "npc");
        return npcs.get(npc).orElseThrow(() -> new IllegalArgumentException("NPC " + npc + " is not managed")).id();
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
