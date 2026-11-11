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
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;
import org.skysongdev.skysongnpcs.api.event.NpcOwnerChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc adopt}, {@code /snpc release} and {@code /snpc owner}. */
class OwnershipCommandsTest {
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
                new AdoptCommand(targeting), new ReleaseCommand(targeting), new OwnerCommand(targeting));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void adoptWithoutPlayerGivesTheNpcToTheServer() throws CommandSyntaxException {
        commands.run(staff, "snpc adopt");

        assertEquals("[NPCs] Guard is now a Skysong NPC owned by the server, with a stat sheet.", plain(staff));
        assertEquals(NpcOwner.server(), owner());
    }

    @Test
    void adoptWithPlayerUsesTheCharacterTheyPlay() throws CommandSyntaxException {
        commands.run(staff, "snpc adopt Alice");

        assertEquals("[NPCs] Guard is now a Skysong NPC owned by Alice (Default), with a stat sheet.", plain(staff));
        assertEquals(NpcOwner.character(alice.getUniqueId(), "Default"), owner());
    }

    @Test
    void anAdoptedNpcHasAStatSheetAtOnce() throws CommandSyntaxException {
        commands.run(staff, "snpc adopt");

        assertTrue(plugin.fakeSheets().exists(guard));
        assertEquals(0, plugin.fakeSheets().value(guard, "strength"));
    }

    @Test
    void anNpcWhoseSheetCannotBeMadeIsNotAdopted() throws CommandSyntaxException {
        plugin.fakeSheets().refuseCreate = true;

        commands.run(staff, "snpc adopt");

        assertTrue(plain(staff).startsWith("[NPCs] Could not adopt Guard: its stat sheet could not be made"));
        assertFalse(plugin.registry().isManaged(guard));
    }

    @Test
    void theApiAdoptsWithASheetOrNotAtAll() {
        UUID dog = plugin.fakeCitizens().add("Dog");
        org.skysongdev.skysongnpcs.api.SkysongNpcsApi.get().npcs().adopt(dog, NpcOwner.server());
        assertTrue(org.skysongdev.skysongnpcs.api.SkysongNpcsApi.get().sheets().hasSheet(dog));

        plugin.fakeSheets().refuseCreate = true;
        UUID cat = plugin.fakeCitizens().add("Cat");
        assertThrows(IllegalStateException.class, () -> org.skysongdev.skysongnpcs.api.SkysongNpcsApi.get().npcs().adopt(cat, NpcOwner.server()));
        assertFalse(org.skysongdev.skysongnpcs.api.SkysongNpcsApi.get().npcs().isManaged(cat));
    }

    @Test
    void adoptNamesAProfileTheyDoNotHave() throws CommandSyntaxException {
        commands.run(staff, "snpc adopt Alice Pirate Queen");

        assertEquals("[NPCs] Alice has no character called Pirate Queen.", plain(staff));
        assertFalse(plugin.registry().isManaged(guard));
    }

    @Test
    void adoptTellsWhoOwnsAnNpcThatIsManagedAlready() throws CommandSyntaxException {
        commands.run(staff, "snpc adopt Alice");
        plain(staff);

        commands.run(staff, "snpc adopt");

        assertEquals("[NPCs] Guard is already a Skysong NPC, owned by Alice (Default).", plain(staff));
    }

    @Test
    void adoptNeedsATarget() throws CommandSyntaxException {
        plugin.fakeCitizens().target((UUID) null);

        commands.run(staff, "snpc adopt");

        assertEquals("[NPCs] Look at the NPC you mean (staff can also /npc select it).", plain(staff));
    }

    @Test
    void adoptAndReleaseAreStaffOnly() {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc adopt"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc release"));
    }

    @Test
    void releaseDeletesTheRecord() throws CommandSyntaxException {
        commands.run(staff, "snpc adopt");
        plain(staff);

        commands.run(staff, "snpc release");

        assertEquals("[NPCs] Guard is a plain Citizens NPC again. Its Skysong data is deleted.", plain(staff));
        assertFalse(plugin.registry().isManaged(guard));
        server.getPluginManager().assertEventFired(NpcDeleteEvent.class);
    }

    @Test
    void releaseRefusesAPlainCitizensNpc() throws CommandSyntaxException {
        commands.run(staff, "snpc release");

        assertEquals("[NPCs] Guard is not a Skysong NPC.", plain(staff));
    }

    @Test
    void theOwnerGivesTheNpcToAnotherCharacter() throws CommandSyntaxException {
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));

        commands.run(alice, "snpc owner Bob");

        assertEquals("[NPCs] Guard now belongs to Bob (Smith).", plain(alice));
        assertEquals(NpcOwner.character(bob.getUniqueId(), "Smith"), owner());
        server.getPluginManager().assertEventFired(NpcOwnerChangeEvent.class);
    }

    @Test
    void theOwnerMustBePlayingTheOwningCharacter() throws CommandSyntaxException {
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        plugin.fakeCharacters().play(alice.getUniqueId(), "Rogue");

        commands.run(alice, "snpc owner Bob");

        assertEquals("[NPCs] Only Guard's owner can do that, while playing that character.", plain(alice));
        assertEquals(NpcOwner.character(alice.getUniqueId(), "Default"), owner());
    }

    @Test
    void ownerShowsWhoOwnsIt() throws CommandSyntaxException {
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));

        commands.run(alice, "snpc owner");

        assertEquals("[NPCs] Guard belongs to Alice (Default).", plain(alice));
    }

    @Test
    void ownerSaysWhenNothingChanges() throws CommandSyntaxException {
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));

        commands.run(staff, "snpc owner Alice Default");

        assertEquals("[NPCs] Guard already belongs to Alice (Default).", plain(staff));
    }

    @Test
    void onlyStaffGiveAnNpcToTheServer() throws CommandSyntaxException {
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));

        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc owner server"));

        commands.run(staff, "snpc owner server");
        assertEquals("[NPCs] Guard now belongs to the server.", plain(staff));
        assertEquals(NpcOwner.server(), owner());
    }

    private NpcOwner owner() {
        return plugin.registry().get(guard).orElseThrow().owner();
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
