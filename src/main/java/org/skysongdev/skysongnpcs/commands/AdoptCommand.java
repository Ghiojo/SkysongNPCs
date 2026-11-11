package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /snpc adopt [player] [profile]} (staff): makes the targeted Citizens NPC a Skysong
 * NPC. Without a player the server owns it; with a player and no profile, the character
 * they are playing now. The NPC gets its stat sheet in the same step.
 */
public final class AdoptCommand implements SubCommand {
    private final NpcTargeting targeting;

    public AdoptCommand(NpcTargeting targeting) {
        this.targeting = targeting;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("adopt")
                .requires(source -> targeting.isStaff(source.getSender()))
                .executes(context -> {
                    adopt(context.getSource().getSender(), NpcOwner.server());
                    return Command.SINGLE_SUCCESS;
                })
                .then(OwnerArguments.character(targeting,
                        (context, owner) -> adopt(context.getSource().getSender(), owner)));
    }

    private void adopt(CommandSender sender, NpcOwner owner) {
        Optional<UUID> npc = targeting.target(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcName(npc.get());
        Optional<NpcRecord> existing = targeting.registry().get(npc.get());
        if (existing.isPresent()) {
            targeting.send(sender, "adopt.already",
                    Map.of("npc", name, "owner", targeting.ownerName(existing.get().owner())));
            return;
        }
        try {
            targeting.registry().adopt(npc.get(), owner);
        } catch (RuntimeException failed) {
            // Its stat sheet could not be made, so it was not adopted.
            targeting.send(sender, "adopt.failed", Map.of("npc", name, "reason", String.valueOf(failed.getMessage())));
            return;
        }
        targeting.send(sender, "adopt.done", Map.of("npc", name, "owner", targeting.ownerName(owner)));
    }
}
