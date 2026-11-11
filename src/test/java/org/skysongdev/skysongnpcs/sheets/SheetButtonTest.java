package org.skysongdev.skysongnpcs.sheets;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NPC menu's Sheet button shows the stat sheet in chat to those who may see it. */
class SheetButtonTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        plugin.fakeSheets().editStat(guard, "strength", StatSheets.StatEdit.SET, 4);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOwnerClicksItAndSeesTheSheetInChat() {
        assertTrue(plugin.menu().buttons().contains("skysongnpcs:sheet"));
        plugin.menu().open(alice, guard);
        ItemStack icon = alice.getOpenInventory().getTopInventory().getItem(SheetButton.SLOT);
        assertEquals(Material.BOOK, icon.getType());
        assertEquals("Stat sheet", PlainTextComponentSerializer.plainText().serialize(icon.getItemMeta().displayName()));

        click(alice, SheetButton.SLOT);

        assertEquals("Guard: strength 4", plain(alice));
        assertEquals("Guard: hp 0", plain(alice));
        assertEquals(InventoryType.CRAFTING, alice.getOpenInventory().getType());
    }

    @Test
    void othersNeedStatsView() {
        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()),
                Set.of(NpcAction.INFO));
        plugin.menu().open(bob, guard);
        Inventory top = bob.getOpenInventory().getTopInventory();
        ItemStack slot = top.getItem(SheetButton.SLOT);
        assertTrue(slot == null || slot.getType() != Material.BOOK);

        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()),
                Set.of(NpcAction.INFO, NpcAction.STATS_VIEW));
        plugin.menu().open(bob, guard);
        assertEquals(Material.BOOK, bob.getOpenInventory().getTopInventory().getItem(SheetButton.SLOT).getType());
    }

    @Test
    void anNpcWhoseSheetIsGoneSaysSo() {
        plugin.menu().open(alice, guard);
        plugin.fakeSheets().delete(guard);

        click(alice, SheetButton.SLOT);

        assertEquals("[NPCs] Guard has no stat sheet.", plain(alice));
    }

    private void click(PlayerMock player, int slot) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
