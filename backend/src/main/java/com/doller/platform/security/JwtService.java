package com.doller.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey activeKey;
    private final SecretKey previousKey;
    private final long accessExpiryMinutes;
    private final long refreshExpiryDays;
    private final String issuer;

    public JwtService(@Value("${app.jwt.active-secret}") String activeSecret,
                      @Value("${app.jwt.previous-secret:}") String previousSecret,
                      @Value("${app.jwt.access-expiry-minutes}") long accessExpiryMinutes,
                      @Value("${app.jwt.refresh-expiry-days}") long refreshExpiryDays,
                      @Value("${app.jwt.issuer}") String issuer) {
        this.activeKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(activeSecret.getBytes())));
        this.previousKey = previousSecret == null || previousSecret.isBlank() ? null :
                Keys.hmacShaKeyFor(Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(previousSecret.getBytes())));
        this.accessExpiryMinutes = accessExpiryMinutes;
        this.refreshExpiryDays = refreshExpiryDays;
        this.issuer = issuer;
    }

    public String issueAccessToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("role", role)
                .claim("typ", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpiryMinutes * 60)))
                .signWith(activeKey)
                .compact();
    }

    public String issueRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("typ", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshExpiryDays * 24 * 3600)))
                .signWith(activeKey)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().requireIssuer(issuer).verifyWith(activeKey).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            if (previousKey == null) throw ex;
            return Jwts.parser().requireIssuer(issuer).verifyWith(previousKey).build().parseSignedClaims(token).getPayload();
        }
    }

    public long getAccessExpirySeconds() { return accessExpiryMinutes * 60; }
    public long getRefreshExpirySeconds() { return refreshExpiryDays * 24 * 3600; }
}
