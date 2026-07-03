package mekanism.common.tile.component.config.slot;

public interface ISlotInfo {

    boolean canInput();

    boolean canOutput();

    default boolean isEnabled() {
        return canInput() || canOutput();
    }

    /**
     * Relevant to output modes.
     *
     * @return true if none of the backing containers have anything to output.
     */
    default boolean isEmpty() {
        return false;
    }
}
