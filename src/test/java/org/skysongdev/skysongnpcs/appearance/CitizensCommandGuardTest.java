package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.Permissions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Citizens' own commands are staff-only on Skysong NPCs. */
class CitizensCommandGuardTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private PlayerMock alice;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        alice = server.addPlayer("Alice");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        plugin.fakeAppearance().commandTarget(guard);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void evenTheOwnerCannotUseNpcCommandsOnIt() {
        assertTrue(run(alice, "/npc rename Thief"));
        assertTrue(run(alice, "/citizens:npc remove"));
        assertTrue(run(alice, "/trait add sentinel"));

        assertEquals("[NPCs] Guard is a Skysong NPC: change it with /snpc. Citizens' own commands are for staff.",
                PlainTextComponentSerializer.plainText().serialize(alice.nextComponentMessage()));
    }

    @Test
    void staffStillCan() {
        alice.addAttachment(plugin, Permissions.ADMIN, true);

        assertFalse(run(alice, "/npc remove"));
    }

    @Test
    void selectingListingAndOtherCommandsStayOpen() {
        assertFalse(run(alice, "/npc select 3"));
        assertFalse(run(alice, "/npc"));
        assertFalse(run(alice, "/npc list"));
        assertFalse(run(alice, "/snpc name Thief"));
        assertFalse(run(alice, "/otherplugin:npc remove"));
    }

    @Test
    void plainCitizensNpcsAreLeftAlone() {
        plugin.fakeAppearance().commandTarget(plugin.fakeCitizens().add("Villager"));

        assertFalse(run(alice, "/npc remove"));
    }

    @Test
    void configYmlChoosesTheGuardedCommands() {
        plugin.configuration().config().set("citizens-commands.open", java.util.List.of("rename"));

        assertFalse(run(alice, "/npc rename Thief"));
        assertTrue(run(alice, "/npc select"));
    }

    /** Sends the command; true when the guard stopped it. */
    private boolean run(PlayerMock player, String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, command);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }
}
