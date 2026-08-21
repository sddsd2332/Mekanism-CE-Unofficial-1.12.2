package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;

/**
 * QIO 处理模块中的 TileEntityQIOManagementTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class TileEntityQIOManagementTerminal extends QIOProcessingTerminal {

    public TileEntityQIOManagementTerminal() {
        super("QIOManagementTerminal", QIOProcessingTerminalType.MANAGEMENT);
    }
}
