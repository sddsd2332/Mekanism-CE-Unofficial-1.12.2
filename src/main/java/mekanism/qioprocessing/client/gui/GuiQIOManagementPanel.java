package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeContainer;

import java.util.Objects;

/** Management-terminal device browser. Route configuration is opened separately. */
public final class GuiQIOManagementPanel extends GuiElement {

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
        addChild(new GuiQIOManagementMachinePanel(gui, deviceContainer, recipeContainer,
              x, y, width, height));
    }
}
