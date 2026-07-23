package mekanism.common.util;

import mekanism.api.IHeatTransfer;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.capabilities.heat.LegacyHeatHandlerAdapter;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import java.util.Objects;

public class HeatUtils {

    /** @deprecated Use {@link mekanism.common.capabilities.heat.ITileHeatHandler#simulate()} instead. */
    @Deprecated
    @SuppressWarnings("removal")
    public static double[] simulate(IHeatTransfer source) {
        double[] heatTransferred = new double[]{0, 0};
        IHeatHandler sourceHandler = new LegacyHeatHandlerAdapter(source);

        // Match the modern API: finish all adjacent transfers before exchanging heat
        // with the environment. Legacy implementations decide connectivity in getAdjacent.
        for (EnumFacing side : EnumFacing.VALUES) {
            IHeatTransfer sink = source.getAdjacent(side);
            if (sink != null && !Objects.equals(source.getHeatIdentity(), sink.getHeatIdentity())) {
                IHeatHandler sinkHandler = new LegacyHeatHandlerAdapter(sink);
                double temp = sourceHandler.getTotalTemperature();
                double sinkTemp = sinkHandler.getTotalTemperature();
                double heatCapacity = sourceHandler.getTotalHeatCapacity();
                double sinkHeatCapacity = sinkHandler.getTotalHeatCapacity();
                if (temp > sinkTemp && heatCapacity >= 1 && sinkHeatCapacity >= 1) {
                    double finalTemp = HeatAPI.getFinalTemperature(temp, heatCapacity, sinkTemp, sinkHeatCapacity);
                    double invConduction = HeatAPI.sanitizeInverseConduction(source.getInverseConductionCoefficient()) +
                          HeatAPI.sanitizeInverseConduction(sink.getInverseConductionCoefficient());
                    if (!HeatAPI.isFinite(invConduction) || invConduction <= 0) {
                        invConduction = HeatAPI.MAX_HEAT;
                    }
                    double heatToTransfer = HeatAPI.multiplyHeat((temp - finalTemp) / invConduction, heatCapacity);
                    double[] sourceBefore = ITileHeatHandler.snapshotHandlerHeat(sourceHandler, null);
                    double[] sinkBefore = ITileHeatHandler.snapshotHandlerHeat(sinkHandler, null);
                    heatToTransfer = Math.min(heatToTransfer, Math.min(ITileHeatHandler.getAvailableHeat(sourceBefore),
                          ITileHeatHandler.getAvailableHeatRoom(sinkBefore)));
                    if (HeatAPI.isFinite(heatToTransfer) && heatToTransfer > HeatAPI.EPSILON) {
                        sourceHandler.handleHeat(-heatToTransfer);
                        sinkHandler.handleHeat(heatToTransfer);
                        double removed = ITileHeatHandler.getHeatDecrease(sourceBefore,
                              ITileHeatHandler.snapshotHandlerHeat(sourceHandler, null));
                        double accepted = ITileHeatHandler.getHeatIncrease(sinkBefore,
                              ITileHeatHandler.snapshotHandlerHeat(sinkHandler, null));
                        if (accepted < removed - HeatAPI.EPSILON) {
                            sourceHandler.handleHeat(removed - accepted);
                        } else if (removed < accepted - HeatAPI.EPSILON) {
                            sinkHandler.handleHeat(-(accepted - removed));
                        }
                        double actualTransfer = Math.min(removed, accepted);
                        if (actualTransfer > HeatAPI.EPSILON &&
                              !(sink instanceof ICapabilityProvider provider && CapabilityUtils.hasCapability(provider,
                                    Capabilities.GRID_TRANSMITTER_CAPABILITY, side.getOpposite()))) {
                            heatTransferred[0] = addClamped(heatTransferred[0], actualTransfer / heatCapacity);
                        }
                    }
                }
            }
        }

        // Environmental exchange is independent of whether a side accepts a heat connection.
        for (EnumFacing side : EnumFacing.VALUES) {
            double heatCapacity = sourceHandler.getTotalHeatCapacity();
            if (heatCapacity < 1) {
                continue;
            }
            double invConduction = HeatAPI.AIR_INVERSE_COEFFICIENT +
                  HeatAPI.sanitizeInverseInsulation(source.getInsulationCoefficient(side)) +
                  HeatAPI.sanitizeInverseConduction(source.getInverseConductionCoefficient());
            if (!HeatAPI.isFinite(invConduction) || invConduction <= 0) {
                invConduction = HeatAPI.MAX_HEAT;
            }
            double temperatureTransfer = (sourceHandler.getTotalTemperature() - HeatAPI.AMBIENT_TEMP) / invConduction;
            double heatToTransfer = HeatAPI.multiplyHeatSigned(temperatureTransfer, heatCapacity);
            if (HeatAPI.isFinite(heatToTransfer) && Math.abs(heatToTransfer) > HeatAPI.EPSILON) {
                double[] before = ITileHeatHandler.snapshotHandlerHeat(sourceHandler, null);
                sourceHandler.handleHeat(-heatToTransfer);
                double actualHeat = ITileHeatHandler.getHeatDecrease(before,
                      ITileHeatHandler.snapshotHandlerHeat(sourceHandler, null));
                if (actualHeat > HeatAPI.EPSILON) {
                    heatTransferred[1] = addClamped(heatTransferred[1], actualHeat / heatCapacity);
                }
            }
        }
        return heatTransferred;
    }

    private static double addClamped(double current, double amount) {
        return amount >= HeatAPI.MAX_HEAT - current ? HeatAPI.MAX_HEAT : current + amount;
    }
}
