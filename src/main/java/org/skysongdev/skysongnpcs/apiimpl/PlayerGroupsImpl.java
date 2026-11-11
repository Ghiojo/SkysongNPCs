package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroups;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.groups.PlayerGroupRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@link PlayerGroups} over the plugin's {@link PlayerGroupRegistry}. */
final class PlayerGroupsImpl implements PlayerGroups {
    private final PlayerGroupRegistry groups;
    private final AccessService access;

    PlayerGroupsImpl(PlayerGroupRegistry groups, AccessService access) {
        this.groups = groups;
        this.access = access;
    }

    @Override
    public boolean luckPermsEnabled() {
        mainThread();
        return access.provider(LUCKPERMS_TYPE).isPresent();
    }

    @Override
    public @NotNull Optional<PlayerGroup> group(@NotNull String id) {
        mainThread();
        return groups.get(Objects.requireNonNull(id, "id"));
    }

    @Override
    public @NotNull List<PlayerGroup> groups() {
        mainThread();
        return groups.all();
    }

    @Override
    public @NotNull List<PlayerGroup> ownedBy(@NotNull NpcOwner owner) {
        mainThread();
        return groups.ownedBy(Objects.requireNonNull(owner, "owner"));
    }

    @Override
    public @NotNull PlayerGroup create(@NotNull String id, @NotNull NpcOwner owner) {
        mainThread();
        return groups.create(Objects.requireNonNull(id, "id"), owner);
    }

    @Override
    public boolean delete(@NotNull String id) {
        mainThread();
        return groups.delete(Objects.requireNonNull(id, "id")).isPresent();
    }

    @Override
    public @NotNull PlayerGroup addMember(@NotNull String id, @NotNull GroupMember member) {
        mainThread();
        return groups.addMember(id, member);
    }

    @Override
    public @NotNull PlayerGroup removeMember(@NotNull String id, @NotNull GroupMember member) {
        mainThread();
        return groups.removeMember(id, member);
    }

    @Override
    public @NotNull PlayerGroup setOwner(@NotNull String id, @NotNull NpcOwner owner) {
        mainThread();
        return groups.setOwner(id, owner);
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
