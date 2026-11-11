package org.skysongdev.skysongnpcs.choices;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/** Clicking through choice pages the way a player does. */
final class ChoiceTestSupport {
    private ChoiceTestSupport() {
    }

    /** Runs the tick that opens or closes pages, then returns the page the player sees. */
    static Inventory page(ServerMock server, PlayerMock player) {
        server.getScheduler().performOneTick();
        Inventory top = player.getOpenInventory().getTopInventory();
        assertInstanceOf(ChoiceGui.class, top.getHolder(), "no choice page is open");
        return top;
    }

    static void click(ServerMock server, PlayerMock player, int slot, ClickType type) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL));
    }

    static void click(ServerMock server, PlayerMock player, int slot) {
        click(server, player, slot, ClickType.LEFT);
    }

    /** The slot of the first item whose name contains the text. */
    static int slotNamed(Inventory inventory, String text) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta() && name(item).contains(text)) {
                return slot;
            }
        }
        return fail("No item named " + text);
    }

    static String name(ItemStack item) {
        Component name = Objects.requireNonNull(item.getItemMeta().displayName());
        return PlainTextComponentSerializer.plainText().serialize(name);
    }
}
