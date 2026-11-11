package org.skysongdev.skysongnpcs;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.apiimpl.SkysongNpcsApiImpl;
import org.skysongdev.skysongnpcs.commands.NpcCommands;
import org.skysongdev.skysongnpcs.commands.ReloadCommand;
import org.skysongdev.skysongnpcs.commands.SubCommand;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;

import java.util.List;

/**
 * Entry point of SkysongNPCs. It only builds and wires the plugin's parts;
 * each feature lives in its own package and is registered here as it is added.
 *
 * <p>Not final, because MockBukkit loads the plugin in tests through a generated subclass.
 */
public class SkysongNPCs extends JavaPlugin {
    private PluginConfiguration configuration;

    @Override
    public void onEnable() {
        configuration = new PluginConfiguration(this);
        configuration.reload();

        List<SubCommand> subCommands = List.of(new ReloadCommand(configuration));
        NpcCommands commands = new NpcCommands(subCommands, configuration, getPluginMeta().getVersion());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.register(event.registrar()));

        // Last, so other plugins only see the API once everything behind it is ready.
        getServer().getServicesManager().register(SkysongNpcsApi.class, new SkysongNpcsApiImpl(), this,
                ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    /** The plugin's files, for tests and for the parts wired up here. */
    public PluginConfiguration configuration() {
        return configuration;
    }
}
