package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.custom.GuiFrequencySelector.IGuiFrequencySelector;
import mekanism.client.gui.element.window.GuiQIOFrequencySelectWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.network.PacketQIOAutomationBinding;
import mekanism.qioprocessing.common.network.PacketQIOAutomationTracking;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * QIO 处理模块中的 GuiQIOAutomationFrequencyWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOAutomationFrequencyWindow extends GuiQIOFrequencySelectWindow {

    private final TileEntityContainerBlock tile;
    private final MekanismTileContainer<?> container;
    private final QIOAutomationContainerState state;
    private boolean tracking = true;

    public GuiQIOAutomationFrequencyWindow(IGuiWrapper gui, TileEntityContainerBlock tile,
          MekanismTileContainer<?> container, QIOAutomationContainerState state,
          SelectedWindowData windowData) {
        super(gui, new Selector(tile, container, state),
              new TextComponentTranslation("gui.mekanismqioprocessing.automation_frequency"), windowData);
        this.tile = tile;
        this.container = container;
        this.state = state;
        state.startClientTracking();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(new PacketQIOAutomationTracking.Message(true,
              container.windowId, tile));
    }

    @Override
    public void close() {
        stopTracking();
        super.close();
    }

    @Override
    public void tick() {
        super.tick();
        if ((state.getMode() == null || !isUpgradeStillInstalled() || state.getState() ==
            mekanism.qioprocessing.api.machine.QIOAutomationHost.State.DRAINING_CHANGE) &&
            gui() instanceof GuiMekanism<?> gui) {
            gui.queueWindowClose(this);
        }
    }

    @Override
    public void onWindowClose() {
        stopTracking();
        super.onWindowClose();
    }

    private void stopTracking() {
        if (!tracking) {
            return;
        }
        tracking = false;
        QIOProcessingPacketHandler.INSTANCE.sendToServer(new PacketQIOAutomationTracking.Message(false,
              container.windowId, tile));
        state.stopClientTracking();
    }

    boolean isFor(TileEntityContainerBlock candidate) {
        return tile == candidate;
    }

    private boolean isUpgradeStillInstalled() {
        if (state.getMode() == null) {
            return false;
        }
        return QIOAutomationUpgradeSupport.isModeInstalled(tile, state.getMode());
    }

    private static final class Selector implements IGuiFrequencySelector<QIOFrequency> {

        private final TileEntityContainerBlock tile;
        private final MekanismTileContainer<?> container;
        private final QIOAutomationContainerState state;

        private Selector(TileEntityContainerBlock tile, MekanismTileContainer<?> container,
              QIOAutomationContainerState state) {
            this.tile = tile;
            this.container = container;
            this.state = state;
        }

        @Override
        public void sendSetFrequency(FrequencyIdentity identity) {
            QIOFrequency frequency = findExact(identity);
            if (frequency != null) {
                QIOProcessingPacketHandler.INSTANCE.sendToServer(PacketQIOAutomationBinding.Message.bind(
                      container.windowId, tile, frequency.getIdentity(), frequency.getFrequencyUUID()));
            }
        }

        @Override
        public void sendRemoveFrequency(FrequencyIdentity identity) {
        }

        @Override
        public void sendUnbindFrequency() {
            QIOProcessingPacketHandler.INSTANCE.sendToServer(PacketQIOAutomationBinding.Message.unbind(
                  container.windowId, tile));
        }

        @Nullable
        @Override
        public QIOFrequency getFrequency() {
            return state.getFrequency();
        }

        @Override
        public List<QIOFrequency> getPublicFrequencies() {
            return state.getFrequencies(SecurityMode.PUBLIC);
        }

        @Override
        public List<QIOFrequency> getTrustedFrequencies() {
            return state.getFrequencies(SecurityMode.TRUSTED);
        }

        @Override
        public List<QIOFrequency> getPrivateFrequencies() {
            return state.getFrequencies(SecurityMode.PRIVATE);
        }

        @Nullable
        @Override
        public UUID getOwnerUUID() {
            return tile instanceof ISecurityTile securityTile ? securityTile.getSecurity().getOwnerUUID() : null;
        }

        @Override
        public String getSelfOwnerName() {
            return tile instanceof ISecurityTile securityTile && securityTile.getSecurity().getClientOwner() != null ?
                  securityTile.getSecurity().getClientOwner() : "";
        }

        @Override
        public boolean supportsFrequencyDeletion() {
            return false;
        }

        @Override
        public ITextComponent getSecondaryButtonText() {
            return new TextComponentTranslation("gui.mekanismqioprocessing.unbind");
        }

        @Nullable
        private QIOFrequency findExact(FrequencyIdentity identity) {
            if (identity == null) {
                return null;
            }
            for (QIOFrequency frequency : state.getFrequencies(identity.securityMode())) {
                if (Objects.equals(frequency.getIdentity(), identity)) {
                    return frequency;
                }
            }
            return null;
        }
    }
}
