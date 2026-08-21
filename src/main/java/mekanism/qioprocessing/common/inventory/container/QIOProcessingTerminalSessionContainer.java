package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;

import javax.annotation.Nullable;

/** Marker used by terminal packet handlers to obtain the server-owned open session. */
/**
 * QIO 处理模块中的 QIOProcessingTerminalSessionContainer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public interface QIOProcessingTerminalSessionContainer {

    @Nullable
    QIOProcessingTerminalSession getTerminalSession();

    /**
     * Returns the current client/server container window id.
     *
     * The client GUI is constructed before the open-gui packet assigns the
     * server window id, so callers must resolve this value when a packet is
     * sent instead of caching it during GUI construction.
     */
    default int getTerminalWindowId() {
        return this instanceof net.minecraft.inventory.Container ?
              ((net.minecraft.inventory.Container) this).windowId : -1;
    }
}
