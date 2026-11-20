package org.skysongdev.skysongnpcs.items;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.skysongdev.skysongnpcs.FakeNpcSpawner;
import org.skysongdev.skysongnpcs.FakeSavedItems;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.items.NpcItems;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.commands.PickupItemCommand;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which item NPCs are picked up into: SkysongItems saved items, the blueprint, or the material. */
class PickupItemsTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeNpcSpawner spawner;
    private FakeSavedItems saved;
    private WorldMock world;
    private PlayerMock alice;
    private NpcPlacer placer;
    private NpcPickup pickup;
    private PickupItems items;
    private Path templates;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        spawner = plugin.fakeSpawner();
        saved = plugin.fakeSavedItems();
        pickup = plugin.pickup();
        items = pickup.items();
        world = new WorldMock(Material.STONE, 63);
        server.addWorld(world);

        templates = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(templates.resolve("guard.yml"), "entity-type: WOLF\nname: Rex\n");
        plugin.templates().reload();
        items.templates().reload();

        alice = server.addPlayer("Alice");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        alice.addAttachment(plugin, Permissions.PLACE, true);
        alice.teleport(new Location(world, 0.5, 64, 3.5, 0, 0));

        placer = new NpcPlacer(plugin.blueprints(), plugin.factory(), plugin.registry(), plugin.characters(),
                spawner, PlaceRegions.ANYWHERE, () -> PlacingSettings.read(plugin.configuration().config()),
                server.getPluginManager(), plugin.getLogger());
        placer.usePickup(pickup);

        saved.save("npc_token", named(Material.NAME_TAG, "NPC Token"));
        saved.save("npcs/guard_token", named(Material.BONE, "Guard Token"));
        saved.save("npcs/special", named(Material.EMERALD, "Special"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anAdoptedNpcComesBackAsTheDefaultSavedItemHoldingIt() {
        setDefault("npc_token");
        UUID npc = adopted("Old Tom");

        ItemStack item = pickup.pickUp(npc);

        assertEquals(Material.NAME_TAG, item.getType());
        // The saved item keeps its own name; SkysongNPCs adds the NPC, its ticket and lore.
        assertEquals("NPC Token", name(item));
        assertTrue(lore(item).contains("Holds Old Tom"), lore(item).toString());
        assertEquals(npc, plugin.blueprints().npc(item).orElseThrow());
        assertTrue(pickup.isCurrent(item));
        assertEquals(NpcPlacer.Result.RETURNED, placer.place(alice, item, ground(5, 5)).result());
    }

    @Test
    void theBlueprintBeatsTheDefault() {
        setDefault("npc_token");
        UUID npc = placeGuard();

        assertEquals(new PickupItems.Choice(PickupItems.Source.BLUEPRINT, null), items.choice(record(npc)));
        assertEquals(Material.PAPER, pickup.pickUp(npc).getType());
    }

    @Test
    void theTemplatesItemBeatsTheBlueprint() throws IOException {
        Files.writeString(templates.resolve("guard.yml"), "entity-type: WOLF\nname: Rex\npickup-item: npcs/guard_token\n");
        assertEquals(List.of(), items.templates().reload());
        UUID npc = placeGuard();

        assertEquals(new PickupItems.Choice(PickupItems.Source.TEMPLATE, "npcs/guard_token"), items.choice(record(npc)));
        ItemStack item = pickup.pickUp(npc);
        assertEquals(Material.BONE, item.getType());
        assertEquals(NpcPlacer.Result.RETURNED, placer.place(alice, item, ground(5, 5)).result());
    }

    @Test
    void theNpcsOwnItemBeatsEverything() throws IOException {
        setDefault("npc_token");
        Files.writeString(templates.resolve("guard.yml"), "entity-type: WOLF\nname: Rex\npickup-item: npcs/guard_token\n");
        items.templates().reload();
        UUID npc = placeGuard();

        // Case does not matter when only one saved item matches; the id is kept as SkysongItems has it.
        assertEquals("npcs/special", items.setOwn(npc, "NPCS/Special"));

        assertEquals(Optional.of("npcs/special"), items.own(npc));
        assertEquals(Material.EMERALD, pickup.pickUp(npc).getType());
    }

    @Test
    void anUnknownSavedItemIsRefused() {
        UUID npc = placeGuard();

        assertThrows(IllegalArgumentException.class, () -> items.setOwn(npc, "nothing"));
        assertEquals(Optional.empty(), items.own(npc));
    }

    @Test
    void aSavedItemThatIsGoneIsSkippedDownTheChain() {
        setDefault("npc_token");
        UUID npc = adopted("Old Tom");
        items.setOwn(npc, "npcs/special");
        saved.delete("npcs/special");

        assertEquals(new PickupItems.Choice(PickupItems.Source.DEFAULT, "npc_token"), items.choice(record(npc)));
        assertEquals(Material.NAME_TAG, pickup.pickUp(npc).getType());

        saved.delete("npc_token");
        ItemStack fallback = pickup.reissue(npc);
        assertEquals(Material.ARMOR_STAND, fallback.getType());
        assertEquals("Old Tom", name(fallback));
    }

    @Test
    void reSavingTheItemChangesItemsMadeAfter() {
        UUID npc = adopted("Old Tom");
        items.setOwn(npc, "npc_token");
        ItemStack before = pickup.pickUp(npc);

        saved.save("npc_token", named(Material.GOLD_INGOT, "Shiny Token"));
        ItemStack after = pickup.reissue(npc);

        assertEquals(Material.NAME_TAG, before.getType());
        assertEquals(Material.GOLD_INGOT, after.getType());
        assertEquals("Shiny Token", name(after));
    }

    @Test
    void clearingGoesBackToTheChainAndCopiesKeepTheItem() {
        UUID npc = placeGuard();
        items.setOwn(npc, "npc_token");
        UUID copy = UUID.randomUUID();

        items.copied(npc, copy);
        assertNull(items.setOwn(npc, null));

        assertEquals(Optional.empty(), items.own(npc));
        assertEquals(Optional.of("npc_token"), items.own(copy));
        assertEquals(PickupItems.Source.BLUEPRINT, items.choice(record(npc)).source());
    }

    @Test
    void theCommandShowsSetsClearsAndPreviews() throws CommandSyntaxException {
        UUID npc = placeGuard();
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        plugin.fakeCitizens().target(npc);
        String rex = pickup.name(npc);
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        CommandRunner commands = new CommandRunner(plugin.configuration(), "test",
                new PickupItemCommand(targeting, () -> pickup));

        commands.run(staff, "snpc pickupitem");
        assertEquals("[NPCs] " + rex + " is picked up into - (the blueprint it was placed from).", plain(staff));

        commands.run(staff, "snpc pickupitem set nothing");
        assertEquals("[NPCs] SkysongItems has no saved item nothing. Save one with /si saveitem first.", plain(staff));

        commands.run(staff, "snpc pickupitem set npcs/guard_token");
        assertEquals("[NPCs] " + rex + " is now picked up into npcs/guard_token. Use /snpc reissue to remake its item if it is"
                + " picked up.", plain(staff));
        assertEquals(Optional.of("npcs/guard_token"), items.own(npc));

        commands.run(staff, "snpc pickupitem preview");
        assertEquals("[NPCs] Gave you " + rex + "'s item as it would be made now (set for this NPC). It holds no NPC.", plain(staff));
        List<ItemStack> held = Arrays.stream(staff.getInventory().getContents()).filter(Objects::nonNull).toList();
        assertEquals(1, held.size());
        assertEquals(Material.BONE, held.get(0).getType());
        assertTrue(plugin.blueprints().npc(held.get(0)).isEmpty());

        commands.run(staff, "snpc pickupitem clear");
        assertEquals("[NPCs] " + rex + " no longer has its own item; it is picked up into - (the blueprint it was placed from).",
                plain(staff));
        commands.run(staff, "snpc pickupitem clear");
        assertEquals("[NPCs] " + rex + " has no item of its own; it is picked up into - (the blueprint it was placed from).",
                plain(staff));

        // Staff only.
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc pickupitem set npc_token"));
        assertEquals(Optional.empty(), items.own(npc));
    }

    @Test
    void theApiReadsAndSetsPickupItems() {
        setDefault("npc_token");
        UUID npc = placeGuard();
        NpcItems api = SkysongNpcsApi.get().items();

        assertEquals(Optional.of("npc_token"), api.defaultPickupItem());
        assertEquals(Optional.empty(), api.pickupItem(npc));
        assertEquals(Optional.empty(), api.effectivePickupItem(npc));

        assertEquals("npcs/special", api.setPickupItem(npc, "npcs/special"));
        assertEquals(Optional.of("npcs/special"), api.pickupItem(npc));
        assertEquals(Optional.of("npcs/special"), api.effectivePickupItem(npc));

        assertThrows(IllegalArgumentException.class, () -> api.setPickupItem(npc, "nothing"));
        assertThrows(IllegalArgumentException.class, () -> api.pickupItem(UUID.randomUUID()));
        assertNull(api.setPickupItem(npc, null));
        assertEquals(Optional.empty(), api.pickupItem(npc));
    }

    @Test
    void aTemplateWithAMistakeKeepsItsPreviousItem() throws IOException {
        Files.writeString(templates.resolve("guard.yml"), "entity-type: WOLF\npickup-item: npcs/guard_token\n");
        items.templates().reload();
        Files.writeString(templates.resolve("guard.yml"), "entity-type: WOLF\npickup-item: [a, b]\n");

        assertEquals(List.of("templates/guard.yml"), items.templates().reload());
        assertEquals(Optional.of("npcs/guard_token"), items.templates().of("guard"));
    }

    @Test
    void everySourceAndReplyHasItsText() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8));
        for (PickupItems.Source source : PickupItems.Source.values()) {
            assertTrue(bundled.isString("pickup-item.source." + source.name().toLowerCase(Locale.ROOT)), source.name());
        }
        for (String key : List.of("show", "set", "unknown", "cleared", "not-set", "preview")) {
            assertTrue(bundled.isString("pickup-item." + key), key);
        }
    }

    private void setDefault(String id) {
        plugin.configuration().config().set("pickup.skysong-item", id);
    }

    private UUID placeGuard() {
        ItemStack blueprint = plugin.blueprints().make(new ItemStack(Material.PAPER), "guard", null);
        assertEquals(NpcPlacer.Result.PLACED, placer.place(alice, blueprint, ground(0, 0)).result());
        return plugin.registry().all().getLast().id();
    }

    private UUID adopted(String name) {
        UUID npc = plugin.fakeCitizens().add(name);
        spawner.spawned().put(npc, new FakeNpcSpawner.Spawned(EntityType.PLAYER, name, null,
                new Location(world, 0.5, 64, 0.5)));
        plugin.registry().adopt(npc, NpcOwner.character(alice.getUniqueId(), "Default"));
        return npc;
    }

    private org.skysongdev.skysongnpcs.npc.NpcRecord record(UUID npc) {
        return plugin.registry().get(npc).orElseThrow();
    }

    private Block ground(int x, int z) {
        return world.getBlockAt(x, 63, z);
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.text(name)));
        return item;
    }

    private static String name(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(item.getItemMeta().displayName()));
    }

    private static List<String> lore(ItemStack item) {
        return Objects.requireNonNull(item.getItemMeta().lore()).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(player.nextComponentMessage()));
    }
}
