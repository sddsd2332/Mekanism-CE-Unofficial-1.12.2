package mekanism.common.base;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.transmitters.IBlockableConnection;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.transmitters.grid.InventoryNetwork;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

public interface ILogisticalTransporter extends IGridTransmitter<TileEntity, InventoryNetwork, Void>, IBlockableConnection {

    TransitResponse insert(Coord4D original, TransitRequest request, EnumColor color, boolean doEmit, int min);

    TransitResponse insertMaybeRR(IAdvancedTransportEjector outputter, Coord4D outputterCoord, TransitRequest request, EnumColor color, boolean doEmit, int min);

    TransitResponse insertUnchecked(Coord4D outputterCoord, TransitRequest request, EnumColor color, boolean doEmit, int min);

    TransporterStack createInsertStack(Coord4D outputterCoord, EnumColor color);

    void entityEntering(TransporterStack stack, int progress);

    EnumColor getColor();

    void setColor(EnumColor c);

    boolean canEmitTo(TileEntity tileEntity, EnumFacing side);

    boolean canReceiveFrom(TileEntity tileEntity, EnumFacing side);

    double getCost();
}
