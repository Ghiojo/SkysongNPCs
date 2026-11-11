package org.skysongdev.skysongnpcs.groups;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.access.Access;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroups;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code SkysongNpcsApi.get().groups()}, the way another plugin would use it. */
class PlayerGroupsApiTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private PlayerGroups groups;
    private Access access;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        groups = SkysongNpcsApi.get().groups();
        access = SkysongNpcsApi.get().access();
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        guard = plugin.fakeCitizens().add("Guard");
        SkysongNpcsApi.get().npcs().adopt(guard, NpcOwner.server());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPluginMakesAGroupAndGrantsIt() {
        Plugin economy = MockBukkit.createMockPlugin("SkysongEconomy");
        PlayerGroup made = groups.create("smithy", NpcOwner.server());
        access.grant(guard, GrantSource.plugin(economy), made.reference(), Set.of(NpcAction.INFO));

        assertFalse(access.may(bob, guard, NpcAction.INFO));
        groups.addMember("smithy", GroupMember.character(bob.getUniqueId(), "Smith"));
        assertTrue(access.may(bob, guard, NpcAction.INFO));

        assertEquals(List.of(groups.group("SMITHY").orElseThrow()), groups.ownedBy(NpcOwner.server()));
        assertTrue(groups.delete("smithy"));
        assertTrue(access.grants(guard).isEmpty());
        assertFalse(access.may(bob, guard, NpcAction.INFO));
    }

    @Test
    void groupAndLpAreReservedForSkysongNpcs() {
        Plugin other = MockBukkit.createMockPlugin("Other");

        assertThrows(IllegalArgumentException.class,
                () -> access.registerGroupProvider(other, PlayerGroup.TYPE, (group, player, profile) -> true));
        assertThrows(IllegalArgumentException.class,
                () -> access.registerGroupProvider(other, PlayerGroups.LUCKPERMS_TYPE, (group, player, profile) -> true));
        assertFalse(groups.luckPermsEnabled());
        assertEquals("lp:guard", PlayerGroups.luckPerms("Guard").toString());
    }

    @Test
    void refusesBadAndTakenNames() {
        groups.create("smithy", NpcOwner.server());

        assertThrows(IllegalArgumentException.class, () -> groups.create("not ok", NpcOwner.server()));
        assertThrows(IllegalStateException.class, () -> groups.create("smithy", NpcOwner.server()));
        assertThrows(IllegalArgumentException.class, () -> groups.addMember("nobody", GroupMember.everyCharacter(UUID.randomUUID())));
    }
}
