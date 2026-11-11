package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeSheetEffects;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongstats.api.CharacterAncestry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc injury}, {@code /snpc condition} and {@code /snpc ancestry}. */
class EffectCommandsTest {
    private OfflineSkysongNPCs plugin;
    private FakeSheetEffects effects;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock dm;
    private PlayerMock alice;
    private UUID guard;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        effects = plugin.fakeEffects();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new InjuryCommand(targeting, plugin.sheets(), effects),
                new ConditionCommand(targeting, plugin.sheets(), effects, plugin.configuration()),
                new AncestryCommand(targeting, plugin.sheets(), effects, plugin.configuration()));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        dm = server.addPlayer("Dm");
        dm.addAttachment(plugin, Permissions.INJURIES, true);
        alice = server.addPlayer("Alice");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void injuriesAndConditionsNeedTheDmPermissionAndAncestryNeedsStaff() throws CommandSyntaxException {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc injury award bruise"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc condition award tired"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(dm, "snpc ancestry set human"));

        commands.run(dm, "snpc injury award bruise");
        assertEquals("[NPCs] Guard now has Bruise.", plain(dm));
        commands.run(staff, "snpc condition award tired");
        assertEquals("[NPCs] Guard now has Tired at level 1.", plain(staff));
    }

    @Test
    void dmsAwardListAndRemoveInjuries() throws CommandSyntaxException {
        commands.run(dm, "snpc injury");
        assertEquals("[NPCs] Guard has no injuries.", plain(dm));

        commands.run(dm, "snpc injury award Broken_Arm");
        assertEquals("[NPCs] Guard now has Broken Arm.", plain(dm));
        commands.run(dm, "snpc injury list");
        assertEquals("[NPCs] Guard's injuries:", plain(dm));
        assertEquals(" - Broken Arm (major)", plain(dm));
        assertEquals("[NPCs] Guard knows: Broken Arm", plain(dm));

        commands.run(dm, "snpc injury forget broken_arm");
        assertEquals("[NPCs] Guard does not know Broken Arm, or has it now.", plain(dm));
        commands.run(dm, "snpc injury remove broken_arm");
        assertEquals("[NPCs] Guard no longer has Broken Arm.", plain(dm));
        commands.run(dm, "snpc injury remove broken_arm");
        assertEquals("[NPCs] Guard does not have Broken Arm.", plain(dm));
        commands.run(dm, "snpc injury forget broken_arm");
        assertEquals("[NPCs] Guard no longer knows Broken Arm.", plain(dm));
        assertTrue(effects.knownInjuries(guard).isEmpty());
    }

    @Test
    void dmsDiscoverInjuriesAndUnknownOnesAreRefused() throws CommandSyntaxException {
        commands.run(dm, "snpc injury discover bruise");
        assertEquals("[NPCs] Guard now knows Bruise.", plain(dm));
        commands.run(dm, "snpc injury discover bruise");
        assertEquals("[NPCs] Guard already knows Bruise.", plain(dm));
        assertTrue(effects.injuries(guard).isEmpty());

        commands.run(dm, "snpc injury award plague");
        assertEquals("[NPCs] There is no injury called plague.", plain(dm));
    }

    @Test
    void conditionsShowWhereTheyComeFrom() throws CommandSyntaxException {
        commands.run(dm, "snpc condition");
        assertEquals("[NPCs] Guard has no conditions.", plain(dm));

        commands.run(dm, "snpc injury award broken_arm");
        plain(dm);
        commands.run(dm, "snpc condition award bleeding 1");
        assertEquals("[NPCs] Guard now has Bleeding at level 2.", plain(dm));
        commands.run(dm, "snpc condition award tired 3");
        plain(dm);

        commands.run(dm, "snpc condition list");
        assertEquals("[NPCs] Guard's conditions:", plain(dm));
        assertEquals(" - Bleeding 2 (given directly, Broken Arm)", plain(dm));
        assertEquals(" - Tired 3 (given directly)", plain(dm));
    }

    @Test
    void onlyDirectConditionsAreRemoved() throws CommandSyntaxException {
        commands.run(dm, "snpc injury award broken_arm");
        plain(dm);
        commands.run(dm, "snpc condition remove bleeding");
        assertEquals("[NPCs] Guard was not given Bleeding directly. If an injury gives it, remove the injury.", plain(dm));

        commands.run(dm, "snpc condition award tired");
        plain(dm);
        commands.run(dm, "snpc condition remove tired");
        assertEquals("[NPCs] Took back Tired from Guard.", plain(dm));
        commands.run(dm, "snpc condition remove fever");
        assertEquals("[NPCs] There is no condition called fever.", plain(dm));
        assertThrows(CommandSyntaxException.class, () -> commands.run(dm, "snpc condition award tired 0"));
    }

    @Test
    void anNpcWithoutASheetIsRefused() throws CommandSyntaxException {
        UUID dog = plugin.fakeCitizens().add("Dog");
        plugin.fakeCitizens().target(dog);
        plugin.registry().adopt(dog, NpcOwner.server());
        // Its SkysongStats character was deleted by hand.
        plugin.fakeSheets().delete(dog);

        commands.run(dm, "snpc injury award bruise");
        assertEquals("[NPCs] Dog has no stat sheet.", plain(dm));
        commands.run(staff, "snpc ancestry");
        assertEquals("[NPCs] Dog has no stat sheet.", plain(staff));
        assertTrue(effects.injuries(dog).isEmpty());
    }

    @Test
    void staffSetAnAncestryWithoutChoices() throws CommandSyntaxException {
        commands.run(staff, "snpc ancestry");
        assertEquals("[NPCs] Guard has no ancestry.", plain(staff));

        commands.run(staff, "snpc ancestry set human");
        assertEquals("[NPCs] Guard is now Human.", plain(staff));
        commands.run(staff, "snpc ancestry");
        assertEquals("[NPCs] Guard's ancestry is Human, which gave: str +1", plain(staff));

        commands.run(staff, "snpc ancestry rechoose");
        assertEquals("[NPCs] Guard has no ancestry with choices.", plain(staff));
        commands.run(staff, "snpc ancestry remove");
        assertEquals("[NPCs] Guard no longer has an ancestry.", plain(staff));
        commands.run(staff, "snpc ancestry remove");
        assertEquals("[NPCs] Guard has no ancestry.", plain(staff));
    }

    @Test
    void staffSetAnAncestryWithPicks() throws CommandSyntaxException {
        commands.run(staff, "snpc ancestry set elf 1=con 2=1");

        assertEquals("[NPCs] Guard is now Elf.", plain(staff));
        CharacterAncestry elf = effects.ancestryOf(guard).orElseThrow();
        assertFalse(elf.choicesPending());
        assertEquals(1, elf.total("con"));
        assertEquals(1, elf.total("wis"));
        commands.run(staff, "snpc ancestry");
        assertEquals("[NPCs] Guard's ancestry is Elf, which gave: dex +2, con +1, wis +1", plain(staff));
    }

    @Test
    void choicesCanBeLeftPendingThenMade() throws CommandSyntaxException {
        commands.run(staff, "snpc ancestry choose 1=str");
        assertEquals("[NPCs] Guard has no ancestry choices to make.", plain(staff));

        commands.run(staff, "snpc ancestry set elf");
        assertEquals("[NPCs] Guard is now Elf. Make its choices with /snpc ancestry choose (see /snpc ancestry info elf).",
                plain(staff));
        commands.run(staff, "snpc ancestry");
        plain(staff);
        assertEquals("[NPCs] Its choices are still to be made: /snpc ancestry info elf, then /snpc ancestry choose.",
                plain(staff));

        commands.run(staff, "snpc ancestry choose 1=str 2=2");
        assertEquals("[NPCs] Made Guard's Elf choices.", plain(staff));
        assertEquals(2, effects.ancestryOf(guard).orElseThrow().total("con") + effects.ancestryOf(guard).orElseThrow().total("str"));

        commands.run(staff, "snpc ancestry rechoose");
        assertEquals("[NPCs] Guard's ancestry choices are taken off; make them again with /snpc ancestry choose.",
                plain(staff));
        assertTrue(effects.ancestryOf(guard).orElseThrow().choicesPending());
    }

    @Test
    void picksThatDoNotFitChangeNothing() throws CommandSyntaxException {
        commands.run(staff, "snpc ancestry set elf 1=dex 2=1");
        assertEquals("[NPCs] Those picks don't fit: choice 1 needs 1 of [str, con]. See /snpc ancestry info elf.",
                plain(staff));
        commands.run(staff, "snpc ancestry set elf 1=str 2=3");
        assertEquals("[NPCs] Those picks don't fit: Choice 2 has 2 package(s), not 3. See /snpc ancestry info elf.",
                plain(staff));
        commands.run(staff, "snpc ancestry set elf banana");
        assertEquals("[NPCs] Those picks don't fit: 'banana' is not <choice>=<pick>. See /snpc ancestry info elf.",
                plain(staff));
        assertTrue(effects.ancestryOf(guard).isEmpty());

        commands.run(staff, "snpc ancestry set dwarf");
        assertEquals("[NPCs] There is no ancestry called dwarf.", plain(staff));
    }

    @Test
    void infoListsTheNumberedChoices() throws CommandSyntaxException {
        commands.run(staff, "snpc ancestry info elf");

        assertEquals("[NPCs] Elf gives dex +2.", plain(staff));
        assertEquals(" 1= Boost: +1 to 1 stat(s) from str, con", plain(staff));
        assertEquals(" 2= Heritage: one of 1) Woodland (wis +1), 2) Cave (con +1)", plain(staff));

        commands.run(staff, "snpc ancestry info human");
        assertEquals("[NPCs] Human gives str +1.", plain(staff));
        assertEquals(" It has no choices.", plain(staff));
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
