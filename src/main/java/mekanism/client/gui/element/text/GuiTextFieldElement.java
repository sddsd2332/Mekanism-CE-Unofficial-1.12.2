package mekanism.client.gui.element.text;

import mekanism.api.functions.CharPredicate;
import mekanism.api.functions.CharUnaryOperator;
import mekanism.client.gui.IGuiWrapper;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * @deprecated Use {@link GuiTextField}. This class remains as a source compatibility bridge for older addons.
 */
@Deprecated
public class GuiTextFieldElement extends GuiTextField {

    public GuiTextFieldElement(IGuiWrapper gui, int id, int x, int y, int width, int height) {
        super(gui, id, x, y, width, height);
    }

    @Override
    public GuiTextFieldElement setMaxLength(int maxLength) {
        super.setMaxLength(maxLength);
        return this;
    }

    @Override
    public GuiTextFieldElement setInputValidator(CharPredicate inputValidator) {
        super.setInputValidator(inputValidator);
        return this;
    }

    @Override
    public GuiTextFieldElement setInputValidator(BiPredicate<Character, Integer> inputValidator) {
        super.setInputValidator(inputValidator);
        return this;
    }

    @Override
    public GuiTextFieldElement setTextValidator(Predicate<String> textValidator) {
        super.setTextValidator(textValidator);
        return this;
    }

    @Override
    public GuiTextFieldElement setInputTransformer(CharUnaryOperator inputTransformer) {
        super.setInputTransformer(inputTransformer);
        return this;
    }

    @Override
    public GuiTextFieldElement setPasteTransformer(UnaryOperator<String> pasteTransformer) {
        super.setPasteTransformer(pasteTransformer);
        return this;
    }

    @Override
    public GuiTextFieldElement setEnterHandler(Runnable enterHandler) {
        super.setEnterHandler(enterHandler);
        return this;
    }

    @Override
    public GuiTextFieldElement setResponder(Consumer<String> responder) {
        super.setResponder(responder);
        return this;
    }

    @Override
    public GuiTextFieldElement setOffset(int offsetX, int offsetY) {
        super.setOffset(offsetX, offsetY);
        return this;
    }

    @Override
    public GuiTextFieldElement setScale(float textScale) {
        super.setScale(textScale);
        return this;
    }

    @Override
    public GuiTextFieldElement setBackgroundDrawing(boolean backgroundDrawing) {
        super.setBackgroundDrawing(backgroundDrawing);
        return this;
    }

    @Override
    public GuiTextFieldElement setBackground(BackgroundType backgroundType) {
        super.setBackground(backgroundType);
        return this;
    }

    @Override
    public GuiTextFieldElement setIcon(IconType iconType) {
        super.setIcon(iconType);
        return this;
    }

    @Override
    public GuiTextFieldElement configureDigitalInput(Runnable enterHandler) {
        super.configureDigitalInput(enterHandler);
        return this;
    }

    @Override
    public GuiTextFieldElement configureDigitalBorderInput(Runnable enterHandler) {
        super.configureDigitalBorderInput(enterHandler);
        return this;
    }

    @Override
    public GuiTextFieldElement addCheckmarkButton(Runnable callback) {
        super.addCheckmarkButton(callback);
        return this;
    }

    @Override
    public GuiTextFieldElement addCheckmarkButton(ButtonType type, Runnable callback) {
        super.addCheckmarkButton(type, callback);
        return this;
    }

    @Override
    public GuiTextFieldElement setTextColor(int color) {
        super.setTextColor(color);
        return this;
    }

    @Override
    public GuiTextFieldElement setDisabledTextColor(int color) {
        super.setDisabledTextColor(color);
        return this;
    }

    @Override
    public GuiTextFieldElement setTextColorUneditable(int color) {
        super.setTextColorUneditable(color);
        return this;
    }

    @Override
    public GuiTextFieldElement setCanLoseFocus(boolean canLoseFocus) {
        super.setCanLoseFocus(canLoseFocus);
        return this;
    }

    @Override
    public GuiTextFieldElement allowColoredText() {
        super.allowColoredText();
        return this;
    }

    @Override
    public GuiTextFieldElement setEditable(boolean editable) {
        super.setEditable(editable);
        return this;
    }

    @Override
    public GuiTextFieldElement setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        return this;
    }
}
