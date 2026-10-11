package mekanism.api.processing;

import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.*;

/**
 * Immutable contents baseline for one machine port.
 */
public final class MachinePortBaseline {
    public static final int MAX_CONTENTS = 128;

    private final String portId;
    private final String portGroupId;
    private final QIOResourceFamilyMatcher resourceMatcher;
    private final List<MachineResourceStack> contents;

    public MachinePortBaseline(String portId, String portGroupId,
                               QIOResourceFamilyMatcher resourceMatcher, Collection<? extends MachineResourceStack> contents) {
        this.portId = requireText(portId, "portId");
        this.portGroupId = requireText(portGroupId, "portGroupId");
        this.resourceMatcher = Objects.requireNonNull(resourceMatcher, "resourceMatcher");
        Objects.requireNonNull(contents, "contents");
        if (contents.size() > MAX_CONTENTS) throw new IllegalArgumentException("Too many port baseline contents");
        List<MachineResourceStack> copy = new ArrayList<>();
        for (MachineResourceStack value : contents) {
            MachineResourceStack checked = Objects.requireNonNull(value, "baseline content");
            if (!portId.equals(checked.portId()) || !resourceMatcher.accepts(checked.descriptor())) {
                throw new IllegalArgumentException("Port baseline content does not match port identity");
            }
            if (copy.stream().anyMatch(existing -> existing.sameResource(checked))) {
                throw new IllegalArgumentException("Duplicate port baseline resource");
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing(value -> value.descriptor().toString()));
        this.contents = Collections.unmodifiableList(copy);
    }

    public static MachinePortBaseline capture(MachinePort port) {
        Objects.requireNonNull(port, "port");
        List<MachineResourceStack> captured = new ArrayList<>();
        for (MachineResourceStack value : port.peekAll()) {
            if (value == null) continue;
            MachineResourceStack checked = value.portId().equals(port.portId()) ? value : value.withPort(port.portId());
            if (!port.acceptsResource(checked))
                throw new IllegalStateException("Port returned an incompatible resource");
            if (captured.stream().anyMatch(existing -> existing.sameResource(checked))) {
                throw new IllegalStateException("Port returned duplicate resource identities");
            }
            captured.add(checked);
        }
        return new MachinePortBaseline(port.portId(), port.portGroupId(), port.resourceMatcher(), captured);
    }

    public String portId() {
        return portId;
    }

    public String portGroupId() {
        return portGroupId;
    }

    public QIOResourceFamilyMatcher resourceMatcher() {
        return resourceMatcher;
    }

    public List<MachineResourceStack> contents() {
        return contents;
    }

    public long amountOf(MachineResourceStack resource) {
        Objects.requireNonNull(resource, "resource");
        for (MachineResourceStack current : contents) {
            if (current.sameResource(resource)) return current.amount();
        }
        return 0;
    }

    public boolean matches(MachinePort port) {
        return identityMatches(port) && sameContents(contents, port.peekAll(), portId);
    }

    public boolean matchesAfterExtraction(MachinePort port, MachineResourceStack extraction) {
        if (!identityMatches(port) || !accepts(extraction) || !portId.equals(extraction.portId())) return false;
        long available = amountOf(extraction);
        if (available < extraction.amount()) return false;
        return sameContents(adjust(extraction, -extraction.amount()), port.peekAll(), portId);
    }

    public boolean matchesAfterInsertion(MachinePort port, MachineResourceStack insertion) {
        if (!identityMatches(port) || !accepts(insertion) || !portId.equals(insertion.portId())) return false;
        if (amountOf(insertion) > Long.MAX_VALUE - insertion.amount()) return false;
        return sameContents(adjust(insertion, insertion.amount()), port.peekAll(), portId);
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("portId", portId);
        data.setString("portGroupId", portGroupId);
        data.setTag("matcher", resourceMatcher.write());
        NBTTagList stored = new NBTTagList();
        for (MachineResourceStack value : contents) stored.appendTag(value.write(new NBTTagCompound()));
        data.setTag("contents", stored);
        return data;
    }

    public static MachinePortBaseline read(NBTTagCompound data) {
        Objects.requireNonNull(data, "baseline data");
        try {
            NBTTagList stored = data.getTagList("contents", NBT.TAG_COMPOUND);
            if (stored.tagCount() > MAX_CONTENTS) throw new IllegalArgumentException("Too many baseline contents");
            List<MachineResourceStack> contents = new ArrayList<>(stored.tagCount());
            for (int index = 0; index < stored.tagCount(); index++) {
                MachineResourceStack value = MachineResourceStack.read(stored.getCompoundTagAt(index));
                if (value == null) throw new IllegalArgumentException("Invalid baseline content");
                contents.add(value);
            }
            return new MachinePortBaseline(data.getString("portId"), data.getString("portGroupId"),
                    QIOResourceFamilyMatcher.read(data.getCompoundTag("matcher")), contents);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid machine port baseline", error);
        }
    }

    private boolean identityMatches(MachinePort port) {
        return port != null && portId.equals(port.portId()) && portGroupId.equals(port.portGroupId())
                && resourceMatcher.equals(port.resourceMatcher());
    }

    private boolean accepts(MachineResourceStack resource) {
        return resource != null && resourceMatcher.accepts(resource.descriptor());
    }

    private List<MachineResourceStack> adjust(MachineResourceStack resource, long delta) {
        List<MachineResourceStack> adjusted = new ArrayList<>();
        boolean matched = false;
        for (MachineResourceStack current : contents) {
            if (!current.sameResource(resource)) {
                adjusted.add(current);
                continue;
            }
            matched = true;
            long amount = delta < 0 ? current.amount() - (-delta) : current.amount() + delta;
            if (amount < 0) return Collections.emptyList();
            if (amount > 0) adjusted.add(current.withAmount(amount));
        }
        if (!matched && delta > 0) adjusted.add(resource.withPort(portId).withOrder(0));
        return adjusted;
    }

    private static boolean sameContents(List<MachineResourceStack> expected,
                                        List<MachineResourceStack> actual, String portId) {
        List<MachineResourceStack> normalized = new ArrayList<>();
        for (MachineResourceStack value : actual) {
            if (value == null || !portId.equals(value.portId())) return false;
            normalized.add(value);
        }
        if (expected.size() != normalized.size()) return false;
        for (MachineResourceStack desired : expected) {
            long amount = 0;
            for (MachineResourceStack current : normalized) {
                if (current.sameResource(desired)) amount = Math.addExact(amount, current.amount());
            }
            if (amount != desired.amount()) return false;
        }
        return true;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty() || value.length() > 128)
            throw new IllegalArgumentException(name + " has invalid length");
        return value;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof MachinePortBaseline)) return false;
        MachinePortBaseline other = (MachinePortBaseline) value;
        return portId.equals(other.portId) && portGroupId.equals(other.portGroupId)
                && resourceMatcher.equals(other.resourceMatcher) && contents.equals(other.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portId, portGroupId, resourceMatcher, contents);
    }
}

