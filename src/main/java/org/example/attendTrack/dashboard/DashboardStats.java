package org.example.attendTrack.dashboard;

public record DashboardStats(
        long totalSites,
        long totalWorkers,
        long presentToday,
        long missingToday
) {}
