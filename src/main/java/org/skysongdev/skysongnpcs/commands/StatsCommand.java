package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.StatSheets.StatEdit;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code /snpc stats <stat> <set|add|reduce> <amount>} (staff): changes a stat on the
 * targeted NPC's sheet, like SkysongStats' own stat command does for players. Players never
 * edit an NPC's stats directly.
 */
public final class StatsCommand implements SubCommand {
    private static final String STAT = "stat";
    private static final String AMOUNT = "amount";

    private final SheetTargeting targeting;

    public StatsCommand(NpcTargeting targeting, NpcSheetService sheets) {
        this.targeting = new SheetTargeting(targeting, sheets);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        RequiredArgumentBuilder<CommandSourceStack, String> stat =
                RequiredArgumentBuilder.<CommandSourceStack, String>argument(STAT, StringArgumentType.word())
                        .suggests(SheetTargeting.suggest(() -> targeting.sheets().stats().statIds()));
        for (StatEdit edit : StatEdit.values()) {
            // Only SET takes a negative amount, as in SkysongStats.
            IntegerArgumentType amount = edit == StatEdit.SET
                    ? IntegerArgumentType.integer() : IntegerArgumentType.integer(0);
            stat.then(LiteralArgumentBuilder.<CommandSourceStack>literal(edit.name().toLowerCase(Locale.ROOT))
                    .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(AMOUNT, amount)
                            .executes(context -> {
                                edit(context.getSource().getSender(), StringArgumentType.getString(context, STAT),
                                        edit, IntegerArgumentType.getInteger(context, AMOUNT));
                                return Command.SINGLE_SUCCESS;
                            })));
        }
        return LiteralArgumentBuilder.<CommandSourceStack>literal("stats")
                .requires(source -> targeting.npcs().isStaff(source.getSender()))
                .then(stat);
    }

    private void edit(CommandSender sender, String stat, StatEdit edit, int amount) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return;
        }
        OptionalInt value = targeting.sheets().stats().editStat(npc.get().id(), stat, edit, amount);
        if (value.isEmpty()) {
            targeting.npcs().send(sender, "stats.unknown", Map.of("stat", stat));
            return;
        }
        targeting.npcs().send(sender, "stats.done", Map.of(
                "npc", targeting.npcs().npcName(npc.get().id()), "stat", stat,
                "value", String.valueOf(value.getAsInt())));
    }
}
