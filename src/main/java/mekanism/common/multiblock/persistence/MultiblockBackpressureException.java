package mekanism.common.multiblock.persistence;

import java.io.IOException;

/** The operation was not accepted; the same authority may retry once pending work drains. */
public final class MultiblockBackpressureException extends IOException {
    public MultiblockBackpressureException(String message) { super(message); }
    public MultiblockBackpressureException(String message, Throwable cause) { super(message, cause); }
}
