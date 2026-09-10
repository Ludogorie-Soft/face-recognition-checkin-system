package org.example.attendTrack.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Pins the JVM default time zone.
 *
 * <p>Attendance times (`recorded_at`) come from the device as local wall-clock values, while
 * `synced_at`, "today" and the schedulers use {@code LocalDateTime.now()} on the server. If the two
 * disagree, the details view shows a record synced "before" it was recorded, the terminal's day
 * boundary drifts from the server's, and the end-of-day auto-checkout fires at the wrong local hour.
 *
 * <p>Set here rather than relying on the container's {@code TZ} so the behaviour is identical
 * however the app is started. Override with {@code APP_TIMEZONE} if the business moves.
 */
@Slf4j
@Configuration
public class TimeZoneConfig {

    @Value("${app.timezone:Europe/Sofia}")
    private String timezone;

    @PostConstruct
    void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        log.info("Application time zone set to {} (server local time is now {})",
                timezone, java.time.LocalDateTime.now());
    }
}
