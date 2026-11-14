package com.restrovision.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JwtTokenProvider {

    public static final String EDGE_ISSUER = "restrovision-edge-auth";
    public static final String EDGE_AUDIENCE = "restrovision-backend";
    public static final String TOKEN_TYPE_EDGE = "EDGE_DEVICE";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${restrovision.jwt.secret}") String secret,
            @Value("${restrovision.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${restrovision.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateEdgeToken(String edgeNodeId, Long restaurantId, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .issuer(EDGE_ISSUER)
                .audience().add(EDGE_AUDIENCE).and()
                .subject(edgeNodeId)
                .claim("type", TOKEN_TYPE_EDGE)
                .claim("restaurantId", restaurantId)
                .claim("roles", Collections.singletonList("AI_SERVICE"))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateEdgeToken(String edgeNodeId, Long restaurantId) {
        // Default: 30 days validity for edge device tokens
        return generateEdgeToken(edgeNodeId, restaurantId, 30L * 24 * 3600 * 1000);
    }

    public String generateAccessToken(Long userId, Long restaurantId, String email, Set<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("restaurantId", restaurantId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId, Long restaurantId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("restaurantId", restaurantId)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
