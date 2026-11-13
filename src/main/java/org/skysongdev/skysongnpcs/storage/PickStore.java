package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickSpend;
import org.skysongdev.skysongnpcs.picks.PromotionSpend;

import java.util.List;
import java.util.UUID;

/** Where NPCs' proficiency picks are kept between restarts: the grants and what was picked with them. */
public interface PickStore extends AutoCloseable {

    /** Every saved grant. Called once on enable, on the main thread. */
    List<PickGrant> loadGrants();

    /** Every saved spend. Called once on enable, on the main thread. */
    List<PickSpend> loadSpends();

    /** Saves a grant with its promotion, adding it or replacing the one with the same id. */
    void saveGrant(PickGrant grant);

    /** Every saved promotion (since 0.22.0). Called once on enable, on the main thread. */
    List<PromotionSpend> loadPromotions();

    /** Deletes a grant, its spends and its promotions. */
    void deleteGrant(UUID grant);

    /** Saves a spend. */
    void saveSpend(PickSpend spend);

    /** Saves a promotion. */
    void savePromotion(PromotionSpend promotion);

    /** Deletes one of a grant's promotions, if it is there. */
    void deletePromotion(UUID grant, int step);

    /** Deletes a spend, if it is there. */
    void deleteSpend(UUID grant, String proficiency);

    /** Deletes every grant, spend and promotion of one NPC. */
    void deleteAll(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();
}
