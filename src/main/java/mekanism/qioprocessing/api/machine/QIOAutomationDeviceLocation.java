package mekanism.qioprocessing.api.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Stable world location used in device identity diagnostics and duplicate UUID auditing. */
/**
 * QIO 处理模块中的 QIOAutomationDeviceLocation 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationDeviceLocation implements Comparable<QIOAutomationDeviceLocation> {

    private final int dimension;
    private final BlockPos position;

    public QIOAutomationDeviceLocation(int dimension, @Nonnull BlockPos position) {
        this.dimension = dimension;
        this.position = Objects.requireNonNull(position, "Device position cannot be null");
    }

    @Nonnull
    public static QIOAutomationDeviceLocation of(@Nonnull World world, @Nonnull BlockPos position) {
        return new QIOAutomationDeviceLocation(world.provider.getDimension(), position);
    }

    public int dimension() {
        return dimension;
    }

    @Nonnull
    public BlockPos position() {
        return position;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("dimension", dimension);
        data.setLong("position", position.toLong());
        return data;
    }

    @Nonnull
    public static QIOAutomationDeviceLocation read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Device location data cannot be null");
        return new QIOAutomationDeviceLocation(data.getInteger("dimension"), BlockPos.fromLong(data.getLong("position")));
    }

    @Override
    public int compareTo(QIOAutomationDeviceLocation other) {
        int dimensionCompare = Integer.compare(dimension, other.dimension);
        return dimensionCompare == 0 ? Long.compare(position.toLong(), other.position.toLong()) : dimensionCompare;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof QIOAutomationDeviceLocation other && dimension == other.dimension &&
              position.equals(other.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, position);
    }

    @Override
    public String toString() {
        return dimension + "@" + position.getX() + "," + position.getY() + "," + position.getZ();
    }
}
