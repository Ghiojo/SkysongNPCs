package org.skysongdev.skysongnpcs.wardrobe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;

/**
 * The Wardrobe button, {@code skysongnpcs:wardrobe}: dresses the NPC for {@code wardrobe-edit},
 * shows what it wears for {@code wardrobe-view}. Hidden without SkysongWardrobe.
 */
public final class WardrobeButton {
    static final String NAME = "wardrobe";
    /** Track B's agreed default layout: movement 11, appearance 12, info 13, wardrobe 14, inventory 15, equipment 16. */
    static final int SLOT = 14;

    private final NpcWardrobes wardrobes;
    private final AccessService access;
    private final NpcLookup citizens;
    private final PluginConfiguration configuration;

    public WardrobeButton(NpcWardrobes wardrobes, AccessService access, NpcLookup citizens,
                          PluginConfiguration configuration) {
        this.wardrobes = wardrobes;
        this.access = access;
        this.citizens = citizens;
        this.configuration = configuration;
    }

    public void register(Plugin plugin, NpcMenuService menu) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        menu.registerButton(plugin, NAME, SLOT, this::icon,
                context -> wardrobes.available() && wardrobes.mayLook(context.viewer(), (NpcRecord) context.npc()),
                // A new menu opens next tick: opening one inside a click handler confuses the client.
                click -> Bukkit.getScheduler().runTask(plugin,
                        () -> wardrobes.open(click.viewer(), (NpcRecord) click.npc(), true)));
    }

    ItemStack icon(MenuContext context) {
        NpcRecord npc = (NpcRecord) context.npc();
        boolean dress = access.may(context.viewer(), npc, NpcAction.WARDROBE_EDIT);
        Messages messages = configuration.messages();
        Map<String, String> placeholders = Map.of("npc", citizens.name(npc.id()));
        ItemStack icon = new ItemStack(Material.LEATHER_CHESTPLATE);
        icon.editMeta(meta -> {
            meta.displayName(itemText(messages.get("wardrobe.button.name", placeholders)));
            meta.lore(messages.lines(dress ? "wardrobe.button.lore-edit" : "wardrobe.button.lore-view", placeholders)
                    .stream().map(WardrobeButton::itemText).toList());
        });
        return icon;
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
