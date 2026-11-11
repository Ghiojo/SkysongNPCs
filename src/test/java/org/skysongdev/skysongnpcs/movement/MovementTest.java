package org.skysongdev.skysongnpcs.movement;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeMover;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.movement.NpcMovement;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.MovementCommands;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.commands.SubCommand;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /snpc select, tphere, follow and look, the menu's Movement button, and the movement API. */
class MovementTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeMover mover;
    private CommandRunner commands;
    private PlayerMock alice;
    private PlayerMock bob;
    private PlayerMock carol;
    private UUID guard;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        mover = plugin.fakeMover();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new MovementCommands(targeting, plugin.citizens(), plugin.access(), plugin.movement(),
                        plugin.configuration()).subCommands().toArray(SubCommand[]::new));

        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        carol = server.addPlayer("Carol");
        alice.teleport(new Location(world, 0, 64, 0));
        bob.teleport(new Location(world, 50, 64, 0));
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        plugin.fakeCharacters().play(carol.getUniqueId(), "Baker");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        mover.place(guard, new Location(world, 10, 64, 10));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // /snpc tphere

    @Test
    void theOwnerBringsTheNpcHere() throws CommandSyntaxException {
        commands.run(alice, "snpc tphere");

        assertEquals("[NPCs] Brought Guard to you.", plain(alice));
        assertEquals(alice.getLocation(), mover.location(guard).orElseThrow());
    }

    @Test
    void someoneWithoutTheActionCannot() throws CommandSyntaxException {
        commands.run(bob, "snpc tphere");

        assertEquals("[NPCs] You may not do that with Guard.", plain(bob));
        assertEquals(10, mover.location(guard).orElseThrow().getX());
    }

    @Test
    void aGrantOfTeleportIsEnough() throws CommandSyntaxException {
        grant(bob, NpcAction.TELEPORT);

        commands.run(bob, "snpc tphere");

        assertEquals("[NPCs] Brought Guard to you.", plain(bob));
        assertEquals(bob.getLocation(), mover.location(guard).orElseThrow());
    }

    @Test
    void aDespawnedNpcCannotBeBrought() throws CommandSyntaxException {
        mover.place(guard, null);

        commands.run(alice, "snpc tphere");

        assertEquals("[NPCs] Guard is not out in the world.", plain(alice));
    }

    @Test
    void configYmlLimitsTheDistanceAndWorlds() throws CommandSyntaxException {
        plugin.configuration().config().set("movement.teleport.max-distance", 5);
        commands.run(alice, "snpc tphere");
        assertEquals("[NPCs] Guard is more than 5 blocks away.", plain(alice));

        plugin.configuration().config().set("movement.teleport.other-worlds", false);
        mover.place(guard, new Location(server.addSimpleWorld("nether"), 0, 64, 0));
        commands.run(alice, "snpc tphere");
        assertEquals("[NPCs] Guard is in another world.", plain(alice));
    }

    @Test
    void staffIgnoreTheLimits() throws CommandSyntaxException {
        carol.addAttachment(plugin, Permissions.ADMIN, true);
        plugin.configuration().config().set("movement.teleport.max-distance", 5);

        commands.run(carol, "snpc tphere");

        assertEquals("[NPCs] Brought Guard to you.", plain(carol));
    }

    // /snpc follow

    @Test
    void followOnFollowsTheSenderAtTheDefaultDistance() throws CommandSyntaxException {
        commands.run(alice, "snpc follow on");

        assertEquals("[NPCs] Guard now follows Alice, staying 3 blocks away.", plain(alice));
        assertEquals(Optional.of(alice.getUniqueId()), mover.following(guard));
        assertEquals(3, mover.followDistance(guard));
    }

    @Test
    void followOnTakesAPlayerAndADistance() throws CommandSyntaxException {
        commands.run(alice, "snpc follow on Bob 6.5");

        assertEquals("[NPCs] Guard now follows Bob, staying 6.5 blocks away.", plain(alice));
        assertEquals(Optional.of(bob.getUniqueId()), mover.following(guard));
        assertEquals(6.5, mover.followDistance(guard));
    }

    @Test
    void followOffStops() throws CommandSyntaxException {
        commands.run(alice, "snpc follow on");
        plain(alice);

        commands.run(alice, "snpc follow off");

        assertEquals("[NPCs] Guard stopped following.", plain(alice));
        assertEquals(Optional.empty(), mover.following(guard));
    }

    @Test
    void followRespectsConfigYml() throws CommandSyntaxException {
        commands.run(alice, "snpc follow on Alice 11");
        assertEquals("[NPCs] NPCs can follow at most 10 blocks away.", plain(alice));

        plugin.configuration().config().set("movement.follow.others", false);
        commands.run(alice, "snpc follow on Bob");
        assertEquals("[NPCs] NPCs can only follow the player who asks.", plain(alice));

        commands.run(alice, "snpc follow on Nobody");
        assertEquals("[NPCs] Nobody is not online.", plain(alice));
        assertEquals(Optional.empty(), mover.following(guard));
    }

    @Test
    void followNeedsTheFollowAction() throws CommandSyntaxException {
        grant(bob, NpcAction.TELEPORT);

        commands.run(bob, "snpc follow on");

        assertEquals("[NPCs] You may not do that with Guard.", plain(bob));
    }

    // /snpc look

    @Test
    void lookTurnsOnWithARangeAndOff() throws CommandSyntaxException {
        commands.run(alice, "snpc look on");
        assertEquals("[NPCs] Guard now looks at players within 5 blocks.", plain(alice));
        assertEquals(5, mover.lookRange(guard));

        commands.run(alice, "snpc look on 8");
        assertEquals(8, mover.lookRange(guard));
        plain(alice);

        commands.run(alice, "snpc look off");
        assertEquals("[NPCs] Guard stopped looking at players.", plain(alice));
        assertFalse(mover.looking(guard));
    }

    @Test
    void lookRangeIsCapped() throws CommandSyntaxException {
        commands.run(alice, "snpc look on 17");

        assertEquals("[NPCs] NPCs can look at most 16 blocks away.", plain(alice));
        assertFalse(mover.looking(guard));
    }

    // /snpc select

    @Test
    void selectKeepsTheNpcAsTheTargetFromAfar() throws CommandSyntaxException {
        grant(bob, NpcAction.LOOK);

        commands.run(bob, "snpc select");
        assertEquals("[NPCs] Selected Guard. NPC commands act on it whenever you are not looking at another NPC.",
                plain(bob));
        assertEquals(Optional.of(guard), plugin.fakeCitizens().selected(bob));

        // Bob looks away: the selection still answers.
        plugin.fakeCitizens().target((UUID) null);
        commands.run(bob, "snpc look on");
        assertTrue(mover.looking(guard));
    }

    @Test
    void selectNeedsSomeAccess() throws CommandSyntaxException {
        commands.run(bob, "snpc select");

        assertEquals("[NPCs] You can't do anything with Guard.", plain(bob));
        assertEquals(Optional.empty(), plugin.fakeCitizens().selected(bob));
    }

    // The menu's Movement button

    @Test
    void theButtonShowsOnlyTheMovesTheViewerMayUse() {
        grant(bob, NpcAction.FOLLOW);

        assertTrue(plugin.menu().open(bob, guard));
        ItemStack button = bob.getOpenInventory().getTopInventory().getItem(MovementButton.SLOT);

        assertEquals(Material.LEAD, button.getType());
        assertEquals("Movement", plain(button.getItemMeta().displayName()));
        assertEquals(List.of("Right-click: follow you (off)"),
                button.getItemMeta().lore().stream().map(MovementTest::plain).toList());
    }

    @Test
    void theButtonIsHiddenWithoutMoves() {
        grant(bob, NpcAction.INFO);

        assertTrue(plugin.menu().open(bob, guard));

        assertNull(bob.getOpenInventory().getTopInventory().getItem(MovementButton.SLOT));
    }

    @Test
    void clicksBringFollowAndLook() {
        plugin.menu().open(alice, guard);

        server.getPluginManager().callEvent(click(alice, ClickType.RIGHT));
        assertEquals(Optional.of(alice.getUniqueId()), mover.following(guard));
        assertEquals("[NPCs] Guard now follows Alice, staying 3 blocks away.", plain(alice));
        assertEquals(List.of("Left-click: bring here", "Right-click: follow you (on)",
                        "Shift-click: look at players (off)"),
                alice.getOpenInventory().getTopInventory().getItem(MovementButton.SLOT)
                        .getItemMeta().lore().stream().map(MovementTest::plain).toList());

        server.getPluginManager().callEvent(click(alice, ClickType.RIGHT));
        assertEquals(Optional.empty(), mover.following(guard));
        plain(alice);

        server.getPluginManager().callEvent(click(alice, ClickType.SHIFT_LEFT));
        assertTrue(mover.looking(guard));
        plain(alice);

        server.getPluginManager().callEvent(click(alice, ClickType.LEFT));
        assertEquals(alice.getLocation(), mover.location(guard).orElseThrow());
        assertEquals("[NPCs] Brought Guard to you.", plain(alice));
    }

    // The API

    @Test
    void theApiMovesManagedNpcsOnly() {
        NpcMovement movement = SkysongNpcsApi.get().movement();
        UUID plain = plugin.fakeCitizens().add("Plain");
        Location there = new Location(world, 100, 70, 100);

        assertTrue(movement.teleport(guard, there));
        assertEquals(there, mover.location(guard).orElseThrow());
        assertFalse(movement.teleport(plain, there));

        assertTrue(movement.follow(guard, bob, 20));
        assertEquals(Optional.of(bob.getUniqueId()), movement.following(guard));
        assertTrue(movement.stopFollowing(guard));
        assertEquals(Optional.empty(), movement.following(guard));
        assertFalse(movement.follow(plain, bob, 2));
        assertThrows(IllegalArgumentException.class, () -> movement.follow(guard, bob, 0.5));

        assertTrue(movement.look(guard, true, 30));
        assertTrue(movement.isLooking(guard));
        assertFalse(movement.isLooking(plain));
        assertEquals(org.skysongdev.skysongnpcs.apiimpl.SkysongNpcsApiImpl.API_VERSION, SkysongNpcsApi.get().apiVersion());
    }

    private void grant(PlayerMock player, NpcAction... actions) {
        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(player.getUniqueId()),
                Set.of(actions));
    }

    private static InventoryClickEvent click(PlayerMock player, ClickType type) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                MovementButton.SLOT, type, InventoryAction.PICKUP_ALL);
    }

    private static String plain(PlayerMock player) {
        return plain(player.nextComponentMessage());
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
