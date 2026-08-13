package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.client.render.bloom.BloomRenderSPS;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.sps.SPSCache;
import mekanism.common.content.sps.SPSUpdateProtocol;
import mekanism.common.content.sps.SynchronizedSPSData;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.particle.SPSOrbitEffect;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

public class TileEntitySPSCasing extends TileEntityMultiblock<SynchronizedSPSData> {

    private static final double RENDER_EFFECT_EXPANSION = 1D;
    private final SoundEvent soundEvent = new SoundEvent(new ResourceLocation(Mekanism.MODID, "tile.machine.sps"));

    @SideOnly(Side.CLIENT)
    private ISound activeSound;
    private int playSoundCooldown;
    private boolean clientSoundActive;
    private boolean lastServerSoundActive;

    public final Queue<SPSOrbitEffect> orbitEffects = new ArrayDeque<>();

    public TileEntitySPSCasing() {
        this("SpsCasing");
    }

    protected TileEntitySPSCasing(String name) {
        super(name);
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderSPS(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderSPS", e);
                }
            }
        }
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        updateSound();
        if (structure == null || !clientHasStructure || !isRendering || !MekanismConfig.current().client.machineEffects.val() || structure.lastProcessed <= 0) {
            orbitEffects.clear();
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!player.isSneaking() && structure != null) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(Mekanism.instance, 78, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure == null) {
            syncSoundState();
            return;
        }
        if (structure.sanitizeStoredGases()) {
            markNoUpdateSync();
        }
        if (isRendering) {
            boolean needsUpdate = structure.needsRenderUpdate();
            double prevEnergy = structure.lastReceivedEnergy;
            double prevProcessed = structure.lastProcessed;
            structure.tick(world);
            boolean coilsDirty = structure.pullCoilLevelsDirty();
            if (structure.needsRenderUpdate() || needsUpdate || prevEnergy != structure.lastReceivedEnergy || prevProcessed != structure.lastProcessed || coilsDirty) {
                sendPacketToRenderer();
            }
            structure.syncPrevTanks();
        }
        syncSoundState();
    }

    private boolean isSoundActive() {
        return structure != null && structure.isFormed() && structure.lastProcessed > 0 && structure.shouldPlaySoundAt(getPos());
    }

    private void syncSoundState() {
        boolean soundActive = isSoundActive();
        if (soundActive != lastServerSoundActive) {
            lastServerSoundActive = soundActive;
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateSound() {
        if (!MekanismConfig.current().client.enableMachineSounds.val()) {
            if (activeSound != null) {
                stopSound();
            }
            return;
        }
        if (clientHasStructure && clientSoundActive && !isInvalid()) {
            if (--playSoundCooldown > 0) {
                return;
            }
            if (activeSound == null || !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(activeSound)) {
                activeSound = SoundHandler.startTileSound(soundEvent.getSoundName(), 1.0F, getPos());
                playSoundCooldown = 20;
            }
        } else if (activeSound != null) {
            stopSound();
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopSound() {
        SoundHandler.stopTileSound(getPos());
        activeSound = null;
        playSoundCooldown = 0;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (isRemote()) {
            clientSoundActive = false;
            stopSound();
        }
    }

    @Override
    public void onChunkUnload() {
        if (isRemote()) {
            clientSoundActive = false;
            stopSound();
        }
        super.onChunkUnload();
    }

    @Override
    protected SynchronizedSPSData getNewStructure() {
        return new SynchronizedSPSData();
    }

    @Override
    public MultiblockCache<SynchronizedSPSData> getNewCache() {
        return new SPSCache();
    }

    @Override
    protected UpdateProtocol<SynchronizedSPSData> getProtocol() {
        return new SPSUpdateProtocol(this);
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB bounds = super.getRenderBoundingBox();
        if (clientHasStructure && isRendering && structure != null && structure.renderLocation != null) {
            return bounds.grow(RENDER_EFFECT_EXPANSION);
        }
        return bounds;
    }

    @Override
    public MultiblockManager<SynchronizedSPSData> getManager() {
        return Mekanism.spsManager;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        if (structure != null) {
            data.add(structure.lastReceivedEnergy);
            data.add(structure.lastProcessed);
            data.add(structure.progress);
            data.add(structure.inputProcessed);
            TileUtils.addTankData(data, structure.inputTank);
            TileUtils.addTankData(data, structure.outputTank);
            data.add(structure.prevCoilLevels.size());
            for (Map.Entry<Coord4D, Integer> entry : structure.prevCoilLevels.entrySet()) {
                Coord4D coilPos = entry.getKey();
                data.add(coilPos.x);
                data.add(coilPos.y);
                data.add(coilPos.z);
                data.add(coilPos.dimensionId);
                data.add(entry.getValue());
            }
        }
        data.add(isSoundActive());
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (clientHasStructure && structure != null) {
                structure.lastReceivedEnergy = dataStream.readDouble();
                structure.lastProcessed = dataStream.readDouble();
                structure.progress = dataStream.readDouble();
                structure.inputProcessed = dataStream.readInt();
                TileUtils.readTankData(dataStream, structure.inputTank);
                TileUtils.readTankData(dataStream, structure.outputTank);
                int coilCount = dataStream.readInt();
                structure.prevCoilLevels.clear();
                for (int i = 0; i < coilCount; i++) {
                    int x = dataStream.readInt();
                    int y = dataStream.readInt();
                    int z = dataStream.readInt();
                    int dim = dataStream.readInt();
                    int level = dataStream.readInt();
                    structure.prevCoilLevels.put(new Coord4D(x, y, z, dim), level);
                }
            }
            clientSoundActive = dataStream.readBoolean();
        }
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }
}
