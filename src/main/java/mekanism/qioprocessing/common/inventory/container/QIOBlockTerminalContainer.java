package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;

import javax.annotation.Nonnull;

public interface QIOBlockTerminalContainer extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminal getTerminalTile();

    boolean acceptAuthorizedBindingChange(long expectedRevision, long updatedRevision);
}
