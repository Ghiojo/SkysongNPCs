package org.skysongdev.skysongnpcs.items;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.skysongdev.skysongnpcs.FakeNpcSpawner;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.event.NpcPlaceEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placing NPCs from blueprint items: {@link NpcPlacer} against the running plugin. */
class NpcPlacerTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeNpcSpawner spawner;
    private WorldMock world;
    private PlayerMock alice;
    private PermissionAttachment placing;
    private boolean regionsAllow = true;
    private NpcPlacer placer;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        spawner = plugin.fakeSpawner();
        // WorldMock fills up to its height, inclusive: stone up to y 63, air from y 64.
        world = new WorldMock(Material.STONE, 63);
        server.addWorld(world);

        Path folder = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(folder.resolve("guard.yml"), "entity-type: WOLF\nname: Rex\nstats:\n  str: 5\n");
        Files.writeString(folder.resolve("ghost.yml"), "name: Boo\nsheet: false\n");
        plugin.templates().reload();

        alice = server.addPlayer("Alice");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        // plugin.yml gives everyone skysongnpcs.place; MockBukkit does not apply plugin.yml defaults.
        placing = alice.addAttachment(plugin, Permissions.PLACE, true);
        alice.teleport(new Location(world, 0.5, 64, 3.5, 0, 0));

        placer = new NpcPlacer(plugin.blueprints(), plugin.factory(), plugin.registry(), plugin.characters(),
                spawner, (player, location) -> regionsAllow,
                () -> PlacingSettings.read(plugin.configuration().config()), server.getPluginManager(),
                plugin.getLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void placesTheNpcForTheActiveCharacterFacingThePlayerAndUsesUpOneItem() {
        ItemStack item = blueprint("guard", null, 2);

        NpcPlacer.Outcome outcome = placer.place(alice, item, ground(0, 0));

        assertEquals(new NpcPlacer.Outcome(NpcPlacer.Result.PLACED, Map.of("npc", "Rex")), outcome);
        UUID npc = onlyNpc();
        NpcRecord record = plugin.registry().get(npc).orElseThrow();
        assertEquals(NpcOwner.character(alice.getUniqueId(), "Default"), record.owner());
        assertEquals(NpcState.SETUP, record.state());
        assertEquals("guard", record.templateId());
        FakeNpcSpawner.Spawned spawned = spawner.spawned().get(npc);
        assertEquals(EntityType.WOLF, spawned.type());
        assertEquals("Rex", spawned.name());
        assertEquals(new Location(world, 0.5, 64, 0.5, 180, 0), spawned.location());
        assertEquals(5, plugin.fakeSheets().value(npc, "strength"));
        assertEquals(1, item.getAmount());
    }

    @Test
    void creativePlayersKeepTheItem() {
        alice.setGameMode(GameMode.CREATIVE);
        ItemStack item = blueprint("guard", null, 1);

        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, item, ground(0, 0)).result());

        assertEquals(1, item.getAmount());
    }

    @Test
    void theNameTagNamesTheNpc() {
        NpcPlacer.Outcome outcome = placer.place(alice, blueprint("guard", "Fido", 1), ground(0, 0));

        assertEquals(Map.of("npc", "Fido"), outcome.placeholders());
        assertEquals("Fido", spawner.spawned().get(onlyNpc()).name());
    }

    @Test
    void theOldSheetFalseKeyStillGivesASheet() {
        // Every Skysong NPC has a stat sheet, whatever the template says.
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint("ghost", null, 1), ground(0, 0)).result());

        assertTrue(plugin.fakeSheets().exists(onlyNpc()));
    }

    @Test
    void otherItemsAreLeftAlone() {
        ItemStack paper = new ItemStack(Material.PAPER);

        assertEquals(NpcPlacer.Result.NOT_A_BLUEPRINT, placer.place(alice, paper, ground(0, 0)).result());
        assertEquals(NpcPlacer.Result.NOT_A_BLUEPRINT, placer.place(alice, null, ground(0, 0)).result());
        assertTrue(spawner.spawned().isEmpty());
    }

    @Test
    void refusalsKeepTheItemAndPlaceNothing() {
        ItemStack unknown = blueprint("gone", null, 1);
        assertRefused(NpcPlacer.Result.UNKNOWN_TEMPLATE, unknown, alice);

        PlayerMock bob = server.addPlayer("Bob");
        bob.addAttachment(plugin, Permissions.PLACE, true);
        bob.teleport(alice.getLocation());
        assertRefused(NpcPlacer.Result.NO_CHARACTER, blueprint("guard", null, 1), bob);

        alice.removeAttachment(placing);
        assertRefused(NpcPlacer.Result.NO_PERMISSION, blueprint("guard", null, 1), alice);
    }

    @Test
    void theNpcNeedsTwoFreeBlocks() {
        world.getBlockAt(0, 65, 0).setType(Material.STONE);

        assertRefused(NpcPlacer.Result.NO_SPACE, blueprint("guard", null, 1), alice);
    }

    @Test
    void regionsCanForbidPlacing() {
        regionsAllow = false;

        assertRefused(NpcPlacer.Result.NO_ACCESS_HERE, blueprint("guard", null, 1), alice);
    }

    @Test
    void aRefusedSpawnPlacesNothing() {
        spawner.refuseSpawns(true);

        assertRefused(NpcPlacer.Result.NO_ACCESS_HERE, blueprint("guard", null, 1), alice);
        assertTrue(plugin.registry().all().isEmpty());
    }

    @Test
    void cancellingTheEventPlacesNothing() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlace(NpcPlaceEvent event) {
                assertEquals("guard", event.getTemplate());
                event.setCancelled(true);
            }
        }, plugin);

        assertRefused(NpcPlacer.Result.CANCELLED, blueprint("guard", null, 1), alice);
    }

    @Test
    void theCharacterLimitCountsNpcsOutInTheWorld() {
        plugin.configuration().config().set("placing.limits.per-character", 1);
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint("guard", null, 1), ground(0, 0)).result());

        NpcPlacer.Outcome second = placer.place(alice, blueprint("guard", null, 1), ground(40, 40));
        assertEquals(new NpcPlacer.Outcome(NpcPlacer.Result.CHARACTER_LIMIT, Map.of("limit", "1")), second);

        // Staff are not limited.
        alice.addAttachment(plugin, Permissions.ADMIN, true);
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint("guard", null, 1), ground(40, 40)).result());
    }

    @Test
    void theChunkLimitCountsSkysongNpcsInTheChunk() {
        plugin.configuration().config().set("placing.limits.per-chunk", 1);
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint("guard", null, 1), ground(0, 0)).result());

        NpcPlacer.Outcome second = placer.place(alice, blueprint("guard", null, 1), ground(3, 3));
        assertEquals(new NpcPlacer.Outcome(NpcPlacer.Result.CHUNK_LIMIT, Map.of("limit", "1")), second);
        // Another chunk has room.
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint("guard", null, 1), ground(20, 0)).result());
    }

    @Test
    void facingSnapsToEightDirections() {
        assertEquals(180f, NpcPlacer.facing(0f));
        assertEquals(0f, NpcPlacer.facing(180f));
        assertEquals(225f, NpcPlacer.facing(50f));
        assertEquals(90f, NpcPlacer.facing(-100f));
    }

    private void assertRefused(NpcPlacer.Result expected, ItemStack item, PlayerMock player) {
        int before = spawner.spawned().size();

        assertEquals(expected, placer.place(player, item, ground(0, 0)).result());

        assertEquals(1, item.getAmount());
        assertEquals(before, spawner.spawned().size());
    }

    private ItemStack blueprint(String template, String name, int amount) {
        ItemStack item = plugin.blueprints().make(new ItemStack(Material.PAPER), template, name);
        item.setAmount(amount);
        return item;
    }

    private Block ground(int x, int z) {
        return world.getBlockAt(x, 63, z);
    }

    private UUID onlyNpc() {
        List<UUID> npcs = List.copyOf(spawner.spawned().keySet());
        assertEquals(1, npcs.size());
        return npcs.get(0);
    }
}
