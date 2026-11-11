package org.skysongdev.skysongnpcs.abilities;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilities;
import org.skysongdev.skysongnpcs.api.ability.NpcAbility;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.commands.AbilitiesCommand;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.commands.SheetCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /snpc abilities}, the abilities menu and button, the line under the sheet, and the API. */
class AbilitiesCommandTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), """
                tough:
                  name: "<red>Tough"
                  description: ["Hard to hurt.", "Very."]
                  icon: SHIELD
                  modifiers:
                    strength: 2
                keen:
                  description: "Sees far."
                """);
        assertTrue(plugin.abilities().definitions().reload());

        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        AbilityText text = new AbilityText(plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new AbilitiesCommand(targeting, plugin.access(), plugin.abilities(), plugin.abilitiesMenu(), text),
                new SheetCommand(targeting, plugin.access(), plugin.sheets(), plugin.templates(), plugin.configuration(),
                        npc -> text.sheetLine(plugin.abilities().of(npc))));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
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
    void staffGrantAndRevoke() throws CommandSyntaxException {
        commands.run(staff, "snpc abilities grant tough");
        assertEquals("[NPCs] Guard now has the ability tough.", plain(staff));
        assertTrue(plugin.abilities().has(guard, "tough"));
        assertEquals(1, plugin.fakeSheets().modifiers(guard).size());

        commands.run(staff, "snpc abilities grant tough");
        assertEquals("[NPCs] Guard already has the ability tough.", plain(staff));
        commands.run(staff, "snpc abilities grant flying");
        assertEquals("[NPCs] abilities.yml has no ability called flying.", plain(staff));

        commands.run(staff, "snpc abilities revoke tough");
        assertEquals("[NPCs] Guard no longer has the ability tough.", plain(staff));
        assertFalse(plugin.abilities().has(guard, "tough"));
        commands.run(staff, "snpc abilities revoke tough");
        assertEquals("[NPCs] Guard does not have the ability tough.", plain(staff));
    }

    @Test
    void playersCannotGrant() {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc abilities grant tough"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc abilities revoke tough"));
    }

    @Test
    void theListShowsEachAbilityAsAHoverWord() throws CommandSyntaxException {
        plugin.abilities().grant(guard, "keen");
        plugin.abilities().grant(guard, "tough");

        commands.run(alice, "snpc abilities list");

        assertEquals("[NPCs] Guard's abilities:", plain(alice));
        Component line = alice.nextComponentMessage();
        // abilities.yml order, not the order they were granted in.
        assertEquals("Abilities: Tough, Keen", PlainTextComponentSerializer.plainText().serialize(line));
        assertEquals(List.of("Hard to hurt.\nVery.", "Sees far."), hovers(line));
    }

    @Test
    void anNpcWithoutAbilitiesSaysSo() throws CommandSyntaxException {
        commands.run(alice, "snpc abilities list");

        assertEquals("[NPCs] Guard has no abilities.", plain(alice));
    }

    @Test
    void othersNeedAbilitiesView() throws CommandSyntaxException {
        commands.run(bob, "snpc abilities");
        assertEquals("[NPCs] You may not see Guard's abilities.", plain(bob));

        plugin.access().grants().set(guard, GrantSource.owner(), AccessGroup.player(bob.getUniqueId()),
                Set.of(NpcAction.ABILITIES_VIEW));
        commands.run(bob, "snpc abilities");
        assertEquals(InventoryType.CHEST, bob.getOpenInventory().getType());
    }

    @Test
    void theSheetEndsWithTheAbilities() throws CommandSyntaxException {
        plugin.abilities().grant(guard, "keen");

        commands.run(alice, "snpc sheet");

        plain(alice);
        plain(alice);
        assertEquals("Abilities: Keen", plain(alice));
    }

    @Test
    void theMenuShowsEveryAbilityAndCannotBeChanged() throws CommandSyntaxException {
        plugin.abilities().grant(guard, "tough");

        commands.run(alice, "snpc abilities");

        Inventory top = alice.getOpenInventory().getTopInventory();
        assertEquals(9, top.getSize());
        assertEquals(Material.SHIELD, top.getItem(0).getType());
        assertEquals("Tough", PlainTextComponentSerializer.plainText().serialize(top.getItem(0).getItemMeta().displayName()));
        assertEquals(2, top.getItem(0).getItemMeta().lore().size());

        InventoryClickEvent click = new InventoryClickEvent(alice.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled());
    }

    @Test
    void theMenuOfAnNpcWithoutAbilitiesSaysSo() throws CommandSyntaxException {
        commands.run(alice, "snpc abilities");

        assertEquals(Material.BARRIER, alice.getOpenInventory().getTopInventory().getItem(4).getType());
    }

    @Test
    void theNpcMenuHasAnAbilitiesButtonForThoseWhoMaySeeThem() {
        assertTrue(plugin.menu().buttons().contains("skysongnpcs:abilities"));
        plugin.menu().open(alice, guard);
        Inventory top = alice.getOpenInventory().getTopInventory();
        assertEquals(Material.ENCHANTED_BOOK, top.getItem(AbilitiesButton.SLOT).getType());

        server.getPluginManager().callEvent(new InventoryClickEvent(alice.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, AbilitiesButton.SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(Material.BARRIER, alice.getOpenInventory().getTopInventory().getItem(4).getType());
    }

    @Test
    void theApiGrantsListsAndRevokes() {
        NpcAbilities api = SkysongNpcsApi.get().abilities();

        assertEquals(List.of("tough", "keen"), api.defined().stream().map(NpcAbility::id).toList());
        assertEquals("Sees far.", api.find("keen").orElseThrow().description());
        assertTrue(api.grant(guard, "keen"));
        assertTrue(api.has(guard, "keen"));
        assertEquals(List.of("keen"), api.of(guard).stream().map(NpcAbility::id).toList());
        assertThrows(IllegalArgumentException.class, () -> api.grant(guard, "flying"));
        assertTrue(api.revoke(guard, "keen"));
        assertFalse(api.has(guard, "keen"));
        assertTrue(api.open(alice, guard));
        assertEquals("0.17.0", SkysongNpcsApi.get().apiVersion());
    }

    @Test
    void reloadAppliesTheFileAndNamesItWhenBroken() throws IOException, CommandSyntaxException {
        CommandRunner reload = new CommandRunner(plugin.configuration(), "1.0.0",
                new org.skysongdev.skysongnpcs.commands.ReloadCommand(plugin.configuration(),
                        List.of(() -> plugin.abilities().definitions().reload() ? List.<String>of() : List.of("abilities.yml"))));
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), "a:\n  name: two words\n");

        reload.run(staff, "snpc reload");

        assertTrue(plain(staff).contains("abilities.yml"));
        assertTrue(plugin.abilities().definitions().get("tough").isPresent());
    }

    private static List<String> hovers(Component line) {
        List<String> hovers = new java.util.ArrayList<>();
        for (Component part : line.iterable(ComponentIteratorType.DEPTH_FIRST)) {
            HoverEvent<?> hover = part.hoverEvent();
            if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
                String text = PlainTextComponentSerializer.plainText().serialize((Component) hover.value());
                if (!hovers.contains(text)) {
                    hovers.add(text);
                }
            }
        }
        return hovers;
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
