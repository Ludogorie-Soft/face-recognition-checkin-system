package org.example.garant.auth;

import lombok.RequiredArgsConstructor;
import org.example.garant.auth.dto.AuthResponse;
import org.example.garant.auth.dto.LoginRequest;
import org.example.garant.user.User;
import org.example.garant.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtTokenProvider.generateToken(user);
        return AuthResponse.of(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
