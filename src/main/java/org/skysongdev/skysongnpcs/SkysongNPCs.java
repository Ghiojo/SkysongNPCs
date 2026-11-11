package org.skysongdev.skysongnpcs;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.skysongdev.skysongnpcs.access.AccessListener;
import org.skysongdev.skysongnpcs.access.AccessRegistry;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.access.ActionPresets;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.event.NpcAccessChangeEvent;
import org.skysongdev.skysongnpcs.apiimpl.EventFiringChanges;
import org.skysongdev.skysongnpcs.apiimpl.SkysongNpcsApiImpl;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.characters.StatsCharacterLookup;
import org.skysongdev.skysongnpcs.citizens.CitizensListener;
import org.skysongdev.skysongnpcs.citizens.CitizensMenuListener;
import org.skysongdev.skysongnpcs.citizens.CitizensNpcLookup;
import org.skysongdev.skysongnpcs.cloning.CitizensRemoveListener;
import org.skysongdev.skysongnpcs.cloning.CloneListener;
import org.skysongdev.skysongnpcs.cloning.NpcCloning;
import org.skysongdev.skysongnpcs.cloning.RebuildingSheetCopier;
import org.skysongdev.skysongnpcs.cloning.SheetCopier;
import org.skysongdev.skysongnpcs.commands.AccessCommand;
import org.skysongdev.skysongnpcs.citizens.CitizensNpcSpawner;
import org.skysongdev.skysongnpcs.commands.AdoptCommand;
import org.skysongdev.skysongnpcs.commands.MenuCommand;
import org.skysongdev.skysongnpcs.commands.NpcCommands;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.OwnerCommand;
import org.skysongdev.skysongnpcs.commands.ReleaseCommand;
import org.skysongdev.skysongnpcs.commands.ReloadCommand;
import org.skysongdev.skysongnpcs.commands.SubCommand;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.InfoButton;
import org.skysongdev.skysongnpcs.menu.MenuListener;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.items.BlueprintTags;
import org.skysongdev.skysongnpcs.items.Blueprints;
import org.skysongdev.skysongnpcs.items.ItemTagGateway;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.items.NpcPlaceListener;
import org.skysongdev.skysongnpcs.items.NpcPlacer;
import org.skysongdev.skysongnpcs.items.NpcSpawner;
import org.skysongdev.skysongnpcs.items.PlaceRegions;
import org.skysongdev.skysongnpcs.items.PlacingSettings;
import org.skysongdev.skysongnpcs.items.SkysongItemsTagGateway;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.Database;
import org.skysongdev.skysongnpcs.storage.DatabaseSettings;
import org.skysongdev.skysongnpcs.storage.GrantStore;
import org.skysongdev.skysongnpcs.storage.NpcStore;
import org.skysongdev.skysongnpcs.storage.Stores;

// M11: stat sheets and templates
import org.skysongdev.skysongnpcs.citizens.CitizensCarriedItems;
import org.skysongdev.skysongnpcs.commands.ModifierCommand;
import org.skysongdev.skysongnpcs.commands.ProficiencyCommand;
import org.skysongdev.skysongnpcs.commands.SheetCommand;
import org.skysongdev.skysongnpcs.commands.StatsCommand;
import org.skysongdev.skysongnpcs.sheets.CarriedItems;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.SkysongStatsSheets;
import org.skysongdev.skysongnpcs.sheets.StatSheets;
import org.skysongdev.skysongnpcs.sheets.StatsReloadListener;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;
import org.skysongdev.skysongnpcs.worldguard.WorldGuardPlaceRegions;

// M5: access policies
import org.skysongdev.skysongnpcs.commands.PolicyCommand;
import org.skysongdev.skysongnpcs.policy.EventFiringPolicyChanges;
import org.skysongdev.skysongnpcs.policy.PolicyListener;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;
import org.skysongdev.skysongnpcs.storage.PolicyStore;
// M9: inventory and equipment
import org.skysongdev.skysongnpcs.citizens.CitizensNpcContainers;
import org.skysongdev.skysongnpcs.commands.ContainerCommand;
import org.skysongdev.skysongnpcs.inventory.Container;
import org.skysongdev.skysongnpcs.inventory.ContainerButtons;
import org.skysongdev.skysongnpcs.inventory.ContainerService;
import org.skysongdev.skysongnpcs.inventory.NpcContainers;
import org.skysongdev.skysongnpcs.inventory.ReadOnlyViewListener;
// M10: wardrobe
import org.skysongdev.skysongnpcs.commands.WardrobeCommand;
import org.skysongdev.skysongnpcs.wardrobe.NpcWardrobes;
import org.skysongdev.skysongnpcs.wardrobe.SkysongWardrobeGateway;
import org.skysongdev.skysongnpcs.wardrobe.WardrobeButton;
import org.skysongdev.skysongnpcs.wardrobe.WardrobeGateway;
import org.skysongdev.skysongnpcs.wardrobe.WardrobeViewListener;

