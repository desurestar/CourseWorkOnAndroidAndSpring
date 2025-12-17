package ru.zagrebin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.zagrebin.model.User;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;

@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs
    ) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT secret must be provided via configuration");
        }
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ensureBase64(secret)));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claims(Map.of(
                        "uid", user.getId(),
                        "role", user.getRole() == null ? "user" : user.getRole()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String ensureBase64(String secret) {
        try {
            Decoders.BASE64.decode(secret);
            return secret;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT secret must be base64 encoded");
        }
    }
}
