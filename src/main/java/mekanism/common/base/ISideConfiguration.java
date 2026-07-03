package mekanism.common.base;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implement this if your TileEntity is capable of being modified by a Configurator in it's 'modify' mode.
 *
 * @author AidanBrady
 */
public interface ISideConfiguration extends IGetBackMachine{

    /**
     * Gets the tile's configuration component.
     *
     * @return the tile's configuration component
     */
    TileComponentConfig getConfig();

    /**
     * Gets this machine's current orientation.
     *
     * @return machine's current orientation
     */
    EnumFacing getOrientation();

    /**
     * Gets this machine's ejector.
     *
     * @return this machine's ejector
     */
    TileComponentEjector getEjector();

    @Nullable
    default DataType getActiveDataType(Object container) {
        ConfigInfo info = null;
        TileComponentConfig config = getConfig();
        if (config == null) {
            return null;
        }
        if (container instanceof IExtendedGasTank && config.supports(TransmissionType.GAS)) {
            info = config.getConfigInfo(TransmissionType.GAS);
        } else if (container instanceof IExtendedFluidTank && config.supports(TransmissionType.FLUID)) {
            info = config.getConfigInfo(TransmissionType.FLUID);
        } else if (container instanceof IInventorySlot && config.supports(TransmissionType.ITEM)) {
            info = config.getConfigInfo(TransmissionType.ITEM);
        }
        if (info != null) {
            List<DataType> types = info.getDataTypeForContainer(container);
            int count = types.size();
            //This checks there are data types for the container and that the mapping is narrower than every supported type except NONE.
            if (count > 0 && count < info.getSupportedDataTypes().size()) {
                return types.get(0);
            }
        }
        return null;
    }

}
