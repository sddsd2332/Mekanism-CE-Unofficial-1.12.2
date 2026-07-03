package mekanism.client.gui.element.window.filter;

import mekanism.api.functions.CharPredicate;
import mekanism.api.functions.CharUnaryOperator;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;

import javax.annotation.Nullable;
import java.util.Locale;

@SuppressWarnings("deprecation")
public abstract class GuiTextFilter<FILTER extends IFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> extends GuiFilter<FILTER, TILE> {

    protected GuiTextField text;

    protected GuiTextFilter(IGuiWrapper gui, int x, int y, int width, int height, String filterName, TILE tile, FILTER origFilter) {
        super(gui, x, y, width, height, filterName, tile, origFilter);
    }

    @Override
    protected void init() {
        super.init();
        text = addChild(new GuiTextField(gui(), this, getTextFieldX(), relativeY + 4 + getScreenHeight(), getTextFieldWidth(), 12)
              .setMaxLength(TransporterFilter.MAX_LENGTH)
              .setBackgroundDrawing(isTextBackgroundEnabled())
              .setTextColor(getTextColor())
              .setInputValidator(getInputValidator())
              .setInputTransformer(getInputTransformer())
              .configureDigitalInput(this::setText)
              .setEditable(true));
        setFocusedChild(text);
    }

    protected int getTextFieldX() {
        return relativeX + 31;
    }

    protected int getTextFieldWidth() {
        return getScreenWidth() - 4;
    }

    protected boolean isTextBackgroundEnabled() {
        return false;
    }

    protected int getTextColor() {
        return 0xFF3CFE9A;
    }

    protected CharPredicate getInputValidator() {
        return c -> Character.isLetter(c) || Character.isDigit(c) || TransporterFilter.SPECIAL_CHARS.contains(c);
    }

    @Nullable
    protected CharUnaryOperator getInputTransformer() {
        return c -> {
            if (c >= 'A' && c <= 'Z') {
                return Character.toString(c).toLowerCase(Locale.ROOT).charAt(0);
            }
            return c;
        };
    }

    @Override
    protected void validateAndSave() {
        if (text == null || text.getText().isEmpty() || setText()) {
            super.validateAndSave();
        }
    }

    protected abstract boolean setText();
}
