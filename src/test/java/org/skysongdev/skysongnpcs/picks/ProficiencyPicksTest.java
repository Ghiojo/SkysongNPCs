package org.skysongdev.skysongnpcs.picks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.FakeProficiencyCatalog;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.api.choices.PickResult;
import org.skysongdev.skysongnpcs.storage.InMemoryNpcStore;
import org.skysongdev.skysongnpcs.storage.InMemoryPickStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The pick rules: granting, spending, taking back, respec, delete and copy. */
class ProficiencyPicksTest {
    private final FakeProficiencyCatalog catalog = new FakeProficiencyCatalog();
    private final InMemoryPickStore store = new InMemoryPickStore();
    private final List<String> picked = new ArrayList<>();
    private NpcRegistry registry;
    private ProficiencyPicks picks;
    private UUID npc;

    @BeforeEach
    void setUp() {
        registry = new NpcRegistry(new InMemoryNpcStore(), new NpcRegistry.Changes() {
            @Override
            public void ownerChanged(NpcRecord record, NpcOwner previous) {
            }

            @Override
            public void cloned(NpcRecord original, NpcRecord copy) {
            }

            @Override
            public void deleted(NpcRecord record, Deletion why) {
            }
        });
        npc = UUID.randomUUID();
        registry.adopt(npc, NpcOwner.server());
        catalog.giveSheet(npc);
        picks = newPicks();
    }

    private ProficiencyPicks newPicks() {
        return new ProficiencyPicks(registry, catalog, store,
                (record, player, grant, proficiency) -> picked.add(proficiency), Logger.getLogger("test"));
    }

    @Test
    void grantsMergeBySourceSkillAndTier() {
        PickGrant first = picks.grant(npc, "template", "fight", "proficient", 1);
        PickGrant second = picks.grant(npc, "template", "combat", "proficient", 2);
        picks.grant(npc, "staff", "combat", "proficient", 1);

        assertEquals(first.id(), second.id());
        assertEquals(3, second.count());
        assertEquals(4, picks.pending(npc));
        assertEquals(3, picks.unspent(npc, "template"));
    }

    @Test
    void grantRefusesUnknownSkillsTiersAndNpcs() {
        assertThrows(IllegalArgumentException.class, () -> picks.grant(npc, "staff", "dancing", "expert", 1));
        assertThrows(IllegalArgumentException.class, () -> picks.grant(npc, "staff", "combat", "godlike", 1));
        assertThrows(IllegalArgumentException.class, () -> picks.grant(npc, "staff", "combat", "expert", 0));
        assertThrows(IllegalArgumentException.class, () -> picks.grant(npc, "two words", "combat", "expert", 1));
        assertThrows(IllegalArgumentException.class, () -> picks.grant(UUID.randomUUID(), "staff", "combat", "expert", 1));
    }

    @Test
    void pickingRaisesTheTierAndSpendsThePick() {
        PickGrant grant = picks.grant(npc, "template", "combat", "expert", 2);

        assertEquals(PickResult.DONE, picks.pick(null, grant.id(), List.of("swords", "bows")));

        assertEquals(Optional.of("expert"), catalog.tier(npc, "swords"));
        assertEquals(Optional.of("expert"), catalog.tier(npc, "bows"));
        assertEquals(0, picks.pending(npc));
        assertEquals(List.of("swords", "bows"), picked);
        assertEquals(2, store.loadSpends().size());
    }

    @Test
    void optionsLeaveOutWhatIsAtTheTierOrPicked() {
        PickGrant grant = picks.grant(npc, "template", "combat", "proficient", 2);
        catalog.setTier(npc, "axes", "expert");

        assertEquals(List.of("swords", "bows"), picks.options(grant));
        picks.pick(null, grant.id(), List.of("swords"));
        assertEquals(List.of("bows"), picks.options(grant));
    }

    @Test
    void picksThatBreakARuleSaveNothing() {
        PickGrant grant = picks.grant(npc, "template", "combat", "proficient", 1);
        catalog.setTier(npc, "axes", "expert");

        assertEquals(PickResult.TOO_MANY, picks.pick(null, grant.id(), List.of("swords", "bows")));
        assertEquals(PickResult.WRONG_SKILL, picks.pick(null, grant.id(), List.of("smithing")));
        assertEquals(PickResult.UNKNOWN_PROFICIENCY, picks.pick(null, grant.id(), List.of("juggling")));
        assertEquals(PickResult.NOT_AN_OPTION, picks.pick(null, grant.id(), List.of("axes")));
        assertEquals(PickResult.NOTHING_PICKED, picks.pick(null, grant.id(), List.of()));
        assertEquals(PickResult.NO_SUCH_GRANT, picks.pick(null, UUID.randomUUID(), List.of("swords")));

        assertEquals(1, picks.pending(npc));
        assertEquals(Optional.of("untrained"), catalog.tier(npc, "swords"));
        assertEquals(List.of(), picked);
    }

