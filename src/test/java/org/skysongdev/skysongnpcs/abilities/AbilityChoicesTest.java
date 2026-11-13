package org.skysongdev.skysongnpcs.abilities;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.ability.AbilityChoiceResult;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilities;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilityChoice;
import org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent;
import org.skysongdev.skysongnpcs.api.event.NpcSetupCompleteEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.choices.ChoicePage;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.commands.RespecCommand;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.picks.PicksPage;
import org.skysongdev.skysongnpcs.storage.InMemoryAbilityChoiceStore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ability choices: the API, the choice menu page, the setup step, respec, abilities.yml changes and copies. */
class AbilityChoicesTest {
    private static final String MODULE = "economy:module:trade";
    private static final String CHOSEN = AbilityChoiceService.abilitySource(MODULE);
    static final String ABILITIES = """
            tough:
              name: Tough
              description: "Hard to hurt."
              icon: IRON_CHESTPLATE
              modifiers:
                strength: 2
            swift:
              name: Swift
              icon: FEATHER
            wise:
              name: "<blue>Wise"
              icon: WRITABLE_BOOK
            """;

    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private NpcAbilities api;
    private AbilityChoiceService choices;
    private PlayerMock staff;
    private PlayerMock alice;
    private UUID guard;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        api = SkysongNpcsApi.get().abilities();
        choices = plugin.abilityChoices();
        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
        writeAbilities(ABILITIES);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theApiGrantsAndMakesAChoice() {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough", "swift", "wise"), 2);
        assertEquals(2, choice.remaining());
        assertEquals(1, api.choices(guard).size());

        assertEquals(AbilityChoiceResult.DONE, api.choose(choice.id(), List.of("tough", "wise")));

        assertTrue(api.has(guard, "tough"));
        assertTrue(api.has(guard, "wise"));
        assertEquals(Set.of(CHOSEN), api.sources(guard, "tough"));
        NpcAbilityChoice after = api.choices(guard).getFirst();
        assertEquals(List.of("tough", "wise"), after.chosen());
        assertEquals(0, after.remaining());
        assertEquals(1, plugin.fakeSheets().modifiers(guard).size());
        server.getPluginManager().assertEventFired(NpcChoiceResolvedEvent.class, event ->
                event.getKind().equals(NpcChoiceResolvedEvent.ABILITY_CHOICES) && event.getChoice().equals("wise")
                        && event.getPlayer() == null);
    }

    @Test
    void chooseChecksEveryAbilityBeforeGrantingAny() {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough", "swift"), 1);
        api.grant(guard, "swift");

        assertEquals(AbilityChoiceResult.NOTHING_CHOSEN, api.choose(choice.id(), List.of()));
        assertEquals(AbilityChoiceResult.TOO_MANY, api.choose(choice.id(), List.of("tough", "swift")));
        assertEquals(AbilityChoiceResult.NOT_AN_OPTION, api.choose(choice.id(), List.of("swift")));
        assertEquals(AbilityChoiceResult.NOT_AN_OPTION, api.choose(choice.id(), List.of("wise")));
        assertEquals(AbilityChoiceResult.NO_SUCH_CHOICE, api.choose(UUID.randomUUID(), List.of("tough")));
        assertFalse(api.has(guard, "tough"));
        assertEquals(List.of("tough"), choices.options(choices.grant(choice.id()).orElseThrow()));
    }

    @Test
    void grantingChecksItsArguments() {
        assertThrows(IllegalArgumentException.class, () -> api.grantChoice(guard, MODULE, List.of("flying"), 1));
        assertThrows(IllegalArgumentException.class, () -> api.grantChoice(guard, MODULE, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> api.grantChoice(guard, MODULE, List.of("tough"), 0));
        assertThrows(IllegalArgumentException.class, () -> api.grantChoice(guard, "two words", List.of("tough"), 1));
        assertThrows(IllegalArgumentException.class, () -> api.grantChoice(UUID.randomUUID(), MODULE, List.of("tough"), 1));
    }

    @Test
    void aSecondGrantWithTheSameOptionsGrowsTheFirst() {
        api.grantChoice(guard, MODULE, List.of("tough", "swift"), 1);
        NpcAbilityChoice grown = api.grantChoice(guard, MODULE, List.of("tough", "swift"), 1);
        api.grantChoice(guard, MODULE, List.of("wise"), 1);

        assertEquals(2, grown.count());
        assertEquals(2, api.choices(guard).size());
        assertEquals(3, choices.pending(guard));
    }

    @Test
    void respecRevokesTheChosenAbilitiesAndOpensTheChoiceAgain() throws CommandSyntaxException {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough", "swift"), 1);
        api.choose(choice.id(), List.of("tough"));
        CommandRunner commands = new CommandRunner(plugin.configuration(), "1.0.0", new RespecCommand(
                new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(), plugin.configuration()),
                plugin.choiceMenu()));

        commands.run(staff, "snpc respec " + MODULE);

        assertFalse(api.has(guard, "tough"));
        assertEquals(0, plugin.fakeSheets().modifiers(guard).size());
        assertEquals(1, api.choices(guard).getFirst().remaining());
        assertEquals(0, SkysongNpcsApi.get().choices().respec(guard, "someone-else"));
    }

    @Test
    void aStaffGrantOfTheSameAbilityKeepsItThroughARespec() {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough"), 1);
        api.choose(choice.id(), List.of("tough"));
        api.grant(guard, "tough");

        assertEquals(1, SkysongNpcsApi.get().choices().respec(guard, MODULE));

        assertTrue(api.has(guard, "tough"));
        assertEquals(Set.of(NpcAbilities.STAFF), api.sources(guard, "tough"));
    }

