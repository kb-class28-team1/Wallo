package com.wallo.auth;

import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HS256 access/refresh JWT를 발급하고 검증한다. */
@Component
public class JwtTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String TOKEN_TYPE = "JWT";

    private final byte[] secret;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenService(
            @Value("${jwt.secret:change-this-wall-o-jwt-secret-at-least-32-bytes}") String secret,
            @Value("${jwt.access-token-ttl-seconds:1800}") long accessTokenTtlSeconds,
            @Value("${jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes.");
        }
        if (accessTokenTtlSeconds < 1 || refreshTokenTtlSeconds < 1) {
            throw new IllegalArgumentException("JWT token TTL must be positive.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, "access", accessTokenTtlSeconds);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, "refresh", refreshTokenTtlSeconds);
    }

    public Long parseAccessToken(String token) {
        return parse(token, "access");
    }

    public Long parseRefreshToken(String token) {
        return parse(token, "refresh");
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    private String createToken(Long userId, String tokenType, long ttlSeconds) {
        if (userId == null) {
            throw new IllegalArgumentException("JWT subject is required.");
        }
        long issuedAt = System.currentTimeMillis() / 1000;
        long expiresAt = issuedAt + ttlSeconds;
        String header = encode("{\"alg\":\"" + JWT_ALGORITHM + "\",\"typ\":\"" + TOKEN_TYPE + "\"}");
        String payload = encode("{\"sub\":\"" + userId + "\",\"type\":\""
                + tokenType + "\",\"iat\":" + issuedAt + ",\"exp\":" + expiresAt + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    private Long parse(String token, String expectedType) {
        try {
            if (token == null || token.isBlank()) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigest.isEqual(
                    sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String type = claim(payload, "type");
            long expiresAt = Long.parseLong(claim(payload, "exp"));
            if (!expectedType.equals(type) || expiresAt <= System.currentTimeMillis() / 1000) {
                throw new IllegalArgumentException();
            }
            return Long.valueOf(claim(payload, "sub"));
        } catch (Exception exception) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }
    }

    private String claim(String payload, String name) {
        String marker = "\"" + name + "\":";
        int start = payload.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException();
        start += marker.length();
        boolean quoted = payload.charAt(start) == '"';
        if (quoted) start++;
        int end = quoted ? payload.indexOf('"', start) : payload.indexOf(',', start);
        if (end < 0) end = payload.indexOf('}', start);
        if (end < 0) throw new IllegalArgumentException();
        return payload.substring(start, end);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 서명에 실패했습니다.", exception);
        }
    }
}
