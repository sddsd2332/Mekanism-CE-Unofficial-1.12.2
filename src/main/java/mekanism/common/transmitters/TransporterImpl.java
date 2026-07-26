package mekanism.common.transmitters;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.content.transporter.TransporterStack.Path;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tier.TransporterTier;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporter;
import mekanism.common.tile.transmitter.TileEntitySidedPipe.ConnectionType;
import mekanism.common.transmitters.grid.InventoryNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TransporterUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;

public class TransporterImpl extends TransmitterImpl<TileEntity, InventoryNetwork, Void> implements ILogisticalTransporter {

    private Int2ObjectMap<TransporterStack> transit = new Int2ObjectOpenHashMap<>();

    private int nextId = 0;

    private EnumColor color;

    private Int2ObjectMap<TransporterStack> needsSync = new Int2ObjectOpenHashMap<>();
    private TransporterTier tier = TransporterTier.BASIC;
    private int delay;
    private int delayCount;

    public TransporterImpl(TileEntityLogisticalTransporter multiPart) {
        super(multiPart);
    }

    public Collection<TransporterStack> getTransit() {
        return Collections.unmodifiableCollection(transit.values());
    }

    public TransporterTier getTier() {
        return tier;
    }

    public void setTier(TransporterTier tier) {
        this.tier = tier;
    }

    public int getSpeed() {
        return tier.getSpeed();
    }

    public int getPullAmount() {
        return tier.getPullAmount();
    }

    public void deleteStack(int id) {
        transit.remove(id);
    }

    public void addStack(int id, TransporterStack s) {
        transit.put(id, s);
    }

    public void writeToPacket(TileNetworkList data) {
        data.add(transit.size());
        transit.forEach((key, value) -> {
            data.add(key);
            value.write(this, data);
        });
    }

    public void readFromPacket(ByteBuf dataStream) {
        transit.clear();
        int count = dataStream.readInt();
        for (int i = 0; i < count; i++) {
            int id = dataStream.readInt();
            TransporterStack s = TransporterStack.readFromPacket(dataStream);
            transit.put(id, s);
        }
    }

