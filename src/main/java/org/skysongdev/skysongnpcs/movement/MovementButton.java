package org.skysongdev.skysongnpcs.movement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuClick;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Movement button, {@code skysongnpcs:movement}, for anyone with teleport, follow or look
 * on the NPC. Left-click brings the NPC to the viewer, right-click makes it follow the viewer
 * or stop, shift-click turns looking at players on or off. Its lore lists only the clicks the
 * viewer may use, with what is on now.
 */
public final class MovementButton {
    static final String NAME = "movement";
    /** Left of the Information button in the default three-row menu. */
    static final int SLOT = 11;

    private final MovementService movement;
    private final AccessService access;
    private final NpcLookup citizens;
    private final PluginConfiguration configuration;

    public MovementButton(MovementService movement, AccessService access, NpcLookup citizens,
                          PluginConfiguration configuration) {
        this.movement = movement;
        this.access = access;
        this.citizens = citizens;
        this.configuration = configuration;
    }

    public void register(Plugin plugin, NpcMenuService menu) {
        menu.registerButton(plugin, NAME, SLOT, this::icon, context -> !moves(context).isEmpty(), this::click);
    }

    /** The movement actions the viewer may use on the NPC, in lore order. */
    private List<NpcAction> moves(MenuContext context) {
        // Menus SkysongNPCs draws always hold an NpcRecord as their npc().
        Set<NpcAction> allowed = access.allowed(context.viewer(), (NpcRecord) context.npc());
        return List.of(NpcAction.TELEPORT, NpcAction.FOLLOW, NpcAction.LOOK).stream().filter(allowed::contains).toList();
    }

    ItemStack icon(MenuContext context) {
        Messages messages = configuration.messages();
        NpcRecord npc = (NpcRecord) context.npc();
        boolean following = movement.following(npc.id()).filter(context.viewer().getUniqueId()::equals).isPresent();
        boolean looking = movement.isLooking(npc.id());
        List<Component> lore = new ArrayList<>();
        for (NpcAction action : moves(context)) {
            switch (action) {
                case TELEPORT -> lore.add(messages.get("movement.button.teleport", Map.of()));
                case FOLLOW -> lore.add(messages.get("movement.button.follow", Map.of("state", state(messages, following))));
                case LOOK -> lore.add(messages.get("movement.button.look", Map.of("state", state(messages, looking))));
                default -> { }
            }
        }
        ItemStack icon = new ItemStack(Material.LEAD);
        icon.editMeta(meta -> {
            meta.displayName(itemText(messages.get("movement.button.name", Map.of("npc", citizens.name(npc.id())))));
            meta.lore(lore.stream().map(MovementButton::itemText).toList());
        });
        return icon;
    }

    void click(MenuClick click) {
        Player viewer = click.viewer();
        NpcRecord npc = (NpcRecord) click.npc();
        boolean staff = access.isStaff(viewer);
        Map<String, String> placeholders = Map.of("npc", citizens.name(npc.id()));
        MovementSettings settings = movement.settings();
        Messages messages = configuration.messages();
        if (click.click().isShiftClick()) {
            if (access.may(viewer, npc, NpcAction.LOOK)) {
                boolean on = !movement.isLooking(npc.id());
                MovementReplies.send(messages, viewer, movement.look(npc, on, settings.lookRange(), staff),
                        on ? "movement.look-on" : "movement.look-off",
                        with(placeholders, "range", MovementReplies.number(settings.lookRange())), settings);
                click.refresh();
            }
        } else if (click.click().isRightClick()) {
            if (access.may(viewer, npc, NpcAction.FOLLOW)) {
                if (movement.following(npc.id()).filter(viewer.getUniqueId()::equals).isPresent()) {
                    movement.stopFollowing(npc.id());
                    messages.send(viewer, "movement.follow-off", placeholders);
                } else {
                    double distance = settings.followDistance();
                    Map<String, String> values = with(with(placeholders, "target", viewer.getName()),
                            "distance", MovementReplies.number(distance));
                    MovementReplies.send(messages, viewer, movement.follow(viewer, npc, viewer, distance, staff),
                            "movement.follow-on", values, settings);
                }
                click.refresh();
            }
        } else if (click.click().isLeftClick() && access.may(viewer, npc, NpcAction.TELEPORT)) {
            MovementReplies.send(messages, viewer, movement.teleportHere(viewer, npc, staff),
                    "movement.teleported", placeholders, settings);
            click.close();
        }
    }

    private static Map<String, String> with(Map<String, String> placeholders, String key, String value) {
        Map<String, String> values = new HashMap<>(placeholders);
        values.put(key, value);
        return values;
    }

    private static String state(Messages messages, boolean on) {
        return messages.plain(on ? "movement.button.on" : "movement.button.off", Map.of());
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
