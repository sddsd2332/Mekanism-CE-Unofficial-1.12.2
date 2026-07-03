package mekanism.client.gui.element.text;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;

import java.util.function.Consumer;

public enum BackgroundType {
    INNER_SCREEN(field -> GuiUtils.renderBackgroundTexture(GuiInnerScreen.SCREEN, GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE,
          field.getRelativeX() - 1, field.getRelativeY() - 1, field.getWidth() + 2, field.getHeight() + 2, 256, 256)),
    ELEMENT_HOLDER(field -> GuiUtils.renderBackgroundTexture(GuiElementHolder.HOLDER, GuiElementHolder.HOLDER_SIZE, GuiElementHolder.HOLDER_SIZE,
          field.getRelativeX() - 1, field.getRelativeY() - 1, field.getWidth() + 2, field.getHeight() + 2, 256, 256)),
    DEFAULT(field -> {
        GuiUtils.fill(field.getRelativeX() - 1, field.getRelativeY() - 1, field.getRelativeX() + field.getWidth() + 1, field.getRelativeY() + field.getHeight() + 1,
              GuiTextField.DEFAULT_BORDER_COLOR);
        GuiUtils.fill(field.getRelativeX(), field.getRelativeY(), field.getRelativeX() + field.getWidth(), field.getRelativeY() + field.getHeight(),
              GuiTextField.DEFAULT_BACKGROUND_COLOR);
    }),
    DIGITAL(field -> {
        GuiUtils.fill(field.getRelativeX() - 1, field.getRelativeY() - 1, field.getRelativeX() + field.getWidth() + 1, field.getRelativeY() + field.getHeight() + 1,
              field.isTextFieldFocused() ? GuiTextField.SCREEN_COLOR : GuiTextField.DARK_SCREEN_COLOR);
        GuiUtils.fill(field.getRelativeX(), field.getRelativeY(), field.getRelativeX() + field.getWidth(), field.getRelativeY() + field.getHeight(),
              GuiTextField.DEFAULT_BACKGROUND_COLOR);
    }),
    NONE(field -> {
    });

    private final Consumer<GuiTextField> renderFunction;

    BackgroundType(Consumer<GuiTextField> renderFunction) {
        this.renderFunction = renderFunction;
    }

    public void render(GuiTextField field) {
        renderFunction.accept(field);
    }
}
