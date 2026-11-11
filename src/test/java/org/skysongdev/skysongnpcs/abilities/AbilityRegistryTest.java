package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.FakeProficiencyCatalog;
import org.skysongdev.skysongnpcs.FakeStatSheets;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityRegistryTest {
    private final FakeStatSheets sheets = new FakeStatSheets();

    @BeforeEach
    void setUp() {
        // Material lookups need a server.
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Map<String, AbilityDefinition> parse(String yaml) {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return AbilityRegistry.parse(file, sheets, new FakeProficiencyCatalog()::skillId);
    }

    @Test
    void readsEveryPart() {
        AbilityDefinition tough = parse("""
                tough:
                  name: "<red>Tough"
                  description: ["Line one", "Line two"]
                  icon: shield
                  modifiers:
                    str: 2
                    MAX_HP: 5
                  points:
                    amount: 3
                    eligible: [str]
                  picks:
                    - skill: fight
                      tier: expert
                  proficiencies:
                    swords: Expert
                """).get("tough");

        assertEquals("<red>Tough", tough.name());
        assertEquals("Line one\nLine two", tough.description());
        assertEquals(Material.SHIELD, tough.icon());
        assertEquals(List.of(new AbilityDefinition.Modifier("strength", 2), new AbilityDefinition.Modifier("max_hp", 5)),
                tough.modifiers());
        assertEquals(new AbilityDefinition.Points(3, List.of("strength")), tough.points());
        assertEquals(List.of(new AbilityDefinition.Picks("combat", "expert", 1)), tough.picks());
        assertEquals(Map.of("swords", "expert"), tough.tiers());
    }

    @Test
    void fillsInDefaults() {
        AbilityDefinition plain = parse("iron_skin: {}\n").get("iron_skin");

        assertEquals("Iron_skin", plain.name());
        assertEquals("", plain.description());
        assertEquals(Material.BOOK, plain.icon());
        assertEquals(AbilityDefinition.Points.NONE, plain.points());
        assertTrue(plain.modifiers().isEmpty() && plain.picks().isEmpty() && plain.tiers().isEmpty());
    }

    @Test
    void keepsTheFileOrder() {
        assertEquals(List.of("b", "a", "c"), List.copyOf(parse("b: {}\na: {}\nc: {}\n").keySet()));
    }

    @Test
    void refusesMistakes() {
        List<String> broken = List.of(
                "Bad-Id: {}\n",
                "a:\n  name: Two words\n",
                "a:\n  icon: NOT_A_THING\n",
                "a:\n  modifiers:\n    luck: 1\n",
                "a:\n  modifiers:\n    strength: lots\n",
                "a:\n  points:\n    amount: 0\n",
                "a:\n  points:\n    amount: 1\n    eligible: [luck]\n",
                "a:\n  picks:\n    - skill: combat\n      tier: master\n",
                "a:\n  picks:\n    - tier: expert\n",
                "a:\n  picks:\n    - skill: dancing\n      tier: expert\n",
                "a:\n  picks:\n    - skill: combat\n      tier: expert\n      count: 0\n",
                "a:\n  picks:\n    - {skill: combat, tier: expert}\n    - {skill: combat, tier: expert}\n",
                "a:\n  proficiencies:\n    axes: expert\n",
                "a:\n  proficiencies:\n    swords: master\n",
                "a: 5\n");
        for (String yaml : broken) {
            assertThrows(AbilityRegistry.AbilityException.class, () -> parse(yaml), yaml);
        }
    }
}
