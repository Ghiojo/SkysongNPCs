package org.skysongdev.skysongnpcs.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The menu a managed NPC opens on right-click (and with {@code /snpc menu}). It holds
 * SkysongNPCs' own buttons and any other plugin's. A player sees only the buttons they may
 * use; a player who would see none gets no menu, and their click goes through to Citizens
 * and other plugins as usual.
 *
 * <p>Every button has an id {@code <plugin>:<name>}, such as {@code skysongnpcs:info} or
 * {@code skysongeconomy:payroll}, where {@code <plugin>} is the registering plugin's name in
 * lower case. Server owners use that id in menus.yml to move, recolour or hide any button.
 *
 * <p>Must be called on the server's main thread, and throws {@link IllegalStateException} otherwise.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface NpcMenu {

    /**
     * Adds a button to every NPC's menu. It is removed again when the plugin disables.
     *
     * @param plugin    the plugin that owns the button; its name, in lower case, starts the id
     * @param name      the button's name inside the plugin: lower-case letters, digits, {@code _} and {@code -}
     * @param slot      where it goes, counted from 0 at the top left, unless menus.yml says otherwise;
     *                  a slot outside the menu or taken by another button moves it to the first free one
     * @param icon      the item to draw for a viewer; called each time the menu is drawn
     * @param visibleTo whether a viewer sees it, for example by asking the NPC's access
     * @param onClick   what a click does
     * @return the button's id, {@code <plugin>:<name>}
     * @throws IllegalArgumentException when the name is not valid or the plugin already has a button with it
     */
    @NotNull String registerButton(@NotNull Plugin plugin, @NotNull String name, int slot,
                                   @NotNull Function<MenuContext, ItemStack> icon,
                                   @NotNull Predicate<MenuContext> visibleTo,
                                   @NotNull Consumer<MenuClick> onClick);

    /**
     * Removes a button. Menus already open keep it until they are redrawn.
     *
     * @param plugin the plugin that registered it
     * @param name   its name inside that plugin
     * @return true when it was there
     */
    boolean unregisterButton(@NotNull Plugin plugin, @NotNull String name);

    /**
     * @return the ids of every registered button, SkysongNPCs' own first, then in the order they were added
     */
    @NotNull List<String> buttons();

    /**
     * Opens an NPC's menu for a player, showing the buttons they may use.
     *
     * @param viewer the player
     * @param npc    the NPC's Citizens UUID
     * @return false, opening nothing, when the NPC is not managed or the player would see no button
     */
    boolean open(@NotNull Player viewer, @NotNull UUID npc);
}
