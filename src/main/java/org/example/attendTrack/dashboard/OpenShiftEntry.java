package org.example.attendTrack.dashboard;

import java.time.LocalDateTime;

/** A 12/24h shift that has been open far longer than a shift can plausibly run. */
public record OpenShiftEntry(String workerName, String siteName, LocalDateTime since) {}
