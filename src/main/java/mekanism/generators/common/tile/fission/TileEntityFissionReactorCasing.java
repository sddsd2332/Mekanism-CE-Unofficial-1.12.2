package mekanism.generators.common.tile.fission;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.IContentsListener;
import mekanism.api.MekanismAPI;
import mekanism.api.TileNetworkList;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.content.fission.FissionReactorCache;
import mekanism.generators.common.content.fission.FissionReactorUpdateProtocol;
import mekanism.generators.common.content.fission.SynchronizedFissionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TileEntityFissionReactorCasing extends TileEntityMultiblock<SynchronizedFissionData> {

    private final SoundEvent soundEvent = new SoundEvent(new ResourceLocation(MekanismGenerators.MODID, "tile.machine.fission_reactor"));
    @SideOnly(Side.CLIENT)
    private ISound activeSound;
    private int playSoundCooldown = 0;

    public TileEntityFissionReactorCasing() {
        this("FissionReactorCasing");
    }

    protected TileEntityFissionReactorCasing(String name) {
        super(name);
        if (getClass() == TileEntityFissionReactorCasing.class) {
            initializeContainerHolders();
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure == null) {
            return;
        }

        if (structure.sanitizeStoredContents()) {
            markNoUpdateSync();
        }

        //The renderer may be in an unloaded chunk while another casing remains loaded. Let any
        //loaded casing own structure-wide processing, while the claim keeps it to once per tick.
        if (tryClaimStructureServerTick()) {
            boolean needsUpdate = structure.needsRenderUpdate();
            structure.tick(world);
            if (structure.shouldMeltdown(world.rand)) {
                triggerMeltdown();
            }
            if (needsUpdate || structure.needsRenderUpdate()) {
                sendPacketToRenderer();
            }
            structure.syncPrev();
            syncCachedDataFromStructure();
            //SynchronizedData has no owning tile listener in the 1.12 multiblock implementation.
            //Ensure runtime heat, fuel, and damage changes cause the current owner chunk to be saved.
            MekanismUtils.saveChunk(this);
        }
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        return null;
    }

    private void triggerMeltdown() {
        if (structure == null || structure.minLocation == null || structure.maxLocation == null) {
            return;
        }
        BlockPos minPos = structure.minLocation.getPos();
        BlockPos maxPos = structure.maxLocation.getPos();
        Coord4D center = new Coord4D((minPos.getX() + maxPos.getX()) / 2D, (minPos.getY() + maxPos.getY()) / 2D, (minPos.getZ() + maxPos.getZ()) / 2D,
                world.provider.getDimension());
        if (MekanismAPI.getRadiationManager().isRadiationEnabled()) {
            double releasedRadiation = structure.collectRadiationForMeltdown();
            if (releasedRadiation > 0) {
                MekanismAPI.getRadiationManager().radiate(center, releasedRadiation);
            }
        }
        double magnitude = structure.getEstimatedMeltdownMagnitude();
        RadiationManager.INSTANCE.createMeltdown(world, minPos, maxPos, magnitude, SynchronizedFissionData.MELTDOWN_EXPLOSION_CHANCE, getMeltdownID());
        structure.onMeltdown();
        sendPacketToRenderer();
        markNoUpdateSync();
    }

    private UUID getMeltdownID() {
        if (structure != null && structure.inventoryID != null) {
            try {
                return UUID.fromString(structure.inventoryID);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.randomUUID();
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        updateSound();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (isRemote()) {
            updateSound();
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateSound() {
        if (!MekanismConfig.current().client.enableMachineSounds.val()) {
            if (activeSound != null) {
                SoundHandler.stopTileSound(getPos());
                activeSound = null;
                playSoundCooldown = 0;
            }
            return;
        }
        boolean burning = clientHasStructure && structure != null && structure.lastBurnRate > 0 && structure.shouldPlaySoundAt(getPos());
        if (burning && !isInvalid()) {
            if (--playSoundCooldown > 0) {
                return;
            }
            if (activeSound == null || !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(activeSound)) {
                activeSound = SoundHandler.startTileSound(soundEvent.getSoundName(), 1.0F, getPos());
                playSoundCooldown = 20;
            }
        } else if (activeSound != null) {
            SoundHandler.stopTileSound(getPos());
            activeSound = null;
            playSoundCooldown = 0;
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!isRemote() && structure != null) {
            if (player.isSneaking()) {
                structure.active = !structure.active && !structure.isForceDisabled();
                String status = structure.active ? LangUtils.localize("gui.on") : LangUtils.localize("gui.off");
                player.sendMessage(new TextComponentString("Fission Reactor: " + status));
                Mekanism.packetHandler.sendUpdatePacket(this);
                markNoUpdateSync();
                return true;
            }
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(MekanismGenerators.instance, 16, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        if (structure != null) {
            structure.sanitizeRuntimeState();
            data.add(structure.fuelAssemblies);
            data.add(structure.surfaceArea);
            data.add(structure.volume);
            data.add(structure.getHeatCapacitor().getHeatCapacity());
            data.add(structure.rateLimit);
            data.add(structure.active);
            data.add(structure.forceDisable);
            data.add(structure.burnRemaining);
            data.add(structure.partialWaste);
            data.add(structure.getHeatCapacitor().getHeat());
            data.add(structure.reactorDamage);
            data.add(structure.lastBoilRate);
            data.add(structure.lastBurnRate);
            data.add(structure.lastEnvironmentLoss);
            TileUtils.addTankData(data, structure.fuelTank);
            TileUtils.addTankData(data, structure.wasteTank);
            TileUtils.addTankData(data, structure.gasCoolantTank);
            TileUtils.addTankData(data, structure.heatedCoolantTank);
            TileUtils.addTankData(data, structure.coolantTank);
            TileUtils.addTankData(data, structure.steamTank);
        }
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (!isRemote()) {
            if (structure != null) {
                int type = dataStream.readInt();
                if (type == 0) {
                    structure.active = !structure.active && !structure.isForceDisabled();
                } else if (type == 1) {
                    structure.setRateLimit(structure.rateLimit + dataStream.readDouble());
                }
                Mekanism.packetHandler.sendUpdatePacket(this);
                markNoUpdateSync();
            }
            return;
        }
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient() && clientHasStructure && structure != null) {
            structure.fuelAssemblies = Math.max(0, dataStream.readInt());
            structure.surfaceArea = Math.max(0, dataStream.readInt());
            structure.volume = Math.max(0, dataStream.readInt());
            structure.getHeatCapacitor().setHeatCapacityFromPacket(dataStream.readDouble());
            structure.rateLimit = dataStream.readDouble();
            structure.active = dataStream.readBoolean();
            structure.forceDisable = dataStream.readBoolean();
            structure.burnRemaining = dataStream.readDouble();
            structure.partialWaste = dataStream.readDouble();
            structure.getHeatCapacitor().setHeat(dataStream.readDouble());
            structure.reactorDamage = dataStream.readDouble();
            structure.lastBoilRate = Math.max(0, dataStream.readLong());
            structure.lastBurnRate = dataStream.readDouble();
            structure.lastEnvironmentLoss = dataStream.readDouble();
            TileUtils.readTankData(dataStream, structure.fuelTank);
            TileUtils.readTankData(dataStream, structure.wasteTank);
            TileUtils.readTankData(dataStream, structure.gasCoolantTank);
            TileUtils.readTankData(dataStream, structure.heatedCoolantTank);
            TileUtils.readTankData(dataStream, structure.coolantTank);
            TileUtils.readTankData(dataStream, structure.steamTank);
            structure.sanitizeRuntimeState();
            structure.updateCapacities();
            structure.syncPrev();
        }
    }

    @Override
    protected SynchronizedFissionData getNewStructure() {
        return new SynchronizedFissionData();
    }

    @Override
    public MultiblockCache<SynchronizedFissionData> getNewCache() {
        return new FissionReactorCache();
    }

    @Override
    protected UpdateProtocol<SynchronizedFissionData> getProtocol() {
        return new FissionReactorUpdateProtocol(this);
    }

    @Override
    public MultiblockManager<SynchronizedFissionData> getManager() {
        return MekanismGenerators.fissionManager;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }
}
