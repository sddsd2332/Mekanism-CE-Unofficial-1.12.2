package mekanism.common.lib.radiation;

import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.api.radiation.IRadiationSource;
import mekanism.common.config.MekanismConfig;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class RadiationSource implements IRadiationSource {

    private final Coord4D pos;
    /** In Sv/h */
    private double magnitude;

    public RadiationSource(Coord4D pos, double magnitude) {
        this.pos = pos;
        this.magnitude = RadiationUtil.sanitizeMagnitude(magnitude);
    }

    @Nonnull
    @Override
    public Coord4D getPos() {
        return pos;
    }

    @Override
    public double getMagnitude() {
        return magnitude;
    }

    @Override
    public void radiate(double magnitude) {
        this.magnitude = RadiationUtil.addClamped(this.magnitude, magnitude);
    }

    @Override
    public boolean decay() {
        magnitude *= MekanismConfig.current().general.radiationSourceDecayRate.val();
        magnitude = RadiationUtil.sanitizeMagnitude(magnitude);
        return magnitude < RadiationManager.MIN_MAGNITUDE;
    }

    @Nullable
    public static RadiationSource load(NBTTagCompound tag) {
        if (!tag.hasKey("x", 99) || !tag.hasKey("y", 99) || !tag.hasKey("z", 99) ||
            !tag.hasKey("dimensionId", 99) || !tag.hasKey(NBTConstants.RADIATION, 99)) {
            return null;
        }
        double magnitude = tag.getDouble(NBTConstants.RADIATION);
        if (!Double.isFinite(magnitude) || !(magnitude > 0)) {
            return null;
        }
        return new RadiationSource(Coord4D.read(tag), magnitude);
    }

    public void write(NBTTagCompound tag) {
        pos.write(tag);
        tag.setDouble(NBTConstants.RADIATION, magnitude);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RadiationSource other = (RadiationSource) o;
        return magnitude == other.magnitude && pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, magnitude);
    }
}
