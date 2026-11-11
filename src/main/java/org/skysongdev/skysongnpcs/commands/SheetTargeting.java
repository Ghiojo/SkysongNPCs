package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** What the stat sheet commands share: finding the targeted NPC's sheet, and suggesting names. */
final class SheetTargeting {
    private final NpcTargeting targeting;
    private final NpcSheetService sheets;

    SheetTargeting(NpcTargeting targeting, NpcSheetService sheets) {
        this.targeting = targeting;
        this.sheets = sheets;
    }

    NpcTargeting npcs() {
        return targeting;
    }

    NpcSheetService sheets() {
        return sheets;
    }

    /** The targeted managed NPC, when it has a sheet; otherwise the sender is told why. */
    Optional<NpcRecord> withSheet(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isPresent() && !sheets.hasSheet(npc.get().id())) {
            targeting.send(sender, "sheet.none", Map.of("npc", targeting.npcName(npc.get().id())));
            return Optional.empty();
        }
        return npc;
    }

    /** Suggests the names {@code names} gives that start with what was typed. */
    static SuggestionProvider<CommandSourceStack> suggest(Supplier<Collection<String>> names) {
        return (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String name : names.get()) {
                if (name.toLowerCase().startsWith(typed)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }
}
