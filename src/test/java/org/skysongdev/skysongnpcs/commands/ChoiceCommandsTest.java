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
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.choices.NpcChoices;
import org.skysongdev.skysongnpcs.api.choices.SpendResult;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc choose}, {@code /snpc points}, {@code /snpc respec} and the choices API. */
class ChoiceCommandsTest {
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
                new ChooseCommand(targeting, plugin.access(), plugin.choiceMenu()),
                new PointsCommand(targeting, plugin.statPoints()),
                new RespecCommand(targeting, plugin.choiceMenu()));
        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        plugin.fakeSheets().create(guard, Map.of("strength", 1));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void staffGrantListAndTakePoints() throws CommandSyntaxException {
        commands.run(staff, "snpc points grant 3 str");
        assertEquals("[NPCs] Gave Guard 3 stat point(s).", plain(staff));

        commands.run(staff, "snpc points list");
        assertEquals("[NPCs] Guard's stat points (3 unspent):", plain(staff));
        assertEquals(" - staff: 0/3 spent, on strength", plain(staff));

        commands.run(staff, "snpc points take 5");
        assertEquals("[NPCs] Took 3 unspent staff point(s) from Guard.", plain(staff));

        commands.run(staff, "snpc points grant 1 luck");
        assertEquals("[NPCs] Not every stat in luck is a SkysongStats stat.", plain(staff));
    }

    @Test
    void respecGivesBackSpentPoints() throws CommandSyntaxException {
        plugin.statPoints().grant(guard, "staff", 2, List.of());
        plugin.statPoints().spend(guard, "str", 2, null);

        commands.run(staff, "snpc respec");

        assertEquals("[NPCs] Gave back 2 choice(s) of Guard.", plain(staff));
        assertEquals(1, plugin.fakeSheets().value(guard, "strength"));
        assertEquals(2, plugin.statPoints().unspent(guard));
    }

    @Test
    void onlyStaffEditPoints() {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc points grant 3"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc respec"));
    }

    @Test
    void chooseNeedsTheChooseAction() throws CommandSyntaxException {
        commands.run(bob, "snpc choose");
        assertEquals("[NPCs] You may not make Guard's choices.", plain(bob));

        commands.run(alice, "snpc choose");
        server.getScheduler().performOneTick();
        assertInstanceOf(ChoiceGui.class, alice.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    void theApiGrantsSpendsAndTakesBack() {
        NpcChoices choices = SkysongNpcsApi.get().choices();

        choices.grantPoints(guard, "economy:smithy", 10, List.of());
        assertEquals(SpendResult.DONE, choices.spendPoints(guard, "strength", 1));
        assertEquals(SpendResult.ABOVE_CAP, choices.spendPoints(guard, "strength", 9));

        assertEquals(9, choices.unspentPoints(guard, "economy:smithy"));
        assertEquals(9, choices.takeUnspentPoints(guard, "economy:smithy", 10));
        assertEquals(0, choices.unspentPoints(guard));
        assertEquals(1, choices.pointGrants(guard).size());
        assertFalse(choices.inSetup(guard));
        assertEquals(1, choices.respec(guard));
        assertTrue(choices.open(alice, guard));
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
