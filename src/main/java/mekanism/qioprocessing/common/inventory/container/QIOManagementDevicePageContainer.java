package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

public interface QIOManagementDevicePageContainer
      extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOManagementDeviceClientCache getDeviceClientCache();

    @Nonnull
    QIOManagementDeviceGroupClientCache getDeviceGroupClientCache();

    boolean tryRequestDevicePage(long currentTick);

    boolean tryRequestDeviceGroupPage(long currentTick);

    boolean tryRequestDeviceCommand(long currentTick);
}
