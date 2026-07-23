package mekanism.common.capabilities.heat;

import mekanism.api.IHeatTransfer;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.IMekanismHeatHandler;
import mekanism.api.heat.ISidedHeatHandler;
import net.minecraft.util.EnumFacing;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;

import javax.annotation.Nullable;
import java.util.function.DoubleSupplier;

/**
 * Exposes a new heat handler through the deprecated 1.12 heat API without duplicating thermal state.
 * @deprecated Compatibility bridge for callers of {@link IHeatTransfer}.
 */
@Deprecated
@SuppressWarnings("removal")
public final class LegacyHeatTransferAdapter implements IHeatTransfer {

    private final IHeatHandler handler;
    private final DoubleSupplier ambientTemperature;
    @Nullable
    private final ITileHeatHandler tileHandler;
    @Nullable
    private final EnumFacing side;

    public LegacyHeatTransferAdapter(IHeatHandler handler) {
        this(handler, () -> HeatAPI.AMBIENT_TEMP, handler instanceof ITileHeatHandler ? (ITileHeatHandler) handler : null, null);
    }

    public LegacyHeatTransferAdapter(IHeatHandler handler, DoubleSupplier ambientTemperature) {
        this(handler, ambientTemperature, handler instanceof ITileHeatHandler ? (ITileHeatHandler) handler : null, null);
    }

    public LegacyHeatTransferAdapter(IHeatHandler handler, ITileHeatHandler tileHandler, @Nullable EnumFacing side) {
        this(scope(handler, side), () -> side == null ? HeatAPI.AMBIENT_TEMP : tileHandler.getAmbientTemperature(side), tileHandler, side);
    }

    private static IHeatHandler scope(IHeatHandler handler, @Nullable EnumFacing side) {
        if (side != null && handler instanceof ISidedHeatHandler sided) {
            return new ProxyHeatHandler(sided, side, null);
        }
        return handler;
    }

    private LegacyHeatTransferAdapter(IHeatHandler handler, DoubleSupplier ambientTemperature, @Nullable ITileHeatHandler tileHandler, @Nullable EnumFacing side) {
        this.handler = handler;
        this.ambientTemperature = ambientTemperature;
        this.tileHandler = tileHandler;
        this.side = side;
    }

    @Override
    public Object getHeatIdentity() {
        return handler.getHeatIdentity();
    }

    public IHeatHandler getHandler() {
        return handler;
    }

    @Override
    public double getTemp() {
        double ambient = HeatAPI.sanitizeTemperature(ambientTemperature.getAsDouble());
        return HeatAPI.sanitizeTemperature(handler.getTotalTemperature()) - ambient;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return HeatAPI.sanitizeInverseConduction(handler.getTotalInverseConduction());
    }

    @Override
    public double getInsulationCoefficient(EnumFacing requestedSide) {
        if (handler instanceof mekanism.api.heat.IHeatCapacitor capacitor) {
            return HeatAPI.sanitizeInverseInsulation(capacitor.getInverseInsulation());
        }
        EnumFacing effectiveSide = side == null ? requestedSide : side;
        if (tileHandler != null) {
            return HeatAPI.sanitizeInverseInsulation(tileHandler.getTotalInverseInsulation(effectiveSide));
        } else if (handler instanceof IMekanismHeatHandler sided) {
            return HeatAPI.sanitizeInverseInsulation(sided.getTotalInverseInsulation(effectiveSide));
        }
        return HeatAPI.DEFAULT_INVERSE_INSULATION;
    }

    @Override
    public double getHeatCapacity() {
        return HeatAPI.sanitizeHeatCapacity(handler.getTotalHeatCapacity());
    }

    @Override
    public double getHeat() {
        if (handler instanceof mekanism.api.heat.IHeatCapacitor capacitor) {
            return HeatAPI.sanitizeHeat(capacitor.getHeat(), HeatAPI.multiplyHeat(HeatAPI.AMBIENT_TEMP, capacitor.getHeatCapacity()));
        }
        return HeatAPI.multiplyHeat(handler.getTotalTemperature(), handler.getTotalHeatCapacity());
    }

    @Override
    public void setHeat(double heat) {
        double current = getHeat();
        double ambient = HeatAPI.sanitizeTemperature(ambientTemperature.getAsDouble());
        double target = HeatAPI.sanitizeHeat(heat, HeatAPI.multiplyHeat(ambient, getHeatCapacity()));
        double delta = target - current;
        if (HeatAPI.isFinite(delta)) {
            handler.handleHeat(Math.max(-HeatAPI.MAX_HEAT, Math.min(HeatAPI.MAX_HEAT, delta)));
        }
    }

    @Override
    public void transferHeatTo(double heat) {
        if (HeatAPI.isFinite(heat)) {
            handler.handleHeat(Math.max(-HeatAPI.MAX_HEAT, Math.min(HeatAPI.MAX_HEAT, heat)));
        }
    }

    @Override
    public double[] simulateHeat() {
        if (tileHandler != null && side == null) {
            HeatAPI.HeatTransfer transfer = tileHandler.simulate();
            return new double[]{transfer.adjacentTransfer(), transfer.environmentTransfer()};
        } else if (tileHandler != null && side != null) {
            return new double[]{tileHandler.simulateAdjacent(side), tileHandler.simulateEnvironment(side)};
        }
        double temperature = HeatAPI.sanitizeTemperature(handler.getTotalTemperature());
        double inverseConduction = HeatAPI.sanitizeInverseConduction(handler.getTotalInverseConduction());
        double inverseInsulation = HeatAPI.sanitizeInverseInsulation(getInsulationCoefficient(side));
        double denominator = HeatAPI.AIR_INVERSE_COEFFICIENT + inverseConduction + inverseInsulation;
        if (!HeatAPI.isFinite(denominator) || denominator <= 0) {
            denominator = HeatAPI.MAX_HEAT;
        }
        double temperatureTransfer = (temperature - HeatAPI.sanitizeTemperature(ambientTemperature.getAsDouble())) /
              denominator;
        double heatCapacity = handler.getTotalHeatCapacity();
        double heatTransfer = HeatAPI.multiplyHeatSigned(temperatureTransfer, heatCapacity);
        double[] before = ITileHeatHandler.snapshotHandlerHeat(handler, null);
        if (HeatAPI.isFinite(heatTransfer)) {
            handler.handleHeat(-heatTransfer);
        }
        double[] after = ITileHeatHandler.snapshotHandlerHeat(handler, null);
        double actualHeat = heatTransfer > 0 ? ITileHeatHandler.getHeatDecrease(before, after) :
              -ITileHeatHandler.getHeatIncrease(before, after);
        double actualTransfer = heatCapacity >= 1 ? actualHeat / heatCapacity : 0;
        return new double[]{0, HeatAPI.isFinite(actualTransfer) ? Math.max(actualTransfer, 0) : 0};
    }

    @Override
    public double applyTemperatureChange() {
        return getTemp();
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        if (this.side != null) {
            return handler.getHeatCapacitorCount() > 0;
        } else if (handler instanceof ISidedHeatHandler sided && side != null) {
            return sided.getHeatCapacitorCount(side) > 0;
        }
        return handler.getHeatCapacitorCount() > 0;
    }

    @Nullable
    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        ITileHeatHandler tile = tileHandler != null ? tileHandler : handler instanceof ITileHeatHandler ? (ITileHeatHandler) handler : null;
        if (tile != null) {
            IHeatHandler adjacent = tile.getAdjacent(side);
            return adjacent == null ? null : new LegacyHeatTransferAdapter(adjacent, () -> tile.getAmbientTemperature(side));
        }
        return null;
    }
}
