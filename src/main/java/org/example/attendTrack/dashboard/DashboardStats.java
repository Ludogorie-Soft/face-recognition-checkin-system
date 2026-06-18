package org.example.garant.dashboard;

public record DashboardStats(
        long totalSites,
        long totalWorkers,
        long presentToday,
        long missingToday
) {}
