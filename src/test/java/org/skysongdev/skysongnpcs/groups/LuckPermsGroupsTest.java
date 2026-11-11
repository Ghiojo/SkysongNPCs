package org.skysongdev.skysongnpcs.groups;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code lp:} groups, against a mocked LuckPerms API. */
class LuckPermsGroupsTest {
    private final LuckPerms luckPerms = mock(LuckPerms.class);
    private final Player player = mock(Player.class);
    private LuckPermsGroups groups;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PlayerAdapter<Player> adapter = mock(PlayerAdapter.class);
        User user = mock(User.class);
        QueryOptions options = mock(QueryOptions.class);
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(adapter);
        when(adapter.getUser(player)).thenReturn(user);
        when(adapter.getQueryOptions(player)).thenReturn(options);
        // Inherited groups: what LuckPerms resolves in the player's contexts, parents included.
        Group guard = group("guard");
        Group admin = group("admin");
        Group fallback = group("default");
        when(user.getInheritedGroups(options)).thenReturn(List.of(guard, fallback));

        GroupManager manager = mock(GroupManager.class);
        when(luckPerms.getGroupManager()).thenReturn(manager);
        when(manager.getLoadedGroups()).thenReturn(Set.of(guard, admin, fallback));

        groups = new LuckPermsGroups(luckPerms);
    }

    private static Group group(String name) {
        Group group = mock(Group.class);
        when(group.getName()).thenReturn(name);
        return group;
    }

    @Test
    void aPlayerIsInTheGroupsTheyInherit() {
        assertTrue(groups.contains("guard", player, "Default"));
        assertTrue(groups.contains("Guard", player, null));
        assertTrue(groups.contains("default", player, "Default"));
        assertFalse(groups.contains("admin", player, "Default"));
    }

    @Test
    void suggestsEveryLoadedGroup() {
        assertEquals(List.of("admin", "default", "guard"), List.copyOf(groups.suggestions(player)));
    }
}
