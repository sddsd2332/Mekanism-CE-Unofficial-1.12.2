package mekanism.common.multiblock.persistence;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** A single gzip member, including its trailer. Extra members and compressed garbage are errors. */
final class StrictGzipInputStream extends InputStream {
    private final PushbackInputStream input;
    private final Inflater inflater = new Inflater(true);
    private final CRC32 crc = new CRC32();
    private final byte[] compressed = new byte[8192];
    private final byte[] single = new byte[1];
    private final long expandedLimit;
    private long expanded;
    private int lastInputLength;
    private boolean finished;
    private boolean closed;

    StrictGzipInputStream(InputStream input, long expandedLimit) throws IOException {
        this.input = new PushbackInputStream(input, compressed.length);
        this.expandedLimit = expandedLimit;
        try {
            readHeader();
        } catch (IOException error) {
            inflater.end();
            throw error;
        }
    }

    private void readHeader() throws IOException {
        CRC32 headerCrc = new CRC32();
        int[] header = new int[10];
        for (int i = 0; i < header.length; i++) {
            header[i] = requiredByte();
            headerCrc.update(header[i]);
        }
        if (header[0] != 0x1f || header[1] != 0x8b || header[2] != 8 || (header[3] & 0xe0) != 0) {
            throw new IOException("Invalid gzip header");
        }
        int flags = header[3];
        if ((flags & 4) != 0) {
            int low = requiredByte();
            int high = requiredByte();
            headerCrc.update(low);
            headerCrc.update(high);
            for (int i = 0, length = low | high << 8; i < length; i++) headerCrc.update(requiredByte());
        }
        for (int flag : new int[]{8, 16}) {
            if ((flags & flag) != 0) {
                int length = 0;
                int value;
                do {
                    if (++length > 65536) throw new IOException("Excessive gzip header string");
                    value = requiredByte();
                    headerCrc.update(value);
                } while (value != 0);
            }
        }
        if ((flags & 2) != 0) {
            int expected = requiredByte() | requiredByte() << 8;
            if (expected != (headerCrc.getValue() & 0xffff)) throw new IOException("Invalid gzip header CRC");
        }
    }

    @Override
    public int read() throws IOException {
        return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (closed) throw new IOException("Gzip stream is closed");
        if (offset < 0 || length < 0 || offset > bytes.length - length) throw new IndexOutOfBoundsException();
        if (length == 0) return 0;
        if (finished) return -1;
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Gzip read interrupted");
                // One byte beyond the budget is enough to detect overflow, without a large allocation.
                int allowed = (int) Math.min(length, expandedLimit - expanded + 1);
                int count = inflater.inflate(bytes, offset, allowed);
                if (count > 0) {
                    expanded += count;
                    if (expanded > expandedLimit) throw new IOException("Gzip expanded-byte budget exceeded");
                    crc.update(bytes, offset, count);
                    return count;
                }
                if (inflater.finished()) {
                    int remaining = inflater.getRemaining();
                    if (remaining > 0) input.unread(compressed, lastInputLength - remaining, remaining);
                    if (readLittleInt() != crc.getValue()) throw new IOException("Invalid gzip payload CRC");
                    if (readLittleInt() != (expanded & 0xffffffffL)) throw new IOException("Invalid gzip expanded length");
                    if (input.read() != -1) throw new IOException("Trailing compressed data after gzip member");
                    finished = true;
                    return -1;
                }
                if (inflater.needsDictionary()) throw new IOException("Unexpected gzip dictionary");
                if (!inflater.needsInput()) throw new IOException("Gzip inflater made no progress");
                lastInputLength = input.read(compressed);
                if (lastInputLength < 0) throw new EOFException("Truncated gzip payload");
                inflater.setInput(compressed, 0, lastInputLength);
            }
        } catch (DataFormatException error) {
            throw new IOException("Invalid gzip deflate payload", error);
        }
    }

    private int requiredByte() throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException("Truncated gzip header/trailer");
        return value;
    }

    private long readLittleInt() throws IOException {
        return (long) requiredByte() | (long) requiredByte() << 8 |
              (long) requiredByte() << 16 | (long) requiredByte() << 24;
    }

    long expandedBytes() {
        return expanded;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            inflater.end();
            input.close();
        }
    }
}
