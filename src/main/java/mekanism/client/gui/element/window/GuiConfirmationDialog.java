package mekanism.client.gui.element.window;

import mekanism.api.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/** Modal confirmation dialog matching the 26.2 frequency deletion flow. */
public class GuiConfirmationDialog extends GuiWindow {

    private static final int WIDTH = 140;
    private static final int PADDING = 5;

    private final List<String> lines;

    private GuiConfirmationDialog(IGuiWrapper gui, int x, int y, int height, ITextComponent message,
          Runnable onConfirm, DialogType type) {
        super(gui, x, y, WIDTH, height, WindowType.CONFIRMATION);
        lines = getFont().listFormattedStringToWidth(message.getFormattedText(), WIDTH - 2 * PADDING);
        interactionStrategy = InteractionStrategy.NONE;
        active = true;
        addChild(new TranslationButton(gui, relativeX + WIDTH / 2 - 51, relativeY + height - 24, 50, 18,
              MekanismLang.BUTTON_CANCEL, this::close));
        addChild(new TranslationButton(gui, relativeX + WIDTH / 2 + 1, relativeY + height - 24, 50, 18,
              MekanismLang.BUTTON_CONFIRM, () -> {
                  onConfirm.run();
                  close();
              }, null, type == DialogType.DANGER ? () -> EnumColor.RED : null));
    }

    public static void show(IGuiWrapper gui, ITextComponent message, Runnable onConfirm, DialogType type) {
        int textHeight = gui.getFont().listFormattedStringToWidth(message.getFormattedText(), WIDTH - 2 * PADDING).size() * 9;
        int height = 33 + Math.max(9, textHeight);
        gui.addWindow(new GuiConfirmationDialog(gui, (gui.getWidth() - WIDTH) / 2,
              (gui.getHeight() - height) / 2, height, message, onConfirm, type));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        int y = relativeY + 6;
        for (String line : lines) {
            int x = relativeX + (WIDTH - getFont().getStringWidth(line)) / 2;
            getFont().drawString(line, x, y, titleTextColor());
            y += 9;
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        //The modal owns all clicks while it is open, including clicks outside its bounds.
        return true;
    }

    @Override
    protected boolean isFocusOverlay() {
        return true;
    }

    public enum DialogType {
        NORMAL,
        DANGER
    }
}
