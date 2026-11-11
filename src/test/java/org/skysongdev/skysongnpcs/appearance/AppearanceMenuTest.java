package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.skysongdev.skysongnpcs.FakeAppearanceDriver;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NPC menu's Appearance button, and the Appearance menu it opens. */
class AppearanceMenuTest {
    private static final int NAME = 2;
    private static final int SKIN = 3;
    private static final int NAMEPLATE = 4;
    private static final int POSE = 5;
    private static final int SITTING = 6;
    private static final int GLOW = 7;

    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeAppearanceDriver citizens;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        citizens = plugin.fakeAppearance();
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theAppearanceButtonOpensTheAppearanceMenu() {
        plugin.menu().open(alice, guard);
        ItemStack button = top(alice).getItem(AppearanceButton.SLOT);
        assertEquals(Material.ARMOR_STAND, button.getType());
        assertEquals("Appearance", plain(button.getItemMeta().displayName()));

        click(alice, AppearanceButton.SLOT, ClickType.LEFT);
        server.getScheduler().performTicks(2);

        assertEquals("Guard: appearance", PlainTextComponentSerializer.plainText()
                .serialize(alice.getOpenInventory().title()));
        assertEquals(Material.NAME_TAG, top(alice).getItem(NAME).getType());
        assertEquals(List.of("Now: always shown", "Click to change"), lore(top(alice).getItem(NAMEPLATE)));
    }

    @Test
    void onlyPlayersWhoMayChangeLooksSeeTheButton() {
        grant(bob, NpcAction.INFO);
        plugin.menu().open(bob, guard);
        assertNull(top(bob).getItem(AppearanceButton.SLOT));
        assertFalse(appearanceMenu().open(bob, guard));
    }

    @Test
    void theMenuShowsOnlyWhatTheViewerMayChange() {
        grant(bob, NpcAction.RENAME);

        assertTrue(appearanceMenu().open(bob, guard));

        assertEquals(Material.NAME_TAG, top(bob).getItem(NAME).getType());
        assertNull(top(bob).getItem(NAMEPLATE));
        assertNull(top(bob).getItem(SKIN));
    }

    @Test
    void clicksChangeTheLooksAndRedraw() {
        appearanceMenu().open(alice, guard);

        click(alice, NAMEPLATE, ClickType.LEFT);
        click(alice, POSE, ClickType.LEFT);
        click(alice, SITTING, ClickType.LEFT);

        assertEquals(Nameplate.HOVER, citizens.nameplate(guard));
        assertEquals(NpcPose.CROUCHING, citizens.pose(guard));
        assertTrue(citizens.sitting(guard));
        assertEquals(List.of("Now: shown when looked at", "Click to change"), lore(top(alice).getItem(NAMEPLATE)));
    }

    @Test
    void leftClickTogglesGlowingAndRightClickPicksTheColour() {
        appearanceMenu().open(alice, guard);

        click(alice, GLOW, ClickType.LEFT);
        assertEquals(Optional.of(NamedTextColor.WHITE), citizens.glow(guard));
        click(alice, GLOW, ClickType.RIGHT);
        assertTrue(citizens.glow(guard).isPresent());
        assertFalse(citizens.glow(guard).get().equals(NamedTextColor.WHITE));
        click(alice, GLOW, ClickType.LEFT);
        assertEquals(Optional.empty(), citizens.glow(guard));
    }

    @Test
    void theNameButtonOffersTheCommandInChat() {
        appearanceMenu().open(alice, guard);

        click(alice, NAME, ClickType.LEFT);
        server.getScheduler().performTicks(2);

        assertEquals(InventoryType.CRAFTING, alice.getOpenInventory().getType());
        assertEquals("[NPCs] Click here and type Guard's new name.",
                PlainTextComponentSerializer.plainText().serialize(alice.nextComponentMessage()));
    }

    @Test
    void noSkinButtonForNpcsThatAreNotPlayerShaped() {
        citizens.notPlayerShaped(guard);

        appearanceMenu().open(alice, guard);

        assertNull(top(alice).getItem(SKIN));
    }

    @Test
    void clicksNeverTakeAnythingOut() {
        appearanceMenu().open(alice, guard);

        assertTrue(click(alice, NAMEPLATE, ClickType.LEFT).isCancelled());
    }

    private AppearanceMenu appearanceMenu() {
        // Any instance will do: the plugin's own one, registered as a listener, handles every Appearance menu's clicks.
        return new AppearanceMenu(plugin, plugin.registry(), plugin.citizens(), plugin.access(), plugin.appearance(),
                plugin.configuration(), plugin.menu());
    }

    private void grant(PlayerMock player, NpcAction action) {
        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(player.getUniqueId()), Set.of(action));
    }

    private InventoryClickEvent click(PlayerMock player, int slot, ClickType type) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static Inventory top(PlayerMock player) {
        return player.getOpenInventory().getTopInventory();
    }

    private static List<String> lore(ItemStack item) {
        return item.getItemMeta().lore().stream().map(AppearanceMenuTest::plain).toList();
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
