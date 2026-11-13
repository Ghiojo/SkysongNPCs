package org.skysongdev.skysongnpcs;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.appearance.AppearanceDriver;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.inventory.NpcContainers;
import org.skysongdev.skysongnpcs.cloning.NpcCloning;
import org.skysongdev.skysongnpcs.cloning.SheetCopier;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.items.ItemTagGateway;
import org.skysongdev.skysongnpcs.items.NpcSpawner;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.sheets.CarriedItems;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.StatSheets;
import org.skysongdev.skysongnpcs.storage.DatabaseSettings;
import org.skysongdev.skysongnpcs.storage.InMemoryChoiceStore;
import org.skysongdev.skysongnpcs.storage.InMemoryPickStore;
import org.skysongdev.skysongnpcs.storage.InMemoryGrantStore;
import org.skysongdev.skysongnpcs.storage.InMemoryGroupStore;
import org.skysongdev.skysongnpcs.storage.InMemoryNpcItemStore;
import org.skysongdev.skysongnpcs.storage.InMemoryAbilityStore;
import org.skysongdev.skysongnpcs.abilities.AbilityChoices;
import org.skysongdev.skysongnpcs.storage.InMemoryNpcStore;
import org.skysongdev.skysongnpcs.storage.InMemoryPolicyStore;
import org.skysongdev.skysongnpcs.storage.Stores;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// M10: wardrobe
import org.skysongdev.skysongnpcs.wardrobe.WardrobeGateway;

/**
 * The plugin with its records in memory, and Citizens and SkysongStats replaced by fakes the
 * test controls, since neither plugin is on the test classpath. The same approach as
 * SkysongStats' OfflineSkysongStats.
 */
public class OfflineSkysongNPCs extends SkysongNPCs {
    private final FakeNpcLookup fakeCitizens = new FakeNpcLookup();
    private final FakeCharacterLookup fakeCharacters = new FakeCharacterLookup();
    // M11: stat sheets
    private final FakeStatSheets fakeSheets = new FakeStatSheets();
    private final FakeCarriedItems fakeItems = new FakeCarriedItems();
    // M12
    private final FakeSheetEffects fakeEffects = new FakeSheetEffects();
    // M17
    private final FakeItemTags fakeItemTags = new FakeItemTags();
    private final FakeNpcSpawner fakeSpawner = new FakeNpcSpawner();
    // M9: inventory and equipment
    private final FakeNpcContainers fakeContainers = new FakeNpcContainers();
    // M10: wardrobe
    private final FakeWardrobe fakeWardrobe = new FakeWardrobe();
    // M8: appearance
    private final FakeAppearanceDriver fakeAppearance = new FakeAppearanceDriver();

    /** Loads it with the commands and permissions of the real plugin.yml. */
    public static OfflineSkysongNPCs load() {
        return MockBukkit.loadWith(OfflineSkysongNPCs.class, description());
    }

