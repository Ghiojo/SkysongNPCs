package org.skysongdev.skysongnpcs.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;
import java.util.UUID;

/**
 * The menu's Inventory ({@code skysongnpcs:inventory}) and Equipment
 * ({@code skysongnpcs:equipment}) buttons. Each shows for anyone who may at least see what it
 * opens, and says in its lore whether a click edits or only shows.
 */
public final class ContainerButtons {
    static final int INVENTORY_SLOT = 15;
    static final int EQUIPMENT_SLOT = 16;

    private final ContainerService containers;
    private final PluginConfiguration configuration;
    private final NpcLookup citizens;

    public ContainerButtons(ContainerService containers, PluginConfiguration configuration, NpcLookup citizens) {
        this.containers = containers;
        this.configuration = configuration;
        this.citizens = citizens;
    }

    public void register(Plugin plugin, NpcMenuService menu) {
        register(plugin, menu, Container.INVENTORY, INVENTORY_SLOT, Material.CHEST);
        register(plugin, menu, Container.EQUIPMENT, EQUIPMENT_SLOT, Material.IRON_CHESTPLATE);
    }

    private void register(Plugin plugin, NpcMenuService menu, Container container, int slot, Material material) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        menu.registerButton(plugin, container.key(), slot, context -> icon(context, container, material),
                context -> containers.maySee(context.viewer(), (NpcRecord) context.npc(), container),
                click -> {
                    Player viewer = click.viewer();
                    UUID npc = click.npc().id();
                    // Opening another inventory inside a click confuses the client; the next tick is safe.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (viewer.isOnline()) {
                            containers.openAndTell(viewer, npc, container, false);
                        }
                    });
                });
    }

    private ItemStack icon(MenuContext context, Container container, Material material) {
        Messages messages = configuration.messages();
        String prefix = "containers." + container.key() + ".button.";
        boolean edit = containers.mayEdit(context.viewer(), (NpcRecord) context.npc(), container);
        Map<String, String> placeholders = Map.of("npc", citizens.name(context.npc().id()));
        ItemStack icon = new ItemStack(material);
        icon.editMeta(meta -> {
            meta.displayName(itemText(messages.get(prefix + "name", placeholders)));
            meta.lore(messages.lines(prefix + (edit ? "lore-edit" : "lore-view"), placeholders).stream()
                    .map(ContainerButtons::itemText).toList());
        });
        return icon;
    }

    /** Item names and lore are italic unless a text says otherwise; messages.yml texts are not, unless they say so. */
    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
