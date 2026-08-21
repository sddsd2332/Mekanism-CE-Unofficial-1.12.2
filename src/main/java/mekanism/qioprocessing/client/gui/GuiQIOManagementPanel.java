package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.client.gui.element.GuiElement;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeContainer;

import javax.annotation.Nullable;
import java.util.Objects;

/** Management-terminal device browser. Route configuration is opened separately. */
/**
 * QIO 处理模块中的 GuiQIOManagementPanel 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOManagementPanel extends GuiElement {

    private final GuiQIOManagementMachinePanel machinePanel;

    public GuiQIOManagementPanel(IGuiWrapper gui,
          QIOManagementDevicePageContainer deviceContainer,
          QIOManagementPolicyPageContainer policyContainer,
          int x, int y, int width, int height) {
        super(gui, x, y, width, height);
        Objects.requireNonNull(policyContainer, "policyContainer");
        if (!(deviceContainer instanceof QIOManagementRecipeContainer recipeContainer)) {
            throw new IllegalArgumentException(
                  "QIO management device container must support remote recipe access");
        }
        machinePanel = addChild(new GuiQIOManagementMachinePanel(gui, deviceContainer,
              recipeContainer, x, y, width, height));
    }

    public boolean hasSelectedDataError() {
        return machinePanel.hasSelectedDataError();
    }

    public void recoverSelectedDataError() {
        machinePanel.recoverSelectedDataError();
    }

    @Nullable
    public String getSelectedDataErrorDiagnostic() {
        return machinePanel.getSelectedDataErrorDiagnostic();
    }

    @Nullable
    public QIOAutomationMode getSelectedDataErrorMode() {
        return machinePanel.getSelectedDataErrorMode();
    }
}
