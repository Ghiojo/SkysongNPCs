package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.Npcs;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link Npcs} over the plugin's {@link NpcRegistry}. */
final class NpcsImpl implements Npcs {
    private final NpcRegistry registry;
    private final NpcLookup citizens;

    NpcsImpl(NpcRegistry registry, NpcLookup citizens) {
        this.registry = registry;
        this.citizens = citizens;
    }

    @Override
    public boolean isManaged(@NotNull UUID npc) {
        mainThread();
        return registry.isManaged(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public @NotNull Optional<ManagedNpc> get(@NotNull UUID npc) {
        mainThread();
        return registry.get(Objects.requireNonNull(npc, "npc")).map(ManagedNpc.class::cast);
    }

    @Override
    public @NotNull List<ManagedNpc> all() {
        mainThread();
        return List.copyOf(registry.all());
    }

    @Override
    public @NotNull List<ManagedNpc> ownedBy(@NotNull NpcOwner owner) {
        mainThread();
        return List.copyOf(registry.ownedBy(Objects.requireNonNull(owner, "owner")));
    }

    @Override
    public @NotNull List<ManagedNpc> ownedByPlayer(@NotNull UUID player) {
        mainThread();
        return List.copyOf(registry.ownedByPlayer(Objects.requireNonNull(player, "player")));
    }

    @Override
    public @NotNull ManagedNpc adopt(@NotNull UUID npc, @NotNull NpcOwner owner) {
        mainThread();
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(owner, "owner");
        if (!citizens.exists(npc)) {
            throw new IllegalArgumentException("Citizens has no NPC " + npc);
        }
        return registry.adopt(npc, owner);
    }

    @Override
    public boolean release(@NotNull UUID npc) {
        mainThread();
        return registry.release(Objects.requireNonNull(npc, "npc")).isPresent();
    }

    @Override
    public @NotNull ManagedNpc setOwner(@NotNull UUID npc, @NotNull NpcOwner owner) {
        mainThread();
        return registry.setOwner(Objects.requireNonNull(npc, "npc"), owner);
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
