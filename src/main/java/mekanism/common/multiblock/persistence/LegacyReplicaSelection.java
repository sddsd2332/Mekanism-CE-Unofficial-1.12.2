package mekanism.common.multiblock.persistence;

import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Chooses one loaded replica of one UUID. It never adds replicas or claims global freshness. */
public final class LegacyReplicaSelection {
    private LegacyReplicaSelection() {}

    public static Replica select(Collection<Replica> loaded, long worldTime, Replica runtimeAuthority) throws IOException {
        if (runtimeAuthority != null) return runtimeAuthority;
        if (loaded.isEmpty()) throw new IOException("Legacy cache has no loaded replica");
        long latest = Long.MIN_VALUE;
        List<Replica> newest = new ArrayList<>();
        List<Replica> unknown = new ArrayList<>();
        for (Replica candidate : loaded) {
            long timestamp = candidate.timestamp;
            if (timestamp < 0 || timestamp > worldTime) {
                unknown.add(candidate);
            } else if (timestamp >= latest) {
                if (timestamp > latest) {
                    latest = timestamp;
                    newest.clear();
                }
                newest.add(candidate);
            }
        }
        Replica selected = newest.isEmpty() ? unknown.get(0) : newest.get(0);
        for (Replica candidate : newest) requireEqual(selected, candidate, "Same-timestamp legacy replicas disagree");
        // An undated copy could be newer than any dated copy. Only equal content resolves that
        // ambiguity without scanning unloaded chunks or silently throwing away an inventory.
        for (Replica candidate : unknown) requireEqual(selected, candidate, "Undated legacy replica disagrees with selected inventory");
        return selected;
    }

    private static void requireEqual(Replica first, Replica second, String reason) throws IOException {
        if (first != second && !first.data.equals(second.data)) throw new IOException(reason);
    }

    public static final class Replica {
        public final long timestamp;
        private final NBTTagCompound data;

        public Replica(long timestamp, NBTTagCompound snapshot) {
            this.timestamp = timestamp;
            data = snapshot.copy();
        }

        public NBTTagCompound snapshot() { return data.copy(); }
    }
}
