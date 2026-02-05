package com.example.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record RotatedToken(User user, String plainToken) {
    }

    @Transactional
    public String issue(User user, long refreshTokenTtlSeconds) {
        revokeAllForUser(user);

        String plainToken = generateTokenValue();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.tokenHash = hashToken(plainToken);
        refreshToken.createdAt = Instant.now();
        refreshToken.expiresAt = refreshToken.createdAt.plusSeconds(refreshTokenTtlSeconds);
        refreshToken.persist();
        return plainToken;
    }

    @Transactional
    public RotatedToken rotate(String plainToken, long refreshTokenTtlSeconds) {
        RefreshToken existing = validate(plainToken);
        if (existing == null) {
            return null;
        }

        existing.revokedAt = Instant.now();

        String rotatedPlainToken = generateTokenValue();
        RefreshToken rotated = new RefreshToken();
        rotated.user = existing.user;
        rotated.tokenHash = hashToken(rotatedPlainToken);
        rotated.createdAt = Instant.now();
        rotated.expiresAt = rotated.createdAt.plusSeconds(refreshTokenTtlSeconds);
        rotated.persist();

        return new RotatedToken(existing.user, rotatedPlainToken);
    }

    @Transactional
    public User revokeByPlainToken(String plainToken) {
        RefreshToken existing = validate(plainToken);
        if (existing == null) {
            return null;
        }
        existing.revokedAt = Instant.now();
        return existing.user;
    }

    @Transactional
    public void revokeAllForUser(User user) {
        Instant now = Instant.now();
        RefreshToken.update("revokedAt = ?1 where user = ?2 and revokedAt is null", now, user);
    }

    public RefreshToken validate(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return null;
        }
        RefreshToken token = RefreshToken.findByTokenHash(hashToken(plainToken));
        if (token == null || token.isRevoked() || token.isExpired() || !Boolean.TRUE.equals(token.user.active)) {
            return null;
        }
        return token;
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
