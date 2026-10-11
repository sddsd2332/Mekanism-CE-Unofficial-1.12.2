package mekanism.common.base;

public interface IComparatorSupport {

    /**
     * Whether this tile participates in comparator updates and exposes a comparator signal.
     * The default keeps legacy implementations enabled until they explicitly opt out.
     */
    default boolean supportsComparator() {
        return true;
    }

    /**
     * Cached comparator signal used by block redstone reads.
     */
    default int getCurrentRedstoneLevel() {
        return getRedstoneLevel();
    }

    /**
     * Which content types can change this tile's comparator signal. Unknown implementations are conservative.
     */
    default boolean isComparatorInput(mekanism.api.transmitters.TransmissionType type) {
        return true;
    }

    int getRedstoneLevel();
}
