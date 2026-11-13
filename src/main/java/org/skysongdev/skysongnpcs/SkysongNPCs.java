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
import org.skysongdev.skysongnpcs.commands.GroupCommand;
import org.skysongdev.skysongnpcs.groups.LuckPermsGroups;
import org.skysongdev.skysongnpcs.groups.PlayerGroupProvider;
import org.skysongdev.skysongnpcs.groups.PlayerGroupRegistry;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroups;
import org.skysongdev.skysongnpcs.api.access.GroupProvider;
import org.skysongdev.skysongnpcs.storage.GroupStore;
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
import org.skysongdev.skysongnpcs.config.Reloadable;
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
import org.skysongdev.skysongnpcs.items.SpotFinder;
import org.skysongdev.skysongnpcs.items.PlaceRegions;
import org.skysongdev.skysongnpcs.items.PlacingSettings;
import org.skysongdev.skysongnpcs.items.SkysongItemsTagGateway;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.npc.NpcRemover;
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
import org.skysongdev.skysongnpcs.sheets.SheetButton;
import org.skysongdev.skysongnpcs.sheets.SheetGuarantee;
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
// M8: appearance
import org.skysongdev.skysongnpcs.appearance.AppearanceButton;
import org.skysongdev.skysongnpcs.appearance.AppearanceDriver;
import org.skysongdev.skysongnpcs.appearance.AppearanceMenu;
import org.skysongdev.skysongnpcs.appearance.AppearanceService;
import org.skysongdev.skysongnpcs.appearance.CitizensCommandGuard;
import org.skysongdev.skysongnpcs.citizens.CitizensAppearance;
import org.skysongdev.skysongnpcs.commands.AppearanceCommands;

// M7: movement
import org.skysongdev.skysongnpcs.citizens.CitizensMover;
import org.skysongdev.skysongnpcs.commands.MovementCommands;
import org.skysongdev.skysongnpcs.movement.MovementButton;
import org.skysongdev.skysongnpcs.movement.MovementService;
import org.skysongdev.skysongnpcs.movement.MovementSettings;
import org.skysongdev.skysongnpcs.movement.NpcMover;
// M18: pickup and anti-clone
import org.skysongdev.skysongnpcs.commands.PickupCommand;
import org.skysongdev.skysongnpcs.commands.ReissueCommand;
import org.skysongdev.skysongnpcs.items.NpcItemListener;
import org.skysongdev.skysongnpcs.items.NpcPickup;
import org.skysongdev.skysongnpcs.items.NpcTickets;
import org.skysongdev.skysongnpcs.items.PickupButton;
import org.skysongdev.skysongnpcs.storage.NpcItemStore;
// M14: choices and first setup
import org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent;
import org.skysongdev.skysongnpcs.api.event.NpcSetupCompleteEvent;
import org.skysongdev.skysongnpcs.choices.AncestryStep;
import org.skysongdev.skysongnpcs.choices.AssignPointsButton;
import org.skysongdev.skysongnpcs.choices.ChoiceLifecycleListener;
import org.skysongdev.skysongnpcs.choices.ChoiceMenu;
import org.skysongdev.skysongnpcs.choices.ChoiceSettings;
import org.skysongdev.skysongnpcs.choices.PointsPage;
import org.skysongdev.skysongnpcs.choices.SetupFlow;
import org.skysongdev.skysongnpcs.choices.SkysongStatsBases;
import org.skysongdev.skysongnpcs.choices.StartingPoints;
import org.skysongdev.skysongnpcs.choices.StatBases;
import org.skysongdev.skysongnpcs.choices.StatPoints;
import org.skysongdev.skysongnpcs.choices.TemplateChoiceRegistry;
import org.skysongdev.skysongnpcs.choices.TemplateChoices;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGuiListener;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.commands.ChooseCommand;
import org.skysongdev.skysongnpcs.commands.PointsCommand;
import org.skysongdev.skysongnpcs.commands.RespecCommand;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.storage.ChoiceStore;
import java.util.function.Supplier;
import java.util.Map;
import java.util.function.Function;

