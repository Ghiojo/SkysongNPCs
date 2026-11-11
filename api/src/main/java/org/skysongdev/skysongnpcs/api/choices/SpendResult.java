package org.skysongdev.skysongnpcs.api.choices;

/** What spending stat points did. Only {@link #DONE} changed anything. */
public enum SpendResult {
    /** The points are on the stat. */
    DONE,
    /** The NPC is not managed or has no stat sheet. */
    NO_SHEET,
    /** SkysongStats has no such stat. */
    UNKNOWN_STAT,
    /** None of the NPC's unspent points may go on that stat. */
    NOT_ELIGIBLE,
    /** The NPC has fewer unspent points for that stat than asked. */
    NOT_ENOUGH_POINTS,
    /** The stat's base would go above its cap (config.yml or the template). */
    ABOVE_CAP
}