    // Only the main line changes. Going through YamlConfiguration would split the
    // dotted permission names into nested sections and lose them.
    private static PluginDescriptionFile description() {
        try (InputStream in = Objects.requireNonNull(
                SkysongNPCs.class.getClassLoader().getResourceAsStream("plugin.yml"), "plugin.yml")) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceFirst("(?m)^main: .*$", "main: " + OfflineSkysongNPCs.class.getName());
            return new PluginDescriptionFile(new StringReader(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InvalidDescriptionException e) {
            throw new IllegalStateException("plugin.yml is broken", e);
        }
    }

    @Override
    protected Stores openStores(DatabaseSettings settings) {
        return new Stores(new InMemoryNpcStore(), new InMemoryGrantStore(), new InMemoryPolicyStore(),
                new InMemoryGroupStore(),
                new InMemoryNpcItemStore(), new InMemoryChoiceStore(), new InMemoryPickStore(),
                new InMemoryAbilityStore(), new org.skysongdev.skysongnpcs.storage.InMemoryAbilityChoiceStore());
    }

    @Override
    protected NpcLookup hookCitizens(NpcRegistry registry) {
        return fakeCitizens;
    }

    @Override
    protected CharacterLookup hookCharacters() {
        return fakeCharacters;
    }

    @Override
    protected void hookMenuClicks(NpcMenuService menu) {
        // Citizens' right-click event is not on the test classpath; tests open menus directly.
    }

    public FakeNpcLookup fakeCitizens() {
        return fakeCitizens;
    }

    public FakeCharacterLookup fakeCharacters() {
        return fakeCharacters;
    }

    // M11: stat sheets

    @Override
    protected StatSheets hookSheets() {
        return fakeSheets;
    }

    @Override
    protected CarriedItems hookCarriedItems() {
        return fakeItems;
    }

    @Override
    protected void listenForStatsReloads(NpcSheetService sheets) {
        // SkysongStats' reload event is not on the test classpath.
    }

    public FakeStatSheets fakeSheets() {
        return fakeSheets;
    }

    public FakeCarriedItems fakeItems() {
        return fakeItems;
    }

    // M17

    @Override
    protected ItemTagGateway hookItemTags() {
        return fakeItemTags;
    }

    @Override
    protected NpcSpawner hookSpawner() {
        return fakeSpawner;
    }

    public FakeNpcSpawner fakeSpawner() {
        return fakeSpawner;
    }

    // M9: inventory and equipment

    @Override
    protected NpcContainers hookContainers() {
        return fakeContainers;
    }

    public FakeNpcContainers fakeContainers() {
        return fakeContainers;
    }

    // M10: wardrobe

    @Override
    protected WardrobeGateway hookWardrobe() {
        return fakeWardrobe;
    }

    public FakeWardrobe fakeWardrobe() {
        return fakeWardrobe;
    }

    // M13: cloning
    private final FakeSheetCopier fakeCopier = new FakeSheetCopier(fakeSheets);
    private NpcCloning cloning;

    @Override
    protected SheetCopier hookSheetCopier() {
        return fakeCopier;
    }

    @Override
    protected void listenForNpcRemovals(NpcCloning cloning) {
        // Citizens' remove event is not on the test classpath; tests call cloning().removed, and
        // the fake spawner runs what the plugin's two listeners do when it destroys an NPC.
        this.cloning = cloning;
        fakeSpawner.actLikeCitizens(fakeCitizens::remove, npc -> {
            registry().removed(npc);
            cloning.removed(npc);
        });
    }

    public FakeSheetCopier fakeCopier() {
        return fakeCopier;
    }

    public NpcCloning cloning() {
        return cloning;
    }

    // M7: movement
    private final FakeMover fakeMover = new FakeMover();

    @Override
    protected org.skysongdev.skysongnpcs.movement.NpcMover hookMover() {
        return fakeMover;
    }

    public FakeMover fakeMover() {
        return fakeMover;
    }

    // M12: injuries, conditions and ancestry

    @Override
    protected org.skysongdev.skysongnpcs.sheets.SheetEffects hookEffects() {
        return fakeEffects;
    }

    public FakeSheetEffects fakeEffects() {
        return fakeEffects;
    }

    // M8: appearance

    @Override
    protected AppearanceDriver hookAppearance() {
        return fakeAppearance;
    }

    public FakeAppearanceDriver fakeAppearance() {
        return fakeAppearance;
    }

    // M14: stat bases come from the fake sheets.

    public FakeItemTags fakeItemTags() {
        return fakeItemTags;
    }

    @Override
    protected org.skysongdev.skysongnpcs.choices.StatBases hookStatBases() {
        return fakeSheets::base;
    }

    // M15: proficiencies come from a fake catalog; an NPC has tiers once it has a fake sheet.
    private final FakeProficiencyCatalog fakeProficiencies = new FakeProficiencyCatalog(fakeSheets::exists);

    @Override
    protected org.skysongdev.skysongnpcs.picks.ProficiencyCatalog hookProficiencies() {
        return fakeProficiencies;
    }

    public FakeProficiencyCatalog fakeProficiencies() {
        return fakeProficiencies;
    }

    // M16: abilities
    private final FakeAbilityChoices fakeChoices = new FakeAbilityChoices();

    @Override
    protected AbilityChoices hookAbilityChoices() {
        return fakeChoices;
    }

    public FakeAbilityChoices fakeChoices() {
        return fakeChoices;
    }
}
