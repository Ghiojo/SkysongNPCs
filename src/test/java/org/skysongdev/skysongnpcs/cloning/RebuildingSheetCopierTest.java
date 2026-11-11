package org.skysongdev.skysongnpcs.cloning;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.skysongdev.skysongnpcs.cloning.SheetCopier.Outcome;
import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.Ancestries;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.CharacterId;
import org.skysongdev.skysongstats.api.CharacterSheet;
import org.skysongdev.skysongstats.api.CharacterSheets;
import org.skysongdev.skysongstats.api.Characters;
import org.skysongdev.skysongstats.api.Conditions;
import org.skysongdev.skysongstats.api.Focus;
import org.skysongdev.skysongstats.api.Injuries;
import org.skysongdev.skysongstats.api.Injury;
import org.skysongdev.skysongstats.api.Modifier;
import org.skysongdev.skysongstats.api.Modifiers;
import org.skysongdev.skysongstats.api.Proficiencies;
import org.skysongdev.skysongstats.api.Proficiency;
import org.skysongdev.skysongstats.api.SheetField;
import org.skysongdev.skysongstats.api.Skill;
import org.skysongdev.skysongstats.api.Skills;
import org.skysongdev.skysongstats.api.SkysongStatsApi;
import org.skysongdev.skysongstats.api.Stat;
import org.skysongdev.skysongstats.api.StatAction;
import org.skysongdev.skysongstats.api.StatType;
import org.skysongdev.skysongstats.api.StatValues;
import org.skysongdev.skysongstats.api.Stats;
import org.skysongdev.skysongstats.api.Tier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RebuildingSheetCopierTest {
    private static final Stat STR = new Stat("str", "Strength", "<red>", List.of(), StatType.VALUE, null, null);
    private static final Stat HP = new Stat("hp", "HP", "<red>", List.of(), StatType.BAR, 0, null);

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();
    private final CharacterId original = new CharacterId(from, "npc");
    private final CharacterId copy = new CharacterId(to, "npc");

    private final SkysongStatsApi api = mock(SkysongStatsApi.class);
    private final Characters characters = mock(Characters.class);
    private final Stats stats = mock(Stats.class);
    private final Modifiers modifiers = mock(Modifiers.class);
    private final Ancestries ancestries = mock(Ancestries.class);
    private final Skills skills = mock(Skills.class);
    private final Proficiencies proficiencies = mock(Proficiencies.class);
    private final CharacterSheets sheets = mock(CharacterSheets.class);
    private final Injuries injuries = mock(Injuries.class);
    private final Conditions conditions = mock(Conditions.class);
    private final Plugin plugin = mock(Plugin.class);
    private RebuildingSheetCopier copier;

    @BeforeEach
    void setUp() {
        when(api.characters()).thenReturn(characters);
        when(api.stats()).thenReturn(stats);
        when(api.modifiers()).thenReturn(modifiers);
        when(api.ancestries()).thenReturn(ancestries);
        when(api.skills()).thenReturn(skills);
        when(api.proficiencies()).thenReturn(proficiencies);
        when(api.sheets()).thenReturn(sheets);
        when(api.injuries()).thenReturn(injuries);
        when(api.conditions()).thenReturn(conditions);
        when(plugin.getName()).thenReturn("SkysongNPCs");

        when(characters.owner(original)).thenReturn(Optional.of("SkysongNPCs"));
        when(characters.create(eq(plugin), eq(copy), any())).thenReturn(true);
        when(stats.all()).thenReturn(List.of(STR, HP));
        when(stats.get(original, STR)).thenReturn(Optional.of(new StatValues(4, 0, 0, null, null)));
        when(stats.get(original, HP)).thenReturn(Optional.of(new StatValues(7, 0, 0, 12, 10)));
        copier = new RebuildingSheetCopier(plugin, () -> api);
    }

    @Test
    void rebuildsTheCopyWithBarsLast() {
        when(modifiers.list(original)).thenReturn(List.of(new Modifier("m1", "max_hp", 2), new Modifier("m2", "str", 1)));

        SheetCopier.CopyResult result = copier.copy(from, to);

        assertEquals(Outcome.COPIED, result.outcome());
        assertEquals(List.of(), result.gaps());
        InOrder order = inOrder(characters, stats, modifiers);
        order.verify(characters).create(plugin, copy, Map.of(STR, 4));
        order.verify(stats).apply(copy, STR, StatAction.SET, 4);
        order.verify(modifiers).add(copy, "max_hp", 2);
        order.verify(modifiers).add(copy, "str", 1);
        order.verify(stats).apply(copy, HP, StatAction.SET, 7);
    }

    @Test
    void basesAlreadyRightAreNotSetAgain() {
        when(stats.get(copy, STR)).thenReturn(Optional.of(new StatValues(4, 0, 0, null, null)));

        copier.copy(from, to);

        verify(stats, never()).apply(copy, STR, StatAction.SET, 4);
    }

    @Test
    void anOriginalWithoutASheetIsNotCopied() {
        when(characters.owner(original)).thenReturn(Optional.empty());

        assertEquals(Outcome.NO_SHEET, copier.copy(from, to).outcome());
        verify(characters, never()).create(any(), any(), any());
    }

    @Test
    void aCopyThatIsACharacterAlreadyIsLeftAlone() {
        when(characters.create(eq(plugin), eq(copy), any())).thenReturn(false);

        assertEquals(Outcome.TAKEN, copier.copy(from, to).outcome());
        verify(stats, never()).apply(any(), any(Stat.class), any(), anyInt());
    }

    @Test
    void givesTheAncestryWithTheSamePicksThenRestoresTheBases() {
        Ancestry elf = elf();
        when(ancestries.of(original)).thenReturn(Optional.of(new CharacterAncestry("elf", "Elf", false,
                List.of(new CharacterAncestry.Given("str", 1, 0)))));
        when(ancestries.ancestry("elf")).thenReturn(Optional.of(elf));

        SheetCopier.CopyResult result = copier.copy(from, to);

        InOrder order = inOrder(ancestries, stats);
        order.verify(ancestries).set(copy, elf, AncestryPicks.builder().stats(0, "str").build());
        order.verify(stats).apply(copy, STR, StatAction.SET, 4);
        assertEquals(List.of(), result.gaps());
    }

    @Test
    void picksThatCannotBeReadAreLeftPending() {
        Ancestry elf = elf();
        when(ancestries.of(original)).thenReturn(Optional.of(new CharacterAncestry("elf", "Elf", false,
                List.of(new CharacterAncestry.Given("str", 3, 0)))));
        when(ancestries.ancestry("elf")).thenReturn(Optional.of(elf));

        SheetCopier.CopyResult result = copier.copy(from, to);

        verify(ancestries).set(copy, elf);
        assertEquals(1, result.gaps().size());
    }

    @Test
    void pendingChoicesStayPending() {
        Ancestry elf = elf();
        when(ancestries.of(original)).thenReturn(Optional.of(new CharacterAncestry("elf", "Elf", true, List.of())));
        when(ancestries.ancestry("elf")).thenReturn(Optional.of(elf));

        SheetCopier.CopyResult result = copier.copy(from, to);

        verify(ancestries).set(copy, elf);
        assertEquals(List.of(), result.gaps());
    }

    @Test
    void anAncestryNoLongerInTheFileIsAGap() {
        when(ancestries.of(original)).thenReturn(Optional.of(new CharacterAncestry("gone", "gone", false, List.of())));

        SheetCopier.CopyResult result = copier.copy(from, to);

        verify(ancestries, never()).set(any(), any());
        assertEquals(1, result.gaps().size());
        assertTrue(result.gaps().getFirst().contains("gone"));
    }

    @Test
    void copiesSkillsProficienciesAndFocus() {
        Skill combat = new Skill("combat", "Combat", "Cbt", "<red>", List.of());
        Proficiency swords = new Proficiency("swords", "Swords", "<red>", "combat", List.of());
        Tier novice = new Tier("novice", "Novice", "<gray>");
        Tier adept = new Tier("adept", "Adept", "<gold>");
        when(skills.all()).thenReturn(List.of(combat));
        when(skills.xp(original, combat)).thenReturn(OptionalInt.of(30));
        when(proficiencies.all()).thenReturn(List.of(swords));
        when(proficiencies.tier(original, swords)).thenReturn(Optional.of(adept));
        when(proficiencies.tier(copy, swords)).thenReturn(Optional.of(novice));
        when(proficiencies.focus(original)).thenReturn(Optional.of(new Focus(swords, 3)));

        copier.copy(from, to);

        verify(skills).setXp(copy, combat, 30);
        verify(proficiencies).setTier(copy, swords, adept);
        verify(proficiencies).setFocus(copy, swords, true);
        verify(proficiencies).setPoints(copy, 3);
    }

    @Test
    void copiesTheSheetFieldsThatWereSet() {
        when(sheets.get(original)).thenReturn(Optional.of(new CharacterSheet(original, "Bram", "40",
                RebuildingSheetCopier.NOT_SET, "Dwarf-ish", "he/him", "A guard.")));

        copier.copy(from, to);

        verify(sheets).set(copy, SheetField.NAME, "Bram");
        verify(sheets).set(copy, SheetField.AGE, "40");
        verify(sheets).set(copy, SheetField.ANCESTRY, "Dwarf-ish");
        verify(sheets).set(copy, SheetField.PRONOUNS, "he/him");
        verify(sheets).set(copy, SheetField.DESCRIPTION, "A guard.");
        verify(sheets, never()).set(copy, SheetField.GENDER, RebuildingSheetCopier.NOT_SET);
    }

    @Test
    void theAncestryFieldIsLeftToSkysongStatsWhenTheCopyHasAnAncestry() {
        when(ancestries.of(original)).thenReturn(Optional.of(new CharacterAncestry("elf", "Elf", true, List.of())));
        when(ancestries.ancestry("elf")).thenReturn(Optional.of(elf()));
        when(sheets.get(original)).thenReturn(Optional.of(new CharacterSheet(original, "Bram", "40",
                "male", "Elf", "he/him", "A guard.")));

        copier.copy(from, to);

        verify(sheets, never()).set(eq(copy), eq(SheetField.ANCESTRY), any());
    }

    @Test
    void copiesInjuriesWhatItKnowsAndDirectConditions() {
        when(injuries.current(original)).thenReturn(List.of(new Injury("broken_arm", Instant.EPOCH)));
        when(injuries.known(original)).thenReturn(Set.of("concussion"));
        when(injuries.known(copy)).thenReturn(Set.of("broken_arm", "concussion"));
        when(conditions.current(original)).thenReturn(List.of(
                new ActiveCondition("tired", 2, true, List.of(), List.of()),
                new ActiveCondition("pain", 1, false, List.of("broken_arm"), List.of())));

        SheetCopier.CopyResult result = copier.copy(from, to);

        verify(injuries).award(copy, "broken_arm");
        verify(injuries).discover(copy, "concussion");
        verify(injuries).forget(copy, "broken_arm");
        verify(conditions).award(copy, "tired", 2);
        verify(conditions, never()).award(copy, "pain", 1);
        assertEquals(1, result.gaps().size());
    }

    @Test
    void deletesThroughSkysongStats() {
        when(characters.delete(plugin, copy)).thenReturn(true);

        assertTrue(copier.delete(to));
        assertFalse(copier.delete(from));
    }

    private static Ancestry elf() {
        return new Ancestry("elf", "Elf", "FEATHER", List.of(), "", List.of(),
                List.of(new AncestryChoice.Stats("", 1, 1, List.of(), true, false)));
    }
}
