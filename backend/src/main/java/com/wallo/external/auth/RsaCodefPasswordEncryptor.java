package com.wallo.external.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

public class RsaCodefPasswordEncryptor implements CodefPasswordEncryptor {

    private final PublicKey publicKey;

    public RsaCodefPasswordEncryptor(String encodedPublicKey) {
        this.publicKey = parsePublicKey(encodedPublicKey);
    }

    @Override
    public String encrypt(String password) {
        if (password == null || password.isBlank()) {
            return password;
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt CODEF password.", exception);
        }
    }

    private PublicKey parsePublicKey(String encodedPublicKey) {
        if (encodedPublicKey == null || encodedPublicKey.isBlank()) {
            throw new IllegalArgumentException("CODEF RSA public key is required.");
        }

        String normalized = encodedPublicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] decoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("Invalid CODEF RSA public key.", exception);
        }
    }
}
