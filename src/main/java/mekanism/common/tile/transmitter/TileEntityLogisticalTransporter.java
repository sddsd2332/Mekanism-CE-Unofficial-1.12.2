package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.block.property.PropertyColor;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.item.CursedTransporterItemHandler;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import mekanism.common.content.transporter.PathfinderCache;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.integration.multipart.MultipartTileNetworkJoiner;
import mekanism.common.tier.AlloyTier;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.TransporterTier;
import mekanism.common.transmitters.TransporterImpl;
import mekanism.common.transmitters.grid.InventoryNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TextComponentGroup;
import mekanism.common.util.TransporterUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TileEntityLogisticalTransporter extends TileEntityTransmitter<TileEntity, InventoryNetwork, Void> {

    private final int SYNC_PACKET = 1;
    private final int BATCH_PACKET = 2;

    public TileEntityLogisticalTransporter() {
        transmitterDelegate = new TransporterImpl(this);
        addCapabilityResolver(new TransporterCapabilityResolver());
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        // In-transit item models can reach the block faces and extend beyond them.
        return new AxisAlignedBB(getPos()).grow(0.5D);
    }

    @Override
    public BaseTier getBaseTier() {
        return getTransmitter().getTier().getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        getTransmitter().setTier(TransporterTier.get(baseTier));
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.LOGISTICAL_TRANSPORTER;
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.ITEM;
    }

    @Override
    public void onWorldSeparate() {
        super.onWorldSeparate();
        if (!getWorld().isRemote) {
            PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
        }
    }

    @Override
    public TileEntity getCachedAcceptor(EnumFacing side) {
        return getCachedTile(side);
    }

    public boolean exposesInsertCap(EnumFacing side) {
        return side != null && connectionTypes[side.ordinal()].canAccept();
    }

    @Override
    public boolean isValidTransmitter(TileEntity tileEntity) {
        ILogisticalTransporter transporter = CapabilityUtils.getCapability(tileEntity, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, null);
        if (getTransmitter().getColor() == null || transporter.getColor() == null || getTransmitter().getColor() == transporter.getColor()) {
            return super.isValidTransmitter(tileEntity);
        }
        return false;
    }

    @Override
    public boolean isValidAcceptor(TileEntity tile, EnumFacing side) {
        return TransporterUtils.isValidAcceptorOnSide(tile, side);
    }

    @Override
    public boolean handlesRedstone() {
        return false;
    }

    @Override
    public void doRestrictedTick() {
        super.doRestrictedTick();
        getTransmitter().update();
    }

    @Override
    public void onWorldJoin() {
        super.onWorldJoin();
        PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
    }

    @Override
    public void refreshConnections() {
        invalidateCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        super.refreshConnections();
    }

    @Override
    public void refreshConnections(EnumFacing side) {
        invalidateCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        super.refreshConnections(side);
    }

    @Override
    protected void onModeChange(EnumFacing side) {
        invalidateCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        super.onModeChange(side);
    }

    @Override
    public InventoryNetwork createNewNetwork() {
        return new InventoryNetwork();
    }

    @Override
    public InventoryNetwork createNetworkByMerging(Collection<InventoryNetwork> networks) {
        return new InventoryNetwork(networks);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) throws Exception {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            int type = dataStream.readInt();
            if (type == 0) {
                super.handlePacketData(dataStream);
                getTransmitter().setTier(MekanismUtils.getByIndex(TransporterTier.values(), dataStream.readInt(), getTransmitter().getTier()));
                int c = dataStream.readInt();
                EnumColor prev = getTransmitter().getColor();
                if (c != -1) {
                    getTransmitter().setColor(MekanismUtils.getByIndex(TransporterUtils.colors, c, null));
                } else {
                    getTransmitter().setColor(null);
                }
                if (prev != getTransmitter().getColor()) {
                    MekanismUtils.updateBlock(world, pos);
                }
                getTransmitter().readFromPacket(dataStream);
            } else if (type == SYNC_PACKET) {
                readStack(dataStream);
            } else if (type == BATCH_PACKET) {
                int updates = dataStream.readInt();
                for (int i = 0; i < updates; i++) {
                    readStack(dataStream);
                }
                int deletes = dataStream.readInt();
                for (int i = 0; i < deletes; i++) {
                    getTransmitter().deleteStack(dataStream.readInt());
                }
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(0);
        super.getNetworkedData(data);
        data.add(getTransmitter().getTier().ordinal());
        if (getTransmitter().getColor() != null) {
            data.add(TransporterUtils.colors.indexOf(getTransmitter().getColor()));
        } else {
            data.add(-1);
        }

        // Serialize all the in-flight stacks (this includes their ID)
        getTransmitter().writeToPacket(data);
        return data;
    }

    public TileNetworkList makeSyncPacket(int stackId, TransporterStack stack) {
        TileNetworkList data = new TileNetworkList();
        if (Mekanism.hooks.MCMPLoaded) {
            MultipartTileNetworkJoiner.addMultipartHeader(this, data, null);
        }
        data.add(SYNC_PACKET);
        data.add(stackId);
        stack.write(getTransmitter(), data);
        return data;
    }

    public TileNetworkList makeBatchPacket(Int2ObjectMap<TransporterStack> updates, IntSet deletes) {
        TileNetworkList data = new TileNetworkList();
        if (Mekanism.hooks.MCMPLoaded) {
            MultipartTileNetworkJoiner.addMultipartHeader(this, data, null);
        }
        data.add(BATCH_PACKET);
        data.add(updates.size());
        updates.forEach((key, value) -> {
            data.add(key);
            value.write(getTransmitter(), data);
        });
        data.add(deletes.size());
        data.addAll(deletes);
        return data;
    }


    private void readStack(ByteBuf dataStream) {
        int id = dataStream.readInt();
        TransporterStack stack = TransporterStack.readFromPacket(dataStream);
        if (stack.progress == 0) {
            stack.progress = 5;
        }
        getTransmitter().addStack(id, stack);
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        getTransmitter().readCustomNBT(nbtTags);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        getTransmitter().writeCustomNBT(nbtTags);
    }

    @Override
    protected EnumActionResult onConfigure(EntityPlayer player, int part, EnumFacing side) {
        TransporterUtils.incrementColor(getTransmitter());
        onPartChanged();
        PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
        Mekanism.packetHandler.sendUpdatePacket(this);
        TextComponentGroup msg = new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG + " ", TextFormatting.DARK_BLUE)
                .translation("tooltip.configurator.toggleColor").string(": ");

        if (getTransmitter().getColor() != null) {
            msg.appendSibling(getTransmitter().getColor().getTranslatedColouredComponent());
        } else {
            msg.translation("gui.none");
        }
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        super.onRightClick(player, side);
        TextComponentGroup msg = new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG + " ", TextFormatting.DARK_BLUE)
                .translation("tooltip.configurator.viewColor").string(": ");

        if (getTransmitter().getColor() != null) {
            msg.appendSibling(getTransmitter().getColor().getTranslatedColouredComponent());
        } else {
            msg.translation("gui.none");
        }
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumColor getRenderColor() {
        return getTransmitter().getColor();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        invalidateCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (!getWorld().isRemote) {
            getTransmitter().getTransit().forEach(stack -> TransporterUtils.drop(getTransmitter(), stack));
        }
    }


    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public Void getBuffer() {
        return null;
    }

    @Override
    public void takeShare() {
    }

    @Override
    public void updateShare() {
    }

    @Override
    public TransporterImpl getTransmitter() {
        return (TransporterImpl) transmitterDelegate;
    }

    public double getCost() {
        return (double) TransporterTier.ULTIMATE.getSpeed() / (double) getTransmitter().getSpeed();
    }

    @Override
    public boolean upgrade(AlloyTier tierOrdinal) {
        TransporterTier tier = getTransmitter().getTier();
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal.ordinal() == tier.ordinal()) {
            getTransmitter().setTier(TransporterTier.values()[tier.ordinal() + 1]);
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public IBlockState getExtendedState(IBlockState state) {
        return ((IExtendedBlockState) super.getExtendedState(state)).withProperty(PropertyColor.INSTANCE, new PropertyColor(getRenderColor()));
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY) {
            return Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY.cast(getTransmitter());
        }
        return super.getCapability(capability, side);
    }

    private class TransporterCapabilityResolver implements ICapabilityResolver {

        private final List<Capability<?>> supportedCapabilities = Collections.singletonList(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        private final Map<EnumFacing, CursedTransporterItemHandler> cursedHandlers = new EnumMap<>(EnumFacing.class);
        private final Map<EnumFacing, IItemHandler> handlers = new EnumMap<>(EnumFacing.class);

        @Nonnull
        @Override
        public List<Capability<?>> getSupportedCapabilities() {
            return supportedCapabilities;
        }

        @Override
        public boolean canResolve(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
            return supports(capability) && side != null && exposesInsertCap(side);
        }

        @Nullable
        @Override
        public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
            if (side == null || !exposesInsertCap(side)) {
                return null;
            }
            IItemHandler handler = handlers.get(side);
            if (handler == null) {
                handler = cursedHandlers.computeIfAbsent(side, s -> new CursedTransporterItemHandler(getTransmitter(), Coord4D.get(TileEntityLogisticalTransporter.this).offset(s),
                      () -> world == null ? -1 : world.getTotalWorldTime(), () -> exposesInsertCap(s)));
                handlers.put(side, handler);
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler);
        }

        @Override
        public void invalidate(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
            if (side == null) {
                invalidateAll();
            } else {
                handlers.remove(side);
            }
        }

        @Override
        public void invalidateAll() {
            handlers.clear();
        }
    }
}
