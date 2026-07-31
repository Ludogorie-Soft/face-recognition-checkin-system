package org.example.attendTrack.dashboard;

import java.util.List;

public record DashboardExtended(
        List<DayAttendance> thisWeek,
        List<DayAttendance> lastWeek,
        List<SiteAttendance> sites,
        List<ActivityEntry> recentActivity,
        long autoCheckoutsLastNight,
        List<OutOfZoneEntry> outOfZoneToday
) {}
