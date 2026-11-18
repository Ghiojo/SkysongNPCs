package org.skysongdev.skysongnpcs.sheets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.sheet.NpcSheets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lines other plugins add under NPCs' sheets through {@code sheets().registerFooter}. */
class SheetFootersTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private Plugin economy;
    private PlayerMock alice;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        economy = MockBukkit.createMockPlugin("SkysongEconomy");
        alice = server.addPlayer("Alice");
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
    void aRegisteredLineShowsUnderTheSheetFromTheMenuButton() {
        NpcSheets sheets = SkysongNpcsApi.get().sheets();
        assertEquals("skysongeconomy:upkeep", sheets.registerFooter(economy, "upkeep",
                npc -> npc.equals(guard) ? Optional.of(Component.text("Unmaintained")) : Optional.empty()));

        plugin.menu().open(alice, guard);
        server.getPluginManager().callEvent(new InventoryClickEvent(alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, SheetButton.SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals("Guard: strength 4", plain(alice));
        assertEquals("Guard: hp 0", plain(alice));
        assertEquals("Unmaintained", plain(alice));
        assertNull(alice.nextComponentMessage());
    }

    @Test
    void namesAreCheckedAndLinesGoAwayWithTheirPlugin() {
        NpcSheets sheets = SkysongNpcsApi.get().sheets();
        sheets.registerFooter(economy, "upkeep", npc -> Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> sheets.registerFooter(economy, "upkeep", npc -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> sheets.registerFooter(economy, "Up Keep", npc -> Optional.empty()));
        assertTrue(sheets.unregisterFooter(economy, "upkeep"));
        assertFalse(sheets.unregisterFooter(economy, "upkeep"));

        sheets.registerFooter(economy, "upkeep", npc -> Optional.of(Component.text("x")));
        server.getPluginManager().disablePlugin(economy);
        assertFalse(sheets.unregisterFooter(economy, "upkeep"));
    }

    @Test
    void linesKeepTheirOrderAndAFailingOneIsSkipped() {
        SheetFooters footers = new SheetFooters(Logger.getLogger("test"));
        footers.register(economy, "first", npc -> Optional.of(Component.text("1")));
        footers.register(economy, "broken", npc -> {
            throw new IllegalStateException("boom");
        });
        footers.register(economy, "none", npc -> Optional.empty());
        footers.register(economy, "last", npc -> Optional.of(Component.text("2")));

        assertEquals(List.of("skysongeconomy:first", "skysongeconomy:broken", "skysongeconomy:none", "skysongeconomy:last"),
                footers.ids());
        assertEquals(List.of(Component.text("1"), Component.text("2")), footers.lines(guard));
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
