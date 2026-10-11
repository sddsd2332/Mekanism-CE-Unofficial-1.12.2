package mekanism.qioprocessing.common.network;

import mekanism.api.qio.resource.QIOResourceDescriptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Owner-thread model for persisted maintenance rules.
 */
public final class ProcessorTerminalMaintenanceClientModel {
    public static final class Rule {
        private final UUID id;
        private final QIOResourceDescriptor descriptor;
        private final BigInteger target;
        private final long batch;
        private final boolean enabled;

        private Rule(UUID id, QIOResourceDescriptor descriptor, BigInteger target, long batch, boolean enabled) {
            this.id = id;
            this.descriptor = descriptor;
            this.target = target;
            this.batch = batch;
            this.enabled = enabled;
        }

        public UUID id() {
            return id;
        }

        public QIOResourceDescriptor descriptor() {
            return descriptor;
        }

        public BigInteger target() {
            return target;
        }

        public long batch() {
            return batch;
        }

        public boolean enabled() {
            return enabled;
        }
    }

    private final Thread owner = Thread.currentThread();
    private String status = "CLOSED";
    private long revision = -1;
    private List<Rule> rules = Collections.emptyList();

    public void accept(ProcessorTerminalPacket packet) {
        requireOwner();
        if (packet == null || !packet.valid) return;
        String nextStatus = packet.data().getString("status");
        status = nextStatus == null || nextStatus.isEmpty() ? "INVALID" : nextStatus;
        if (!"OK".equals(status)) return;
        net.minecraft.nbt.NBTTagCompound data = packet.data();
        net.minecraft.nbt.NBTTagList rows = data.getTagList("rules", 10);
        if (rows.tagCount() > 4096) throw new IllegalArgumentException("Too many maintenance rules");
        List<Rule> next = new ArrayList<>();
        for (int index = 0; index < rows.tagCount(); index++) {
            net.minecraft.nbt.NBTTagCompound row = rows.getCompoundTagAt(index);
            BigInteger target;
            try {
                target = new BigInteger(row.getString("target"));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid maintenance target", error);
            }
            if (target.signum() < 0) throw new IllegalArgumentException("Negative maintenance target");
            next.add(new Rule(row.getUniqueId("id"),
                    QIOResourceDescriptor.read(row.getCompoundTag("descriptor")), target,
                    row.getLong("batch"), row.getBoolean("enabled")));
        }
        revision = data.getLong("revision");
        rules = Collections.unmodifiableList(next);
    }

    public String status() {
        return status;
    }

    public long revision() {
        return revision;
    }

    public List<Rule> rules() {
        return rules;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Maintenance model requires its owner thread");
    }
}

