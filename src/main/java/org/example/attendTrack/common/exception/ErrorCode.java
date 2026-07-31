package org.example.attendTrack.common.exception;

public enum ErrorCode {
    // Auth
    INVALID_CREDENTIALS,
    ACCESS_DENIED,

    // Users
    USER_NOT_FOUND,
    DUPLICATE_EMAIL,
    PASSWORD_REQUIRED,
    WRONG_ROLE,

    // Companies
    COMPANY_NOT_FOUND,

    // Sites
    SITE_NOT_FOUND,
    ALREADY_ASSIGNED,
    ASSIGNMENT_NOT_FOUND,
    SITE_NOT_ASSIGNED,
    WORKER_NOT_IN_COMPANY,

    // Face
    FACE_ALREADY_REGISTERED,

    // Attendance
    DUPLICATE_ATTENDANCE,
    ATTENDANCE_NOT_FOUND,

    // General
    VALIDATION_ERROR,
    INTERNAL_ERROR,
}
