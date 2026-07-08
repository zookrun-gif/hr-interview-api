package com.zook.hrinterview.interfaces.auth.security;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTokenProvider implements InitializingBean {

    private final String secret;

    private final long expiresInSeconds;

    private SecretKey secretKey;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expires-in-seconds:86400}") long expiresInSeconds
    ) {
        this.secret = secret;
        this.expiresInSeconds = expiresInSeconds;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("security.jwt.secret must be configured and at least 32 characters long");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(LoginUser loginUser) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + Duration.ofSeconds(expiresInSeconds).toMillis());
        return Jwts.builder()
                .setSubject(String.valueOf(loginUser.getId()))
                .claim("name", loginUser.getName())
                .claim("email", loginUser.getEmail())
                .claim("role", loginUser.getRole())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public LoginUser parseToken(String token) {
        try {
            Claims claims = parseClaims(token);

            LoginUser loginUser = new LoginUser();
            loginUser.setId(Long.valueOf(claims.getSubject()));
            loginUser.setName(claims.get("name", String.class));
            loginUser.setEmail(claims.get("email", String.class));
            loginUser.setRole(claims.get("role", String.class));
            return loginUser;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseClaims(token);
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return 0L;
            }
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            return remainingMillis <= 0 ? 0L : Math.max(1L, remainingMillis / 1000L);
        } catch (JwtException | IllegalArgumentException ex) {
            return 0L;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