    @Test
    void anNpcWithoutASheetCannotPick() {
        UUID bare = UUID.randomUUID();
        registry.adopt(bare, NpcOwner.server());
        PickGrant grant = picks.grant(bare, "template", "combat", "proficient", 1);

        assertEquals(List.of(), picks.options(grant));
        assertEquals(PickResult.NO_SHEET, picks.pick(null, grant.id(), List.of("swords")));
    }

    @Test
    void removeUnspentOnlyTakesUnspentPicksOfThatSource() {
        PickGrant ability = picks.grant(npc, "ability:smith", "combat", "proficient", 3);
        picks.grant(npc, "template", "combat", "proficient", 1);
        picks.pick(null, ability.id(), List.of("swords"));

        assertEquals(1, picks.removeUnspent(npc, "ability:smith", 1));
        assertEquals(1, picks.unspent(npc, "ability:smith"));
        assertEquals(1, picks.removeUnspent(npc, "ability:smith"));

        assertEquals(0, picks.unspent(npc, "ability:smith"));
        assertEquals(1, picks.grant(ability.id()).orElseThrow().count());
        assertEquals(Optional.of("proficient"), catalog.tier(npc, "swords"));
        assertEquals(1, picks.unspent(npc, "template"));
    }

    @Test
    void anUnspentGrantTakenBackIsDeleted() {
        PickGrant grant = picks.grant(npc, "ability:smith", "metalworking", "expert", 2);

        assertEquals(2, picks.removeUnspent(npc, "ability:smith"));

        assertEquals(Optional.empty(), picks.grant(grant.id()));
        assertEquals(List.of(), store.loadGrants());
    }

    @Test
    void respecPutsTiersBackAndRefundsPicks() {
        PickGrant grant = picks.grant(npc, "template", "combat", "expert", 2);
        catalog.setTier(npc, "bows", "proficient");
        picks.pick(null, grant.id(), List.of("swords", "bows"));

        assertEquals(2, picks.respec(npc));

        assertEquals(Optional.of("untrained"), catalog.tier(npc, "swords"));
        assertEquals(Optional.of("proficient"), catalog.tier(npc, "bows"));
        assertEquals(2, picks.pending(npc));
        assertEquals(List.of(), store.loadSpends());
    }

    @Test
    void aPromotingGrantRaisesItsOwnPicksOneTierForItsCost() {
        PickGrant grant = picks.grant(npc, "economy:martial", "combat", "proficient", 3, "expert", 1);
        assertEquals(List.of(), picks.promotable(grant));
        picks.pick(null, grant.id(), List.of("swords"));
        catalog.setTier(npc, "bows", "proficient");

        // Only what this grant picked can be promoted, not bows, which was proficient anyway.
        assertEquals(List.of("swords"), picks.promotable(grant));
        assertEquals(PickResult.NOT_AN_OPTION, picks.promote(null, grant.id(), "bows"));
        assertEquals(PickResult.DONE, picks.promote(null, grant.id(), "swords"));

        assertEquals(Optional.of("expert"), catalog.tier(npc, "swords"));
        assertEquals(1, picks.remaining(grant));
        // At the promotion's tier: nothing more to promote.
        assertEquals(List.of(), picks.promotable(grant));
        assertEquals(PickResult.NOT_AN_OPTION, picks.promote(null, grant.id(), "swords"));
        assertEquals(List.of("swords", "swords"), picked);
        assertEquals(List.of("swords"), picks.toApi(grant).promoted());
        assertEquals(1, picks.toApi(grant).unspent());
    }

    @Test
    void aPromotionNeedsEnoughPicksLeft() {
        PickGrant grant = picks.grant(npc, "staff", "combat", "proficient", 2, "expert", 2);
        picks.pick(null, grant.id(), List.of("swords"));

        assertEquals(List.of(), picks.promotable(grant));
        assertEquals(PickResult.TOO_MANY, picks.promote(null, grant.id(), "swords"));
    }

    @Test
    void grantsOnlyMergeWithTheSamePromotion() {
        PickGrant plain = picks.grant(npc, "staff", "combat", "proficient", 1);
        PickGrant promoting = picks.grant(npc, "staff", "combat", "proficient", 1, "expert", 1);
        PickGrant more = picks.grant(npc, "staff", "combat", "proficient", 2, "expert", 1);

        assertNotEquals(plain.id(), promoting.id());
        assertEquals(promoting.id(), more.id());
        assertEquals(3, more.count());
        assertThrows(IllegalArgumentException.class,
                () -> picks.grant(npc, "staff", "combat", "expert", 1, "proficient", 1));
        assertThrows(IllegalArgumentException.class,
                () -> picks.grant(npc, "staff", "combat", "proficient", 1, "expert", 0));
    }

