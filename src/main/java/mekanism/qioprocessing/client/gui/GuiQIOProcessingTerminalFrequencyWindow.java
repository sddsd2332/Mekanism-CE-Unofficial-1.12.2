package mekanism.qioprocessing.client.gui;

import mekanism.client.MekanismClient;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.custom.GuiFrequencySelector.IGuiFrequencySelector;
import mekanism.client.gui.element.window.GuiQIOFrequencySelectWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOCraftingProcessor;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.network.PacketQIOCraftingProcessorBinding;
import mekanism.qioprocessing.common.network.PacketQIOProcessingTerminalBinding;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** In-place terminal frequency selector with exact UUID and session checks. */
/**
 * QIO 处理模块中的 GuiQIOProcessingTerminalFrequencyWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOProcessingTerminalFrequencyWindow extends GuiQIOFrequencySelectWindow {

    @Nullable
    private final QIOProcessingTerminalFrequencyContainer terminalContainer;

    public GuiQIOProcessingTerminalFrequencyWindow(IGuiWrapper gui,
          QIOProcessingTerminalFrequencyContainer container,
          SelectedWindowData windowData) {
        super(gui, new Selector(container),
              new TextComponentTranslation("gui.mekanismqioprocessing.terminal_frequency"),
              windowData);
        terminalContainer = container;
    }

    public GuiQIOProcessingTerminalFrequencyWindow(IGuiWrapper gui,
          ContainerQIOCraftingProcessor container,
          SelectedWindowData windowData) {
        super(gui, new ProcessorSelector(container),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.crafting_processor_frequency"),
              windowData);
        terminalContainer = null;
    }

    @Override
    public void tick() {
        super.tick();
        if (terminalContainer != null && !terminalContainer.getTerminalState().isValid() &&
            gui() instanceof GuiMekanism<?> gui) {
            gui.queueWindowClose(this);
        }
    }

    private static final class Selector implements IGuiFrequencySelector<QIOFrequency> {

        private final QIOProcessingTerminalFrequencyContainer container;

        private Selector(QIOProcessingTerminalFrequencyContainer container) {
            this.container = container;
        }

        @Override
        public void sendSetFrequency(FrequencyIdentity identity) {
            QIOFrequency frequency = findExact(identity);
            if (frequency != null) {
                QIOProcessingPacketHandler.INSTANCE.sendToServer(
                      PacketQIOProcessingTerminalBinding.Message.bind(
                            container.getTerminalWindowId(), container.getTerminalState(),
                            frequency.getIdentity(), frequency.getFrequencyUUID()));
            }
        }

        @Override
        public void sendRemoveFrequency(FrequencyIdentity identity) {
        }

        @Override
        public void sendUnbindFrequency() {
            QIOProcessingPacketHandler.INSTANCE.sendToServer(
                  PacketQIOProcessingTerminalBinding.Message.unbind(
                        container.getTerminalWindowId(), container.getTerminalState()));
        }

        @Nullable
        @Override
        public QIOFrequency getFrequency() {
            return container.getTerminalFrequency();
        }

        @Override
        public List<QIOFrequency> getPublicFrequencies() {
            return container.getPublicTerminalFrequencies();
        }

        @Override
        public List<QIOFrequency> getTrustedFrequencies() {
            return container.getTrustedTerminalFrequencies();
        }

        @Override
        public List<QIOFrequency> getPrivateFrequencies() {
            return container.getPrivateTerminalFrequencies();
        }

        @Nullable
        @Override
        public UUID getOwnerUUID() {
            return container.getTerminalOwnerUUID();
        }

        @Override
        public String getSelfOwnerName() {
            String configured = container.getTerminalOwnerName();
            if (!configured.isEmpty()) {
                return configured;
            }
            UUID owner = getOwnerUUID();
            if (owner == null) {
                return "";
            }
            String name = MekanismClient.clientUUIDMap.get(owner);
            if (name == null && net.minecraft.client.Minecraft.getMinecraft().player != null &&
                owner.equals(net.minecraft.client.Minecraft.getMinecraft().player.getUniqueID())) {
                name = net.minecraft.client.Minecraft.getMinecraft().player.getName();
            }
            return name == null ? "" : name;
        }

        @Override
        public boolean isPortable() {
            return container.isPortableTerminal();
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
        private QIOFrequency findExact(@Nullable FrequencyIdentity identity) {
            if (identity == null) {
                return null;
            }
            List<QIOFrequency> frequencies = switch (identity.securityMode()) {
                case PUBLIC -> container.getPublicTerminalFrequencies();
                case PRIVATE -> container.getPrivateTerminalFrequencies();
                case TRUSTED -> container.getTrustedTerminalFrequencies();
            };
            for (QIOFrequency frequency : frequencies) {
                if (Objects.equals(frequency.getIdentity(), identity)) {
                    return frequency;
                }
            }
            return null;
        }
    }

    private static final class ProcessorSelector implements IGuiFrequencySelector<QIOFrequency> {

        private final ContainerQIOCraftingProcessor container;

        private ProcessorSelector(ContainerQIOCraftingProcessor container) {
            this.container = container;
        }

        @Override
        public void sendSetFrequency(FrequencyIdentity identity) {
            QIOFrequency frequency = findExact(identity);
            if (frequency != null) {
                QIOProcessingPacketHandler.INSTANCE.sendToServer(
                      PacketQIOCraftingProcessorBinding.Message.bind(
                            container.windowId,
                            container.getTileEntity(), frequency.getIdentity(),
                            frequency.getFrequencyUUID()));
            }
        }

        @Override
        public void sendRemoveFrequency(FrequencyIdentity identity) {
        }

        @Override
        public void sendUnbindFrequency() {
            QIOProcessingPacketHandler.INSTANCE.sendToServer(
                  PacketQIOCraftingProcessorBinding.Message.unbind(
                        container.windowId,
                        container.getTileEntity()));
        }

        @Nullable
        @Override
        public QIOFrequency getFrequency() {
            return container.getProcessorFrequency();
        }

        @Override
        public List<QIOFrequency> getPublicFrequencies() {
            return container.getPublicProcessorFrequencies();
        }

        @Override
        public List<QIOFrequency> getTrustedFrequencies() {
            return container.getTrustedProcessorFrequencies();
        }

        @Override
        public List<QIOFrequency> getPrivateFrequencies() {
            return container.getPrivateProcessorFrequencies();
        }

        @Nullable
        @Override
        public UUID getOwnerUUID() {
            return container.getProcessorOwnerUUID();
        }

        @Override
        public String getSelfOwnerName() {
            String configured = container.getProcessorOwnerName();
            if (!configured.isEmpty()) {
                return configured;
            }
            UUID owner = getOwnerUUID();
            if (owner == null) {
                return "";
            }
            String name = MekanismClient.clientUUIDMap.get(owner);
            if (name == null && net.minecraft.client.Minecraft.getMinecraft().player != null &&
                owner.equals(net.minecraft.client.Minecraft.getMinecraft().player.getUniqueID())) {
                name = net.minecraft.client.Minecraft.getMinecraft().player.getName();
            }
            return name == null ? "" : name;
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
        private QIOFrequency findExact(@Nullable FrequencyIdentity identity) {
            if (identity == null) {
                return null;
            }
            List<QIOFrequency> frequencies = switch (identity.securityMode()) {
                case PUBLIC -> container.getPublicProcessorFrequencies();
                case PRIVATE -> container.getPrivateProcessorFrequencies();
                case TRUSTED -> container.getTrustedProcessorFrequencies();
            };
            for (QIOFrequency frequency : frequencies) {
                if (Objects.equals(frequency.getIdentity(), identity)) {
                    return frequency;
                }
            }
            return null;
        }
    }
}
