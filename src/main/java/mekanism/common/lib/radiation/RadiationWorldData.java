package mekanism.common.lib.radiation;

import java.util.*;
import mekanism.api.*;
import mekanism.api.radiation.IRadiationSource;
import mekanism.common.config.MekanismConfig;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.*;

/** World-owned sources, indexed by source chunk; query radius changes require no rebuild. */
public final class RadiationWorldData extends WorldRadiationProvider<RadiationWorldData> {
    @CapabilityInject(RadiationWorldData.class)
    public static Capability<RadiationWorldData> CAPABILITY;
    private Map<Long, Map<BlockPos, RadiationSource>> chunks = new HashMap<>();
    boolean dirty;
    public RadiationWorldData(World world) { super(world); }
    public static void register() { register(RadiationWorldData.class, () -> new RadiationWorldData(null)); }
    @Override protected Capability<RadiationWorldData> capability() { return CAPABILITY; }
    public static RadiationWorldData get(World world) {
        return world == null || world.isRemote || CAPABILITY == null ? null : world.getCapability(CAPABILITY, null);
    }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static long key(BlockPos pos) { return key(pos.getX() >> 4, pos.getZ() >> 4); }
    public boolean hasSources() { return isAvailable() && !chunks.isEmpty(); }
    public void radiate(BlockPos pos, double magnitude) {
        requireAvailable();
        if (!Double.isFinite(magnitude) || magnitude <= 0) throw new IllegalArgumentException("Source magnitude must be finite and positive");
        Map<BlockPos, RadiationSource> row = chunks.computeIfAbsent(key(pos), ignored -> new HashMap<>());
        RadiationSource source = row.get(pos);
        if (source == null) row.put(pos.toImmutable(), new RadiationSource(new Coord4D(pos, world == null ? 0 : world.provider.getDimension()), magnitude));
        else source.radiate(magnitude);
        dirty = true;
    }
    public void remove(BlockPos pos) {
        requireAvailable();
        Map<BlockPos, RadiationSource> row = chunks.get(key(pos));
        if (row != null) { row.remove(pos); if (row.isEmpty()) chunks.remove(key(pos)); dirty = true; }
    }
    public void removeChunk(int x, int z) { requireAvailable(); chunks.remove(key(x, z)); dirty = true; }
    public void clear() { requireAvailable(); chunks.clear(); dirty = true; }
    public List<IRadiationSource> snapshot() {
        requireAvailable();
        List<IRadiationSource> result = new ArrayList<>();
        for (Map<BlockPos, RadiationSource> row : chunks.values()) for (RadiationSource source : row.values())
            result.add(new RadiationSource(source.getPos().clone(), source.getMagnitude()));
        return Collections.unmodifiableList(result);
    }
    public List<IRadiationSource> snapshot(int x, int z) {
        requireAvailable();
        Map<BlockPos, RadiationSource> row = chunks.get(key(x, z));
        if (row == null) return Collections.emptyList();
        List<IRadiationSource> result = new ArrayList<>(row.size());
        for (RadiationSource source : row.values()) result.add(new RadiationSource(source.getPos().clone(), source.getMagnitude()));
        return Collections.unmodifiableList(result);
    }
    public void decay() {
        if (!isAvailable()) return;
        for (Iterator<Map<BlockPos, RadiationSource>> it = chunks.values().iterator(); it.hasNext();) {
            Map<BlockPos, RadiationSource> row = it.next();
            row.values().removeIf(RadiationSource::decay);
            if (row.isEmpty()) it.remove();
            dirty = true;
        }
    }
    public LevelAndMaxMagnitude query(BlockPos pos) {
        if (!isAvailable()) return LevelAndMaxMagnitude.UNAVAILABLE;
        long range = Math.max(0L, (long) MekanismConfig.current().general.radiationChunkCheckRadius.val() * 16);
        long minX = Math.floorDiv((long) pos.getX() - range, 16), maxX = Math.floorDiv((long) pos.getX() + range, 16);
        long minZ = Math.floorDiv((long) pos.getZ() - range, 16), maxZ = Math.floorDiv((long) pos.getZ() + range, 16);
        double[] result = {RadiationManager.BASELINE, RadiationManager.BASELINE};
        if ((maxX - minX + 1) * (double) (maxZ - minZ + 1) <= chunks.size()) {
            for (long x = minX; x <= maxX; x++) for (long z = minZ; z <= maxZ; z++)
                accumulate(chunks.get(key((int) x, (int) z)), pos, range, result);
        } else for (Map<BlockPos, RadiationSource> row : chunks.values()) accumulate(row, pos, range, result);
        return new LevelAndMaxMagnitude(result[0], result[1]);
    }
    private void accumulate(Map<BlockPos, RadiationSource> row, BlockPos pos, long range, double[] result) {
        if (row == null) return;
        for (RadiationSource source : row.values()) {
            Coord4D other = source.getPos();
            long dx = (long) other.x - pos.getX(), dy = (long) other.y - pos.getY(), dz = (long) other.z - pos.getZ();
            if (Math.abs(dx) <= range && Math.abs(dy) <= range && Math.abs(dz) <= range) {
                double distance = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                result[0] = RadiationUtil.addClamped(result[0], source.getMagnitude() / Math.max(1, distance));
                result[1] = Math.max(result[1], source.getMagnitude());
            }
        }
    }
    @Override protected void readValidated(NBTTagList entries) {
        Map<Long, Map<BlockPos, RadiationSource>> decoded = new HashMap<>();
        for (NBTBase base : entries) {
            NBTTagCompound tag = (NBTTagCompound) base;
            if (!tag.hasKey("x", 3) || !tag.hasKey("y", 3) || !tag.hasKey("z", 3) || !tag.hasKey(NBTConstants.RADIATION, 6))
                throw new IllegalArgumentException("Invalid source position or magnitude type");
            double magnitude = tag.getDouble(NBTConstants.RADIATION);
            if (!Double.isFinite(magnitude) || magnitude <= 0) throw new IllegalArgumentException("Invalid source magnitude");
            BlockPos pos = new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
            Map<BlockPos, RadiationSource> row = decoded.computeIfAbsent(key(pos), ignored -> new HashMap<>());
            if (row.put(pos, new RadiationSource(new Coord4D(pos, world == null ? 0 : world.provider.getDimension()), magnitude)) != null)
                throw new IllegalArgumentException("Duplicate source position");
        }
        chunks = decoded;
        dirty = true;
    }
    @Override protected NBTTagList writeEntries() {
        NBTTagList list = new NBTTagList();
        for (Map<BlockPos, RadiationSource> row : chunks.values()) for (RadiationSource source : row.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            source.write(tag);
            tag.removeTag("dimensionId");
            list.appendTag(tag);
        }
        return list;
    }
}
