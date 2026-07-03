package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.content.filter.*;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;

public class MovableFilterButton extends FilterButton {

    private static final java.util.List<String> MOVE_UP = Arrays.asList(mekanism.common.MekanismLang.MOVE_UP.translate().getFormattedText(),
          mekanism.common.MekanismLang.MOVE_TO_TOP.translate().getFormattedText());
    private static final java.util.List<String> MOVE_DOWN = Arrays.asList(mekanism.common.MekanismLang.MOVE_DOWN.translate().getFormattedText(),
          mekanism.common.MekanismLang.MOVE_TO_BOTTOM.translate().getFormattedText());

    private final FilterSelectButton upButton;
    private final FilterSelectButton downButton;

    public MovableFilterButton(IGuiWrapper gui, int x, int y, int index, IntSupplier filterIndex, FilterManager<?> filterManager, IntConsumer upButtonPress,
          IntConsumer downButtonPress, ObjIntConsumer<IFilter> onPress, IntConsumer toggleButtonPress, Function<IFilter, List<ItemStack>> renderStackSupplier) {
        super(gui, x, y, TEXTURE_WIDTH, TEXTURE_HEIGHT / 2, index, filterIndex, filterManager, onPress, toggleButtonPress, renderStackSupplier);
        int arrowX = relativeX + width - 14;
        int halfHeight = height / 2;
        upButton = addChild(new FilterSelectButton(gui, arrowX, relativeY + halfHeight - 8, false, (element, mouseX, mouseY) -> {
            upButtonPress.accept(getActualIndex());
            return true;
        }));
        downButton = addChild(new FilterSelectButton(gui, arrowX, relativeY + halfHeight + 1, true, (element, mouseX, mouseY) -> {
            downButtonPress.accept(getActualIndex());
            return true;
        }));
    }

    @Override
    protected int getToggleXShift() {
        return 17;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        if (!visible) {
            return;
        }
        IFilter filter = getFilter();
        EnumColor color = getFilterColor(filter);
        if (color != null) {
            GuiUtils.fill(getButtonX(), getButtonY(), getButtonX() + getButtonWidth(), getButtonY() + getButtonHeight(), MekanismRenderer.getColorARGB(color, 0.3F));
        }
        updateButtonVisibility(filter);
    }

    @Override
    protected void setVisibility(boolean visible) {
        super.setVisibility(visible);
        if (visible) {
            updateButtonVisibility(getFilter());
        } else {
            upButton.visible = false;
            downButton.visible = false;
        }
    }

    private void updateButtonVisibility(@Nullable IFilter filter) {
        int actualIndex = getActualIndex();
        upButton.visible = filter != null && actualIndex > 0;
        downButton.visible = filter != null && actualIndex < filterManager.count() - 1;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (!visible) {
            return;
        }
        int actualIndex = getActualIndex();
        if (actualIndex > 0 && upButton.isMouseOver(mouseX, mouseY)) {
            displayTooltips(MOVE_UP, mouseX, mouseY);
        } else if (actualIndex < filterManager.count() - 1 && downButton.isMouseOver(mouseX, mouseY)) {
            displayTooltips(MOVE_DOWN, mouseX, mouseY);
        }
    }

    @Nullable
    private EnumColor getFilterColor(@Nullable IFilter filter) {
        if (filter instanceof IItemStackFilter) {
            return EnumColor.INDIGO;
        } else if (filter instanceof IOreDictFilter) {
            return EnumColor.BRIGHT_GREEN;
        } else if (filter instanceof IMaterialFilter) {
            return EnumColor.PURPLE;
        } else if (filter instanceof IModIDFilter) {
            return EnumColor.PINK;
        }
        return null;
    }
}
