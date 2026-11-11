package org.skysongdev.skysongnpcs.abilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.FakeAbilityChoices;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.event.NpcAbilityChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.sheets.StatSheets.ModifierLine;
import org.skysongdev.skysongnpcs.storage.InMemoryAbilityStore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityServiceTest {
    static final String TOUGH = """
            tough:
              name: Tough
              description: "Hard to hurt."
              modifiers:
                strength: 2
                max_hp: 5
              points:
                amount: 3
                eligible: [strength]
              picks:
                - skill: combat
                  tier: expert
                  count: 2
              proficiencies:
                swords: expert
            """;

    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private AbilityService abilities;
    private FakeStatSheets sheets;
    private FakeAbilityChoices choices;
    private UUID guard;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        abilities = plugin.abilities();
        sheets = plugin.fakeSheets();
        choices = plugin.fakeChoices();
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.server());
        writeAbilities(TOUGH);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void writeAbilities(String yaml) throws IOException {
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), yaml);
        assertTrue(abilities.definitions().reload());
        abilities.syncAll();
    }

    private List<String> modifiers(UUID npc) {
        return sheets.modifiers(npc).stream().map(line -> line.target() + " " + line.amount()).toList();
    }

    @Test
    void grantingAppliesEveryEffect() {
        assertTrue(abilities.grant(guard, "tough"));

        assertEquals(List.of("strength 2", "max_hp 5"), modifiers(guard));
        assertEquals("expert", sheets.tier(guard, "swords"));
        assertEquals(3, choices.unspentPoints(guard, "ability:tough"));
        assertEquals(List.of("strength"), choices.eligible(guard, "ability:tough"));
        assertEquals(2, choices.unspentPicks(guard, "ability:tough:combat/expert"));
        assertEquals("combat/expert", choices.pickKind(guard, "ability:tough:combat/expert"));
        assertTrue(abilities.has(guard, "tough"));
        server.getPluginManager().assertEventFired(NpcAbilityChangeEvent.class,
                event -> event.getChange() == NpcAbilityChangeEvent.Change.GRANTED && event.getAbility().equals("tough"));
    }

    @Test
    void grantingTwiceChangesNothing() {
        abilities.grant(guard, "tough");

        assertFalse(abilities.grant(guard, "tough"));
        assertEquals(2, sheets.modifiers(guard).size());
        assertEquals(3, choices.unspentPoints(guard, "ability:tough"));
    }

    @Test
    void refusesUnknownAbilitiesAndUnmanagedNpcs() {
        assertThrows(IllegalArgumentException.class, () -> abilities.grant(guard, "flying"));
        UUID plain = plugin.fakeCitizens().add("Plain");
        assertThrows(IllegalArgumentException.class, () -> abilities.grant(plain, "tough"));
    }

    @Test
    void revokingTakesTheEffectsAwayButSpentPointsStay() {
        abilities.grant(guard, "tough");
        choices.spendPoints(guard, "ability:tough", 1);

        assertTrue(abilities.revoke(guard, "tough"));

        assertEquals(List.of(), modifiers(guard));
        assertEquals("untrained", sheets.tier(guard, "swords"));
        assertEquals(0, choices.unspentPoints(guard, "ability:tough"));
        assertEquals(1, choices.spentPoints(guard, "ability:tough"));
        assertEquals(0, choices.unspentPicks(guard, "ability:tough:combat/expert"));
        assertFalse(abilities.has(guard, "tough"));
        assertFalse(abilities.revoke(guard, "tough"));
        server.getPluginManager().assertEventFired(NpcAbilityChangeEvent.class,
                event -> event.getChange() == NpcAbilityChangeEvent.Change.REVOKED);
    }

    @Test
    void aTierAlreadyHighEnoughIsLeftAlone() {
        sheets.setProficiency(guard, "swords", "expert");
        abilities.grant(guard, "tough");

        abilities.revoke(guard, "tough");

        assertEquals("expert", sheets.tier(guard, "swords"));
    }

    @Test
    void aTierStaffLoweredIsNotPutBack() {
        abilities.grant(guard, "tough");
        sheets.setProficiency(guard, "swords", "untrained");

        abilities.revoke(guard, "tough");

        assertEquals("untrained", sheets.tier(guard, "swords"));
    }

    @Test
    void twoAbilitiesOnOneProficiencyKeepItRaisedUntilBothAreGone() throws IOException {
        writeAbilities(TOUGH + """
                swordsman:
                  proficiencies:
                    swords: expert
                """);
        abilities.grant(guard, "tough");
        abilities.grant(guard, "swordsman");

        abilities.revoke(guard, "tough");
        assertEquals("expert", sheets.tier(guard, "swords"));

        abilities.revoke(guard, "swordsman");
        assertEquals("untrained", sheets.tier(guard, "swords"));
    }

    @Test
    void reloadingBringsNpcsInLineWithTheFile() throws IOException {
        abilities.grant(guard, "tough");
        choices.spendPoints(guard, "ability:tough", 2);

        writeAbilities("""
                tough:
                  modifiers:
                    strength: 3
                  points:
                    amount: 1
                    eligible: [strength]
                  picks:
                    - skill: combat
                      tier: expert
                      count: 3
                """);

        assertEquals(List.of("strength 3"), modifiers(guard));
        assertEquals("untrained", sheets.tier(guard, "swords"));
        // 3 given, 2 spent, 1 unspent; it now gives 1, so the unspent one goes and the spent stay.
        assertEquals(0, choices.unspentPoints(guard, "ability:tough"));
        assertEquals(2, choices.spentPoints(guard, "ability:tough"));
        assertEquals(3, choices.unspentPicks(guard, "ability:tough:combat/expert"));
        assertEquals(2, abilities.applied(guard, "tough").orElseThrow().points());

        // Giving more again only gives the difference.
        writeAbilities(TOUGH);
        assertEquals(1, choices.unspentPoints(guard, "ability:tough"));
        assertEquals(2, choices.unspentPicks(guard, "ability:tough:combat/expert"));
    }

    @Test
    void picksOfAKindTheAbilityNoLongerGivesAreTakenBack() throws IOException {
        abilities.grant(guard, "tough");

        writeAbilities(TOUGH.replace("skill: combat", "skill: metalworking"));

        assertEquals(0, choices.unspentPicks(guard, "ability:tough:combat/expert"));
        assertEquals(2, choices.unspentPicks(guard, "ability:tough:metalworking/expert"));
        assertEquals(Map.of("metalworking/expert", 2), abilities.applied(guard, "tough").orElseThrow().picks());
    }

    @Test
    void anUnchangedFileLeavesModifiersAlone() throws IOException {
        abilities.grant(guard, "tough");
        List<String> ids = sheets.modifiers(guard).stream().map(ModifierLine::id).toList();

        writeAbilities(TOUGH);

        assertEquals(ids, sheets.modifiers(guard).stream().map(ModifierLine::id).toList());
    }

    @Test
    void aModifierStaffRemovedIsAddedAgainOnReload() throws IOException {
        abilities.grant(guard, "tough");
        sheets.removeModifier(guard, sheets.modifiers(guard).get(0).id());

        writeAbilities(TOUGH);

        assertEquals(List.of("strength 2", "max_hp 5"), modifiers(guard));
    }

    @Test
    void changedEligibleStatsGiveTheUnspentPointsAgain() throws IOException {
        abilities.grant(guard, "tough");
        choices.spendPoints(guard, "ability:tough", 1);

        writeAbilities(TOUGH.replace("eligible: [strength]", "eligible: [hp]"));

        assertEquals(List.of("hp"), choices.eligible(guard, "ability:tough"));
        assertEquals(2, choices.unspentPoints(guard, "ability:tough"));
        assertEquals(3, abilities.applied(guard, "tough").orElseThrow().points());
    }

    @Test
    void anAbilityGoneFromTheFileIsRemovedFromEveryNpc() throws IOException {
        abilities.grant(guard, "tough");

        writeAbilities("other:\n  name: Other\n");

        assertFalse(abilities.has(guard, "tough"));
        assertEquals(List.of(), modifiers(guard));
        assertEquals(0, choices.unspentPoints(guard, "ability:tough"));
        server.getPluginManager().assertEventFired(NpcAbilityChangeEvent.class,
                event -> event.getChange() == NpcAbilityChangeEvent.Change.REMOVED_FROM_FILE);
    }

    @Test
    void aBrokenFileChangesNothing() throws IOException {
        abilities.grant(guard, "tough");
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), "tough:\n  modifiers:\n    luck: 1\n");

        assertFalse(abilities.definitions().reload());
        abilities.syncAll();

        assertTrue(abilities.has(guard, "tough"));
        assertEquals(List.of("strength 2", "max_hp 5"), modifiers(guard));
    }

    @Test
    void anNpcWithoutASheetGetsModifiersOnceItHasOne() {
        UUID dog = plugin.fakeCitizens().add("Dog");
        plugin.registry().adopt(dog, NpcOwner.server());
        // Its SkysongStats character was deleted by hand.
        plugin.fakeSheets().delete(dog);
        abilities.grant(dog, "tough");
        assertEquals(3, choices.unspentPoints(dog, "ability:tough"));

        plugin.sheets().create(dog, Map.of());
        abilities.syncAll();

        assertEquals(List.of("strength 2", "max_hp 5"), modifiers(dog));
        assertEquals("expert", sheets.tier(dog, "swords"));
        assertEquals(3, choices.unspentPoints(dog, "ability:tough"));
    }

    @Test
    void aCopyKeepsTheAbilitiesAndFindsItsCopiedModifiers() {
        abilities.grant(guard, "tough");
        sheets.addModifier(guard, "strength", 2);
        UUID copy = plugin.fakeCitizens().add("Copy");
        plugin.registry().adopt(copy, NpcOwner.server());
        // What M13 does: every modifier again, same order, new ids.
        sheets.modifiers(guard).forEach(line -> sheets.addModifier(copy, line.target(), line.amount()));

        abilities.copied(guard, copy);

        List<String> copyIds = sheets.modifiers(copy).stream().map(ModifierLine::id).toList();
        assertEquals(copyIds.subList(0, 2), abilities.applied(copy, "tough").orElseThrow().modifiers());
        abilities.revoke(copy, "tough");
        assertEquals(List.of("strength 2"), modifiers(copy));
        assertEquals(List.of("strength 2", "max_hp 5", "strength 2"), modifiers(guard));
    }

    @Test
    void aDeletedNpcForgetsItsAbilities() {
        abilities.grant(guard, "tough");

        abilities.deleted(guard);

        assertFalse(abilities.has(guard, "tough"));
    }

    @Test
    void savedAbilitiesComeBackOnLoad() {
        InMemoryAbilityStore store = new InMemoryAbilityStore();
        AbilityService first = new AbilityService(plugin.registry(), abilities.definitions(), store, sheets, choices,
                event -> { });
        first.grant(guard, "tough");
        AppliedAbility saved = store.saved(guard, "tough");
        assertEquals(2, saved.modifiers().size());

        AbilityService second = new AbilityService(plugin.registry(), abilities.definitions(), store, sheets, choices,
                event -> { });
        second.load();

        assertEquals(saved, second.applied(guard, "tough").orElseThrow());
        assertEquals(List.of("tough"), second.of(guard).stream().map(AbilityDefinition::id).toList());
    }
}
