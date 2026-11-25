package com.suma.security;

import com.suma.config.SumaProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private static final String ISSUER = "suma-tracking-tool";
    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(SumaProperties props, Clock clock) {
        byte[] bytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("suma.jwt.secret must be at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
        this.clock = clock;
    }

    public Duration accessTtl() { return accessTtl; }

    public String issueAccessToken(UUID userId, String username, String role, UUID sessionId) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("usr", username)
                .claim("role", role)
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public ParsedToken parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER)
                    .clock(() -> Date.from(Instant.now(clock))).build()
                    .parseSignedClaims(token).getPayload();
            return new ParsedToken(UUID.fromString(c.getSubject()), c.get("usr", String.class), c.get("role", String.class),
                    UUID.fromString(c.get("sid", String.class)), null);
        } catch (ExpiredJwtException e) {
            return new ParsedToken(null, e.getClaims().get("usr", String.class), null, null, TokenError.EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            return new ParsedToken(null, null, null, null, TokenError.INVALID);
        }
    }

    public enum TokenError { EXPIRED, INVALID }

    public record ParsedToken(UUID userId, String username, String role, UUID sessionId, TokenError error) {
        public boolean valid() { return error == null; }
    }

}
