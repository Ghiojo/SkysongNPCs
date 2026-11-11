package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickSpend;

import java.util.List;
import java.util.UUID;

/** Where NPCs' proficiency picks are kept between restarts: the grants and what was picked with them. */
public interface PickStore extends AutoCloseable {

    /** Every saved grant. Called once on enable, on the main thread. */
    List<PickGrant> loadGrants();

    /** Every saved spend. Called once on enable, on the main thread. */
    List<PickSpend> loadSpends();

    /** Saves a grant, adding it or replacing the one with the same id. */
    void saveGrant(PickGrant grant);

    /** Deletes a grant and its spends. */
    void deleteGrant(UUID grant);

    /** Saves a spend. */
    void saveSpend(PickSpend spend);

    /** Deletes a spend, if it is there. */
    void deleteSpend(UUID grant, String proficiency);

    /** Deletes every grant and spend of one NPC. */
    void deleteAll(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();
}
