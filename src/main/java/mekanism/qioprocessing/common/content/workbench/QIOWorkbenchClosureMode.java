package mekanism.qioprocessing.common.content.workbench;

/** Controls how an explicitly encoded workbench recipe discovers its dependencies. */
/**
 * QIO 处理模块中的 QIOWorkbenchClosureMode 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public enum QIOWorkbenchClosureMode {
    NONE,
    PREFERRED,
    ALL;

    public QIOWorkbenchClosureMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
