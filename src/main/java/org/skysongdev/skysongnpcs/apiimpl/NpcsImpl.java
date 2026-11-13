package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.Npcs;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.npc.NpcRemover;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link Npcs} over the plugin's {@link NpcRegistry}. */
final class NpcsImpl implements Npcs {
    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final NpcFactory factory;
    private final NpcRemover remover;

    NpcsImpl(NpcRegistry registry, NpcLookup citizens, NpcFactory factory, NpcRemover remover) {
        this.registry = registry;
        this.citizens = citizens;
        this.factory = factory;
        this.remover = remover;
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
    public boolean remove(@NotNull UUID npc) {
        mainThread();
        return remover.remove(Objects.requireNonNull(npc, "npc"));
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

    // M17

    @Override
    public @NotNull ManagedNpc create(@NotNull String template, @Nullable String name, @NotNull NpcOwner owner,
                                      @NotNull Location location) {
        mainThread();
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
        NpcTemplate found = factory.template(template)
                .orElseThrow(() -> new IllegalArgumentException("There is no NPC template " + template));
        try {
            return factory.create(found, name, owner, location)
                    .orElseThrow(() -> new IllegalStateException("Citizens could not spawn the NPC at " + location
                            + ": another plugin refused the spawn"));
        } catch (IllegalStateException refused) {
            throw refused;
        } catch (RuntimeException failed) {
            throw new IllegalStateException("Could not make an NPC from the template " + template, failed);
        }
    }
}
