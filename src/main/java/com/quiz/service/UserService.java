package com.quiz.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.quiz.dto.AuthResponse;
import com.quiz.dto.GoogleLoginRequest;
import com.quiz.entity.User;
import com.quiz.repository.UserRepository;
import com.quiz.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GoogleTokenVerifierService googleVerifier;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleVerifier.verify(request.getIdToken());
        String email   = payload.getEmail();
        String sub     = payload.getSubject();
        String name    = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        User user = userRepository.findByGoogleSub(sub)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> { existing.setGoogleSub(sub); return existing; })
                        .orElseGet(() -> User.builder()
                                .email(email).name(name).googleSub(sub)
                                .pictureUrl(picture).role(User.Role.USER).build()));
        user.setName(name);
        user.setPictureUrl(picture);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(), user.getPictureUrl());
    }
}