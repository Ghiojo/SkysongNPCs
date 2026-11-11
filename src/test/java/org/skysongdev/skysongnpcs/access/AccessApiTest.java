package org.skysongdev.skysongnpcs.access;

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
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code SkysongNpcsApi.get().access()}, the way SkysongEconomy would use it, and grants following their NPC. */
class AccessApiTest {
    private static final AccessGroup WORKERS = new AccessGroup("economy", "smithy/workers");

    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private Access access;
    private Plugin economy;
    private GrantSource fromEconomy;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        access = SkysongNpcsApi.get().access();
        economy = MockBukkit.createMockPlugin("SkysongEconomy");
        fromEconomy = GrantSource.plugin(economy);
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        guard = plugin.fakeCitizens().add("Guard");
        SkysongNpcsApi.get().npcs().adopt(guard, NpcOwner.server());
        access.registerGroupProvider(economy, "economy", (group, player, profile) -> "Smith".equals(profile));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPluginGrantsSetsAndRevokesItsOwnGrants() {
        access.grant(guard, fromEconomy, WORKERS, Set.of(NpcAction.INFO));
        access.grant(guard, fromEconomy, WORKERS, Set.of(NpcAction.LOOK));
        assertEquals(Set.of(NpcAction.INFO, NpcAction.LOOK), access.allowed(bob, guard));

        access.setGrant(guard, fromEconomy, WORKERS, Set.of(NpcAction.INVENTORY_EDIT));
        assertTrue(access.may(bob, guard, NpcAction.INVENTORY_VIEW));
        assertFalse(access.may(bob, guard, NpcAction.INFO));

        access.grant(guard, GrantSource.owner(), AccessGroup.permission("x"), Set.of(NpcAction.INFO));
        assertEquals(1, access.revokeAll(guard, fromEconomy));
        assertEquals(1, access.grants(guard).size(), "the owner side's grant stays");
        assertFalse(access.revoke(guard, fromEconomy, WORKERS));
    }

    @Test
    void providersGoWhenTheirPluginDisables() {
        access.grant(guard, fromEconomy, WORKERS, Set.of(NpcAction.INFO));

        server.getPluginManager().disablePlugin(economy);

        assertFalse(access.may(bob, guard, NpcAction.INFO));
        assertFalse(access.unregisterGroupProvider("economy"));
    }

    @Test
    void grantsGoWithTheirNpcAndComeWithItsCopy() {
        UUID copy = UUID.randomUUID();
        access.grant(guard, fromEconomy, WORKERS, Set.of(NpcAction.INFO));

        plugin.registry().copied(guard, copy);
        plugin.registry().removed(guard);

        assertEquals(List.of(), plugin.access().grants().grants(guard));
        assertEquals(1, access.grants(copy).size());
    }

    @Test
    void refusesUnmanagedNpcsAndEmptyGrants() {
        UUID plain = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> access.grants(plain));
        assertThrows(IllegalArgumentException.class, () -> access.may(bob, plain, NpcAction.INFO));
        assertThrows(IllegalArgumentException.class, () -> access.grant(guard, fromEconomy, WORKERS, Set.of()));
        assertEquals(Optional.empty(), access.setGrant(guard, fromEconomy, WORKERS, Set.of()));
    }

    @Test
    void readsPresetsFromConfig() {
        assertEquals(Optional.of(Set.of(NpcAction.INFO, NpcAction.STATS_VIEW, NpcAction.TELEPORT, NpcAction.FOLLOW,
                NpcAction.LOOK)), access.preset("customer"));
    }
}
