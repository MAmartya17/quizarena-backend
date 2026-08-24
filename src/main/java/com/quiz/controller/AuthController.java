package com.quiz.controller;

import com.quiz.dto.AuthResponse;
import com.quiz.dto.GoogleLoginRequest;
import com.quiz.security.UserPrincipal;
import com.quiz.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest req) {
        return ResponseEntity.ok(userService.loginWithGoogle(req));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        var u = principal.getUser();
        return ResponseEntity.ok(Map.of(
                "id", u.getId(), "email", u.getEmail(),
                "name", u.getName(), "pictureUrl", u.getPictureUrl()
        ));
    }
}