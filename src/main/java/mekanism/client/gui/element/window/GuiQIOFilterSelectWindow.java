package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.filter.QIOModIDFilter;
import mekanism.common.content.qio.filter.QIOOreDictFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Resource-kind selector used before creating a mixed QIO filter. */
public class GuiQIOFilterSelectWindow extends GuiWindow {

    private final TileEntityQIOFilterHandler tile;

    public GuiQIOFilterSelectWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile) {
        super(gui, (gui.getWidth() - 152) / 2, 20, 152, 130, SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        interactionStrategy = InteractionStrategy.CONTAINER;
        addChild(new GuiElementHolder(gui, relativeX + 11, relativeY + 18, 130, 102));
        addResourceButton(QIOResourceKind.ITEM, MekanismLang.BUTTON_ITEMSTACK_FILTER.translate(), 19);
        addTextButton(true, MekanismLang.BUTTON_OREDICT_FILTER.translate(), 39);
        addTextButton(false, MekanismLang.BUTTON_MODID_FILTER.translate(), 59);
        addResourceButton(QIOResourceKind.FLUID, new TextComponentTranslation("gui.qio.resource.fluid"), 79);
        addResourceButton(QIOResourceKind.GAS, new TextComponentTranslation("gui.qio.resource.gas"), 99);
    }

    private void addResourceButton(QIOResourceKind kind, ITextComponent label, int y) {
        addChild(new MekanismButton(gui(), relativeX + 12, relativeY + y, 128, 20,
              label,
              () -> {
                  IGuiWrapper parent = gui();
                  parent.addWindow(new GuiQIOResourceFilterWindow(parent, tile, kind));
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
