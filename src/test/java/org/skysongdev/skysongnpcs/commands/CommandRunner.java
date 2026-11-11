package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Runs {@code /snpc ...} against a command tree, as a given sender, without a server's dispatcher. */
public final class CommandRunner {
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

    public CommandRunner(PluginConfiguration configuration, String version, SubCommand... subCommands) {
        dispatcher.getRoot().addChild(new NpcCommands(List.of(subCommands), configuration, version).tree());
    }

    /** Runs the command; throws if Brigadier refuses it (unknown, bad syntax or no permission). */
    public int run(CommandSender sender, String command) throws CommandSyntaxException {
        return dispatcher.execute(command, source(sender));
    }

    private static CommandSourceStack source(CommandSender sender) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        when(source.getExecutor()).thenReturn(sender instanceof Entity entity ? entity : null);
        return source;
    }
}
