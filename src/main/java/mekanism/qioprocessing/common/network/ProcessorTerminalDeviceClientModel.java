package mekanism.qioprocessing.common.network;

import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owner-thread model for management-terminal Provider/device pages.
 */
public final class ProcessorTerminalDeviceClientModel {
    public static final class Device {
        private final String source;
        private final String provider;
        private final int routes;
        private final int ports;
        private final int configurationRevision;
        private final List<String> routeIds;
        private final List<String> portIds;

        private Device(String source, String provider, int routes, int ports, int configurationRevision,
                       List<String> routeIds, List<String> portIds) {
            this.source = source;
            this.provider = provider;
            this.routes = routes;
            this.ports = ports;
            this.configurationRevision = configurationRevision;
            this.routeIds = Collections.unmodifiableList(new ArrayList<>(routeIds));
            this.portIds = Collections.unmodifiableList(new ArrayList<>(portIds));
        }

        public String source() {
            return source;
        }

        public String provider() {
            return provider;
        }

        public int routes() {
            return routes;
        }

        public int ports() {
            return ports;
        }

        public int configurationRevision() {
            return configurationRevision;
        }

        public List<String> routeIds() {
            return routeIds;
        }

        public List<String> portIds() {
            return portIds;
        }
    }

    private final Thread owner = Thread.currentThread();
    private String status = "CLOSED";
    private long nextCursor = -1;
    private long revision = -1;
    private List<Device> devices = Collections.emptyList();

    public void accept(ProcessorTerminalPacket packet, boolean append) {
        requireOwner();
        if (packet == null || !packet.valid) return;
        String nextStatus = packet.data().getString("status");
        status = nextStatus == null || nextStatus.isEmpty() ? "INVALID" : nextStatus;
        if (!"OK".equals(status)) return;
        net.minecraft.nbt.NBTTagCompound data = packet.data();
        NBTTagList stored = data.getTagList("devices", 10);
        List<Device> next = new ArrayList<>();
        if (append) next.addAll(devices);
        if (stored.tagCount() > 32) throw new IllegalArgumentException("Too many device rows");
        for (int index = 0; index < stored.tagCount(); index++) {
            net.minecraft.nbt.NBTTagCompound row = stored.getCompoundTagAt(index);
            NBTTagList routeRows = row.getTagList("routeIds", 10);
            NBTTagList portRows = row.getTagList("portIds", 10);
            if (routeRows.tagCount() > 128 || portRows.tagCount() > 64) {
                throw new IllegalArgumentException("Too many device details");
            }
            List<String> routeIds = new ArrayList<>();
            for (int route = 0; route < routeRows.tagCount(); route++) {
                routeIds.add(routeRows.getCompoundTagAt(route).getString("id") + " | "
                        + routeRows.getCompoundTagAt(route).getString("recipe"));
            }
            List<String> portIds = new ArrayList<>();
            for (int port = 0; port < portRows.tagCount(); port++) {
                portIds.add(portRows.getCompoundTagAt(port).getString("id"));
            }
            next.add(new Device(row.getString("source"), row.getString("provider"),
                    row.getInteger("routes"), row.getInteger("ports"),
                    row.getInteger("configurationRevision"), routeIds, portIds));
        }
        revision = data.getLong("revision");
        nextCursor = data.getLong("nextCursor");
        devices = Collections.unmodifiableList(next);
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

    public List<Device> devices() {
        return devices;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Device model requires its owner thread");
    }
}

