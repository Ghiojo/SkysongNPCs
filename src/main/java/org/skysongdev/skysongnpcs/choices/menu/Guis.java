package org.skysongdev.skysongnpcs.choices.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;

import java.util.List;
import java.util.Map;

/** What every choice page needs: messages.yml, icons, and opening or closing pages safely. */
public final class Guis {
    private final Plugin plugin;
    private final PluginConfiguration configuration;

    public Guis(Plugin plugin, PluginConfiguration configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
    }

    public Messages messages() {
        return configuration.messages();
    }

    /** A page titled with the text at {@code key}. */
    public ChoiceGui page(Player viewer, int rows, String key, Map<String, String> placeholders) {
        return new ChoiceGui(viewer, rows, messages().get(key, placeholders));
    }

    /**
     * An icon named by the text at {@code key + ".name"} with the lore at {@code key + ".lore"}.
     */
    public ItemStack icon(Material material, String key, Map<String, String> placeholders) {
        return icon(material, messages().get(key + ".name", placeholders), messages().lines(key + ".lore", placeholders));
    }

    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack icon = new ItemStack(material);
        icon.editMeta(meta -> {
            meta.displayName(plain(name));
            meta.lore(lore.stream().map(Guis::plain).toList());
        });
        return icon;
    }

    /** Opens a page on the next tick: opening one inside a click on another confuses the client. */
    public void show(ChoiceGui gui) {
        Bukkit.getScheduler().runTask(plugin, () -> gui.viewer().openInventory(gui.getInventory()));
    }

    /** Closes the viewer's page on the next tick, if it is still open. */
    public void close(ChoiceGui gui) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (gui.viewer().getOpenInventory().getTopInventory().getHolder() == gui) {
                gui.viewer().closeInventory();
            }
        });
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        messages().send(player, key, placeholders);
    }

    private static Component plain(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
