package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * {@code /snpc release} (staff): the targeted NPC stops being a Skysong NPC. Its record is
 * deleted and the Citizens NPC stays as it is.
 */
public final class ReleaseCommand implements SubCommand {
    private final NpcTargeting targeting;

    public ReleaseCommand(NpcTargeting targeting) {
        this.targeting = targeting;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("release")
                .requires(source -> targeting.isStaff(source.getSender()))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    targeting.managedTarget(sender).ifPresent(npc -> {
                        targeting.registry().release(npc.id());
                        targeting.send(sender, "release.done", Map.of("npc", targeting.npcName(npc.id())));
                    });
                    return Command.SINGLE_SUCCESS;
                });
    }
}
