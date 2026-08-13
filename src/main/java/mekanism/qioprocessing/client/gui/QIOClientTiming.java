package mekanism.qioprocessing.client.gui;

/** Small helpers for monotonic client tick timestamps with an unset sentinel. */
final class QIOClientTiming {

    private QIOClientTiming() {
    }

    static boolean elapsed(long currentTick, long previousTick, long requiredTicks) {
        if (requiredTicks <= 0 || previousTick == Long.MIN_VALUE) {
            return true;
        }
        return currentTick >= previousTick && currentTick - previousTick >= requiredTicks;
    }
}
