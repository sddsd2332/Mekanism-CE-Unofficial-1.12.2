package mekanism.client.gui.element.window.filter;

import mekanism.api.text.ILangEntry;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

@SideOnly(Side.CLIENT)
public abstract class GuiFilterSelect<TILE extends TileEntityContainerBlock> extends GuiWindow {

    private static final int FILTER_HEIGHT = 20;
    protected final TILE tile;

    protected GuiFilterSelect(IGuiWrapper gui, TILE tile, int filterCount) {
        super(gui, (gui.getWidth() - 152) / 2, 20, 152, 30 + filterCount * FILTER_HEIGHT, SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        addChild(new GuiElementHolder(gui, relativeX + 11, relativeY + 18, 130, 2 + filterCount * FILTER_HEIGHT));
        int buttonY = relativeY + 19;
        buttonY = addFilterButton(buttonY, getItemStackFilterLabel(), getItemStackFilterCreator());
        buttonY = addFilterButton(buttonY, getOreDictFilterLabel(), getOreDictFilterCreator());
        buttonY = addFilterButton(buttonY, getMaterialFilterLabel(), getMaterialFilterCreator());
        addFilterButton(buttonY, getModIDFilterLabel(), getModIDFilterCreator());
    }

    private int addFilterButton(int buttonY, ILangEntry translationHelper, @Nullable GuiFilterCreator<TILE> filterSupplier) {
        if (filterSupplier == null) {
            return buttonY;
        }
        addChild(new TranslationButton(gui(), relativeX + 12, buttonY, 128, FILTER_HEIGHT, translationHelper, () -> {
            IGuiWrapper gui = gui();
            gui.addWindow(filterSupplier.create(gui, tile));
            close();
        }));
        return buttonY + FILTER_HEIGHT;
    }

    @Nullable
    protected GuiFilterCreator<TILE> getItemStackFilterCreator() {
        return null;
    }

    @Nullable
    protected GuiFilterCreator<TILE> getOreDictFilterCreator() {
        return null;
    }

    @Nullable
    protected GuiFilterCreator<TILE> getMaterialFilterCreator() {
        return null;
    }

    @Nullable
    protected GuiFilterCreator<TILE> getModIDFilterCreator() {
        return null;
    }

    protected ILangEntry getItemStackFilterLabel() {
        return MekanismLang.BUTTON_ITEMSTACK_FILTER;
    }

    protected ILangEntry getOreDictFilterLabel() {
        return MekanismLang.BUTTON_OREDICT_FILTER;
    }

    protected ILangEntry getMaterialFilterLabel() {
        return MekanismLang.BUTTON_MATERIAL_FILTER;
    }

    protected ILangEntry getModIDFilterLabel() {
        return MekanismLang.BUTTON_MODID_FILTER;
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentString(LangUtils.localize("gui.filterSelect.title")), 6);
    }

    @FunctionalInterface
    protected interface GuiFilterCreator<TILE extends TileEntityContainerBlock> {

        GuiFilter<?, ?> create(IGuiWrapper gui, TILE tile);
    }
}
