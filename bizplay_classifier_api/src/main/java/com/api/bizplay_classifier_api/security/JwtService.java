package com.api.bizplay_classifier_api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtService {
    public static final long JWT_TOKEN_VALIDITY = 31 * 24 * 60 * 60;
    private static final String STATIC_TOKEN_SUBJECT = "bizplay.admin@local";
    private static final Date STATIC_TOKEN_ISSUED_AT = Date.from(Instant.parse("2026-01-01T00:00:00Z"));
    private static final Date STATIC_TOKEN_EXPIRATION = Date.from(Instant.parse("2099-12-31T23:59:59Z"));
    private final String base64Secret;
    private final String staticToken;

    public JwtService(
            @Value("${app.jwt.base64-secret}") String base64Secret,
            @Value("${app.auth.static-token:}") String staticToken
    ) {
        this.base64Secret = base64Secret;
        this.staticToken = staticToken;
    }

    private String createToken(Map<String, Object> claim, String subject) {
        return Jwts.builder()
                .setClaims(claim)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY * 1000))
                .signWith(getSignKey(), SignatureAlgorithm.HS256).compact();
    }

    private String createStaticJwtToken() {
        return Jwts.builder()
                .setClaims(Map.of("type", "static"))
                .setSubject(STATIC_TOKEN_SUBJECT)
                .setIssuedAt(STATIC_TOKEN_ISSUED_AT)
                .setExpiration(STATIC_TOKEN_EXPIRATION)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignKey() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = base64Secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret key must be at least 32 bytes for HS256.");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    //2. generate token for user
    public String generateToken(UserDetails userDetails) {
        if (isStaticTokenEnabled()) {
            return createStaticJwtToken();
        }
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    //3. retrieving any information from token we will need the secret key
    private Claims extractAllClaim(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    //4. extract a specific claim from the JWT token’s claims.
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaim(token);
        return claimsResolver.apply(claims);
    }

    //5. retrieve username from jwt token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    //6. retrieve expiration date from jwt token
    public Date extractExpirationDate(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //7. check expired token
    private Boolean isTokenExpired(String token) {
        return extractExpirationDate(token).before(new Date());
    }

    //8. validate token
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public boolean isStaticToken(String token) {
        return isStaticTokenEnabled() && createStaticJwtToken().equals(token);
    }

    private boolean isStaticTokenEnabled() {
        return staticToken != null && !staticToken.isBlank() && !"disabled".equalsIgnoreCase(staticToken);
    }
}
