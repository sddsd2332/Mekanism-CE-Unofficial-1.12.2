package mekanism.qioprocessing.common.inventory.container;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.inventory.container.item.ItemStackSlotAccess;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData;
import mekanism.qioprocessing.common.terminal.QIOPortableTerminalIdentityRegistry;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Shared exact-stack, generation and duplicate identity checks for portable containers. */
final class QIOPortableTerminalSessionTarget {

    private final ItemStackSlotAccess itemAccess;
    @Nullable
    private final QIOProcessingTerminalSession session;
    @Nullable
    private final QIOPortableTerminalIdentityRegistry.Lease identityLease;

    QIOPortableTerminalSessionTarget(@Nonnull InventoryPlayer inventory,
          @Nonnull ItemStackSlotAccess itemAccess, boolean remote) {
        this.itemAccess = itemAccess;
        QIOProcessingTerminalSession createdSession = null;
        QIOPortableTerminalIdentityRegistry.Lease acquiredLease = null;
        ItemStack stack = itemAccess.getStack();
        if (!remote && isTerminal(stack)) {
            try {
                PortableQIOProcessingTerminalData data =
                      PortableQIOProcessingTerminalData.read(stack);
                if (data != null) {
                    ItemPortableQIOProcessingTerminal item =
                          (ItemPortableQIOProcessingTerminal) stack.getItem();
                    UUID playerUUID = inventory.player.getUniqueID();
                    QIOFrequencyReference reference = data.getFrequencyReference();
                    long accessRevision = QIOProcessingFrequencyAccess.getAccessibleRevision(
                          reference, playerUUID);
                    UUID frequencyUUID = accessRevision < 0 || reference == null ? null :
                          reference.getFrequencyUUID();
                    createdSession = new QIOProcessingTerminalSession(playerUUID,
                          QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
                          item.getTerminalType(), data.getTerminalUUID(), data.getGeneration(),
                          frequencyUUID, accessRevision < 0 ? -1 : accessRevision);
                    acquiredLease = QIOPortableTerminalIdentityRegistry.INSTANCE.acquire(
                          data.getTerminalUUID(), stack, playerUUID + "/" +
                                itemAccess.getHand() + "/" + itemAccess.getSlot());
                }
            } catch (QIOProcessingDataException ignored) {
            }
        }
        session = createdSession;
        identityLease = acquiredLease;
    }

    @Nullable
    QIOProcessingTerminalSession getSession() {
        return session;
    }

    boolean validate(@Nonnull EntityPlayer player) {
        if (session == null || identityLease == null || !identityLease.isUsable() ||
            !itemAccess.isOriginalStackPresent() || !isTerminal(itemAccess.getStack())) {
            return false;
        }
        try {
            ItemStack stack = itemAccess.getStack();
            PortableQIOProcessingTerminalData data =
                  PortableQIOProcessingTerminalData.read(stack);
            if (data == null) {
                return false;
            }
            ItemPortableQIOProcessingTerminal item =
                  (ItemPortableQIOProcessingTerminal) stack.getItem();
            QIOFrequencyReference reference = data.getFrequencyReference();
            long accessRevision = QIOProcessingFrequencyAccess.getAccessibleRevision(reference,
                  player.getUniqueID());
            UUID frequencyUUID = accessRevision < 0 || reference == null ? null :
                  reference.getFrequencyUUID();
            return session.validate(session.getSessionNonce(), player.getUniqueID(),
                  QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
                  item.getTerminalType(), data.getTerminalUUID(), data.getGeneration(),
                  frequencyUUID, accessRevision < 0 ? -1 : accessRevision) ==
                  QIOProcessingTerminalSession.Validation.ACCEPTED;
        } catch (QIOProcessingDataException e) {
            return false;
        }
    }

    boolean acceptMutation(long expectedGeneration, long updatedGeneration) {
        if (session == null || session.getTargetRevision() != expectedGeneration) {
            return false;
        }
        try {
            PortableQIOProcessingTerminalData data =
                  PortableQIOProcessingTerminalData.read(itemAccess.getStack());
            if (data == null || data.getGeneration() != updatedGeneration ||
                !data.getTerminalUUID().equals(session.getTerminalUUID())) {
                return false;
            }
            QIOFrequencyReference reference = data.getFrequencyReference();
            long accessRevision = QIOProcessingFrequencyAccess.getAccessibleRevision(reference,
                  session.getPlayerUUID());
            UUID frequencyUUID = accessRevision < 0 || reference == null ? null :
                  reference.getFrequencyUUID();
            session.rebind(expectedGeneration, updatedGeneration, frequencyUUID,
                  accessRevision < 0 ? -1 : accessRevision);
            return true;
        } catch (QIOProcessingDataException e) {
            return false;
        }
    }

    void close() {
        if (session != null) {
            session.close();
        }
        if (identityLease != null) {
            identityLease.close();
        }
    }

    private static boolean isTerminal(ItemStack stack) {
        return stack != null && !stack.isEmpty() &&
              stack.getItem() instanceof ItemPortableQIOProcessingTerminal;
    }
}
