package org.example.garant.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.garant.auth.dto.AuthResponse;
import org.example.garant.auth.dto.LoginRequest;
import org.example.garant.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                AuthResponse.of(null, user.getId(), user.getName(), user.getEmail(), user.getRole())
        );
    }
}
