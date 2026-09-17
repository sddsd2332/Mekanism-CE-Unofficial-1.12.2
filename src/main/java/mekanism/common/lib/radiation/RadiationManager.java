package mekanism.common.lib.radiation;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
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
import mekanism.common.network.PacketRadiationData;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

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
    private static final Random RAND = new Random();

    public static final double BASELINE = 0.0000001; // 100 nSv/h
    public static final double MIN_MAGNITUDE = 0.00001; // 10 uSv/h

    private final Map<UUID, PreviousRadiationData> playerExposureMap = new HashMap<>();
    private final Map<UUID, World> playerWorlds = new HashMap<>();
    private final Map<UUID, LevelAndMaxMagnitude> playerEnvironmentalExposureMap = new HashMap<>();
    private boolean decayThisTick;

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void attachWorld(net.minecraftforge.event.AttachCapabilitiesEvent<World> event) {
        World world = event.getObject();
        if (!world.isRemote) {
            event.addCapability(Mekanism.rl("radiation_data"), new RadiationWorldData(world));
            event.addCapability(Mekanism.rl("meltdown_data"), new MeltdownWorldData(world));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void unloadWorld(net.minecraftforge.event.world.WorldEvent.Unload event) {
        List<UUID> departed = new ArrayList<>();
        playerWorlds.forEach((id, world) -> { if (world == event.getWorld()) departed.add(id); });
        departed.forEach(this::resetPlayer);
    }

    public boolean isAvailable(World world) {
        RadiationWorldData data = RadiationWorldData.get(world);
        return data != null && data.isAvailable();
    }

    public boolean canCreateMeltdown(World world) {
        MeltdownWorldData data = MeltdownWorldData.get(world);
        return data != null && data.isAvailable() && (!isRadiationEnabled() || isAvailable(world));
    }

    /** Legacy dimension-only adapter: never loads a dimension. */
    public World loadedWorld(int dimension) {
        for (World world : loadedWorlds()) if (world != null && world.provider.getDimension() == dimension) return world;
        return null;
    }

    private World[] loadedWorlds() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null || server.worlds == null ? new World[0] : server.worlds;
    }

    // client fields
    private RadiationScale clientRadiationScale = RadiationScale.NONE;
    private double clientEnvironmentalRadiation = BASELINE;
    private double clientMaxMagnitude = BASELINE;

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

    @Override
    public DamageSource getRadiationDamageSource() {
        return MekanismDamageSource.RADIATION;
    }

    @Override public double getRadiationLevel(Entity entity) { return getRadiationLevel(entity.world, entity.getPosition()); }
    @Override public double getRadiationLevel(World world, BlockPos pos) { return getRadiationLevelAndMaxMagnitude(world, pos).getLevel(); }
    @Override public double getRadiationLevel(Coord4D coord) { return getRadiationLevel(loadedWorld(coord.dimensionId), coord.getPos()); }

    @Override public Table<Chunk3D, Coord4D, IRadiationSource> getRadiationSources() {
        Table<Chunk3D, Coord4D, IRadiationSource> snapshot = HashBasedTable.create();
        for (World world : loadedWorlds()) for (IRadiationSource source : getRadiationSources(world))
            snapshot.put(new Chunk3D(source.getPos()), source.getPos(), source);
        return Tables.unmodifiableTable(snapshot);
    }
    @Override public List<IRadiationSource> getRadiationSources(World world) {
        RadiationWorldData data = RadiationWorldData.get(world);
        if (data == null) throw new IllegalStateException("Radiation world attachment missing");
        return data.snapshot();
    }
    @Override public List<IRadiationSource> getRadiationSources(World world, int x, int z) {
        RadiationWorldData data = RadiationWorldData.get(world);
        if (data == null) throw new IllegalStateException("Radiation world attachment missing");
        return data.snapshot(x, z);
    }
    @Override public void removeRadiationSources(World world, int x, int z) {
        RadiationWorldData data = RadiationWorldData.get(world);
        if (data == null) throw new IllegalStateException("Radiation world attachment missing");
        data.removeChunk(x, z);
    }
    @Override public void removeRadiationSources(Chunk3D chunk) { removeRadiationSources(loadedWorld(chunk.dimensionId), chunk.x, chunk.z); }
    @Override public void removeRadiationSource(World world, BlockPos pos) {
        RadiationWorldData data = RadiationWorldData.get(world);
        if (data == null) throw new IllegalStateException("Radiation world attachment missing");
        data.remove(pos);
    }
    @Override public void removeRadiationSource(Coord4D coord) { removeRadiationSource(loadedWorld(coord.dimensionId), coord.getPos()); }
    public LevelAndMaxMagnitude getRadiationLevelAndMaxMagnitude(Entity entity) { return getRadiationLevelAndMaxMagnitude(entity.world, entity.getPosition()); }
    public LevelAndMaxMagnitude getRadiationLevelAndMaxMagnitude(World world, BlockPos pos) {
        RadiationWorldData data = RadiationWorldData.get(world);
        return data == null ? LevelAndMaxMagnitude.UNAVAILABLE : data.query(pos);
    }
    @Override public boolean radiate(World world, BlockPos pos, double magnitude) {
        if (!isRadiationEnabled() || !isAvailable(world) || !Double.isFinite(magnitude) || magnitude <= 0) return false;
        RadiationWorldData.get(world).radiate(pos, magnitude);
        return true;
    }
    @Override public void radiate(Coord4D coord, double magnitude) { radiate(loadedWorld(coord.dimensionId), coord.getPos(), magnitude); }

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
        return dumpRadiation(loadedWorld(coord.dimensionId), coord.getPos(), stack);
    }

    @Override public boolean dumpRadiation(World world, BlockPos pos, GasStack stack) {
        if (stack == null || stack.getGas() == null || !stack.getGas().isRadiation()) return false;
        double radioactivity = stack.getGas().getRadioactivity();
        if (!Double.isFinite(radioactivity) || radioactivity <= 0 || stack.amount <= 0) return false;
        double amount = RadiationUtil.sanitizeMagnitude(radioactivity * stack.amount);
        return radiate(world, pos, amount);
    }

    public void createMeltdown(World world, BlockPos minPos, BlockPos maxPos, double magnitude, double chance, UUID multiblockID) {
        float radius = MekanismConfig.current().generators == null ? 8F : MekanismConfig.current().generators.fissionMeltdownRadius.val();
        createMeltdown(world, minPos, maxPos, magnitude, chance, radius, multiblockID);
    }
    public void createMeltdown(World world, BlockPos minPos, BlockPos maxPos, double magnitude, double chance, float radius, UUID multiblockID) {
        MeltdownWorldData data = MeltdownWorldData.get(world);
        if (data == null) throw new IllegalStateException("Meltdown world attachment missing");
        data.add(new Meltdown(minPos, maxPos, magnitude, chance, radius, multiblockID));
    }
    public void clearSources() { clearSources(loadedWorlds()); }
    public void clearSources(World[] worlds) {
        // Validate all targets before changing any of them.
        for (World world : worlds) if (world != null && !isAvailable(world)) throw new IllegalStateException("Radiation world attachment unavailable");
        for (World world : worlds) if (world != null) RadiationWorldData.get(world).clear();
    }

    public void setClientEnvironmentalRadiation(double radiation) {
        setClientEnvironmentalRadiation(radiation, radiation);
    }

    public void setClientEnvironmentalRadiation(double radiation, double maxMagnitude) {
        clientEnvironmentalRadiation = Double.isNaN(radiation) ? Double.NaN : RadiationUtil.sanitizeAtLeastBaseline(radiation);
        clientMaxMagnitude = Double.isNaN(maxMagnitude) ? Double.NaN : RadiationUtil.sanitizeAtLeastBaseline(maxMagnitude);
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
            boolean hasEnvironmentalSources = !isAvailable(entity.world) || RadiationWorldData.get(entity.world).hasSources();
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
        if (environmentChanged(player.getUniqueID(), player.world, radiation, force)) {
            Mekanism.packetHandler.sendTo(PacketRadiationData.createEnvironmental(radiation.getLevel(), radiation.getMaxMagnitude()), player);
        }
    }

    boolean environmentChanged(UUID uuid, World world, LevelAndMaxMagnitude radiation, boolean force) {
        LevelAndMaxMagnitude old = playerEnvironmentalExposureMap.get(uuid);
        boolean changed = force || playerWorlds.get(uuid) != world || old == null || old.isAvailable() != radiation.isAvailable();
        if (!changed && radiation.isAvailable()) {
            changed = PreviousRadiationData.compareTo(PreviousRadiationData.of(old.getLevel()), radiation.getLevel()) != null ||
                PreviousRadiationData.compareTo(PreviousRadiationData.of(old.getMaxMagnitude()), radiation.getMaxMagnitude()) != null;
        }
        if (changed) {
            playerWorlds.put(uuid, world);
            playerEnvironmentalExposureMap.put(uuid, radiation);
        }
        return changed;
    }

    public void tickServerWorld(World world) {
        if (world.isRemote) return;
        MeltdownWorldData meltdowns = MeltdownWorldData.get(world);
        if (meltdowns != null) meltdowns.tick();
        RadiationWorldData data = RadiationWorldData.get(world);
        if (isRadiationEnabled() && decayThisTick && data != null) data.decay();
        if (world.playerEntities != null) for (EntityPlayer player : world.playerEntities) {
            if (player instanceof EntityPlayerMP && (!isRadiationEnabled() || data == null || !data.isAvailable() || data.dirty ||
                playerWorlds.get(player.getUniqueID()) != world)) syncEnvironmentalRadiation((EntityPlayerMP) player,
                isRadiationEnabled() ? getRadiationLevelAndMaxMagnitude(player) : LevelAndMaxMagnitude.BASELINE, false);
        }
        if (data != null) data.dirty = false;
    }

    /** Called exactly once at server START, before any world ticks. */
    public void tickServer() { decayThisTick = isRadiationEnabled() && RAND.nextInt(20) == 0; }

    public void reset() {
        playerEnvironmentalExposureMap.clear();
        playerExposureMap.clear();
        playerWorlds.clear();
        decayThisTick = false;
    }

    public void resetClient() {
        clientRadiationScale = RadiationScale.NONE;
        clientEnvironmentalRadiation = BASELINE;
        clientMaxMagnitude = BASELINE;
    }

    public void resetPlayer(UUID uuid) {
        playerWorlds.remove(uuid);
        playerEnvironmentalExposureMap.remove(uuid);
        playerExposureMap.remove(uuid);
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

}
