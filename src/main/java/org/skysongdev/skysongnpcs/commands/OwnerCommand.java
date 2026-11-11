package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /snpc owner}: shows who owns the targeted NPC. {@code /snpc owner <player> [profile]}
 * gives it to another character (the owner or staff); {@code /snpc owner server} gives it to
 * the server (staff only).
 */
public final class OwnerCommand implements SubCommand {
    private final NpcTargeting targeting;

    public OwnerCommand(NpcTargeting targeting) {
        this.targeting = targeting;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("owner")
                .requires(source -> source.getSender() instanceof Player || targeting.isStaff(source.getSender()))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    ownedTarget(sender).ifPresent(npc -> targeting.send(sender, "owner.show", Map.of(
                            "npc", targeting.npcName(npc.id()), "owner", targeting.ownerName(npc.owner()))));
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("server")
                        .requires(source -> targeting.isStaff(source.getSender()))
                        .executes(context -> {
                            give(context.getSource().getSender(), NpcOwner.server());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(OwnerArguments.character(targeting,
                        (context, owner) -> give(context.getSource().getSender(), owner)));
    }

    private void give(CommandSender sender, NpcOwner owner) {
        Optional<NpcRecord> npc = ownedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = Map.of(
                "npc", targeting.npcName(npc.get().id()), "owner", targeting.ownerName(owner));
        if (npc.get().owner().equals(owner)) {
            targeting.send(sender, "owner.same", placeholders);
            return;
        }
        targeting.registry().setOwner(npc.get().id(), owner);
        targeting.send(sender, "owner.done", placeholders);
    }

    /** The targeted managed NPC, when the sender is staff or its owner. */
    private Optional<NpcRecord> ownedTarget(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isPresent() && !targeting.isStaff(sender) && !targeting.isOwner(sender, npc.get())) {
            targeting.send(sender, "npc.not-owner", Map.of("npc", targeting.npcName(npc.get().id())));
            return Optional.empty();
        }
        return npc;
    }
}
