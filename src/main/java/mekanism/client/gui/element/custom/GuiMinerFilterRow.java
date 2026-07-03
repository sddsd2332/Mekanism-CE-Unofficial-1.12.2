package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.window.filter.miner.GuiMinerItemStackFilter;
import mekanism.client.gui.element.window.filter.miner.GuiMinerMaterialFilter;
import mekanism.client.gui.element.window.filter.miner.GuiMinerModIDFilter;
import mekanism.client.gui.element.window.filter.miner.GuiMinerOreDictFilter;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.content.filter.IItemStackFilter;
import mekanism.common.content.filter.IMaterialFilter;
import mekanism.common.content.filter.IModIDFilter;
import mekanism.common.content.filter.IOreDictFilter;
import mekanism.common.content.miner.*;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import java.util.List;
import java.util.function.IntSupplier;

public class GuiMinerFilterRow extends GuiElement {

    private static final ResourceLocation ARROW_UP = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "arrow_up.png");
    private static final ResourceLocation ARROW_DOWN = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "arrow_down.png");
    private static final int ARROW_WIDTH = 11;
    private static final int UP_ARROW_HEIGHT = 7;
    private static final int DOWN_ARROW_HEIGHT = 7;
    private static final int TOGGLE_SIZE = 8;

    private final TileEntityDigitalMiner tile;
    private final int row;
    private final IntSupplier scrollSupplier;
    private List<ItemStack> cyclingStacks;
    private int stackIndex = -1;
    private int stackSwitch;
    private MinerFilter cachedFilter;
    private ItemStack renderStack = ItemStack.EMPTY;

    public GuiMinerFilterRow(IGuiWrapper gui, TileEntityDigitalMiner tile, int x, int y, int width, int height, int row, IntSupplier scrollSupplier) {
        super(gui, x, y, width, height);
        this.tile = tile;
        this.row = row;
        this.scrollSupplier = scrollSupplier;
        playClickSound = true;
    }

    @Override
    public void tick() {
        super.tick();
        MinerFilter filter = getFilter();
        if (filter == null) {
            cachedFilter = null;
            cyclingStacks = null;
            renderStack = ItemStack.EMPTY;
            stackIndex = -1;
            return;
        }
        if (filter != cachedFilter) {
            cachedFilter = filter;
            cyclingStacks = null;
            renderStack = ItemStack.EMPTY;
            stackIndex = -1;
            stackSwitch = 0;
        }
        if (filter instanceof IOreDictFilter oreFilter) {
            updateCyclingStackList(OreDictCache.getOreDictStacks(oreFilter.getOreDictName(), true));
        } else if (filter instanceof IModIDFilter modFilter) {
            updateCyclingStackList(OreDictCache.getModIDStacks(modFilter.getModID(), true));
        } else {
            cyclingStacks = null;
            stackIndex = -1;
        }
        if (stackSwitch > 0) {
            stackSwitch--;
        }
        if (stackSwitch == 0 && cyclingStacks != null) {
            setNextRenderStack();
            stackSwitch = 20;
        }
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        MinerFilter filter = getFilter();
        if (filter == null) {
            return;
        }
        if (filter instanceof IItemStackFilter) {
            MekanismRenderer.color(EnumColor.INDIGO, 1.0F, 2.5F);
        } else if (filter instanceof IOreDictFilter) {
            MekanismRenderer.color(EnumColor.BRIGHT_GREEN, 1.0F, 2.5F);
        } else if (filter instanceof IMaterialFilter) {
            MekanismRenderer.color(EnumColor.PURPLE, 1.0F, 4F);
        } else if (filter instanceof IModIDFilter) {
            MekanismRenderer.color(EnumColor.PINK, 1.0F, 2.5F);
        }
        renderButtonBackground();
        MekanismRenderer.resetColor();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        MinerFilter filter = getFilter();
        if (filter == null) {
            return;
        }
        int filterIndex = getFilterIndex();
        ItemStack stack = getRenderStack(filter);
        gui().renderItem(stack, relativeX + 3, relativeY + 3);
        int textWidth = width - 48;
        drawScaledScrollingString(new TextComponentString(getFilterName(filter)), 22, 2, TextAlignment.LEFT, 0x404040, textWidth, 3, false, 1, GuiElement.getMillis());
        drawScaledScrollingString(new TextComponentString(getFilterDetail(filter)), 22, 13, TextAlignment.LEFT, 0x404040, textWidth, 3, false, 0.8F,
              GuiElement.getMillis());
        drawEnabledToggle(filter);
        int arrowX = relativeX + width - 12;
        if (filterIndex > 0) {
            drawArrow(arrowX, relativeY + 1, isMouseOverUp(mouseX, mouseY), true);
        }
        if (filterIndex < tile.getFilterManager().count() - 1) {
            drawArrow(arrowX, relativeY + height - 8, isMouseOverDown(mouseX, mouseY), false);
        }
        super.renderForeground(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clicked(mouseX, mouseY) && getFilter() != null) {
            int filterIndex = getFilterIndex();
            if (filterIndex > 0 && isMouseOverUp(mouseX, mouseY)) {
                sendMovePacket(11, filterIndex);
                playDownSound(minecraft.getSoundHandler());
                return true;
            } else if (filterIndex < tile.getFilterManager().count() - 1 && isMouseOverDown(mouseX, mouseY)) {
                sendMovePacket(12, filterIndex);
                playDownSound(minecraft.getSoundHandler());
                return true;
            } else if (isMouseOverToggle(mouseX, mouseY)) {
                sendMovePacket(13, filterIndex);
                playDownSound(minecraft.getSoundHandler());
                return true;
            }
            if (openFilterWindow(getFilter())) {
                playDownSound(minecraft.getSoundHandler());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        MinerFilter filter = getFilter();
        if (filter == null) {
            return;
        }
        int filterIndex = getFilterIndex();
        if (filterIndex > 0 && isMouseOverUp(mouseX, mouseY)) {
            displayTooltip(new TextComponentString(LangUtils.localize("gui.moveUp")), mouseX, mouseY);
        } else if (filterIndex < tile.getFilterManager().count() - 1 && isMouseOverDown(mouseX, mouseY)) {
            displayTooltip(new TextComponentString(LangUtils.localize("gui.moveDown")), mouseX, mouseY);
        } else if (isMouseOverToggle(mouseX, mouseY)) {
            displayTooltip(new TextComponentString(LangUtils.localize("gui.filter") + ": " + LangUtils.transOnOff(filter.isEnabled())), mouseX, mouseY);
        }
    }

    private void updateCyclingStackList(List<ItemStack> stacks) {
        if (cyclingStacks != stacks) {
            cyclingStacks = stacks;
            stackIndex = -1;
            stackSwitch = 0;
            renderStack = ItemStack.EMPTY;
        }
    }

    private void setNextRenderStack() {
        if (cyclingStacks == null || cyclingStacks.isEmpty()) {
            renderStack = ItemStack.EMPTY;
            return;
        }
        stackIndex = stackIndex == -1 || stackIndex == cyclingStacks.size() - 1 ? 0 : stackIndex + 1;
        renderStack = cyclingStacks.get(stackIndex);
    }

    private ItemStack getRenderStack(MinerFilter filter) {
        if (filter instanceof IItemStackFilter itemFilter) {
            return itemFilter.getItemStack();
        } else if (filter instanceof IMaterialFilter materialFilter) {
            return materialFilter.getMaterialItem();
        } else if (filter instanceof IOreDictFilter || filter instanceof IModIDFilter) {
            return renderStack;
        }
        return ItemStack.EMPTY;
    }

    private String getFilterName(MinerFilter filter) {
        if (filter instanceof IItemStackFilter) {
            return MekanismLang.ITEM_FILTER.translate().getFormattedText();
        } else if (filter instanceof IOreDictFilter) {
            return MekanismLang.TAG_FILTER.translate().getFormattedText();
        } else if (filter instanceof IMaterialFilter) {
            return LangUtils.localize("gui.materialFilter");
        } else if (filter instanceof IModIDFilter) {
            return MekanismLang.MODID_FILTER.translate().getFormattedText();
        }
        return MekanismLang.FILTER.translate().getFormattedText();
    }

    private String getFilterDetail(MinerFilter filter) {
        if (filter instanceof IOreDictFilter oreFilter) {
            return oreFilter.getOreDictName();
        } else if (filter instanceof IModIDFilter modFilter) {
            return modFilter.getModID();
        } else if (filter instanceof IItemStackFilter itemFilter) {
            ItemStack stack = itemFilter.getItemStack();
            return stack.isEmpty() ? LangUtils.localize("gui.none") : stack.getDisplayName();
        } else if (filter instanceof IMaterialFilter materialFilter) {
            ItemStack stack = materialFilter.getMaterialItem();
            return stack.isEmpty() ? LangUtils.localize("gui.none") : stack.getDisplayName();
        }
        return "";
    }

    private boolean openFilterWindow(MinerFilter filter) {
        if (filter instanceof MItemStackFilter itemFilter) {
            gui().addWindow(GuiMinerItemStackFilter.edit(gui(), tile, itemFilter));
        } else if (filter instanceof MOreDictFilter oreFilter) {
            gui().addWindow(GuiMinerOreDictFilter.edit(gui(), tile, oreFilter));
        } else if (filter instanceof MMaterialFilter materialFilter) {
            gui().addWindow(GuiMinerMaterialFilter.edit(gui(), tile, materialFilter));
        } else if (filter instanceof MModIDFilter modIDFilter) {
            gui().addWindow(GuiMinerModIDFilter.edit(gui(), tile, modIDFilter));
        } else {
            return false;
        }
        return true;
    }

    private MinerFilter getFilter() {
        int index = getFilterIndex();
        return index >= 0 && index < tile.getFilterManager().count() ? tile.getFilterManager().getFilters().get(index) : null;
    }

    private int getFilterIndex() {
        return scrollSupplier.getAsInt() + row;
    }

    private void sendMovePacket(int type, int filterIndex) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tile, TileNetworkList.withContents(type, filterIndex)));
    }

    private boolean isMouseOverUp(double mouseX, double mouseY) {
        int arrowX = getX() + width - 12;
        return mouseX >= arrowX && mouseX <= arrowX + ARROW_WIDTH && mouseY >= getY() + 1 && mouseY <= getY() + 1 + UP_ARROW_HEIGHT;
    }

    private boolean isMouseOverDown(double mouseX, double mouseY) {
        int arrowX = getX() + width - 12;
        return mouseX >= arrowX && mouseX <= arrowX + ARROW_WIDTH && mouseY >= getY() + height - 8 && mouseY <= getY() + height - 8 + DOWN_ARROW_HEIGHT;
    }

    private boolean isMouseOverToggle(double mouseX, double mouseY) {
        int toggleX = getX() + width - 24;
        int toggleY = getY() + height - 11;
        return mouseX >= toggleX && mouseX <= toggleX + TOGGLE_SIZE && mouseY >= toggleY && mouseY <= toggleY + TOGGLE_SIZE;
    }

    private void drawEnabledToggle(MinerFilter filter) {
        int toggleX = relativeX + width - 24;
        int toggleY = relativeY + height - 11;
        drawRect(toggleX, toggleY, toggleX + TOGGLE_SIZE, toggleY + TOGGLE_SIZE, 0xFF202020);
        drawRect(toggleX + 1, toggleY + 1, toggleX + TOGGLE_SIZE - 1, toggleY + TOGGLE_SIZE - 1, filter.isEnabled() ? 0xFF36C45B : 0xFFC43E36);
    }

    private void renderButtonBackground() {
        minecraft.renderEngine.bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "button.png"));
        int x = getButtonX();
        int y = getButtonY();
        GuiUtils.blit(x, y, 0, isHovered() ? 40 : 20, width / 2, height / 2, 200, 60);
        GuiUtils.blit(x, y + height / 2, 0, (isHovered() ? 40 : 20) + 20 - height / 2, width / 2, height - height / 2, 200, 60);
        GuiUtils.blit(x + width / 2, y, 200 - width / 2, isHovered() ? 40 : 20, width - width / 2, height / 2, 200, 60);
        GuiUtils.blit(x + width / 2, y + height / 2, 200 - (width - width / 2), (isHovered() ? 40 : 20) + 20 - (height - height / 2),
              width - width / 2, height - height / 2, 200, 60);
    }

    private void drawArrow(int x, int y, boolean hovered, boolean up) {
        minecraft.renderEngine.bindTexture(up ? ARROW_UP : ARROW_DOWN);
        GuiUtils.blit(x, y, 0, 0, ARROW_WIDTH, up ? UP_ARROW_HEIGHT : DOWN_ARROW_HEIGHT, ARROW_WIDTH, up ? UP_ARROW_HEIGHT : DOWN_ARROW_HEIGHT);
    }
}
