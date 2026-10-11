package mekanism.qioprocessing.common.pattern;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class PatternSignature {
    private final MessageDigest digest;

    PatternSignature() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    PatternSignature add(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
        return this;
    }

    String finish() {
        byte[] value = digest.digest();
        char[] hex = "0123456789abcdef".toCharArray();
        char[] result = new char[64];
        for (int i = 0; i < value.length; i++) {
            result[i * 2] = hex[(value[i] & 255) >>> 4];
            result[i * 2 + 1] = hex[value[i] & 15];
        }
        return new String(result);
    }
}

