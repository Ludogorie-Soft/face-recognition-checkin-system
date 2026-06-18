package org.example.attendTrack.common.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ApiError(
        int status,
        String code,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp
) {
    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(status, code.name(), message, LocalDateTime.now());
    }
}
