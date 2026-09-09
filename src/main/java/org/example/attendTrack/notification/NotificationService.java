package org.example.attendTrack.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final Optional<PushService> pushService;

    /**
     * Notifies all admins and managers of the site about missing workers.
     */
    public void notifyMissingWorkers(Site site, List<User> missingWorkers) {
        if (missingWorkers.isEmpty()) return;

        String subject = "Missing workers – " + site.getName();
        String body = buildMissingWorkersBody(site, missingWorkers);

        List<User> recipients = resolveRecipients();
        List<UUID> recipientIds = recipients.stream().map(User::getId).toList();

        recipients.forEach(user -> sendEmail(user.getEmail(), subject, body));
        sendPushToUsers(recipientIds, subject, body);
    }

    /**
     * Alerts admins about 12/24h shifts left open. These are deliberately NOT auto-closed —
     * inventing an end time would silently corrupt payroll, so a human enters the real one.
     */
    public void notifyOpenGuardShifts(List<org.example.attendTrack.dashboard.OpenShiftEntry> openShifts) {
        if (openShifts.isEmpty()) return;

        String subject = "Незатворени смени на пазачи (" + openShifts.size() + ")";
        StringBuilder body = new StringBuilder("Следните смени стоят отворени и изискват ръчна корекция:\n\n");
        openShifts.forEach(e -> body
                .append("• ").append(e.workerName())
                .append(" — ").append(e.siteName())
                .append(", от ").append(e.since()).append('\n'));

        List<User> recipients = resolveRecipients();
        recipients.forEach(user -> sendEmail(user.getEmail(), subject, body.toString()));
        sendPushToUsers(recipients.stream().map(User::getId).toList(), subject, body.toString());
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private void sendPushToUsers(List<UUID> userIds, String title, String body) {
        if (pushService.isEmpty()) return;

        String payload = """
                {"title":"%s","body":"%s"}
                """.formatted(title, body).trim();

        pushSubscriptionRepository.findByUserIds(userIds).forEach(sub -> {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload
                );
                pushService.get().send(notification);
            } catch (Exception e) {
                log.error("Failed to send push to endpoint {}: {}", sub.getEndpoint(), e.getMessage());
            }
        });
    }

    private List<User> resolveRecipients() {
        return userRepository.findAllByRoleAndActiveTrue(Role.ADMIN);
    }

    /**
     * Notifies all admins when a worker checks in from outside all site checkpoint zones.
     */
    public void notifySuspiciousCheckIn(Site site, User worker, double lat, double lng) {
        String subject = "Suspicious check-in – " + site.getName();
        String body = "Worker \"" + worker.getName() + "\" checked in at site \"" + site.getName()
                + "\" from an unexpected location.\n\nCoordinates: " + lat + ", " + lng;

        List<User> recipients = resolveRecipients();
        List<UUID> recipientIds = recipients.stream().map(User::getId).toList();

        recipients.forEach(u -> sendEmail(u.getEmail(), subject, body));
        sendPushToUsers(recipientIds, subject, body);
    }

    private String buildMissingWorkersBody(Site site, List<User> missingWorkers) {
        StringBuilder sb = new StringBuilder();
        sb.append("The following workers did not check in at site \"")
                .append(site.getName()).append("\" today:\n\n");
        missingWorkers.forEach(w -> sb.append("  - ").append(w.getName()).append("\n"));
        return sb.toString();
    }
}
