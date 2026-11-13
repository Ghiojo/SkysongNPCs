package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.abilities.AbilityChoiceGrant;

import java.util.List;
import java.util.UUID;

/** Where NPCs' ability choices are kept between restarts: the grants and what was chosen with them. */
public interface AbilityChoiceStore extends AutoCloseable {

    /** Every saved grant. Called once on enable, on the main thread. */
    List<AbilityChoiceGrant> loadGrants();

    /** Every saved choice. Called once on enable, on the main thread. */
    List<StoredChoice> loadChoices();

    /** Saves a grant, adding it or replacing the one with the same id. */
    void saveGrant(AbilityChoiceGrant grant);

    /** Deletes a grant and its choices. */
    void deleteGrant(UUID grant);

    /** Saves an ability chosen with a grant. */
    void saveChoice(UUID grant, String ability);

    /** Deletes an ability chosen with a grant, if it is there. */
    void deleteChoice(UUID grant, String ability);

    /** Deletes every grant and choice of one NPC. */
    void deleteAll(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();

    /** An ability chosen with a grant. */
    record StoredChoice(UUID grant, String ability) {
    }
}
