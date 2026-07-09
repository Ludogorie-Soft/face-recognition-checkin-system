package org.example.attendTrack.dashboard;

import org.example.attendTrack.attendance.AttendanceType;

import java.time.LocalDateTime;

public record ActivityEntry(String workerName, String siteName, AttendanceType type, LocalDateTime recordedAt) {}
