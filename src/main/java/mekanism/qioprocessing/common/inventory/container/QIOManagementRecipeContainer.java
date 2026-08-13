package mekanism.qioprocessing.common.inventory.container;

import javax.annotation.Nonnull;

public interface QIOManagementRecipeContainer extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nonnull
    QIOManagementRecipeClientCache getManagementRecipeClientCache();

    boolean tryRequestManagementRecipe(long currentTick);
}
