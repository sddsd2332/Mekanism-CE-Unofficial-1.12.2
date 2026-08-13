package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.inventory.container.slot.IVirtualSlot;

import javax.annotation.Nonnull;
import java.util.List;

public interface QIOWorkbenchConfigurationContainer
      extends QIOProcessingTerminalSessionContainer {

    enum RequestStream {
        PRODUCTS,
        RECIPES,
        CANDIDATES,
        COMMANDS
    }

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOWorkbenchConfigurationClientCache getWorkbenchConfigurationClientCache();

    @Nonnull
    List<? extends IVirtualSlot> getWorkbenchEditorInventorySlots();

    boolean tryRequestWorkbenchConfiguration(long currentTick,
          @Nonnull RequestStream requestStream);
}
