package mekanism.common.lib.radiation;

import java.util.*;
import mekanism.api.NBTConstants;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.*;

/** Separate world attachment: existing meltdowns advance even with radiation disabled. */
public final class MeltdownWorldData extends WorldRadiationProvider<MeltdownWorldData> {
    @CapabilityInject(MeltdownWorldData.class)
    public static Capability<MeltdownWorldData> CAPABILITY;
    private List<Meltdown> meltdowns = new ArrayList<>();
    public MeltdownWorldData(World world) { super(world); }
    public static void register() { register(MeltdownWorldData.class, () -> new MeltdownWorldData(null)); }
    @Override protected Capability<MeltdownWorldData> capability() { return CAPABILITY; }
    public static MeltdownWorldData get(World world) {
        return world == null || world.isRemote || CAPABILITY == null ? null : world.getCapability(CAPABILITY, null);
    }
    public void add(Meltdown meltdown) {
        requireAvailable();
        NBTTagCompound tag = new NBTTagCompound();
        meltdown.write(tag);
        validate(tag);
        meltdowns.add(meltdown);
    }
    public void tick() { if (isAvailable()) meltdowns.removeIf(meltdown -> meltdown.update(world)); }
    @Override protected void readValidated(NBTTagList entries) {
        List<Meltdown> decoded = new ArrayList<>();
        for (NBTBase base : entries) {
            NBTTagCompound tag = (NBTTagCompound) base;
            validate(tag);
            decoded.add(Meltdown.load(tag));
        }
        meltdowns = decoded;
    }
    private static void validate(NBTTagCompound tag) {
            for (String bound : new String[]{NBTConstants.MIN, NBTConstants.MAX}) {
                if (!tag.hasKey(bound, 10)) throw new IllegalArgumentException("Missing meltdown bounds");
                NBTTagCompound pos = tag.getCompoundTag(bound);
                for (String axis : new String[]{"X", "Y", "Z"}) if (!pos.hasKey(axis, 3)) throw new IllegalArgumentException("Invalid bounds");
            }
            for (String axis : new String[]{"X", "Y", "Z"}) {
                long min = tag.getCompoundTag(NBTConstants.MIN).getInteger(axis), max = tag.getCompoundTag(NBTConstants.MAX).getInteger(axis);
                if (min > max || max - min >= Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid bounds range");
            }
            for (String field : new String[]{NBTConstants.MAGNITUDE, NBTConstants.CHANCE, NBTConstants.RADIUS})
                if (!tag.hasKey(field, 99) || !Double.isFinite(tag.getDouble(field))) throw new IllegalArgumentException("Invalid meltdown number");
            if (tag.getDouble(NBTConstants.MAGNITUDE) < 0 || tag.getDouble(NBTConstants.CHANCE) < 0 || tag.getDouble(NBTConstants.CHANCE) > 1 ||
                tag.getFloat(NBTConstants.RADIUS) < 1 || tag.getFloat(NBTConstants.RADIUS) > 500 || !tag.hasUniqueId(NBTConstants.INVENTORY_ID) ||
                !tag.hasKey(NBTConstants.AGE, 3) || tag.getInteger(NBTConstants.AGE) < 0 || tag.getInteger(NBTConstants.AGE) > 100)
                throw new IllegalArgumentException("Invalid meltdown state");
    }
    @Override protected NBTTagList writeEntries() {
        NBTTagList list = new NBTTagList();
        for (Meltdown meltdown : meltdowns) { NBTTagCompound tag = new NBTTagCompound(); meltdown.write(tag); list.appendTag(tag); }
        return list;
    }
}
