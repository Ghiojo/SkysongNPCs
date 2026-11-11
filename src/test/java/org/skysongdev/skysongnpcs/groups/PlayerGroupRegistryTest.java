package org.skysongdev.skysongnpcs.groups;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.access.AccessRegistry;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.storage.InMemoryGrantStore;
import org.skysongdev.skysongnpcs.storage.InMemoryGroupStore;
import org.skysongdev.skysongnpcs.storage.InMemoryPolicyStore;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player groups in memory and in the store, and the grants that name them. */
class PlayerGroupRegistryTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final NpcOwner ALICE_DEFAULT = NpcOwner.character(ALICE, "Default");
    private static final UUID NPC = UUID.randomUUID();

    private final InMemoryGroupStore store = new InMemoryGroupStore();
    private final List<String> grantChanges = new ArrayList<>();
    private AccessRegistry grants;
    private PolicyRegistry policies;
    private PlayerGroupRegistry groups;

    @BeforeEach
    void setUp() {
        grants = new AccessRegistry(new InMemoryGrantStore(),
                (npc, group, source, previous, current) -> grantChanges.add(group + " " + source.key() + " " + current));
        policies = new PolicyRegistry(new InMemoryPolicyStore(), new PolicyRegistry.Changes() {
            @Override
            public void grantChanged(String policy, AccessGroup group, Set<NpcAction> previous, Set<NpcAction> current) {
            }

            @Override
            public void membershipChanged(UUID npc, String previous, String current) {
            }
        });
        groups = new PlayerGroupRegistry(store, grants, policies);
    }

    @Test
    void createsGroupsWithLowerCaseNames() {
        PlayerGroup made = groups.create("Friends", ALICE_DEFAULT);

        assertEquals(new PlayerGroup("friends", ALICE_DEFAULT, Set.of()), made);
        assertEquals(Optional.of(made), groups.get("FRIENDS"));
        assertEquals(List.of(made), store.loadAll());
    }

    @Test
    void refusesBadAndTakenNames() {
        groups.create("friends", ALICE_DEFAULT);

        assertEquals(Optional.of(PlayerGroupRegistry.Refusal.BAD_NAME), groups.refusal("two words"));
        assertEquals(Optional.of(PlayerGroupRegistry.Refusal.BAD_NAME), groups.refusal("x".repeat(33)));
        assertEquals(Optional.of(PlayerGroupRegistry.Refusal.TAKEN), groups.refusal("friends"));
        assertThrows(IllegalArgumentException.class, () -> groups.create("two words", ALICE_DEFAULT));
        assertThrows(IllegalStateException.class, () -> groups.create("friends", NpcOwner.server()));
    }

    @Test
    void aNameAGrantStillNamesIsTaken() {
        grants.set(NPC, GrantSource.owner(), PlayerGroup.reference("guards"), Set.of(NpcAction.INFO));

        assertEquals(Optional.of(PlayerGroupRegistry.Refusal.TAKEN), groups.refusal("guards"));
    }

    @Test
    void deletingAlsoRevokesPolicyGrantsAndAPolicyGrantKeepsTheNameTaken() {
        groups.create("friends", ALICE_DEFAULT);
        policies.create("shop", PolicyOwner.character(ALICE, "Default"));
        policies.setGrant("shop", PlayerGroup.reference("friends"), Set.of(NpcAction.INFO));

        assertEquals(Optional.of(1), groups.delete("friends"));
        assertTrue(policies.get("shop").orElseThrow().grants().isEmpty());

        policies.setGrant("shop", PlayerGroup.reference("guards"), Set.of(NpcAction.INFO));
        assertEquals(Optional.of(PlayerGroupRegistry.Refusal.TAKEN), groups.refusal("guards"));
    }

    @Test
    void membersAreOneCharacterOrEveryCharacterOfAPlayer() {
        groups.create("friends", ALICE_DEFAULT);
        groups.addMember("friends", GroupMember.everyCharacter(BOB));
        groups.addMember("friends", GroupMember.character(ALICE, "Second"));

        assertTrue(groups.contains("friends", BOB, "Smith"));
        assertTrue(groups.contains("friends", BOB, null));
        assertTrue(groups.contains("friends", ALICE, "Second"));
        assertFalse(groups.contains("friends", ALICE, "Default"));
        assertFalse(groups.contains("nobody", BOB, "Smith"));

        groups.removeMember("friends", GroupMember.everyCharacter(BOB));
        assertFalse(groups.contains("friends", BOB, "Smith"));
        assertEquals(Set.of(GroupMember.character(ALICE, "Second")), store.loadAll().get(0).members());
    }

    @Test
    void deletingRevokesEveryGrantThatNamesIt() {
        groups.create("friends", ALICE_DEFAULT);
        AccessGroup friends = PlayerGroup.reference("friends");
        grants.set(NPC, GrantSource.owner(), friends, Set.of(NpcAction.INFO));
        grants.set(NPC, GrantSource.plugin("SkysongEconomy"), friends, Set.of(NpcAction.LOOK));
        grants.set(NPC, GrantSource.owner(), AccessGroup.player(BOB), Set.of(NpcAction.INFO));
        grantChanges.clear();

        assertEquals(Optional.of(2), groups.delete("friends"));

        assertEquals(List.of(AccessGroup.player(BOB)), grants.grants(NPC).stream().map(grant -> grant.group()).toList());
        assertEquals(2, grantChanges.size());
        assertTrue(store.loadAll().isEmpty());
        assertEquals(Optional.empty(), groups.refusal("friends"));
        assertEquals(Optional.empty(), groups.delete("friends"));
    }

    @Test
    void loadsWhatTheStoreKept() {
        groups.create("friends", ALICE_DEFAULT);
        groups.addMember("friends", GroupMember.everyCharacter(BOB));

        PlayerGroupRegistry reloaded = new PlayerGroupRegistry(store, grants, policies);
        reloaded.load();

        assertEquals(groups.all(), reloaded.all());
        assertEquals(groups.all(), reloaded.ownedBy(ALICE_DEFAULT));
        assertEquals(groups.all(), reloaded.ownedByPlayer(ALICE));
    }

    @Test
    void changesTheOwner() {
        groups.create("friends", ALICE_DEFAULT);

        groups.setOwner("friends", NpcOwner.server());

        assertEquals(NpcOwner.server(), store.loadAll().get(0).owner());
        assertThrows(IllegalArgumentException.class, () -> groups.setOwner("nobody", NpcOwner.server()));
    }
}
