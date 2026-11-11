package org.skysongdev.skysongnpcs.menu;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.api.menu.MenuClick;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** A button as a plugin registered it; {@link NpcMenuService#registerButton} describes the parts. */
record RegisteredButton(String id, Plugin plugin, int slot, Function<MenuContext, ItemStack> icon,
                        Predicate<MenuContext> visibleTo, Consumer<MenuClick> onClick) {
}
