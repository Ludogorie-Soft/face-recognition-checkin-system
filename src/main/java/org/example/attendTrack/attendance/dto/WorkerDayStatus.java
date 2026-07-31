package org.example.attendTrack.attendance.dto;

import org.example.attendTrack.attendance.AttendanceType;

import java.util.UUID;

/**
 * Status of one worker for a given site + day, used by the admin manual-attendance panel.
 * attendanceId and lastType are null when the worker has no attendance records for that day.
 */
public record WorkerDayStatus(
        UUID workerId,
        String workerName,
        UUID attendanceId,     // ID of the last attendance record; null → no record today
        AttendanceType lastType // last recorded type; null → no record today
) {}
