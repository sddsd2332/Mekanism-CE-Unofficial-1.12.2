package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.OreDictCache;
import mekanism.common.PacketHandler;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.filter.FilterManager;
import mekanism.common.content.filter.IFilter;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TileEntityOredictionificator extends TileEntityContainerBlock implements IRedstoneControl, ISpecialConfigData, ISustainedData, ISecurityTile, ISideConfiguration, ITileFilterHolder<TileEntityOredictionificator.OredictionificatorFilter> {


    public static List<String> possibleFilters = Arrays.asList(MekanismConfig.current().general.validOredictionificatorFilters.get());
    private final FilterManager<OredictionificatorFilter> filterManager = new FilterManager<>(OredictionificatorFilter.class, this::onFilterManagerChanged);
    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;

    public boolean didProcess;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private InputInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;

    public TileEntityOredictionificator() {
        super(MachineType.OREDICTIONIFICATOR.getBlockName());
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, outputSlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
        doAutoSync = false;
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> !getResult(stack).isEmpty(), stack -> getValidName(stack) != null, listener, 56, 115));
        outputSlot = builder.addSlot(OutputInventorySlot.at(listener, 164, 115));
        return builder.build();
    }
@Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (!playersUsing.isEmpty()) {
            playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this, getGenericPacket(new TileNetworkList())), (EntityPlayerMP) player));
        }
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        didProcess = false;
        ItemStack inputStack = inputSlot.getStack();
        if (MekanismUtils.canFunction(this) && !inputStack.isEmpty() && getValidName(inputStack) != null) {
            ItemStack result = getResult(inputStack);
            if (!result.isEmpty()) {
                ItemStack outputStack = outputSlot.getStack();
                if (outputStack.isEmpty()) {
                    inputSlot.shrinkStack(1, Action.EXECUTE);
                    outputSlot.setStack(result);
                    didProcess = true;
                } else if (ItemHandlerHelper.canItemStacksStack(outputStack, result) && outputStack.getCount() < outputSlot.getLimit(outputStack)) {
                    inputSlot.shrinkStack(1, Action.EXECUTE);
                    outputSlot.growStack(1, Action.EXECUTE);
                    didProcess = true;
                }
                markNoUpdateSync();
            }
        }
    }

    public String getValidName(ItemStack stack) {
        List<String> def = OreDictCache.getOreDictName(stack);
        for (String s : def) {
            for (String pre : possibleFilters) {
                if (s.startsWith(pre)) {
                    return s;
                }
            }
        }
        return null;
    }

    public ItemStack getResult(ItemStack stack) {
        String s = getValidName(stack);
        if (s == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> ores = OreDictionary.getOres(s, false);
        for (OredictionificatorFilter filter : filterManager.getEnabledFilters()) {
            if (filter.filter.equals(s)) {
                if (ores.size() - 1 >= filter.index) {
                    return StackUtils.size(ores.get(filter.index), 1);
                }
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("controlType", controlType.ordinal());
        filterManager.writeToNBT(nbtTags, OredictionificatorFilter::write);

    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        filterManager.readFromNBT(nbtTags, OredictionificatorFilter::readFromNBT);

        //to fix any badly placed blocks in the world
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            facing = EnumFacing.NORTH;
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                filterManager.toggleState(dataStream.readInt());
                sendFilterUpdate(null);
            }
            return;
        }
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            int type = dataStream.readInt();
            if (type == 0) {
                controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
                didProcess = dataStream.readBoolean();
                filterManager.readFromPacket(dataStream, OredictionificatorFilter::readFromPacket);
            } else if (type == 1) {
                controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
                didProcess = dataStream.readBoolean();
            } else if (type == 2) {
                filterManager.readFromPacket(dataStream, OredictionificatorFilter::readFromPacket);
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(0);
        data.add(controlType.ordinal());
        data.add(didProcess);
        filterManager.writeToPacket(data, OredictionificatorFilter::write);
        return data;
    }

    public TileNetworkList getGenericPacket(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(1);
        data.add(controlType.ordinal());
        data.add(didProcess);
        return data;
    }

    public TileNetworkList getFilterPacket(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(2);
        filterManager.writeToPacket(data, OredictionificatorFilter::write);
        return data;
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
        if (!isRemote()) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        filterManager.writeToNBT(nbtTags, OredictionificatorFilter::write);
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        filterManager.readFromNBT(nbtTags, OredictionificatorFilter::readFromNBT);
    }

    @Override
    public String getDataType() {
        return getBlockType().getTranslationKey() + "." + fullName + ".name";
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        ItemDataUtils.setBoolean(itemStack, "hasOredictionificatorConfig", true);
        NBTTagCompound filterData = new NBTTagCompound();
        filterManager.writeToNBT(filterData, OredictionificatorFilter::write);
        if (filterData.hasKey(NBTConstants.FILTERS)) {
            ItemDataUtils.setList(itemStack, NBTConstants.FILTERS, filterData.getTagList(NBTConstants.FILTERS, NBT.TAG_COMPOUND));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, "hasOredictionificatorConfig")) {
            if (ItemDataUtils.hasData(itemStack, "filters")) {
                filterManager.readFromNBTList(ItemDataUtils.getList(itemStack, "filters"), OredictionificatorFilter::readFromNBT);
            } else {
                filterManager.clear();
            }
        }
    }

    private void onFilterManagerChanged() {
        markDirty();
    }

    @Override
    public FilterManager<OredictionificatorFilter> getFilterManager() {
        return filterManager;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(() -> didProcess, value -> didProcess = value));
        filterManager.addContainerTrackers(container, FilterListType.OREDICTIONIFICATOR, OredictionificatorFilter::write);
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
    public TileComponentSecurity getSecurity() {
        return securityComponent;
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
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }



    public static class OredictionificatorFilter implements IFilter {

        public String filter;
        public int index;
        private boolean enabled = true;

        public static OredictionificatorFilter readFromNBT(NBTTagCompound nbtTags) {
            OredictionificatorFilter filter = new OredictionificatorFilter();
            filter.read(nbtTags);
            return filter;
        }

        public static OredictionificatorFilter readFromPacket(ByteBuf dataStream) {
            OredictionificatorFilter filter = new OredictionificatorFilter();
            filter.read(dataStream);
            return filter;
        }

        public void write(NBTTagCompound nbtTags) {
            nbtTags.setBoolean(NBTConstants.ENABLED, enabled);
            nbtTags.setString("filter", filter);
            nbtTags.setInteger("index", index);
        }

        protected void read(NBTTagCompound nbtTags) {
            enabled = !nbtTags.hasKey(NBTConstants.ENABLED) || nbtTags.getBoolean(NBTConstants.ENABLED);
            filter = nbtTags.getString("filter");
            index = nbtTags.getInteger("index");
        }

        public void write(TileNetworkList data) {
            data.add(enabled);
            data.add(filter);
            data.add(index);
        }

        protected void read(ByteBuf dataStream) {
            enabled = dataStream.readBoolean();
            filter = PacketHandler.readString(dataStream);
            index = dataStream.readInt();
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public OredictionificatorFilter clone() {
            OredictionificatorFilter newFilter = new OredictionificatorFilter();
            newFilter.filter = filter;
            newFilter.index = index;
            newFilter.enabled = enabled;
            return newFilter;
        }

        @Override
        public int hashCode() {
            int code = 1;
            code = 31 * code + (enabled ? 1 : 0);
            code = 31 * code + Objects.hashCode(filter);
            code = 31 * code + index;
            return code;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof OredictionificatorFilter ore && ore.enabled == enabled && ore.index == index && Objects.equals(ore.filter, filter);
        }
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public EnumFacing getOrientation() {
        return facing;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }
}
