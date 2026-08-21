package mekanism.qioprocessing.common.terminal;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Opaque continuation cursor bound to one terminal session and source revision. */
/**
 * QIO 处理模块中的 QIOPageCursor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPageCursor {

    private static final int WIRE_VERSION = 1;

    private final UUID sessionNonce;
    private final long sourceRevision;
    private final int offset;

    public QIOPageCursor(@Nonnull UUID sessionNonce, long sourceRevision, int offset) {
        this.sessionNonce = Objects.requireNonNull(sessionNonce, "sessionNonce");
        if (sourceRevision < 0 || offset < 0) {
            throw new IllegalArgumentException("QIO page cursor values cannot be negative");
        }
        this.sourceRevision = sourceRevision;
        this.offset = offset;
    }

    @Nonnull
    public UUID getSessionNonce() {
        return sessionNonce;
    }

    public long getSourceRevision() {
        return sourceRevision;
    }

    public int getOffset() {
        return offset;
    }

    public void write(@Nonnull ByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeByte(WIRE_VERSION);
        buffer.writeLong(sessionNonce.getMostSignificantBits());
        buffer.writeLong(sessionNonce.getLeastSignificantBits());
        buffer.writeLong(sourceRevision);
        buffer.writeInt(offset);
    }

    @Nonnull
    public static QIOPageCursor read(@Nonnull ByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        try {
            if (buffer.readUnsignedByte() != WIRE_VERSION) {
                throw new IllegalArgumentException("Unsupported QIO page cursor version");
            }
            UUID nonce = new UUID(buffer.readLong(), buffer.readLong());
            return new QIOPageCursor(nonce, buffer.readLong(), buffer.readInt());
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Truncated QIO page cursor", e);
        }
    }
}
