package mekanism.qioprocessing.common.network;

import mekanism.api.qio.resource.QIOResourceDescriptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Owner-thread model for the maintenance terminal's exact QIO resource directory.
 */
public final class ProcessorTerminalResourceClientModel {
    public static final class Resource {
        private final QIOResourceDescriptor descriptor;
        private final BigInteger available;
        private final UUID resource;

        private Resource(UUID resource, QIOResourceDescriptor descriptor, BigInteger available) {
            this.resource = resource;
            this.descriptor = descriptor;
            this.available = available;
        }

        public UUID resource() {
            return resource;
        }

        public QIOResourceDescriptor descriptor() {
            return descriptor;
        }

        public BigInteger available() {
            return available;
        }
    }

    private final Thread owner = Thread.currentThread();
    private String status = "CLOSED";
    private long nextCursor = -1;
    private long revision = -1;
    private List<Resource> resources = Collections.emptyList();

    public void accept(ProcessorTerminalPacket packet, boolean append) {
        requireOwner();
        if (packet == null || !packet.valid) return;
        String nextStatus = packet.data().getString("status");
        status = nextStatus == null || nextStatus.isEmpty() ? "INVALID" : nextStatus;
        if (!"OK".equals(status)) return;
        net.minecraft.nbt.NBTTagList rows = packet.data().getTagList("resources", 10);
        List<Resource> next = new ArrayList<>();
        if (append) next.addAll(resources);
        if (rows.tagCount() > 32) throw new IllegalArgumentException("Too many resource rows");
        for (int index = 0; index < rows.tagCount(); index++) {
            net.minecraft.nbt.NBTTagCompound row = rows.getCompoundTagAt(index);
            UUID resource = row.hasUniqueId("resource") ? row.getUniqueId("resource") : null;
            QIOResourceDescriptor descriptor = QIOResourceDescriptor.read(row.getCompoundTag("descriptor"));
            BigInteger amount;
            try {
                amount = new BigInteger(row.getString("available"));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid resource amount", error);
            }
            if (amount.signum() < 0) throw new IllegalArgumentException("Negative resource amount");
            next.add(new Resource(resource, descriptor, amount));
        }
        revision = packet.data().getLong("revision");
        nextCursor = packet.data().getLong("nextCursor");
        resources = Collections.unmodifiableList(next);
    }

    public String status() {
        return status;
    }

    public long nextCursor() {
        return nextCursor;
    }

    public long revision() {
        return revision;
    }

    public List<Resource> resources() {
        return resources;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Resource model requires its owner thread");
    }
}

