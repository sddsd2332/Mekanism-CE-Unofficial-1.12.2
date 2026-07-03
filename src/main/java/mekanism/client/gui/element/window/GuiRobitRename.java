package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.network.PacketRobit.RobitMessage;

public class GuiRobitRename extends GuiWindow {

    private final EntityRobit robit;
    private final GuiTextField nameField;

    public GuiRobitRename(IGuiWrapper gui, int x, int y, EntityRobit robit) {
        super(gui, x, y, 172, 58, WindowType.RENAME);
        interactionStrategy = InteractionStrategy.NONE;
        this.robit = robit;
        addChild(new TranslationButton(gui, relativeX + 56, relativeY + 32, 60, 20, MekanismLang.BUTTON_CONFIRM, this::changeName));
        nameField = addChild(new GuiTextField(gui, 0, relativeX + 21, relativeY + 17, width - 42, 12)
              .setMaxLength(RobitMessage.MAX_NAME_LENGTH)
              .setCanLoseFocus(false)
              .allowColoredText()
              .setEnterHandler(this::changeName));
        nameField.setFocused(true);
    }

    private void changeName() {
        String name = nameField.getText().trim();
        if (RobitMessage.hasContent(name)) {
            Mekanism.packetHandler.sendToServer(new RobitMessage(robit.getEntityId(), name));
            close();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(MekanismLang.ROBIT_RENAME.translate(), 7);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Focus window: block clicks outside while rename is open.
        return true;
    }

    @Override
    protected boolean isFocusOverlay() {
        return true;
    }
}
