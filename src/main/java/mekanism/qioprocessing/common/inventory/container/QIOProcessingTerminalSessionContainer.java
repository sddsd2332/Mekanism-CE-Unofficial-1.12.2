package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;

import javax.annotation.Nullable;

/** Marker used by terminal packet handlers to obtain the server-owned open session. */
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
