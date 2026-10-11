package mekanism.common.transmitters.grid;

import mekanism.api.Coord4D;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.content.transporter.PathfinderCache;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.content.transporter.TransporterPathfinder;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.*;

public class InventoryNetwork extends DynamicNetwork<TileEntity, InventoryNetwork, Void> {

    public InventoryNetwork() {
    }

    public InventoryNetwork(Collection<InventoryNetwork> networks) {
        networks.forEach(net -> {
            if (net != null) {
                adoptTransmittersAndAcceptorsFrom(net);
                net.deregister();
            }
        });
        register();
    }

    public List<AcceptorData> calculateAcceptors(TransitRequest request, TransporterStack stack) {
        return calculateAcceptors(request, stack, Collections.emptyMap());
    }

    public List<AcceptorData> calculateAcceptors(TransitRequest request, TransporterStack stack, Map<Coord4D, Set<TransporterStack>> additionalFlowingStacks) {
        return calculateAcceptors(request, stack, additionalFlowingStacks, null);
    }

    public List<AcceptorData> calculateAcceptors(TransitRequest request, TransporterStack stack,
                                                 Map<Coord4D, Set<TransporterStack>> additionalFlowingStacks, ILogisticalTransporter start) {
        List<AcceptorData> toReturn = new ArrayList<>();
        for (Coord4D coord : possibleAcceptors) {
            if (coord == null || coord.equals(stack.homeLocation)) {
                continue;
            }
            EnumSet<EnumFacing> sides = acceptorDirections.get(coord);
            if (sides == null || sides.isEmpty()) {
                continue;
            }
            TileEntity acceptor = coord.getTileEntity(getWorld());
            if (acceptor == null) {
                continue;
            }

            Map<TransitResponse, AcceptorData> responseData = new HashMap<>();
            for (EnumFacing side : sides) {
                EnumFacing opposite = side.getOpposite();
                List<Coord4D> checked = start == null ? null : TransporterPathfinder.preflightPath(start, coord, opposite, stack);
                if (checked == TransporterPathfinder.UNREACHABLE_PATH) continue;
                long revision = PathfinderCache.getRevision();
                TransitResponse response = TransporterManager.getPredictedInsert(acceptor, stack.color, request, opposite, additionalFlowingStacks);
                if (!response.isEmpty()) {
                    addAcceptorData(toReturn, responseData, coord, response, opposite);
                    if (checked != null && revision == PathfinderCache.getRevision())
                        responseData.get(response).prevalidate(checked, revision);
                }
            }
        }
        return toReturn;
    }

    static void addAcceptorData(List<AcceptorData> acceptors, Map<TransitResponse, AcceptorData> responseData,
                                Coord4D coord, TransitResponse response, EnumFacing side) {
        AcceptorData data = responseData.get(response);
        if (data == null) {
            data = new AcceptorData(coord, response, side);
            responseData.put(response, data);
            acceptors.add(data);
        } else {
            data.sides.add(side);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            //Future!
        }
    }

    @Override
    public void absorbBuffer(IGridTransmitter<TileEntity, InventoryNetwork, Void> transmitter) {
    }

    @Override
    public void clampBuffer() {
    }

    @Override
    public void updateCapacity() {
        //The capacity is always zero so no point in doing calculations.
    }

    @Override
    public String toString() {
        return "[InventoryNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
    }

    @Override
    public String getNeededInfo() {
        return null;
    }

    @Override
    public String getStoredInfo() {
        return null;
    }

    @Override
    public String getFlowInfo() {
        return null;
    }

    public static class AcceptorData {

        private Coord4D location;
        private TransitResponse response;
        private Set<EnumFacing> sides;
        private List<Coord4D> prevalidatedPath;
        private long pathRevision;

        private void prevalidate(List<Coord4D> path, long revision) {
            if (prevalidatedPath == null || pathRevision != revision || path.size() < prevalidatedPath.size()) {
                prevalidatedPath = path;
                pathRevision = revision;
            }
        }

        public boolean isPrevalidated(List<Coord4D> path) {
            return path == prevalidatedPath && pathRevision == PathfinderCache.getRevision();
        }

        public AcceptorData(Coord4D coord, TransitResponse ret, EnumFacing side) {
            location = coord;
            response = ret;
            sides = EnumSet.of(side);
        }

        public TransitResponse getResponse() {
            return response;
        }

        public Coord4D getLocation() {
            return location;
        }

        public Set<EnumFacing> getSides() {
            return sides;
        }
    }
}
