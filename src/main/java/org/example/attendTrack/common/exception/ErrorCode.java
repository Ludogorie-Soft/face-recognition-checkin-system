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

    // Sites
    SITE_NOT_FOUND,
    ALREADY_ASSIGNED,
    ASSIGNMENT_NOT_FOUND,
    SITE_NOT_ASSIGNED,

    // Face
    FACE_ALREADY_REGISTERED,

    // Attendance
    DUPLICATE_ATTENDANCE,

    // General
    VALIDATION_ERROR,
    INTERNAL_ERROR,
}
