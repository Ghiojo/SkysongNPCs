package org.skysongdev.skysongnpcs.api.choices;

/**
 * What spending proficiency picks did.
 *
 * @since 0.15.0
 */
public enum PickResult {
    /** Saved: every picked proficiency is at the grant's tier now. */
    DONE,
    /** The grant does not exist, or its NPC is not managed. */
    NO_SUCH_GRANT,
    /** No proficiency was given. */
    NOTHING_PICKED,
    /** More proficiencies than the grant has picks left. */
    TOO_MANY,
    /** SkysongStats has no such proficiency. */
    UNKNOWN_PROFICIENCY,
    /** The proficiency is of another skill than the grant's, or the NPC has no picks of its skill left. */
    WRONG_SKILL,
    /** The proficiency is already at the grant's tier or above, or was picked already. */
    NOT_AN_OPTION,
    /** The NPC has no stat sheet. */
    NO_SHEET
}
