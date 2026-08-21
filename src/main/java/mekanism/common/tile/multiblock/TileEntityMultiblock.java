package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.multiblock.*;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public abstract class TileEntityMultiblock<T extends SynchronizedData<T>> extends TileEntityContainerBlock implements IMultiblock<T> {

    /**
     * The multiblock data for this structure.
     */
    @Nullable
    public T structure;

    /**
     * Whether or not to send this multiblock's structure in the next update packet.
     */
    public boolean sendStructure;

    /**
     * This multiblock's previous "has structure" state.
     */
    public boolean prevStructure;

    /**
     * Whether or not this multiblock has it's structure, for the client side mechanics.
     */
    public boolean clientHasStructure;

    /**
     * Whether or not this multiblock segment is rendering the structure.
     */
    public boolean isRendering;

    /**
     * This multiblock segment's cached data
     */
    public MultiblockCache<T> cachedData = getNewCache();

    /**
     * This multiblock segment's cached inventory ID
     */
    @Nullable
    public String cachedID = null;

    /** World time represented by cachedData, used to choose the newest replica after chunk loading. */
    public long cachedDataTimestamp = Long.MIN_VALUE;

    private static final int MULTIBLOCK_OCCLUSION_CACHE_INTERVAL = 20;
    private static final int MULTIBLOCK_OCCLUSION_MAX_SAMPLE_BLOCKS = 32;
    private static final int MULTIBLOCK_OCCLUSION_MAX_GLASS_BLOCKS = 64;
    private static final int MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS = 320;
    private static final double MULTIBLOCK_EDGE_PROBE_MIN = 0.02D;
    private static final double MULTIBLOCK_EDGE_PROBE_MAX = 0.98D;

    private long cachedMultiblockOcclusionTick = Long.MIN_VALUE;
    private int cachedMultiblockMinX = Integer.MIN_VALUE;
    private int cachedMultiblockMinY = Integer.MIN_VALUE;
    private int cachedMultiblockMinZ = Integer.MIN_VALUE;
    private int cachedMultiblockMaxX = Integer.MIN_VALUE;
    private int cachedMultiblockMaxY = Integer.MIN_VALUE;
    private int cachedMultiblockMaxZ = Integer.MIN_VALUE;
    private MultiblockOcclusionData cachedMultiblockOcclusionData = MultiblockOcclusionData.EMPTY;

    public TileEntityMultiblock(String name) {
        super(name);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        return side -> structure == null ? Collections.emptyList() : structure.getInventorySlots(side);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!playersUsing.isEmpty() && ((isRemote() && !clientHasStructure) || (!isRemote() && structure == null))) {
            playersUsing.forEach(EntityPlayer::closeScreen);
        }
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (structure == null) {
            structure = getNewStructure();
        }
        if (structure != null && structure.renderLocation != null && clientHasStructure && isRendering && !prevStructure) {
            Mekanism.proxy.doMultiblockSparkle(this, structure.renderLocation.getPos(), structure.volLength, structure.volWidth, structure.volHeight, tile -> MultiblockManager.areEqual(this, tile));
        }
        prevStructure = clientHasStructure;
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure == null) {
            isRendering = false;
            if (cachedID != null) {
                getManager().updateCache(this);
            }
            if (ticker == 5) {
                doUpdate();
            }
        }

        if (prevStructure == (structure == null)) {
            if (structure != null && !structure.hasRenderer) {
                structure.hasRenderer = true;
                isRendering = true;
                sendStructure = true;
            }

            Coord4D thisCoord = Coord4D.get(this);
            for (EnumFacing side : EnumFacing.VALUES) {
                Coord4D obj = thisCoord.offset(side);
                if (structure != null && (structure.locations.contains(obj) || structure.internalLocations.contains(obj))) {
                    continue;
                }
                TileEntity tile = obj.getTileEntity(world);
                if (!obj.isAirBlock(world) && (tile == null || tile.getClass() != getClass()) && !(tile instanceof IStructuralMultiblock || tile instanceof IMultiblock)) {
                    MekanismUtils.notifyNeighborofChange(world, obj, getPos());
                }
            }

            Mekanism.packetHandler.sendUpdatePacket(this);
        }

        prevStructure = structure != null;

        if (structure != null) {
            structure.didTick = false;
            if (structure.inventoryID != null) {
                syncCanonicalCacheFromStructure();
            }
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    @Override
    public void doUpdate() {
        if (!isRemote() && (structure == null || !structure.didTick)) {
            getProtocol().doUpdate();
            if (structure != null) {
                structure.didTick = true;
            }
        }
    }

    /**
     * Uses the persistent inventory ID so a replacement structure object remains guarded during
     * the same tick. A malformed formed structure without an ID falls back to its local guard.
     */
    protected boolean tryClaimStructureServerTick() {
        if (structure == null || world == null) {
            return false;
        }
        long gameTime = world.getTotalWorldTime();
        return structure.inventoryID == null ? structure.tryClaimServerTick(gameTime) :
              getManager().tryClaimServerTick(world.provider.getDimension(), structure.inventoryID, gameTime);
    }

    /** Synchronizes both this tile's persisted replica and the manager's canonical runtime cache. */
    public void syncCachedDataFromStructure() {
        if (structure != null && structure.inventoryID != null) {
            cachedData.sync(structure);
            cachedID = structure.inventoryID;
            if (world != null) {
                cachedDataTimestamp = world.getTotalWorldTime();
            }
            getManager().updateCache(this);
        }
    }

    /** Keeps the runtime cache current once per structure tick without copying every casing replica. */
    private void syncCanonicalCacheFromStructure() {
        if (structure == null || structure.inventoryID == null || world == null) {
            return;
        }
        cachedID = structure.inventoryID;
        long gameTime = world.getTotalWorldTime();
        boolean syncCanonical = getManager().tryClaimCacheSync(world.provider.getDimension(), cachedID, gameTime);
        if (syncCanonical) {
            cachedData.sync(structure);
            cachedDataTimestamp = gameTime;
        }
        getManager().updateCache(this, syncCanonical);
    }

    public void sendPacketToRenderer() {
        if (structure != null) {
            structure.locations.forEach(obj -> {
                TileEntity tile = obj.getTileEntity(world);
                if (tile instanceof TileEntityMultiblock<?> tileEntity && tileEntity.isRendering) {
                    Mekanism.packetHandler.sendUpdatePacket(tileEntity);
                }
            });
        }
    }

    protected abstract T getNewStructure();

    public abstract MultiblockCache<T> getNewCache();

    protected abstract UpdateProtocol<T> getProtocol();

    public abstract MultiblockManager<T> getManager();

    @Nullable
    public T getMultiblock() {
        return structure;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isRendering);
        data.add(structure != null);

        if (structure != null && isRendering) {
            if (sendStructure) {
                sendStructure = false;

                data.add(true);

                data.add(structure.volHeight);
                data.add(structure.volWidth);
                data.add(structure.volLength);

                structure.renderLocation.write(data);
                data.add(structure.inventoryID != null);//boolean for if has inv id
                if (structure.inventoryID != null) {
                    data.add(structure.inventoryID);
                }
            } else {
                data.add(false);
            }
        }
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (structure == null) {
                structure = getNewStructure();
            }

            isRendering = dataStream.readBoolean();
            clientHasStructure = dataStream.readBoolean();
            structure.setFormed(clientHasStructure);
            if (clientHasStructure && isRendering) {
                if (dataStream.readBoolean()) {
                    structure.volHeight = dataStream.readInt();
                    structure.volWidth = dataStream.readInt();
                    structure.volLength = dataStream.readInt();
                    structure.volume = structure.volHeight * structure.volWidth * structure.volLength;
                    structure.renderLocation = Coord4D.read(dataStream);
                    if (dataStream.readBoolean()) {
                        structure.inventoryID = PacketHandler.readString(dataStream);
                    } else {
                        structure.inventoryID = null;
                    }
                }
                if (structure.volume <= 0 && structure.volHeight > 0 && structure.volWidth > 0 && structure.volLength > 0) {
                    structure.volume = structure.volHeight * structure.volWidth * structure.volLength;
                }
            }
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (structure == null) {
            if (nbtTags.hasKey("cachedID")) {
                String loadedID = nbtTags.getString("cachedID");
                if (isValidCacheID(loadedID)) {
                    cachedID = loadedID;
                    long loadedTimestamp = nbtTags.hasKey("cachedDataTimestamp") ? nbtTags.getLong("cachedDataTimestamp") : Long.MIN_VALUE;
                    long currentTime = world == null ? Long.MAX_VALUE : world.getTotalWorldTime();
                    cachedDataTimestamp = loadedTimestamp >= 0 && loadedTimestamp <= currentTime ? loadedTimestamp : Long.MIN_VALUE;
                    cachedData.load(nbtTags);
                } else {
                    cachedID = null;
                    cachedData = getNewCache();
                    cachedDataTimestamp = Long.MIN_VALUE;
                }
            }
        }
    }

    static boolean isValidCacheID(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        try {
            return UUID.fromString(id).toString().equalsIgnoreCase(id);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        // Ensure the serialized cache reflects the latest formed structure state.
        if (structure != null && structure.inventoryID != null) {
            cachedID = structure.inventoryID;
            cachedData.sync(structure);
            if (world != null) {
                cachedDataTimestamp = world.getTotalWorldTime();
            }
        }
        if (cachedID != null) {
            nbtTags.setString("cachedID", cachedID);
            nbtTags.setLong("cachedDataTimestamp", cachedDataTimestamp);
            cachedData.save(nbtTags);
        }
    }

    @Nullable
    @Override
    public IInventorySlot getInventorySlot(int slot) {
        if (structure != null) {
            return structure.getInventorySlot(slot, null);
        }
        return super.getInventorySlot(slot);
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        if (structure != null) {
            return structure.getInventorySlots(side);
        }
        return super.getInventorySlots(side);
    }

    @Nullable
    protected IInventorySlot getStructureInventorySlot(int slot) {
        return structure == null ? null : structure.getInventorySlot(slot, null);
    }

    @Nullable
    protected <SLOT extends IInventorySlot> SLOT getStructureInventorySlotAs(int slot, Class<SLOT> slotType) {
        IInventorySlot inventorySlot = getStructureInventorySlot(slot);
        return slotType.isInstance(inventorySlot) ? slotType.cast(inventorySlot) : null;
    }

    @Override
    public void setInventorySlotContents(int slotID, @Nonnull ItemStack itemstack) {
        if (structure == null || slotID < 0 || slotID >= structure.getInternalInventorySlots().size()) {
            return;
        }
        if (!itemstack.isEmpty() && itemstack.getCount() > getInventoryStackLimit()) {
            itemstack = itemstack.copy();
            itemstack.setCount(getInventoryStackLimit());
        }
        IInventorySlot slot = structure.getInventorySlot(slotID, null);
        if (slot != null) {
            slot.setStack(itemstack);
        }
    }

    @Override
    public boolean canInsertItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        IInventorySlot slot = getInventorySlot(slotID);
        return slot != null && canInsertItem(slot, itemstack, side) &&
              slot.insertItem(itemstack, Action.SIMULATE, AutomationType.EXTERNAL).getCount() < itemstack.getCount();
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        IInventorySlot slot = getInventorySlot(slotID);
        return slot != null && canExtractItem(slot, itemstack, side) &&
              !slot.extractItem(itemstack.getCount(), Action.SIMULATE, AutomationType.EXTERNAL).isEmpty();
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        return false;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (clientHasStructure && isRendering) {
            IntBounds bounds = resolveClientStructureBounds();
            if (bounds != null) {
                return new AxisAlignedBB(bounds.minX, bounds.minY, bounds.minZ,
                      bounds.maxX + 1D, bounds.maxY + 1D, bounds.maxZ + 1D);
            }
        }
        return new AxisAlignedBB(getPos());
    }

    @Override
    public boolean persistInventory() {
        return false;
    }

    @Override
    protected boolean persistFluidTanks() {
        return false;
    }

    @Override
    protected boolean persistGasTanks() {
        return false;
    }

    @Override
    public boolean hasInventory() {
        return structure != null && structure.hasInventory();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldCullForOcclusion() {
        if (!MekanismConfig.current().client.GazeCullingTracking.val()) {
            return false;
        }
        if (!isClientStructuredForOcclusion()) {
            return super.shouldCullForOcclusion();
        }
        MultiblockOcclusionData occlusionData = getMultiblockOcclusionData();
        if (!occlusionData.hasStructuralGlass || occlusionData.interiorSamplePoints.isEmpty()) {
            return true;
        }
        return super.shouldCullForOcclusion();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public List<Vec3d> computeOcclusionSamplePoints() {
        if (!isClientStructuredForOcclusion()) {
            return super.computeOcclusionSamplePoints();
        }
        MultiblockOcclusionData occlusionData = getMultiblockOcclusionData();
        if (occlusionData.hasStructuralGlass && !occlusionData.interiorSamplePoints.isEmpty()) {
            return occlusionData.interiorSamplePoints;
        }
        return Collections.emptyList();
    }

    @SideOnly(Side.CLIENT)
    private boolean isClientStructuredForOcclusion() {
        return clientHasStructure && isRendering && structure != null && resolveClientStructureBounds() != null;
    }

    @SideOnly(Side.CLIENT)
    private MultiblockOcclusionData getMultiblockOcclusionData() {
        if (!isClientStructuredForOcclusion()) {
            return MultiblockOcclusionData.EMPTY;
        }
        if (world == null || structure == null) {
            return MultiblockOcclusionData.EMPTY;
        }
        IntBounds bounds = resolveClientStructureBounds();
        if (bounds == null) {
            return MultiblockOcclusionData.EMPTY;
        }
        int minX = bounds.minX;
        int minY = bounds.minY;
        int minZ = bounds.minZ;
        int maxX = bounds.maxX;
        int maxY = bounds.maxY;
        int maxZ = bounds.maxZ;

        long worldTime = world.getTotalWorldTime();
        boolean boundsChanged = minX != cachedMultiblockMinX || minY != cachedMultiblockMinY || minZ != cachedMultiblockMinZ
                || maxX != cachedMultiblockMaxX || maxY != cachedMultiblockMaxY || maxZ != cachedMultiblockMaxZ;
        if (!boundsChanged && cachedMultiblockOcclusionTick != Long.MIN_VALUE
                && worldTime - cachedMultiblockOcclusionTick < MULTIBLOCK_OCCLUSION_CACHE_INTERVAL) {
            return cachedMultiblockOcclusionData;
        }

        cachedMultiblockOcclusionData = buildMultiblockOcclusionData(minX, minY, minZ, maxX, maxY, maxZ);
        cachedMultiblockOcclusionTick = worldTime;
        cachedMultiblockMinX = minX;
        cachedMultiblockMinY = minY;
        cachedMultiblockMinZ = minZ;
        cachedMultiblockMaxX = maxX;
        cachedMultiblockMaxY = maxY;
        cachedMultiblockMaxZ = maxZ;
        return cachedMultiblockOcclusionData;
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    private IntBounds resolveClientStructureBounds() {
        if (structure == null) {
            return null;
        }
        if (structure.minLocation != null && structure.maxLocation != null) {
            return new IntBounds(structure.minLocation.x, structure.minLocation.y, structure.minLocation.z,
                    structure.maxLocation.x, structure.maxLocation.y, structure.maxLocation.z);
        }
        if (structure.renderLocation == null || structure.volLength <= 0 || structure.volWidth <= 0 || structure.volHeight <= 0) {
            return null;
        }
        int minX = structure.renderLocation.x;
        int minY = structure.renderLocation.y - 1;
        int minZ = structure.renderLocation.z;
        int maxX = minX + structure.volLength - 1;
        int maxY = minY + structure.volHeight - 1;
        int maxZ = minZ + structure.volWidth - 1;
        return new IntBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @SideOnly(Side.CLIENT)
    private MultiblockOcclusionData buildMultiblockOcclusionData(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int interiorMinX = minX + 1;
        int interiorMinY = minY + 1;
        int interiorMinZ = minZ + 1;
        int interiorMaxX = maxX - 1;
        int interiorMaxY = maxY - 1;
        int interiorMaxZ = maxZ - 1;
        if (interiorMinX > interiorMaxX || interiorMinY > interiorMaxY || interiorMinZ > interiorMaxZ) {
            return MultiblockOcclusionData.EMPTY;
        }

        boolean hasStructuralGlass = false;
        Set<BlockPos> interiorProbeBlocks = new HashSet<>();
        Set<BlockPos> structuralGlassBlocks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isBoundaryPosition(x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                        continue;
                    }
                    IBlockState state = world.getBlockState(new BlockPos(x, y, z));
                    if (BasicBlockType.get(state) != BasicBlockType.STRUCTURAL_GLASS) {
                        continue;
                    }
                    hasStructuralGlass = true;
                    structuralGlassBlocks.add(new BlockPos(x, y, z));
                    for (EnumFacing side : EnumFacing.VALUES) {
                        int probeX = x + side.getXOffset();
                        int probeY = y + side.getYOffset();
                        int probeZ = z + side.getZOffset();
                        if (probeX >= interiorMinX && probeX <= interiorMaxX
                                && probeY >= interiorMinY && probeY <= interiorMaxY
                                && probeZ >= interiorMinZ && probeZ <= interiorMaxZ) {
                            interiorProbeBlocks.add(new BlockPos(probeX, probeY, probeZ));
                        }
                    }
                }
            }
        }

        if (!hasStructuralGlass) {
            return MultiblockOcclusionData.EMPTY;
        }

        List<Vec3d> samplePoints = new ArrayList<>(Math.min(MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS, 192));
        addGlassSamplePoints(samplePoints, structuralGlassBlocks, interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ);
        if (samplePoints.size() < MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
            samplePoints.addAll(buildInteriorSamplePoints(interiorProbeBlocks, interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ,
                    MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS - samplePoints.size()));
        }
        return new MultiblockOcclusionData(true, samplePoints);
    }

    @SideOnly(Side.CLIENT)
    private boolean isBoundaryPosition(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
    }

    @SideOnly(Side.CLIENT)
    private List<Vec3d> buildInteriorSamplePoints(Set<BlockPos> interiorProbeBlocks, int interiorMinX, int interiorMinY, int interiorMinZ, int interiorMaxX, int interiorMaxY,
                                                  int interiorMaxZ, int maxPoints) {
        if (maxPoints <= 0) {
            return Collections.emptyList();
        }
        List<Vec3d> samplePoints = new ArrayList<>(Math.min(maxPoints, 128));
        addInteriorAABBProbePoints(samplePoints, interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ, maxPoints);
        if (samplePoints.size() >= maxPoints || interiorProbeBlocks.isEmpty()) {
            return samplePoints;
        }

        List<BlockPos> ordered = new ArrayList<>(interiorProbeBlocks);
        ordered.sort(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        int stride = Math.max(1, ordered.size() / MULTIBLOCK_OCCLUSION_MAX_SAMPLE_BLOCKS);
        int sampledBlocks = 0;
        Set<BlockPos> sampled = new HashSet<>();
        for (int i = 0; i < ordered.size() && sampledBlocks < MULTIBLOCK_OCCLUSION_MAX_SAMPLE_BLOCKS
                && samplePoints.size() < maxPoints; i += stride) {
            BlockPos probeBlock = ordered.get(i);
            if (!sampled.add(probeBlock)) {
                continue;
            }
            addBlockProbePoints(samplePoints, probeBlock, maxPoints);
            sampledBlocks++;
        }
        if (sampledBlocks < MULTIBLOCK_OCCLUSION_MAX_SAMPLE_BLOCKS && samplePoints.size() < maxPoints) {
            for (BlockPos probeBlock : ordered) {
                if (sampledBlocks >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_BLOCKS || samplePoints.size() >= maxPoints) {
                    break;
                }
                if (!sampled.add(probeBlock)) {
                    continue;
                }
                addBlockProbePoints(samplePoints, probeBlock, maxPoints);
                sampledBlocks++;
            }
        }
        return samplePoints;
    }

    @SideOnly(Side.CLIENT)
    private void addInteriorAABBProbePoints(List<Vec3d> samplePoints, int interiorMinX, int interiorMinY, int interiorMinZ, int interiorMaxX, int interiorMaxY, int interiorMaxZ,
                                            int maxPoints) {
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        double minX = interiorMinX + MULTIBLOCK_EDGE_PROBE_MIN;
        double minY = interiorMinY + MULTIBLOCK_EDGE_PROBE_MIN;
        double minZ = interiorMinZ + MULTIBLOCK_EDGE_PROBE_MIN;
        double maxX = interiorMaxX + MULTIBLOCK_EDGE_PROBE_MAX;
        double maxY = interiorMaxY + MULTIBLOCK_EDGE_PROBE_MAX;
        double maxZ = interiorMaxZ + MULTIBLOCK_EDGE_PROBE_MAX;
        double centerX = (minX + maxX) * 0.5D;
        double centerY = (minY + maxY) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;

        samplePoints.add(new Vec3d(centerX, centerY, centerZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(minX, minY, minZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(minX, minY, maxZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(minX, maxY, minZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(minX, maxY, maxZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(maxX, minY, minZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(maxX, minY, maxZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(maxX, maxY, minZ));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(maxX, maxY, maxZ));
    }

    @SideOnly(Side.CLIENT)
    private void addBlockProbePoints(List<Vec3d> samplePoints, BlockPos probePos, int maxPoints) {
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        double x = probePos.getX();
        double y = probePos.getY();
        double z = probePos.getZ();
        double xMin = x + MULTIBLOCK_EDGE_PROBE_MIN;
        double yMin = y + MULTIBLOCK_EDGE_PROBE_MIN;
        double zMin = z + MULTIBLOCK_EDGE_PROBE_MIN;
        double xMax = x + MULTIBLOCK_EDGE_PROBE_MAX;
        double yMax = y + MULTIBLOCK_EDGE_PROBE_MAX;
        double zMax = z + MULTIBLOCK_EDGE_PROBE_MAX;

        samplePoints.add(new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMin, yMin, zMin));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMin, yMin, zMax));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMin, yMax, zMin));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMin, yMax, zMax));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMax, yMin, zMin));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMax, yMin, zMax));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMax, yMax, zMin));
        if (samplePoints.size() >= maxPoints) {
            return;
        }
        samplePoints.add(new Vec3d(xMax, yMax, zMax));
    }

    @SideOnly(Side.CLIENT)
    private void addGlassSamplePoints(List<Vec3d> samplePoints, Set<BlockPos> structuralGlassBlocks, int interiorMinX, int interiorMinY, int interiorMinZ,
                                      int interiorMaxX, int interiorMaxY, int interiorMaxZ) {
        if (samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS || structuralGlassBlocks.isEmpty()) {
            return;
        }
        List<BlockPos> orderedGlass = new ArrayList<>(structuralGlassBlocks);
        orderedGlass.sort(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        int stride = Math.max(1, orderedGlass.size() / MULTIBLOCK_OCCLUSION_MAX_GLASS_BLOCKS);
        int sampledBlocks = 0;
        for (int i = 0; i < orderedGlass.size() && sampledBlocks < MULTIBLOCK_OCCLUSION_MAX_GLASS_BLOCKS
                && samplePoints.size() < MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS; i += stride) {
            sampledBlocks += addGlassBlockSamplePoints(samplePoints, orderedGlass.get(i), interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ);
        }
        if (sampledBlocks < MULTIBLOCK_OCCLUSION_MAX_GLASS_BLOCKS && samplePoints.size() < MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
            for (BlockPos glassPos : orderedGlass) {
                if (sampledBlocks >= MULTIBLOCK_OCCLUSION_MAX_GLASS_BLOCKS || samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
                    break;
                }
                sampledBlocks += addGlassBlockSamplePoints(samplePoints, glassPos, interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private int addGlassBlockSamplePoints(List<Vec3d> samplePoints, BlockPos glassPos, int interiorMinX, int interiorMinY, int interiorMinZ, int interiorMaxX, int interiorMaxY,
                                          int interiorMaxZ) {
        if (samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
            return 0;
        }
        int added = 0;
        samplePoints.add(new Vec3d(glassPos).add(0.5D, 0.5D, 0.5D));
        added++;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
                break;
            }
            int inX = glassPos.getX() + side.getXOffset();
            int inY = glassPos.getY() + side.getYOffset();
            int inZ = glassPos.getZ() + side.getZOffset();
            if (inX < interiorMinX || inX > interiorMaxX || inY < interiorMinY || inY > interiorMaxY || inZ < interiorMinZ || inZ > interiorMaxZ) {
                continue;
            }
            double innerX = glassPos.getX() + 0.5D + side.getXOffset() * 0.48D;
            double innerY = glassPos.getY() + 0.5D + side.getYOffset() * 0.48D;
            double innerZ = glassPos.getZ() + 0.5D + side.getZOffset() * 0.48D;
            samplePoints.add(new Vec3d(innerX, innerY, innerZ));
            added++;
            if (samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
                continue;
            }
            added += addGlassInnerOffsetPoints(samplePoints, innerX, innerY, innerZ, side);
        }
        return added;
    }

    @SideOnly(Side.CLIENT)
    private int addGlassInnerOffsetPoints(List<Vec3d> samplePoints, double innerX, double innerY, double innerZ, EnumFacing side) {
        if (samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
            return 0;
        }
        double offset = 0.24D;
        int added = 0;
        switch (side.getAxis()) {
            case X:
                added += tryAddSamplePoint(samplePoints, innerX, innerY + offset, innerZ + offset);
                added += tryAddSamplePoint(samplePoints, innerX, innerY + offset, innerZ - offset);
                added += tryAddSamplePoint(samplePoints, innerX, innerY - offset, innerZ + offset);
                added += tryAddSamplePoint(samplePoints, innerX, innerY - offset, innerZ - offset);
                break;
            case Y:
                added += tryAddSamplePoint(samplePoints, innerX + offset, innerY, innerZ + offset);
                added += tryAddSamplePoint(samplePoints, innerX + offset, innerY, innerZ - offset);
                added += tryAddSamplePoint(samplePoints, innerX - offset, innerY, innerZ + offset);
                added += tryAddSamplePoint(samplePoints, innerX - offset, innerY, innerZ - offset);
                break;
            case Z:
                added += tryAddSamplePoint(samplePoints, innerX + offset, innerY + offset, innerZ);
                added += tryAddSamplePoint(samplePoints, innerX + offset, innerY - offset, innerZ);
                added += tryAddSamplePoint(samplePoints, innerX - offset, innerY + offset, innerZ);
                added += tryAddSamplePoint(samplePoints, innerX - offset, innerY - offset, innerZ);
                break;
        }
        return added;
    }

    @SideOnly(Side.CLIENT)
    private int tryAddSamplePoint(List<Vec3d> samplePoints, double x, double y, double z) {
        if (samplePoints.size() >= MULTIBLOCK_OCCLUSION_MAX_SAMPLE_POINTS) {
            return 0;
        }
        samplePoints.add(new Vec3d(x, y, z));
        return 1;
    }

    @Override
    public T getSynchronizedData() {
        return structure;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        //We handle dumping radiation separately for multiblocks
        return false;
    }

    private static class MultiblockOcclusionData {

        private static final MultiblockOcclusionData EMPTY = new MultiblockOcclusionData(false, Collections.emptyList());

        private final boolean hasStructuralGlass;
        private final List<Vec3d> interiorSamplePoints;

        private MultiblockOcclusionData(boolean hasStructuralGlass, List<Vec3d> interiorSamplePoints) {
            this.hasStructuralGlass = hasStructuralGlass;
            this.interiorSamplePoints = interiorSamplePoints;
        }
    }

    private static class IntBounds {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private IntBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
