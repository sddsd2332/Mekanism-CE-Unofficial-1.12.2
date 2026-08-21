package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.client.render.bloom.BloomRenderDigitalMiner;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.chunkloading.IChunkLoader;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.filter.SortableFilterManager;
import mekanism.common.content.miner.MItemStackFilter;
import mekanism.common.content.miner.MOreDictFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.content.miner.ThreadMinerSearch;
import mekanism.common.content.miner.ThreadMinerSearch.State;
import mekanism.common.inventory.container.ContainerDigitalMinerConfig;
import mekanism.common.inventory.container.ContainerFilter;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.lib.inventory.HandlerTransitRequest;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import mekanism.common.tile.component.TileComponentChunkLoader;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.Constants.WorldEvents;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TileEntityDigitalMiner extends TileEntityElectricBlock implements IUpgradeTile, IRedstoneControl, IActiveState, ISustainedData, IChunkLoader, IAdvancedBoundingBlock,
        IHasVisualization, ISpecialSelectionWireframeTile, ITileFilterHolder<MinerFilter> {
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_NORTH = {
            ISpecialSelectionWireframeTile.SelectionTransform.translate(0.0D, 0.0D, -1.0D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_SOUTH = {
            ISpecialSelectionWireframeTile.SelectionTransform.translate(0.0D, 0.0D, -1.0D),
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180.0D, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_WEST = {
            ISpecialSelectionWireframeTile.SelectionTransform.translate(0.0D, 0.0D, -1.0D),
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(90.0D, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_EAST = {
            ISpecialSelectionWireframeTile.SelectionTransform.translate(0.0D, 0.0D, -1.0D),
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(270.0D, 0.5D, 0.5D, 0.5D)
    };

    private static final int[] MAIN_SLOTS = IntStream.range(0, 27).toArray();

    public Map<Chunk3D, BitSet> oresToMine = new HashMap<>();
    public Int2ObjectMap<MinerFilter> replaceMap = new Int2ObjectOpenHashMap<>();
    private final SortableFilterManager<MinerFilter> filterManager = new SortableFilterManager<>(MinerFilter.class, this::onFilterManagerChanged);
    public ThreadMinerSearch searcher = new ThreadMinerSearch(this);
    public final double BASE_ENERGY_USAGE = MachineType.DIGITAL_MINER.getUsage();
    public double energyUsage = BASE_ENERGY_USAGE;
    private int radius;

    public boolean inverse;
    private ItemStack inverseReplaceTarget = ItemStack.EMPTY;
    private boolean inverseRequiresReplacement;

    public int minY = 0;
    public int maxY = 60;

    public boolean doEject = false;
    public boolean doPull = false;

    public ItemStack missingStack = ItemStack.EMPTY;

    public int BASE_DELAY = 80;

    public int delay;

    public int delayLength = BASE_DELAY;

    public int clientToMine;

    public boolean isActive;
    public boolean clientActive;

    public boolean silkTouch;

    public boolean running;

    public double prevEnergy;

    public int delayTicks;

    public boolean initCalc = false;

    public int numPowering;

    public boolean clientRendering = false;

    private Set<ChunkPos> chunkSet;

    /**
     * This machine's current RedstoneControl type.
     */
    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentUpgrade upgradeComponent = new TileComponentUpgrade(this);
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    public TileComponentChunkLoader chunkLoaderComponent = new TileComponentChunkLoader(this);
    public String[] methods = {"setRadius", "setMin", "setMax", "addFilter", "removeFilter", "addOreFilter", "removeOreFilter", "reset", "start", "stop", "getToMine"};
    private List<IInventorySlot> mainSlots = new ArrayList<>();
    private EnergyInventorySlot energySlot;

    /**
     * Exposes the miner inventory to network-independent output providers. External extraction rules on these slots
     * keep configured replacement materials inside the miner.
     */
    public List<IInventorySlot> getMiningOutputSlots() {
        return Collections.unmodifiableList(mainSlots);
    }

    public TileEntityDigitalMiner() {
        super("DigitalMiner", MachineType.DIGITAL_MINER.getStorage());
        initializeInventorySlots();
        // The miner sends a small runtime packet while its GUI is open and sends configuration/filter packets on
        // demand. The generic per-tick packet would duplicate that traffic and resend the full filter list.
        doAutoSync = false;
        radius = 10;
        setSupportedUpgrade(Upgrade.ANCHOR);
        setSupportedUpgrade(Upgrade.STONE_GENERATOR);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        mainSlots = new ArrayList<>();
        InventorySlotHelper builder = createInventorySlotHelper();
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                BasicInventorySlot slot = BasicInventorySlot.at((stack, automationType) -> automationType != AutomationType.EXTERNAL || !isReplaceStack(stack),
                      (stack, automationType) -> automationType != AutomationType.EXTERNAL || isReplaceStack(stack), listener, 8 + slotX * 18, 92 + slotY * 18);
                builder.addSlot(slot, RelativeSide.BACK, RelativeSide.TOP);
                mainSlots.add(slot);
            }
        }
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 152, 20));
        return builder.build();
    }

    @Override
    protected double getMainEnergyPerTick() {
        return getPerTick();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (getActive()) {
            new ReferenceOpenHashSet<>(playersUsing).forEach(player -> {
                if (player.openContainer instanceof ContainerDigitalMinerConfig || player.openContainer instanceof ContainerNull || player.openContainer instanceof ContainerFilter) {
                    player.closeScreen();
                }
            });
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (!initCalc) {
            if (searcher.state != State.IDLE) {
                boolean prevRunning = running;
                reset();
                start();
                running = prevRunning;
            }
            initCalc = true;
        }
        if (searcher.state == State.SEARCHING) {
            searcher.captureSnapshotBatch();
        }

        energySlot.fillContainerOrConvert();

        if (MekanismUtils.canFunction(this) && running && searcher.state == State.FINISHED && !oresToMine.isEmpty()) {
            double energyPerTick = getPerTick();
            if (getMainEnergyContainer().extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL) == energyPerTick) {
                setActive(true);
                if (delay > 0) {
                    delay--;
                }
                getMainEnergyContainer().extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                if (delay == 0) {
                    tryMineBlock();
                    delay = getDelay();
                }
            } else {
                setActive(false);
            }
        } else {
            setActive(false);
        }

        TransitRequest ejectMap = getEjectItemMap();
        if (doEject && delayTicks == 0 && !ejectMap.isEmpty()) {
            TileEntity ejectInv = getEjectInv();
            TileEntity ejectTile = getEjectTile();
            if (ejectInv != null && ejectTile != null) {
                ILogisticalTransporter capability = CapabilityUtils.getCapability(ejectInv, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, facing.getOpposite());
                TransitResponse response;
                if (capability == null) {
                    response = InventoryUtils.putStackInInventory(ejectInv, ejectMap, facing.getOpposite(), false);
                } else {
                    response = TransporterUtils.insert(ejectTile, capability, ejectMap, null, true, 0);
                }
                if (!response.isEmpty()) {
                    response.useAll();
                }
                delayTicks = 10;
            }
        } else if (delayTicks > 0) {
            delayTicks--;
        }

        if (!playersUsing.isEmpty()) {
            playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this, getSmallPacket(new TileNetworkList())), (EntityPlayerMP) player));
        }
        prevEnergy = getEnergy();
    }

    //TODO
    private void tryMineBlock() {
        boolean did = false;
        for (Iterator<Chunk3D> it = oresToMine.keySet().iterator(); it.hasNext(); ) {
            Chunk3D chunk = it.next();
            BitSet set = oresToMine.get(chunk);
            int next = 0;
            while (!did) {
                int index = set.nextSetBit(next);
                Coord4D coord = getCoordFromIndex(index);
                if (index == -1) {
                    it.remove();
                    break;
                }

                if (!coord.exists(world)) {
                    set.clear(index);
                    if (set.cardinality() == 0) {
                        it.remove();
                        break;
                    }
                    next = index + 1;
                    continue;
                }

                IBlockState state = coord.getBlockState(world);
                Block block = state.getBlock();
                int meta = block.getMetaFromState(state);
                if (coord.isAirBlock(world)) {
                    set.clear(index);
                    if (set.cardinality() == 0) {
                        it.remove();
                        break;
                    }
                    next = index + 1;
                    continue;
                }
                boolean hasFilter = false;
                ItemStack is = new ItemStack(block, 1, meta);
                for (MinerFilter filter : filterManager.getEnabledFilters()) {
                    if (filter.canFilter(is)) {
                        hasFilter = true;
                        break;
                    }
                }
                if (inverse == hasFilter || !canMine(coord)) {
                    set.clear(index);
                    if (set.cardinality() == 0) {
                        it.remove();
                        break;
                    }
                    next = index + 1;
                    continue;
                }

                List<ItemStack> drops = MinerUtils.getDrops(world, coord, silkTouch, this.pos);
                if (canInsert(drops) && setReplace(coord, index, !hasFilter)) {
                    did = true;
                    add(drops);
                    set.clear(index);
                    if (set.cardinality() == 0) {
                        it.remove();
                    }
                    world.playEvent(WorldEvents.BREAK_BLOCK_EFFECTS, coord.getPos(), Block.getStateId(state));
                    missingStack = ItemStack.EMPTY;
                }
                break;
            }
        }

    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public double getPerTick() {
        double ret = energyUsage;
        if (silkTouch) {
            ret *= MekanismConfig.current().general.minerSilkMultiplier.val();
        }
        int baseRad = Math.max(radius - 10, 0);
        ret *= 1 + ((float) baseRad / 22F);
        int baseHeight = Math.max(maxY - minY - 60, 0);
        ret *= 1 + ((float) baseHeight / 195F);
        return ret;
    }

    public int getDelay() {
        return Math.max(delayLength, 0);
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int newRadius) {
        boolean changed = radius != newRadius;
        radius = newRadius;
        // If the radius changed and we're on the server, go ahead and refresh
        // the chunk set
        if (changed && hasWorld() && !isRemote()) {
            chunkSet = null;
            getChunkSet();
        }
    }

    public int getMinimumYLimit() {
        return MekanismConfig.current().mekce.DigitalMinerMinY.val();
    }

    public int getMaximumYLimit() {
        int configuredMax = MekanismConfig.current().mekce.DigitalMinerMaxY.val();
        int max = world == null ? configuredMax : Math.min(configuredMax, world.getHeight() - 1);
        return Math.max(getMinimumYLimit(), max);
    }

    public void setMinYFromPacket(int newMinY) {
        int minLimit = getMinimumYLimit();
        setMinY(MathHelper.clamp(newMinY, minLimit, Math.max(minLimit, maxY)));
        Mekanism.packetHandler.sendUpdatePacket(this);
        MekanismUtils.saveChunk(this);
    }

    private void setMinY(int newMinY) {
        minY = newMinY;
    }

    public void setMaxYFromPacket(int newMaxY) {
        int maxLimit = getMaximumYLimit();
        setMaxY(MathHelper.clamp(newMaxY, Math.min(minY, maxLimit), maxLimit));
        Mekanism.packetHandler.sendUpdatePacket(this);
        MekanismUtils.saveChunk(this);
    }

    private void setMaxY(int newMaxY) {
        maxY = newMaxY;
    }

    /*
     * returns false if unsuccessful
     */
    public boolean setReplace(Coord4D obj, int index, boolean inverseMatch) {
        BlockPos pos = obj.getPos();
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        ItemStack stack = getReplace(index, inverseMatch);
        EntityPlayer fakePlayer = Objects.requireNonNull(Mekanism.proxy.getDummyPlayer((WorldServer) world, this.pos).get());

        //if its a shulker box, remove it TE so it can't drop itself in breakBlock - we've already captured its itemblock
        TileEntity te = world.getTileEntity(pos);
        TileEntityShulkerBox tileEntityShulkerBox = null;
        if (te instanceof TileEntityShulkerBox box) {
            tileEntityShulkerBox = box;
            world.removeTileEntity(pos);
        }

        if (!stack.isEmpty()) {
            if (!world.setBlockState(pos, StackUtils.getStateForPlacement(stack, world, pos, fakePlayer), 3)) {
                if (tileEntityShulkerBox != null) {
                    tileEntityShulkerBox.validate();
                    world.setTileEntity(pos, tileEntityShulkerBox);
                }
                return false;
            }
            IBlockState s = obj.getBlockState(world);
            if (s.getBlock() instanceof BlockBush blockBush && !blockBush.canBlockStay(world, pos, s)) {
                s.getBlock().dropBlockAsItem(world, pos, s, 1);
                if (!world.setBlockToAir(pos)) {
                    if (tileEntityShulkerBox != null) {
                        tileEntityShulkerBox.validate();
                        world.setTileEntity(pos, tileEntityShulkerBox);
                    }
                    return false;
                }
            }
            return true;
        } else {
            ItemStack requiredStack = inverseMatch ? inverseReplaceTarget : getFilterReplaceStack(index);
            boolean requiresReplacement = inverseMatch ? inverseRequiresReplacement : requiresFilterReplacement(index);
            if (requiredStack.isEmpty() || !requiresReplacement) {
                if (!world.setBlockToAir(pos)) {
                    if (tileEntityShulkerBox != null) {
                        tileEntityShulkerBox.validate();
                        world.setTileEntity(pos, tileEntityShulkerBox);
                    }
                    return false;
                }
                return true;
            }
            missingStack = requiredStack;

            // something failed, so put that thing back where it came from
            if (tileEntityShulkerBox != null) {
                tileEntityShulkerBox.validate();
                world.setTileEntity(pos, tileEntityShulkerBox);
            }
            return false;
        }
    }

    private boolean canMine(Coord4D coord) {
        IBlockState state = coord.getBlockState(world);
        //Check if the block is breakable, to avoid blocks like bedrock being being mined.
        if (state.getBlockHardness(world, coord.getPos()) < 0) {
            return false;
        }

        EntityPlayer dummy = Objects.requireNonNull(Mekanism.proxy.getDummyPlayer((WorldServer) world, pos).get());
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, coord.getPos(), state, dummy);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    public ItemStack getReplace(int index, boolean inverseMatch) {
        ItemStack replaceStack = inverseMatch ? inverseReplaceTarget : getFilterReplaceStack(index);
        if (replaceStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (IInventorySlot slot : mainSlots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.isItemEqual(replaceStack)) {
                slot.shrinkStack(1, Action.EXECUTE);
                return StackUtils.size(replaceStack, 1);
            }
        }

        if (isUpgradeInstalled(Upgrade.STONE_GENERATOR)) {
            if (replaceStack.getItem() == Item.getItemFromBlock(Blocks.STONE) || replaceStack.getItem() == Item.getItemFromBlock(Blocks.COBBLESTONE)) {
                return StackUtils.size(replaceStack, 1);
            }
        }


        if (doPull && getPullInv() != null) {
            IItemHandler pullInv = InventoryUtils.getItemHandler(getPullInv(), EnumFacing.DOWN);
            TransitRequest request = TransitRequest.definedItem(pullInv, 1, stack -> StackUtils.equalsWildcardWithNBT(replaceStack, stack));
            if (!request.isEmpty()) {
                TransitResponse response = request.createSimpleResponse();
                response.useAll();
                return StackUtils.size(replaceStack, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack getFilterReplaceStack(int index) {
        MinerFilter filter = replaceMap.get(index);
        return filter == null ? ItemStack.EMPTY : filter.replaceStack;
    }

    private boolean requiresFilterReplacement(int index) {
        MinerFilter filter = replaceMap.get(index);
        return filter != null && filter.requireStack;
    }

    public TransitRequest getEjectItemMap() {
        EnumFacing outputSide = facing.getOpposite();
        IItemHandler handler = getItemHandler(outputSide);
        return handler == null ? new HandlerTransitRequest(null) : InventoryUtils.getEjectItemMap(handler, getInventorySlots(outputSide));
    }

    public boolean canInsert(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return true;
        }
        int slots = mainSlots.size();
        Int2ObjectMap<ItemCount> cachedStacks = new Int2ObjectOpenHashMap<>(slots);
        for (int i = 0; i < slots; i++) {
            IInventorySlot slot = mainSlots.get(i);
            if (!slot.isEmpty()) {
                cachedStacks.put(i, new ItemCount(slot.getStack(), slot.getCount()));
            }
        }
        for (ItemStack stack : stacks) {
            if (!simulateInsert(cachedStacks, slots, stack).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack simulateInsert(Int2ObjectMap<ItemCount> cachedStacks, int slots, ItemStack stackToInsert) {
        if (stackToInsert.isEmpty()) {
            return stackToInsert;
        }
        ItemStack stack = stackToInsert.copy();
        for (int i = 0; i < slots; i++) {
            ItemCount cachedItem = cachedStacks.get(i);
            if (cachedItem != null && InventoryUtils.areItemsStackable(stack, cachedItem.stack)) {
                IInventorySlot slot = mainSlots.get(i);
                int limit = slot.getLimit(stack);
                if (cachedItem.count < limit) {
                    cachedItem.count += stack.getCount();
                    if (cachedItem.count <= limit) {
                        return ItemStack.EMPTY;
                    }
                    stack = StackUtils.size(stack, cachedItem.count - limit);
                    cachedItem.count = limit;
                }
            }
        }
        for (int i = 0; i < slots; i++) {
            if (!cachedStacks.containsKey(i)) {
                IInventorySlot slot = mainSlots.get(i);
                int stackSize = stack.getCount();
                stack = slot.insertItem(stack, Action.SIMULATE, AutomationType.INTERNAL);
                int remainderSize = stack.getCount();
                if (remainderSize < stackSize) {
                    cachedStacks.put(i, new ItemCount(StackUtils.size(stackToInsert, stackSize - remainderSize), stackSize - remainderSize));
                    if (stack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return stack;
    }

    public TileEntity getPullInv() {
        return Coord4D.get(this).translate(0, 2, 0).getTileEntity(world);
    }

    public TileEntity getEjectInv() {
        final EnumFacing side = facing.getOpposite();
        final BlockPos pos = getPos().up().offset(side, 2);
        if (world.isBlockLoaded(pos)) {
            return world.getTileEntity(pos);
        }
        return null;
    }

    public void add(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }

        for (ItemStack stack : stacks) {
            InventoryUtils.insertItem(mainSlots, stack, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    public void start() {
        if (searcher.state == State.IDLE && searcher.prepare()) {
            searcher.start();
        }
        running = true;
        MekanismUtils.saveChunk(this);
    }

    public void stop() {
        if (searcher.state == State.SEARCHING) {
            reset();
            return;
        } else if (searcher.state == State.FINISHED) {
            running = false;
        }
        MekanismUtils.saveChunk(this);
    }

    public void reset() {
        if (searcher != null) {
            searcher.cancel();
        }
        searcher = new ThreadMinerSearch(this);
        running = false;
        oresToMine.clear();
        replaceMap.clear();
        missingStack = ItemStack.EMPTY;
        setActive(false);
        MekanismUtils.saveChunk(this);
    }

    @Override
    public void onChunkUnload() {
        if (searcher != null) {
            searcher.cancel();
        }
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        if (searcher != null) {
            searcher.cancel();
        }
        super.invalidate();
    }

    public boolean isReplaceStack(ItemStack stack) {
        if (inverse && !inverseReplaceTarget.isEmpty() && StackUtils.equalsWildcardWithNBT(inverseReplaceTarget, stack)) {
            return true;
        }
        for (MinerFilter filter : filterManager.getEnabledFilters()) {
            if (!filter.replaceStack.isEmpty() && filter.replaceStack.isItemEqual(stack)) {
                return true;
            }
        }
        return false;
    }

    public int getSize() {
        int size = 0;
        for (Chunk3D chunk : oresToMine.keySet()) {
            size += oresToMine.get(chunk).cardinality();
        }
        return size;
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
        super.openInventory(player);
        if (!isRemote()) {
            Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player);
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        clientActive = isActive = nbtTags.getBoolean("isActive");
        running = nbtTags.getBoolean("running");
        delay = nbtTags.getInteger("delay");
        numPowering = nbtTags.getInteger("numPowering");
        searcher.state = MekanismUtils.getByIndex(State.values(), nbtTags.getInteger("state"), searcher.state);
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        setConfigurationData(nbtTags);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setBoolean("running", running);
        nbtTags.setInteger("delay", delay);
        nbtTags.setInteger("numPowering", numPowering);
        nbtTags.setInteger("state", searcher.state.ordinal());
        nbtTags.setInteger("controlType", controlType.ordinal());
        getConfigurationData(nbtTags);
    }

    private void readBasicData(ByteBuf dataStream) {
        setRadius(dataStream.readInt());//client allowed to use whatever server sends
        minY = dataStream.readInt();
        maxY = dataStream.readInt();
        doEject = dataStream.readBoolean();
        doPull = dataStream.readBoolean();
        clientActive = dataStream.readBoolean();
        running = dataStream.readBoolean();
        silkTouch = dataStream.readBoolean();
        numPowering = dataStream.readInt();
        searcher.state = MekanismUtils.getByIndex(State.values(), dataStream.readInt(), searcher.state);
        clientToMine = dataStream.readInt();
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
        inverse = dataStream.readBoolean();
        inverseRequiresReplacement = dataStream.readBoolean();
        setInverseReplaceTarget(PacketHandler.readStack(dataStream));
        if (dataStream.readBoolean()) {
            missingStack = new ItemStack(Item.getItemById(dataStream.readInt()), 1, dataStream.readInt());
        } else {
            missingStack = ItemStack.EMPTY;
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            switch (type) {
                case 0 -> doEject = !doEject;
                case 1 -> doPull = !doPull;
                case 3 -> start();
                case 4 -> stop();
                case 5 -> reset();
                case 6 ->
                        setRadius(Math.max(0, Math.min(dataStream.readInt(), MekanismConfig.current().general.digitalMinerMaxRadius.val())));
                case 7 -> setMinYFromPacket(dataStream.readInt());
                case 8 -> setMaxYFromPacket(dataStream.readInt());
                case 9 -> silkTouch = !silkTouch;
                case 10 -> inverse = !inverse;
                case 16 -> setInverseReplaceTarget(PacketHandler.readStack(dataStream));
                case 17 -> inverseRequiresReplacement = !inverseRequiresReplacement;
                case 11 -> {
                    // Move filter up
                    int filterIndex = dataStream.readInt();
                    if (filterManager.moveUp(filterIndex)) {
                        playersUsing.forEach(this::openInventory);
                    }
                }
                case 12 -> {
                    // Move filter down
                    int filterIndex = dataStream.readInt();
                    if (filterManager.moveDown(filterIndex)) {
                        playersUsing.forEach(this::openInventory);
                    }
                }
                case 13 -> {
                    filterManager.toggleState(dataStream.readInt());
                    sendFilterUpdate(null);
                }
                case 14 -> {
                    int filterIndex = dataStream.readInt();
                    if (filterManager.moveToTop(filterIndex)) {
                        playersUsing.forEach(this::openInventory);
                    }
                }
                case 15 -> {
                    int filterIndex = dataStream.readInt();
                    if (filterManager.moveToBottom(filterIndex)) {
                        playersUsing.forEach(this::openInventory);
                    }
                }
            }

            MekanismUtils.saveChunk(this);
            playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this, getGenericPacket(new TileNetworkList())), (EntityPlayerMP) player));
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            int type = dataStream.readInt();
            if (type == 0) {
                readBasicData(dataStream);
                filterManager.readFromPacket(dataStream, MinerFilter::readFromPacket);
            } else if (type == 1) {
                readBasicData(dataStream);
            } else if (type == 2) {
                filterManager.readFromPacket(dataStream, MinerFilter::readFromPacket);
            } else if (type == 3) {
                clientActive = dataStream.readBoolean();
                running = dataStream.readBoolean();
                clientToMine = dataStream.readInt();
                if (dataStream.readBoolean()) {
                    missingStack = new ItemStack(Item.getItemById(dataStream.readInt()), 1, dataStream.readInt());
                } else {
                    missingStack = ItemStack.EMPTY;
                }
            }
            if (clientActive != isActive) {
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    private void addBasicData(TileNetworkList data) {
        data.add(radius);
        data.add(minY);
        data.add(maxY);
        data.add(doEject);
        data.add(doPull);
        data.add(isActive);
        data.add(running);
        data.add(silkTouch);
        data.add(numPowering);
        data.add(searcher.state.ordinal());

        if (searcher.state == State.SEARCHING) {
            data.add(searcher.found);
        } else {
            data.add(getSize());
        }

        data.add(controlType.ordinal());
        data.add(inverse);
        data.add(inverseRequiresReplacement);
        data.add(inverseReplaceTarget);
        if (!missingStack.isEmpty()) {
            data.add(true);
            data.add(MekanismUtils.getID(missingStack));
            data.add(missingStack.getItemDamage());
        } else {
            data.add(false);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(0);
        addBasicData(data);
        filterManager.writeToPacket(data, MinerFilter::write);
        return data;
    }

    public TileNetworkList getSmallPacket(TileNetworkList data) {
        super.getNetworkedData(data);

        data.add(3);

        data.add(isActive);
        data.add(running);

        if (searcher.state == State.SEARCHING) {
            data.add(searcher.found);
        } else {
            data.add(getSize());
        }
        if (!missingStack.isEmpty()) {
            data.add(true);
            data.add(MekanismUtils.getID(missingStack));
            data.add(missingStack.getItemDamage());
        } else {
            data.add(false);
        }
        return data;
    }

    public TileNetworkList getGenericPacket(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(1);
        addBasicData(data);
        return data;
    }

    public TileNetworkList getFilterPacket(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(2);
        filterManager.writeToPacket(data, MinerFilter::write);

        return data;
    }

    public int getTotalSize() {
        return getDiameter() * getDiameter() * (maxY - minY + 1);
    }

    public int getDiameter() {
        return (radius * 2) + 1;
    }

    public Coord4D getStartingCoord() {
        return new Coord4D(getPos().getX() - radius, minY, getPos().getZ() - radius, world.provider.getDimension());
    }

    public Coord4D getCoordFromIndex(int index) {
        int diameter = getDiameter();
        Coord4D start = getStartingCoord();
        int x = start.x + index % diameter;
        int y = start.y + (index / diameter / diameter);
        int z = start.z + (index / diameter) % diameter;
        return new Coord4D(x, y, z, world.provider.getDimension());
    }

    @Override
    public boolean isPowered() {
        return redstone || numPowering > 0;
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
        MekanismUtils.saveChunk(this);
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            clientActive = active;
        }
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }


    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        consumer.accept(getPos().add(x, y, z), true);
                    }
                }
            }
        }
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side, Vec3i offset) {
        return isOffsetCapabilityDisabled(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side, offset) ? InventoryUtils.EMPTY : MAIN_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, @Nonnull ItemStack stack, @Nonnull EnumFacing side, Vec3i offset) {
        return !isOffsetCapabilityDisabled(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side, offset) && super.canInsertItem(slot, stack, side);
    }

    @Override
    public boolean canExtractItem(int slot, @Nonnull ItemStack stack, @Nonnull EnumFacing side, Vec3i offset) {
        return !isOffsetCapabilityDisabled(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side, offset) && super.canExtractItem(slot, stack, side);
    }

    @Override
    protected InventorySlotHelper createInventorySlotHelper() {
        return InventorySlotHelper.forSide(() -> facing, side -> side == RelativeSide.TOP, side -> side == RelativeSide.BACK);
    }

    public TileEntity getEjectTile() {
        final EnumFacing side = facing.getOpposite();
        final BlockPos pos = getPos().up().offset(side);
        if (world.isBlockLoaded(pos)) {
            return world.getTileEntity(pos);
        }
        return null;
    }

    @Override
    public void onPower() {
        numPowering++;
    }

    @Override
    public void onNoPower() {
        numPowering--;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            if (arguments.length != 1 || !(arguments[0] instanceof Double)) {
                return new Object[]{"Invalid parameters."};
            }
            setRadius(Math.max(0, Math.min(((Double) arguments[0]).intValue(), MekanismConfig.current().general.digitalMinerMaxRadius.val())));
        } else if (method == 1) {
            if (arguments.length != 1 || !(arguments[0] instanceof Double)) {
                return new Object[]{"Invalid parameters."};
            }
            setMinYFromPacket(((Double) arguments[0]).intValue());
        } else if (method == 2) {
            if (arguments.length != 1 || !(arguments[0] instanceof Double)) {
                return new Object[]{"Invalid parameters."};
            }
            setMaxYFromPacket(((Double) arguments[0]).intValue());
        } else if (method == 3) {
            if (arguments.length < 1 || !(arguments[0] instanceof Double)) {
                return new Object[]{"Invalid parameters."};
            }
            int id = ((Double) arguments[0]).intValue();
            int meta = 0;
            if (arguments.length > 1) {
                if (arguments[1] instanceof Double) {
                    meta = ((Double) arguments[1]).intValue();
                }
            }
            filterManager.addFilter(new MItemStackFilter(new ItemStack(Item.getItemById(id), 1, meta)));
            return new Object[]{"Added filter."};
        } else if (method == 4) {
            if (arguments.length < 1 || !(arguments[0] instanceof Double)) {
                return new Object[]{"Invalid parameters."};
            }
            int id = ((Double) arguments[0]).intValue();
            Iterator<MinerFilter> iter = filterManager.getFilters().iterator();
            while (iter.hasNext()) {
                MinerFilter filter = iter.next();
                if (filter instanceof MItemStackFilter) {
                    if (MekanismUtils.getID(((MItemStackFilter) filter).getItemStack()) == id) {
                            filterManager.removeFilter(filter);
                            return new Object[]{"Removed filter."};
                    }
                }
            }
            return new Object[]{"Couldn't find filter."};
        } else if (method == 5) {
            if (arguments.length < 1 || !(arguments[0] instanceof String ore)) {
                return new Object[]{"Invalid parameters."};
            }
            MOreDictFilter filter = new MOreDictFilter();
            filter.setOreDictName(ore);
            filterManager.addFilter(filter);
            return new Object[]{"Added filter."};
        } else if (method == 6) {
            if (arguments.length < 1 || !(arguments[0] instanceof String ore)) {
                return new Object[]{"Invalid parameters."};
            }
            Iterator<MinerFilter> iter = filterManager.getFilters().iterator();
            while (iter.hasNext()) {
                MinerFilter filter = iter.next();
                if (filter instanceof MOreDictFilter) {
                    if (((MOreDictFilter) filter).getOreDictName().equals(ore)) {
                            filterManager.removeFilter(filter);
                            return new Object[]{"Removed filter."};
                    }
                }
            }
            return new Object[]{"Couldn't find filter."};
        } else if (method == 7) {
            reset();
            return new Object[]{"Reset miner."};
        } else if (method == 8) {
            start();
            return new Object[]{"Started miner."};
        } else if (method == 9) {
            stop();
            return new Object[]{"Stopped miner."};
        } else if (method == 10) {
            return new Object[]{searcher != null ? searcher.found : 0};
        }
        playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this, getGenericPacket(new TileNetworkList())), (EntityPlayerMP) player));
        return null;
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        nbtTags.setInteger("radius", radius);
        nbtTags.setInteger("minY", minY);
        nbtTags.setInteger("maxY", maxY);
        nbtTags.setBoolean("doEject", doEject);
        nbtTags.setBoolean("doPull", doPull);
        nbtTags.setBoolean("silkTouch", silkTouch);
        nbtTags.setBoolean("inverse", inverse);
        nbtTags.setBoolean("inverseRequiresReplacement", inverseRequiresReplacement);
        if (!inverseReplaceTarget.isEmpty()) {
            nbtTags.setTag("inverseReplaceTarget", inverseReplaceTarget.writeToNBT(new NBTTagCompound()));
        }
        filterManager.writeToNBT(nbtTags, MinerFilter::write);
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        setRadius(Math.max(0, Math.min(nbtTags.getInteger("radius"), MekanismConfig.current().general.digitalMinerMaxRadius.val())));
        setMinY(nbtTags.getInteger("minY"));
        setMaxY(nbtTags.getInteger("maxY"));
        doEject = nbtTags.getBoolean("doEject");
        doPull = nbtTags.getBoolean("doPull");
        silkTouch = nbtTags.getBoolean("silkTouch");
        inverse = nbtTags.getBoolean("inverse");
        inverseRequiresReplacement = nbtTags.getBoolean("inverseRequiresReplacement");
        if (nbtTags.hasKey("inverseReplaceTarget", NBT.TAG_COMPOUND)) {
            setInverseReplaceTarget(new ItemStack(nbtTags.getCompoundTag("inverseReplaceTarget")));
        } else {
            inverseReplaceTarget = ItemStack.EMPTY;
        }
        filterManager.readFromNBT(nbtTags, MinerFilter::readFromNBT);
    }

    @Override
    public String getDataType() {
        return getBlockType().getTranslationKey() + "." + fullName + ".name";
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        ItemDataUtils.setBoolean(itemStack, "hasMinerConfig", true);

        ItemDataUtils.setInt(itemStack, "radius", radius);
        ItemDataUtils.setInt(itemStack, "minY", minY);
        ItemDataUtils.setInt(itemStack, "maxY", maxY);
        ItemDataUtils.setBoolean(itemStack, "doEject", doEject);
        ItemDataUtils.setBoolean(itemStack, "doPull", doPull);
        ItemDataUtils.setBoolean(itemStack, "silkTouch", silkTouch);
        ItemDataUtils.setBoolean(itemStack, "inverse", inverse);
        ItemDataUtils.setBoolean(itemStack, "inverseRequiresReplacement", inverseRequiresReplacement);
        if (!inverseReplaceTarget.isEmpty()) {
            ItemDataUtils.setCompound(itemStack, "inverseReplaceTarget", inverseReplaceTarget.writeToNBT(new NBTTagCompound()));
        }

        NBTTagCompound filterData = new NBTTagCompound();
        filterManager.writeToNBT(filterData, MinerFilter::write);
        if (filterData.hasKey(NBTConstants.FILTERS)) {
            ItemDataUtils.setList(itemStack, NBTConstants.FILTERS, filterData.getTagList(NBTConstants.FILTERS, NBT.TAG_COMPOUND));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, "hasMinerConfig")) {
            setRadius(Math.max(0, Math.min(ItemDataUtils.getInt(itemStack, "radius"), MekanismConfig.current().general.digitalMinerMaxRadius.val())));
            setMinY(ItemDataUtils.getInt(itemStack, "minY"));
            setMaxY(ItemDataUtils.getInt(itemStack, "maxY"));
            doEject = ItemDataUtils.getBoolean(itemStack, "doEject");
            doPull = ItemDataUtils.getBoolean(itemStack, "doPull");
            silkTouch = ItemDataUtils.getBoolean(itemStack, "silkTouch");
            inverse = ItemDataUtils.getBoolean(itemStack, "inverse");
            inverseRequiresReplacement = ItemDataUtils.getBoolean(itemStack, "inverseRequiresReplacement");
            if (ItemDataUtils.hasData(itemStack, "inverseReplaceTarget")) {
                setInverseReplaceTarget(new ItemStack(ItemDataUtils.getCompound(itemStack, "inverseReplaceTarget")));
            } else {
                inverseReplaceTarget = ItemStack.EMPTY;
            }

            if (ItemDataUtils.hasData(itemStack, "filters")) {
                filterManager.readFromNBTList(ItemDataUtils.getList(itemStack, "filters"), MinerFilter::readFromNBT);
            } else {
                filterManager.clear();
            }
        }
    }

    private void onFilterManagerChanged() {
        MekanismUtils.saveChunk(this);
    }

    @Override
    public SortableFilterManager<MinerFilter> getFilterManager() {
        return filterManager;
    }

    @Override
    public void sendFilterUpdate(@Nullable EntityPlayerMP player) {
        TileNetworkList filterPacket = getFilterPacket(new TileNetworkList());
        playersUsing.forEach(iterPlayer -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this, filterPacket), (EntityPlayerMP) iterPlayer));
        if (player != null && !playersUsing.contains(player)) {
            Mekanism.packetHandler.sendTo(new TileEntityMessage(this, filterPacket), player);
        }
    }

    public void addConfigContainerTrackers(MekanismContainer container) {
        container.track(SyncableInt.create(this::getRadius, this::setRadius));
        container.track(SyncableInt.create(() -> minY, this::setMinY));
        container.track(SyncableInt.create(() -> maxY, this::setMaxY));
        container.track(SyncableBoolean.create(() -> inverse, value -> inverse = value));
        container.track(SyncableBoolean.create(() -> inverseRequiresReplacement, value -> inverseRequiresReplacement = value));
        container.track(SyncableItemStack.create(this::getInverseReplaceTarget, this::setInverseReplaceTarget));
        filterManager.addContainerTrackers(container, FilterListType.MINER, MinerFilter::write);
    }

    public ItemStack getInverseReplaceTarget() {
        return inverseReplaceTarget;
    }

    public void setInverseReplaceTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty() || Block.getBlockFromItem(stack.getItem()) == Blocks.AIR) {
            inverseReplaceTarget = ItemStack.EMPTY;
        } else {
            inverseReplaceTarget = StackUtils.size(stack, 1);
        }
    }

    public boolean getInverseRequiresReplacement() {
        return inverseRequiresReplacement;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED) {
            delayLength = MekanismUtils.getTicks(this, BASE_DELAY);
        }
        if (!isRecalculatingAllUpgradables() && (upgrade == Upgrade.SPEED || upgrade == Upgrade.ENERGY)) {
            recalculateEnergyAndCapacity();
        }
    }

    @Override
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
        super.onAllUpgradablesRecalculated(upgrades);
        if (upgrades.contains(Upgrade.SPEED) || upgrades.contains(Upgrade.ENERGY)) {
            recalculateEnergyAndCapacity();
        }
    }

    private void recalculateEnergyAndCapacity() {
        energyUsage = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_USAGE);
        maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
        setEnergy(Math.min(getMaxEnergy(), getEnergy()));
    }

    @Override
    public boolean canBoundReceiveEnergy(BlockPos coord, EnumFacing side) {
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        if (coord.equals(getPos().offset(left))) {
            return side == left;
        } else if (coord.equals(getPos().offset(right))) {
            return side == right;
        }
        return false;
    }

    @Override
    public boolean canBoundOutPutEnergy(BlockPos location, EnumFacing side) {
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return side == MekanismUtils.getLeft(facing) || side == MekanismUtils.getRight(facing) || side == EnumFacing.DOWN;
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return false;
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        return automationType == AutomationType.EXTERNAL ? 0 : super.extract(amount, action, automationType);
    }

    @Override
    public double extract(double amount, EnumFacing side, Action action, AutomationType automationType) {
        return automationType == AutomationType.EXTERNAL ? 0 : super.extract(amount, side, action, automationType);
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return side == null ? super.pullEnergy(null, amount, simulate) : 0;
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean hasOffsetCapability(@Nonnull Capability<?> capability, EnumFacing side, @Nonnull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return false;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return getItemHandler(side) != null;
        } else if (isManagedStrictEnergy(capability)) {
            return getEnergyHandler(capability, side) != null;
        } else if (capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return hasCapability(capability, side);
    }

    @Override
    public <T> T getOffsetCapability(@Nonnull Capability<T> capability, EnumFacing side, @Nonnull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return null;
        } else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(getItemHandler(side));
        } else if (isManagedStrictEnergy(capability)) {
            return getEnergyHandler(capability, side);
        } else if (isTesla(capability, side)) {
            return (T) getTeslaEnergyWrapper(side);
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(getForgeEnergyWrapper(side));
        }
        return getCapability(capability, side);
    }

    @Override
    public boolean isOffsetCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side, @Nonnull Vec3i offset) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            //Input
            if (offset.equals(new Vec3i(0, 1, 0))) {
                //If input then disable if wrong face of input
                return side != EnumFacing.UP;
            }
            //Output
            EnumFacing back = facing.getOpposite();
            if (offset.equals(new Vec3i(back.getXOffset(), 1, back.getZOffset()))) {
                //If output then disable if wrong face of output
                return side != back;
            }
            return true;
        }
        if (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            if (offset.equals(Vec3i.NULL_VECTOR)) {
                //Disable if it is the bottom port but wrong side of it
                return side != EnumFacing.DOWN;
            }
            EnumFacing left = MekanismUtils.getLeft(facing);
            EnumFacing right = MekanismUtils.getRight(facing);
            if (offset.equals(new Vec3i(left.getXOffset(), 0, left.getZOffset()))) {
                //Disable if left power port but wrong side of the port
                return side != left;
            } else if (offset.equals(new Vec3i(right.getXOffset(), 0, right.getZOffset()))) {
                //Disable if right power port but wrong side of the port
                return side != right;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        //Return some capabilities as disabled, and handle them with offset capabilities instead
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        } else if (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    private boolean isManagedStrictEnergy(@Nonnull Capability<?> capability) {
        return capability == Capabilities.STRICT_ENERGY_CAPABILITY || isStrictEnergy(capability);
    }

    @Override
    public TileComponentChunkLoader getChunkLoader() {
        return chunkLoaderComponent;
    }

    @Override
    public Set<ChunkPos> getChunkSet() {
        if (chunkSet == null) {
            chunkSet = new Range4D(Coord4D.get(this)).expandFromCenter(radius).getIntersectingChunks().stream().map(Chunk3D::getPos).collect(Collectors.toSet());
        }
        return chunkSet;
    }

    @Nonnull
    @Override
    public BlockFaceShape getOffsetBlockFaceShape(@Nonnull EnumFacing face, @Nonnull Vec3i offset) {
        if (offset.equals(new Vec3i(0, 1, 0))) {
            return BlockFaceShape.SOLID;
        }
        EnumFacing back = facing.getOpposite();
        if (offset.equals(new Vec3i(back.getXOffset(), 1, back.getZOffset()))) {
            return BlockFaceShape.SOLID;
        }
        return BlockFaceShape.UNDEFINED;
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
        return this.getRadius() <= 64;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB machineBounds = super.getRenderBoundingBox();
        if (!clientRendering || !canDisplayVisuals()) {
            return machineBounds;
        }
        return machineBounds.union(getVisualizationRenderBoundingBox(getPos(), getRadius(), minY, maxY))
              .grow(IBoundingBlock.RENDER_BOUNDS_EPSILON);
    }

    public static AxisAlignedBB getVisualizationRenderBoundingBox(BlockPos pos, int radius, int minY, int maxY) {
        int clampedRadius = Math.max(0, Math.min(64, radius));
        int renderMinY = Math.min(minY, maxY);
        int renderMaxY = Math.max(minY, maxY);
        return new AxisAlignedBB(pos.getX() - clampedRadius, renderMinY, pos.getZ() - clampedRadius,
              pos.getX() + clampedRadius + 1D, renderMaxY + 1D, pos.getZ() + clampedRadius + 1D);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public List<Vec3d> computeOcclusionSamplePoints() {
        List<Vec3d> samplePoints = new ArrayList<>(super.computeOcclusionSamplePoints());
        if (clientRendering && canDisplayVisuals()) {
            samplePoints.addAll(cullingGetAabbOcclusionSamplePoints(getVisualizationRenderBoundingBox(getPos(), getRadius(), minY, maxY)));
        }
        return samplePoints;
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom&& MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderDigitalMiner(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderDigitalMiner", e);
                }
            }
        }
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelDigitalMiner.class;
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public ISpecialSelectionWireframeTile.SelectionTransform[] getSelectionWireframeTransforms(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing currentFacing = facing == null ? EnumFacing.NORTH : facing;
        return switch (currentFacing) {
            case SOUTH -> SELECTION_ROTATE_SOUTH;
            case WEST -> SELECTION_ROTATE_WEST;
            case EAST -> SELECTION_ROTATE_EAST;
            default -> SELECTION_ROTATE_NORTH;
        };
    }

    private static class ItemCount {

        private final ItemStack stack;
        private int count;

        private ItemCount(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
