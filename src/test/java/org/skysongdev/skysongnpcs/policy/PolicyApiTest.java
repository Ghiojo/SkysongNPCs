package org.skysongdev.skysongnpcs.policy;

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
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.event.AccessPolicyChangeEvent;
import org.skysongdev.skysongnpcs.api.event.NpcPolicyChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.policy.AccessPolicy;
import org.skysongdev.skysongnpcs.api.policy.Policies;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code SkysongNpcsApi.get().policies()}, the way SkysongEconomy would keep one policy per business. */
class PolicyApiTest {
    private static final AccessGroup WORKERS = new AccessGroup("economy", "smithy/workers");

    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private Policies policies;
    private Access access;
    private PolicyOwner economy;
    private PlayerMock bob;
    private UUID guard;
    private UUID clerk;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        policies = SkysongNpcsApi.get().policies();
        access = SkysongNpcsApi.get().access();
        Plugin economyPlugin = MockBukkit.createMockPlugin("SkysongEconomy");
        economy = PolicyOwner.plugin(economyPlugin);
        access.registerGroupProvider(economyPlugin, "economy", (group, player, profile) -> "Smith".equals(profile));
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        guard = plugin.fakeCitizens().add("Guard");
        clerk = plugin.fakeCitizens().add("Clerk");
        SkysongNpcsApi.get().npcs().adopt(guard, NpcOwner.server());
        SkysongNpcsApi.get().npcs().adopt(clerk, NpcOwner.server());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyNpcInAPolicyLetsItsGroupsIn() {
        policies.create("economy.smithy", economy);
        policies.assign(guard, "economy.smithy");
        policies.assign(clerk, "Economy.Smithy");
        assertFalse(access.may(bob, guard, NpcAction.INVENTORY_VIEW));

        policies.grant("economy.smithy", WORKERS, Set.of(NpcAction.INVENTORY_EDIT));

        assertTrue(access.may(bob, guard, NpcAction.INVENTORY_VIEW));
        assertTrue(access.may(bob, clerk, NpcAction.INVENTORY_EDIT));
        assertFalse(access.may(bob, clerk, NpcAction.PICKUP));
        assertEquals(Set.of(guard, clerk), policies.members("economy.smithy"));
        server.getPluginManager().assertEventFired(AccessPolicyChangeEvent.class);
        server.getPluginManager().assertEventFired(NpcPolicyChangeEvent.class);
    }

    @Test
    void policyGrantsAddToTheNpcsOwnAndShowThePolicysSource() {
        access.grant(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()), Set.of(NpcAction.LOOK));
        policies.create("economy.smithy", economy);
        AccessPolicy policy = policies.grant("economy.smithy", WORKERS, Set.of(NpcAction.INFO));
        policies.assign(guard, "economy.smithy");

        assertEquals(List.of(new AccessGrant(WORKERS, GrantSource.plugin("SkysongEconomy"), Set.of(NpcAction.INFO))),
                policy.grants());
        assertEquals(Set.of(NpcAction.LOOK, NpcAction.INFO), access.allowed(bob, guard));
        assertEquals(1, access.grants(guard).size(), "the NPC's own grants stay its own");
    }

    @Test
    void grantSetGrantAndRevoke() {
        policies.create("shop", economy);
        policies.grant("shop", WORKERS, Set.of(NpcAction.INFO));
        policies.grant("shop", WORKERS, Set.of(NpcAction.LOOK));
        assertEquals(Set.of(NpcAction.INFO, NpcAction.LOOK), policies.get("shop").orElseThrow().grants().get(0).actions());

        policies.setGrant("shop", WORKERS, Set.of(NpcAction.FOLLOW));
        assertEquals(Set.of(NpcAction.FOLLOW), policies.get("shop").orElseThrow().grants().get(0).actions());

        assertTrue(policies.revoke("shop", WORKERS));
        assertFalse(policies.revoke("shop", WORKERS));
        assertEquals(List.of(), policies.get("shop").orElseThrow().grants());
    }

    @Test
    void anNpcFollowsOnePolicyAtATime() {
        policies.create("one", economy);
        policies.create("two", economy);
        policies.assign(guard, "one");

        policies.assign(guard, "two");

        assertEquals("two", policies.policyOf(guard).map(AccessPolicy::id).orElseThrow());
        assertEquals(Set.of(), policies.members("one"));
        assertTrue(policies.unassign(guard));
        assertEquals(Optional.empty(), policies.policyOf(guard));
        assertFalse(policies.unassign(guard));
    }

    @Test
    void deletingAPolicyLeavesItsNpcsWithTheirOwnGrants() {
        policies.create("shop", economy);
        policies.grant("shop", WORKERS, Set.of(NpcAction.INFO));
        policies.assign(guard, "shop");

        assertTrue(policies.delete("shop"));

        assertEquals(Optional.empty(), policies.policyOf(guard));
        assertFalse(access.may(bob, guard, NpcAction.INFO));
        assertFalse(policies.delete("shop"));
    }

    @Test
    void listsByOwner() {
        PolicyOwner alice = PolicyOwner.character(UUID.randomUUID(), "Default");
        policies.create("a", economy);
        policies.create("b", alice);

        assertEquals(List.of("a"), policies.ownedBy(economy).stream().map(AccessPolicy::id).toList());
        assertEquals(List.of("a", "b"), policies.all().stream().map(AccessPolicy::id).toList());
    }

    @Test
    void refusesBadIdsTakenIdsUnknownPoliciesAndUnmanagedNpcs() {
        policies.create("shop", economy);

        assertThrows(IllegalArgumentException.class, () -> policies.create("my shop", economy));
        assertThrows(IllegalStateException.class, () -> policies.create("SHOP", economy));
        assertThrows(IllegalArgumentException.class, () -> policies.assign(guard, "nope"));
        assertThrows(IllegalArgumentException.class, () -> policies.grant("shop", WORKERS, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policies.assign(UUID.randomUUID(), "shop"));
        assertEquals(Optional.empty(), policies.get("my shop"));
    }

    @Test
    void aDeletedNpcLeavesItsPolicyAndACopyFollowsIt() {
        policies.create("shop", economy);
        policies.assign(guard, "shop");
        UUID copy = plugin.fakeCitizens().add("Guard copy");

        plugin.registry().copied(guard, copy);
        plugin.registry().removed(guard);

        assertEquals(Set.of(copy), policies.members("shop"));
    }

    @Test
    void policiesSurviveARestart() {
        policies.create("shop", economy);
        policies.grant("shop", WORKERS, Set.of(NpcAction.INFO));
        policies.assign(guard, "shop");

        plugin.policies().load();

        assertTrue(access.may(bob, guard, NpcAction.INFO));
        assertEquals("shop", policies.policyOf(guard).map(AccessPolicy::id).orElseThrow());
    }
}
