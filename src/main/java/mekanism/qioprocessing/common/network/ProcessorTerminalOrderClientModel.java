package mekanism.qioprocessing.common.network;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Client-thread order status model independent of the eventual GUI skin.
 */
public final class ProcessorTerminalOrderClientModel {
    private final Thread owner = Thread.currentThread();
    private UUID orderId;
    private String status = "NONE";
    private String detail = "";

    public void accept(ProcessorTerminalPacket packet) {
        requireOwner();
        if (packet == null || !packet.valid) return;
        NBTTagCompound data = packet.data();
        status = data.getString("status");
        detail = data.getString("detail");
        if (data.hasUniqueId("order")) orderId = data.getUniqueId("order");
        if ("CANCELLED".equals(detail) || "COMPLETED".equals(detail)
                || "FAILED".equals(detail) || "CLAIM_REJECTED".equals(detail)
                || "PLANNING_FAILED".equals(detail)) {
            if ("CANCELLED".equals(detail) || "COMPLETED".equals(detail)
                    || "FAILED".equals(detail) || "CLAIM_REJECTED".equals(detail)
                    || "PLANNING_FAILED".equals(detail)) orderId = null;
        }
    }

    public UUID orderId() {
        return orderId;
    }

    public String status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Order model requires its owner thread");
    }
}

