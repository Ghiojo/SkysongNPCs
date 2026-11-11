package org.skysongdev.skysongnpcs.choices.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One open choice or setup page: a read-only inventory whose slots run an action when clicked.
 * {@link ChoiceGuiListener} cancels every click and passes those on its own slots here.
 */
public final class ChoiceGui implements InventoryHolder {
    private final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    public ChoiceGui(Player viewer, int rows, Component title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);
    }

    public Player viewer() {
        return viewer;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    /** Puts an item in a slot that does something when clicked. */
    public void button(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    /** Puts an item in a slot that does nothing. */
    public void label(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        actions.remove(slot);
    }

    /** Empties the page, for drawing it again. */
    public void clear() {
        inventory.clear();
        actions.clear();
    }

    void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }
}
