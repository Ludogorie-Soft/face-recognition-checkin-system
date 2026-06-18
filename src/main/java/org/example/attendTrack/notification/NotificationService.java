package org.example.garant.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.example.garant.site.Site;
import org.example.garant.site.SiteManagerRepository;
import org.example.garant.user.Role;
import org.example.garant.user.User;
import org.example.garant.user.UserRepository;
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
    private final SiteManagerRepository siteManagerRepository;
    private final UserRepository userRepository;
    private final Optional<PushService> pushService;

    /**
     * Notifies all admins and managers of the site about missing workers.
     */
    public void notifyMissingWorkers(Site site, List<User> missingWorkers) {
        if (missingWorkers.isEmpty()) return;

        String subject = "Missing workers – " + site.getName();
        String body = buildMissingWorkersBody(site, missingWorkers);

        List<User> recipients = resolveRecipients(site);
        List<UUID> recipientIds = recipients.stream().map(User::getId).toList();

        recipients.forEach(user -> sendEmail(user.getEmail(), subject, body));
        sendPushToUsers(recipientIds, subject, body);
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

    private List<User> resolveRecipients(Site site) {
        List<User> admins = userRepository.findAllByRoleAndActiveTrue(Role.ADMIN);
        List<User> managers = siteManagerRepository.findBySiteId(site.getId())
                .stream().map(sm -> sm.getUser()).toList();

        return java.util.stream.Stream.concat(admins.stream(), managers.stream())
                .distinct()
                .toList();
    }

    private String buildMissingWorkersBody(Site site, List<User> missingWorkers) {
        StringBuilder sb = new StringBuilder();
        sb.append("The following workers did not check in at site \"")
                .append(site.getName()).append("\" today:\n\n");
        missingWorkers.forEach(w -> sb.append("  - ").append(w.getName()).append("\n"));
        return sb.toString();
    }
}