// M15: proficiency picks
import org.skysongdev.skysongnpcs.commands.PicksCommand;
import org.skysongdev.skysongnpcs.picks.PicksListener;
import org.skysongdev.skysongnpcs.picks.PicksPage;
import org.skysongdev.skysongnpcs.picks.ProficiencyCatalog;
import org.skysongdev.skysongnpcs.picks.ProficiencyPicks;
import org.skysongdev.skysongnpcs.picks.SkysongStatsProficiencyCatalog;
import org.skysongdev.skysongnpcs.picks.StartingPicks;
import org.skysongdev.skysongnpcs.picks.TemplatePicks;
import org.skysongdev.skysongnpcs.storage.PickStore;

// M16: abilities
import org.skysongdev.skysongnpcs.abilities.AbilitiesButton;
import org.skysongdev.skysongnpcs.abilities.AbilitiesMenu;
import org.skysongdev.skysongnpcs.abilities.AbilityChoices;
import org.skysongdev.skysongnpcs.abilities.AbilityListener;
import org.skysongdev.skysongnpcs.abilities.AbilityRegistry;
import org.skysongdev.skysongnpcs.abilities.AbilityService;
import org.skysongdev.skysongnpcs.abilities.AbilityText;
import org.skysongdev.skysongnpcs.abilities.PluginAbilityChoices;
import org.skysongdev.skysongnpcs.abilities.StartingAbilities;
import org.skysongdev.skysongnpcs.abilities.TemplateAbilities;
import org.skysongdev.skysongnpcs.commands.AbilitiesCommand;
import org.skysongdev.skysongnpcs.storage.AbilityStore;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

