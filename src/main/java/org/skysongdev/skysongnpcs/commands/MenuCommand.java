package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;

import java.util.Map;

/** {@code /snpc menu}: opens the targeted NPC's menu, the same one right-clicking it opens. */
public final class MenuCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final NpcMenuService menu;

    public MenuCommand(NpcTargeting targeting, NpcMenuService menu) {
        this.targeting = targeting;
        this.menu = menu;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("menu")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getSender();
                    targeting.managedTarget(player).ifPresent(npc -> {
                        if (!menu.open(player, npc.id())) {
                            targeting.send(player, "menu.nothing", Map.of("npc", targeting.npcName(npc.id())));
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                });
    }
}
