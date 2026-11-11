package org.skysongdev.skysongnpcs.items;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.skysongdev.skysongnpcs.FakeNpcSpawner;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.event.NpcPickupEvent;
import org.skysongdev.skysongnpcs.api.event.NpcPlaceEvent;
import org.skysongdev.skysongnpcs.api.items.NpcItems;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.commands.PickupCommand;
import org.skysongdev.skysongnpcs.commands.ReissueCommand;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Picking NPCs up into items and placing them again, against the running plugin (M18). */
class NpcPickupTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeNpcSpawner spawner;
    private WorldMock world;
    private PlayerMock alice;
    private PlayerMock bob;
    private NpcPlacer placer;
    private NpcPickup pickup;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        spawner = plugin.fakeSpawner();
        pickup = plugin.pickup();
        world = new WorldMock(Material.STONE, 63);
        server.addWorld(world);

        Path folder = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(folder.resolve("guard.yml"), "entity-type: WOLF\nname: Rex\n");
        plugin.templates().reload();

        alice = player("Alice", "Default");
        bob = player("Bob", "Smith");

        placer = new NpcPlacer(plugin.blueprints(), plugin.factory(), plugin.registry(), plugin.characters(),
                spawner, PlaceRegions.ANYWHERE, () -> PlacingSettings.read(plugin.configuration().config()),
                server.getPluginManager(), plugin.getLogger());
        placer.usePickup(pickup);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pickingUpStoresTheNpcAndGivesBackItsBlueprintHoldingIt() {
        UUID npc = placeGuard();

        assertEquals(NpcPickup.Result.PICKED_UP, pickup.pickUp(alice, npc).result());

        assertEquals(NpcState.STORED, plugin.registry().get(npc).orElseThrow().state());
        assertFalse(spawner.spawned().containsKey(npc));
        assertTrue(spawner.despawned().containsKey(npc));
        ItemStack item = onlyItem(alice);
        assertEquals(Material.PAPER, item.getType());
        assertEquals(1, item.getAmount());
        assertEquals("guard", plugin.blueprints().template(item).orElseThrow());
        assertEquals(npc, plugin.blueprints().npc(item).orElseThrow());
        assertTrue(pickup.isCurrent(item));
        assertTrue(lore(item).contains("Owner: Alice (Default)"), lore(item).toString());
    }

    @Test
    void theItemPlacesTheNpcAgainInTheStateItHad() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        ItemStack item = onlyItem(alice);

        NpcPlacer.Outcome outcome = placer.place(alice, item, ground(10, 10));

        assertEquals(NpcPlacer.Result.RETURNED, outcome.result());
        assertEquals(NpcState.SETUP, plugin.registry().get(npc).orElseThrow().state());
        assertEquals(new Location(world, 10.5, 64, 10.5, 180, 0), spawner.spawned().get(npc).location());
        assertEquals(0, item.getAmount());
        // The same NPC came back: nothing new was made.
        assertEquals(1, plugin.registry().all().size());
    }

    @Test
    void aCopyOfTheCurrentItemFindsTheNpcAlreadyPlaced() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        ItemStack item = onlyItem(alice);
        ItemStack copy = item.clone();
        placer.place(alice, item, ground(10, 10));

        NpcPlacer.Outcome outcome = placer.place(alice, copy, ground(20, 20));

        assertEquals(NpcPlacer.Result.ALREADY_PLACED, outcome.result());
        assertEquals(Map.of("npc", pickup.name(npc), "world", world.getName(), "x", "10", "y", "64", "z", "10"),
                outcome.placeholders());
        assertEquals(1, copy.getAmount());
    }

    @Test
    void anItemFromAnEarlierPickupIsStaleAndRefused() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        ItemStack first = onlyItem(alice);
        ItemStack kept = first.clone();
        placer.place(alice, first, ground(10, 10));
        pickup.pickUp(alice, npc);

        NpcPlacer.Outcome outcome = placer.place(alice, kept, ground(20, 20));

        assertEquals(NpcPlacer.Result.STALE_ITEM, outcome.result());
        assertEquals("Alice", outcome.placeholders().get("player"));
        assertEquals(NpcState.STORED, plugin.registry().get(npc).orElseThrow().state());
        assertFalse(pickup.isCurrent(kept));
        assertEquals(1, kept.getAmount());
    }

    @Test
    void onlyTheOwnerOrThePickupActionMayPickUpOrPlace() {
        UUID npc = placeGuard();

        assertEquals(NpcPickup.Result.NOT_ALLOWED, pickup.pickUp(bob, npc).result());
        assertEquals(NpcState.SETUP, plugin.registry().get(npc).orElseThrow().state());

        // The item can be traded, but only the owner or a pickup group may place it.
        pickup.pickUp(alice, npc);
        ItemStack item = onlyItem(alice);
        assertEquals(NpcPlacer.Result.NOT_YOURS, placer.place(bob, item, ground(10, 10)).result());

        plugin.access().grants().set(npc, GrantSource.owner(), AccessGroup.player(bob.getUniqueId(), "Smith"),
                Set.of(NpcAction.PICKUP));
        assertEquals(NpcPlacer.Result.RETURNED, placer.place(bob, item, ground(10, 10)).result());
        assertEquals(NpcPickup.Result.PICKED_UP, pickup.pickUp(bob, npc).result());
        assertEquals(npc, plugin.blueprints().npc(onlyItem(bob)).orElseThrow());
    }

    @Test
    void cancellingThePickupEventKeepsTheNpcOut() {
        UUID npc = placeGuard();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPickup(NpcPickupEvent event) {
                assertEquals(npc, event.getNpc().id());
                event.setCancelled(true);
            }
        }, plugin);

        assertEquals(NpcPickup.Result.CANCELLED, pickup.pickUp(alice, npc).result());
        assertTrue(spawner.spawned().containsKey(npc));
        assertEquals(NpcState.SETUP, plugin.registry().get(npc).orElseThrow().state());
    }

    @Test
    void placingAgainFiresThePlaceEventWithTheNpc() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlace(NpcPlaceEvent event) {
                assertEquals(npc, event.getNpc().orElseThrow());
                assertEquals("guard", event.getTemplate());
                event.setCancelled(true);
            }
        }, plugin);

        assertEquals(NpcPlacer.Result.CANCELLED, placer.place(alice, onlyItem(alice), ground(10, 10)).result());
        assertEquals(NpcState.STORED, plugin.registry().get(npc).orElseThrow().state());
    }

    @Test
    void anNpcWithoutBlueprintComesBackAsTheConfiguredItem() {
        UUID npc = plugin.fakeCitizens().add("Old Tom");
        spawner.spawned().put(npc, new FakeNpcSpawner.Spawned(EntityType.PLAYER, "Old Tom", null,
                new Location(world, 0.5, 64, 0.5)));
        plugin.registry().adopt(npc, NpcOwner.character(alice.getUniqueId(), "Default"));

        assertEquals(NpcPickup.Result.PICKED_UP, pickup.pickUp(alice, npc).result());

        ItemStack item = onlyItem(alice);
        assertEquals(Material.ARMOR_STAND, item.getType());
        assertEquals("Old Tom", PlainTextComponentSerializer.plainText()
                .serialize(Objects.requireNonNull(item.getItemMeta().displayName())));
        assertEquals(NpcPlacer.Result.RETURNED, placer.place(alice, item, ground(5, 5)).result());
        assertEquals(NpcState.PLACED, plugin.registry().get(npc).orElseThrow().state());
    }

    @Test
    void aDeletedNpcsItemPlacesNothing() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        ItemStack item = onlyItem(alice);

        plugin.registry().release(npc);

        assertEquals(NpcPlacer.Result.NPC_GONE, placer.place(alice, item, ground(10, 10)).result());
        assertFalse(pickup.isCurrent(item));
    }

    @Test
    void pickupAndReissueCommands() throws Exception {
        UUID npc = placeGuard();
        PlayerMock staff = player("Staff", "Admin");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        plugin.fakeCitizens().target(npc);
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        CommandRunner commands = new CommandRunner(plugin.configuration(), "test",
                new PickupCommand(targeting, () -> pickup), new ReissueCommand(targeting, () -> pickup, plugin.getLogger()));

        commands.run(alice, "snpc pickup");
        ItemStack lost = onlyItem(alice);
        assertEquals(NpcState.STORED, plugin.registry().get(npc).orElseThrow().state());

        commands.run(staff, "snpc reissue Alice");

        // Alice now holds the old item and the new one; only the new one works.
        List<ItemStack> items = items(alice);
        assertEquals(2, items.size());
        ItemStack fresh = items.stream().filter(pickup::isCurrent).findFirst().orElseThrow();
        assertFalse(pickup.isCurrent(lost));
        assertEquals(NpcPlacer.Result.STALE_ITEM, placer.place(alice, lost, ground(10, 10)).result());
        assertEquals(NpcPlacer.Result.RETURNED, placer.place(alice, fresh, ground(10, 10)).result());

        // A placed NPC is not reissued.
        commands.run(staff, "snpc reissue Alice");
        assertEquals(1, items(alice).size());
    }

    @Test
    void creativePlayersKeepTheItemWhichThenFindsTheNpcPlaced() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        alice.setGameMode(GameMode.CREATIVE);
        ItemStack item = onlyItem(alice);

        assertEquals(NpcPlacer.Result.RETURNED, placer.place(alice, item, ground(10, 10)).result());
        assertEquals(1, item.getAmount());
        assertEquals(NpcPlacer.Result.ALREADY_PLACED, placer.place(alice, item, ground(20, 20)).result());
    }

    @Test
    void theApiPicksUpReissuesAndReadsNpcItems() {
        UUID npc = placeGuard();
        NpcItems items = SkysongNpcsApi.get().items();

        ItemStack first = items.pickUp(npc);
        assertEquals(npc, items.storedNpc(first).orElseThrow());
        assertTrue(items.isCurrent(first));
        assertThrows(IllegalStateException.class, () -> items.pickUp(npc));

        ItemStack second = items.reissue(npc);
        assertFalse(items.isCurrent(first));
        assertTrue(items.isCurrent(second));
        assertTrue(items.storedNpc(new ItemStack(Material.PAPER)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> items.reissue(UUID.randomUUID()));

        placer.place(alice, second, ground(10, 10));
        assertThrows(IllegalStateException.class, () -> items.reissue(npc));
    }

    @Test
    void theMenuShowsPickUpToWhoeverMayPickUp() {
        UUID npc = placeGuard();
        assertTrue(plugin.menu().open(alice, npc));
        ItemStack button = alice.getOpenInventory().getTopInventory().getItem(PickupButton.SLOT);
        assertNotNull(button);
        assertEquals(Material.BUNDLE, button.getType());
        assertFalse(plugin.menu().open(bob, npc));
    }

    @Test
    void staffAreToldAboutStaleItems() {
        UUID npc = placeGuard();
        pickup.pickUp(alice, npc);
        ItemStack kept = onlyItem(alice).clone();
        SkysongNpcsApi.get().items().reissue(npc);
        PlayerMock staff = player("Staff", "Admin");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice.getInventory().setItemInMainHand(kept);

        new NpcPlaceListener(placer, plugin.configuration()).onInteract(new PlayerInteractEvent(alice,
                Action.RIGHT_CLICK_BLOCK, kept, ground(10, 10), BlockFace.UP, EquipmentSlot.HAND));

        String told = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(staff.nextComponentMessage()));
        assertTrue(told.contains("Alice tried to place"), told);
    }

    @Test
    void everyPickupResultHasItsText() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8));
        for (NpcPickup.Result result : NpcPickup.Result.values()) {
            if (result != NpcPickup.Result.CANCELLED && result != NpcPickup.Result.NOT_MANAGED) {
                assertTrue(bundled.isString("pickup." + result.name().toLowerCase(Locale.ROOT).replace('_', '-')),
                        result.name());
            }
        }
        for (String key : List.of("done", "placed", "not-online", "who")) {
            assertTrue(bundled.isString("reissue." + key), key);
        }
    }

    private UUID placeGuard() {
        ItemStack blueprint = plugin.blueprints().make(new ItemStack(Material.PAPER), "guard", null);
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint, ground(0, 0)).result());
        return plugin.registry().all().getLast().id();
    }

    private PlayerMock player(String name, String profile) {
        PlayerMock player = server.addPlayer(name);
        plugin.fakeCharacters().play(player.getUniqueId(), profile);
        // plugin.yml gives everyone skysongnpcs.place; MockBukkit does not apply plugin.yml defaults.
        player.addAttachment(plugin, Permissions.PLACE, true);
        player.teleport(new Location(world, 0.5, 64, 3.5, 0, 0));
        return player;
    }

    private Block ground(int x, int z) {
        return world.getBlockAt(x, 63, z);
    }

    private static List<ItemStack> items(PlayerMock player) {
        return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).toList();
    }

    private static ItemStack onlyItem(PlayerMock player) {
        List<ItemStack> items = items(player);
        assertEquals(1, items.size(), items.toString());
        return items.get(0);
    }

    private static List<String> lore(ItemStack item) {
        return Objects.requireNonNull(item.getItemMeta().lore()).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }
}
