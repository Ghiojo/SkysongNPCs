package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;

/**
 * The NPC menu's Abilities button, {@code skysongnpcs:abilities}: for anyone with the
 * {@code abilities-view} action, it opens the abilities menu.
 */
public final class AbilitiesButton {
    static final String NAME = "abilities";
    static final int SLOT = 21;

    private final AbilityService abilities;
    private final AbilitiesMenu menu;
    private final PluginConfiguration configuration;

    public AbilitiesButton(AbilityService abilities, AbilitiesMenu menu, PluginConfiguration configuration) {
        this.abilities = abilities;
        this.menu = menu;
        this.configuration = configuration;
    }

    public void register(Plugin plugin, NpcMenuService npcMenu, AccessService access) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        npcMenu.registerButton(plugin, NAME, SLOT, this::icon,
                context -> access.may(context.viewer(), (NpcRecord) context.npc(), NpcAction.ABILITIES_VIEW),
                click -> menu.open(click.viewer(), click.npc().id()));
    }

    ItemStack icon(MenuContext context) {
        Map<String, String> placeholders = Map.of("count", String.valueOf(abilities.of(context.npc().id()).size()));
        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        icon.editMeta(meta -> {
            meta.displayName(itemText(configuration.messages().get("abilities.button.name", placeholders)));
            meta.lore(configuration.messages().lines("abilities.button.lore", placeholders).stream()
                    .map(AbilitiesButton::itemText).toList());
        });
        return icon;
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
