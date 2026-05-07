package ir.bita.esm.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import ir.bita.esm.auth.entity.Role;
import ir.bita.esm.auth.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT token provider for generating and validating tokens.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Generates an access token for a user.
     */
    public String generateAccessToken(User user) {
        return generateToken(user, accessTokenExpiration, "access");
    }

    /**
     * Generates a refresh token for a user.
     */
    public String generateRefreshToken(User user) {
        return generateToken(user, refreshTokenExpiration, "refresh");
    }

    /**
     * Gets the access token expiration time in seconds.
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    private String generateToken(User user, long expiration, String type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        String roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("mobile", user.getMobileNumber())
                .claim("name", user.getFullName())
                .claim("roles", roles)
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extracts user ID from token.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extracts mobile number from token.
     */
    public String getMobileFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("mobile", String.class);
    }

    /**
     * Extracts roles from token.
     */
    public Set<Role> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        String rolesStr = claims.get("roles", String.class);
        if (rolesStr == null || rolesStr.isEmpty()) {
            return Set.of();
        }
        return Set.of(rolesStr.split(",")).stream()
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    /**
     * Validates a token.
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if token is an access token.
     */
    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "access".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if token is a refresh token.
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
