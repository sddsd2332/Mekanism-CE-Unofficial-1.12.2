package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Client-facing frequency state shared by block and portable processing terminals. */
public interface QIOProcessingTerminalFrequencyContainer extends
      QIOProcessingTerminalSessionContainer {

    int getTerminalWindowId();

    @Nonnull
    QIOProcessingTerminalContainerState getTerminalState();

    @Nullable
    QIOFrequency getTerminalFrequency();

    @Nonnull
    List<QIOFrequency> getPublicTerminalFrequencies();

    @Nonnull
    List<QIOFrequency> getPrivateTerminalFrequencies();

    @Nonnull
    List<QIOFrequency> getTrustedTerminalFrequencies();

    @Nullable
    UUID getTerminalOwnerUUID();

    @Nonnull
    String getTerminalOwnerName();

    boolean isPortableTerminal();
}