    public void readCustomNBT(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey("tier")) {
            setTier(MekanismUtils.getByIndex(TransporterTier.values(), nbtTags.getInteger("tier"), tier));
        }
        if (nbtTags.hasKey("color")) {
            setColor(MekanismUtils.getByIndex(TransporterUtils.colors, nbtTags.getInteger("color"), null));
        }
        if (nbtTags.hasKey("stacks")) {
            NBTTagList tagList = nbtTags.getTagList("stacks", NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                TransporterStack stack = TransporterStack.readFromNBT(tagList.getCompoundTagAt(i));
                transit.put(nextId++, stack);
            }
        }
    }

    public void writeCustomNBT(NBTTagCompound nbtTags) {
        nbtTags.setInteger("tier", tier.ordinal());
        if (getColor() != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(getColor()));
        }
        NBTTagList stacks = new NBTTagList();
        getTransit().forEach(stack -> {
            NBTTagCompound tagCompound = new NBTTagCompound();
            stack.write(tagCompound);
            stacks.appendTag(tagCompound);
        });
        if (stacks.tagCount() != 0) {
            nbtTags.setTag("stacks", stacks);
        }
    }

    public void update() {
        if (world().isRemote) {
            transit.values().forEach(stack -> stack.progress = Math.min(100, stack.progress + getSpeed()));
        } else if (getTransmitterNetwork() != null) {
            IntSet deletes = null;
            pullItems();
            Coord4D coord = coord();
            for (Entry<Integer, TransporterStack> entry : transit.entrySet()) {
                int stackId = entry.getKey();
                TransporterStack stack = entry.getValue();
                if (!stack.initiatedPath) {
                    if (stack.itemStack.isEmpty() || !recalculate(stackId, stack, null)) {
                        deletes = addDelete(deletes, stackId);
                        continue;
                    }
                }

                stack.progress += getSpeed();
                if (stack.progress >= 100) {
                    Coord4D prevSet = null;
                    if (stack.hasPath()) {
                        int currentIndex = stack.getPathIndex(coord);
                        if (currentIndex == 0) { //Necessary for transition reasons, not sure why
                            deletes = addDelete(deletes, stackId);
                            continue;
                        }

                        if (currentIndex > 0) {
                            Coord4D next = stack.getPath().get(currentIndex - 1);
                            if (!stack.isFinal(this)) {
                                if (next != null) {
                                    TileEntity tile = next.getTileEntity(world());
                                    if (stack.canInsertToTransporter(tile, stack.getSide(this))) {
                                        ILogisticalTransporter nextTile = CapabilityUtils.getCapability(tile, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, null);
                                        nextTile.entityEntering(stack, stack.progress % 100);
                                        deletes = addDelete(deletes, stackId);
                                        continue;
                                    }
                                    prevSet = next;
                                }
                            } else if (stack.getPathType() != Path.NONE) {
                                TileEntity tile = next.getTileEntity(world());
                                if (tile != null) {
                                    TransitResponse response = InventoryUtils.putStackInInventory(tile, TransitRequest.getFromTransport(stack), stack.getSide(this),
                                          stack.getPathType() == Path.HOME);
                                    // Nothing was rejected; remove the stack from the prediction tracker and
                                    // schedule this stack for deletion. Continue the loop thereafter
                                    ItemStack rejected = response.getRejected(stack.itemStack);
                                    if (rejected.isEmpty()) {
                                        TransporterManager.remove(stack);
                                        deletes = addDelete(deletes, stackId);
                                        continue;
                                    }
                                    // Some portion of the stack got rejected; save the remainder and
                                    // let the recalculate below sort out what to do next
                                    stack.itemStack = rejected;
                                    prevSet = next;
                                }
                            }
                        }
                    }
                    if (!recalculate(stackId, stack, prevSet)) {
                        deletes = addDelete(deletes, stackId);
                    } else if (prevSet != null) {
                        stack.progress = 0;
                    } else {
                        stack.progress = 50;
                    }
                } else if (stack.progress == 50) {
                    boolean tryRecalculate;
                    if (stack.isFinal(this)) {
                        tryRecalculate = checkPath(stack, Path.DEST, false) || checkPath(stack, Path.HOME, true) || stack.getPathType() == Path.NONE;
                    } else {
                        Coord4D next = stack.getNext(this);
                        tryRecalculate = next == null || !stack.canInsertToTransporter(next.getTileEntity(world()), stack.getSide(this));
                    }
                    if (tryRecalculate && !recalculate(stackId, stack, null)) {
                        deletes = addDelete(deletes, stackId);
                    }
                }
            }

            if (deletes != null || !needsSync.isEmpty()) {
                IntSet deletedStacks = deletes == null ? IntSets.EMPTY_SET : deletes;
                TileEntityMessage msg = new TileEntityMessage(coord, getTileEntity().makeBatchPacket(needsSync, deletedStacks));
                // Now remove any entries from transit that have been deleted
                if (deletes != null) {
                    deletes.forEach(id -> transit.remove(id));
                }

                // Clear the pending sync packets
                needsSync.clear();

                // Finally, notify clients and mark chunk for save
                Mekanism.packetHandler.sendToAllTracking(msg, coord);
                MekanismUtils.saveChunk(getTileEntity());
            }
        }
    }

    private static IntSet addDelete(IntSet deletes, int stackId) {
        if (deletes == null) {
            deletes = new IntOpenHashSet();
        }
        deletes.add(stackId);
        return deletes;
    }

    private void pullItems() {
        if (delay > 0) {
            delay--;
            return;
        }
        delay = 3;
        TileEntityLogisticalTransporter tileEntity = getTileEntity();
        for (EnumFacing side : EnumFacing.VALUES) {
            if (tileEntity.getConnectionType(side) != ConnectionType.PULL) {
                continue;
            }
            TileEntity tile = MekanismUtils.getTileEntity(world(), tileEntity.getPos().offset(side));
            if (tile != null) {
                TransitRequest request = TransitRequest.buildInventoryMap(tile, side, getPullAmount());
                if (!request.isEmpty()) {
                    TransitResponse response = TransporterUtils.insert(tile, this, request, getColor(), true, 0);
                    if (!response.isEmpty()) {
                        response.useAll();
                        delay = 10;
                    } else {
                        delayCount++;
                        delay = Math.min(40, (int) Math.exp(delayCount));
                    }
                }
            }
        }
    }

    private boolean checkPath(TransporterStack stack, Path dest, boolean home) {
        return stack.getPathType() == dest && (!checkSideForInsert(stack) || !InventoryUtils.canInsert(stack.getDest().getTileEntity(world()), stack.color, stack.itemStack,
                stack.getSide(this), home));
    }

    private boolean checkSideForInsert(TransporterStack stack) {
        EnumFacing side = stack.getSide(this);
        return getTileEntity().getConnectionType(side).canSendTo();
    }

    private boolean recalculate(int stackId, TransporterStack stack, Coord4D from) {
        boolean noPath = stack.getPathType() == Path.NONE;
        if (!noPath) {
            noPath = stack.recalculatePath(TransitRequest.getFromTransport(stack), this, 0).isEmpty();
        }
        if (noPath && !stack.calculateIdle(this)) {
            TransporterUtils.drop(this, stack);
            return false;
        }

        //Only add to needsSync if true is being returned; otherwise it gets added to deletes
        needsSync.put(stackId, stack);
        if (from != null) {
            stack.originalLocation = from;
        }
        return true;
    }

    @Override
    public TransitResponse insert(Coord4D original, TransitRequest request, EnumColor color, boolean doEmit, int min) {
        EnumFacing from = coord().sideDifference(original);
        if (from == null || !canReceiveFrom(null, from.getOpposite())) {
            return request.getEmptyResponse();
        }
        TransporterStack stack = createInsertStack(original, color);
        if (!stack.canInsertToTransporter(this, from)) {
            return request.getEmptyResponse();
        }
        TransitResponse response = stack.recalculatePath(request, this, min);
        return updateTransit(doEmit, stack, response);
    }

    @Override
    public TransitResponse insertMaybeRR(IAdvancedTransportEjector outputter, Coord4D outputterCoord, TransitRequest request, EnumColor color, boolean doEmit, int min) {
        if (outputter != null && outputter.getRoundRobin()) {
            return insert(outputter, outputterCoord, request, color, doEmit, min);
        }
        return insert(outputterCoord, request, color, doEmit, min);
    }

    private TransitResponse insert(IAdvancedTransportEjector outputter, Coord4D outputterCoord, TransitRequest request, EnumColor color, boolean doEmit, int min) {
        EnumFacing from = coord().sideDifference(outputterCoord);
        if (from == null || !canReceiveFrom(null, from.getOpposite())) {
            return request.getEmptyResponse();
        }
        TransporterStack stack = createInsertStack(outputterCoord, color);
        if (!stack.canInsertToTransporter(this, from)) {
            return request.getEmptyResponse();
        }
        TransitResponse response = stack.recalculateRRPath(request, outputter, this, min, doEmit);
        return updateTransit(doEmit, stack, response);
    }

    @Override
    public TransitResponse insertUnchecked(Coord4D outputterCoord, TransitRequest request, EnumColor color, boolean doEmit, int min) {
        TransporterStack stack = createInsertStack(outputterCoord, color);
        TransitResponse response = stack.recalculatePath(request, this, min);
        return updateTransit(doEmit, stack, response);
    }

    @Nonnull
    private TransitResponse updateTransit(boolean doEmit, TransporterStack stack, TransitResponse response) {
        if (!response.isEmpty()) {
            stack.itemStack = response.getStack();
            if (doEmit) {
                int stackId = nextId++;
                transit.put(stackId, stack);
                Coord4D coord = coord();
                Mekanism.packetHandler.sendToAllTracking(new TileEntityMessage(coord, getTileEntity().makeSyncPacket(stackId, stack)), coord);
                MekanismUtils.saveChunk(getTileEntity());
            }
            return response;
        }
        return response;
    }

    @Override
    public TransporterStack createInsertStack(Coord4D outputterCoord, EnumColor color) {
        TransporterStack stack = new TransporterStack();
        stack.originalLocation = outputterCoord;
        stack.homeLocation = outputterCoord;
        stack.color = color;
        return stack;
    }

    @Override
    public void entityEntering(TransporterStack stack, int progress) {
        // Update the progress of the stack and add it as something that's both
        // in transit and needs sync down to the client.
        //
        // This code used to generate a sync message at this point, but that was a LOT
        // of bandwidth in a busy server, so by adding to needsSync, the sync will happen
        // in a batch on a per-tick basis.
        int stackId = nextId++;
        stack.getPathIndex(coord());
        stack.progress = progress;
        transit.put(stackId, stack);
        needsSync.put(stackId, stack);

        // N.B. We are not marking the chunk as dirty here! I don't believe it's needed, since
        // the next tick will generate the necessary save and if we crash before the next tick,
        // it's unlikely the data will be save anyways (since chunks aren't saved until the end of
        // a tick).
    }

    @Override
    public EnumColor getColor() {
        return color;
    }

    @Override
    public void setColor(EnumColor c) {
        color = c;
    }

    @Override
    public boolean canEmitTo(TileEntity tileEntity, EnumFacing side) {
        if (!getTileEntity().canConnect(side)) {
            return false;
        }
        return getTileEntity().getConnectionType(side).canSendTo();
    }

    @Override
    public boolean canReceiveFrom(TileEntity tileEntity, EnumFacing side) {
        if (!getTileEntity().canConnect(side)) {
            return false;
        }
        return getTileEntity().getConnectionType(side).canAccept();
    }

    @Override
    public double getCost() {
        return getTileEntity().getCost();
    }

    @Override
    public boolean canConnectMutual(EnumFacing side) {
        return getTileEntity().canConnectMutual(side);
    }

    @Override
    public boolean canConnect(EnumFacing side) {
        return getTileEntity().canConnect(side);
    }

    @Override
    public TileEntityLogisticalTransporter getTileEntity() {
        return (TileEntityLogisticalTransporter) containingTile;
    }
}
