package mekanism.qioprocessing.common.network;

import mekanism.qioprocessing.common.processor.QIOCraftingProcessorSnapshot;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Client-thread read-only page model for the future Processor Monitor GUI.
 */
public final class ProcessorTerminalMonitorClientModel {
    private final Thread owner = Thread.currentThread();
    private String status = "CLOSED";
    private long nextCursor = -1;
    private List<QIOCraftingProcessorSnapshot> snapshots = Collections.emptyList();

    public void accept(ProcessorTerminalPacket packet) {
        accept(packet, false);
    }

    public void accept(ProcessorTerminalPacket packet, boolean append) {
        requireOwner();
        Objects.requireNonNull(packet, "packet");
        if (!packet.valid) return;
        String nextStatus = packet.data().getString("status");
        status = nextStatus == null || nextStatus.isEmpty() ? "INVALID" : nextStatus;
        if (!"OK".equals(status)) return;
        NBTTagList stored = packet.data().getTagList("snapshots", 10);
        List<QIOCraftingProcessorSnapshot> next = new ArrayList<>();
        if (append) next.addAll(snapshots);
        for (int index = 0; index < stored.tagCount(); index++) {
            next.add(QIOCraftingProcessorSnapshot.read(stored.getCompoundTagAt(index)));
        }
        nextCursor = packet.data().getLong("nextCursor");
        snapshots = Collections.unmodifiableList(next);
    }

    public String status() {
        return status;
    }

    public long nextCursor() {
        return nextCursor;
    }

    public List<QIOCraftingProcessorSnapshot> snapshots() {
        return snapshots;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Monitor model requires its owner thread");
    }
}

