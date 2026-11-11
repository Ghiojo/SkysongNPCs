package org.skysongdev.skysongnpcs.appearance;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeAppearanceDriver;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.AppearanceCommands;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.SubCommand;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc name, skin, nameplate, pose, sit, glow}: what they change, and who may. */
class AppearanceCommandsTest {
    private OfflineSkysongNPCs plugin;
    private FakeAppearanceDriver citizens;
    private CommandRunner commands;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        citizens = plugin.fakeAppearance();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        AppearanceMenu menu = new AppearanceMenu(plugin, plugin.registry(), plugin.citizens(), plugin.access(),
                plugin.appearance(), plugin.configuration(), plugin.menu());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new AppearanceCommands(targeting, plugin.access(), plugin.appearance(), menu, plugin.configuration())
                        .subCommands().toArray(SubCommand[]::new));

        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOwnerRenamesTheirNpc() throws CommandSyntaxException {
        commands.run(alice, "snpc name Sir Reginald");

        assertEquals("Sir Reginald", citizens.rawName(guard));
        assertEquals("[NPCs] Renamed Guard to Sir Reginald.", plain(alice));
    }

    @Test
    void someoneWithoutAccessChangesNothing() throws CommandSyntaxException {
        commands.run(bob, "snpc name Thief");
        commands.run(bob, "snpc glow red");

        assertEquals("NPC", citizens.rawName(guard));
        assertEquals(Optional.empty(), citizens.glow(guard));
        assertEquals("[NPCs] You may not change that about Guard.", plain(bob));
    }

    @Test
    void eachChangeNeedsItsOwnAction() throws CommandSyntaxException {
        grant(bob, NpcAction.APPEARANCE);

        commands.run(bob, "snpc pose crouch");
        commands.run(bob, "snpc name Thief");

        assertEquals(NpcPose.CROUCHING, citizens.pose(guard));
        assertEquals("NPC", citizens.rawName(guard));
        assertEquals("[NPCs] Guard is now crouching.", plain(bob));
        assertEquals("[NPCs] You may not change that about Guard.", plain(bob));
    }

    @Test
    void coloursInNamesNeedThePermission() throws CommandSyntaxException {
        commands.run(alice, "snpc name <red>Guard");
        commands.run(alice, "snpc name &cGuard");
        assertEquals("NPC", citizens.rawName(guard));
        assertEquals("[NPCs] You may not use colours or styles in NPC names.", plain(alice));

        alice.addAttachment(plugin, AppearanceCommands.FORMAT_NAMES, true);
        commands.run(alice, "snpc name <red>Guard");

        assertEquals("<red>Guard", citizens.rawName(guard));
    }

    @Test
    void namesHaveAMaximumLength() throws CommandSyntaxException {
        plugin.configuration().config().set("appearance.name.max-length", 5);

        commands.run(alice, "snpc name Reginald");

        assertEquals("NPC", citizens.rawName(guard));
        assertEquals("[NPCs] NPC names can be at most 5 characters long.", plain(alice));
    }

    @Test
    void skinsComeFromPlayerNames() throws CommandSyntaxException {
        commands.run(alice, "snpc skin Notch");

        assertEquals(Optional.of("Notch"), citizens.skin(guard));
        assertEquals("[NPCs] Guard now wears Notch's skin.", plain(alice));
    }

    @Test
    void skinsComeFromLinks() throws CommandSyntaxException {
        commands.run(alice, "snpc skin https://example.com/knight.png slim");

        assertTrue(citizens.slim(guard));
        assertEquals("[NPCs] Getting the skin for Guard, this takes a few seconds...", plain(alice));
        assertEquals("[NPCs] Guard now wears the skin from that link.", plain(alice));
    }

    @Test
    void aBrokenLinkSaysWhy() throws CommandSyntaxException {
        commands.run(alice, "snpc skin " + FakeAppearanceDriver.BROKEN_URL);

        plain(alice);
        assertEquals("[NPCs] Could not get a skin from that link for Guard: MineSkin gave no skin for that link",
                plain(alice));
    }

    @Test
    void onlyPlayerShapedNpcsHaveSkins() throws CommandSyntaxException {
        citizens.notPlayerShaped(guard);

        commands.run(alice, "snpc skin Notch");

        assertEquals(Optional.empty(), citizens.skin(guard));
        assertEquals("[NPCs] Guard is not player-shaped, so it has no skin.", plain(alice));
    }

    @Test
    void nameplatesShowHideOrShowOnHover() throws CommandSyntaxException {
        commands.run(alice, "snpc nameplate hover");

        assertEquals(Nameplate.HOVER, citizens.nameplate(guard));
        assertEquals("[NPCs] Guard's name is now shown when looked at.", plain(alice));
    }

    @Test
    void sittingAndLyingNeverHappenAtOnce() throws CommandSyntaxException {
        commands.run(alice, "snpc sit on");
        commands.run(alice, "snpc pose lie");
        assertFalse(citizens.sitting(guard));
        assertEquals(NpcPose.LYING, citizens.pose(guard));

        commands.run(alice, "snpc sit on");
        assertTrue(citizens.sitting(guard));
        assertEquals(NpcPose.STANDING, citizens.pose(guard));
    }

    @Test
    void glowingTakesAColourOrOff() throws CommandSyntaxException {
        commands.run(alice, "snpc glow dark_red");
        assertEquals(Optional.of(NamedTextColor.DARK_RED), citizens.glow(guard));
        assertEquals("[NPCs] Guard now glows dark red.", plain(alice));

        commands.run(alice, "snpc glow pink");
        assertEquals(Optional.of(NamedTextColor.DARK_RED), citizens.glow(guard));
        assertTrue(plain(alice).startsWith("[NPCs] pink is not a colour."));

        commands.run(alice, "snpc glow off");
        assertEquals(Optional.empty(), citizens.glow(guard));
    }

    @Test
    void staffChangeAnyNpc() throws CommandSyntaxException {
        PlayerMock staff = MockBukkit.getMock().addPlayer("Staff");
        staff.setOp(true);

        commands.run(staff, "snpc nameplate hide");

        assertEquals(Nameplate.HIDDEN, citizens.nameplate(guard));
    }

    private void grant(PlayerMock player, NpcAction action) {
        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(player.getUniqueId()), Set.of(action));
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
