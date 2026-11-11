package org.skysongdev.skysongnpcs.choices;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.choices.PointGrant;
import org.skysongdev.skysongnpcs.api.event.NpcSetupCompleteEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.items.BlueprintTags;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;
import org.skysongdev.skysongstats.api.CharacterAncestry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.skysongdev.skysongnpcs.choices.ChoiceTestSupport.click;
import static org.skysongdev.skysongnpcs.choices.ChoiceTestSupport.page;
import static org.skysongdev.skysongnpcs.choices.ChoiceTestSupport.slotNamed;

/** A new NPC's first setup, clicked through as its owner: ancestry, then points. */
class SetupFlowTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private PlayerMock alice;
    private Location spot;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        WorldMock world = new WorldMock(Material.STONE, 63);
        server.addWorld(world);
        spot = new Location(world, 0.5, 64, 0.5);
        alice = server.addPlayer("Alice");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");

        Path folder = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(folder.resolve("guard.yml"), "stats:\n  str: 2\npoints: 3\nancestries: [elf, human]\n");
        Files.writeString(folder.resolve("dog.yml"), "points: 1\nancestries: [human]\nallow-other: true\n");
        Files.writeString(folder.resolve("ghost.yml"), "sheet: false\npoints: 4\n");
        plugin.templates().reload();
        plugin.templateChoices().reload();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private UUID make(String template, ItemStack blueprint) {
        NpcTemplate found = plugin.factory().template(template).orElseThrow();
        return plugin.factory().create(found, null, NpcOwner.character(alice.getUniqueId(), "Default"), spot, blueprint)
                .orElseThrow().id();
    }

    @Test
    void theTemplateGivesStartingPointsAndABlueprintOverridesThem() {
        UUID fromTemplate = make("guard", null);
        ItemStack item = new ItemStack(Material.PAPER);
        plugin.fakeItemTags().setDetached(item, BlueprintTags.POINTS, "5");
        UUID fromItem = make("guard", item);
        UUID ghost = make("ghost", null);

        assertEquals(List.of("template"), plugin.statPoints().grants(fromTemplate).stream().map(PointGrant::source).toList());
        assertEquals(3, plugin.statPoints().unspent(fromTemplate));
        assertEquals(5, plugin.statPoints().unspent(fromItem, "blueprint"));
        // No sheet, no points.
        assertEquals(0, plugin.statPoints().unspent(ghost));
    }

    @Test
    void theOwnerPicksAnAncestryWithItsChoicesThenSpendsPoints() {
        UUID npc = make("guard", null);

        assertTrue(plugin.choiceMenu().open(alice, npc));
        Inventory ancestries = page(server, alice);
        click(server, alice, slotNamed(ancestries, "Elf"));
        click(server, alice, slotNamed(ancestries, "Confirm"));
        // Elf's first choice: one of str and con.
        click(server, alice, slotNamed(ancestries, "STR"));
        click(server, alice, slotNamed(ancestries, "Next"));
        // Its second: a package.
        click(server, alice, slotNamed(ancestries, "Cave"));
        click(server, alice, slotNamed(ancestries, "Next"));

        CharacterAncestry ancestry = plugin.fakeEffects().ancestryOf(npc).orElseThrow();
        assertEquals("elf", ancestry.id());
        assertEquals(NpcState.SETUP, record(npc).state());

        Inventory points = page(server, alice);
        int strength = slotNamed(points, "STRENGTH");
        click(server, alice, strength);
        click(server, alice, strength);
        click(server, alice, strength, ClickType.RIGHT);
        click(server, alice, slotNamed(points, "Confirm"));
        server.getScheduler().performOneTick();

        assertEquals(3, plugin.fakeSheets().value(npc, "strength"));
        assertEquals(2, plugin.statPoints().unspent(npc));
        assertEquals(NpcState.PLACED, record(npc).state());
        server.getPluginManager().assertEventFired(NpcSetupCompleteEvent.class,
                event -> event.getNpc().id().equals(npc) && alice.equals(event.getPlayer()));
    }

    @Test
    void otherMeansNoAncestryAndAClosedSetupGoesOnWhereItStopped() {
        UUID npc = make("dog", null);

        plugin.choiceMenu().open(alice, npc);
        Inventory ancestries = page(server, alice);
        click(server, alice, slotNamed(ancestries, "Other"));
        click(server, alice, slotNamed(ancestries, "Confirm"));
        page(server, alice);
        alice.closeInventory();

        assertTrue(plugin.fakeEffects().ancestryOf(npc).isEmpty());
        // Opened again, the setup is on the points step.
        plugin.choiceMenu().open(alice, npc);
        Inventory points = page(server, alice);
        slotNamed(points, "point(s) left");
        assertEquals(NpcState.SETUP, record(npc).state());
    }

    @Test
    void anNpcWithNothingToChooseFinishesItsSetupAtOnce() {
        UUID npc = make("ghost", null);

        assertTrue(plugin.choiceMenu().open(alice, npc));

        assertEquals(NpcState.PLACED, record(npc).state());
    }

    @Test
    void afterSetupTheMenuListsThePointsPage() {
        UUID npc = make("ghost", null);
        plugin.choiceMenu().open(alice, npc);
        plugin.fakeSheets().create(npc, java.util.Map.of());
        plugin.statPoints().grant(npc, "staff", 2, List.of());

        plugin.choiceMenu().open(alice, npc);
        Inventory menu = page(server, alice);
        click(server, alice, slotNamed(menu, "Stat points"));
        Inventory points = page(server, alice);
        click(server, alice, slotNamed(points, "STRENGTH"));
        click(server, alice, slotNamed(points, "Confirm"));

        assertEquals(1, plugin.fakeSheets().value(npc, "strength"));
        assertEquals(1, plugin.choiceMenu().pending(npc));
    }

    private NpcRecord record(UUID npc) {
        return plugin.registry().get(npc).orElseThrow();
    }
}
