package org.skysongdev.skysongnpcs.picks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.FakeProficiencyCatalog;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.choices.NpcChoices;
import org.skysongdev.skysongnpcs.api.choices.PickResult;
import org.skysongdev.skysongnpcs.api.choices.ProficiencyPickGrant;
import org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent;
import org.skysongdev.skysongnpcs.api.event.NpcRespecEvent;
import org.skysongdev.skysongnpcs.api.event.NpcSetupCompleteEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.choices.ChoicePage;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.commands.ChooseCommand;
import org.skysongdev.skysongnpcs.commands.CommandRunner;
import org.skysongdev.skysongnpcs.commands.NpcTargeting;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.commands.PicksCommand;
import org.skysongdev.skysongnpcs.commands.RespecCommand;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proficiency picks on the running plugin: /snpc picks, the picks page, the setup step, the API and NPC deletion. */
class PicksInGameTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeProficiencyCatalog catalog;
    private ProficiencyPicks picks;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        catalog = plugin.fakeProficiencies();
        picks = plugin.picks();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new PicksCommand(targeting, plugin.access(), picks),
                new ChooseCommand(targeting, plugin.access(), plugin.choiceMenu()),
                new RespecCommand(targeting, plugin.choiceMenu()));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void staffGrantAndTakePicks() throws CommandSyntaxException {
        commands.run(staff, "snpc picks grant fight expert 2");
        assertEquals("[NPCs] Guard got 2 Combat pick(s) up to Expert.", plain(staff));
        assertEquals(2, picks.unspent(guard, PicksCommand.STAFF_SOURCE));

        commands.run(staff, "snpc picks grant dancing expert");
        assertEquals("[NPCs] There is no skill called dancing.", plain(staff));
        commands.run(staff, "snpc picks grant combat godlike");
        assertEquals("[NPCs] There is no tier called godlike.", plain(staff));

        commands.run(staff, "snpc picks take staff 1");
        assertEquals("[NPCs] Took 1 unspent pick(s) from staff away from Guard.", plain(staff));
        assertEquals(1, picks.pending(guard));
    }

    @Test
    void playersCannotGrantPicks() {
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc picks grant combat expert"));
        assertThrows(CommandSyntaxException.class, () -> commands.run(alice, "snpc picks take staff"));
    }

    @Test
    void theOwnerListsAndSpendsPicks() throws CommandSyntaxException {
        picks.grant(guard, "template", "combat", "proficient", 2);

        commands.run(alice, "snpc picks");
        assertEquals("[NPCs] Guard's proficiency picks, 2 left:", plain(alice));
        assertEquals("- Combat up to Proficient: 2 of 2 left (template)", plain(alice));

        commands.run(alice, "snpc picks pick swords");
        assertEquals("[NPCs] Guard's Swords is now Proficient.", plain(alice));
        assertEquals(Optional.of("proficient"), catalog.tier(guard, "swords"));

        commands.run(alice, "snpc picks pick swords");
        assertEquals("[NPCs] Swords is already at Proficient or above, or picked already.", plain(alice));
        commands.run(alice, "snpc picks pick smithing");
        assertEquals("[NPCs] Guard has no Metalworking picks left.", plain(alice));
        commands.run(alice, "snpc picks pick juggling");
        assertEquals("[NPCs] There is no proficiency called juggling.", plain(alice));
    }

    @Test
    void othersMayNotMakeTheChoices() throws CommandSyntaxException {
        picks.grant(guard, "template", "combat", "proficient", 1);

        commands.run(bob, "snpc picks pick swords");

        assertEquals("[NPCs] You may not make Guard's choices.", plain(bob));
        assertEquals(1, picks.pending(guard));
    }

    @Test
    void thePicksPageSpendsWhatIsSelectedOnConfirm() {
        picks.grant(guard, "template", "combat", "expert", 2);
        picks.grant(guard, "template", "metalworking", "proficient", 1);
        NpcRecord npc = plugin.registry().get(guard).orElseThrow();
        int[] backs = {0};

        page().open(alice, npc, () -> backs[0]++);
        Inventory first = shown(alice);
        assertEquals(Material.BOOK, first.getItem(0).getType());
        click(alice, 0);
        click(alice, 2);
        click(alice, 1);
        assertEquals(Material.ENCHANTED_BOOK, first.getItem(0).getType());
        assertEquals(Material.BOOK, first.getItem(1).getType());
        click(alice, first.getSize() - 1);
        server.getScheduler().performOneTick();

        assertEquals(Optional.of("expert"), catalog.tier(guard, "swords"));
        assertEquals(Optional.of("expert"), catalog.tier(guard, "bows"));
        assertEquals(Optional.of("untrained"), catalog.tier(guard, "axes"));
        assertEquals("[NPCs] Guard's Swords is now Expert.", plain(alice));
        assertEquals("[NPCs] Guard's Bows is now Expert.", plain(alice));

        // The next grant opens; confirming it goes back to the choice menu.
        Inventory second = shown(alice);
        click(alice, 0);
        click(alice, second.getSize() - 1);
        server.getScheduler().performOneTick();
        assertEquals(Optional.of("proficient"), catalog.tier(guard, "smithing"));
        assertEquals(0, picks.pending(guard));
        assertEquals(1, backs[0]);
    }

    @Test
    void theChoiceMenuListsThePicksPageAndRespecRefundsThem() throws CommandSyntaxException {
        assertTrue(plugin.choiceMenu().pages().stream().anyMatch(page -> page.id().equals(PicksPage.ID)));
        PickGrant grant = picks.grant(guard, "template", "combat", "expert", 1);
        picks.pick(alice, grant.id(), List.of("axes"));

        commands.run(staff, "snpc respec");

        assertEquals(Optional.of("untrained"), catalog.tier(guard, "axes"));
        assertEquals(1, picks.pending(guard));
    }

    @Test
    void thePicksPageOffersPromotionsOfWhatTheGrantPicked() {
        PickGrant grant = picks.grant(guard, "economy:martial", "combat", "proficient", 3, "expert", 1);
        picks.pick(alice, grant.id(), List.of("swords"));
        NpcRecord npc = plugin.registry().get(guard).orElseThrow();

        page().open(alice, npc, () -> { });
        Inventory shown = shown(alice);
        // axes and bows to pick, then swords to promote.
        assertEquals(Material.BOOK, shown.getItem(0).getType());
        assertEquals(Material.BOOK, shown.getItem(1).getType());
        assertEquals(Material.GLASS_BOTTLE, shown.getItem(2).getType());
        click(alice, 2);
        assertEquals(Material.EXPERIENCE_BOTTLE, shown.getItem(2).getType());
        click(alice, 0);
        click(alice, shown.getSize() - 1);
        server.getScheduler().performOneTick();

        assertEquals(Optional.of("expert"), catalog.tier(guard, "swords"));
        assertEquals(Optional.of("proficient"), catalog.tier(guard, "axes"));
        assertEquals(0, picks.remaining(grant));
        assertEquals("[NPCs] Guard's Axes is now Proficient.", plain(alice));
        assertEquals("[NPCs] Guard's Swords is promoted to Expert.", plain(alice));
    }

    @Test
    void promotingByCommandAndGrantingPromotionsAsStaff() throws CommandSyntaxException {
        commands.run(staff, "snpc picks grant combat proficient 2 expert");
        assertEquals("[NPCs] Guard got 2 Combat pick(s) up to Proficient, which may promote up to Expert for 1 pick(s) each.",
                plain(staff));
        commands.run(staff, "snpc picks grant combat expert 1 proficient");
        assertEquals("[NPCs] Promotions must go above Expert, not to Proficient.", plain(staff));

        commands.run(alice, "snpc picks promote swords");
        assertEquals("[NPCs] Guard cannot promote Swords now.", plain(alice));
        commands.run(alice, "snpc picks pick swords");
        plain(alice);
        commands.run(alice, "snpc picks promote swords");
        assertEquals("[NPCs] Guard's Swords is promoted to Expert.", plain(alice));
        assertEquals(PickResult.TOO_MANY, SkysongNpcsApi.get().choices().promote(guard, "swords"));
        assertEquals(0, SkysongNpcsApi.get().choices().unspentPicks(guard));
    }

    @Test
    void theApiGrantsPromotingPicks() {
        ProficiencyPickGrant grant = SkysongNpcsApi.get().choices().grantPicks(guard, "economy:martial", "combat",
                "proficient", 2, "expert", 1);
        assertEquals("expert", grant.promoteTo());
        assertEquals(1, grant.promotionCost());
        SkysongNpcsApi.get().choices().spendPick(guard, "axes");

        assertEquals(PickResult.DONE, SkysongNpcsApi.get().choices().promote(guard, "axes"));

        ProficiencyPickGrant after = SkysongNpcsApi.get().choices().pickGrants(guard).getFirst();
        assertEquals(List.of("axes"), after.promoted());
        assertEquals(0, after.unspent());
    }

    @Test
    void staffCanRespecOneSourceAndEveryRespecFiresAnEvent() throws CommandSyntaxException {
        List<NpcRespecEvent> respecs = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onRespec(NpcRespecEvent event) {
                respecs.add(event);
            }
        }, plugin);
        PickGrant economy = picks.grant(guard, "economy:martial", "combat", "expert", 1);
        PickGrant template = picks.grant(guard, "template", "combat", "expert", 1);
        picks.pick(alice, economy.id(), List.of("axes"));
        picks.pick(alice, template.id(), List.of("swords"));

        commands.run(staff, "snpc respec economy:martial");

        assertEquals(Optional.of("untrained"), catalog.tier(guard, "axes"));
        assertEquals(Optional.of("expert"), catalog.tier(guard, "swords"));
        assertEquals(1, respecs.size());
        assertEquals("economy:martial", respecs.get(0).getSource());
        assertEquals(1, respecs.get(0).getGivenBack());

        assertEquals(1, SkysongNpcsApi.get().choices().respec(guard));
        assertTrue(respecs.get(1).isEverySource());
        assertEquals(0, SkysongNpcsApi.get().choices().respec(guard, "economy:martial"));
        assertEquals(3, respecs.size());
    }

    @Test
    void theSetupAsksForPicksAfterThePoints() {
        plugin.registry().setState(guard, NpcState.SETUP);
        picks.grant(guard, "template", "combat", "expert", 1);
        assertTrue(plugin.choiceMenu().setup().steps().stream().map(step -> step.id()).toList()
                .indexOf(PicksPage.ID) > plugin.choiceMenu().setup().steps().stream().map(step -> step.id()).toList()
                .indexOf("points"));

        plugin.choiceMenu().open(alice, guard);
        Inventory inventory = shown(alice);
        click(alice, 0);
        click(alice, inventory.getSize() - 1);
        server.getScheduler().performOneTick();

        assertEquals(Optional.of("expert"), catalog.tier(guard, "swords"));
        assertEquals(NpcState.PLACED, plugin.registry().get(guard).orElseThrow().state());
        server.getPluginManager().assertEventFired(NpcSetupCompleteEvent.class);
    }

    @Test
    void theSetupSkipsPicksWhenNothingCanBePicked() {
        plugin.registry().setState(guard, NpcState.SETUP);
        picks.grant(guard, "template", "metalworking", "proficient", 1);
        catalog.setTier(guard, "smithing", "expert");

        plugin.choiceMenu().open(alice, guard);
        server.getScheduler().performOneTick();

        assertEquals(NpcState.PLACED, plugin.registry().get(guard).orElseThrow().state());
    }

    @Test
    void theApiGrantsSpendsAndTakesPicks() {
        NpcChoices choices = server.getServicesManager().load(SkysongNpcsApi.class).choices();

        ProficiencyPickGrant grant = choices.grantPicks(guard, "economy:smithy", "combat", "proficient", 2);
        assertEquals(2, grant.unspent());
        assertEquals(PickResult.DONE, choices.spendPick(guard, "bows"));
        assertEquals(List.of("bows"), choices.pickGrants(guard).getFirst().picked());
        assertEquals(1, choices.unspentPicks(guard, "economy:smithy"));
        server.getPluginManager().assertEventFired(NpcChoiceResolvedEvent.class, event ->
                event.getKind().equals(NpcChoiceResolvedEvent.PROFICIENCY_PICKS) && event.getChoice().equals("bows")
                        && event.getAmount() == 1 && event.getPlayer() == null);

        assertEquals(1, choices.takeUnspentPicks(guard, "economy:smithy", 5));
        assertEquals(0, choices.unspentPicks(guard));
        assertEquals(PickResult.WRONG_SKILL, choices.spendPick(guard, "axes"));
        assertThrows(IllegalArgumentException.class, () -> choices.grantPicks(guard, "x", "dancing", "expert", 1));
    }

    @Test
    void releasingTheNpcDropsItsPicks() {
        picks.grant(guard, "template", "combat", "expert", 1);

        plugin.registry().release(guard);

        assertEquals(List.of(), picks.grants(guard));
    }

    @Test
    void aNewNpcGetsItsTemplatesPicks(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("guard.yml"), "proficiency-picks:\n  - skill: combat\n    tier: expert\n");
        TemplatePicks templatePicks = new TemplatePicks(folder.toFile(), catalog, Logger.getLogger("test"));
        templatePicks.reload();
        StartingPicks starting = new StartingPicks(picks, templatePicks);
        NpcRecord npc = plugin.registry().get(guard).orElseThrow();

        starting.created(npc, new NpcTemplate("ghost", EntityType.PLAYER, "Ghost", null, Map.of()), null);
        assertEquals(0, picks.pending(guard));
        starting.created(npc, new NpcTemplate("guard", EntityType.PLAYER, "Guard", null, Map.of()), null);
        assertEquals(1, picks.unspent(guard, TemplatePicks.SOURCE));
    }

    private ChoicePage page() {
        return plugin.choiceMenu().pages().stream().filter(page -> page.id().equals(PicksPage.ID)).findFirst().orElseThrow();
    }

    /** Runs the tick that opens a page, and returns it. */
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