// M12: injuries, conditions and ancestry
import org.skysongdev.skysongnpcs.commands.AncestryCommand;
import org.skysongdev.skysongnpcs.commands.ConditionCommand;
import org.skysongdev.skysongnpcs.commands.InjuryCommand;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongnpcs.sheets.SkysongStatsEffects;

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
    private SheetGuarantee sheetGuarantee;
    // M12: injuries, conditions and ancestry
    private SheetEffects effects;
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
    // M4
    private GroupStore groupStore;
    private PlayerGroupRegistry groups;
    // M8: appearance
    private AppearanceService appearance;

    // M7: movement
    private MovementService movement;
    // M18: pickup and anti-clone
    private NpcItemStore itemStore;
    private NpcPickup pickup;
    // M14: choices and first setup
    private ChoiceStore choiceStore;
    private StatPoints statPoints;
    private ChoiceMenu choiceMenu;
    private TemplateChoiceRegistry templateChoices;
    // M15: proficiency picks
    private PickStore pickStore;
    private ProficiencyPicks picks;

    /** M17: WorldGuard is optional; its flags must be registered before it enables, so here. */
    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            WorldGuardPlaceRegions.registerFlag(getLogger());
            worldGuard = true;
        }
    }

    // M16: abilities
    private AbilityStore abilityStore;
    private AbilityService abilities;
    private AbilitiesMenu abilitiesMenu;

    @Override
    public void onEnable() {
        configuration = new PluginConfiguration(this);
        configuration.reload();

        try {
            Stores stores = openStores(configuration.database());
            store = stores.npcs();
            grantStore = stores.grants();
            policyStore = stores.policies();
            groupStore = stores.groups();
            itemStore = stores.items();
            choiceStore = stores.choices();
            pickStore = stores.picks();

            // M16: abilities
            abilityStore = stores.abilities();
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
        // M14: the choice keys of config.yml and of the template files.
        ChoiceSettings choiceSettings = new ChoiceSettings(configuration::config, statSheets::statId, getLogger());
        choiceSettings.reload();
        templateChoices = new TemplateChoiceRegistry(
                new File(getDataFolder(), "templates"), statSheets::statId, getLogger());
        templateChoices.reload();
        // M15: the proficiency-picks key of the template files.
        ProficiencyCatalog proficiencies = hookProficiencies();
        TemplatePicks templatePicks = new TemplatePicks(new File(getDataFolder(), "templates"), proficiencies, getLogger());
        templatePicks.reload();
        sheets = new NpcSheetService(registry, statSheets, hookCarriedItems());
        // Every managed NPC has a stat sheet: adopting makes it in the same step, and NPCs
        // registered without one (before this rule, or whose character was deleted) get one now.
        sheetGuarantee = new SheetGuarantee(registry, sheets, templates::get,
                () -> configuration.config().getString("sheets.adopt-template", ""), getLogger());
        registry.onAdopt(record -> sheetGuarantee.ensure(record.id()));
        sheetGuarantee.repairAll();
        // SkysongStats does not save item counts, so they are sent again on every start.
        sheets.recountAll(true);
        listenForStatsReloads(sheets);
        long recountTicks = Math.max(1, configuration.config().getInt("sheets.item-recount-seconds", 5)) * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> sheets.recountAll(false), recountTicks, recountTicks);

        // M13: cloning. A copy gets its own copy of the original's sheet; a removed NPC's sheet is deleted.
        NpcCloning cloning = new NpcCloning(hookSheetCopier(), sheets, sheetGuarantee::ensure, getLogger());
        getServer().getPluginManager().registerEvents(new CloneListener(cloning), this);
        listenForNpcRemovals(cloning);

        // M4: player groups (group:) and LuckPerms groups (lp:) for grants.
        groups = new PlayerGroupRegistry(groupStore, grants, policies);
        groups.load();
        access.registerBuiltIn(PlayerGroup.TYPE, new PlayerGroupProvider(groups));
        hookLuckPerms().ifPresent(luckPerms -> access.registerBuiltIn(PlayerGroups.LUCKPERMS_TYPE, luckPerms));

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
        // M16: abilities, from abilities.yml; reloads bring every NPC in line with the file.
        AbilityRegistry abilityFile = new AbilityRegistry(new File(getDataFolder(), "abilities.yml"),
                () -> saveResource("abilities.yml", false), statSheets, proficiencies::skillId, getLogger());
        abilityFile.reload();
        abilities = new AbilityService(registry, abilityFile, abilityStore, statSheets, hookAbilityChoices(),
                event -> getServer().getPluginManager().callEvent(event));
        abilities.load();
        // Brought in line with abilities.yml further down, once stat points and picks exist.
        TemplateAbilities templateAbilities = new TemplateAbilities(new File(getDataFolder(), "templates"), getLogger());
        templateAbilities.reload();
        AbilityText abilityText = new AbilityText(configuration);
        abilitiesMenu = new AbilitiesMenu(registry, citizens, abilities, abilityText, configuration);
        getServer().getPluginManager().registerEvents(abilitiesMenu, this);
        getServer().getPluginManager().registerEvents(new AbilityListener(abilities), this);
        new AbilitiesButton(abilities, abilitiesMenu, configuration).register(this, menu, access);
        Reloadable abilityReload = () -> {
            if (!abilityFile.reload()) {
                return List.of("abilities.yml");
            }
            abilities.syncAll();
            return List.of();
        };

        // The NPC menu's Sheet button shows the sheet as /snpc sheet does.
        SheetCommand sheetCommand = new SheetCommand(targeting, access, sheets, templates, configuration,
                npc -> abilityText.sheetLine(abilities.of(npc)));
        new SheetButton(configuration, npc -> citizens.name(npc.id()), sheetCommand::showSheet)
                .register(this, menu, access);

        List<SubCommand> subCommands = List.of(
                new ReloadCommand(configuration, List.of(templates, choiceSettings, templateChoices, templatePicks,
                        templateAbilities, abilityReload)),
                new AdoptCommand(targeting),
                new ReleaseCommand(targeting),
                new OwnerCommand(targeting),
                accessCommand,
                new MenuCommand(targeting, menu),
                // M11: stat sheets and templates
                sheetCommand,
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

        // M7: movement (/snpc select, tphere, follow, look, and the Movement button)
        movement = new MovementService(registry, hookMover(), () -> MovementSettings.read(configuration.config()));
        new MovementButton(movement, access, citizens, configuration).register(this, menu);
        subCommands = Stream.concat(subCommands.stream(),
                new MovementCommands(targeting, citizens, access, movement, configuration).subCommands().stream()).toList();

        // M4: player groups (/snpc group)
        subCommands = Stream.concat(subCommands.stream(),
                Stream.of(new GroupCommand(targeting, groups, configuration))).toList();

        // M12: injuries, conditions and ancestry on NPC sheets.
        effects = hookEffects();
        subCommands = Stream.concat(subCommands.stream(), Stream.of(
                new InjuryCommand(targeting, sheets, effects),
                new ConditionCommand(targeting, sheets, effects, configuration),
                new AncestryCommand(targeting, sheets, effects, configuration))).toList();

        // M8: appearance (/snpc appearance, name, skin, nameplate, pose, sit, glow, the Appearance
        // button and menu), and Citizens' own commands kept to staff on Skysong NPCs.
        AppearanceDriver appearanceDriver = hookAppearance();
        appearance = new AppearanceService(registry, appearanceDriver, () -> configuration.config());
        AppearanceMenu appearanceMenu = new AppearanceMenu(this, registry, citizens, access, appearance, configuration, menu);
        new AppearanceButton(configuration, citizens, appearanceMenu).register(this, menu);
        getServer().getPluginManager().registerEvents(appearanceMenu, this);
        getServer().getPluginManager().registerEvents(new CitizensCommandGuard(registry, appearanceDriver, citizens,
                () -> configuration.config(), () -> configuration.messages()), this);
        subCommands = Stream.concat(subCommands.stream(),
                new AppearanceCommands(targeting, access, appearance, appearanceMenu, configuration).subCommands().stream()).toList();

        // M18: pickup and anti-clone (/snpc pickup, /snpc reissue).
        subCommands = Stream.concat(subCommands.stream(), Stream.of(
                new PickupCommand(targeting, () -> pickup),
                new ReissueCommand(targeting, () -> pickup, getLogger()))).toList();
        // M14: choices and first setup.
        Function<NpcRecord, TemplateChoices> templateOf = npc -> npc.template()
                .map(templateChoices::get).orElseGet(() -> TemplateChoices.none(""));
        statPoints = new StatPoints(choiceStore, registry, statSheets, hookStatBases(),
                npc -> choiceSettings.defaults().with(templateOf.apply(npc).eligible(), templateOf.apply(npc).max()),
                (npc, player, stat, amount) -> getServer().getPluginManager().callEvent(
                        new NpcChoiceResolvedEvent(npc, player, NpcChoiceResolvedEvent.STAT_POINTS, stat, amount)),
                System::currentTimeMillis);
        statPoints.load();
        SetupFlow setup = new SetupFlow(choiceStore, registry, (npc, player) -> {
            getServer().getPluginManager().callEvent(new NpcSetupCompleteEvent(npc, player));
            if (player != null) {
                configuration.messages().send(player, "choices.setup-complete", Map.of("npc", citizens.name(npc.id())));
            }
        });
        setup.load();
        Guis guis = new Guis(this, configuration);
        choiceMenu = new ChoiceMenu(guis, setup, registry, citizens);
        PointsPage pointsPage = new PointsPage(guis, statPoints, statSheets, citizens);
        setup.addStep(new AncestryStep(guis, effects, statSheets, citizens, templateOf));
        setup.addStep(pointsPage);
        choiceMenu.addPage(pointsPage);
        new AssignPointsButton(guis, choiceMenu).register(this, menu, access);
        getServer().getPluginManager().registerEvents(new ChoiceGuiListener(getLogger()), this);
        getServer().getPluginManager().registerEvents(new ChoiceLifecycleListener(statPoints, setup), this);
        subCommands = Stream.concat(subCommands.stream(), Stream.of(
                new ChooseCommand(targeting, access, choiceMenu),
                new PointsCommand(targeting, statPoints),
                new RespecCommand(targeting, choiceMenu))).toList();
        // M15: proficiency picks, a setup step after the points and a page of the choice menu.
        picks = new ProficiencyPicks(registry, proficiencies, pickStore,
                (npc, player, grant, proficiency) -> getServer().getPluginManager().callEvent(new NpcChoiceResolvedEvent(
                        npc, player, NpcChoiceResolvedEvent.PROFICIENCY_PICKS, proficiency, 1)),
                getLogger());
        picks.load();
        PicksPage picksPage = new PicksPage(guis, picks, citizens);
        setup.addStep(picksPage);
        choiceMenu.addPage(picksPage);
        getServer().getPluginManager().registerEvents(new PicksListener(picks), this);
        subCommands = Stream.concat(subCommands.stream(), Stream.of(
                new PicksCommand(targeting, access, picks))).toList();

        // M16: abilities (/snpc abilities)
        subCommands = Stream.concat(subCommands.stream(),
                Stream.of(new AbilitiesCommand(targeting, access, abilities, abilitiesMenu, abilityText))).toList();
        NpcCommands commands = new NpcCommands(subCommands, configuration, getPluginMeta().getVersion());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.register(event.registrar()));

        // M17: blueprint items place NPCs.
        blueprints = new Blueprints(hookItemTags());
        NpcSpawner spawner = hookSpawner();
        factory = new NpcFactory(templates, registry, spawner, sheets::create);
        // Removing an NPC through the API (Economy's dismiss, downgrade and business deletion).
        NpcRemover remover = new NpcRemover(registry, spawner::destroy, cloning::removed);
        PlaceRegions placeRegions = worldGuard ? new WorldGuardPlaceRegions() : PlaceRegions.ANYWHERE;
        Supplier<PlacingSettings> placingSettings = () -> PlacingSettings.read(configuration.config());
        NpcPlacer placer = new NpcPlacer(blueprints, factory, registry, characters, spawner,
                placeRegions, placingSettings, getServer().getPluginManager(), getLogger());
        // The API's free-spot search, with the same checks as placing (except the character limit).
        SpotFinder spots = new SpotFinder(registry, spawner, placeRegions, placingSettings);
        getServer().getPluginManager().registerEvents(new NpcPlaceListener(placer, configuration), this);
        // M14: a new NPC gets its starting points.
        factory.addCreatedHook(new StartingPoints(statPoints, templateChoices, blueprints));
        // M15: and its template's proficiency picks.
        factory.addCreatedHook(new StartingPicks(picks, templatePicks));
        // M16: abilities, now that the stat points and picks they give exist; and a new NPC's starting abilities.
        abilities.syncAll();
        factory.addCreatedHook(new StartingAbilities(abilities, templateAbilities, blueprints, getLogger()));

        // M18: picking NPCs up into items, and placing them again only from the current item.
        NpcTickets tickets = new NpcTickets(itemStore, getLogger());
        tickets.load();
        pickup = new NpcPickup(registry, tickets, blueprints, spawner, access, citizens, targeting::ownerName,
                configuration, getServer().getPluginManager());
        placer.usePickup(pickup);
        getServer().getPluginManager().registerEvents(new NpcItemListener(pickup), this);
        new PickupButton(pickup, access, configuration).register(this, menu);

        // Last, so other plugins only see the API once everything behind it is ready.
        getServer().getServicesManager().register(SkysongNpcsApi.class, new SkysongNpcsApiImpl(registry, citizens, remover, spots, access, menu,
                        sheets, templates, factory, blueprints, policies,
                        containers, movement, groups, effects, appearance,
                        pickup, statPoints, choiceMenu, picks, abilities, abilitiesMenu),
                this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (itemStore != null) {
            itemStore.close();
            itemStore = null;
        }
        if (policyStore != null) {
            policyStore.close();
            policyStore = null;
        }
        if (groupStore != null) {
            groupStore.close();
            groupStore = null;
        }
        if (pickStore != null) {
            pickStore.close();
            pickStore = null;
        }
        if (choiceStore != null) {
            choiceStore.close();
            choiceStore = null;
        }
        // M16: abilities
        if (abilityStore != null) {
            abilityStore.close();
            abilityStore = null;
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
        getServer().getPluginManager().registerEvents(new StatsReloadListener(sheets, sheetGuarantee::repairAll), this);
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

    // M15: proficiency picks

    /** How the plugin reads skills, proficiencies and NPCs' tiers, through SkysongStats. */
    protected ProficiencyCatalog hookProficiencies() {
        return new SkysongStatsProficiencyCatalog();
    }

    /** The NPCs' proficiency picks; null while the plugin is disabled. */
    public ProficiencyPicks picks() {
        return picks;
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

    // M7: movement

    /** How NPCs are moved, through Citizens. */
    protected NpcMover hookMover() {
        return new CitizensMover();
    }

    /** Moves NPCs; null while the plugin is disabled. */
    public MovementService movement() {
        return movement;
    }

    // M4: player groups

    /** The lp: group provider, when LuckPerms is installed. */
    protected Optional<GroupProvider> hookLuckPerms() {
        return LuckPermsGroups.hook(getServer());
    }

    /** The player groups; null while the plugin is disabled. */
    public PlayerGroupRegistry groups() {
        return groups;
    }

    // M12

    /** How the plugin reads and changes NPC injuries, conditions and ancestry, through SkysongStats. */
    protected SheetEffects hookEffects() {
        return new SkysongStatsEffects();
    }

    /** NPC injuries, conditions and ancestry; null while the plugin is disabled. */
    public SheetEffects effects() {
        return effects;
    }

    // M8: appearance

    /** How the plugin changes NPCs' looks, through Citizens. */
    protected AppearanceDriver hookAppearance() {
        return new CitizensAppearance(this);
    }

    /** NPCs' looks; null while the plugin is disabled. */
    public AppearanceService appearance() {
        return appearance;
    }

    // M18: pickup and anti-clone

    /** Picking NPCs up into items; null while the plugin is disabled. */
    public NpcPickup pickup() {
        return pickup;
    }

    // M14: choices and first setup

    /** How the plugin reads stat base values for caps, through SkysongStats. */
    protected StatBases hookStatBases() {
        return new SkysongStatsBases();
    }

    /** NPCs' stat points; null while the plugin is disabled. */
    public StatPoints statPoints() {
        return statPoints;
    }

    /** The choice keys of the template files; null while the plugin is disabled. */
    public TemplateChoiceRegistry templateChoices() {
        return templateChoices;
    }

    /** The choice menu and setup; null while the plugin is disabled. */
    public ChoiceMenu choiceMenu() {
        return choiceMenu;
    }

    // M16: abilities

    /** Where abilities put the stat points and picks they give: M14's and M15's choices. */
    protected AbilityChoices hookAbilityChoices() {
        return new PluginAbilityChoices(() -> statPoints, () -> picks);
    }

    /** NPCs' abilities; null while the plugin is disabled. */
    public AbilityService abilities() {
        return abilities;
    }

    /** The abilities menu; null while the plugin is disabled. */
    public AbilitiesMenu abilitiesMenu() {
        return abilitiesMenu;
    }
}
