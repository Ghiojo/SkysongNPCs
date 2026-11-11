package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.Access;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.GroupProvider;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link Access} over the plugin's {@link AccessService}. */
final class AccessImpl implements Access {
    private final NpcRegistry npcs;
    private final AccessService access;

    AccessImpl(NpcRegistry npcs, AccessService access) {
        this.npcs = npcs;
        this.access = access;
    }

    @Override
    public boolean may(@NotNull Player player, @NotNull UUID npc, @NotNull NpcAction action) {
        mainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        return access.may(player, managed(npc), action);
    }

    @Override
    public @NotNull Set<NpcAction> allowed(@NotNull Player player, @NotNull UUID npc) {
        mainThread();
        Objects.requireNonNull(player, "player");
        return access.allowed(player, managed(npc));
    }

    @Override
    public @NotNull List<AccessGrant> grants(@NotNull UUID npc) {
        mainThread();
        return access.grants().grants(managed(npc).id());
    }

    @Override
    public @NotNull AccessGrant grant(@NotNull UUID npc, @NotNull GrantSource source, @NotNull AccessGroup group,
                                      @NotNull Set<NpcAction> actions) {
        mainThread();
        UUID id = managed(npc).id();
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("No actions to grant");
        }
        Set<NpcAction> merged = EnumSet.copyOf(actions);
        access.grants().find(id, source, group).ifPresent(current -> merged.addAll(current.actions()));
        return access.grants().set(id, source, group, merged).orElseThrow();
    }

    @Override
    public @NotNull Optional<AccessGrant> setGrant(@NotNull UUID npc, @NotNull GrantSource source,
                                                   @NotNull AccessGroup group, @NotNull Set<NpcAction> actions) {
        mainThread();
        Objects.requireNonNull(actions, "actions");
        Set<NpcAction> copy = actions.isEmpty() ? Set.of() : EnumSet.copyOf(actions);
        return access.grants().set(managed(npc).id(), source, group, copy);
    }

    @Override
    public boolean revoke(@NotNull UUID npc, @NotNull GrantSource source, @NotNull AccessGroup group) {
        mainThread();
        UUID id = managed(npc).id();
        boolean had = access.grants().find(id, source, group).isPresent();
        access.grants().set(id, source, group, Set.of());
        return had;
    }

    @Override
    public int revokeAll(@NotNull UUID npc, @NotNull GrantSource source) {
        mainThread();
        Objects.requireNonNull(source, "source");
        UUID id = managed(npc).id();
        int revoked = 0;
        for (AccessGrant grant : access.grants().grants(id)) {
            if (grant.source().equals(source)) {
                access.grants().set(id, source, grant.group(), Set.of());
                revoked++;
            }
        }
        return revoked;
    }

    @Override
    public void registerGroupProvider(@NotNull Plugin plugin, @NotNull String type, @NotNull GroupProvider provider) {
        mainThread();
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        access.registerProvider(plugin, type, provider);
    }

    @Override
    public boolean unregisterGroupProvider(@NotNull String type) {
        mainThread();
        return access.unregisterProvider(Objects.requireNonNull(type, "type"));
    }

    @Override
    public @NotNull Optional<Set<NpcAction>> preset(@NotNull String name) {
        mainThread();
        return access.presets().preset(Objects.requireNonNull(name, "name"));
    }

    private NpcRecord managed(UUID npc) {
        Objects.requireNonNull(npc, "npc");
        return npcs.get(npc).orElseThrow(() -> new IllegalArgumentException("NPC " + npc + " is not managed"));
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
