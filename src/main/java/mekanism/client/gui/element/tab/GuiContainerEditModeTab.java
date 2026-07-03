package mekanism.client.gui.element.tab;

import mekanism.api.Coord4D;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.base.IFluidContainerManager;
import mekanism.common.network.PacketContainerEditMode.ContainerEditModeMessage;
import mekanism.common.util.MekanismUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

public class GuiContainerEditModeTab<TILE extends TileEntity & IFluidContainerManager> extends GuiInsetElement<TILE> {

    private static final ResourceLocation BOTH = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "container_edit_mode_both.png");
    private static final ResourceLocation FILL = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "container_edit_mode_fill.png");
    private static final ResourceLocation EMPTY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "container_edit_mode_empty.png");

    public GuiContainerEditModeTab(IGuiWrapper gui, TILE tile) {
        this(gui, tile, 138);
    }

    public GuiContainerEditModeTab(IGuiWrapper gui, TILE tile, int y) {
        super(BOTH, gui, tile, gui.getWidth(), y, 26, 18, false);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_CONTAINER_EDIT_MODE.argb());
    }

    @Override
    protected ResourceLocation getOverlay() {
        return switch (dataSource.getContainerEditMode()) {
            case FILL -> FILL;
            case EMPTY -> EMPTY;
            default -> super.getOverlay();
        };
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentString(dataSource.getContainerEditMode().getDisplay()), mouseX, mouseY);
    }

    @Override
    public boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        setMode(button == 1 ? -1 : 1);
    }

    private void setMode(int shift) {
        Mekanism.packetHandler.sendToServer(new ContainerEditModeMessage(Coord4D.get(dataSource), dataSource.getContainerEditMode().adjust(shift)));
    }
}
