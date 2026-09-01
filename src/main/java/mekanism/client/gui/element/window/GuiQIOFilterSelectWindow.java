package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.filter.QIOModIDFilter;
import mekanism.common.content.qio.filter.QIOOreDictFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Filter selector with one codec-aware exact-resource entry. */
public class GuiQIOFilterSelectWindow extends GuiWindow {

    private final TileEntityQIOFilterHandler tile;

    public GuiQIOFilterSelectWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile) {
        super(gui, (gui.getWidth() - 152) / 2, 20, 152, 90, SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        interactionStrategy = InteractionStrategy.CONTAINER;
        addChild(new GuiElementHolder(gui, relativeX + 11, relativeY + 18, 130, 62));
        addResourceButton(MekanismLang.QIO_RESOURCES.translate(), 19);
        addTextButton(true, MekanismLang.BUTTON_OREDICT_FILTER.translate(), 39);
        addTextButton(false, MekanismLang.BUTTON_MODID_FILTER.translate(), 59);
    }

    private void addResourceButton(ITextComponent label, int y) {
        addChild(new MekanismButton(gui(), relativeX + 12, relativeY + y, 128, 20,
              label,
              () -> {
                  IGuiWrapper parent = gui();
                  parent.addWindow(new GuiQIOResourceFilterWindow(parent, tile));
                  close();
              }, null));
    }

    private void addTextButton(boolean oreDictionary, ITextComponent label, int y) {
        addChild(new MekanismButton(gui(), relativeX + 12, relativeY + y, 128, 20, label, () -> {
            IGuiWrapper parent = gui();
            parent.addWindow(new GuiQIOTextFilterWindow(parent, tile,
                  oreDictionary ? new QIOOreDictFilter() : new QIOModIDFilter(), -1));
            close();
        }, null));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation("gui.filterSelect.title"), 6);
    }
}
