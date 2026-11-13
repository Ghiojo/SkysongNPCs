package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.choices.ChoiceMenu;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code /snpc respec [source]} (staff, M14): gives back every choice the targeted NPC made,
 * or only those one source's grants paid for. Its stats lose what its points added, and the
 * points, with every other kind of choice, are unspent again.
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
                .executes(context -> respec(context.getSource().getSender(), null))
                // Sources hold colons (economy:module:trade), so the rest of the line is the source.
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("source",
                                StringArgumentType.greedyString())
                        .executes(context -> respec(context.getSource().getSender(),
                                StringArgumentType.getString(context, "source").trim())));
    }

    private int respec(CommandSender sender, @Nullable String source) {
        targeting.managedTarget(sender).ifPresent(npc -> {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("npc", targeting.npcName(npc.id()));
            placeholders.put("choices", Integer.toString(choices.respec(npc.id(), source)));
            if (source != null) {
                placeholders.put("source", source);
            }
            targeting.send(sender, source == null ? "choices.respec" : "choices.respec-source", placeholders);
        });
        return Command.SINGLE_SUCCESS;
    }
}
