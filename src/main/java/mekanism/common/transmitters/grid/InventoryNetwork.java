package mekanism.common.transmitters.grid;

import mekanism.api.Coord4D;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.content.transporter.TransporterManager;
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

            AcceptorData data = null;
            for (EnumFacing side : sides) {
                EnumFacing opposite = side.getOpposite();
                TransitResponse response = TransporterManager.getPredictedInsert(acceptor, stack.color, request, opposite, additionalFlowingStacks);
                if (!response.isEmpty()) {
                    if (data == null) {
                        toReturn.add(data = new AcceptorData(coord, response, opposite));
                    } else {
                        data.sides.add(opposite);
                    }
                }
            }
        }
        return toReturn;
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
