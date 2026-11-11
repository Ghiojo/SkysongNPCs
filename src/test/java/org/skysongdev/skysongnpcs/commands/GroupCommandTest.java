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
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc group}, and {@code group:} and {@code lp:} in {@code /snpc access}. */
class GroupCommandTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new GroupCommand(targeting, plugin.groups(), plugin.configuration()),
                new AccessCommand(targeting, plugin.access(), plugin.configuration()));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        // plugin.yml gives every player skysongnpcs.groups; MockBukkit does not apply defaults.
        alice.addAttachment(plugin, Permissions.GROUPS, true);
        bob.addAttachment(plugin, Permissions.GROUPS, true);

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aGroupLetsItsMembersUseAnNpc() throws CommandSyntaxException {
        commands.run(alice, "snpc group create Friends");
        assertEquals("[NPCs] Made group friends. Let it use an NPC with /snpc access grant group:friends ACTIONS.",
                plain(alice));
        commands.run(alice, "snpc access grant group:friends info");
        plain(alice);
        assertFalse(plugin.access().may(bob, plugin.registry().get(guard).orElseThrow(), NpcAction.INFO));

        commands.run(alice, "snpc group add friends Bob");

        assertEquals("[NPCs] Bob (every character) is now in friends.", plain(alice));
        assertTrue(plugin.access().may(bob, plugin.registry().get(guard).orElseThrow(), NpcAction.INFO));
        assertFalse(plugin.access().may(bob, plugin.registry().get(guard).orElseThrow(), NpcAction.LOOK));
    }

    @Test
    void aMemberCanBeOneCharacter() throws CommandSyntaxException {
        plugin.fakeCharacters().play(bob.getUniqueId(), "Thief");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        commands.run(alice, "snpc group create friends");
        commands.run(alice, "snpc group add friends Bob Thief");
        commands.run(alice, "snpc access grant group:friends info");

        assertFalse(plugin.access().may(bob, plugin.registry().get(guard).orElseThrow(), NpcAction.INFO));
        plugin.fakeCharacters().play(bob.getUniqueId(), "Thief");
        assertTrue(plugin.access().may(bob, plugin.registry().get(guard).orElseThrow(), NpcAction.INFO));
    }

    @Test
    void addingAnUnknownProfileIsRefused() throws CommandSyntaxException {
        commands.run(alice, "snpc group create friends");
        plain(alice);

        commands.run(alice, "snpc group add friends Bob Nobody");

        assertEquals("[NPCs] Bob has no character called Nobody.", plain(alice));
        assertTrue(plugin.groups().get("friends").orElseThrow().members().isEmpty());
    }

    @Test
    void onlyTheOwningCharacterAndStaffChangeAGroup() throws CommandSyntaxException {
        commands.run(alice, "snpc group create friends");
        plain(alice);

        commands.run(bob, "snpc group add friends Bob");
        assertEquals("[NPCs] Only Alice (Default) and staff can change group friends.", plain(bob));

        plugin.fakeCharacters().play(alice.getUniqueId(), "Other");
        commands.run(alice, "snpc group add friends Bob");
        assertEquals("[NPCs] Only Alice (Default) and staff can change group friends.", plain(alice));

        commands.run(staff, "snpc group add friends Bob");
        assertEquals(Set.of(GroupMember.everyCharacter(bob.getUniqueId())),
                plugin.groups().get("friends").orElseThrow().members());
    }

    @Test
    void namesMustBeValidAndFree() throws CommandSyntaxException {
        commands.run(alice, "snpc group create friends");
        plain(alice);

        commands.run(bob, "snpc group create friends");
        assertEquals("[NPCs] The name friends is taken.", plain(bob));
        commands.run(bob, "snpc group create fr.ends");
        assertEquals("[NPCs] fr.ends can't be a group name: use up to 32 lower-case letters, digits, - and _.", plain(bob));
    }

    @Test
    void deletingAGroupRevokesItsGrants() throws CommandSyntaxException {
        commands.run(alice, "snpc group create friends");
        commands.run(alice, "snpc group add friends Bob");
        commands.run(alice, "snpc access grant group:friends info");
        for (int i = 0; i < 3; i++) {
            plain(alice);
        }

        commands.run(alice, "snpc group delete friends");

        assertEquals("[NPCs] Deleted group friends, and the 1 grant(s) that named it.", plain(alice));
        assertTrue(plugin.access().grants().grants(guard).isEmpty());
        assertEquals(Optional.empty(), plugin.groups().get("friends"));
    }

    @Test
    void listsAndShowsTheGroupsOfTheCharacterPlayed() throws CommandSyntaxException {
        commands.run(alice, "snpc group create friends");
        commands.run(alice, "snpc group add friends Bob Smith");
        plain(alice);
        plain(alice);

        commands.run(alice, "snpc group");
        assertEquals("[NPCs] Groups you can change:", plain(alice));
        assertEquals(" - friends (Alice (Default)): 1 member(s)", plain(alice));

        commands.run(alice, "snpc group info friends");
        assertEquals("[NPCs] Group friends, made by Alice (Default):", plain(alice));
        assertEquals(" - Bob (Smith)", plain(alice));

        commands.run(bob, "snpc group list");
        assertEquals("[NPCs] You have no groups. Make one with /snpc group create NAME.", plain(bob));
    }

    @Test
    void theLimitsInConfigApplyToPlayersButNotStaff() throws CommandSyntaxException {
        plugin.configuration().config().set("groups.per-character", 1);
        plugin.configuration().config().set("groups.max-members", 1);
        commands.run(alice, "snpc group create one");
        commands.run(alice, "snpc group add one Bob");
        plain(alice);
        plain(alice);

        commands.run(alice, "snpc group create two");
        assertEquals("[NPCs] This character already has 1 group(s).", plain(alice));
        commands.run(alice, "snpc group add one Staff");
        assertEquals("[NPCs] one already has 1 members.", plain(alice));

        commands.run(staff, "snpc group add one Alice");
        assertEquals(2, plugin.groups().get("one").orElseThrow().members().size());
    }

    @Test
    void staffMakeServerGroups() throws CommandSyntaxException {
        commands.run(staff, "snpc group create guards server");

        assertEquals(NpcOwner.server(), plugin.groups().get("guards").orElseThrow().owner());
    }

    @Test
    void playersWithoutTheGroupsPermissionCannotUseIt() {
        PlayerMock carol = server.addPlayer("Carol");

        assertThrows(CommandSyntaxException.class, () -> commands.run(carol, "snpc group create mine"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc group create mine server"));
    }

    @Test
    void lpGroupsAreRefusedWithoutLuckPerms() throws CommandSyntaxException {
        commands.run(alice, "snpc access grant lp:guard info");

        assertTrue(plain(alice).startsWith("[NPCs] lp:guard is not a group."));
        assertTrue(plugin.access().grants().grants(guard).isEmpty());
        assertNull(plugin.access().provider("lp").orElse(null));
        assertTrue(plugin.access().provider(PlayerGroup.TYPE).isPresent());
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
