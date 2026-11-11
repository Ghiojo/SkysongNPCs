package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.items.NpcPickup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * {@code /snpc reissue [player]} (staff): gives a new item for the picked-up NPC selected with
 * {@code /npc select}, to the sender or an online player, and voids every older item for it.
 * For an item that was lost, or to stop a duplicated one.
 */
public final class ReissueCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final Supplier<NpcPickup> pickup;
    private final Logger logger;

    /** @param pickup the pickup, made after the commands are; asked for when the command runs */
    public ReissueCommand(NpcTargeting targeting, Supplier<NpcPickup> pickup, Logger logger) {
        this.targeting = targeting;
        this.pickup = pickup;
        this.logger = logger;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("reissue")
                .requires(source -> targeting.isStaff(source.getSender()))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (sender instanceof Player player) {
                        reissue(sender, player);
                    } else {
                        targeting.send(sender, "reissue.who", Map.of());
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.word())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            String name = StringArgumentType.getString(context, "player");
                            Player receiver = Bukkit.getPlayerExact(name);
                            if (receiver == null) {
                                targeting.send(sender, "reissue.not-online", Map.of("player", name));
                            } else {
                                reissue(sender, receiver);
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void reissue(CommandSender sender, Player receiver) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = Map.of(
                "npc", targeting.npcName(npc.get().id()), "player", receiver.getName());
        if (npc.get().state() != NpcState.STORED) {
            targeting.send(sender, "reissue.placed", placeholders);
            return;
        }
        receiver.getInventory().addItem(pickup.get().reissue(npc.get().id())).values()
                .forEach(left -> receiver.getWorld().dropItem(receiver.getLocation(), left));
        logger.info(sender.getName() + " reissued the item of NPC " + placeholders.get("npc") + " ("
                + npc.get().id() + ") to " + receiver.getName() + "; older items for it no longer work.");
        targeting.send(sender, "reissue.done", placeholders);
    }
}
