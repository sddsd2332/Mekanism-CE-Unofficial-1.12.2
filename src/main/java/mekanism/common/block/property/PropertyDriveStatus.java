package mekanism.common.block.property;

import net.minecraftforge.common.property.IUnlistedProperty;

/** Compact client-side status data for the twelve QIO drive positions. */
public class PropertyDriveStatus implements IUnlistedProperty<Long> {

    public static final PropertyDriveStatus INSTANCE = new PropertyDriveStatus();

    private PropertyDriveStatus() {
    }

    @Override
    public String getName() {
        return "drive_status";
    }

    @Override
    public boolean isValid(Long value) {
        return value != null;
    }

    @Override
    public Class<Long> getType() {
        return Long.class;
    }

    @Override
    public String valueToString(Long value) {
        return value == null ? "0" : Long.toUnsignedString(value);
    }
}
