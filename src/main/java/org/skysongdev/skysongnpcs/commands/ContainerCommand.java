package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.inventory.Container;
import org.skysongdev.skysongnpcs.inventory.ContainerService;

/**
 * {@code /snpc inventory [view]} and {@code /snpc equip [view]}: opens the targeted NPC's
 * inventory or equipment, in Citizens' editor for whoever may change it and as a read-only copy
 * for whoever may only see it. {@code view} asks for the copy even when editing is allowed.
 */
public final class ContainerCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final ContainerService containers;
    private final Container container;

    public ContainerCommand(NpcTargeting targeting, ContainerService containers, Container container) {
        this.targeting = targeting;
        this.containers = containers;
        this.container = container;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(container == Container.INVENTORY ? "inventory" : "equip")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> open(context.getSource(), false))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("view")
                        .executes(context -> open(context.getSource(), true)));
    }

    private int open(CommandSourceStack source, boolean viewOnly) {
        Player player = (Player) source.getSender();
        targeting.managedTarget(player).ifPresent(npc -> containers.openAndTell(player, npc.id(), container, viewOnly));
        return Command.SINGLE_SUCCESS;
    }
}
