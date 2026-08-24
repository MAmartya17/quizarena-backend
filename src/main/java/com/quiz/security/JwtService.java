package com.quiz.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")           private String secret;
    @Value("${app.jwt.expiration-ms}")    private long expirationMs;

    private SecretKey key() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length < 32) {
                keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of("email", email))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key())
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public String extractEmail(String token) {
        return parse(token).get("email", String.class);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) { return false; }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}