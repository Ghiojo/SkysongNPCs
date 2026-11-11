package org.skysongdev.skysongnpcs.access;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActionPresetsTest {
    private ActionPresets presets;

    @BeforeEach
    void setUp() throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                access:
                  presets:
                    customer: [info, follow]
                    sloppy: [look, fly]
                """);
        presets = new ActionPresets(() -> config);
    }

    @Test
    void readsPresetsAndAll() {
        assertEquals(Optional.of(Set.of(NpcAction.INFO, NpcAction.FOLLOW)), presets.preset("Customer"));
        assertEquals(Optional.of(Set.of(NpcAction.LOOK)), presets.preset("sloppy"), "unknown ids are skipped");
        assertEquals(Optional.of(EnumSet.allOf(NpcAction.class)), presets.preset("all"));
        assertEquals(Optional.empty(), presets.preset("nobody"));
        assertEquals(List.of("all", "customer", "sloppy"), List.copyOf(presets.names()));
    }

    @Test
    void parsesActionsAndPresetsSplitBySpacesOrCommas() {
        ActionPresets.Parsed parsed = presets.parse("customer, inventory-edit  pickup");

        assertEquals(Set.of(NpcAction.INFO, NpcAction.FOLLOW, NpcAction.INVENTORY_EDIT, NpcAction.PICKUP), parsed.actions());
        assertNull(parsed.unknown());
        assertEquals(Set.of(), presets.parse("").actions());
    }

    @Test
    void namesTheFirstUnknownWord() {
        ActionPresets.Parsed parsed = presets.parse("info,fly,swim");

        assertEquals("fly", parsed.unknown());
        assertEquals(Set.of(), parsed.actions());
    }
}
