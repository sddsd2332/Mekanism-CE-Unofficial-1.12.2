package mekanism.qioprocessing.api.machine;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/** Immutable contents snapshot used to detect changes outside an active machine lease. */
public final class MachinePortBaseline implements Comparable<MachinePortBaseline> {

    private static final String PORT_ID = "portId";
    private static final String PORT_GROUP_ID = "portGroupId";
    private static final String KIND = "kind";
    private static final String CONTENTS = "contents";

    private final String portId;
    private final String portGroupId;
    private final MachineResourceKind kind;
    @Nullable
    private final MachineResourceStack contents;

    public MachinePortBaseline(@Nonnull String portId, @Nonnull String portGroupId,
          @Nonnull MachineResourceKind kind, @Nullable MachineResourceStack contents) {
        this.portId = requireId(portId, "Port id");
        this.portGroupId = requireId(portGroupId, "Port group id");
        this.kind = Objects.requireNonNull(kind, "Resource kind cannot be null");
        if (contents != null && (contents.kind() != kind || !contents.portId().equals(portId))) {
            throw new IllegalArgumentException("Baseline contents do not match their port identity");
        }
        this.contents = contents;
    }

    @Nonnull
    public static MachinePortBaseline capture(@Nonnull MachinePort port) {
        Objects.requireNonNull(port, "Port cannot be null");
        MachineResourceStack contents = port.peek();
        if (contents != null && !contents.portId().equals(port.portId())) {
            contents = contents.withPort(port.portId());
        }
        return new MachinePortBaseline(port.portId(), port.portGroupId(), port.kind(), contents);
    }

    @Nonnull
    public String portId() {
        return portId;
    }

    @Nonnull
    public String portGroupId() {
        return portGroupId;
    }

    @Nonnull
    public MachineResourceKind kind() {
        return kind;
    }

    @Nullable
    public MachineResourceStack contents() {
        return contents;
    }

    public boolean matches(@Nonnull MachinePort port) {
        Objects.requireNonNull(port, "Port cannot be null");
        if (!portId.equals(port.portId()) || !portGroupId.equals(port.portGroupId()) || kind != port.kind()) {
            return false;
        }
        MachineResourceStack current = port.peek();
        if (contents == null || current == null) {
            return contents == null && current == null;
        }
        return contents.amount() == current.amount() && contents.sameResource(current);
    }

    /** Checks the exact source state expected after a bounded extraction from this baseline. */
    public boolean matchesAfterExtraction(@Nonnull MachinePort port,
          @Nonnull MachineResourceStack extraction) {
        Objects.requireNonNull(port, "Port cannot be null");
        Objects.requireNonNull(extraction, "Extraction cannot be null");
        if (!portId.equals(port.portId()) || !portGroupId.equals(port.portGroupId()) ||
            kind != port.kind() || contents == null || extraction.kind() != kind ||
            !portId.equals(extraction.portId()) || extraction.amount() > contents.amount() ||
            !contents.sameResource(extraction)) {
            return false;
        }
        MachineResourceStack current = port.peek();
        long remaining = contents.amount() - extraction.amount();
        return remaining == 0 ? current == null : current != null && current.amount() == remaining &&
              contents.sameResource(current);
    }

    /** Checks the exact destination state expected after inserting into this baseline. */
    public boolean matchesAfterInsertion(@Nonnull MachinePort port,
          @Nonnull MachineResourceStack insertion) {
        Objects.requireNonNull(port, "Port cannot be null");
        Objects.requireNonNull(insertion, "Insertion cannot be null");
        if (!portId.equals(port.portId()) || !portGroupId.equals(port.portGroupId()) ||
              kind != port.kind() || insertion.kind() != kind ||
              !portId.equals(insertion.portId())) {
            return false;
        }
        MachineResourceStack current = port.peek();
        if (current == null || !current.sameResource(insertion)) {
            return false;
        }
        long previous = 0;
        if (contents != null) {
            if (!contents.sameResource(insertion)) {
                return false;
            }
            previous = contents.amount();
        }
        return previous <= Long.MAX_VALUE - insertion.amount() &&
              current.amount() == previous + insertion.amount();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(PORT_ID, portId);
        data.setString(PORT_GROUP_ID, portGroupId);
        data.setString(KIND, kind.name());
        if (contents != null) {
            data.setTag(CONTENTS, contents.write(new NBTTagCompound()));
        }
        return data;
    }

    @Nonnull
    public static MachinePortBaseline read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Baseline data cannot be null");
        try {
            MachineResourceKind kind = MachineResourceKind.valueOf(data.getString(KIND));
            MachineResourceStack contents = data.hasKey(CONTENTS, NBT.TAG_COMPOUND) ?
                  MachineResourceStack.read(data.getCompoundTag(CONTENTS)) : null;
            if (data.hasKey(CONTENTS, NBT.TAG_COMPOUND) && contents == null) {
                throw new IllegalArgumentException("Invalid baseline contents");
            }
            return new MachinePortBaseline(data.getString(PORT_ID), data.getString(PORT_GROUP_ID), kind, contents);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid machine port baseline", e);
        }
    }

    @Override
    public int compareTo(MachinePortBaseline other) {
        int groupCompare = portGroupId.compareTo(other.portGroupId);
        return groupCompare == 0 ? portId.compareTo(other.portId) : groupCompare;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MachinePortBaseline other)) {
            return false;
        }
        return portId.equals(other.portId) && portGroupId.equals(other.portGroupId) && kind == other.kind &&
              Objects.equals(contents, other.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portId, portGroupId, kind, contents);
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException(name + " must contain 1..128 characters");
        }
        return value;
    }
}
