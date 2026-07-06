package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IHasVisualization;
import mekanism.common.base.ISustainedData;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.chunkloading.IChunkLoader;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.component.TileComponentChunkLoader;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Set;

public class TileEntityDimensionalStabilizer extends TileEntityMachine implements IChunkLoader, ISustainedData, IHasVisualization, IComparatorSupport {

    public static final int MAX_LOAD_RADIUS = 2;
    public static final int MAX_LOAD_DIAMETER = 2 * MAX_LOAD_RADIUS + 1;

    public static final int PACKET_TOGGLE_CHUNK = 0;
    public static final int PACKET_ENABLE_RADIUS = 1;
    public static final int PACKET_DISABLE_RADIUS = 2;

    private static final String NBT_CHUNKS_TO_LOAD = "chunksToLoad";
    private static final String SUSTAINED_CHUNKS = "stabilizerChunks";

    private final boolean[][] loadingChunks = new boolean[MAX_LOAD_DIAMETER][MAX_LOAD_DIAMETER];
    private int chunksLoaded = 1;
    private boolean clientRendering;

    public final TileComponentChunkLoader chunkLoaderComponent;
    private MachineEnergyContainer energyContainer;
    private EnergyInventorySlot energySlot;

    public TileEntityDimensionalStabilizer() {
        super("null", BlockStateMachine.MachineType.DIMENSIONAL_STABILIZER, 1);
        removeSupportedUpgrade(Upgrade.SPEED);
        removeSupportedUpgrade(Upgrade.MUFFLING);
        initializeInventorySlots();
        loadingChunks[MAX_LOAD_RADIUS][MAX_LOAD_RADIUS] = true;
        chunkLoaderComponent = new TileComponentChunkLoader(this) {
            @Override
            public boolean canOperate() {
                return MekanismConfig.current().general.allowChunkloading.val() && getActive();
            }
        };
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(energyContainer, this::getWorld, listener, 143, 35), RelativeSide.BACK);
        return builder.build();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = createEnergyContainerHelper();
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this::getEnergy, this::setEnergy, this::getMaxEnergy, this::getEnergyUsage, listener),
              RelativeSide.BACK);
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        double needed = getEnergyUsage();
        if (MekanismConfig.current().general.allowChunkloading.val() && MekanismUtils.canFunction(this)) {
            if (energyContainer.extract(needed, Action.SIMULATE, AutomationType.INTERNAL) == needed) {
                energyContainer.extract(needed, Action.EXECUTE, AutomationType.INTERNAL);
                setActive(true);
            } else {
                setActive(false);
            }
        } else {
            setActive(false);
        }
    }

    public double getEnergyUsage() {
        return energyPerTick * chunksLoaded;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    public int getChunksLoaded() {
        return chunksLoaded;
    }

    public boolean isChunkLoadingAt(int x, int z) {
        return x >= 0 && x < MAX_LOAD_DIAMETER && z >= 0 && z < MAX_LOAD_DIAMETER && loadingChunks[x][z];
    }

    public void toggleChunkLoadingAt(int x, int z) {
        if (x >= 0 && x < MAX_LOAD_DIAMETER && z >= 0 && z < MAX_LOAD_DIAMETER) {
            if (setChunkLoadingAt(x, z, !loadingChunks[x][z])) {
                onConfiguredChunksChanged();
            }
        }
    }

    public void adjustChunkLoadingRadius(int radius, boolean load) {
        if (radius <= 0 || radius > MAX_LOAD_RADIUS) {
            return;
        }
        boolean changed = false;
        for (int x = -radius; x <= radius; x++) {
            boolean skipInner = x > -radius && x < radius;
            int actualX = x + MAX_LOAD_RADIUS;
            for (int z = -radius; z <= radius; z += skipInner ? 2 * radius : 1) {
                changed |= setChunkLoadingAt(actualX, z + MAX_LOAD_RADIUS, load);
            }
        }
        if (changed) {
            onConfiguredChunksChanged();
        }
    }

    private boolean setChunkLoadingAt(int x, int z, boolean load) {
        if (x == MAX_LOAD_RADIUS && z == MAX_LOAD_RADIUS) {
            return false;
        } else if (loadingChunks[x][z] != load) {
            loadingChunks[x][z] = load;
            if (load) {
                chunksLoaded++;
            } else {
                chunksLoaded--;
            }
            return true;
        }
        return false;
    }

    private void onConfiguredChunksChanged() {
        markNoUpdateSync();
        chunkLoaderComponent.refreshChunkSet();
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == PACKET_TOGGLE_CHUNK) {
                toggleChunkLoadingAt(dataStream.readInt(), dataStream.readInt());
            } else if (type == PACKET_ENABLE_RADIUS) {
                adjustChunkLoadingRadius(dataStream.readInt(), true);
            } else if (type == PACKET_DISABLE_RADIUS) {
                adjustChunkLoadingRadius(dataStream.readInt(), false);
            }
            playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player));
            return;
        }
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            int loaded = 0;
            for (int x = 0; x < MAX_LOAD_DIAMETER; x++) {
                for (int z = 0; z < MAX_LOAD_DIAMETER; z++) {
                    loadingChunks[x][z] = dataStream.readBoolean();
                    if (loadingChunks[x][z]) {
                        loaded++;
                    }
                }
            }
            chunksLoaded = loaded;
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        for (int x = 0; x < MAX_LOAD_DIAMETER; x++) {
            for (int z = 0; z < MAX_LOAD_DIAMETER; z++) {
                data.add(loadingChunks[x][z]);
            }
        }
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        deserializeChunks(nbtTags.getByteArray(NBT_CHUNKS_TO_LOAD));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setByteArray(NBT_CHUNKS_TO_LOAD, serializeChunks());
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        NBTTagCompound data = new NBTTagCompound();
        data.setByteArray(NBT_CHUNKS_TO_LOAD, serializeChunks());
        ItemDataUtils.setCompound(itemStack, SUSTAINED_CHUNKS, data);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        deserializeChunks(ItemDataUtils.getCompound(itemStack, SUSTAINED_CHUNKS).getByteArray(NBT_CHUNKS_TO_LOAD));
    }

    private byte[] serializeChunks() {
        byte[] chunksToLoad = new byte[MAX_LOAD_DIAMETER * MAX_LOAD_DIAMETER];
        for (int x = 0; x < MAX_LOAD_DIAMETER; x++) {
            for (int z = 0; z < MAX_LOAD_DIAMETER; z++) {
                chunksToLoad[x * MAX_LOAD_DIAMETER + z] = (byte) (loadingChunks[x][z] ? 1 : 0);
            }
        }
        return chunksToLoad;
    }

    private void deserializeChunks(byte[] chunksToLoad) {
        if (chunksToLoad.length != MAX_LOAD_DIAMETER * MAX_LOAD_DIAMETER) {
            chunksToLoad = new byte[MAX_LOAD_DIAMETER * MAX_LOAD_DIAMETER];
        }
        chunksLoaded = 1;
        for (int x = 0; x < MAX_LOAD_DIAMETER; x++) {
            for (int z = 0; z < MAX_LOAD_DIAMETER; z++) {
                boolean shouldLoad = x == MAX_LOAD_RADIUS && z == MAX_LOAD_RADIUS || chunksToLoad[x * MAX_LOAD_DIAMETER + z] == 1;
                loadingChunks[x][z] = shouldLoad;
                if (shouldLoad && !(x == MAX_LOAD_RADIUS && z == MAX_LOAD_RADIUS)) {
                    chunksLoaded++;
                }
            }
        }
    }

    @Override
    protected mekanism.common.capabilities.holder.slot.InventorySlotHelper createInventorySlotHelper() {
        return mekanism.common.capabilities.holder.slot.InventorySlotHelper.forSide(() -> facing, side -> side == RelativeSide.BACK, side -> side == RelativeSide.BACK);
    }

    @Override
    public TileComponentChunkLoader getChunkLoader() {
        return chunkLoaderComponent;
    }

    @Override
    public Set<ChunkPos> getChunkSet() {
        Set<ChunkPos> set = new ObjectOpenHashSet<>();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (int x = -MAX_LOAD_RADIUS; x <= MAX_LOAD_RADIUS; x++) {
            for (int z = -MAX_LOAD_RADIUS; z <= MAX_LOAD_RADIUS; z++) {
                if (loadingChunks[x + MAX_LOAD_RADIUS][z + MAX_LOAD_RADIUS]) {
                    set.add(new ChunkPos(chunkX + x, chunkZ + z));
                }
            }
        }
        return set;
    }

    @Override
    public int getRedstoneLevel() {
        return getActive() ? 15 : 0;
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    public boolean isClientRendering() {
        return clientRendering;
    }

    @Override
    public void toggleClientRendering() {
        clientRendering = !clientRendering;
    }

    @Override
    public boolean canDisplayVisuals() {
        return true;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (isClientRendering() && world != null) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int minX = (chunkX - MAX_LOAD_RADIUS) << 4;
            int minZ = (chunkZ - MAX_LOAD_RADIUS) << 4;
            int maxX = ((chunkX + MAX_LOAD_RADIUS) << 4) + 16;
            int maxZ = ((chunkZ + MAX_LOAD_RADIUS) << 4) + 16;
            return new AxisAlignedBB(minX, 0, minZ, maxX, world.getHeight(), maxZ);
        }
        return super.getRenderBoundingBox();
    }
}
