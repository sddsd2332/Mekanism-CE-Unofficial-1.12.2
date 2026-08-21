package mekanism.qioprocessing.client.gui;

/** Small helpers for monotonic client tick timestamps with an unset sentinel. */
/**
 * QIO 处理模块中的 QIOClientTiming 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
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
