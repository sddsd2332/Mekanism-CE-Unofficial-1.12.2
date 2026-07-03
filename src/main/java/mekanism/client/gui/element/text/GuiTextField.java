package mekanism.client.gui.element.text;

import mekanism.api.functions.CharPredicate;
import mekanism.api.functions.CharUnaryOperator;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.lib.Color;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class GuiTextField extends GuiElement {

    public static final int DEFAULT_BORDER_COLOR = 0xFFA0A0A0;
    public static final int DEFAULT_BACKGROUND_COLOR = 0xFF000000;
    public static final int SCREEN_COLOR = screenTextColorStatic();
    public static final int DARK_SCREEN_COLOR = Color.argb(SCREEN_COLOR).darken(0.4).argb();

    private final net.minecraft.client.gui.GuiTextField textField;
    private CharPredicate inputValidator;
    private BiPredicate<Character, Integer> legacyInputValidator;
    private Predicate<String> textValidator = text -> true;
    private CharUnaryOperator inputTransformer;
    private UnaryOperator<String> pasteTransformer;
    private Runnable enterHandler;
    private Consumer<String> responder;
    private BackgroundType backgroundType = BackgroundType.DEFAULT;
    private IconType iconType;
    private MekanismImageButton checkmarkButton;
    private int offsetX;
    private int offsetY;
    private float textScale = 1.0F;
    private boolean allowColoredText;
    private boolean canLoseFocus = true;

    public GuiTextField(IGuiWrapper gui, int x, int y, int width, int height) {
        this(gui, 0, x, y, width, height);
    }

    public GuiTextField(IGuiWrapper gui, GuiElement parent, int x, int y, int width, int height) {
        this(gui, 0, x, y, width, height);
    }

    public GuiTextField(IGuiWrapper gui, int id, int x, int y, int width, int height) {
        super(gui, x, y, width, height, new TextComponentGroup());
        textField = new net.minecraft.client.gui.GuiTextField(id, getFont(), getX(), getY(), width, height);
        textField.setEnableBackgroundDrawing(false);
        active = true;
        playClickSound = false;
        gui.addFocusListener(this);
        updateTextField();
    }

    public GuiTextField setMaxLength(int maxLength) {
        textField.setMaxStringLength(maxLength);
        return this;
    }

    public GuiTextField setInputValidator(CharPredicate inputValidator) {
        this.inputValidator = inputValidator;
        this.legacyInputValidator = null;
        return this;
    }

    public GuiTextField setInputValidator(BiPredicate<Character, Integer> inputValidator) {
        this.legacyInputValidator = inputValidator;
        this.inputValidator = null;
        return this;
    }

    public GuiTextField setTextValidator(Predicate<String> textValidator) {
        this.textValidator = textValidator;
        textField.setValidator(textValidator::test);
        return this;
    }

    public GuiTextField setInputTransformer(CharUnaryOperator inputTransformer) {
        this.inputTransformer = inputTransformer;
        return this;
    }

    public GuiTextField setPasteTransformer(UnaryOperator<String> pasteTransformer) {
        this.pasteTransformer = pasteTransformer;
        return this;
    }

    public GuiTextField setEnterHandler(Runnable enterHandler) {
        this.enterHandler = enterHandler;
        return this;
    }

    public GuiTextField setResponder(Consumer<String> responder) {
        this.responder = responder;
        return this;
    }

    public GuiTextField setOffset(int offsetX, int offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        updateTextField();
        return this;
    }

    public GuiTextField setScale(float textScale) {
        this.textScale = textScale;
        updateTextField();
        return this;
    }

    public GuiTextField setBackgroundDrawing(boolean backgroundDrawing) {
        return setBackground(backgroundDrawing ? BackgroundType.DEFAULT : BackgroundType.NONE);
    }

    public GuiTextField setBackground(BackgroundType backgroundType) {
        this.backgroundType = backgroundType;
        return this;
    }

    public GuiTextField setIcon(IconType iconType) {
        this.iconType = iconType;
        updateTextField();
        return this;
    }

    public GuiTextField configureDigitalInput(Runnable enterHandler) {
        setBackground(BackgroundType.NONE);
        setIcon(IconType.DIGITAL);
        setTextColor(screenTextColor());
        setEnterHandler(enterHandler);
        addCheckmarkButton(ButtonType.DIGITAL, enterHandler);
        setScale(1.0F);
        return this;
    }

    public GuiTextField configureDigitalBorderInput(Runnable enterHandler) {
        setBackground(BackgroundType.DIGITAL);
        setTextColor(screenTextColor());
        setEnterHandler(enterHandler);
        addCheckmarkButton(ButtonType.DIGITAL, enterHandler);
        setScale(1.0F);
        return this;
    }

    public GuiTextField addCheckmarkButton(Runnable callback) {
        return addCheckmarkButton(ButtonType.NORMAL, callback);
    }

    public GuiTextField addCheckmarkButton(ButtonType type, Runnable callback) {
        checkmarkButton = addChild(type.getButton(this, () -> {
            callback.run();
            setFocused(true);
            gui().focusChange(this);
        }));
        checkmarkButton.active = false;
        updateTextField();
        return this;
    }

    public GuiTextField setTextColor(int color) {
        textField.setTextColor(color);
        return this;
    }

    public GuiTextField setDisabledTextColor(int color) {
        textField.setDisabledTextColour(color);
        return this;
    }

    public GuiTextField setTextColorUneditable(int color) {
        return setDisabledTextColor(color);
    }

    public GuiTextField setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
        textField.setCanLoseFocus(canLoseFocus);
        return this;
    }

    public GuiTextField allowColoredText() {
        allowColoredText = true;
        return this;
    }

    public GuiTextField setEditable(boolean editable) {
        textField.setEnabled(editable);
        active = editable;
        return this;
    }

    public GuiTextField setEnabled(boolean enabled) {
        return setEditable(enabled);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getText() {
        return textField.getText();
    }

    public boolean isEmpty() {
        return getText().isEmpty();
    }

    public void setText(String text) {
        textField.setText(text);
        notifyResponder();
        updateCheckmarkButton();
    }

    public void setTextSilently(String text) {
        textField.setText(text);
        updateCheckmarkButton();
    }

    public void clear() {
        setText("");
    }

    @Override
    public void onWindowClose() {
        super.onWindowClose();
        gui().removeFocusListener(this);
    }

    public boolean isFocused() {
        return textField.isFocused();
    }

    public boolean isTextFieldFocused() {
        return isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        if (canLoseFocus || focused) {
            super.setFocused(focused);
            textField.setFocused(focused);
        }
    }

    @Override
    public void tick() {
        super.tick();
        textField.updateCursorCounter();
    }

    @Override
    public void resize(int prevLeft, int prevTop, int left, int top) {
        super.resize(prevLeft, prevTop, left, top);
        updateTextField();
    }

    @Override
    public void move(int changeX, int changeY) {
        super.move(changeX, changeY);
        updateTextField();
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        backgroundType.render(this);
        drawTextBox();
        if (iconType != null) {
            MekanismRenderer.bindTexture(iconType.getIcon());
            mekanism.client.gui.GuiUtils.blit(relativeX + 2, relativeY + (height / 2) - (int) Math.ceil(iconType.getHeight() / 2F),
                  0, 0, iconType.getWidth(), iconType.getHeight(), iconType.getWidth(), iconType.getHeight());
            MekanismRenderer.resetColor();
        }
    }

    private void drawTextBox() {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-getGuiLeft(), -getGuiTop(), 0);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        MekanismRenderer.resetColor();
        if (textScale == 1.0F) {
            textField.drawTextBox();
        } else {
            float reverse = (1 - textScale) / textScale;
            GlStateManager.scale(textScale, textScale, textScale);
            GlStateManager.translate(textField.x * reverse, (textField.y + 4) * reverse, 0);
            textField.drawTextBox();
        }
        MekanismRenderer.resetColor();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double scaledX = mouseX;
        if (textScale != 1.0F && scaledX > textField.x) {
            scaledX = textField.x + (scaledX - textField.x) / textScale;
        }
        if (button == 1 && isMouseOver(mouseX, mouseY)) {
            clear();
            setFocused(true);
            gui().focusChange(this);
            return true;
        }
        boolean clicked = textField.mouseClicked((int) scaledX, (int) mouseY, button);
        if (clicked || isFocused()) {
            gui().focusChange(this);
        }
        return clicked || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return false;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            if (canLoseFocus) {
                gui().incrementFocus(this);
                return true;
            }
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (enterHandler != null) {
                enterHandler.run();
            }
            return true;
        }
        String previous = textField.getText();
        boolean typed = handleControlKey(keyCode);
        notifyResponderIfChanged(previous);
        updateCheckmarkButton();
        return typed;
    }

    @Override
    public boolean charTyped(char c, int keyCode) {
        if (!active || !visible || !isFocused()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (enterHandler != null) {
                enterHandler.run();
            }
            return true;
        }
        c = transform(c);
        if (!isValidChar(c, keyCode)) {
            return false;
        }
        String previous = textField.getText();
        boolean typed;
        if (allowColoredText && c == '\u00A7') {
            typed = writeTextAllowingColors(Character.toString(c));
        } else {
            typed = textField.textboxKeyTyped(c, keyCode);
        }
        notifyResponderIfChanged(previous);
        updateCheckmarkButton();
        return typed;
    }

    private boolean handleControlKey(int keyCode) {
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            return pasteClipboard();
        }
        return GuiMekanism.isTextboxKey('\0', keyCode) && textField.textboxKeyTyped('\0', keyCode);
    }

    private boolean pasteClipboard() {
        String previous = textField.getText();
        String text = GuiScreen.getClipboardString();
        if (pasteTransformer != null) {
            text = pasteTransformer.apply(text);
        }
        text = transform(text);
        if (!isValidTextInput(text)) {
            return false;
        }
        if (allowColoredText) {
            writeTextAllowingColors(text);
        } else {
            textField.writeText(text);
        }
        notifyResponderIfChanged(previous);
        updateCheckmarkButton();
        return true;
    }

    private char transform(char c) {
        return inputTransformer == null ? c : inputTransformer.applyAsChar(c);
    }

    private String transform(String text) {
        if (inputTransformer == null || text.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = transform(chars[i]);
        }
        return String.valueOf(chars);
    }

    private boolean isValidTextInput(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!isValidChar(text.charAt(i), 0)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidChar(char c, int keyCode) {
        if (legacyInputValidator != null) {
            return legacyInputValidator.test(c, keyCode);
        }
        return inputValidator == null || inputValidator.test(c);
    }

    private boolean writeTextAllowingColors(String textToWrite) {
        String filteredText = filterAllowedCharacters(textToWrite);
        String current = textField.getText();
        int cursor = textField.getCursorPosition();
        int selection = textField.getSelectionEnd();
        int start = Math.min(cursor, selection);
        int end = Math.max(cursor, selection);
        int maxInsert = textField.getMaxStringLength() - current.length() + (end - start);
        if (filteredText.length() > maxInsert) {
            filteredText = filteredText.substring(0, Math.max(maxInsert, 0));
        }
        String newText = current.substring(0, start) + filteredText + current.substring(end);
        if (textValidator.test(newText)) {
            textField.setText(newText);
            textField.setCursorPosition(start + filteredText.length());
            return !current.equals(newText);
        }
        return false;
    }

    private String filterAllowedCharacters(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' || ChatAllowedCharacters.isAllowedCharacter(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private void notifyResponderIfChanged(String previous) {
        if (!previous.equals(textField.getText())) {
            notifyResponder();
        }
    }

    private void notifyResponder() {
        if (responder != null) {
            responder.accept(textField.getText());
        }
    }

    private void updateTextField() {
        int iconOffsetX = iconType == null ? 0 : iconType.getOffsetX();
        textField.width = Math.round((width - (checkmarkButton == null ? 0 : textField.height + 2) - iconOffsetX) / textScale);
        textField.x = getX() + offsetX + 2 + iconOffsetX;
        textField.y = getY() + offsetY + 1 + (int) ((height / 2F) - 4);
        if (checkmarkButton != null) {
            checkmarkButton.move(getRelativeRight() - getHeight() - checkmarkButton.getRelativeX(), getRelativeY() - checkmarkButton.getRelativeY());
        }
    }

    private void updateCheckmarkButton() {
        if (checkmarkButton != null) {
            checkmarkButton.active = !isEmpty();
        }
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        if (element instanceof GuiTextField field) {
            setText(field.getText());
        }
    }

    private static int screenTextColorStatic() {
        return mekanism.client.SpecialColors.TEXT_SCREEN.argb();
    }
}
