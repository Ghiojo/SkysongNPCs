package org.skysongdev.skysongnpcs.sheets;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every managed NPC has a stat sheet, and one found without is given one back. */
class SheetGuaranteeTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeStatSheets stats;
    private String adoptTemplate;
    private SheetGuarantee guarantee;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        stats = plugin.fakeSheets();
        Path folder = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(folder.resolve("brute.yml"), "stats:\n  str: 5\n");
        Files.writeString(folder.resolve("clerk.yml"), "stats:\n  str: 2\n");
        plugin.templates().reload();
        adoptTemplate = "";
        guarantee = new SheetGuarantee(plugin.registry(), plugin.sheets(), plugin.templates()::get,
                () -> adoptTemplate, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void npcsWithoutASheetGetOneOnRepair() {
        UUID adopted = adopt("Guard");
        UUID made = plugin.factory().create(plugin.templates().get("brute").orElseThrow(), null, NpcOwner.server(),
                new Location(new WorldMock(), 0, 64, 0)).orElseThrow().id();
        UUID fine = adopt("Clerk");
        stats.editStat(fine, "strength", StatSheets.StatEdit.SET, 9);
        // Their SkysongStats characters were deleted by hand, or they were registered before the rule.
        stats.delete(adopted);
        stats.delete(made);

        assertEquals(2, guarantee.repairAll());

        assertEquals(0, stats.value(adopted, "strength"));
        // From the template it was made from.
        assertEquals(5, stats.value(made, "strength"));
        // An NPC that has a sheet keeps it.
        assertEquals(9, stats.value(fine, "strength"));
        assertEquals(0, guarantee.repairAll());
    }

    @Test
    void adoptedNpcsStartFromTheAdoptTemplate() {
        adoptTemplate = "Clerk";
        UUID guard = adopt("Guard");
        stats.delete(guard);

        assertTrue(guarantee.ensure(guard));

        assertEquals(2, stats.value(guard, "strength"));
    }

    @Test
    void anAdoptTemplateThatDoesNotExistStartsEveryStatAtItsStartValue() {
        adoptTemplate = "nope";
        UUID guard = adopt("Guard");
        stats.delete(guard);

        assertTrue(guarantee.ensure(guard));

        assertEquals(0, stats.value(guard, "strength"));
    }

    @Test
    void theAdoptTemplateInConfigIsUsedWhenAdopting() {
        plugin.configuration().config().set("sheets.adopt-template", "brute");

        UUID guard = adopt("Guard");

        assertEquals(5, stats.value(guard, "strength"));
    }

    @Test
    void aSheetThatCannotBeMadeIsLoggedNotThrown() {
        UUID guard = adopt("Guard");
        stats.delete(guard);
        stats.refuseCreate = true;

        assertThrows(IllegalStateException.class, () -> guarantee.ensure(guard));
        assertEquals(0, assertDoesNotThrow(guarantee::repairAll));
        assertFalse(stats.exists(guard));
        assertTrue(plugin.registry().isManaged(guard));
    }

    @Test
    void unmanagedNpcsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> guarantee.ensure(plugin.fakeCitizens().add("Stranger")));
    }

    private UUID adopt(String name) {
        UUID npc = plugin.fakeCitizens().add(name);
        plugin.registry().adopt(npc, NpcOwner.server());
        return npc;
    }
}
