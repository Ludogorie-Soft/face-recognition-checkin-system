package org.example.attendTrack.dashboard;

import java.util.UUID;

public record SiteAttendance(UUID siteId, String siteName, long presentCount, long totalWorkers) {}
