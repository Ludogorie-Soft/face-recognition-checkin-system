package org.example.attendTrack.dashboard;

import java.time.LocalDateTime;

public record OutOfZoneEntry(String workerName, String siteName, LocalDateTime recordedAt) {}
