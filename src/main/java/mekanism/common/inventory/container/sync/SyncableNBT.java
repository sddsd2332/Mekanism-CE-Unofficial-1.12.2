package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.NBTPropertyData;
import mekanism.common.network.to_client.container.property.PropertyData;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Synchronizes one bounded compound value through the normal container property channel. */
public final class SyncableNBT implements ISyncableData {

    private final Supplier<NBTTagCompound> getter;
    private final Consumer<NBTTagCompound> setter;
    private NBTTagCompound lastKnownValue = new NBTTagCompound();

    private SyncableNBT(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> setter) {
        this.getter = Objects.requireNonNull(getter, "NBT getter cannot be null");
        this.setter = Objects.requireNonNull(setter, "NBT setter cannot be null");
    }

    public static SyncableNBT create(@Nonnull Supplier<NBTTagCompound> getter,
          @Nonnull Consumer<NBTTagCompound> setter) {
        return new SyncableNBT(getter, setter);
    }

    @Nonnull
    public NBTTagCompound get() {
        NBTTagCompound value = getter.get();
        return value == null ? new NBTTagCompound() : value.copy();
    }

    public void set(@Nonnull NBTTagCompound value) {
        setter.accept(Objects.requireNonNull(value, "Synced NBT cannot be null").copy());
    }

    @Override
    public DirtyType isDirty() {
        NBTTagCompound value = get();
        if (value.equals(lastKnownValue)) {
            return DirtyType.CLEAN;
        }
        lastKnownValue = value;
        return DirtyType.DIRTY;
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new NBTPropertyData(property, get());
    }
}
