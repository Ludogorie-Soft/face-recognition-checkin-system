package org.example.attendTrack.attendance;

/**
 * Why the sync reconciliation flagged an attendance record as anomalous.
 * The record is still persisted — the flag only marks it for admin review.
 */
public enum AnomalyReason {
    /** A CHECK_IN arrived while the worker already had an open CHECK_IN (session not closed). */
    DUPLICATE_CHECK_IN,
    /** A CHECK_OUT arrived while the worker was already checked out (no open session). */
    DUPLICATE_CHECK_OUT,
    /** A CHECK_OUT arrived with no preceding open CHECK_IN on that day. */
    CHECKOUT_WITHOUT_CHECKIN
}
