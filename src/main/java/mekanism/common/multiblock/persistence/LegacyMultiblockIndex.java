package mekanism.common.multiblock.persistence;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, primitive UUID index of a read-only 1.12 WorldSavedData tombstone file.
 * This parser deliberately knows only the old schema: it never constructs a million NBT objects,
 * accesses a World, or turns malformed/partially read input into an empty index.
 */
public final class LegacyMultiblockIndex {
    public static final int MAX_RECORDS = 4_000_000;
    public static final long MAX_COMPRESSED_BYTES = 128L * 1024 * 1024;
    public static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;
    private final long[] table;
    private final int mask;
    private final boolean containsZero;
    private final int uniqueCount;
    public final int recordCount;
    public final long compressedBytes;
    public final long expandedBytes;
    public final long loadNanos;
    public final String sha256;
    public final boolean missing;

    private LegacyMultiblockIndex(Builder builder, long compressedBytes, long expandedBytes,
          long loadNanos, String sha256, boolean missing) {
        table = builder.table;
        mask = table.length / 2 - 1;
        containsZero = builder.containsZero;
        uniqueCount = builder.uniqueCount;
        recordCount = builder.recordCount;
        this.compressedBytes = compressedBytes;
        this.expandedBytes = expandedBytes;
        this.loadNanos = loadNanos;
        this.sha256 = sha256;
        this.missing = missing;
    }

    public int size() {
        return uniqueCount;
    }

    public long memoryBytes() {
        return (long) table.length * Long.BYTES;
    }

    public boolean contains(UUID id) {
        long high = id.getMostSignificantBits();
        long low = id.getLeastSignificantBits();
        if ((high | low) == 0) return containsZero;
        int slot = hash(high, low) & mask;
        while (true) {
            int offset = slot * 2;
            if ((table[offset] | table[offset + 1]) == 0) return false;
            if (table[offset] == high && table[offset + 1] == low) return true;
            slot = (slot + 1) & mask;
        }
    }

    public boolean contains(String id) {
        return contains(parseId(id));
    }

    public static UUID parseId(String id) {
        UUID uuid = UUID.fromString(id);
        if (id.length() != 36 || !uuid.toString().equalsIgnoreCase(id)) {
            throw new IllegalArgumentException("Non-canonical multiblock UUID: " + id);
        }
        return uuid;
    }

    /** Must be called by an IO worker, after the owner has resolved the real per-world data path. */
    public static LegacyMultiblockIndex read(Path file) throws IOException {
        return read(file, Files::newInputStream);
    }

    @FunctionalInterface
    interface InputOpener {
        InputStream open(Path file) throws IOException;
    }

