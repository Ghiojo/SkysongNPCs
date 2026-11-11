package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.api.choices.PointGrant;
import org.skysongdev.skysongnpcs.choices.StatPoints;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /snpc points [list | grant <amount> [stats] | take <amount>]} (staff, M14): the
 * targeted NPC's stat points. Staff grants have the source {@code staff}, and take only takes
 * back unspent staff points. {@code stats} is a comma list, such as {@code str,dex}; without
 * it the points go on the NPC's default eligible stats.
 */
public final class PointsCommand implements SubCommand {
    /** The source of points staff give. */
    public static final String SOURCE = "staff";
    private static final String AMOUNT = "amount";
    private static final String STATS = "stats";

    private final NpcTargeting targeting;
    private final StatPoints points;

    public PointsCommand(NpcTargeting targeting, StatPoints points) {
        this.targeting = targeting;
        this.points = points;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("points")
                .requires(source -> targeting.isStaff(source.getSender()))
                .executes(context -> list(context.getSource().getSender()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(context -> list(context.getSource().getSender())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("grant")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(AMOUNT, IntegerArgumentType.integer(1))
                                .executes(context -> grant(context.getSource().getSender(),
                                        IntegerArgumentType.getInteger(context, AMOUNT), ""))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(STATS, StringArgumentType.word())
                                        .executes(context -> grant(context.getSource().getSender(),
                                                IntegerArgumentType.getInteger(context, AMOUNT),
                                                StringArgumentType.getString(context, STATS))))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("take")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(AMOUNT, IntegerArgumentType.integer(1))
                                .executes(context -> take(context.getSource().getSender(),
                                        IntegerArgumentType.getInteger(context, AMOUNT)))));
    }

    private int list(CommandSender sender) {
        targeting.managedTarget(sender).ifPresent(npc -> {
            String name = targeting.npcName(npc.id());
            List<PointGrant> grants = points.grants(npc.id());
            if (grants.isEmpty()) {
                targeting.send(sender, "choices.points.list.empty", Map.of("npc", name));
                return;
            }
            targeting.send(sender, "choices.points.list.header", Map.of("npc", name,
                    "points", Integer.toString(points.unspent(npc.id()))));
            for (PointGrant grant : grants) {
                targeting.send(sender, "choices.points.list.entry", Map.of(
                        "source", grant.source(), "amount", Integer.toString(grant.amount()),
                        "spent", Integer.toString(grant.spent()),
                        "stats", grant.eligible().isEmpty() ? "default" : String.join(", ", grant.eligible())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int grant(CommandSender sender, int amount, String stats) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        List<String> eligible = Arrays.stream(stats.split(",")).map(String::trim).filter(stat -> !stat.isEmpty()).toList();
        try {
            points.grant(npc.get().id(), SOURCE, amount, eligible);
        } catch (IllegalArgumentException unknownStat) {
            targeting.send(sender, "choices.points.unknown-stat", Map.of("stats", stats));
            return Command.SINGLE_SUCCESS;
        }
        targeting.send(sender, "choices.points.granted", Map.of("npc", targeting.npcName(npc.get().id()),
                "points", Integer.toString(amount)));
        return Command.SINGLE_SUCCESS;
    }

    private int take(CommandSender sender, int amount) {
        targeting.managedTarget(sender).ifPresent(npc -> targeting.send(sender, "choices.points.taken", Map.of(
                "npc", targeting.npcName(npc.id()),
                "points", Integer.toString(points.take(npc.id(), SOURCE, amount)))));
        return Command.SINGLE_SUCCESS;
    }
}