import java.io.File;
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
    private GrantStore grantStore;
    private AccessService access;
    private NpcMenuService menu;
    // M11: stat sheets and templates
    private TemplateRegistry templates;
    private NpcSheetService sheets;
    // M17
    private boolean worldGuard;
    private Blueprints blueprints;
    private NpcFactory factory;
    // M5: access policies
    private PolicyStore policyStore;
    private PolicyRegistry policies;
    // M9: inventory and equipment
    private ContainerService containers;
    // M10: wardrobe
    private NpcWardrobes wardrobes;

    /** M17: WorldGuard is optional; its flags must be registered before it enables, so here. */
    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            WorldGuardPlaceRegions.registerFlag(getLogger());
            worldGuard = true;
        }
    }

    @Override
    public void onEnable() {
        configuration = new PluginConfiguration(this);
        configuration.reload();

        try {
            Stores stores = openStores(configuration.database());
            store = stores.npcs();
            grantStore = stores.grants();
            policyStore = stores.policies();
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

        AccessRegistry grants = new AccessRegistry(grantStore, (npc, group, source, previous, current) ->
                getServer().getPluginManager().callEvent(new NpcAccessChangeEvent(npc, group, source, previous, current)));
        grants.load();
        access = new AccessService(grants, characters, new ActionPresets(() -> configuration.config()), getLogger());
        getServer().getPluginManager().registerEvents(new AccessListener(access), this);

        // M5: access policies. NPCs also allow what the policy they follow grants.
        policies = new PolicyRegistry(policyStore, new EventFiringPolicyChanges(getServer().getPluginManager()));
        policies.load();
        access.usePolicies(policies);
        getServer().getPluginManager().registerEvents(new PolicyListener(policies), this);

        // M11: stat sheets and templates
        StatSheets statSheets = hookSheets();
        templates = new TemplateRegistry(new File(getDataFolder(), "templates"),
                () -> saveResource("templates/example.yml", false), statSheets::statId, getLogger());
        templates.reload();
        sheets = new NpcSheetService(registry, statSheets, hookCarriedItems());
        // SkysongStats does not save item counts, so they are sent again on every start.
        sheets.recountAll(true);
        listenForStatsReloads(sheets);
        long recountTicks = Math.max(1, configuration.config().getInt("sheets.item-recount-seconds", 5)) * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> sheets.recountAll(false), recountTicks, recountTicks);

        // M13: cloning. A copy gets its own copy of the original's sheet; a removed NPC's sheet is deleted.
        NpcCloning cloning = new NpcCloning(hookSheetCopier(), sheets, getLogger());
        getServer().getPluginManager().registerEvents(new CloneListener(cloning), this);
        listenForNpcRemovals(cloning);

        NpcTargeting targeting = new NpcTargeting(registry, citizens, characters, configuration);

        // The NPC menu: right-click or /snpc menu.
        menu = new NpcMenuService(this, registry, citizens, configuration);
        new InfoButton(configuration, citizens, npc -> targeting.ownerName(npc.owner())).register(this, menu, access);
        getServer().getPluginManager().registerEvents(new MenuListener(menu), this);
        hookMenuClicks(menu);

        AccessCommand accessCommand = new AccessCommand(targeting, access, configuration);
        // M9: inventory and equipment, from the menu and /snpc inventory and /snpc equip.
        containers = new ContainerService(registry, citizens, hookContainers(), access, configuration);
        new ContainerButtons(containers, configuration, citizens).register(this, menu);
        getServer().getPluginManager().registerEvents(new ReadOnlyViewListener(), this);
        // M10: wardrobe, through SkysongWardrobe when it is installed.
        WardrobeGateway wardrobeGateway = hookWardrobe();
        wardrobes = new NpcWardrobes(this, registry, citizens, access, menu, configuration, wardrobeGateway);
        wardrobeGateway.guard(this, wardrobes);
        new WardrobeButton(wardrobes, access, citizens, configuration).register(this, menu);
        getServer().getPluginManager().registerEvents(new WardrobeViewListener(wardrobes), this);

        List<SubCommand> subCommands = List.of(
                new ReloadCommand(configuration, List.of(templates)),
                new AdoptCommand(targeting),
                new ReleaseCommand(targeting),
                new OwnerCommand(targeting),
                accessCommand,
                new MenuCommand(targeting, menu),
                // M11: stat sheets and templates
                new SheetCommand(targeting, access, sheets, templates, configuration),
                new StatsCommand(targeting, sheets),
                new ModifierCommand(targeting, sheets),
                new ProficiencyCommand(targeting, sheets),
                // M5: access policies
                new PolicyCommand(targeting, access, accessCommand, policies, configuration),
                // M9: inventory and equipment
                new ContainerCommand(targeting, containers, Container.INVENTORY),
                new ContainerCommand(targeting, containers, Container.EQUIPMENT),
                // M10: wardrobe
                new WardrobeCommand(targeting, wardrobes));
        NpcCommands commands = new NpcCommands(subCommands, configuration, getPluginMeta().getVersion());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.register(event.registrar()));

        // M17: blueprint items place NPCs.
        blueprints = new Blueprints(hookItemTags());
        NpcSpawner spawner = hookSpawner();
        factory = new NpcFactory(templates, registry, spawner, sheets::create);
        NpcPlacer placer = new NpcPlacer(blueprints, factory, registry, characters, spawner,
                worldGuard ? new WorldGuardPlaceRegions() : PlaceRegions.ANYWHERE,
                () -> PlacingSettings.read(configuration.config()), getServer().getPluginManager(), getLogger());
        getServer().getPluginManager().registerEvents(new NpcPlaceListener(placer, configuration), this);

        // Last, so other plugins only see the API once everything behind it is ready.
        getServer().getServicesManager().register(SkysongNpcsApi.class, new SkysongNpcsApiImpl(registry, citizens, access, menu,
                        sheets, templates, factory, blueprints, policies,
                        containers),
                this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (policyStore != null) {
            policyStore.close();
            policyStore = null;
        }
        if (grantStore != null) {
            grantStore.close();
            grantStore = null;
        }
        if (store != null) {
            // Waits for the queued writes, so no change is lost on shutdown. Last: it owns the pool.
            store.close();
            store = null;
        }
    }

    /** Connects to MySQL. Tests replace it with stores in memory. */
    protected Stores openStores(DatabaseSettings settings) {
        return Database.open(settings, getLogger()::warning);
    }

    /** Keeps records in step with Citizens and returns how the plugin asks Citizens about NPCs. */
    protected NpcLookup hookCitizens(NpcRegistry registry) {
        getServer().getPluginManager().registerEvents(new CitizensListener(registry), this);
        return new CitizensNpcLookup();
    }

    /** Right-clicking a managed NPC opens its menu. Tests have no Citizens, so they skip it. */
    protected void hookMenuClicks(NpcMenuService menu) {
        getServer().getPluginManager().registerEvents(new CitizensMenuListener(menu), this);
    }

    /** How the plugin reads players' characters, from SkysongStats. */
    protected CharacterLookup hookCharacters() {
        return new StatsCharacterLookup();
    }

    // M11: stat sheets and templates

    /** How the plugin reads and edits NPC stat sheets, through SkysongStats. */
    protected StatSheets hookSheets() {
        return new SkysongStatsSheets(this);
    }

    /** How the plugin reads the items NPCs carry, from Citizens. */
    protected CarriedItems hookCarriedItems() {
        return new CitizensCarriedItems();
    }

    /** Counts every NPC's items again after a SkysongStats reload. */
    protected void listenForStatsReloads(NpcSheetService sheets) {
        getServer().getPluginManager().registerEvents(new StatsReloadListener(sheets), this);
    }

    // M13: cloning

    /** How the plugin copies and deletes sheets in SkysongStats. */
    protected SheetCopier hookSheetCopier() {
        return new RebuildingSheetCopier(this);
    }

    /** Deletes a sheet when Citizens removes its NPC. Tests have no Citizens, so they skip it. */
    protected void listenForNpcRemovals(NpcCloning cloning) {
        getServer().getPluginManager().registerEvents(new CitizensRemoveListener(cloning), this);
    }

    /** The NPC templates; null while the plugin is disabled. */
    public TemplateRegistry templates() {
        return templates;
    }

    /** The NPCs' stat sheets; null while the plugin is disabled. */
    public NpcSheetService sheets() {
        return sheets;
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

    /** Who may do what with the NPCs; null while the plugin is disabled. */
    public AccessService access() {
        return access;
    }

    /** The NPC menu; null while the plugin is disabled. */
    public NpcMenuService menu() {
        return menu;
    }

    // M17

    /** Registers the blueprint tags with SkysongItems and returns how blueprints are read. */
    protected ItemTagGateway hookItemTags() {
        getServer().getPluginManager().registerEvents(new BlueprintTags(this), this);
        return new SkysongItemsTagGateway();
    }

    /** How new NPCs are made in Citizens. */
    protected NpcSpawner hookSpawner() {
        return new CitizensNpcSpawner();
    }

    /** For tests that place NPCs against the running plugin. */
    public Blueprints blueprints() {
        return blueprints;
    }

    /** For tests that place NPCs against the running plugin. */
    public NpcFactory factory() {
        return factory;
    }

    // M5: access policies

    /** The access policies; null while the plugin is disabled. */
    public PolicyRegistry policies() {
        return policies;
    }

    // M9: inventory and equipment

    /** How the plugin reads and edits NPCs' inventories and equipment, in Citizens. */
    protected NpcContainers hookContainers() {
        return new CitizensNpcContainers();
    }

    /** NPC inventories and equipment; null while the plugin is disabled. */
    public ContainerService containers() {
        return containers;
    }

    // M10: wardrobe

    /** SkysongWardrobe's NPC wardrobes, or none when it is not installed. */
    protected WardrobeGateway hookWardrobe() {
        if (!getServer().getPluginManager().isPluginEnabled("SkysongWardrobe")) {
            return WardrobeGateway.NONE;
        }
        // Only reached when SkysongWardrobe is enabled, so its classes are never loaded without it.
        return SkysongWardrobeGateway.hook(this);
    }

    /** NPC wardrobes; null while the plugin is disabled. */
    public NpcWardrobes wardrobes() {
        return wardrobes;
    }
}
