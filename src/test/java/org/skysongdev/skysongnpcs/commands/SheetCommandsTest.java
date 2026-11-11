package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc sheet}, {@code /snpc stats}, {@code /snpc modifier} and {@code /snpc proficiency}. */
class SheetCommandsTest {
    private OfflineSkysongNPCs plugin;
    private FakeStatSheets stats;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() throws IOException {
        ServerMock server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        stats = plugin.fakeSheets();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new SheetCommand(targeting, plugin.access(), plugin.sheets(), plugin.templates(), plugin.configuration()),
                new StatsCommand(targeting, plugin.sheets()),
                new ModifierCommand(targeting, plugin.sheets()),
                new ProficiencyCommand(targeting, plugin.sheets()));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));

        Path folder = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(folder.resolve("brute.yml"), "stats:\n  str: 5\n");
        Files.writeString(folder.resolve("ghost.yml"), "sheet: false\n");
        plugin.templates().reload();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theExampleTemplateIsWrittenOnFirstStart() {
        assertTrue(plugin.templates().get("example").isPresent());
    }

    @Test
    void staffCreateASheetFromATemplate() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create brute");

        assertEquals("[NPCs] Guard now has a stat sheet from template brute.", plain(staff));
        assertEquals(5, stats.value(guard, "strength"));
    }

    @Test
    void createWithoutTemplateStartsEveryStatAtItsStartValue() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create");

        assertEquals("[NPCs] Guard now has a stat sheet, every stat at its start value.", plain(staff));
        assertTrue(stats.exists(guard));
    }

    @Test
    void createRefusesWhatItCannotDo() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create nope");
        assertEquals("[NPCs] There is no NPC template called nope.", plain(staff));
        commands.run(staff, "snpc sheet create ghost");
        assertEquals("[NPCs] Template ghost gives no stat sheet.", plain(staff));
        assertFalse(stats.exists(guard));

        commands.run(staff, "snpc sheet create");
        plain(staff);
        commands.run(staff, "snpc sheet create");
        assertEquals("[NPCs] Guard already has a stat sheet.", plain(staff));
    }

    @Test
    void playersCannotCreateSheets() {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc sheet create"));
    }

    @Test
    void theOwnerSeesTheSheetAndOthersNeedStatsView() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create brute");
        plain(staff);

        commands.run(alice, "snpc sheet");
        assertEquals("Guard: strength 5", plain(alice));
        assertEquals("Guard: hp 0", plain(alice));

        commands.run(bob, "snpc sheet");
        assertEquals("[NPCs] You may not see Guard's stat sheet.", plain(bob));

        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()),
                Set.of(NpcAction.STATS_VIEW));
        commands.run(bob, "snpc sheet");
        assertEquals("Guard: strength 5", plain(bob));
    }

    @Test
    void anNpcWithoutSheetSaysSo() throws CommandSyntaxException {
        commands.run(alice, "snpc sheet");

        assertEquals("[NPCs] Guard has no stat sheet.", plain(alice));
    }

    @Test
    void staffEditStats() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create brute");
        plain(staff);

        commands.run(staff, "snpc stats str add 2");
        assertEquals("[NPCs] Guard's str is now 7.", plain(staff));
        commands.run(staff, "snpc stats strength set -1");
        assertEquals(-1, stats.value(guard, "strength"));
        plain(staff);
        commands.run(staff, "snpc stats luck set 3");
        assertEquals("[NPCs] There is no stat called luck.", plain(staff));
    }

    @Test
    void playersCannotEditStats() {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc stats str add 2"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc modifier add str 2"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc proficiency swords expert"));
    }

    @Test
    void staffManageModifiers() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create");
        plain(staff);

        commands.run(staff, "snpc modifier");
        assertEquals("[NPCs] Guard has no modifiers.", plain(staff));
        commands.run(staff, "snpc modifier add str -2");
        assertEquals("[NPCs] Added -2 str to Guard (modifier 1).", plain(staff));
        commands.run(staff, "snpc modifier list");
        assertEquals("[NPCs] Guard's modifiers:", plain(staff));
        assertEquals(" - 1: str -2", plain(staff));
        commands.run(staff, "snpc modifier add luck 1");
        assertEquals("[NPCs] There is no stat or bar max called luck.", plain(staff));
        commands.run(staff, "snpc modifier del 1");
        assertEquals("[NPCs] Removed modifier 1 from Guard.", plain(staff));
        commands.run(staff, "snpc modifier del 1");
        assertEquals("[NPCs] Guard has no modifier 1.", plain(staff));
    }

    @Test
    void staffSetProficiencyTiers() throws CommandSyntaxException {
        commands.run(staff, "snpc sheet create");
        plain(staff);

        commands.run(staff, "snpc proficiency swords expert");
        assertEquals("[NPCs] Guard's swords is now expert.", plain(staff));
        assertEquals("expert", stats.tier(guard, "swords"));
        commands.run(staff, "snpc proficiency axes expert");
        assertEquals("[NPCs] There is no proficiency called axes.", plain(staff));
        commands.run(staff, "snpc proficiency swords godlike");
        assertEquals("[NPCs] There is no tier called godlike.", plain(staff));
    }

    @Test
    void editingNeedsASheet() throws CommandSyntaxException {
        commands.run(staff, "snpc stats str add 1");

        assertEquals("[NPCs] Guard has no stat sheet.", plain(staff));
        assertNull(stats.tier(guard, "swords"));
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
