package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.config.Reloadable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /snpc reload}: rereads config.yml, messages.yml and every {@link Reloadable} (the
 * templates folder). A file with a mistake keeps its previous values.
 */
public final class ReloadCommand implements SubCommand {
    private final PluginConfiguration configuration;
    private final List<Reloadable> others;

    public ReloadCommand(PluginConfiguration configuration) {
        this(configuration, List.of());
    }

    public ReloadCommand(PluginConfiguration configuration, List<Reloadable> others) {
        this.configuration = configuration;
        this.others = List.copyOf(others);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
                .requires(source -> source.getSender().hasPermission(Permissions.ADMIN))
                .executes(context -> {
                    List<String> broken = new ArrayList<>(configuration.reload());
                    others.forEach(reloadable -> broken.addAll(reloadable.reload()));
                    // Sent after the reload, so the reply already uses the new messages.yml.
                    if (broken.isEmpty()) {
                        configuration.messages().send(context.getSource().getSender(), "reload.done");
                    } else {
                        configuration.messages().send(context.getSource().getSender(), "reload.failed",
                                Map.of("files", String.join(", ", broken)));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