    @Test
    void losingAChosenAbilityGivesTheChoiceBack() throws IOException {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough", "swift", "wise"), 2);
        api.choose(choice.id(), List.of("tough", "swift"));

        api.revoke(guard, "tough");
        assertEquals(List.of("swift"), api.choices(guard).getFirst().chosen());

        writeAbilities(ABILITIES.substring(0, ABILITIES.indexOf("swift:")));
        plugin.abilities().syncAll();

        NpcAbilityChoice after = api.choices(guard).getFirst();
        assertEquals(List.of(), after.chosen());
        assertEquals(2, after.remaining());
        assertEquals(List.of("tough"), choices.options(choices.grant(choice.id()).orElseThrow()));
        assertEquals(1, choices.pending(guard));
    }

    @Test
    void takingUnspentChoicesLeavesTheMadeOnes() {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough", "swift", "wise"), 3);
        api.choose(choice.id(), List.of("swift"));

        assertEquals(2, api.takeUnspentChoices(guard, MODULE));

        NpcAbilityChoice after = api.choices(guard).getFirst();
        assertEquals(1, after.count());
        assertEquals(List.of("swift"), after.chosen());
        assertEquals(0, api.takeUnspentChoices(guard, MODULE));
    }

    @Test
    void thePageGrantsWhatIsSelectedOnConfirm() {
        assertTrue(plugin.choiceMenu().pages().stream().anyMatch(page -> page.id().equals(AbilityChoicePage.ID)));
        api.grantChoice(guard, MODULE, List.of("tough", "swift", "wise"), 1);
        NpcRecord npc = plugin.registry().get(guard).orElseThrow();
        int[] backs = {0};

        page().open(alice, npc, () -> backs[0]++);
        Inventory shown = shown(alice);
        assertEquals(Material.IRON_CHESTPLATE, shown.getItem(0).getType());
        assertEquals(Material.WRITABLE_BOOK, shown.getItem(2).getType());
        click(alice, 2);
        click(alice, 0);
        assertTrue(shown.getItem(2).getItemMeta().getEnchantmentGlintOverride());
        click(alice, shown.getSize() - 1);
        server.getScheduler().performOneTick();

        assertTrue(api.has(guard, "wise"));
        assertFalse(api.has(guard, "tough"));
        assertEquals("[NPCs] Guard now has Wise.", plain(alice));
        assertEquals(0, choices.pending(guard));
        assertEquals(1, backs[0]);
    }

    @Test
    void theSetupAsksForAbilityChoicesAfterThePicks() {
        List<String> steps = plugin.choiceMenu().setup().steps().stream().map(step -> step.id()).toList();
        assertTrue(steps.indexOf(AbilityChoicePage.ID) > steps.indexOf(PicksPage.ID));
        plugin.registry().setState(guard, NpcState.SETUP);
        api.grantChoice(guard, MODULE, List.of("swift"), 1);

        plugin.choiceMenu().open(alice, guard);
        Inventory shown = shown(alice);
        click(alice, 0);
        click(alice, shown.getSize() - 1);
        server.getScheduler().performOneTick();

        assertTrue(api.has(guard, "swift"));
        assertEquals(NpcState.PLACED, plugin.registry().get(guard).orElseThrow().state());
        server.getPluginManager().assertEventFired(NpcSetupCompleteEvent.class);
    }

    @Test
    void releasingTheNpcDropsItsChoices() {
        api.grantChoice(guard, MODULE, List.of("swift"), 1);

        plugin.registry().release(guard);

        assertEquals(List.of(), choices.grants(guard));
    }

    @Test
    void aCopyGetsTheChoicesUnderNewIds() {
        NpcAbilityChoice choice = api.grantChoice(guard, MODULE, List.of("tough", "swift"), 2);
        api.choose(choice.id(), List.of("swift"));
        UUID copy = plugin.fakeCitizens().add("Guard copy");
        plugin.registry().adopt(copy, NpcOwner.server());
        plugin.abilities().copied(guard, copy);

        choices.copy(guard, copy);

        NpcAbilityChoice copied = api.choices(copy).getFirst();
        assertFalse(copied.id().equals(choice.id()));
        assertEquals(List.of("swift"), copied.chosen());
        assertEquals(1, copied.remaining());
    }

    @Test
    void choicesSurviveARestart() {
        InMemoryAbilityChoiceStore store = new InMemoryAbilityChoiceStore();
        AbilityChoiceService before = new AbilityChoiceService(plugin.registry(), plugin.abilities(), store,
                (npc, player, grant, ability) -> { }, Logger.getLogger("test"));
        AbilityChoiceGrant grant = before.grant(guard, MODULE, List.of("tough", "swift"), 2);
        before.choose(null, grant.id(), List.of("swift"));

        AbilityChoiceService after = new AbilityChoiceService(plugin.registry(), plugin.abilities(), store,
                (npc, player, g, ability) -> { }, Logger.getLogger("test"));
        after.load();

        assertEquals(List.of(grant), after.grants(guard));
        assertEquals(List.of("swift"), after.chosen(grant));
    }

    private void writeAbilities(String yaml) throws IOException {
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), yaml);
        assertTrue(plugin.abilities().definitions().reload());
    }

    private ChoicePage page() {
        return plugin.choiceMenu().pages().stream().filter(page -> page.id().equals(AbilityChoicePage.ID))
                .findFirst().orElseThrow();
    }

    private Inventory shown(PlayerMock player) {
        server.getScheduler().performOneTick();
        Inventory top = player.getOpenInventory().getTopInventory();
        assertInstanceOf(ChoiceGui.class, top.getHolder());
        return top;
    }

    private static void click(PlayerMock player, int slot) {
        player.simulateInventoryClick(player.getOpenInventory(), slot);
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
