package org.example.attendTrack.attendance.dto;

import org.example.attendTrack.attendance.Attendance;

import java.time.LocalDateTime;
import java.util.UUID;

/** Full audit view of a single attendance record, for the admin report details modal. */
public record AttendanceDetail(
        UUID id,
        String workerName,
        String siteName,
        String type,
        LocalDateTime recordedAt,
        LocalDateTime syncedAt,
        String source,
        String ipAddress,
        String userAgent,
        String clientDeviceId,
        String appVersion,
        boolean createdOffline,
        boolean locationValid,
        double lat,
        double lng,
        Double faceConfidence,
        boolean manualOverride,
        String managerName,
        String clientType,
        /** Site the terminal claimed — null unless it differs from the one the server resolved. */
        String clientSiteName,
        /** Metres from the recorded position to the resolved site's zone; null when unmeasured. */
        Integer distanceMeters,
        boolean ignored,
        String ignoredReason
) {
    public static AttendanceDetail from(Attendance a) {
        return new AttendanceDetail(
                a.getId(),
                a.getWorker() != null ? a.getWorker().getName() : null,
                a.getSite() != null ? a.getSite().getName() : null,
                a.getType() != null ? a.getType().name() : null,
                a.getRecordedAt(),
                a.getSyncedAt(),
                a.getSource() != null ? a.getSource().name() : null,
                a.getIpAddress(),
                a.getUserAgent(),
                a.getClientDeviceId(),
                a.getAppVersion(),
                a.isCreatedOffline(),
                a.isLocationValid(),
                a.getLat(),
                a.getLng(),
                a.getFaceConfidence(),
                a.isManualOverride(),
                a.getManager() != null ? a.getManager().getName() : null,
                a.getClientType() != null ? a.getClientType().name() : null,
                // Only worth showing when the terminal got it wrong — otherwise it is noise.
                a.getClientSite() != null && a.getSite() != null
                        && !a.getClientSite().getId().equals(a.getSite().getId())
                        ? a.getClientSite().getName() : null,
                a.getDistanceMeters(),
                a.isIgnored(),
                a.getIgnoredReason() != null ? a.getIgnoredReason().name() : null
        );
    }
}
