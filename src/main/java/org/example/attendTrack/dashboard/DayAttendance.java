package org.example.attendTrack.dashboard;

import java.time.LocalDate;

public record DayAttendance(LocalDate date, long count) {}
