package org.example.attendTrack.dashboard;

import java.util.List;

public record DashboardExtended(
        List<DayAttendance> thisWeek,
        List<DayAttendance> lastWeek,
        List<SiteAttendance> sites,
        List<ActivityEntry> recentActivity,
        List<String> inactiveSiteNames,
        long autoCheckoutsLastNight,
        List<AbsenteeRow> topAbsentees
) {}
