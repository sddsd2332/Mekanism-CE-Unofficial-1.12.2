package mekanism.qioprocessing.api.machine;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Immutable contents snapshot used to detect changes outside an active machine lease. */
/**
 * QIO 处理模块中的 MachinePortBaseline 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class MachinePortBaseline implements Comparable<MachinePortBaseline> {

    private static final String PORT_ID = "portId";
    private static final String PORT_GROUP_ID = "portGroupId";
    private static final String KIND = "kind";
    private static final String CONTENTS = "contents";
    private static final String CONTENTS_LIST = "contentsList";

    private final String portId;
    private final String portGroupId;
    private final MachineResourceKind kind;
    @Nullable
    private final MachineResourceStack contents;
    private final List<MachineResourceStack> allContents;

    /**
     * 创建一个端口基线。
     *
     * @param portId 端口标识
     * @param portGroupId 端口组标识
     * @param kind 资源种类
     * @param contents 捕获时的资源内容，可为 null
     */
    public MachinePortBaseline(@Nonnull String portId, @Nonnull String portGroupId,
          @Nonnull MachineResourceKind kind, @Nullable MachineResourceStack contents) {
        this(portId, portGroupId, kind, contents,
              contents == null ? Collections.emptyList() : Collections.singletonList(contents));
    }

    /** Creates a baseline with all resource identities held by a grouped port. */
    public MachinePortBaseline(@Nonnull String portId, @Nonnull String portGroupId,
          @Nonnull MachineResourceKind kind, @Nullable MachineResourceStack contents,
          @Nullable Collection<? extends MachineResourceStack> allContents) {
        this.portId = requireId(portId, "Port id");
        this.portGroupId = requireId(portGroupId, "Port group id");
        this.kind = Objects.requireNonNull(kind, "Resource kind cannot be null");
        List<MachineResourceStack> checked = new ArrayList<>();
        if (allContents != null) {
            for (MachineResourceStack current : allContents) {
                if (current == null || current.kind() != kind || !current.portId().equals(portId) ||
                      checked.stream().anyMatch(existing -> existing.sameResource(current))) {
                    throw new IllegalArgumentException("Baseline contents do not match their port identity");
                }
                checked.add(current);
            }
        }
        if (contents != null) {
            if (contents.kind() != kind || !contents.portId().equals(portId)) {
                throw new IllegalArgumentException("Baseline contents do not match their port identity");
            }
            if (checked.isEmpty()) {
                checked.add(contents);
            } else if (!checked.get(0).equals(contents)) {
                throw new IllegalArgumentException("Primary baseline contents do not match grouped contents");
            }
        } else if (!checked.isEmpty()) {
            contents = checked.get(0);
        }
        this.contents = contents;
        this.allContents = Collections.unmodifiableList(checked);
    }

    /** 从 Provider 端口当前内容捕获基线。 */
    @Nonnull
    public static MachinePortBaseline capture(@Nonnull MachinePort port) {
        Objects.requireNonNull(port, "Port cannot be null");
        List<MachineResourceStack> contents = new ArrayList<>();
        for (MachineResourceStack current : port.peekAll()) {
            if (current != null) {
                contents.add(current.portId().equals(port.portId()) ? current : current.withPort(port.portId()));
            }
        }
        return new MachinePortBaseline(port.portId(), port.portGroupId(), port.kind(),
              contents.isEmpty() ? null : contents.get(0), contents);
    }

    /** 返回端口标识。 */
    @Nonnull
    public String portId() {
        return portId;
    }

    /** 返回端口组标识。 */
    @Nonnull
    public String portGroupId() {
        return portGroupId;
    }

    /** 返回资源种类。 */
    @Nonnull
    public MachineResourceKind kind() {
        return kind;
    }

    /** 返回捕获的内容；空端口时返回 null。 */
    @Nullable
    public MachineResourceStack contents() {
        return contents;
    }

    /** Returns every resource identity captured from the port. */
    @Nonnull
    public List<MachineResourceStack> allContents() {
        return allContents;
    }

    /** Returns the captured amount for one resource identity, or zero when absent. */
    public long amountOf(@Nonnull MachineResourceStack resource) {
        Objects.requireNonNull(resource, "Resource cannot be null");
        long amount = 0;
        for (MachineResourceStack current : allContents) {
            if (current.sameResource(resource)) {
                if (amount > Long.MAX_VALUE - current.amount()) {
                    throw new IllegalStateException("Machine baseline amount overflow");
                }
                amount += current.amount();
            }
        }
        return amount;
    }

    /** 判断端口身份及当前内容是否与基线完全一致。 */
    public boolean matches(@Nonnull MachinePort port) {
        Objects.requireNonNull(port, "Port cannot be null");
        if (!portId.equals(port.portId()) || !portGroupId.equals(port.portGroupId()) || kind != port.kind()) {
            return false;
        }
        return sameContents(allContents, port.peekAll(), portId);
    }

    /** 判断端口是否处于按指定数量抽取后的精确状态。 */
    public boolean matchesAfterExtraction(@Nonnull MachinePort port,
          @Nonnull MachineResourceStack extraction) {
        Objects.requireNonNull(port, "Port cannot be null");
        Objects.requireNonNull(extraction, "Extraction cannot be null");
        if (!portId.equals(port.portId()) || !portGroupId.equals(port.portGroupId()) ||
            kind != port.kind() || extraction.kind() != kind ||
            !portId.equals(extraction.portId()) || extraction.amount() > amountOf(extraction)) {
            return false;
        }
        List<MachineResourceStack> expected = adjusted(extraction, -extraction.amount());
        return sameContents(expected, port.peekAll(), portId);
    }

    /** 判断端口是否处于按指定数量插入后的精确状态。 */
    public boolean matchesAfterInsertion(@Nonnull MachinePort port,
          @Nonnull MachineResourceStack insertion) {
        Objects.requireNonNull(port, "Port cannot be null");
        Objects.requireNonNull(insertion, "Insertion cannot be null");
        if (!portId.equals(port.portId()) || !portGroupId.equals(port.portGroupId()) ||
              kind != port.kind() || insertion.kind() != kind ||
              !portId.equals(insertion.portId())) {
            return false;
        }
        long previousAmount = amountOf(insertion);
        if (previousAmount > Long.MAX_VALUE - insertion.amount()) {
            return false;
        }
        List<MachineResourceStack> expected = adjusted(insertion, insertion.amount());
        return sameContents(expected, port.peekAll(), portId);
    }

    /** 将端口身份和内容基线写入 NBT。 */
    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(PORT_ID, portId);
        data.setString(PORT_GROUP_ID, portGroupId);
        data.setString(KIND, kind.name());
        if (contents != null) {
            data.setTag(CONTENTS, contents.write(new NBTTagCompound()));
        }
        if (allContents.size() > 1) {
            NBTTagList stored = new NBTTagList();
            for (MachineResourceStack current : allContents) {
                stored.appendTag(current.write(new NBTTagCompound()));
            }
            data.setTag(CONTENTS_LIST, stored);
        }
        return data;
    }

    /** 从 NBT 读取并验证端口身份与资源种类。 */
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
            List<MachineResourceStack> allContents = new ArrayList<>();
            if (data.hasKey(CONTENTS_LIST, NBT.TAG_LIST)) {
                NBTTagList stored = data.getTagList(CONTENTS_LIST, NBT.TAG_COMPOUND);
                for (int index = 0; index < stored.tagCount(); index++) {
                    MachineResourceStack current = MachineResourceStack.read(stored.getCompoundTagAt(index));
                    if (current == null) {
                        throw new IllegalArgumentException("Invalid grouped baseline contents");
                    }
                    allContents.add(current);
                }
            }
            if (allContents.isEmpty() && contents != null) {
                allContents.add(contents);
            }
            return new MachinePortBaseline(data.getString(PORT_ID), data.getString(PORT_GROUP_ID), kind,
                  contents, allContents);
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
              allContents.equals(other.allContents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portId, portGroupId, kind, allContents);
    }

    private List<MachineResourceStack> adjusted(MachineResourceStack resource, long delta) {
        List<MachineResourceStack> adjusted = new ArrayList<>();
        boolean matched = false;
        for (MachineResourceStack current : allContents) {
            if (!current.sameResource(resource)) {
                adjusted.add(current);
                continue;
            }
            matched = true;
            long amount = delta < 0 ? current.amount() - (-delta) : current.amount() + delta;
            if (amount < 0) {
                return Collections.emptyList();
            }
            if (amount > 0) {
                adjusted.add(current.withAmount(amount));
            }
        }
        if (!matched && delta > 0) {
            adjusted.add(resource.withAmount(delta));
        }
        return adjusted;
    }

    private static boolean sameContents(List<MachineResourceStack> expected,
          List<MachineResourceStack> actual, String portId) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (MachineResourceStack desired : expected) {
            long amount = 0;
            for (MachineResourceStack current : actual) {
                if (!current.portId().equals(portId)) {
                    return false;
                }
                if (current.sameResource(desired)) {
                    amount = Math.addExact(amount, current.amount());
                }
            }
            if (amount != desired.amount() || !desired.portId().equals(portId)) {
                return false;
            }
        }
        return true;
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException(name + " must contain 1..128 characters");
        }
        return value;
    }
}
