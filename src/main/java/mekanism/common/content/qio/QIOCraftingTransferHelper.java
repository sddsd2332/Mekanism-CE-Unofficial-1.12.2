package mekanism.common.content.qio;

import javax.annotation.Nullable;
import java.util.UUID;

/** Small immutable-on-the-wire source descriptors used by crafting transfer. */
public final class QIOCraftingTransferHelper {

    private QIOCraftingTransferHelper() {
    }

    /**
     * Describes either a player/container slot or a resource UUID in QIO.
     * Amounts are always pieces for crafting recipes; the server resolves the
     * UUID and verifies that it is an ITEM resource before using it.
     */
    public static class SingularHashedItemSource {

        @Nullable
        private final UUID qioSource;
        private final byte slot;
        private int used;

        public SingularHashedItemSource(@Nullable UUID qioSource, int used) {
            if (qioSource == null || used <= 0) {
                throw new IllegalArgumentException("QIO source requires a UUID and a positive amount");
            }
            this.qioSource = qioSource;
            this.slot = -1;
            this.used = used;
        }

        public SingularHashedItemSource(byte slot, int used) {
            if (slot < 0 || used <= 0) {
                throw new IllegalArgumentException("Slot source requires a non-negative slot and a positive amount");
            }
            this.qioSource = null;
            this.slot = slot;
            this.used = used;
        }

        @Nullable
        public UUID getQioSource() {
            return qioSource;
        }

        public byte getSlot() {
            return slot;
        }

        public int getUsed() {
            return used;
        }

        public void setUsed(int used) {
            if (used < 0 || used > this.used) {
                throw new IllegalArgumentException("Used must not exceed the simulated amount");
            }
            this.used = used;
        }
    }

    /** Generic request shape for future fluid/gas recipe adapters. */
    public static final class ResourceRequest {
        private final UUID resource;
        private final QIOResourceKind kind;
        private final long amount;

        public ResourceRequest(UUID resource, QIOResourceKind kind, long amount) {
            this.resource = resource;
            this.kind = kind;
            this.amount = amount;
        }

        public UUID getResource() {
            return resource;
        }

        public QIOResourceKind getKind() {
            return kind;
        }

        public long getAmount() {
            return amount;
        }
    }
}
