package org.example.attendTrack.dashboard;

public record AbsenteeRow(String workerName, long daysPresent, long totalDays, long absenceDays) {}
