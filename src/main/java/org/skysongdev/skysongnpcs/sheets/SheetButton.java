package org.skysongdev.skysongnpcs.sheets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The NPC menu's Sheet button, {@code skysongnpcs:sheet}: for anyone with the
 * {@code stats-view} action, it closes the menu and shows the NPC's stat sheet in chat, as
 * {@code /snpc sheet} does.
 */
public final class SheetButton {
    static final String NAME = "sheet";
    /** Next to the Abilities button in the default three-row menu. */
    static final int SLOT = 20;

    private final PluginConfiguration configuration;
    private final Function<NpcRecord, String> npcName;
    private final BiConsumer<Player, NpcRecord> show;

    /**
     * @param npcName the name the button shows for an NPC
     * @param show    shows a player an NPC's sheet in chat
     */
    public SheetButton(PluginConfiguration configuration, Function<NpcRecord, String> npcName,
                       BiConsumer<Player, NpcRecord> show) {
        this.configuration = configuration;
        this.npcName = npcName;
        this.show = show;
    }

    public void register(Plugin plugin, NpcMenuService menu, AccessService access) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        menu.registerButton(plugin, NAME, SLOT, this::icon,
                context -> access.may(context.viewer(), (NpcRecord) context.npc(), NpcAction.STATS_VIEW),
                click -> {
                    click.viewer().closeInventory();
                    show.accept(click.viewer(), (NpcRecord) click.npc());
                });
    }

    ItemStack icon(MenuContext context) {
        Map<String, String> placeholders = Map.of("npc", npcName.apply((NpcRecord) context.npc()));
        ItemStack icon = new ItemStack(Material.BOOK);
        icon.editMeta(meta -> {
            meta.displayName(itemText(configuration.messages().get("sheet.button.name", placeholders)));
            meta.lore(configuration.messages().lines("sheet.button.lore", placeholders).stream()
                    .map(SheetButton::itemText).toList());
        });
        return icon;
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