    @Test
    void respecUndoesPromotionsThenPicksAndTheGrantCanPromoteAgain() {
        PickGrant grant = picks.grant(npc, "economy:martial", "combat", "proficient", 2, "expert", 1);
        picks.pick(null, grant.id(), List.of("swords"));
        picks.promote(null, grant.id(), "swords");

        assertEquals(2, picks.respec(npc));

        assertEquals(Optional.of("untrained"), catalog.tier(npc, "swords"));
        assertEquals(2, picks.remaining(grant));
        assertEquals(List.of(), store.loadPromotions());
        picks.pick(null, grant.id(), List.of("axes"));
        assertEquals(PickResult.DONE, picks.promote(null, grant.id(), "axes"));
    }

    @Test
    void promotionsSurviveARestartAndCopy() {
        PickGrant grant = picks.grant(npc, "economy:martial", "combat", "proficient", 3, "expert", 1);
        picks.pick(null, grant.id(), List.of("swords"));
        picks.promote(null, grant.id(), "swords");

        ProficiencyPicks reloaded = newPicks();
        reloaded.load();
        PickGrant loaded = reloaded.grant(grant.id()).orElseThrow();
        assertEquals(grant.promotion(), loaded.promotion());
        assertEquals(1, reloaded.remaining(loaded));

        UUID copy = UUID.randomUUID();
        registry.adopt(copy, NpcOwner.server());
        reloaded.copy(npc, copy);
        PickGrant copied = reloaded.grants(copy).getFirst();
        assertEquals(1, reloaded.promotions(copied.id()).size());
        assertEquals(1, reloaded.remaining(copied));
    }

    @Test
    void respecForOneSourceLeavesTheOthersPicks() {
        PickGrant economy = picks.grant(npc, "economy:martial", "combat", "expert", 1);
        PickGrant template = picks.grant(npc, "template", "combat", "expert", 1);
        picks.pick(null, economy.id(), List.of("swords"));
        picks.pick(null, template.id(), List.of("bows"));

        assertEquals(1, picks.respec(npc, "economy:martial"));

        assertEquals(Optional.of("untrained"), catalog.tier(npc, "swords"));
        assertEquals(Optional.of("expert"), catalog.tier(npc, "bows"));
        assertEquals(1, picks.unspent(npc, "economy:martial"));
        assertEquals(0, picks.unspent(npc, "template"));
    }

    @Test
    void respecLeavesATierStaffChangedSince() {
        PickGrant grant = picks.grant(npc, "template", "combat", "proficient", 1);
        picks.pick(null, grant.id(), List.of("swords"));
        catalog.setTier(npc, "swords", "expert");

        picks.respec(npc);

        assertEquals(Optional.of("expert"), catalog.tier(npc, "swords"));
        assertEquals(1, picks.pending(npc));
    }

    @Test
    void picksSurviveARestart() {
        PickGrant grant = picks.grant(npc, "template", "combat", "proficient", 2);
        picks.pick(null, grant.id(), List.of("swords"));

        ProficiencyPicks restarted = newPicks();
        restarted.load();

        assertEquals(1, restarted.pending(npc));
        assertEquals(List.of("axes", "bows"), restarted.options(restarted.grant(grant.id()).orElseThrow()));
    }

    @Test
    void loadDropsPicksOfNpcsNoLongerManaged() {
        picks.grant(npc, "template", "combat", "proficient", 2);
        registry.release(npc);

        ProficiencyPicks restarted = newPicks();
        restarted.load();

        assertEquals(0, restarted.pending(npc));
        assertEquals(List.of(), store.loadGrants());
    }

    @Test
    void deleteAndCopyFollowTheNpc() {
        PickGrant grant = picks.grant(npc, "template", "combat", "proficient", 2);
        picks.pick(null, grant.id(), List.of("swords"));
        UUID copy = UUID.randomUUID();
        registry.adopt(copy, NpcOwner.server());

        picks.copy(npc, copy);
        picks.deleteAll(npc);

        assertEquals(0, picks.pending(npc));
        assertEquals(1, picks.pending(copy));
        PickGrant copied = picks.grants(copy).getFirst();
        assertEquals("swords", picks.spends(copied.id()).getFirst().proficiency());
        assertEquals(1, store.loadGrants().size());
    }
}
