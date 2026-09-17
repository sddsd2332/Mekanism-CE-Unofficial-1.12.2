package mekanism.common.lib.radiation;

import mekanism.common.Mekanism;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.*;

/** Versioned world attachment. Invalid payloads are retained verbatim until repaired offline. */
public abstract class WorldRadiationProvider<T> implements ICapabilitySerializable<NBTBase> {
    protected final World world;
    private NBTBase rejected;
    protected WorldRadiationProvider(World world) { this.world = world; }
    protected abstract Capability<T> capability();
    protected abstract void readValidated(NBTTagList entries);
    protected abstract NBTTagList writeEntries();
    public final boolean isAvailable() { return rejected == null; }
    public final void requireAvailable() {
        if (!isAvailable()) throw new IllegalStateException("Radiation world attachment unavailable: " + identity());
    }
    private String identity() {
        return world == null ? "unbound" : "dimension " + world.provider.getDimension() + " world@" + Integer.toHexString(System.identityHashCode(world));
    }
    @Override public final NBTBase serializeNBT() {
        if (rejected != null) return rejected.copy();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", 1);
        tag.setTag("entries", writeEntries());
        return tag;
    }
    @Override public final void deserializeNBT(NBTBase payload) {
        try {
            if (!(payload instanceof NBTTagCompound)) throw new IllegalArgumentException("Expected compound payload");
            NBTTagCompound tag = (NBTTagCompound) payload;
            if (!tag.hasKey("version", 3) || tag.getInteger("version") != 1 || !tag.hasKey("entries", 9))
                throw new IllegalArgumentException("Unsupported version or missing entries");
            NBTTagList list = (NBTTagList) tag.getTag("entries");
            if (list.tagCount() > 0 && list.getTagType() != 10) throw new IllegalArgumentException("Expected compound entries");
            readValidated(list);
            rejected = null;
        } catch (RuntimeException error) {
            boolean first = rejected == null;
            rejected = payload.copy();
            if (first && Mekanism.logger != null) Mekanism.logger.error("Preserving invalid {} for {}: {}", getClass().getSimpleName(), identity(), error.toString());
        }
    }
    @Override public boolean hasCapability(Capability<?> cap, EnumFacing side) { return cap != null && cap == capability(); }
    @SuppressWarnings("unchecked")
    @Override public <V> V getCapability(Capability<V> cap, EnumFacing side) { return hasCapability(cap, side) ? (V) this : null; }
    protected static <V extends WorldRadiationProvider<?>> void register(Class<V> type, java.util.concurrent.Callable<V> factory) {
        CapabilityManager.INSTANCE.register(type, new Capability.IStorage<V>() {
            @Override public NBTBase writeNBT(Capability<V> cap, V value, EnumFacing side) { return value.serializeNBT(); }
            @Override public void readNBT(Capability<V> cap, V value, EnumFacing side, NBTBase nbt) {
                value.deserializeNBT(nbt);
            }
        }, factory);
    }
}
