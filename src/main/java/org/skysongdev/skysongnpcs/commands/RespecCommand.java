package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.skysongdev.skysongnpcs.choices.ChoiceMenu;

import java.util.Map;

/**
 * {@code /snpc respec} (staff, M14): gives back every choice the targeted NPC made. Its
 * stats lose what its points added, and the points, with every other kind of choice, are
 * unspent again.
 */
public final class RespecCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final ChoiceMenu choices;

    public RespecCommand(NpcTargeting targeting, ChoiceMenu choices) {
        this.targeting = targeting;
        this.choices = choices;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("respec")
                .requires(source -> targeting.isStaff(source.getSender()))
                .executes(context -> {
                    targeting.managedTarget(context.getSource().getSender()).ifPresent(npc ->
                            targeting.send(context.getSource().getSender(), "choices.respec", Map.of(
                                    "npc", targeting.npcName(npc.id()),
                                    "choices", Integer.toString(choices.respec(npc.id())))));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
