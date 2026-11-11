package org.skysongdev.skysongnpcs.groups;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.access.GroupProvider;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.commands.Permissions;

import java.util.Collection;

/** Answers for {@code group:<id>} grants, from the {@link PlayerGroupRegistry}. */
public final class PlayerGroupProvider implements GroupProvider {
    private final PlayerGroupRegistry groups;

    public PlayerGroupProvider(PlayerGroupRegistry groups) {
        this.groups = groups;
    }

    @Override
    public boolean contains(@NotNull String group, @NotNull Player player, @Nullable String profile) {
        return groups.contains(group, player.getUniqueId(), profile);
    }

    /** The groups the player's characters made; staff get every group. */
    @Override
    public @NotNull Collection<String> suggestions(@NotNull Player player) {
        return (player.hasPermission(Permissions.ADMIN) ? groups.all() : groups.ownedByPlayer(player.getUniqueId()))
                .stream().map(PlayerGroup::id).toList();
    }
}
