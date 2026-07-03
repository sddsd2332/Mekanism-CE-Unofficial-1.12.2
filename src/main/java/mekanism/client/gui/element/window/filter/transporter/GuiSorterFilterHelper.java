package mekanism.client.gui.element.window.filter.transporter;

import mekanism.api.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.filter.GuiFilterHelper;
import mekanism.common.MekanismLang;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.TransporterUtils;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

interface GuiSorterFilterHelper extends GuiFilterHelper<TileEntityLogisticalSorter> {

    int SORTER_FILTER_WIDTH = 200;

    TransporterFilter getSorterFilter();

    TileEntityLogisticalSorter getSorterTile();

    int getRelativeX();

    int getRelativeY();

    int getXSize();

    IGuiWrapper gui();

    <ELEMENT extends GuiElement> ELEMENT addSorterChild(ELEMENT element);

    @Override
    default TransporterFilter getFilter() {
        return getSorterFilter();
    }

    @Override
    default GuiSorterFilterSelect getFilterSelect(IGuiWrapper gui, TileEntityLogisticalSorter tile) {
        return new GuiSorterFilterSelect(gui, tile);
    }

    default void addSorterCommonControls(int slotOffset) {
        int relativeX = getRelativeX();
        int relativeY = getRelativeY();
        int slotX = relativeX + 7;
        int colorSlotY = relativeY + slotOffset + 25;
        addSorterChild(new GuiSlot(SlotType.NORMAL, gui(), slotX, colorSlotY));
        addSorterChild(new ColorButton(gui(), slotX + 1, colorSlotY + 1, 16, 16, () -> getSorterFilter().color, () -> {
            TransporterFilter filter = getSorterFilter();
            filter.color = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? null : TransporterUtils.increment(filter.color);
        }, () -> {
            TransporterFilter filter = getSorterFilter();
            filter.color = TransporterUtils.decrement(filter.color);
        }, () -> Collections.singletonList(getSorterFilter().color != null ? getSorterFilter().color.getColoredName() : LangUtils.localize("gui.none"))));
        addSorterChild(new MekanismImageButton(gui(), relativeX + 148, relativeY + 18, 11,
              MekanismUtils.getResource(ResourceType.GUI_BUTTON, "default.png"),
              () -> getSorterFilter().allowDefault = !getSorterFilter().allowDefault,
              (element, mouseX, mouseY) -> element.displayTooltip(MekanismLang.FILTER_ALLOW_DEFAULT.translate(), mouseX, mouseY)));
    }

    default void addSorterSizeControls(BiConsumer<GuiTextField, GuiTextField> rangeSetter) {
        int relativeX = getRelativeX();
        int relativeY = getRelativeY();
        GuiTextField minField = new GuiTextField(gui(), 3, relativeX + 174, relativeY + 31, 20, 11)
              .setMaxLength(2)
              .setInputValidator(this::isDigitOrTextKey);
        minField.setText(Integer.toString(getFilterMin()));
        addSorterChild(minField);
        GuiTextField maxField = new GuiTextField(gui(), 4, relativeX + 174, relativeY + 43, 20, 11)
              .setMaxLength(2)
              .setInputValidator(this::isDigitOrTextKey);
        maxField.setText(Integer.toString(getFilterMax()));
        addSorterChild(maxField);
        rangeSetter.accept(minField, maxField);
        addSorterChild(new TooltipToggleButton(gui(), relativeX + 148, relativeY + 56, 11, 14,
              MekanismUtils.getResource(ResourceType.GUI_BUTTON, "silk_touch.png"),
              () -> getSorterTile().singleItem && getFilterSizeMode(),
              () -> setFilterSizeMode(!getFilterSizeMode()),
              new TextComponentString(getSizeModeTooltip()), MekanismLang.SORTER_SIZE_MODE.translate()));
    }

    default boolean isDigitOrTextKey(char c, int keyCode) {
        return Character.isDigit(c) || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT ||
              keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END;
    }

    default boolean validateSorterFields(GuiTextField minField, GuiTextField maxField, Function<String, Boolean> errorHandler) {
        if (minField.isEmpty() || maxField.isEmpty()) {
            return errorHandler.apply("Max/min");
        }
        int min = Integer.parseInt(minField.getText());
        int max = Integer.parseInt(maxField.getText());
        if (max >= min && max <= 64) {
            setFilterMin(min);
            setFilterMax(max);
            return true;
        } else if (min > max) {
            return errorHandler.apply("Max<min");
        }
        return errorHandler.apply("Max>64");
    }

    default List<ITextComponent> addSorterCommonScreenText(List<ITextComponent> list) {
        list.add(new TextComponentString(LangUtils.localize("gui.allowDefault") + ": " + LangUtils.transOnOff(getSorterFilter().allowDefault)));
        return list;
    }

    default List<ITextComponent> addSorterSizeScreenText(List<ITextComponent> list) {
        list.add(new TextComponentString(LangUtils.localize("gui.itemFilter.min") + ": " + getFilterMin() + "  " +
              LangUtils.localize("gui.itemFilter.max") + ": " + getFilterMax()));
        String sizeMode = LangUtils.transOnOff(getFilterSizeMode());
        if (getSorterTile().singleItem && getFilterSizeMode()) {
            sizeMode = EnumColor.RED + sizeMode + "!";
        }
        list.add(new TextComponentString(LangUtils.localize("gui.sizeMode") + ": " + sizeMode));
        return list;
    }

    default String getSizeModeTooltip() {
        String sizeModeTooltip = LangUtils.localize("gui.sizeMode");
        if (getSorterTile().singleItem && getFilterSizeMode()) {
            sizeModeTooltip += " - " + LangUtils.localize("mekanism.gui.sizeModeConflict");
        }
        return sizeModeTooltip;
    }

    default int getFilterMin() {
        return 0;
    }

    default int getFilterMax() {
        return 0;
    }

    default void setFilterMin(int min) {
    }

    default void setFilterMax(int max) {
    }

    default boolean getFilterSizeMode() {
        return false;
    }

    default void setFilterSizeMode(boolean sizeMode) {
    }
}
