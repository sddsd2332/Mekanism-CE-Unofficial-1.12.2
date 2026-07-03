package mekanism.api;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public enum AutomationType {
    /**
     * External interaction (third party interacting with a machine)
     */
    EXTERNAL,
    /**
     * Internal interaction (machine interacting with its own contents)
     */
    INTERNAL,
    /**
     * Manual interaction (player interacting manually, such as in a GUI)
     */
    MANUAL;

    /**
     * Helper method to convert a null side into an internal automation type, and anything else into an external automation type.
     */
    public static AutomationType handler(@Nullable EnumFacing side) {
        return side == null ? INTERNAL : EXTERNAL;
    }
}
