package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.policy.AccessPolicy;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc policy}. */
class PolicyCommandTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private PolicyRegistry policies;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;
    private UUID clerk;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        policies = plugin.policies();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        AccessCommand access = new AccessCommand(targeting, plugin.access(), plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0", access,
                new PolicyCommand(targeting, plugin.access(), access, policies, plugin.configuration()));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");

        guard = plugin.fakeCitizens().add("Guard");
        clerk = plugin.fakeCitizens().add("Clerk");
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        plugin.registry().adopt(clerk, NpcOwner.character(alice.getUniqueId(), "Default"));
        plugin.fakeCitizens().target(guard);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOwnerMakesAPolicyGrantsThroughItAndPutsNpcsInIt() throws CommandSyntaxException {
        commands.run(alice, "snpc policy create Shop");
        assertEquals("[NPCs] Made policy shop. Add grants with /snpc policy grant shop and put NPCs in it with"
                + " /snpc policy assign shop.", plain(alice));

        commands.run(alice, "snpc policy grant shop player:Bob customer");
        assertEquals("[NPCs] player:Bob now has info, stats-view, teleport, follow, look on every NPC following shop.",
                plain(alice));
        commands.run(alice, "snpc policy assign shop");
        assertEquals("[NPCs] Guard now follows policy shop.", plain(alice));
        plugin.fakeCitizens().target(clerk);
        commands.run(alice, "snpc policy assign shop");
        plain(alice);

        assertTrue(plugin.access().may(bob, plugin.registry().get(guard).orElseThrow(), NpcAction.FOLLOW));
        assertTrue(plugin.access().may(bob, plugin.registry().get(clerk).orElseThrow(), NpcAction.INFO));
        assertEquals(PolicyOwner.character(alice.getUniqueId(), "Default"), policies.get("shop").orElseThrow().owner());
        assertEquals(List.of(new AccessGrant(AccessGroup.player(bob.getUniqueId()), GrantSource.owner(),
                Set.of(NpcAction.INFO, NpcAction.STATS_VIEW, NpcAction.TELEPORT, NpcAction.FOLLOW, NpcAction.LOOK))),
                policies.get("shop").orElseThrow().grants());
    }

    @Test
    void showsListsAndDescribesPolicies() throws CommandSyntaxException {
        commands.run(alice, "snpc policy");
        assertEquals("[NPCs] Guard follows no policy.", plain(alice));
        commands.run(alice, "snpc policy list");
        assertEquals("[NPCs] No policies yet. Make one with /snpc policy create name.", plain(alice));

        policies.create("shop", PolicyOwner.character(alice.getUniqueId(), "Default"));
        commands.run(alice, "snpc policy info shop");
        assertEquals("[NPCs] Policy shop of Alice (Default) lets nobody in yet. 0 NPC(s) follow it.", plain(alice));
        policies.setGrant("shop", AccessGroup.permission("shop.staff"), Set.of(NpcAction.INFO));
        policies.assign(guard, "shop");

        commands.run(alice, "snpc policy");
        assertEquals("[NPCs] Guard follows policy shop of Alice (Default).", plain(alice));
        commands.run(alice, "snpc policy list");
        assertEquals("[NPCs] Policies:", plain(alice));
        assertEquals(" - shop (Alice (Default)): 1 NPC(s)", plain(alice));
        commands.run(alice, "snpc policy info shop");
        assertEquals("[NPCs] Policy shop of Alice (Default), followed by 1 NPC(s), lets in:", plain(alice));
        assertEquals(" - perm:shop.staff: info", plain(alice));
    }

    @Test
    void revokeTakesActionsAwayOrTheWholeGrant() throws CommandSyntaxException {
        commands.run(alice, "snpc policy create shop");
        commands.run(alice, "snpc policy grant shop player:Bob info follow");
        plain(alice);
        plain(alice);

        commands.run(alice, "snpc policy revoke shop player:Bob follow");
        assertEquals("[NPCs] player:Bob now has only info on NPCs following shop.", plain(alice));
        commands.run(alice, "snpc policy revoke shop player:Bob");
        assertEquals("[NPCs] player:Bob no longer has any access through shop.", plain(alice));
        commands.run(alice, "snpc policy revoke shop player:Bob");
        assertEquals("[NPCs] Policy shop gives player:Bob nothing.", plain(alice));
    }

    @Test
    void onlyThePolicysCharacterOrStaffChangeIt() throws CommandSyntaxException {
        commands.run(alice, "snpc policy create shop");
        plain(alice);

        commands.run(bob, "snpc policy grant shop player:Bob all");
        assertEquals("[NPCs] Policy shop is not yours.", plain(bob));
        commands.run(bob, "snpc policy delete shop");
        assertEquals("[NPCs] Policy shop is not yours.", plain(bob));
        plugin.fakeCharacters().play(alice.getUniqueId(), "Other");
        commands.run(alice, "snpc policy info shop");
        assertEquals("[NPCs] Policy shop is not yours.", plain(alice));

        commands.run(staff, "snpc policy delete shop");
        assertEquals("[NPCs] Deleted policy shop. Its 0 NPC(s) now follow no policy.", plain(staff));
        assertEquals(Optional.empty(), policies.get("shop"));
    }

    @Test
    void puttingAnNpcInAPolicyNeedsManageOnItAndThePolicyToBeYours() throws CommandSyntaxException {
        policies.create("bobs", PolicyOwner.character(bob.getUniqueId(), "Smith"));

        commands.run(bob, "snpc policy assign bobs");
        assertEquals("[NPCs] You may not change who can use Guard.", plain(bob));
        commands.run(alice, "snpc policy assign bobs");
        assertEquals("[NPCs] Policy bobs is not yours.", plain(alice));

        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()),
                Set.of(NpcAction.MANAGE));
        commands.run(bob, "snpc policy assign bobs");
        assertEquals("[NPCs] Guard now follows policy bobs.", plain(bob));
        commands.run(bob, "snpc policy assign bobs");
        assertEquals("[NPCs] Guard already follows policy bobs.", plain(bob));
    }

    @Test
    void unassignTakesTheNpcOut() throws CommandSyntaxException {
        policies.create("shop", PolicyOwner.character(alice.getUniqueId(), "Default"));
        policies.assign(guard, "shop");

        commands.run(alice, "snpc policy unassign");
        assertEquals("[NPCs] Guard no longer follows policy shop.", plain(alice));
        commands.run(alice, "snpc policy unassign");
        assertEquals("[NPCs] Guard follows no policy.", plain(alice));
        assertEquals(Set.of(), policies.members("shop"));
    }

    @Test
    void refusesBadTakenAndUnknownNames() throws CommandSyntaxException {
        commands.run(alice, "snpc policy create shop");
        plain(alice);

        commands.run(bob, "snpc policy create shop");
        assertEquals("[NPCs] There is already a policy called shop.", plain(bob));
        commands.run(bob, "snpc policy create sh+p");
        assertEquals("[NPCs] sh+p can't be a policy name: use lower-case letters, digits, - _ and . only.", plain(bob));
        commands.run(bob, "snpc policy info nope");
        assertEquals("[NPCs] There is no policy called nope.", plain(bob));
    }

    @Test
    void aCharacterMayMakeOnlyAsManyPoliciesAsConfigured() throws CommandSyntaxException {
        plugin.configuration().config().set("policies.per-character", 1);
        commands.run(alice, "snpc policy create one");
        plain(alice);

        commands.run(alice, "snpc policy create two");

        assertEquals("[NPCs] This character already has 1 policies.", plain(alice));
        assertFalse(policies.get("two").isPresent());
    }

    @Test
    void aPlayerWithNoCharacterCannotMakeOne() throws CommandSyntaxException {
        PlayerMock carol = server.addPlayer("Carol");

        commands.run(carol, "snpc policy create shop");

        assertEquals("[NPCs] Play a character first: policies belong to characters.", plain(carol));
        assertEquals(List.of(), policies.all().stream().map(AccessPolicy::id).toList());
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
