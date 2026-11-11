package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;

import java.util.Map;

/**
 * The NPC menu's Appearance button, {@code skysongnpcs:appearance}: for anyone who may rename
 * the NPC, change its skin or its appearance. Clicking it opens the {@link AppearanceMenu}.
 */
public final class AppearanceButton {
    static final String NAME = "appearance";
    /** Left of the Information button in the default three-row menu. */
    static final int SLOT = 12;

    private final PluginConfiguration configuration;
    private final NpcLookup names;
    private final AppearanceMenu appearanceMenu;

    public AppearanceButton(PluginConfiguration configuration, NpcLookup names, AppearanceMenu appearanceMenu) {
        this.configuration = configuration;
        this.names = names;
        this.appearanceMenu = appearanceMenu;
    }

    public void register(Plugin plugin, NpcMenuService menu) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        menu.registerButton(plugin, NAME, SLOT, this::icon,
                context -> appearanceMenu.mayUse(context.viewer(), (NpcRecord) context.npc()),
                click -> {
                    click.close();
                    // After the NPC menu has closed, on the next tick.
                    Bukkit.getScheduler().runTask(plugin, () -> appearanceMenu.open(click.viewer(), click.npc().id()));
                });
    }

    ItemStack icon(MenuContext context) {
        Map<String, String> placeholders = Map.of("npc", names.name(context.npc().id()));
        ItemStack icon = new ItemStack(Material.ARMOR_STAND);
        icon.editMeta(meta -> {
            meta.displayName(itemText(configuration.messages().get("appearance.menu.button.name", placeholders)));
            meta.lore(configuration.messages().lines("appearance.menu.button.lore", placeholders).stream()
                    .map(AppearanceButton::itemText).toList());
        });
        return icon;
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
