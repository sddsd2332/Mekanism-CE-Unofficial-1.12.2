package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.GuiOredictionificatorFilter;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.machine.TileEntityOredictionificator;
import mekanism.common.tile.machine.TileEntityOredictionificator.OredictionificatorFilter;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;
import java.util.function.IntSupplier;

public class GuiOredictionificatorFilterRow extends GuiElement {

    private static final int TOGGLE_SIZE = 8;

    private final TileEntityOredictionificator tile;
    private final int row;
    private final IntSupplier scrollSupplier;

    public GuiOredictionificatorFilterRow(IGuiWrapper gui, TileEntityOredictionificator tile, int x, int y, int width, int height, int row,
          IntSupplier scrollSupplier) {
        super(gui, x, y, width, height);
        this.tile = tile;
        this.row = row;
        this.scrollSupplier = scrollSupplier;
        playClickSound = true;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        OredictionificatorFilter filter = getFilter();
        if (filter == null) {
            return;
        }
        if (isHovered()) {
            MekanismRenderer.color(EnumColor.GREY);
        }
        renderButtonBackground();
        if (isHovered()) {
            MekanismRenderer.resetColor();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        OredictionificatorFilter filter = getFilter();
        if (filter == null) {
            return;
        }
        ItemStack renderStack = getRenderStack(filter);
        gui().renderItem(renderStack, relativeX + 3, relativeY + 3);
        int textWidth = width - 40;
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.filter")), 22, 2, TextAlignment.LEFT, 0x404040, textWidth, 3, false, 1,
              GuiElement.getMillis());
        drawScaledScrollingString(new TextComponentString(filter.filter), 22, 11, TextAlignment.LEFT, 0x404040, textWidth, 3, false, 0.8F,
              GuiElement.getMillis());
        drawEnabledToggle(filter);
        super.renderForeground(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        OredictionificatorFilter filter = getFilter();
        if (button == 0 && clicked(mouseX, mouseY) && filter != null) {
            if (isMouseOverToggle(mouseX, mouseY)) {
                Mekanism.packetHandler.sendToServer(new TileEntityMessage(tile, TileNetworkList.withContents(0, getFilterIndex())));
                playDownSound(minecraft.getSoundHandler());
                return true;
            }
            gui().addWindow(GuiOredictionificatorFilter.edit(gui(), tile, filter));
            playDownSound(minecraft.getSoundHandler());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        OredictionificatorFilter filter = getFilter();
        if (filter != null && isMouseOverToggle(mouseX, mouseY)) {
            displayTooltip(new TextComponentString(LangUtils.localize("gui.filter") + ": " + LangUtils.transOnOff(filter.isEnabled())), mouseX, mouseY);
        }
    }

    private OredictionificatorFilter getFilter() {
        int index = getFilterIndex();
        return index >= 0 && index < tile.getFilterManager().count() ? tile.getFilterManager().getFilters().get(index) : null;
    }

    private int getFilterIndex() {
        return scrollSupplier.getAsInt() + row;
    }

    private ItemStack getRenderStack(OredictionificatorFilter filter) {
        if (filter.filter == null || filter.filter.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> stacks = OreDictionary.getOres(filter.filter, false);
        return stacks.size() - 1 >= filter.index ? stacks.get(filter.index).copy() : ItemStack.EMPTY;
    }

    private boolean isMouseOverToggle(double mouseX, double mouseY) {
        int toggleX = getX() + width - 12;
        int toggleY = getY() + height - 11;
        return mouseX >= toggleX && mouseX <= toggleX + TOGGLE_SIZE && mouseY >= toggleY && mouseY <= toggleY + TOGGLE_SIZE;
    }

    private void drawEnabledToggle(OredictionificatorFilter filter) {
        int toggleX = relativeX + width - 12;
        int toggleY = relativeY + height - 11;
        drawRect(toggleX, toggleY, toggleX + TOGGLE_SIZE, toggleY + TOGGLE_SIZE, 0xFF202020);
        drawRect(toggleX + 1, toggleY + 1, toggleX + TOGGLE_SIZE - 1, toggleY + TOGGLE_SIZE - 1, filter.isEnabled() ? 0xFF36C45B : 0xFFC43E36);
    }

    private void renderButtonBackground() {
        minecraft.renderEngine.bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "button.png"));
        int x = getButtonX();
        int y = getButtonY();
        GuiUtils.blit(x, y, 0, 20, width / 2, height / 2, 200, 60);
        GuiUtils.blit(x, y + height / 2, 0, 20 + 20 - height / 2, width / 2, height - height / 2, 200, 60);
        GuiUtils.blit(x + width / 2, y, 200 - width / 2, 20, width - width / 2, height / 2, 200, 60);
        GuiUtils.blit(x + width / 2, y + height / 2, 200 - (width - width / 2), 20 + 20 - (height - height / 2), width - width / 2, height - height / 2, 200, 60);
    }
}