    static LegacyMultiblockIndex read(Path file, InputOpener opener) throws IOException {
        long started = System.nanoTime();
        BasicFileAttributes before;
        try {
            before = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            // A wrong/unmounted/inaccessible parent is not an empty legacy history.
            BasicFileAttributes parent = Files.readAttributes(file.toAbsolutePath().getParent(), BasicFileAttributes.class);
            if (!parent.isDirectory() || !Files.isReadable(file.toAbsolutePath().getParent())) throw missing;
            return new LegacyMultiblockIndex(new Builder(0), 0, 0, System.nanoTime() - started, "", true);
        }
        if (!before.isRegularFile() || before.size() > MAX_COMPRESSED_BYTES) {
            throw new IOException("Invalid legacy tombstone file or compressed size: " + file);
        }
        MessageDigest digest = sha256Digest();
        Builder builder;
        long expanded;
        try (InputStream raw = opener.open(file);
             InputStream bounded = new BoundedInputStream(raw, MAX_COMPRESSED_BYTES);
             DigestInputStream hashed = new DigestInputStream(bounded, digest);
             StrictGzipInputStream gzip = new StrictGzipInputStream(hashed, MAX_EXPANDED_BYTES);
             DataInputStream input = new DataInputStream(new BufferedInputStream(gzip))) {
            require(input.readUnsignedByte() == 10, "root must be a compound");
            input.readUTF(); // Root name is not part of WorldSavedData's schema.
            require(input.readUnsignedByte() == 10 && "data".equals(input.readUTF()), "missing data compound");
            require(input.readUnsignedByte() == 9 && "invalidatedIDs".equals(input.readUTF()), "missing invalidatedIDs list");
            int type = input.readUnsignedByte();
            int count = input.readInt();
            require(count >= 0 && count <= MAX_RECORDS, "invalid or excessive record count");
            require(type == 8 || count == 0 && type == 0, "invalid UUID list element type");
            builder = new Builder(count);
            byte[] encoded = new byte[36];
            for (int i = 0; i < count; i++) {
                require(input.readUnsignedShort() == encoded.length, "UUID must contain 36 ASCII characters");
                input.readFully(encoded);
                long high = 0;
                long low = 0;
                int digits = 0;
                for (int c = 0; c < encoded.length; c++) {
                    int value = encoded[c] & 0xff;
                    if (c == 8 || c == 13 || c == 18 || c == 23) {
                        require(value == '-', "UUID separator is invalid");
                    } else {
                        int hex = value >= '0' && value <= '9' ? value - '0' :
                              value >= 'a' && value <= 'f' ? value - 'a' + 10 :
                                    value >= 'A' && value <= 'F' ? value - 'A' + 10 : -1;
                        require(hex >= 0, "UUID contains a non-hexadecimal character");
                        if (digits++ < 16) high = high << 4 | hex;
                        else low = low << 4 | hex;
                    }
                }
                builder.add(high, low);
            }
            require(input.readUnsignedByte() == 0 && input.readUnsignedByte() == 0, "unexpected NBT fields");
            // Reading EOF is mandatory: validates the gzip CRC, ISIZE and compressed trailing bytes.
            require(input.read() == -1, "trailing expanded data");
            expanded = gzip.expandedBytes();
        }
        BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || before.size() != after.size() ||
              !before.lastModifiedTime().equals(after.lastModifiedTime()) || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Legacy tombstone file changed while reading: " + file);
        }
        return new LegacyMultiblockIndex(builder, before.size(), expanded, System.nanoTime() - started, hex(digest.digest()), false);
    }

    static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static String hex(byte[] digest) {
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(Character.forDigit(value >>> 4 & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException("Invalid legacy multiblock tombstones: " + reason);
    }

    private static int hash(long high, long low) {
        long value = high ^ Long.rotateLeft(low, 23);
        value = (value ^ value >>> 33) * 0xff51afd7ed558ccdL;
        value = (value ^ value >>> 33) * 0xc4ceb9fe1a85ec53L;
        return (int) (value ^ value >>> 33);
    }

    private static final class Builder {
        final long[] table;
        final int recordCount;
        int uniqueCount;
        boolean containsZero;

        Builder(int records) {
            recordCount = records;
            int capacity = 2;
            while (capacity * 0.65 < records) capacity <<= 1;
            table = new long[capacity * 2];
        }

        void add(long high, long low) {
            if ((high | low) == 0) {
                if (!containsZero) uniqueCount++;
                containsZero = true;
                return;
            }
            int mask = table.length / 2 - 1;
            int slot = hash(high, low) & mask;
            while (true) {
                int offset = slot * 2;
                if ((table[offset] | table[offset + 1]) == 0) {
                    table[offset] = high;
                    table[offset + 1] = low;
                    uniqueCount++;
                    return;
                }
                if (table[offset] == high && table[offset + 1] == low) return;
                slot = (slot + 1) & mask;
            }
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        BoundedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0 && ++count > limit) throw new IOException("Compressed-byte budget exceeded");
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int read = in.read(bytes, offset, (int) Math.min(length, limit - count + 1));
            if (read > 0 && (count += read) > limit) throw new IOException("Compressed-byte budget exceeded");
            return read;
        }
    }
}
