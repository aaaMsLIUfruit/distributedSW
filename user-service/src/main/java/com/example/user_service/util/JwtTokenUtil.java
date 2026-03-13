package com.example.user_service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secret;
    private final long expireSeconds;
    private final ObjectMapper objectMapper;

    public JwtTokenUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expire-seconds:7200}") long expireSeconds,
            ObjectMapper objectMapper) {
        this.secret = secret;
        this.expireSeconds = expireSeconds;
        this.objectMapper = objectMapper;
    }

    public String generateToken(Long userId, String username) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expireSeconds;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("username", username);
        payload.put("iat", now);
        payload.put("exp", exp);

        String encodedHeader = base64UrlEncode(toJson(header));
        String encodedPayload = base64UrlEncode(toJson(payload));
        String content = encodedHeader + "." + encodedPayload;
        String signature = base64UrlEncode(hmacSha256(content, secret));
        return content + "." + signature;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成JWT失败");
        }
    }

    private byte[] hmacSha256(String content, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("生成JWT签名失败");
        }
    }

    private String base64UrlEncode(String text) {
        return base64UrlEncode(text.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] bytes) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
