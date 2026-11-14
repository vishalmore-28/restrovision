package com.restrovision.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class HmacValidator {

    private final String hmacSecret;

    public HmacValidator(@Value("${restrovision.edge.hmac-secret:8f4a2b1c9e3d7a5f6e8b0c2d4e6f8a0b1c3d5e7f9a1b3c5d7e9f1a3b5c7d9e1f}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public boolean verifySignature(byte[] rawBodyBytes, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            String expectedHash = signatureHeader.substring("sha256=".length()).trim();
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);

            byte[] hmacBytes = sha256Hmac.doFinal(rawBodyBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            String calculatedHash = sb.toString();

            return MessageDigest.isEqual(calculatedHash.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean verifySignature(String rawBody, String signatureHeader) {
        if (rawBody == null) return false;
        return verifySignature(rawBody.getBytes(StandardCharsets.UTF_8), signatureHeader);
    }
}
