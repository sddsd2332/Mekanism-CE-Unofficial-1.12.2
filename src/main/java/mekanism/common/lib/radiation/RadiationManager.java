package mekanism.common.lib.radiation;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.radiation.IRadiationManager;
import mekanism.api.radiation.IRadiationSource;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.client.Particle;
import mekanism.common.Mekanism;
import mekanism.common.MekanismDamageSource;
import mekanism.common.MekanismSounds;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.collection.HashList;
import mekanism.common.network.PacketRadiationData;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

/**
 * The RadiationManager handles radiation across all in-game dimensions. Radiation exposure levels are provided in _sieverts, defining a rate of accumulation of
 * equivalent dose. For reference, here are examples of equivalent dose (credit: wikipedia)
 * <ul>
 * <li>100 nSv: baseline dose (banana equivalent dose)</li>
 * <li>250 nSv: airport security screening</li>
 * <li>1 mSv: annual total civilian dose equivalent</li>
 * <li>50 mSv: annual total occupational equivalent dose limit</li>
 * <li>250 mSv: total dose equivalent from 6-month trip to mars</li>
 * <li>1 Sv: maximum allowed dose allowed for NASA astronauts over their careers</li>
 * <li>5 Sv: dose required to (50% chance) kill human if received over 30-day period</li>
 * <li>50 Sv: dose received after spending 10 min next to Chernobyl reactor core directly after meltdown</li>
 * </ul>
 * For defining rate of accumulation, we use _sieverts per hour_ (Sv/h). Here are examples of dose accumulation rates.
 * <ul>
 * <li>100 nSv/h: max recommended human irradiation</li>
 * <li>2.7 uSv/h: irradiation from airline at cruise altitude</li>
 * <li>190 mSv/h: highest reading from fallout of Trinity (Manhattan project test) bomb, _20 miles away_, 3 hours after detonation</li>
 * <li>~500 Sv/h: irradiation inside primary containment vessel of Fukushima power station (at this rate, it takes 30 seconds to accumulate a median lethal dose)</li>
 * </ul>
 *
 * @author aidancbrady
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RadiationManager implements IRadiationManager {

    /**
     * RadiationManager for handling radiation across all dimensions
     */
    public static final RadiationManager INSTANCE = new RadiationManager();
    private static final String DATA_HANDLER_NAME = "radiation_manager";
    private static final Random RAND = new Random();

    public static final double BASELINE = 0.0000001; // 100 nSv/h
    public static final double MIN_MAGNITUDE = 0.00001; // 10 uSv/h

    public static boolean loaded;

    private final Table<Chunk3D, Coord4D, RadiationSource> radiationTable = HashBasedTable.create();
    private final Int2IntMap sourceCountsByDimension = new Int2IntOpenHashMap();
    private final Int2IntMap chunkCountsByDimension = new Int2IntOpenHashMap();
    private final Int2ObjectMap<Set<Chunk3D>> radiationChunksByDimension = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, List<Meltdown>> meltdowns = new Object2ObjectOpenHashMap<>();
    private final Map<UUID, PreviousRadiationData> playerEnvironmentalExposureMap = new Object2ObjectOpenHashMap<>();
    private final Map<UUID, PreviousRadiationData> playerExposureMap = new Object2ObjectOpenHashMap<>();
    private final IntSet dirtyRadiationDimensions = new IntOpenHashSet();
    private boolean allRadiationDimensionsDirty;

    // client fields
    private RadiationScale clientRadiationScale = RadiationScale.NONE;
    private double clientEnvironmentalRadiation = BASELINE;
    private double clientMaxMagnitude = BASELINE;

    /**
     * Note: This can and will be null on the client side
     */
    @Nullable
    private RadiationDataHandler dataHandler;

    @Override
    public boolean isRadiationEnabled() {
        return MekanismConfig.current().general.radiationEnabled.val();
    }

    @Override
    public double baselineRadiation() {
        return BASELINE;
    }

    @Override
    public double minRadiationMagnitude() {
        return MIN_MAGNITUDE;
    }

    private void markDirty() {
        if (dataHandler != null) {
            dataHandler.markDirty();
        }
    }

    @Override
    public DamageSource getRadiationDamageSource() {
        return MekanismDamageSource.RADIATION;
    }

    @Override
    public double getRadiationLevel(Entity entity) {
        return getRadiationLevel(new Coord4D(entity));
    }

    @Override
    public Table<Chunk3D, Coord4D, IRadiationSource> getRadiationSources() {
        return Tables.unmodifiableTable(radiationTable);
    }

    @Override
    public void removeRadiationSources(Chunk3D chunk) {
        Map<Coord4D, RadiationSource> chunkSources = radiationTable.row(chunk);
        if (!chunkSources.isEmpty()) {
            int removed = chunkSources.size();
            chunkSources.clear();
            decrementCount(sourceCountsByDimension, chunk.dimensionId, removed);
            decrementCount(chunkCountsByDimension, chunk.dimensionId, 1);
            removeIndexedChunk(chunk);
            markDirty();
            markRadiationDirty(chunk.dimensionId);
        }
    }

    @Override
    public void removeRadiationSource(Coord4D coord) {
        if (removeSource(coord)) {
            markDirty();
            markRadiationDirty(coord.dimensionId);
        }
    }

    @Override
    public double getRadiationLevel(Coord4D coord) {
        return getRadiationLevelAndMaxMagnitude(coord).getLevel();
    }

    public LevelAndMaxMagnitude getRadiationLevelAndMaxMagnitude(Entity entity) {
        return getRadiationLevelAndMaxMagnitude(new Coord4D(entity));
    }

    LevelAndMaxMagnitude getRadiationLevelAndMaxMagnitude(Coord4D coord) {
        if (!hasRadiationSources(coord.dimensionId)) {
            return LevelAndMaxMagnitude.BASELINE;
        }
        int maxBlockRange = MekanismConfig.current().general.radiationChunkCheckRadius.val() * 16;
        int minChunkX = Math.floorDiv(coord.x - maxBlockRange, 16);
        int minChunkZ = Math.floorDiv(coord.z - maxBlockRange, 16);
        int maxChunkX = Math.floorDiv(coord.x + maxBlockRange, 16);
        int maxChunkZ = Math.floorDiv(coord.z + maxBlockRange, 16);
        long searchChunkCount = ((long) maxChunkX - minChunkX + 1) * ((long) maxChunkZ - minChunkZ + 1);
        RadiationAccumulator accumulator = new RadiationAccumulator();
        if (searchChunkCount <= chunkCountsByDimension.get(coord.dimensionId)) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    for (RadiationSource source : radiationTable.row(new Chunk3D(chunkX, chunkZ, coord.dimensionId)).values()) {
                        accumulator.addIfInRange(source, coord, maxBlockRange);
                    }
                }
            }
        } else {
            Set<Chunk3D> dimensionChunks = radiationChunksByDimension.get(coord.dimensionId);
            if (dimensionChunks != null) {
                for (Chunk3D chunk : dimensionChunks) {
                    for (RadiationSource source : radiationTable.row(chunk).values()) {
                        accumulator.addIfInRange(source, coord, maxBlockRange);
                    }
                }
            }
        }
        return accumulator.toResult();
    }

    @Override
    public void radiate(Coord4D coord, double magnitude) {
        if (!isRadiationEnabled()) {
            return;
        }
        double sanitizedMagnitude = RadiationUtil.sanitizeMagnitude(magnitude);
        if (sanitizedMagnitude == 0) {
            return;
        }
        Chunk3D chunk = new Chunk3D(coord);
        RadiationSource src = radiationTable.get(chunk, coord);
        if (src == null) {
            addSource(new RadiationSource(coord.clone(), sanitizedMagnitude));
        } else {
            src.radiate(sanitizedMagnitude);
        }
        markDirty();
        markRadiationDirty(coord.dimensionId);
    }

    @Override
    public void radiate(EntityLivingBase entity, double magnitude) {
        if (!isRadiationEnabled()) {
            return;
        }
        if (!(entity instanceof EntityPlayer player) || MekanismUtils.isPlayingMode(player)) {
            if (entity.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                IRadiationEntity radiation = entity.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
                if (radiation != null) {
                    double sanitizedMagnitude = RadiationUtil.sanitizeMagnitude(magnitude);
                    radiation.radiate(sanitizedMagnitude * (1 - RadiationUtil.getRadiationResistance(entity)));
                }
            }
        }
    }

    @Deprecated
    @Override
    public void dumpRadiation(Coord4D coord, GasTankInfo[] gasTanks, boolean clearRadioactive) {
        for (GasTankInfo gasTank : gasTanks) {
            if (gasTank instanceof IExtendedGasTank tank) {
                if (tank.getGas() != null && dumpRadiation(coord, tank.getGas()) && clearRadioactive) {
                    tank.setEmpty();
                }
            } else if (gasTank instanceof GasTank tank) {
                if (tank.getGas() != null && dumpRadiation(coord, tank.getGas()) && clearRadioactive) {
                    tank.setGas(null);
                }
            }
        }
    }

    @Override
    public boolean dumpRadiation(Coord4D coord, GasStack stack) {
        // Match high-version semantics: don't report success when radiation is disabled,
        // otherwise clearRadioactive callers would silently void radioactive contents.
        if (isRadiationEnabled() && stack != null && stack.getGas() != null && stack.getGas().isRadiation()) {
            double radioactivity = stack.getGas().getRadioactivity();
            radiate(coord, radioactivity * stack.amount);
            return true;
        }
        return false;
    }

    public void createMeltdown(World world, BlockPos minPos, BlockPos maxPos, double magnitude, double chance, UUID multiblockID) {
        float radius = MekanismConfig.current().generators == null ? 8F : MekanismConfig.current().generators.fissionMeltdownRadius.val();
        createMeltdown(world, minPos, maxPos, magnitude, chance, radius, multiblockID);
    }

    public void createMeltdown(World world, BlockPos minPos, BlockPos maxPos, double magnitude, double chance, float radius, UUID multiblockID) {
        meltdowns.computeIfAbsent(world.provider.getDimension(), id -> new ArrayList<>()).add(new Meltdown(minPos, maxPos, magnitude, chance, radius, multiblockID));
        markDirty();
    }

    public void clearSources() {
        if (!radiationTable.isEmpty()) {
            radiationTable.clear();
            sourceCountsByDimension.clear();
            chunkCountsByDimension.clear();
            radiationChunksByDimension.clear();
            markDirty();
            markAllRadiationDirty();
        }
    }

    public void setClientEnvironmentalRadiation(double radiation) {
        setClientEnvironmentalRadiation(radiation, radiation);
    }

    public void setClientEnvironmentalRadiation(double radiation, double maxMagnitude) {
        clientEnvironmentalRadiation = RadiationUtil.sanitizeAtLeastBaseline(radiation);
        clientMaxMagnitude = RadiationUtil.sanitizeAtLeastBaseline(maxMagnitude);
        clientRadiationScale = RadiationScale.get(clientEnvironmentalRadiation);
    }

    public double getClientEnvironmentalRadiation() {
        return isRadiationEnabled() ? clientEnvironmentalRadiation : BASELINE;
    }

    public double getClientMaxMagnitude() {
        return isRadiationEnabled() ? clientMaxMagnitude : BASELINE;
    }

    public RadiationScale getClientScale() {
        return isRadiationEnabled() ? clientRadiationScale : RadiationScale.NONE;
    }

    public void tickClient(EntityPlayer player) {
        // perhaps also play Geiger counter sound effect, even when not using item (similar to fallout)
        int maxCount = getClientParticleRandomBound(clientRadiationScale,
              MekanismConfig.current().client.radiationParticleCount.val(), MekanismConfig.current().client.radiationParticleLimit.val());
        if (maxCount > 0 && player.world.rand.nextInt(2) == 0) {
            int count = player.world.rand.nextInt(maxCount);
            int radius = Math.max(0, MekanismConfig.current().client.radiationParticleRadius.val());
            for (int i = 0; i < count; i++) {
                double x = player.posX + player.world.rand.nextDouble() * radius * 2 - radius;
                double y = player.posY + player.world.rand.nextDouble() * radius * 2 - radius;
                double z = player.posZ + player.world.rand.nextDouble() * radius * 2 - radius;
                player.world.spawnParticle(Particle.radiation, x, y, z, 0, 0, 0);
            }
        }
    }

    static int getClientParticleRandomBound(RadiationScale scale, int configuredCount, int particleLimit) {
        if (scale == null || scale == RadiationScale.NONE || configuredCount <= 0 || particleLimit <= 0) {
            return 0;
        }
        long scaledCount = (long) scale.ordinal() * configuredCount;
        return (int) Math.min(scaledCount, particleLimit);
    }

    public void tickServer(EntityPlayerMP player) {
        updateEntityRadiation(player);
    }

    public void updateEntityRadiation(EntityLivingBase entity) {
        if (!isRadiationEnabled()) {
            return;
        }
        if (entity.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
            IRadiationEntity radiationCap = entity.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
            if (radiationCap == null) {
                return;
            }
            boolean hasEnvironmentalSources = hasRadiationSources(entity.world.provider.getDimension());
            boolean isIrradiated = radiationCap.getRadiation() > BASELINE;
            if (!hasEnvironmentalSources && !isIrradiated) {
                if (entity instanceof EntityPlayerMP player) {
                    syncPersonalRadiation(player, radiationCap.getRadiation(), false);
                }
                return;
            }
            if (entity.world.rand.nextInt(20) == 0) {
                LevelAndMaxMagnitude environmental = hasEnvironmentalSources ? getRadiationLevelAndMaxMagnitude(entity) : LevelAndMaxMagnitude.BASELINE;
                double magnitude = environmental.getLevel();
                if (magnitude > BASELINE && (!(entity instanceof EntityPlayer player) || MekanismUtils.isPlayingMode(player))) {
                    radiate(entity, magnitude / 3_600D); // convert to Sv/s
                }
                radiationCap.decay();
                if (entity instanceof EntityPlayerMP mp) {
                    syncEnvironmentalRadiation(mp, environmental, false);
                }
            }
            radiationCap.update(entity);
            if (entity instanceof EntityPlayerMP mp) {
                syncPersonalRadiation(mp, radiationCap.getRadiation(), false);
            }
        }
    }

    public void syncPlayer(EntityPlayerMP player) {
        if (!isRadiationEnabled()) {
            Mekanism.packetHandler.sendTo(PacketRadiationData.createPlayer(BASELINE), player);
            Mekanism.packetHandler.sendTo(PacketRadiationData.createEnvironmental(BASELINE, BASELINE), player);
            return;
        }
        IRadiationEntity radiation = player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
        double personal = radiation == null ? BASELINE : radiation.getRadiation();
        syncPersonalRadiation(player, personal, true);
        syncEnvironmentalRadiation(player, getRadiationLevelAndMaxMagnitude(player), true);
    }

    private void syncPersonalRadiation(EntityPlayerMP player, double radiation, boolean force) {
        UUID uuid = player.getUniqueID();
        PreviousRadiationData relevant = force ? PreviousRadiationData.of(radiation)
                                                : PreviousRadiationData.compareTo(playerExposureMap.get(uuid), radiation);
        if (relevant != null) {
            playerExposureMap.put(uuid, relevant);
            Mekanism.packetHandler.sendTo(PacketRadiationData.createPlayer(radiation), player);
        }
    }

    private void syncEnvironmentalRadiation(EntityPlayerMP player, LevelAndMaxMagnitude radiation, boolean force) {
        UUID uuid = player.getUniqueID();
        PreviousRadiationData relevant = force ? PreviousRadiationData.of(radiation.getLevel())
                                                : PreviousRadiationData.compareTo(playerEnvironmentalExposureMap.get(uuid), radiation.getLevel());
        if (relevant != null) {
            playerEnvironmentalExposureMap.put(uuid, relevant);
            Mekanism.packetHandler.sendTo(PacketRadiationData.createEnvironmental(radiation.getLevel(), radiation.getMaxMagnitude()), player);
        }
    }

    public void tickServerWorld(World world) {
        // terminate early if we're disabled
        if (!isRadiationEnabled()) {
            return;
        }
        if (!loaded) {
            createOrLoad(world);
        }

        // update meltdowns
        List<Meltdown> dimensionMeltdowns = meltdowns.getOrDefault(world.provider.getDimension(), Collections.emptyList());
        if (!dimensionMeltdowns.isEmpty()) {
            dimensionMeltdowns.removeIf(meltdown -> meltdown.update(world));
            //If we have/had any meltdowns mark our data handler as dirty as when a meltdown updates
            // the number of ticks it has been around for will change
            markDirty();
        }
    }

    public void tickServer() {
        if (!isRadiationEnabled()) {
            playerEnvironmentalExposureMap.clear();
            playerExposureMap.clear();
            dirtyRadiationDimensions.clear();
            allRadiationDimensionsDirty = false;
            return;
        }
        if (RAND.nextInt(20) == 0) {
            decaySources();
        }
        flushDirtyRadiationSync();
    }

    void decaySources() {
        if (radiationTable.isEmpty()) {
            return;
        }
        List<Coord4D> removed = new ArrayList<>();
        IntSet changedDimensions = new IntOpenHashSet();
        for (RadiationSource source : radiationTable.values()) {
            changedDimensions.add(source.getPos().dimensionId);
            if (source.decay()) {
                removed.add(source.getPos());
            }
        }
        for (Coord4D coord : removed) {
            removeSource(coord);
        }
        for (int dimension : changedDimensions) {
            markRadiationDirty(dimension);
        }
        markDirty();
    }

    private void flushDirtyRadiationSync() {
        if (!allRadiationDimensionsDirty && dirtyRadiationDimensions.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        boolean allDirty = allRadiationDimensionsDirty;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (allDirty || dirtyRadiationDimensions.contains(player.dimension)) {
                syncEnvironmentalRadiation(player, getRadiationLevelAndMaxMagnitude(player), false);
            }
        }
        allRadiationDimensionsDirty = false;
        dirtyRadiationDimensions.clear();
    }

    /**
     * Note: This should only be called from the server side
     */
    public void createOrLoad(World world) {
        String name = DATA_HANDLER_NAME;
        if (dataHandler == null) {
            dataHandler = (RadiationDataHandler) world.getPerWorldStorage().getOrLoadData(RadiationDataHandler.class, name);
            //Always associate the world with the over world as the frequencies are global
            if (dataHandler == null) {
                dataHandler = new RadiationDataHandler(name);
                dataHandler.setManagerAndSync(this);
                dataHandler.clearCached();
                world.getPerWorldStorage().setData(name, dataHandler);
            } else {
                dataHandler.setManagerAndSync(this);
                dataHandler.clearCached();
            }
        }
        loaded = true;
    }

    public void reset() {
        radiationTable.clear();
        sourceCountsByDimension.clear();
        chunkCountsByDimension.clear();
        radiationChunksByDimension.clear();
        playerEnvironmentalExposureMap.clear();
        playerExposureMap.clear();
        dirtyRadiationDimensions.clear();
        allRadiationDimensionsDirty = false;
        meltdowns.clear();
        dataHandler = null;
        loaded = false;
    }

    public void resetClient() {
        clientRadiationScale = RadiationScale.NONE;
        clientEnvironmentalRadiation = BASELINE;
        clientMaxMagnitude = BASELINE;
    }

    public void resetPlayer(UUID uuid) {
        playerEnvironmentalExposureMap.remove(uuid);
        playerExposureMap.remove(uuid);
    }

    boolean hasRadiationSources(int dimension) {
        return sourceCountsByDimension.get(dimension) > 0;
    }

    int getSourceCount(int dimension) {
        return sourceCountsByDimension.get(dimension);
    }

    int getChunkCount(int dimension) {
        return chunkCountsByDimension.get(dimension);
    }

    private void addSource(RadiationSource source) {
        Coord4D coord = source.getPos();
        if (!(source.getMagnitude() > 0)) {
            return;
        }
        Chunk3D chunk = new Chunk3D(coord);
        RadiationSource existing = radiationTable.get(chunk, coord);
        if (existing != null) {
            existing.radiate(source.getMagnitude());
            return;
        }
        boolean newChunk = radiationTable.row(chunk).isEmpty();
        radiationTable.put(chunk, coord, source);
        sourceCountsByDimension.put(coord.dimensionId, sourceCountsByDimension.get(coord.dimensionId) + 1);
        if (newChunk) {
            chunkCountsByDimension.put(coord.dimensionId, chunkCountsByDimension.get(coord.dimensionId) + 1);
            radiationChunksByDimension.computeIfAbsent(coord.dimensionId, ignored -> new HashSet<>()).add(chunk);
        }
    }

    private boolean removeSource(Coord4D coord) {
        Chunk3D chunk = new Chunk3D(coord);
        RadiationSource removed = radiationTable.remove(chunk, coord);
        if (removed == null) {
            return false;
        }
        decrementCount(sourceCountsByDimension, coord.dimensionId, 1);
        if (radiationTable.row(chunk).isEmpty()) {
            decrementCount(chunkCountsByDimension, coord.dimensionId, 1);
            removeIndexedChunk(chunk);
        }
        return true;
    }

    private void removeIndexedChunk(Chunk3D chunk) {
        Set<Chunk3D> dimensionChunks = radiationChunksByDimension.get(chunk.dimensionId);
        if (dimensionChunks != null) {
            dimensionChunks.remove(chunk);
            if (dimensionChunks.isEmpty()) {
                radiationChunksByDimension.remove(chunk.dimensionId);
            }
        }
    }

    private static void decrementCount(Int2IntMap counts, int dimension, int amount) {
        int remaining = counts.get(dimension) - amount;
        if (remaining > 0) {
            counts.put(dimension, remaining);
        } else {
            counts.remove(dimension);
        }
    }

    private void markRadiationDirty(int dimension) {
        if (!allRadiationDimensionsDirty) {
            dirtyRadiationDimensions.add(dimension);
        }
    }

    private void markAllRadiationDirty() {
        allRadiationDimensionsDirty = true;
        dirtyRadiationDimensions.clear();
    }

    private static boolean isInRange(RadiationSource source, Coord4D coord, int maxBlockRange) {
        Coord4D sourcePos = source.getPos();
        return Math.abs((long) sourcePos.x - coord.x) <= maxBlockRange &&
               Math.abs((long) sourcePos.y - coord.y) <= maxBlockRange &&
               Math.abs((long) sourcePos.z - coord.z) <= maxBlockRange;
    }

    private static class RadiationAccumulator {

        private double level = BASELINE;
        private double maxMagnitude = BASELINE;

        private void addIfInRange(RadiationSource source, Coord4D coord, int maxBlockRange) {
            if (isInRange(source, coord, maxBlockRange)) {
                level = RadiationUtil.addClamped(level, RadiationUtil.computeExposure(source, coord));
                maxMagnitude = Math.max(maxMagnitude, source.getMagnitude());
            }
        }

        private LevelAndMaxMagnitude toResult() {
            return level <= BASELINE && maxMagnitude <= BASELINE ? LevelAndMaxMagnitude.BASELINE
                                                                 : new LevelAndMaxMagnitude(level, maxMagnitude);
        }
    }


    public enum RadiationScale {
        NONE,
        LOW,
        MEDIUM,
        ELEVATED,
        HIGH,
        EXTREME;

        /**
         * Get the corresponding RadiationScale from an equivalent dose rate (Sv/h)
         */
        public static RadiationScale get(double magnitude) {
            magnitude = RadiationUtil.sanitizeMagnitude(magnitude);
            if (magnitude < 0.00001) { // 10 uSv/h
                return NONE;
            } else if (magnitude < 0.001) { // 1 mSv/h
                return LOW;
            } else if (magnitude < 0.1) { // 100 mSv/h
                return MEDIUM;
            } else if (magnitude < 10) { // 100 Sv/h
                return ELEVATED;
            } else if (magnitude < 100) {
                return HIGH;
            }
            return EXTREME;
        }

        /**
         * For both Sv and Sv/h.
         */
        public static EnumColor getSeverityColor(double magnitude) {
            magnitude = RadiationUtil.sanitizeMagnitude(magnitude);
            if (magnitude <= BASELINE) {
                return EnumColor.BRIGHT_GREEN;
            } else if (magnitude < 0.00001) { // 10 uSv/h
                return EnumColor.GREY;
            } else if (magnitude < 0.001) { // 1 mSv/h
                return EnumColor.YELLOW;
            } else if (magnitude < 0.1) { // 100 mSv/h
                return EnumColor.ORANGE;
            } else if (magnitude < 10) { // 100 Sv/h
                return EnumColor.RED;
            }
            return EnumColor.DARK_RED;
        }

        private static final double LOG_BASELINE = Math.log10(MIN_MAGNITUDE);
        private static final double LOG_MAX = Math.log10(100); // 100 Sv
        private static final double SCALE = LOG_MAX - LOG_BASELINE;

        /**
         * Gets the severity of a dose (between 0 and 1) from a provided dosage in Sv.
         */
        public static double getScaledDoseSeverity(double magnitude) {
            magnitude = RadiationUtil.sanitizeMagnitude(magnitude);
            if (magnitude < MIN_MAGNITUDE) {
                return 0;
            }
            return Math.min(1, Math.max(0, (-LOG_BASELINE + Math.log10(magnitude)) / SCALE));
        }

        public SoundEvent getSoundEvent() {
            return switch (this) {
                case LOW -> MekanismSounds.GEIGER_SLOW;
                case MEDIUM -> MekanismSounds.GEIGER_MEDIUM;
                case ELEVATED, HIGH -> MekanismSounds.GEIGER_ELEVATED;
                case EXTREME -> MekanismSounds.GEIGER_FAST;
                default -> null;
            };
        }
    }

    public static class RadiationDataHandler extends WorldSavedData {

        private Map<Integer, List<Meltdown>> savedMeltdowns = Collections.emptyMap();
        public List<RadiationSource> loadedSources = Collections.emptyList();
        public RadiationManager manager;

        public RadiationDataHandler(String name) {
            super(name);
        }

        public void setManagerAndSync(RadiationManager m) {
            manager = m;
            // Keep saved state while disabled so toggling radiation does not erase a world's sources or meltdowns.
            for (RadiationSource source : loadedSources) {
                manager.addSource(source);
            }
            for (Map.Entry<Integer, List<Meltdown>> entry : savedMeltdowns.entrySet()) {
                List<Meltdown> meltdowns = entry.getValue();
                manager.meltdowns.computeIfAbsent(entry.getKey(), id -> new ArrayList<>(meltdowns.size())).addAll(meltdowns);
            }
        }

        public void clearCached() {
            //Clear cached sources and meltdowns after loading them to not keep pointers in our data handler
            // that are referencing objects that eventually will be removed
            loadedSources = Collections.emptyList();
            savedMeltdowns = Collections.emptyMap();
        }

        @Override
        public void readFromNBT(@Nonnull NBTTagCompound nbtTags) {
            if (nbtTags.hasKey(NBTConstants.RADIATION_LIST, Constants.NBT.TAG_LIST)) {
                NBTTagList list = nbtTags.getTagList(NBTConstants.RADIATION_LIST, Constants.NBT.TAG_COMPOUND);
                loadedSources = new HashList<>(list.tagCount());
                for (NBTBase nbt : list) {
                    RadiationSource source = RadiationSource.load((NBTTagCompound) nbt);
                    if (source != null) {
                        loadedSources.add(source);
                    }
                }
            } else {
                loadedSources = Collections.emptyList();
            }
            if (nbtTags.hasKey(NBTConstants.MELTDOWNS, Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound meltdownNBT = nbtTags.getCompoundTag(NBTConstants.MELTDOWNS);
                savedMeltdowns = new HashMap<>(meltdownNBT.getSize());
                for (String dim : meltdownNBT.getKeySet()) {
                    if (!dim.isEmpty()) {
                        try {
                            int dimension = Integer.parseInt(dim);
                            NBTTagList meltdowns = meltdownNBT.getTagList(dim, Constants.NBT.TAG_COMPOUND);
                            List<Meltdown> dimensionMeltdowns = new ArrayList<>(meltdowns.tagCount());
                            for (int i = 0; i < meltdowns.tagCount(); i++) {
                                dimensionMeltdowns.add(Meltdown.load(meltdowns.getCompoundTagAt(i)));
                            }
                            savedMeltdowns.put(dimension, dimensionMeltdowns);
                        } catch (NumberFormatException ignored) {
                            Mekanism.logger.warn("Ignoring radiation meltdown data with invalid dimension id: {}", dim);
                        }
                    }
                }
            } else {
                savedMeltdowns = Collections.emptyMap();
            }
        }

        @Nonnull
        @Override
        public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbtTags) {
            if (!manager.radiationTable.isEmpty()) {
                NBTTagList list = new NBTTagList();
                for (RadiationSource source : manager.radiationTable.values()) {
                    NBTTagCompound compound = new NBTTagCompound();
                    source.write(compound);
                    list.appendTag(compound);
                }
                nbtTags.setTag(NBTConstants.RADIATION_LIST, list);
            }
            if (!manager.meltdowns.isEmpty()) {
                NBTTagCompound meltdownNBT = new NBTTagCompound();
                for (Map.Entry<Integer, List<Meltdown>> entry : manager.meltdowns.entrySet()) {
                    List<Meltdown> meltdowns = entry.getValue();
                    if (!meltdowns.isEmpty()) {
                        NBTTagList list = new NBTTagList();
                        for (Meltdown meltdown : meltdowns) {
                            NBTTagCompound compound = new NBTTagCompound();
                            meltdown.write(compound);
                            list.appendTag(compound);
                        }
                        meltdownNBT.setTag(entry.getKey().toString(), list);
                    }
                }
                if (!meltdownNBT.isEmpty()) {
                    nbtTags.setTag(NBTConstants.MELTDOWNS, meltdownNBT);
                }
            }
            return nbtTags;
        }
    }
}
