package org.skysongdev.skysongnpcs.inventory;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeNpcContainers;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.inventory.NpcInventories;
import org.skysongdev.skysongnpcs.api.inventory.NpcInventories.Opened;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.ContainerCommand;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NPC inventories and equipment: editors, read-only copies, the menu buttons, the commands and the API. */
class ContainersTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeNpcContainers citizens;
    private CommandRunner commands;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        citizens = plugin.fakeContainers();
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new ContainerCommand(targeting, plugin.containers(), Container.INVENTORY),
                new ContainerCommand(targeting, plugin.containers(), Container.EQUIPMENT));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOwnerGetsCitizensEditors() throws CommandSyntaxException {
        commands.run(alice, "snpc inventory");
        commands.run(alice, "snpc equip");

        assertEquals(List.of("inventory Alice", "equipment Alice"), citizens.editorsOpened());
    }

    @Test
    void viewAccessGetsAReadOnlyCopyOfTheInventory() throws CommandSyntaxException {
        citizens.carry(guard, new ItemStack(Material.BREAD, 3), null, new ItemStack(Material.IRON_SWORD));
        allowBob(NpcAction.INVENTORY_VIEW);

        commands.run(bob, "snpc inventory");

        assertTrue(citizens.editorsOpened().isEmpty());
        Inventory top = bob.getOpenInventory().getTopInventory();
        assertInstanceOf(ReadOnlyView.class, top.getHolder());
        assertEquals(36, top.getSize());
        assertEquals(new ItemStack(Material.BREAD, 3), top.getItem(0));
        assertNull(top.getItem(1));
        assertEquals(Material.IRON_SWORD, top.getItem(2).getType());
        assertEquals("Guard's inventory (view only)", plain(bob.getOpenInventory().title()));
    }

    @Test
    void nothingMovesInOrOutOfACopy() throws CommandSyntaxException {
        citizens.carry(guard, new ItemStack(Material.BREAD));
        allowBob(NpcAction.INVENTORY_VIEW);
        commands.run(bob, "snpc inventory");

        InventoryClickEvent take = new InventoryClickEvent(bob.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        // A shift-click in the player's own inventory, below the copy.
        InventoryClickEvent put = new InventoryClickEvent(bob.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                40, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(take);
        server.getPluginManager().callEvent(put);

        assertTrue(take.isCancelled());
        assertTrue(put.isCancelled());
    }

    @Test
    void theEquipmentCopyGoesFromHeadToHands() throws CommandSyntaxException {
        citizens.wear(guard, EquipmentSlot.HEAD, new ItemStack(Material.IRON_HELMET));
        citizens.wear(guard, EquipmentSlot.HAND, new ItemStack(Material.SHIELD));
        allowBob(NpcAction.EQUIPMENT_VIEW);

        commands.run(bob, "snpc equip");

        Inventory top = bob.getOpenInventory().getTopInventory();
        assertEquals(9, top.getSize());
        assertEquals(Material.IRON_HELMET, top.getItem(0).getType());
        assertNull(top.getItem(1));
        assertEquals(Material.SHIELD, top.getItem(4).getType());
    }

    @Test
    void viewAsksForTheCopyEvenWhenEditingIsAllowed() throws CommandSyntaxException {
        commands.run(alice, "snpc inventory view");

        assertTrue(citizens.editorsOpened().isEmpty());
        assertInstanceOf(ReadOnlyView.class, alice.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    void inventoryAccessDoesNotOpenTheEquipment() throws CommandSyntaxException {
        allowBob(NpcAction.INVENTORY_EDIT);

        commands.run(bob, "snpc equip");
        assertEquals("[NPCs] You may not see Guard's equipment.", plain(bob.nextComponentMessage()));

        commands.run(bob, "snpc inventory");
        assertEquals(List.of("inventory Bob"), citizens.editorsOpened());
    }

    @Test
    void editorsNeedTheNpcPlacedButCopiesDoNot() throws CommandSyntaxException {
        citizens.despawn(guard);

        commands.run(alice, "snpc inventory");
        assertTrue(citizens.editorsOpened().isEmpty());
        assertTrue(plain(alice.nextComponentMessage()).startsWith("[NPCs] Guard is not placed in the world"));

        commands.run(alice, "snpc inventory view");
        assertInstanceOf(ReadOnlyView.class, alice.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    void theMenuShowsEachButtonByAccessAndSaysWhatAClickDoes() {
        assertTrue(plugin.menu().open(alice, guard));
        Inventory ownerMenu = alice.getOpenInventory().getTopInventory();
        assertEquals(Material.CHEST, ownerMenu.getItem(ContainerButtons.INVENTORY_SLOT).getType());
        assertEquals(Material.IRON_CHESTPLATE, ownerMenu.getItem(ContainerButtons.EQUIPMENT_SLOT).getType());
        assertEquals(List.of("Click to change what Guard carries."), lore(ownerMenu.getItem(ContainerButtons.INVENTORY_SLOT)));

        assertFalse(plugin.menu().open(bob, guard));
        allowBob(NpcAction.INVENTORY_VIEW);
        assertTrue(plugin.menu().open(bob, guard));
        Inventory viewerMenu = bob.getOpenInventory().getTopInventory();
        assertEquals(List.of("Click to see what Guard carries."), lore(viewerMenu.getItem(ContainerButtons.INVENTORY_SLOT)));
        assertNull(viewerMenu.getItem(ContainerButtons.EQUIPMENT_SLOT));
    }

    @Test
    void clickingAButtonOpensItOnTheNextTick() {
        plugin.menu().open(alice, guard);
        server.getPluginManager().callEvent(new InventoryClickEvent(alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, ContainerButtons.EQUIPMENT_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(citizens.editorsOpened().isEmpty());

        server.getScheduler().performOneTick();

        assertEquals(List.of("equipment Alice"), citizens.editorsOpened());
    }

    @Test
    void theApiOpensAndReadsThem() {
        NpcInventories api = SkysongNpcsApi.get().inventories();
        citizens.carry(guard, null, new ItemStack(Material.BREAD));
        citizens.wear(guard, EquipmentSlot.FEET, new ItemStack(Material.LEATHER_BOOTS));
        UUID plainNpc = plugin.fakeCitizens().add("Villager");

        assertEquals(Opened.EDITOR, api.openEquipment(alice, guard, false));
        assertEquals(Opened.VIEW, api.openInventory(alice, guard, true));
        assertEquals(Opened.NOT_ALLOWED, api.openInventory(bob, guard, false));
        assertEquals(Opened.NOT_MANAGED, api.openInventory(alice, plainNpc, false));

        List<ItemStack> items = api.inventory(guard).orElseThrow();
        assertEquals(36, items.size());
        assertTrue(items.get(0).isEmpty());
        assertEquals(Material.BREAD, items.get(1).getType());
        assertEquals(Map.of(EquipmentSlot.FEET, new ItemStack(Material.LEATHER_BOOTS)), api.equipment(guard).orElseThrow());
        assertEquals(Optional.empty(), api.inventory(plainNpc));
    }

    private void allowBob(NpcAction action) {
        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()), Set.of(action));
    }

    private static List<String> lore(ItemStack item) {
        return item.getItemMeta().lore().stream().map(ContainersTest::plain).toList();
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
