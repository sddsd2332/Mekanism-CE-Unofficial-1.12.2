package mekanism.api.heat;

import mekanism.api.IContentsListener;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;

public interface IMekanismHeatHandler extends ISidedHeatHandler, IContentsListener {

    default boolean canHandleHeat() {
        return true;
    }

    @Override
    default int getHeatCapacitorCount(@Nullable EnumFacing side) {
        return getHeatCapacitors(side).size();
    }

    List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side);

    @Nullable
    default IHeatCapacitor getHeatCapacitor(int capacitor, @Nullable EnumFacing side) {
        List<IHeatCapacitor> capacitors = getHeatCapacitors(side);
        return capacitor >= 0 && capacitor < capacitors.size() ? capacitors.get(capacitor) : null;
    }

    @Override
    default double getTemperature(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.AMBIENT_TEMP : heatCapacitor.getTemperature();
    }

    @Override
    default double getInverseConduction(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.DEFAULT_INVERSE_CONDUCTION : heatCapacitor.getInverseConduction();
    }

    @Override
    default double getHeatCapacity(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.DEFAULT_HEAT_CAPACITY : heatCapacitor.getHeatCapacity();
    }

    @Override
    default void handleHeat(int capacitor, double transfer, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        if (heatCapacitor != null) {
            heatCapacitor.handleHeat(transfer);
        }
    }

    default double getInverseInsulation(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.DEFAULT_INVERSE_INSULATION : heatCapacitor.getInverseInsulation();
    }

    default double getTotalInverseInsulation(@Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.isEmpty()) {
            return HeatAPI.DEFAULT_INVERSE_INSULATION;
        } else if (heatCapacitors.size() == 1) {
            return heatCapacitors.get(0).getInverseInsulation();
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(heatCapacitors);
        for (IHeatCapacitor capacitor : heatCapacitors) {
            sum += capacitor.getInverseInsulation() * (capacitor.getHeatCapacity() / totalCapacity);
        }
        return sum;
    }

    @Override
    default double getTotalTemperature(@Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.isEmpty()) {
            return 0;
        } else if (heatCapacitors.size() == 1) {
            return heatCapacitors.get(0).getTemperature();
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(heatCapacitors);
        for (IHeatCapacitor capacitor : heatCapacitors) {
            sum += capacitor.getTemperature() * (capacitor.getHeatCapacity() / totalCapacity);
        }
        return sum;
    }

    @Override
    default double getTotalInverseConductionCoefficient(@Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.isEmpty()) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        } else if (heatCapacitors.size() == 1) {
            return heatCapacitors.get(0).getInverseConduction();
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(heatCapacitors);
        for (IHeatCapacitor capacitor : heatCapacitors) {
            sum += capacitor.getInverseConduction() * (capacitor.getHeatCapacity() / totalCapacity);
        }
        return sum;
    }

    @Override
    default double getTotalHeatCapacity(@Nullable EnumFacing side) {
        return getTotalHeatCapacity(getHeatCapacitors(side));
    }

    static double getTotalHeatCapacity(List<IHeatCapacitor> capacitors) {
        if (capacitors.size() == 1) {
            return capacitors.get(0).getHeatCapacity();
        }
        double sum = 0;
        for (IHeatCapacitor capacitor : capacitors) {
            sum += capacitor.getHeatCapacity();
        }
        return sum;
    }

    @Override
    default void handleHeat(double transfer, @Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.size() == 1) {
            heatCapacitors.get(0).handleHeat(transfer);
        } else if (!heatCapacitors.isEmpty()) {
            double totalHeatCapacity = getTotalHeatCapacity(heatCapacitors);
            for (IHeatCapacitor heatCapacitor : heatCapacitors) {
                heatCapacitor.handleHeat(transfer * (heatCapacitor.getHeatCapacity() / totalHeatCapacity));
            }
        }
    }
}
