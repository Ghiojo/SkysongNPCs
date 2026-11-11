package org.skysongdev.skysongnpcs.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;
import java.util.UUID;

/** The menu's Pick up button ({@code skysongnpcs:pickup}), for whoever may pick the NPC up. */
public final class PickupButton {
    public static final String ID = "pickup";
    static final int SLOT = 22;

    private final NpcPickup pickup;
    private final AccessService access;
    private final PluginConfiguration configuration;

    public PickupButton(NpcPickup pickup, AccessService access, PluginConfiguration configuration) {
        this.pickup = pickup;
        this.access = access;
        this.configuration = configuration;
    }

    public void register(Plugin plugin, NpcMenuService menu) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        menu.registerButton(plugin, ID, SLOT, this::icon,
                context -> access.may(context.viewer(), (NpcRecord) context.npc(), NpcAction.PICKUP),
                click -> {
                    Player viewer = click.viewer();
                    UUID npc = click.npc().id();
                    viewer.closeInventory();
                    // Changing inventories inside a click confuses the client; the next tick is safe.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (viewer.isOnline()) {
                            pickup.pickUpAndTell(viewer, npc);
                        }
                    });
                });
    }

    private ItemStack icon(MenuContext context) {
        Messages messages = configuration.messages();
        Map<String, String> placeholders = Map.of("npc", pickup.name(context.npc().id()));
        ItemStack icon = new ItemStack(Material.BUNDLE);
        icon.editMeta(meta -> {
            meta.displayName(itemText(messages.get("pickup.button.name", placeholders)));
            meta.lore(messages.lines("pickup.button.lore", placeholders).stream().map(PickupButton::itemText).toList());
        });
        return icon;
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
