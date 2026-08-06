package org.example.attendTrack.attendance.dto;

import org.example.attendTrack.attendance.AttendanceType;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Status of one worker for a given site + day, used by the admin manual-attendance panel.
 * attendanceId and lastType are null when the worker has no attendance records for that day.
 * checkInTime / checkOutTime are the times of the first CHECK_IN and last CHECK_OUT.
 * calculatedHours is populated only when both check-in and check-out exist.
 */
public record WorkerDayStatus(
        UUID workerId,
        String workerName,
        UUID attendanceId,      // ID of the last attendance record; null → no record today
        AttendanceType lastType, // last recorded type; null → no record today
        LocalTime checkInTime,   // null if no check-in today
        LocalTime checkOutTime,  // null if no check-out today
        Double calculatedHours   // null if session is incomplete
) {}
