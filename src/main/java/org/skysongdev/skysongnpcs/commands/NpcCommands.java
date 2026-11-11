package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Builds {@code /snpc}, alias {@code /skysongnpcs}, out of its subcommands. On its own,
 * {@code /snpc} says which version is running and how to get help.
 */
public final class NpcCommands {
    static final String ROOT = "snpc";
    static final List<String> ALIASES = List.of("skysongnpcs");
    private static final String DESCRIPTION = "Manage Skysong NPCs";

    private final List<SubCommand> subCommands;
    private final PluginConfiguration configuration;
    private final String version;

    public NpcCommands(List<SubCommand> subCommands, PluginConfiguration configuration, String version) {
        this.subCommands = List.copyOf(subCommands);
        this.configuration = configuration;
        this.version = version;
    }

    /** The command tree; also used by tests to run commands without a server. */
    public LiteralCommandNode<CommandSourceStack> tree() {
        LiteralArgumentBuilder<CommandSourceStack> root = LiteralArgumentBuilder.<CommandSourceStack>literal(ROOT)
                .executes(context -> {
                    configuration.messages().send(context.getSource().getSender(), "info", Map.of("version", version));
                    return Command.SINGLE_SUCCESS;
                });
        subCommands.forEach(subCommand -> root.then(subCommand.node()));
        return root.build();
    }

    /** Registers the tree with Paper; call from the COMMANDS lifecycle event. */
    public void register(Commands registrar) {
        registrar.register(tree(), DESCRIPTION, ALIASES);
    }
}
