package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /snpc proficiency <proficiency> <tier>} (staff): sets a proficiency's tier on the
 * targeted NPC's sheet. NPCs never train; this and, later, proficiency picks are the only
 * ways they gain tiers.
 */
public final class ProficiencyCommand implements SubCommand {
    private static final String PROFICIENCY = "proficiency";
    private static final String TIER = "tier";

    private final SheetTargeting targeting;

    public ProficiencyCommand(NpcTargeting targeting, NpcSheetService sheets) {
        this.targeting = new SheetTargeting(targeting, sheets);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("proficiency")
                .requires(source -> targeting.npcs().isStaff(source.getSender()))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PROFICIENCY, StringArgumentType.word())
                        .suggests(SheetTargeting.suggest(() -> targeting.sheets().stats().proficiencyIds()))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(TIER, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(() -> targeting.sheets().stats().tierIds()))
                                .executes(context -> {
                                    set(context.getSource().getSender(),
                                            StringArgumentType.getString(context, PROFICIENCY),
                                            StringArgumentType.getString(context, TIER));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void set(CommandSender sender, String proficiency, String tier) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcs().npcName(npc.get().id());
        Map<String, String> placeholders = Map.of("npc", name, "proficiency", proficiency, "tier", tier);
        String key = switch (targeting.sheets().stats().setProficiency(npc.get().id(), proficiency, tier)) {
            case DONE -> "proficiency.done";
            case NO_SHEET -> "sheet.none";
            case UNKNOWN_PROFICIENCY -> "proficiency.unknown";
            case UNKNOWN_TIER -> "proficiency.unknown-tier";
        };
        targeting.npcs().send(sender, key, placeholders);
    }
}
