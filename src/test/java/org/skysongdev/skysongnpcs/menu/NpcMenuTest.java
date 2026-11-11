package org.skysongdev.skysongnpcs.menu;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.MenuCommand;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NPC menu: who gets it, what it shows, menus.yml, other plugins' buttons and /snpc menu. */
class NpcMenuTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private NpcMenuService menu;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        menu = plugin.menu();
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
    void theOwnerSeesTheInformationButton() {
        assertTrue(menu.open(alice, guard));

        Inventory top = alice.getOpenInventory().getTopInventory();
        assertEquals(27, top.getSize());
        ItemStack info = top.getItem(InfoButton.SLOT);
        assertEquals(Material.NAME_TAG, info.getType());
        assertEquals("Guard", plain(info.getItemMeta().displayName()));
        assertEquals(List.of("Owner: Alice (Default)", "Template: none", "State: placed"),
                info.getItemMeta().lore().stream().map(NpcMenuTest::plain).toList());
    }

    @Test
    void someoneWithNoAccessGetsNoMenu() {
        assertFalse(menu.open(bob, guard));

        assertEquals(InventoryType.CRAFTING, bob.getOpenInventory().getType());
    }

    @Test
    void aPlainCitizensNpcHasNoMenu() {
        UUID plain = plugin.fakeCitizens().add("Villager");

        assertFalse(menu.open(alice, plain));
    }

    @Test
    void clicksNeverTakeAnythingOutOfTheMenu() {
        menu.open(alice, guard);
        InventoryClickEvent click = click(alice, InfoButton.SLOT);

        server.getPluginManager().callEvent(click);

        assertTrue(click.isCancelled());
    }

    @Test
    void menusYmlMovesRecoloursAndHidesButtons() {
        plugin.configuration().menus().set("npc-menu.rows", 1);
        plugin.configuration().menus().set("npc-menu.buttons.skysongnpcs:info.slot", 4);
        plugin.configuration().menus().set("npc-menu.buttons.skysongnpcs:info.material", "PAPER");

        menu.open(alice, guard);
        Inventory top = alice.getOpenInventory().getTopInventory();
        assertEquals(9, top.getSize());
        assertEquals(Material.PAPER, top.getItem(4).getType());

        alice.closeInventory();
        // Every button hidden, the Information button and those later milestones add.
        menu.buttons().forEach(id -> plugin.configuration().menus().set("npc-menu.buttons." + id + ".hidden", true));
        assertFalse(menu.open(alice, guard));
    }

    @Test
    void anotherPluginsButtonShowsAndGetsClicks() {
        Plugin economy = MockBukkit.createMockPlugin("SkysongEconomy");
        AtomicInteger clicks = new AtomicInteger();
        String id = menu.registerButton(economy, "payroll", 10, context -> new ItemStack(Material.GOLD_INGOT),
                context -> context.viewer().equals(bob), click -> clicks.incrementAndGet());

        assertEquals("skysongeconomy:payroll", id);
        // SkysongNPCs' own buttons come first, the Information button leading.
        List<String> ids = menu.buttons();
        assertEquals("skysongnpcs:info", ids.get(0));
        assertEquals("skysongeconomy:payroll", ids.get(ids.size() - 1));
        // Bob has no access, but the economy button lets him in, and it is all he sees.
        assertTrue(menu.open(bob, guard));
        Inventory top = bob.getOpenInventory().getTopInventory();
        assertEquals(Material.GOLD_INGOT, top.getItem(10).getType());
        assertNull(top.getItem(InfoButton.SLOT));

        server.getPluginManager().callEvent(click(bob, 10));
        assertEquals(1, clicks.get());
    }

    @Test
    void aPluginsButtonsGoWhenItDisables() {
        Plugin economy = MockBukkit.createMockPlugin("SkysongEconomy");
        menu.registerButton(economy, "payroll", 10, context -> new ItemStack(Material.GOLD_INGOT),
                context -> true, click -> { });

        server.getPluginManager().disablePlugin(economy);

        assertTrue(menu.buttons().stream().allMatch(id -> id.startsWith("skysongnpcs:")));
    }

    @Test
    void buttonNamesAreChecked() {
        Plugin economy = MockBukkit.createMockPlugin("SkysongEconomy");
        menu.registerButton(economy, "payroll", 0, context -> null, context -> true, click -> { });

        assertThrows(IllegalArgumentException.class, () -> menu.registerButton(economy, "payroll", 0,
                context -> null, context -> true, click -> { }));
        assertThrows(IllegalArgumentException.class, () -> menu.registerButton(economy, "Pay.Roll", 0,
                context -> null, context -> true, click -> { }));
    }

    @Test
    void snpcMenuOpensItOrSaysThereIsNothingToDo() throws CommandSyntaxException {
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        CommandRunner commands = new CommandRunner(plugin.configuration(), "1.0.0", new MenuCommand(targeting, menu));

        commands.run(alice, "snpc menu");
        assertEquals(InventoryType.CHEST, alice.getOpenInventory().getType());

        commands.run(bob, "snpc menu");
        assertEquals("[NPCs] You can't do anything with Guard.",
                PlainTextComponentSerializer.plainText().serialize(bob.nextComponentMessage()));
    }

    private static InventoryClickEvent click(PlayerMock player, int slot) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
