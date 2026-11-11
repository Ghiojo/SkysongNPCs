package org.skysongdev.skysongnpcs.wardrobe;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeWardrobe;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.WardrobeCommand;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NPC wardrobes: who dresses, who only looks, picked-up NPCs, the menu button and /snpc wardrobe. */
class NpcWardrobesTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private NpcWardrobes wardrobes;
    private FakeWardrobe wardrobe;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        wardrobes = plugin.wardrobes();
        wardrobe = plugin.fakeWardrobe();
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
    void theOwnerDressesTheNpcInSkysongWardrobe() {
        assertTrue(wardrobes.open(alice, record(), false));

        assertEquals(List.of(guard), wardrobe.opened());
        assertNull(wardrobe.lastOnClose());
        assertTrue(wardrobes.mayDress(alice, guard));
    }

    @Test
    void wardrobeViewShowsAReadOnlyCopyOfWhatItWears() {
        grant(NpcAction.WARDROBE_VIEW);
        wardrobe.wear(guard, "hat", new ItemStack(Material.LEATHER_HELMET));
        wardrobe.wear(guard, "cloak", new ItemStack(Material.BLACK_BANNER));

        assertTrue(wardrobes.open(bob, record(), false));

        assertTrue(wardrobe.opened().isEmpty());
        Inventory top = bob.getOpenInventory().getTopInventory();
        assertInstanceOf(WardrobeViewHolder.class, top.getHolder());
        assertEquals(9, top.getSize());
        assertEquals(Material.LEATHER_HELMET, top.getItem(0).getType());
        assertEquals(Material.BLACK_BANNER, top.getItem(1).getType());
        assertEquals("Guard's wardrobe", plain(bob.getOpenInventory().title()));

        InventoryClickEvent click = new InventoryClickEvent(bob.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled());
    }

    @Test
    void aViewerWhoTriesToDressIsToldToUseTheView() {
        grant(NpcAction.WARDROBE_VIEW);

        assertEquals(Optional.of("wardrobe.view-only"), wardrobes.dressRefusal(bob, guard));
        assertFalse(wardrobes.mayDress(bob, guard));
    }

    @Test
    void wardrobeEditLetsOthersDress() {
        grant(NpcAction.WARDROBE_EDIT);

        assertTrue(wardrobes.mayDress(bob, guard));
        assertTrue(wardrobes.open(bob, record(), false));
        assertEquals(List.of(guard), wardrobe.opened());
    }

    @Test
    void everyoneElseIsKeptOut() {
        assertFalse(wardrobes.open(bob, record(), false));

        assertEquals("[NPCs] You may not open Guard's wardrobe.", plain(bob.nextComponentMessage()));
        assertEquals(Optional.of("wardrobe.not-allowed"), wardrobes.dressRefusal(bob, guard));
        assertTrue(wardrobe.opened().isEmpty());
    }

    @Test
    void npcsSkysongNpcsDoesNotManageAreLeftToSkysongWardrobe() {
        UUID plain = plugin.fakeCitizens().add("Villager");

        assertEquals(Optional.empty(), wardrobes.dressRefusal(bob, plain));
        // The access rule adds nobody for them either.
        assertFalse(wardrobes.mayDress(bob, plain));
    }

    @Test
    void nobodyOpensAPickedUpNpcsWardrobeNotEvenStaff() {
        NpcRecord stored = new NpcRecord(guard, record().owner(), NpcState.STORED, null);
        bob.setOp(true);

        assertEquals(Optional.of("wardrobe.stored"), wardrobes.dressRefusal(alice, stored));
        assertEquals(Optional.of("wardrobe.stored"), wardrobes.dressRefusal(bob, stored));
        assertFalse(wardrobes.mayLook(alice, stored));
        assertFalse(wardrobes.open(alice, stored, false));
        assertEquals("[NPCs] Guard is packed into its item, so its wardrobe stays closed.",
                plain(alice.nextComponentMessage()));
    }

    @Test
    void withoutSkysongWardrobeNothingOpensAndTheButtonHides() {
        wardrobe.available(false);

        assertFalse(wardrobes.open(alice, record(), false));
        assertEquals("[NPCs] NPC wardrobes need SkysongWardrobe, which is not running.",
                plain(alice.nextComponentMessage()));

        plugin.menu().open(alice, guard);
        assertNull(alice.getOpenInventory().getTopInventory().getItem(WardrobeButton.SLOT));
    }

    @Test
    void skysongWardrobeRefusalsAreExplained() {
        wardrobe.result(FakeWardrobe.Opening.IN_USE);

        assertFalse(wardrobes.open(alice, record(), false));
        assertEquals("[NPCs] Someone else is dressing Guard right now.", plain(alice.nextComponentMessage()));
    }

    @Test
    void theMenuButtonOpensTheWardrobeAndClosingItGoesBackToTheMenu() {
        plugin.menu().open(alice, guard);
        ItemStack button = alice.getOpenInventory().getTopInventory().getItem(WardrobeButton.SLOT);
        assertEquals(Material.LEATHER_CHESTPLATE, button.getType());
        assertEquals("Wardrobe", plain(button.getItemMeta().displayName()));
        assertEquals(List.of("Dress Guard."), button.getItemMeta().lore().stream().map(NpcWardrobesTest::plain).toList());

        server.getPluginManager().callEvent(new InventoryClickEvent(alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, WardrobeButton.SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        server.getScheduler().performOneTick();
        assertEquals(List.of(guard), wardrobe.opened());

        // SkysongWardrobe runs the close action once the player has closed it.
        alice.closeInventory();
        wardrobe.lastOnClose().accept(alice);
        server.getScheduler().performOneTick();
        assertEquals(Material.LEATHER_CHESTPLATE,
                alice.getOpenInventory().getTopInventory().getItem(WardrobeButton.SLOT).getType());
    }

    @Test
    void theButtonShowsOnlyToViewersAndSaysTheyOnlyLook() {
        plugin.menu().open(bob, guard);
        assertEquals(InventoryType.CRAFTING, bob.getOpenInventory().getType());

        grant(NpcAction.WARDROBE_VIEW);
        plugin.menu().open(bob, guard);
        ItemStack button = bob.getOpenInventory().getTopInventory().getItem(WardrobeButton.SLOT);
        assertEquals(List.of("See what Guard wears."),
                button.getItemMeta().lore().stream().map(NpcWardrobesTest::plain).toList());
    }

    @Test
    void closingTheViewGoesBackToTheMenu() {
        grant(NpcAction.WARDROBE_VIEW);
        wardrobe.wear(guard, "hat", new ItemStack(Material.LEATHER_HELMET));
        wardrobes.open(bob, record(), true);

        bob.closeInventory();
        server.getScheduler().performOneTick();

        assertEquals(Material.LEATHER_CHESTPLATE,
                bob.getOpenInventory().getTopInventory().getItem(WardrobeButton.SLOT).getType());
    }

    @Test
    void anEmptyWardrobeSaysSo() {
        grant(NpcAction.WARDROBE_VIEW);

        assertFalse(wardrobes.open(bob, record(), false));
        assertEquals("[NPCs] Guard wears nothing from a wardrobe.", plain(bob.nextComponentMessage()));
    }

    @Test
    void snpcWardrobeOpensTheTargetedNpcsWardrobe() throws CommandSyntaxException {
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        CommandRunner commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new WardrobeCommand(targeting, wardrobes));

        commands.run(alice, "snpc wardrobe");
        assertEquals(List.of(guard), wardrobe.opened());

        commands.run(bob, "snpc wardrobe");
        assertEquals("[NPCs] You may not open Guard's wardrobe.", plain(bob.nextComponentMessage()));
    }

    private void grant(NpcAction action) {
        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()), Set.of(action));
    }

    private NpcRecord record() {
        return plugin.registry().get(guard).orElseThrow();
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
