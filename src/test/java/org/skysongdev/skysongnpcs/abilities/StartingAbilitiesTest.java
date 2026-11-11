package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.items.BlueprintTags;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Template abilities, the npc_abilities blueprint tag, and abilities giving real stat points
 * (M14) and proficiency picks (M15).
 */
class StartingAbilitiesTest {
    private OfflineSkysongNPCs plugin;
    private Location location;
    private Path templates;

    @BeforeEach
    void setUp() throws IOException {
        ServerMock server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        location = new Location(server.addSimpleWorld("world"), 0.5, 64, 0.5);
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), """
                tough:
                  modifiers:
                    strength: 1
                  points:
                    amount: 2
                  picks:
                    - skill: combat
                      tier: expert
                keen: {}
                """);
        assertTrue(plugin.abilities().definitions().reload());
        templates = plugin.getDataFolder().toPath().resolve("templates");
        Files.writeString(templates.resolve("guard.yml"), "abilities: [Tough, ghost]\n");
        plugin.templates().reload();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TemplateAbilities templateAbilities() {
        TemplateAbilities read = new TemplateAbilities(templates.toFile(), plugin.getLogger());
        assertEquals(List.of(), read.reload());
        return read;
    }

    private NpcRecord create(ItemStack blueprint) {
        NpcTemplate guard = plugin.templates().get("guard").orElseThrow();
        NpcRecord npc = plugin.factory().create(guard, null, NpcOwner.server(), location).orElseThrow();
        new StartingAbilities(plugin.abilities(), templateAbilities(), plugin.blueprints(), plugin.getLogger())
                .created(npc, guard, blueprint);
        return npc;
    }

    @Test
    void aNewNpcGetsItsTemplatesAbilitiesAndSkipsUnknownOnes() {
        NpcRecord npc = create(null);

        assertEquals(List.of("tough"), plugin.abilities().of(npc.id()).stream().map(AbilityDefinition::id).toList());
    }

    @Test
    void theBlueprintTagReplacesTheTemplatesAbilities() {
        ItemStack blueprint = new ItemStack(Material.PAPER);
        plugin.fakeItemTags().setDetached(blueprint, BlueprintTags.ABILITIES, " keen , KEEN");

        NpcRecord npc = create(blueprint);

        assertEquals(List.of("keen"), plugin.abilities().of(npc.id()).stream().map(AbilityDefinition::id).toList());
    }

    @Test
    void templateListsWithMistakesAreNamed() throws IOException {
        Files.writeString(templates.resolve("broken.yml"), "abilities: tough\n");

        assertEquals(List.of("templates/broken.yml"),
                new TemplateAbilities(templates.toFile(), plugin.getLogger()).reload());
    }

    @Test
    void abilitiesGiveRealStatPointsAndPicks() {
        UUID npc = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(npc, NpcOwner.server());
        plugin.sheets().create(npc, Map.of());
        AbilityService service = new AbilityService(plugin.registry(), plugin.abilities().definitions(),
                new org.skysongdev.skysongnpcs.storage.InMemoryAbilityStore(), plugin.fakeSheets(),
                new PluginAbilityChoices(plugin::statPoints, plugin::picks), event -> { });

        service.grant(npc, "tough");
        assertEquals(2, plugin.statPoints().unspent(npc, "ability:tough"));
        assertEquals(1, plugin.picks().unspent(npc, "ability:tough:combat/expert"));

        service.revoke(npc, "tough");
        assertEquals(0, plugin.statPoints().unspent(npc, "ability:tough"));
        assertEquals(0, plugin.picks().unspent(npc, "ability:tough:combat/expert"));
    }
}
