package org.skysongdev.skysongnpcs.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The Information button, {@code skysongnpcs:info}: the NPC's name, owner, template and state,
 * for anyone with the {@code info} action. Clicking it does nothing. Later milestones add the
 * policy and unspent points to its lore.
 */
public final class InfoButton {
    static final String NAME = "info";
    /** The middle of the default three-row menu. */
    static final int SLOT = 13;

    private final PluginConfiguration configuration;
    private final NpcLookup citizens;
    private final Function<NpcRecord, String> ownerName;

    /**
     * @param ownerName writes an NPC's owner as messages.yml's {@code owner-name} texts do
     */
    public InfoButton(PluginConfiguration configuration, NpcLookup citizens, Function<NpcRecord, String> ownerName) {
        this.configuration = configuration;
        this.citizens = citizens;
        this.ownerName = ownerName;
    }

    public void register(Plugin plugin, NpcMenuService menu, AccessService access) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        menu.registerButton(plugin, NAME, SLOT, this::icon,
                context -> access.may(context.viewer(), (NpcRecord) context.npc(), NpcAction.INFO),
                click -> { });
    }

    ItemStack icon(MenuContext context) {
        NpcRecord npc = (NpcRecord) context.npc();
        Messages messages = configuration.messages();
        String state = messages.plain("menu.info.states." + npc.state().name().toLowerCase(Locale.ROOT), Map.of());
        String template = npc.template().orElseGet(() -> messages.plain("menu.info.no-template", Map.of()));
        Map<String, String> placeholders = Map.of(
                "npc", citizens.name(npc.id()),
                "owner", ownerName.apply(npc),
                "template", template,
                "state", state);

        ItemStack icon = new ItemStack(Material.NAME_TAG);
        icon.editMeta(meta -> {
            meta.displayName(itemText(messages.get("menu.info.name", placeholders)));
            meta.lore(messages.lines("menu.info.lore", placeholders).stream().map(InfoButton::itemText).toList());
        });
        return icon;
    }

    /** Item names and lore are italic unless a text says otherwise; messages.yml texts are not, unless they say so. */
    static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
