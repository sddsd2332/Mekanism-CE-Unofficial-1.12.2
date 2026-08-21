package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.common.MekanismLang;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOProcessingTerminal;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/** Base screen for management, maintenance and monitor terminal blocks. */
@SideOnly(Side.CLIENT)
/**
 * QIO 处理模块中的 GuiQIOProcessingTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOProcessingTerminal extends
      GuiMekanismTile<QIOProcessingTerminal, ContainerQIOProcessingTerminal> {

    private static final int MANAGEMENT_WIDTH = 384;
    private static final int MANAGEMENT_HEIGHT = 220;
    private static final int MAINTENANCE_HEIGHT = 214;
    private static final int CRAFTING_MONITOR_WIDTH = 384;
    private static final int CRAFTING_MONITOR_HEIGHT = 224;

    private GuiQIOProcessingTerminalFrequencyTab frequencyTab;
    private GuiQIOWorkbenchConfigurationTab workbenchConfigurationTab;
    private final ContainerQIOProcessingTerminal container;
    private GuiQIOManagementPanel managementPanel;
    private GuiQIOAutomationRecoveryTab recoveryTab;
    private GuiQIOMaintenanceRulesPanel maintenancePanel;
    private GuiQIOCraftingMonitorPanel craftingMonitorPanel;

    public GuiQIOProcessingTerminal(InventoryPlayer inventory,
          QIOProcessingTerminal terminal) {
        super(terminal, new ContainerQIOProcessingTerminal(inventory, terminal));
        container = (ContainerQIOProcessingTerminal) inventorySlots;
        if (terminal.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            xSize = MANAGEMENT_WIDTH;
            ySize = MANAGEMENT_HEIGHT;
        } else if (terminal.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            xSize = 236;
            ySize = MAINTENANCE_HEIGHT;
            dynamicSlots = true;
            inventoryLabelX = 38;
            inventoryLabelY = 122;
        } else if (terminal.getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            xSize = CRAFTING_MONITOR_WIDTH;
            ySize = CRAFTING_MONITOR_HEIGHT;
        }
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        trackWarning(WarningType.QIO_AUTOMATION_ERROR, () -> tileEntity.hasDataError() ||
              tileEntity.hasIdentityConflict() || tileEntity.getTerminalType() ==
                    mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT &&
                    container.getDeviceClientCache().getDevices().stream().anyMatch(device ->
                        "DATA_ERROR".equals(device.getStateName()) ||
                              "IDENTITY_CONFLICT".equals(device.getStateName()) ||
                              device.hasRecoveryPending() ||
                              !device.getDiagnostic().isEmpty()));
        frequencyTab = addButton(new GuiQIOProcessingTerminalFrequencyTab(this,
              container, () -> frequencyTab));
        if (tileEntity.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            workbenchConfigurationTab = addButton(new GuiQIOWorkbenchConfigurationTab(this,
                  container, () -> workbenchConfigurationTab));
        }
        addButton(new GuiInnerScreen(this, 7, 15, xSize - 16, 12,
              this::frequencyText).clearFormat().clearSpacing().padding(3).textScale(0.8F));
        if (tileEntity.getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            managementPanel = addButton(new GuiQIOManagementPanel(this, container,
                  container, 8, 31, xSize - 16, ySize - 39));
            // 频率 Tab 为 y=6，工作台配置 Tab 为 y=32；按同样间距放在其下方。
            recoveryTab = addButton(new GuiQIOAutomationRecoveryTab(this, -26, 58,
                  () -> managementPanel != null && managementPanel.hasSelectedDataError(),
                  () -> managementPanel == null ? null : managementPanel.getSelectedDataErrorMode(),
                  () -> managementPanel == null ? null :
                        managementPanel.getSelectedDataErrorDiagnostic(),
                  () -> {
                      if (managementPanel != null) {
                          managementPanel.recoverSelectedDataError();
                      }
                  }, true));
        } else if (tileEntity.getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            maintenancePanel = addButton(new GuiQIOMaintenanceRulesPanel(this,
                  container, 8, 31, xSize - 16, 90));
        } else if (tileEntity.getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            craftingMonitorPanel = addButton(new GuiQIOCraftingMonitorPanel(this,
                  container, 8, 31, xSize - 16, ySize - 39));
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        if (tileEntity.getTerminalType() !=
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT &&
            tileEntity.getTerminalType() !=
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            renderInventoryText();
        }
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> frequencyText() {
        QIOFrequency frequency = container.getTerminalFrequency();
        return Collections.singletonList(frequency == null ?
              new TextComponentTranslation("frequency.mekanism.none") :
              MekanismLang.FREQUENCY.translate(frequency.getName()));
    }
}
