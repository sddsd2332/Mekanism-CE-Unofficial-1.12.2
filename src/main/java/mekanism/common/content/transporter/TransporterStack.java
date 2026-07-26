package mekanism.common.content.transporter;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.transporter.TransporterPathfinder.Destination;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TransporterUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class TransporterStack {

    public ItemStack itemStack = ItemStack.EMPTY;

    public int progress;

    public EnumColor color = null;

    public boolean initiatedPath = false;

    public EnumFacing idleDir = null;
    public Coord4D originalLocation;
    public Coord4D homeLocation;
    private Coord4D clientNext;
    private Coord4D clientPrev;
    private Path pathType;
    private List<Coord4D> pathToTarget = new ArrayList<>();
    private int pathIndex = -1;

    public static TransporterStack readFromNBT(NBTTagCompound nbtTags) {
        TransporterStack stack = new TransporterStack();
        stack.read(nbtTags);
        return stack;
    }

    public static TransporterStack readFromPacket(ByteBuf dataStream) {
        TransporterStack stack = new TransporterStack();
        stack.read(dataStream);
        return stack;
    }

    public void write(ILogisticalTransporter transporter, TileNetworkList data) {
        if (color != null) {
            data.add(TransporterUtils.colors.indexOf(color));
        } else {
            data.add(-1);
        }

        data.add(progress);
        originalLocation.write(data);
        data.add(pathType.ordinal());

        if (getPathIndex(transporter.coord()) > 0) {
            data.add(true);
            getNext(transporter).write(data);
        } else {
            data.add(false);
        }

        getPrev(transporter).write(data);
        data.add(itemStack);
    }

    public void read(ByteBuf dataStream) {
        pathToTarget = new ArrayList<>();
        pathIndex = -1;
        initiatedPath = false;
        int c = dataStream.readInt();
        if (c != -1) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, c, null);
        } else {
            color = null;
        }

        progress = dataStream.readInt();
        originalLocation = Coord4D.read(dataStream);
        pathType = MekanismUtils.getByIndex(Path.values(), dataStream.readInt(), Path.NONE);

        if (dataStream.readBoolean()) {
            clientNext = Coord4D.read(dataStream);
        }
        clientPrev = Coord4D.read(dataStream);
        itemStack = PacketHandler.readStack(dataStream);
    }

    public void write(NBTTagCompound nbtTags) {
        if (color != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
        }

        nbtTags.setInteger("progress", progress);
        nbtTags.setTag("originalLocation", originalLocation.write(new NBTTagCompound()));

        if (idleDir != null) {
            nbtTags.setInteger("idleDir", idleDir.ordinal());
        }
        if (homeLocation != null) {
            nbtTags.setTag("homeLocation", homeLocation.write(new NBTTagCompound()));
        }
        nbtTags.setInteger("pathType", pathType.ordinal());
        itemStack.writeToNBT(nbtTags);
    }

    public void read(NBTTagCompound nbtTags) {
        pathToTarget = new ArrayList<>();
        pathIndex = -1;
        initiatedPath = false;
        if (nbtTags.hasKey("color")) {
            color = MekanismUtils.getByIndex(TransporterUtils.colors, nbtTags.getInteger("color"), null);
        }

        progress = nbtTags.getInteger("progress");
        originalLocation = Coord4D.read(nbtTags.getCompoundTag("originalLocation"));

        if (nbtTags.hasKey("idleDir")) {
            idleDir = MekanismUtils.getByIndex(EnumFacing.VALUES, nbtTags.getInteger("idleDir"), null);
        }
        if (nbtTags.hasKey("homeLocation")) {
            homeLocation = Coord4D.read(nbtTags.getCompoundTag("homeLocation"));
        }
        pathType = MekanismUtils.getByIndex(Path.values(), nbtTags.getInteger("pathType"), Path.NONE);
        itemStack = new ItemStack(nbtTags);
    }

    public void setPath(List<Coord4D> path, Path type) {
        setPath(path, type, true);
    }

    public void setPath(List<Coord4D> path, Path type, boolean updateFlowing) {
        //Make sure old path isn't null
        if (updateFlowing && pathType != Path.NONE) {
            TransporterManager.remove(this);
        }
        pathToTarget = path;
        pathType = type;
        pathIndex = path == null || path.isEmpty() ? -1 : path.size() - 1;
        if (updateFlowing && pathType != Path.NONE) {
            TransporterManager.add(this);
        }
    }

    public boolean hasPath() {
        return pathToTarget != null && pathToTarget.size() >= 2;
    }

    public List<Coord4D> getPath() {
        return pathToTarget;
    }

    public Path getPathType() {
        return pathType;
    }

    public TransitResponse recalculatePath(TransitRequest request, ILogisticalTransporter transporter, int min) {
        return recalculatePath(request, transporter, min, true);
    }

    public TransitResponse recalculatePath(TransitRequest request, ILogisticalTransporter transporter, int min, boolean updateFlowing) {
        return recalculatePath(request, transporter, min, updateFlowing, Collections.emptyMap());
    }

    public TransitResponse recalculatePath(TransitRequest request, ILogisticalTransporter transporter, int min, Map<Coord4D, Set<TransporterStack>> additionalFlowingStacks) {
        return recalculatePath(request, transporter, min, false, additionalFlowingStacks);
    }

    private TransitResponse recalculatePath(TransitRequest request, ILogisticalTransporter transporter, int min, boolean updateFlowing,
          Map<Coord4D, Set<TransporterStack>> additionalFlowingStacks) {
        Destination newPath = TransporterPathfinder.getNewBasePath(transporter, this, request, min, additionalFlowingStacks);
        if (newPath == null) {
            return request.getEmptyResponse();
        }
        idleDir = null;
        setPath(newPath.getPath(), Path.DEST, updateFlowing);
        initiatedPath = true;
        return newPath.getResponse();
    }

    public TransitResponse recalculateRRPath(TransitRequest request, IAdvancedTransportEjector outputter, ILogisticalTransporter transporter, int min) {
        return recalculateRRPath(request, outputter, transporter, min, true);
    }

    public TransitResponse recalculateRRPath(TransitRequest request, IAdvancedTransportEjector outputter, ILogisticalTransporter transporter, int min,
          boolean updateFlowing) {
        Destination newPath = TransporterPathfinder.getNewRRPath(transporter, this, request, outputter, min);
        if (newPath == null) {
            return request.getEmptyResponse();
        }
        idleDir = null;
        setPath(newPath.getPath(), Path.DEST, updateFlowing);
        initiatedPath = true;
        return newPath.getResponse();
    }

    public boolean calculateIdle(ILogisticalTransporter transporter) {
        Pair<List<Coord4D>, Path> newPath = TransporterPathfinder.getIdlePath(transporter, this);
        if (newPath == null) {
            return false;
        }
        if (newPath.getRight() == Path.HOME) {
            idleDir = null;
        }
        setPath(newPath.getLeft(), newPath.getRight());
        originalLocation = transporter.coord();
        initiatedPath = true;
        return true;
    }

    public boolean isFinal(ILogisticalTransporter transporter) {
        return getPathIndex(transporter.coord()) == (pathType == Path.NONE ? 0 : 1);
    }

    /**
     * Resolves the current position against the reversed path. Normal movement advances one entry
     * toward index zero, while the full scan is retained as a recovery path after reloads or reroutes.
     */
    public int getPathIndex(Coord4D current) {
        if (current == null || pathToTarget == null || pathToTarget.isEmpty()) {
            pathIndex = -1;
            return -1;
        }
        if (pathIndex >= 0 && pathIndex < pathToTarget.size()) {
            if (pathToTarget.get(pathIndex).equals(current)) {
                return pathIndex;
            }
            if (pathIndex > 0 && pathToTarget.get(pathIndex - 1).equals(current)) {
                return --pathIndex;
            }
        }
        pathIndex = pathToTarget.indexOf(current);
        return pathIndex;
    }

    public Coord4D getNext(ILogisticalTransporter transporter) {
        if (!transporter.world().isRemote) {
            int index = getPathIndex(transporter.coord()) - 1;
            if (index < 0) {
                return null;
            }
            return pathToTarget.get(index);
        }
        return clientNext;
    }

    public Coord4D getPrev(ILogisticalTransporter transporter) {
        if (!transporter.world().isRemote) {
            int currentIndex = getPathIndex(transporter.coord());
            if (currentIndex < 0) {
                return originalLocation;
            }
            int index = currentIndex + 1;
            if (index < pathToTarget.size()) {
                return pathToTarget.get(index);
            }
            return originalLocation;
        }
        return clientPrev;
    }

    public EnumFacing getSide(ILogisticalTransporter transporter) {
        EnumFacing side = null;
        if (progress < 50) {
            Coord4D prev = getPrev(transporter);
            if (prev != null) {
                side = transporter.coord().sideDifference(prev);
            }
        } else {
            Coord4D next = getNext(transporter);
            if (next != null) {
                side = next.sideDifference(transporter.coord());
            }
        }
        //sideDifference can return null
        //TODO: Look into implications further about what side should be returned.
        // This is mainly to stop a crash I randomly encountered but was unable to reproduce.
        // (I believe the difference returns null when it is the "same" transporter somehow or something)
        return side == null ? EnumFacing.DOWN : side;
    }

    public boolean canInsertToTransporter(TileEntity tileEntity, EnumFacing from) {
        EnumFacing opposite = from.getOpposite();
        ILogisticalTransporter transporter = CapabilityUtils.getCapability(tileEntity, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, opposite);
        if (transporter != null && CapabilityUtils.getCapability(tileEntity, Capabilities.BLOCKABLE_CONNECTION_CAPABILITY, opposite).canConnectMutual(opposite)) {
            return transporter.getColor() == color || transporter.getColor() == null;
        }
        return false;
    }

    public boolean canInsertToTransporter(ILogisticalTransporter transporter, EnumFacing side) {
        return transporter.canConnectMutual(side.getOpposite()) && (transporter.getColor() == color || transporter.getColor() == null);
    }

    public Coord4D getDest() {
        return pathToTarget.get(0);
    }

    public EnumFacing getSideOfDest() {
        if (hasPath()) {
            Coord4D lastTransporter = pathToTarget.get(1);
            return lastTransporter.sideDifference(getDest());
        }
        return null;
    }

    public enum Path {
        DEST,
        HOME,
        NONE;

        public boolean hasTarget() {
            return this != NONE;
        }

        public boolean noTarget() {
            return this == NONE;
        }

        public boolean isHome() {
            return this == HOME;
        }
    }
}
