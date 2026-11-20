package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.items.NpcPickup;
import org.skysongdev.skysongnpcs.items.PickupItems;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /snpc pickupitem} (staff): which SkysongItems saved item the selected NPC is picked up
 * into. On its own it says which item and where it comes from; {@code set <saved item>} gives
 * the NPC its own, {@code clear} goes back to its template's, blueprint's or the default, and
 * {@code preview} hands the sender the item as it would be made now, without the NPC in it.
 */
public final class PickupItemCommand implements SubCommand {
    private static final String ITEM = "item";

    private final NpcTargeting targeting;
    private final Supplier<NpcPickup> pickup;

    /** @param pickup the pickup, made after the commands are; asked for when the command runs */
    public PickupItemCommand(NpcTargeting targeting, Supplier<NpcPickup> pickup) {
        this.targeting = targeting;
        this.pickup = pickup;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("pickupitem")
                .requires(source -> targeting.isStaff(source.getSender()))
                .executes(context -> {
                    show(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("set")
                        // Greedy: saved items in folders have a / in their id.
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(ITEM, StringArgumentType.greedyString())
                                .suggests(SheetTargeting.suggest(() -> items().savedIds()))
                                .executes(context -> {
                                    set(context.getSource().getSender(), StringArgumentType.getString(context, ITEM));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                        .executes(context -> {
                            clear(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("preview")
                        .requires(source -> source.getSender() instanceof Player)
                        .executes(context -> {
                            preview((Player) context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void show(CommandSender sender) {
        targeting.managedTarget(sender).ifPresent(npc ->
                targeting.send(sender, "pickup-item.show", placeholders(npc, items().choice(npc))));
    }

    private void set(CommandSender sender, String typed) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = Map.of("npc", targeting.npcName(npc.get().id()), ITEM, typed.trim());
        try {
            String id = items().setOwn(npc.get().id(), typed);
            targeting.send(sender, "pickup-item.set", Map.of("npc", placeholders.get("npc"), ITEM, id));
        } catch (IllegalArgumentException unknown) {
            targeting.send(sender, "pickup-item.unknown", placeholders);
        }
    }

    private void clear(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        if (items().own(npc.get().id()).isEmpty()) {
            targeting.send(sender, "pickup-item.not-set", placeholders(npc.get(), items().choice(npc.get())));
            return;
        }
        items().setOwn(npc.get().id(), null);
        targeting.send(sender, "pickup-item.cleared", placeholders(npc.get(), items().choice(npc.get())));
    }

    private void preview(Player player) {
        Optional<NpcRecord> npc = targeting.managedTarget(player);
        if (npc.isEmpty()) {
            return;
        }
        PickupItems.Made made = items().make(npc.get());
        ItemStack item = made.item();
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        targeting.send(player, "pickup-item.preview", placeholders(npc.get(), made.choice()));
    }

    /** {@code <npc>}, {@code <item>} (the saved item, or the source's text) and {@code <source>}. */
    private Map<String, String> placeholders(NpcRecord npc, PickupItems.Choice choice) {
        String source = targeting.text("pickup-item.source." + choice.source().name().toLowerCase(Locale.ROOT));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("npc", targeting.npcName(npc.id()));
        placeholders.put(ITEM, choice.savedItem() == null ? "-" : choice.savedItem());
        placeholders.put("source", source);
        return placeholders;
    }

    private PickupItems items() {
        return pickup.get().items();
    }
}
