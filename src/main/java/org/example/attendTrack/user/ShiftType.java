package org.example.attendTrack.user;

/**
 * How a worker's shift relates to the calendar day. This is a scheduling attribute, NOT a
 * permission — guards have exactly the same rights as any other worker.
 */
public enum ShiftType {
    /** Normal day shift: every calendar day starts a new session. */
    DAY,
    /**
     * 12/24-hour shift that routinely crosses midnight (guards). Sessions continue across the day
     * boundary and are never closed by the automatic end-of-day checkout.
     */
    SHIFT_24H
}
