package org.skysongdev.skysongnpcs.access;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.FakeCharacterLookup;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.GroupProvider;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.storage.InMemoryGrantStore;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who may do what: owner, staff, and each kind of group. Players are Mockito mocks. */
class AccessServiceTest {
    private FakeCharacterLookup characters;
    private AccessRegistry grants;
    private AccessService access;
    private Player alice;
    private Player bob;
    private NpcRecord npc;

    @BeforeEach
    void setUp() {
        characters = new FakeCharacterLookup();
        grants = new AccessRegistry(new InMemoryGrantStore(), (id, group, source, previous, current) -> { });
        access = new AccessService(grants, characters, new ActionPresets(() -> mock(ConfigurationSection.class)),
                Logger.getAnonymousLogger());
        alice = player("Alice");
        bob = player("Bob");
        characters.play(alice.getUniqueId(), "Default");
        characters.play(bob.getUniqueId(), "Smith");
        npc = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        return player;
    }

    @Test
    void theOwnerMayDoEverythingOnlyWhilePlayingTheOwningCharacter() {
        assertEquals(EnumSet.allOf(NpcAction.class), access.allowed(alice, npc));

        characters.play(alice.getUniqueId(), "Rogue");

        assertFalse(access.may(alice, npc, NpcAction.INFO));
    }

    @Test
    void staffMayDoEverythingAndOthersNothingWithoutAGrant() {
        when(bob.hasPermission(Permissions.ADMIN)).thenReturn(true);
        Player carol = player("Carol");

        assertTrue(access.may(bob, npc, NpcAction.MANAGE));
        assertEquals(Set.of(), access.allowed(carol, npc));
    }

    @Test
    void aServerOwnedNpcIsStaffOnly() {
        NpcRecord server = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());

        assertFalse(access.may(alice, server, NpcAction.INFO));
    }

    @Test
    void playerGroupsMatchEveryCharacterOrOne() {
        grants.set(npc.id(), GrantSource.owner(), AccessGroup.player(bob.getUniqueId()), Set.of(NpcAction.INFO));
        grants.set(npc.id(), GrantSource.owner(), AccessGroup.player(bob.getUniqueId(), "Smith"), Set.of(NpcAction.FOLLOW));

        assertEquals(Set.of(NpcAction.INFO, NpcAction.FOLLOW), access.allowed(bob, npc));
        characters.play(bob.getUniqueId(), "Thief");
        assertEquals(Set.of(NpcAction.INFO), access.allowed(bob, npc));
    }

    @Test
    void permissionGroupsAskThePlayersPermissions() {
        grants.set(npc.id(), GrantSource.owner(), AccessGroup.permission("server.guards"), Set.of(NpcAction.INVENTORY_EDIT));
        when(bob.hasPermission("server.guards")).thenReturn(true);

        assertTrue(access.may(bob, npc, NpcAction.INVENTORY_VIEW));
        assertFalse(access.may(bob, npc, NpcAction.EQUIPMENT_VIEW));
    }

    @Test
    void providerGroupsAreAskedLiveWithTheCharacterPlayed() {
        Plugin economy = mock(Plugin.class);
        when(economy.getName()).thenReturn("SkysongEconomy");
        GroupProvider workers = (group, player, profile) -> group.equals("smithy/workers") && "Smith".equals(profile);
        grants.set(npc.id(), GrantSource.plugin(economy), new AccessGroup("economy", "smithy/workers"), Set.of(NpcAction.LOOK));

        assertFalse(access.may(bob, npc, NpcAction.LOOK), "no provider registered yet");
        access.registerProvider(economy, "Economy", workers);
        assertTrue(access.may(bob, npc, NpcAction.LOOK));

        access.unregisterAll(economy);
        assertFalse(access.may(bob, npc, NpcAction.LOOK));
    }

    @Test
    void aFailingProviderLetsNobodyIn() {
        Plugin broken = mock(Plugin.class);
        when(broken.getName()).thenReturn("Broken");
        access.registerProvider(broken, "broken", (group, player, profile) -> {
            throw new IllegalStateException("boom");
        });
        grants.set(npc.id(), GrantSource.owner(), new AccessGroup("broken", "x"), Set.of(NpcAction.INFO));

        assertFalse(access.may(bob, npc, NpcAction.INFO));
    }

    @Test
    void reservedAndTakenTypesAreRefused() {
        Plugin economy = mock(Plugin.class);
        Plugin other = mock(Plugin.class);
        when(economy.getName()).thenReturn("SkysongEconomy");
        GroupProvider nobody = (group, player, profile) -> false;
        access.registerProvider(economy, "economy", nobody);

        assertThrows(IllegalArgumentException.class, () -> access.registerProvider(economy, "player", nobody));
        assertThrows(IllegalArgumentException.class, () -> access.registerProvider(economy, "lp", nobody));
        assertThrows(IllegalArgumentException.class, () -> access.registerProvider(economy, "two words", nobody));
        assertThrows(IllegalStateException.class, () -> access.registerProvider(other, "economy", nobody));
    }
}
