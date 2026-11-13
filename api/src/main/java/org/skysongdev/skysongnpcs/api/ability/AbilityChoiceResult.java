package org.skysongdev.skysongnpcs.api.ability;

/**
 * What choosing abilities with an ability choice did.
 *
 * @since 0.23.0
 */
public enum AbilityChoiceResult {
    /** Saved: the NPC has every chosen ability now. */
    DONE,
    /** The choice does not exist, or its NPC is not managed. */
    NO_SUCH_CHOICE,
    /** No ability was given. */
    NOTHING_CHOSEN,
    /** More abilities than the choice has left. */
    TOO_MANY,
    /**
     * The ability is not one the choice offers, abilities.yml no longer defines it, the NPC
     * has it already, or it was given twice.
     */
    NOT_AN_OPTION
}
