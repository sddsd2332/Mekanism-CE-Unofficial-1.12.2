package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.content.filter.SortableFilterManager;
import mekanism.common.content.transporter.TItemStackFilter;
import mekanism.common.content.transporter.TOreDictFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.InternalInventorySlot;
import mekanism.common.lib.SidedBlockPos;
import mekanism.common.lib.inventory.Finder;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityEffectsBlock;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;

public class TileEntityLogisticalSorter extends TileEntityEffectsBlock implements IRedstoneControl, ISpecialConfigData, ISustainedData, ISecurityTile,
        IComputerIntegration, IUpgradeTile, IComparatorSupport, IAdvancedTransportEjector, ITileFilterHolder<TransporterFilter> {

    private final SortableFilterManager<TransporterFilter> filterManager = new SortableFilterManager<>(TransporterFilter.class, this::onFilterManagerChanged);
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public EnumColor color;
    public boolean autoEject;
    public boolean roundRobin;
    public boolean singleItem;
    @Nullable
    private SidedBlockPos rrTarget;
    public int delayTicks;
    public TileComponentUpgrade upgradeComponent;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    public String[] methods = {"setDefaultColor", "setRoundRobin", "setAutoEject", "addFilter", "removeFilter", "addOreFilter", "removeOreFilter", "setSingleItem"};
    private int currentRedstoneLevel;

    public TileEntityLogisticalSorter() {
        super("machine.logisticalsorter", "LogisticalSorter", MachineType.LOGISTICAL_SORTER.getStorage(), 3);
        initializeInventorySlots();
        doAutoSync = false;
        upgradeComponent = new TileComponentUpgrade(this);
        clearSupportedUpgrades();
        setSupportedUpgrade(Upgrade.MUFFLING);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        builder.addSlot(InternalInventorySlot.create(listener), RelativeSide.FRONT);
        return builder.build();
    }

    @Override
    public boolean persistInventory() {
        // The internal slot only exists so transporters can connect visually.
        return false;
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        delayTicks = Math.max(0, delayTicks - 1);
        if (delayTicks == 6) {
            setActive(false);
        }

        if (MekanismUtils.canFunction(this) && delayTicks == 0) {
            TileEntity back = Coord4D.get(this).offset(facing.getOpposite()).getTileEntity(world);
            TileEntity front = Coord4D.get(this).offset(facing).getTileEntity(world);
            //If there is no tile to pull from or the push to, skip doing any checks
            if (InventoryUtils.isItemHandler(back, facing) && front != null) {
                boolean sentItems = false;

                outer:
                for (TransporterFilter filter : filterManager.getEnabledFilters()) {
                    TransitRequest request = filter.mapInventory(back, facing, singleItem);
                    if (request.isEmpty()) {
                        continue;
                    }
                    int min = !singleItem && filter instanceof TItemStackFilter itemFilter && itemFilter.sizeMode ? itemFilter.min : 0;

                    TransitResponse response = emitItemToTransporter(front, request, filter.color, min);
                    if (!response.isEmpty()) {
                        response.useAll();
                        back.markDirty();
                        setActive(true);
                        sentItems = true;
                        break outer;
                    }
                }

                if (!sentItems && autoEject) {
                    TransitRequest request = TransitRequest.definedItem(back, facing, singleItem ? 1 : 64, new StrictFilterFinder());
                    TransitResponse response = emitItemToTransporter(front, request, color, 0);
                    if (!response.isEmpty()) {
                        response.useAll();
                        back.markDirty();
                        setActive(true);
                    }
                }
            }

            delayTicks = 10;
        }
        if (!playersUsing.isEmpty()) {
            playersUsing.forEach(player ->  Mekanism.packetHandler.sendTo(new TileEntityMessage(this, getGenericPacket(new TileNetworkList())), (EntityPlayerMP) player));
        }

        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public TransitResponse emitItemToTransporter(TileEntity front, TransitRequest request, EnumColor filterColor, int min) {
        if (CapabilityUtils.hasCapability(front, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, facing.getOpposite())) {
            ILogisticalTransporter transporter = CapabilityUtils.getCapability(front, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, facing.getOpposite());
            return TransporterUtils.insertMaybeRR(this, this, transporter, request, filterColor, true, min);
        }
        return InventoryUtils.putStackInInventory(front, request, facing, false);
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("controlType", controlType.ordinal());

        if (color != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
        }

        nbtTags.setBoolean("autoEject", autoEject);
        nbtTags.setBoolean("roundRobin", roundRobin);
        nbtTags.setBoolean("singleItem", singleItem);

        SidedBlockPos rrTarget = getRoundRobinTarget();
        if (rrTarget != null) {
            nbtTags.setTag(NBTConstants.ROUND_ROBIN_TARGET, rrTarget.serialize());
        }

        filterManager.writeToNBT(nbtTags, TransporterFilter::write);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        if (nbtTags.hasKey("color")) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, nbtTags.getInteger("color"), null);
        }

        autoEject = nbtTags.getBoolean("autoEject");
        roundRobin = nbtTags.getBoolean("roundRobin");
        singleItem = nbtTags.getBoolean("singleItem");

        if (nbtTags.hasKey(NBTConstants.ROUND_ROBIN_TARGET)) {
            setRoundRobinTarget(SidedBlockPos.deserialize(nbtTags.getCompoundTag(NBTConstants.ROUND_ROBIN_TARGET)));
        } else {
            setRoundRobinTarget((SidedBlockPos) null);
        }
        filterManager.readFromNBT(nbtTags, TransporterFilter::readFromNBT);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                int clickType = dataStream.readInt();
                if (clickType == 0) {
                    color = TransporterUtils.increment(color);
                } else if (clickType == 1) {
                    color = TransporterUtils.decrement(color);
                } else if (clickType == 2) {
                    color = null;
                }
            } else if (type == 1) {
                autoEject = !autoEject;
            } else if (type == 2) {
                toggleRoundRobin();
            } else if (type == 3) {
                // Move filter up
                int filterIndex = dataStream.readInt();
                if (filterManager.moveUp(filterIndex)) {
                    playersUsing.forEach(this::openInventory);
                }
            } else if (type == 4) {
                // Move filter down
                int filterIndex = dataStream.readInt();
                if (filterManager.moveDown(filterIndex)) {
                    playersUsing.forEach(this::openInventory);
                }
            } else if (type == 7) {
                int filterIndex = dataStream.readInt();
                if (filterManager.moveToTop(filterIndex)) {
                    playersUsing.forEach(this::openInventory);
                }
            } else if (type == 8) {
                int filterIndex = dataStream.readInt();
                if (filterManager.moveToBottom(filterIndex)) {
                    playersUsing.forEach(this::openInventory);
                }
            } else if (type == 5) {
                singleItem = !singleItem;
            } else if (type == 6) {
                filterManager.toggleState(dataStream.readInt());
                sendFilterUpdate(null);
            }
            return;
        }

        boolean wasActive = isActive;
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            int type = dataStream.readInt();

            if (type == 0) {
                readState(dataStream);
                readFilters(dataStream);
            } else if (type == 1) {
                readState(dataStream);
            } else if (type == 2) {
                readFilters(dataStream);
            }
            if (wasActive != isActive) {
                //TileEntityEffectsBlock only updates it if it was not recently turned off.
                // (This is soo that lighting updates do not cause lag)
                // The sorter gets toggled a lot we need to make sure to update it anyways
                // so that the light on the side of it (the texture) updates properly.
                // We do not need to worry about block lighting updates causing lag as
                // #lightUpdate() returns false meaning that logistical sorters do not give
                // off actual light.
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    private void readState(ByteBuf dataStream) {
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
        int c = dataStream.readInt();
        if (c != -1) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, c, null);
        } else {
            color = null;
        }
        autoEject = dataStream.readBoolean();
        roundRobin = dataStream.readBoolean();
        singleItem = dataStream.readBoolean();
    }

    private void readFilters(ByteBuf dataStream) {
        filterManager.readFromPacket(dataStream, TransporterFilter::readFromPacket);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(0);
        data.add(controlType.ordinal());
        if (color != null) {
            data.add(TransporterUtils.colors.indexOf(color));
        } else {
            data.add(-1);
        }

        data.add(autoEject);
        data.add(roundRobin);
        data.add(singleItem);

        filterManager.writeToPacket(data, TransporterFilter::write);
        return data;
    }

    public TileNetworkList getGenericPacket(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(1);
        data.add(controlType.ordinal());
        if (color != null) {
            data.add(TransporterUtils.colors.indexOf(color));
        } else {
            data.add(-1);
        }

        data.add(autoEject);
        data.add(roundRobin);
        data.add(singleItem);
        return data;
    }

    public TileNetworkList getFilterPacket(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(2);
        filterManager.writeToPacket(data, TransporterFilter::write);
        return data;
    }

    @Override
    @Nullable
    public SidedBlockPos getRoundRobinTarget() {
        return rrTarget;
    }

    @Override
    public void setRoundRobinTarget(@Nullable SidedBlockPos target) {
        rrTarget = target;
    }

    @Override
    public boolean getRoundRobin() {
        return roundRobin;
    }

    @Override
    public void toggleRoundRobin() {
        roundRobin = !roundRobin;
        setRoundRobinTarget((SidedBlockPos) null);
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canSendHome(ItemStack stack) {
        TileEntity back = Coord4D.get(this).offset(facing.getOpposite()).getTileEntity(world);
        return InventoryUtils.canInsert(back, null, stack, facing.getOpposite(), true);
    }

    public boolean hasConnectedInventory() {
        if (world == null) {
            return false;
        }
        TileEntity tile = Coord4D.get(this).offset(facing.getOpposite()).getTileEntity(world);
        return TransporterUtils.isValidAcceptorOnSide(tile, facing.getOpposite());
    }

    public TransitResponse sendHome(ItemStack stack) {
        return sendHome(TransitRequest.getFromStack(stack));
    }

    @Override
    public TransitResponse sendHome(TransitRequest request) {
        TileEntity back = Coord4D.get(this).offset(facing.getOpposite()).getTileEntity(world);
        return request.addToInventory(back, facing.getOpposite(), 0, false);
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
        if (!isRemote()) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
    }

    @Override
    public boolean canPulse() {
        return true;
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return false;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return true;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        if (color != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
        }
        nbtTags.setBoolean("autoEject", autoEject);
        nbtTags.setBoolean("roundRobin", roundRobin);
        nbtTags.setBoolean("singleItem", singleItem);

        filterManager.writeToNBT(nbtTags, TransporterFilter::write);
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey("color")) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, nbtTags.getInteger("color"), null);
        }
        autoEject = nbtTags.getBoolean("autoEject");
        roundRobin = nbtTags.getBoolean("roundRobin");
        singleItem = nbtTags.getBoolean("singleItem");
        filterManager.readFromNBT(nbtTags, TransporterFilter::readFromNBT);
    }

    @Override
    public String getDataType() {
        return getBlockType().getTranslationKey() + "." + fullName + ".name";
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        ItemDataUtils.setBoolean(itemStack, "hasSorterConfig", true);
        if (color != null) {
            ItemDataUtils.setInt(itemStack, "color", TransporterUtils.colors.indexOf(color));
        }

        ItemDataUtils.setBoolean(itemStack, "autoEject", autoEject);
        ItemDataUtils.setBoolean(itemStack, "roundRobin", roundRobin);
        ItemDataUtils.setBoolean(itemStack, "singleItem", singleItem);

        NBTTagCompound filterData = new NBTTagCompound();
        filterManager.writeToNBT(filterData, TransporterFilter::write);
        if (filterData.hasKey(NBTConstants.FILTERS)) {
            ItemDataUtils.setList(itemStack, NBTConstants.FILTERS, filterData.getTagList(NBTConstants.FILTERS, NBT.TAG_COMPOUND));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, "hasSorterConfig")) {
            if (ItemDataUtils.hasData(itemStack, "color")) {
                color = MekanismUtils.getByIndex(TransporterUtils.colors, ItemDataUtils.getInt(itemStack, "color"), null);
            }
            autoEject = ItemDataUtils.getBoolean(itemStack, "autoEject");
            roundRobin = ItemDataUtils.getBoolean(itemStack, "roundRobin");
            singleItem = ItemDataUtils.getBoolean(itemStack, "singleItem");
            if (ItemDataUtils.hasData(itemStack, "filters")) {
                filterManager.readFromNBTList(ItemDataUtils.getList(itemStack, "filters"), TransporterFilter::readFromNBT);
            } else {
                filterManager.clear();
            }
        }
    }

    private void onFilterManagerChanged() {
        markDirty();
    }

    @Override
    public SortableFilterManager<TransporterFilter> getFilterManager() {
        return filterManager;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(() -> autoEject, value -> autoEject = value));
        container.track(SyncableBoolean.create(() -> roundRobin, value -> roundRobin = value));
        container.track(SyncableBoolean.create(() -> singleItem, value -> singleItem = value));
        container.track(SyncableInt.create(() -> color == null ? -1 : TransporterUtils.colors.indexOf(color),
              value -> color = MekanismUtils.getByIndex(TransporterUtils.colors, value, null)));
        filterManager.addContainerTrackers(container, FilterListType.TRANSPORTER, TransporterFilter::write);
    }

    @Override
    public void sendFilterUpdate(@Nullable EntityPlayerMP player) {
        TileNetworkList filterPacket = getFilterPacket(new TileNetworkList());
        playersUsing.forEach(iterPlayer -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this, filterPacket), (EntityPlayerMP) iterPlayer));
        if (player != null && !playersUsing.contains(player)) {
            Mekanism.packetHandler.sendTo(new TileEntityMessage(this, filterPacket), player);
        }
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (arguments.length > 0) {
            if (method == 0) {
                if (!(arguments[0] instanceof String)) {
                    return new Object[]{"Invalid parameters."};
                }
                color = EnumColor.getFromDyeName((String) arguments[0]);
                if (color == null) {
                    return new Object[]{"Default color set to null"};
                }
                return new Object[]{"Default color set to " + color.dyeName};
            } else if (method == 1) {
                if (!(arguments[0] instanceof Boolean)) {
                    return new Object[]{"Invalid parameters."};
                }
                roundRobin = (Boolean) arguments[0];
                return new Object[]{"Round-robin mode set to " + roundRobin};
            } else if (method == 2) {
                if (!(arguments[0] instanceof Boolean)) {
                    return new Object[]{"Invalid parameters."};
                }
                autoEject = (Boolean) arguments[0];
                return new Object[]{"Auto-eject mode set to " + autoEject};
            } else if (method == 3) {
                if (arguments.length != 6 || !(arguments[0] instanceof String) || !(arguments[1] instanceof Double) ||
                        !(arguments[2] instanceof String) || !(arguments[3] instanceof Boolean) ||
                        !(arguments[4] instanceof Double) || !(arguments[5] instanceof Double)) {
                    return new Object[]{"Invalid parameters."};
                }
                TItemStackFilter filter = new TItemStackFilter();
                filter.setItemStack(new ItemStack(Item.getByNameOrId((String) arguments[0]), 1, ((Double) arguments[1]).intValue()));
                filter.color = EnumColor.getFromDyeName((String) arguments[2]);
                filter.sizeMode = (Boolean) arguments[3];
                filter.min = ((Double) arguments[4]).intValue();
                filter.max = ((Double) arguments[5]).intValue();
                filterManager.addFilter(filter);
                return new Object[]{"Added filter."};
            } else if (method == 4) {
                if (arguments.length != 2 || !(arguments[0] instanceof String) || !(arguments[1] instanceof Double)) {
                    return new Object[]{"Invalid parameters."};
                }
                ItemStack stack = new ItemStack(Item.getByNameOrId((String) arguments[0]), 1, ((Double) arguments[1]).intValue());
                Iterator<TransporterFilter> iter = filterManager.getFilters().iterator();
                while (iter.hasNext()) {
                    TransporterFilter filter = iter.next();
                    if (filter instanceof TItemStackFilter) {
                        if (StackUtils.equalsWildcard(((TItemStackFilter) filter).getItemStack(), stack)) {
                            filterManager.removeFilter(filter);
                            return new Object[]{"Removed filter."};
                        }
                    }
                }
                return new Object[]{"Couldn't find filter."};
            } else if (method == 5) {
                if (arguments.length != 2 || !(arguments[0] instanceof String) || !(arguments[1] instanceof String)) {
                    return new Object[]{"Invalid parameters."};
                }
                TOreDictFilter filter = new TOreDictFilter();
                filter.setOreDictName((String) arguments[0]);
                filter.color = EnumColor.getFromDyeName((String) arguments[1]);
                filterManager.addFilter(filter);
                return new Object[]{"Added filter."};
            } else if (method == 6) {
                if (arguments.length != 1 || !(arguments[0] instanceof String ore)) {
                    return new Object[]{"Invalid parameters."};
                }
                Iterator<TransporterFilter> iter = filterManager.getFilters().iterator();
                while (iter.hasNext()) {
                    TransporterFilter filter = iter.next();
                    if (filter instanceof TOreDictFilter) {
                        if (((TOreDictFilter) filter).getOreDictName().equals(ore)) {
                            filterManager.removeFilter(filter);
                            return new Object[]{"Removed filter."};
                        }
                    }
                }
                return new Object[]{"Couldn't find filter."};
            } else if (method == 7) {
                if (!(arguments[0] instanceof Boolean)) {
                    return new Object[]{"Invalid parameters."};
                }
                singleItem = (Boolean) arguments[0];
                return new Object[]{"Single-item mode set to " + singleItem};
            }
        }
        playersUsing.forEach(player ->  Mekanism.packetHandler.sendTo(new TileEntityMessage(this, getGenericPacket(new TileNetworkList())), (EntityPlayerMP) player));
        return null;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return side != null && side != facing && side != facing.getOpposite();
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public int getRedstoneLevel() {
        return isActive ? 15 : 0;
    }


    private class StrictFilterFinder implements Finder {

        @Override
        public boolean modifies(ItemStack stack) {
            for (TransporterFilter filter : filterManager.getEnabledFilters()) {
                if (filter.canFilter(stack, false) && !filter.allowDefault) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }
}
