package mekanism.common.lib.radiation;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
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
import mekanism.api.radiation.capability.IRadiationShielding;
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
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

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
    private static final IntSupplier MAX_RANGE = () -> MekanismConfig.current().general.radiationChunkCheckRadius.val() * 16;
    private static final Random RAND = new Random();

    public static final double BASELINE = 0.0000001; // 100 nSv/h
    public static final double MIN_MAGNITUDE = 0.00001; // 10 uSv/h

    public static boolean loaded;

    private final Table<Chunk3D, Coord4D, RadiationSource> radiationTable = HashBasedTable.create();
    private final Map<Integer, List<Meltdown>> meltdowns = new Object2ObjectOpenHashMap<>();

    private final Object2DoubleMap<UUID> playerExposureMap = new Object2DoubleOpenHashMap<>();

    // client fields
    private RadiationScale clientRadiationScale = RadiationScale.NONE;
    private double clientEnvironmentalRadiation = BASELINE;

    /**
     * Note: This can and will be null on the client side
     */
    @Nullable
    private RadiationDataHandler dataHandler;

    @Override
    public boolean isRadiationEnabled() {
        return MekanismConfig.current().general.radiationEnabled.val();
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
            chunkSources.clear();
            markDirty();
        }
    }

    @Override
    public void removeRadiationSource(Coord4D coord) {
        Chunk3D chunk = new Chunk3D(coord);
        if (radiationTable.contains(chunk, coord)) {
            radiationTable.remove(chunk, coord);
            markDirty();
        }
    }

    @Override
    public double getRadiationLevel(Coord4D coord) {
        Set<Chunk3D> checkChunks = new Chunk3D(coord).expand(MekanismConfig.current().general.radiationChunkCheckRadius.val());
        double level = BASELINE;
        for (Chunk3D chunk : checkChunks) {
            Map<Coord4D, RadiationSource> row = radiationTable.row(chunk);
            if (row != null) {
                for (Map.Entry<Coord4D, RadiationSource> entry : row.entrySet()) {
                    // we only compute exposure when within the MAX_RANGE bounds
                    if (entry.getKey().distanceTo(coord) <= MAX_RANGE.getAsInt()) {
                        level += computeExposure(coord, entry.getValue());
                    }
                }
            }
        }
        return level;
    }

    @Override
    public void radiate(Coord4D coord, double magnitude) {
        if (!isRadiationEnabled()) {
            return;
        }
        Map<Coord4D, RadiationSource> radiationSourceMap = radiationTable.row(new Chunk3D(coord));
        RadiationSource src = radiationSourceMap.get(coord);
        if (src == null) {
            radiationSourceMap.put(coord, new RadiationSource(coord, magnitude));
        } else {
            src.radiate(magnitude);
        }
        markDirty();
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
                    radiation.radiate(magnitude * (1 - Math.min(1, getRadiationResistance(entity))));
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
        meltdowns.computeIfAbsent(world.provider.getDimension(), id -> new ArrayList<>()).add(new Meltdown(minPos, maxPos, magnitude, chance, multiblockID));
        markDirty();
    }

    public void clearSources() {
        if (!radiationTable.isEmpty()) {
            radiationTable.clear();
            markDirty();
        }
    }

    private double computeExposure(Coord4D coord, RadiationSource source) {
        return source.getMagnitude() / Math.max(1, coord.distanceToSquared(source.getPos()));
    }


    EntityEquipmentSlot[] ARMOR_SLOTS = new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};

    private double getRadiationResistance(EntityLivingBase entity) {
        double resistance = 0;
        for (EntityEquipmentSlot type : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemStackFromSlot(type);
            if (!stack.isEmpty()) {
                IRadiationShielding shielding = stack.getCapability(Capabilities.RADIATION_SHIELDING_CAPABILITY, null);
                if (shielding != null) {
                    resistance += shielding.getRadiationShielding();
                }
            }
        }
        return resistance;
    }

    public void setClientEnvironmentalRadiation(double radiation) {
        clientEnvironmentalRadiation = radiation;
        clientRadiationScale = RadiationScale.get(clientEnvironmentalRadiation);
    }

    public double getClientEnvironmentalRadiation() {
        return clientEnvironmentalRadiation;
    }

    public RadiationScale getClientScale() {
        return clientRadiationScale;
    }

    public void tickClient(EntityPlayer player) {
        // perhaps also play Geiger counter sound effect, even when not using item (similar to fallout)
        if (clientRadiationScale != RadiationScale.NONE && player.world.rand.nextInt(2) == 0) {
            int count = player.world.rand.nextInt(clientRadiationScale.ordinal() * MekanismConfig.current().client.radiationParticleCount.val());
            int radius = MekanismConfig.current().client.radiationParticleRadius.val();
            for (int i = 0; i < count; i++) {
                double x = player.posX + player.world.rand.nextDouble() * radius * 2 - radius;
                double y = player.posY + player.world.rand.nextDouble() * radius * 2 - radius;
                double z = player.posZ + player.world.rand.nextDouble() * radius * 2 - radius;
                player.world.spawnParticle(Particle.radiation, x, y, z, 0, 0, 0);
            }
        }
    }

    public void tickServer(EntityPlayerMP player) {
        updateEntityRadiation(player);
    }

    public void updateEntityRadiation(EntityLivingBase entity) {
        // terminate early if we're disabled
        if (!isRadiationEnabled()) {
            return;
        }
        if (entity.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
            IRadiationEntity radiationCap = entity.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
            if (radiationCap == null) {
                return;
            }
            if (entity.world.rand.nextInt(20) == 0) {
                double magnitude = getRadiationLevel(new Coord4D(entity));
                if (magnitude > BASELINE && (!(entity instanceof EntityPlayer player) || MekanismUtils.isPlayingMode(player))) {
                    // apply radiation to the player
                    radiate(entity, magnitude / 3_600D); // convert to Sv/s
                }
                radiationCap.decay();
                if (entity instanceof EntityPlayerMP mp) {
                    double current = playerExposureMap.getOrDefault(entity.getUniqueID(), BASELINE);
                    //If the last sync radiation value is different in magnitude by over the baseline, sync
                    if (Math.abs(magnitude - current) >= BASELINE) {
                        playerExposureMap.put(entity.getUniqueID(), magnitude);
                        Mekanism.packetHandler.sendTo(PacketRadiationData.createEnvironmental(magnitude), mp);
                    }
                }
            }
            radiationCap.update(entity);
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
        // terminate early if we're disabled
        if (!isRadiationEnabled()) {
            return;
        }
        // each tick, there's a 1/20 chance we'll decay radiation sources (averages to 1 decay operation per second)
        if (RAND.nextInt(20) == 0) {
            Collection<RadiationSource> sources = radiationTable.values();
            if (!sources.isEmpty()) {
                // remove if source gets too low
                sources.removeIf(RadiationSource::decay);
                //Mark dirty regardless if we have any sources as magnitude changes or radiation sources change
                markDirty();
            }
        }
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
        //Clear the table directly instead of via the method, so it doesn't mark it as dirty
        radiationTable.clear();
        playerExposureMap.clear();
        meltdowns.clear();
        dataHandler = null;
        loaded = false;
    }

    public void resetClient() {
        clientRadiationScale = RadiationScale.NONE;
        clientEnvironmentalRadiation = BASELINE;
    }

    public void resetPlayer(UUID uuid) {
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
            // don't sync the manager if radiation has been disabled
            if (MekanismAPI.getRadiationManager().isRadiationEnabled()) {
                for (RadiationSource source : loadedSources) {
                    manager.radiationTable.put(new Chunk3D(source.getPos()), source.getPos(), source);
                }
                for (Map.Entry<Integer, List<Meltdown>> entry : savedMeltdowns.entrySet()) {
                    List<Meltdown> meltdowns = entry.getValue();
                    manager.meltdowns.computeIfAbsent(entry.getKey(), id -> new ArrayList<>(meltdowns.size())).addAll(meltdowns);
                }
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
                    loadedSources.add(RadiationSource.load((NBTTagCompound) nbt));
                }
            } else {
                loadedSources = Collections.emptyList();
            }
            if (nbtTags.hasKey(NBTConstants.MELTDOWNS, Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound meltdownNBT = nbtTags.getCompoundTag(NBTConstants.MELTDOWNS);
                savedMeltdowns = new HashMap<>(meltdownNBT.getSize());
                for (String dim : meltdownNBT.getKeySet()) {
                    if (!dim.isEmpty()) {
                        int dimension = Integer.parseInt(dim);
                        //It should be a valid dimension, but validate it just in case
                        NBTTagList meltdowns = meltdownNBT.getTagList(dim, Constants.NBT.TAG_COMPOUND);
                        List<Meltdown> dimensionMeltdowns = new ArrayList<>(meltdowns.tagCount());
                        for (int i = 0; i < meltdowns.tagCount(); i++) {
                            dimensionMeltdowns.add(Meltdown.load(meltdowns.getCompoundTagAt(i)));
                        }
                        savedMeltdowns.put(dimension, dimensionMeltdowns);
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
