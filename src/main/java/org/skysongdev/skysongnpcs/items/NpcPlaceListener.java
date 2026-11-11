package org.skysongdev.skysongnpcs.items;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;

import java.util.Locale;

/**
 * Right-clicking the top of a block with a blueprint in the main hand places its NPC there.
 * A click another plugin already denied is left alone (SkysongItems denies clicks with
 * disabled items, protection plugins deny theirs), and a blueprint click is always used up,
 * so the item never does anything else.
 */
public final class NpcPlaceListener implements Listener {
    private final NpcPlacer placer;
    private final PluginConfiguration configuration;

    public NpcPlaceListener(NpcPlacer placer, PluginConfiguration configuration) {
        this.placer = placer;
        this.configuration = configuration;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getBlockFace() != BlockFace.UP
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        NpcPlacer.Outcome outcome = placer.place(event.getPlayer(), event.getItem(), event.getClickedBlock());
        if (outcome.result() == NpcPlacer.Result.NOT_A_BLUEPRINT) {
            return;
        }
        event.setCancelled(true);
        if (outcome.result() != NpcPlacer.Result.CANCELLED) {
            configuration.messages().send(event.getPlayer(), messageKey(outcome.result()), outcome.placeholders());
        }
        if (outcome.result() == NpcPlacer.Result.STALE_ITEM) {
            // M18: staff hear about every refused stale item, which may be a dupe.
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(Permissions.ADMIN)) {
                    configuration.messages().send(staff, "npc-item.stale-item-staff", outcome.placeholders());
                }
            }
        }
    }

    /** The messages.yml key of a result's text; NPC item results (M18) have their own section. */
    static String messageKey(NpcPlacer.Result result) {
        return (result.npcItem() ? "npc-item." : "place.") + key(result);
    }

    static String key(NpcPlacer.Result result) {
        return result.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
