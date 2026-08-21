package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.network.PacketQIOProcessingTerminalPreference;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Block smart-processing viewer with three native QIO manual crafting windows. */
/**
 * QIO 处理模块中的 ContainerQIOSmartProcessingTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class ContainerQIOSmartProcessingTerminal extends QIOItemViewerContainer
      implements QIOBlockTerminalContainer, QIOProcessingTerminalFrequencyContainer,
      QIOSmartProcessingPageContainer {

    private final TileEntityQIOSmartProcessingTerminal terminal;
    private final QIOBlockTerminalSessionTarget sessionTarget;
    private final QIOProcessingTerminalContainerState terminalState;
    private final QIOSmartProcessingClientCache smartProcessingClientCache =
          new QIOSmartProcessingClientCache();
    private final QIOSmartProcessingRequestLedger smartProcessingRequestLedger =
          new QIOSmartProcessingRequestLedger();
    private long lastSmartProcessingRequestTick = Long.MIN_VALUE;
    private long lastSmartProcessingPollTick = Long.MIN_VALUE;

    public ContainerQIOSmartProcessingTerminal(InventoryPlayer inventory,
          TileEntityQIOSmartProcessingTerminal terminal) {
        super(inventory, terminal == null ? null : terminal.getQIOFrequency(), false);
        this.terminal = terminal;
        setCraftingWindowHolder(terminal);
        terminal.addContainerTrackers(this);
        sessionTarget = new QIOBlockTerminalSessionTarget(inventory, terminal, isRemote());
        terminalState = new QIOProcessingTerminalContainerState(this,
              this::getTerminalSession);
        addSlotsAndOpen();
        openViewer();
    }

    @Nullable
    @Override
    public QIOProcessingTerminalSession getTerminalSession() {
        return sessionTarget.getSession();
    }

    public QIOProcessingTerminalContainerState getTerminalState() {
        return terminalState;
    }

    @Override
    public QIOSmartProcessingClientCache getSmartProcessingClientCache() {
        return smartProcessingClientCache;
    }

    @Override
    public QIOSmartProcessingRequestLedger getSmartProcessingRequestLedger() {
        return smartProcessingRequestLedger;
    }

    @Override
    public boolean tryRequestSmartProcessing(long currentTick) {
        if (currentTick <= lastSmartProcessingRequestTick) {
            return false;
        }
        lastSmartProcessingRequestTick = currentTick;
        return true;
    }

    @Override
    public boolean tryRequestSmartProcessingPoll(long currentTick) {
        if (currentTick <= lastSmartProcessingPollTick) {
            return false;
        }
        lastSmartProcessingPollTick = currentTick;
        return true;
    }

    @Override
    public int getTerminalWindowId() {
        return windowId;
    }

    @Nullable
    @Override
    public QIOFrequency getTerminalFrequency() {
        return getFrequency();
    }

    @Override
    public List<QIOFrequency> getPublicTerminalFrequencies() {
        return terminal.getPublicCache(FrequencyType.QIO);
    }

    @Override
    public List<QIOFrequency> getPrivateTerminalFrequencies() {
        return terminal.getPrivateCache(FrequencyType.QIO);
    }

    @Override
    public List<QIOFrequency> getTrustedTerminalFrequencies() {
        return terminal.getTrustedCache(FrequencyType.QIO);
    }

    @Nullable
    @Override
    public UUID getTerminalOwnerUUID() {
        return terminal.getSecurity().getOwnerUUID();
    }

    @Override
    public String getTerminalOwnerName() {
        String owner = terminal.getSecurity().getClientOwner();
        return owner == null ? "" : owner;
    }

    @Override
    public boolean isPortableTerminal() {
        return false;
    }

    @Nonnull
    @Override
    public QIOProcessingTerminal getTerminalTile() {
        return terminal;
    }

    @Override
    public boolean acceptAuthorizedBindingChange(long expectedRevision,
          long updatedRevision) {
        return sessionTarget.acceptMutation(expectedRevision, updatedRevision);
    }

    @Override
    @Nullable
    public QIOFrequency getFrequency() {
        if (isRemote()) {
            frequency = terminal.getQIOFrequency();
            if (frequency == null && terminal.getFrequencyReference() != null) {
                UUID expectedUUID = terminal.getFrequencyReference().getFrequencyUUID();
                for (QIOFrequency candidate : getPublicTerminalFrequencies()) {
                    if (expectedUUID.equals(candidate.getFrequencyUUID())) {
                        frequency = candidate;
                        break;
                    }
                }
                if (frequency == null) {
                    for (QIOFrequency candidate : getPrivateTerminalFrequencies()) {
                        if (expectedUUID.equals(candidate.getFrequencyUUID())) {
                            frequency = candidate;
                            break;
                        }
                    }
                }
                if (frequency == null) {
                    for (QIOFrequency candidate : getTrustedTerminalFrequencies()) {
                        if (expectedUUID.equals(candidate.getFrequencyUUID())) {
                            frequency = candidate;
                            break;
                        }
                    }
                }
            }
        } else {
            QIOFrequency resolved = terminal.getQIOFrequency();
            if (resolved != frequency && inv.player instanceof EntityPlayerMP player) {
                if (frequency != null) {
                    frequency.closeViewer(player);
                }
                frequency = resolved;
                if (frequency != null) {
                    frequency.openViewer(player);
                }
                sendViewerSync(player);
            } else {
                frequency = resolved;
            }
        }
        return frequency;
    }

    @Override
    public void detectAndSendChanges() {
        if (!isRemote()) {
            getFrequency();
        }
        super.detectAndSendChanges();
    }

    @Override
    public boolean shiftClickIntoFrequency() {
        return terminal.shiftClickIntoFrequency();
    }

    @Override
    public void toggleTargetDirection() {
        if (isRemote() && terminalState.isValid()) {
            QIOProcessingPacketHandler.INSTANCE.sendToServer(
                  PacketQIOProcessingTerminalPreference.Message.create(windowId,
                        terminalState));
        }
    }

    @Override
    protected void openInventory(@Nonnull InventoryPlayer inventory) {
        super.openInventory(inventory);
        terminal.open(inventory.player);
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        smartProcessingClientCache.clear();
        smartProcessingRequestLedger.clear();
        sessionTarget.close();
        terminal.close(player);
        super.closeInventory(player);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        if (player.world.isRemote) {
            return !isKilled();
        }
        return !isKilled() && terminal.isUsableByPlayer(player) &&
              SecurityUtils.canAccess(player, terminal) && sessionTarget.validate(player);
    }
}
