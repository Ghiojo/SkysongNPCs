package org.skysongdev.skysongnpcs.sheets;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSheetServiceTest {
    private OfflineSkysongNPCs plugin;
    private NpcSheetService sheets;
    private FakeStatSheets stats;
    private UUID guard;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        sheets = plugin.sheets();
        stats = plugin.fakeSheets();
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.server());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsTheSheetFromTheTemplateStatline() {
        sheets.create(guard, template(true, Map.of("strength", 4)));

        assertTrue(sheets.hasSheet(guard));
        assertEquals(4, stats.value(guard, "strength"));
        assertEquals(0, stats.value(guard, "hp"));
    }

    @Test
    void acceptsAliases() {
        sheets.create(guard, Map.of("str", 2));

        assertEquals(2, stats.value(guard, "strength"));
    }

    @Test
    void refusesWhatCannotHaveASheet() {
        UUID plain = plugin.fakeCitizens().add("Plain");
        assertThrows(IllegalArgumentException.class, () -> sheets.create(plain, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> sheets.create(guard, Map.of("luck", 1)));
        assertThrows(IllegalArgumentException.class, () -> sheets.create(guard, template(false, Map.of())));

        sheets.create(guard, Map.of());
        assertThrows(IllegalStateException.class, () -> sheets.create(guard, Map.of()));
    }

    @Test
    void countsItemsOnCreateAndOnlyAgainWhenTheyChange() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        plugin.fakeItems().give(guard, sword);
        sheets.create(guard, Map.of());
        sheets.recountAll(false);

        assertEquals(List.of(List.of(sword)), stats.itemCalls);

        ItemStack shield = new ItemStack(Material.SHIELD);
        plugin.fakeItems().give(guard, sword, shield);
        sheets.recountAll(false);

        assertEquals(List.of(sword, shield), stats.itemCalls.getLast());
        assertEquals(2, stats.itemCalls.size());
    }

    @Test
    void aForcedRecountSendsEverythingAgain() {
        sheets.create(guard, Map.of());
        int before = stats.itemCalls.size();

        sheets.recountAll(true);

        assertEquals(before + 1, stats.itemCalls.size());
    }

    @Test
    void npcsWithoutASheetAreNotCounted() {
        sheets.recountAll(true);

        assertTrue(stats.itemCalls.isEmpty());
    }

    private static NpcTemplate template(boolean sheet, Map<String, Integer> stats) {
        return new NpcTemplate("guard", EntityType.PLAYER, "Guard", null, sheet, stats);
    }
}
