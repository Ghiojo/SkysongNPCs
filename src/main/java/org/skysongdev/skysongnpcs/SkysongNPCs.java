package org.skysongdev.skysongnpcs;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.apiimpl.EventFiringChanges;
import org.skysongdev.skysongnpcs.apiimpl.SkysongNpcsApiImpl;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.characters.StatsCharacterLookup;
import org.skysongdev.skysongnpcs.citizens.CitizensListener;
import org.skysongdev.skysongnpcs.citizens.CitizensNpcLookup;
import org.skysongdev.skysongnpcs.commands.AdoptCommand;
import org.skysongdev.skysongnpcs.commands.NpcCommands;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.OwnerCommand;
import org.skysongdev.skysongnpcs.commands.ReleaseCommand;
import org.skysongdev.skysongnpcs.commands.ReloadCommand;
import org.skysongdev.skysongnpcs.commands.SubCommand;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.Database;
import org.skysongdev.skysongnpcs.storage.DatabaseSettings;
import org.skysongdev.skysongnpcs.storage.NpcStore;

import java.util.List;
import java.util.logging.Level;

/**
 * Entry point of SkysongNPCs. It only builds and wires the plugin's parts;
 * each feature lives in its own package and is registered here as it is added.
 *
 * <p>Not final: tests load a subclass that keeps records in memory and fakes Citizens and
 * SkysongStats, which are not on the test classpath.
 */
public class SkysongNPCs extends JavaPlugin {
    private PluginConfiguration configuration;
    private NpcStore store;
    private NpcRegistry registry;
    private NpcLookup citizens;
    private CharacterLookup characters;

    @Override
    public void onEnable() {
        configuration = new PluginConfiguration(this);
        configuration.reload();

        try {
            store = openStore(configuration.database());
        } catch (RuntimeException failed) {
            getLogger().log(Level.SEVERE, "Could not reach the database in config.yml (" + configuration.database()
                    + "). SkysongNPCs is disabled.", failed);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registry = new NpcRegistry(store, new EventFiringChanges(getServer().getPluginManager()));
        registry.load();
        getLogger().info("Loaded " + registry.all().size() + " Skysong NPC(s).");

        citizens = hookCitizens(registry);
        characters = hookCharacters();

        NpcTargeting targeting = new NpcTargeting(registry, citizens, characters, configuration);
        List<SubCommand> subCommands = List.of(
                new ReloadCommand(configuration),
                new AdoptCommand(targeting),
                new ReleaseCommand(targeting),
                new OwnerCommand(targeting));
        NpcCommands commands = new NpcCommands(subCommands, configuration, getPluginMeta().getVersion());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.register(event.registrar()));

        // Last, so other plugins only see the API once everything behind it is ready.
        getServer().getServicesManager().register(SkysongNpcsApi.class, new SkysongNpcsApiImpl(registry, citizens),
                this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (store != null) {
            // Waits for the queued writes, so no change is lost on shutdown.
            store.close();
            store = null;
        }
    }

    /** Connects to MySQL. Tests replace it with a store in memory. */
    protected NpcStore openStore(DatabaseSettings settings) {
        return Database.open(settings, getLogger()::warning);
    }

    /** Keeps records in step with Citizens and returns how the plugin asks Citizens about NPCs. */
    protected NpcLookup hookCitizens(NpcRegistry registry) {
        getServer().getPluginManager().registerEvents(new CitizensListener(registry), this);
        return new CitizensNpcLookup();
    }

    /** How the plugin reads players' characters, from SkysongStats. */
    protected CharacterLookup hookCharacters() {
        return new StatsCharacterLookup();
    }

    /** The plugin's files, for tests and for the parts wired up here. */
    public PluginConfiguration configuration() {
        return configuration;
    }

    /** The managed NPCs; null while the plugin is disabled. */
    public NpcRegistry registry() {
        return registry;
    }

    /** For tests that build commands against the running plugin. */
    public NpcLookup citizens() {
        return citizens;
    }

    /** For tests that build commands against the running plugin. */
    public CharacterLookup characters() {
        return characters;
    }
}
