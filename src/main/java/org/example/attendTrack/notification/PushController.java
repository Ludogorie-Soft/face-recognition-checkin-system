package org.example.attendTrack.notification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.notification.dto.PushSubscriptionRequest;
import org.example.attendTrack.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    @Value("${vapid.public-key:}")
    private String vapidPublicKey;

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            @AuthenticationPrincipal User currentUser) {

        boolean exists = pushSubscriptionRepository
                .findByUserId(currentUser.getId())
                .stream()
                .anyMatch(s -> s.getEndpoint().equals(request.endpoint()));

        if (!exists) {
            pushSubscriptionRepository.save(PushSubscription.builder()
                    .user(currentUser)
                    .endpoint(request.endpoint())
                    .p256dh(request.keys().p256dh())
                    .auth(request.keys().auth())
                    .build());
        }

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(
            @Valid @RequestBody PushSubscriptionRequest request) {
        pushSubscriptionRepository.deleteByEndpoint(request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
