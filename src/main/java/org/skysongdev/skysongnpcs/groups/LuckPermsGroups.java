package org.skysongdev.skysongnpcs.groups;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.access.GroupProvider;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Answers for {@code lp:<group>} grants through LuckPerms' API: a player is in a group when
 * they inherit it, directly or through another group, in the contexts they are in now (world,
 * server...). The profile they play does not matter: LuckPerms groups belong to the player.
 *
 * <p>Only loaded when LuckPerms is installed; {@link #hook} is the one way in.
 */
public final class LuckPermsGroups implements GroupProvider {
    private final LuckPerms luckPerms;

    LuckPermsGroups(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** The provider, when LuckPerms is installed and enabled. */
    public static Optional<GroupProvider> hook(Server server) {
        if (!server.getPluginManager().isPluginEnabled("LuckPerms")) {
            return Optional.empty();
        }
        return Optional.of(new LuckPermsGroups(LuckPermsProvider.get()));
    }

    @Override
    public boolean contains(@NotNull String group, @NotNull Player player, @Nullable String profile) {
        PlayerAdapter<Player> players = luckPerms.getPlayerAdapter(Player.class);
        User user = players.getUser(player);
        String name = group.toLowerCase(Locale.ROOT);
        for (Group inherited : user.getInheritedGroups(players.getQueryOptions(player))) {
            if (inherited.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Every group LuckPerms has loaded, by name. */
    @Override
    public @NotNull Collection<String> suggestions(@NotNull Player player) {
        return luckPerms.getGroupManager().getLoadedGroups().stream().map(Group::getName).sorted().toList();
    }
}
