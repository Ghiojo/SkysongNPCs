package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.choices.ChoiceMenu;

import java.util.Map;

/**
 * {@code /snpc choose} (M14): opens the targeted NPC's setup while it is in setup, otherwise
 * its choice menu, the same as the Assign points button. Needs the {@code choose} action.
 */
public final class ChooseCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final AccessService access;
    private final ChoiceMenu choices;

    public ChooseCommand(NpcTargeting targeting, AccessService access, ChoiceMenu choices) {
        this.targeting = targeting;
        this.access = access;
        this.choices = choices;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("choose")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getSender();
                    targeting.managedTarget(player).ifPresent(npc -> {
                        if (!access.may(player, npc, NpcAction.CHOOSE)) {
                            targeting.send(player, "choices.not-allowed", Map.of("npc", targeting.npcName(npc.id())));
                        } else {
                            choices.open(player, npc.id());
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                });
    }
}
