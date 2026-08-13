package mekanism.qioprocessing.common.util;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Shared allocation-light hashing for persisted QIO identities and integrity signatures. */
public final class QIOHashing {

    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    });

    private QIOHashing() {
    }

    @Nonnull
    public static String sha256(@Nonnull CharSequence value) {
        Objects.requireNonNull(value, "value");
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return lowerHex(digest.digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Nonnull
    static String lowerHex(@Nonnull byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            encoded[index * 2] = LOWER_HEX[value >>> 4];
            encoded[index * 2 + 1] = LOWER_HEX[value & 0x0F];
        }
        return new String(encoded);
    }
}
