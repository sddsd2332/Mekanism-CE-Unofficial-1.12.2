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

    @Override
    default Object getHeatIdentity(@Nullable EnumFacing side) {
        List<IHeatCapacitor> capacitors = getHeatCapacitors(side);
        return capacitors.size() == 1 ? capacitors.get(0).getHeatIdentity() : this;
    }

    @Nullable
    default IHeatCapacitor getHeatCapacitor(int capacitor, @Nullable EnumFacing side) {
        List<IHeatCapacitor> capacitors = getHeatCapacitors(side);
        return capacitor >= 0 && capacitor < capacitors.size() ? capacitors.get(capacitor) : null;
    }

    @Override
    default double getTemperature(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.AMBIENT_TEMP : HeatAPI.sanitizeTemperature(heatCapacitor.getTemperature());
    }

    @Override
    default double getInverseConduction(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.DEFAULT_INVERSE_CONDUCTION : HeatAPI.sanitizeInverseConduction(heatCapacitor.getInverseConduction());
    }

    @Override
    default double getHeatCapacity(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.DEFAULT_HEAT_CAPACITY : HeatAPI.sanitizeHeatCapacity(heatCapacitor.getHeatCapacity());
    }

    @Override
    default void handleHeat(int capacitor, double transfer, @Nullable EnumFacing side) {
        if (!HeatAPI.isFinite(transfer) || Math.abs(transfer) <= HeatAPI.EPSILON) {
            return;
        }
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        if (heatCapacitor != null) {
            heatCapacitor.handleHeat(transfer);
        }
    }

    default double getInverseInsulation(int capacitor, @Nullable EnumFacing side) {
        IHeatCapacitor heatCapacitor = getHeatCapacitor(capacitor, side);
        return heatCapacitor == null ? HeatAPI.DEFAULT_INVERSE_INSULATION : HeatAPI.sanitizeInverseInsulation(heatCapacitor.getInverseInsulation());
    }

    default double getTotalInverseInsulation(@Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.isEmpty()) {
            return HeatAPI.DEFAULT_INVERSE_INSULATION;
        } else if (heatCapacitors.size() == 1) {
            return HeatAPI.sanitizeInverseInsulation(heatCapacitors.get(0).getInverseInsulation());
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(heatCapacitors);
        if (totalCapacity < 1) {
            return HeatAPI.DEFAULT_INVERSE_INSULATION;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitors);
        double totalWeight = getTotalCapacityWeight(heatCapacitors, maxCapacity);
        if (totalWeight <= 0) {
            return HeatAPI.DEFAULT_INVERSE_INSULATION;
        }
        for (IHeatCapacitor capacitor : heatCapacitors) {
            double capacity = capacitor.getHeatCapacity();
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeInverseInsulation(capacitor.getInverseInsulation()) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeInverseInsulation(sum);
    }

    @Override
    default double getTotalTemperature(@Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.isEmpty()) {
            return HeatAPI.AMBIENT_TEMP;
        } else if (heatCapacitors.size() == 1) {
            return HeatAPI.sanitizeTemperature(heatCapacitors.get(0).getTemperature());
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(heatCapacitors);
        if (totalCapacity < 1) {
            return HeatAPI.AMBIENT_TEMP;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitors);
        double totalWeight = getTotalCapacityWeight(heatCapacitors, maxCapacity);
        if (totalWeight <= 0) {
            return HeatAPI.AMBIENT_TEMP;
        }
        for (IHeatCapacitor capacitor : heatCapacitors) {
            double capacity = capacitor.getHeatCapacity();
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeTemperature(capacitor.getTemperature()) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeTemperature(sum);
    }

    @Override
    default double getTotalInverseConductionCoefficient(@Nullable EnumFacing side) {
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.isEmpty()) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        } else if (heatCapacitors.size() == 1) {
            return HeatAPI.sanitizeInverseConduction(heatCapacitors.get(0).getInverseConduction());
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(heatCapacitors);
        if (totalCapacity < 1) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitors);
        double totalWeight = getTotalCapacityWeight(heatCapacitors, maxCapacity);
        if (totalWeight <= 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        for (IHeatCapacitor capacitor : heatCapacitors) {
            double capacity = capacitor.getHeatCapacity();
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeInverseConduction(capacitor.getInverseConduction()) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeInverseConduction(sum);
    }

    @Override
    default double getTotalHeatCapacity(@Nullable EnumFacing side) {
        return getTotalHeatCapacity(getHeatCapacitors(side));
    }

    static double getTotalHeatCapacity(List<IHeatCapacitor> capacitors) {
        if (capacitors.size() == 1) {
            return HeatAPI.sanitizeHeatCapacity(capacitors.get(0).getHeatCapacity());
        }
        double sum = 0;
        for (IHeatCapacitor capacitor : capacitors) {
            double capacity = capacitor.getHeatCapacity();
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                if (capacity >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += capacity;
            }
        }
        return sum;
    }

    static double getMaxHeatCapacity(List<IHeatCapacitor> capacitors) {
        double maxCapacity = 0;
        for (IHeatCapacitor capacitor : capacitors) {
            double capacity = capacitor.getHeatCapacity();
            if (HeatAPI.isFinite(capacity) && capacity > maxCapacity) {
                maxCapacity = capacity;
            }
        }
        return maxCapacity;
    }

    static double getTotalCapacityWeight(List<IHeatCapacitor> capacitors, double maxCapacity) {
        double totalWeight = 0;
        for (IHeatCapacitor capacitor : capacitors) {
            totalWeight += HeatAPI.getCapacityWeight(capacitor.getHeatCapacity(), maxCapacity);
        }
        return totalWeight;
    }

    @Override
    default void handleHeat(double transfer, @Nullable EnumFacing side) {
        if (!HeatAPI.isFinite(transfer) || Math.abs(transfer) <= HeatAPI.EPSILON) {
            return;
        }
        List<IHeatCapacitor> heatCapacitors = getHeatCapacitors(side);
        if (heatCapacitors.size() == 1) {
            heatCapacitors.get(0).handleHeat(transfer);
        } else if (!heatCapacitors.isEmpty()) {
            double totalHeatCapacity = getTotalHeatCapacity(heatCapacitors);
            if (totalHeatCapacity < 1) {
                return;
            }
            double maxCapacity = getMaxHeatCapacity(heatCapacitors);
            double totalWeight = getTotalCapacityWeight(heatCapacitors, maxCapacity);
            if (totalWeight <= 0) {
                return;
            }
            for (IHeatCapacitor heatCapacitor : heatCapacitors) {
                double capacity = heatCapacitor.getHeatCapacity();
                if (HeatAPI.isFinite(capacity) && capacity > 0) {
                    double share = transfer * (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                    if (HeatAPI.isFinite(share)) {
                        heatCapacitor.handleHeat(share);
                    }
                }
            }
        }
    }
}
