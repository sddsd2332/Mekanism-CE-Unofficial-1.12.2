package mekanism.client.gui.element.tab;

import mekanism.api.Coord4D;
import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.network.PacketRedstoneControl.RedstoneControlMessage;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

public class GuiRedstoneControlTab<TILE extends net.minecraft.tileentity.TileEntity & IRedstoneControl> extends GuiInsetElement<TILE> {

    private static final ResourceLocation DISABLED = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "redstone_control_disabled.png");
    private static final ResourceLocation HIGH = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "redstone_control_high.png");
    private static final ResourceLocation LOW = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "redstone_control_low.png");

    public GuiRedstoneControlTab(IGuiWrapper gui, TILE tile) {
        this(gui, tile, 137);
    }

    public GuiRedstoneControlTab(IGuiWrapper gui, TILE tile, int y) {
        super(DISABLED, gui, tile, gui.getWidth(), y, 26, 18, false);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_REDSTONE_CONTROL.argb());
    }

    @Override
    protected ResourceLocation getOverlay() {
        return switch (dataSource.getControlType()) {
            case HIGH -> HIGH;
            case LOW -> LOW;
            default -> super.getOverlay();
        };
    }

    @Override
    protected void drawBackgroundOverlay() {
        if (dataSource.getControlType() == RedstoneControl.PULSE && MekanismRenderer.redstonePulse != null) {
            drawTiledSprite(getButtonX() + 1, getButtonY() + 1, innerHeight - 2, innerWidth - 2, innerHeight - 2, MekanismRenderer.redstonePulse,
                  TilingDirection.DOWN_RIGHT);
            MekanismRenderer.resetColor();
        } else {
            super.drawBackgroundOverlay();
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentString(dataSource.getControlType().getDisplay()), mouseX, mouseY);
    }

    @Override
    public boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        sendModeChange(button);
    }

    private void sendModeChange(int button) {
        RedstoneControl current = dataSource.getControlType();
        int shift = button == 1 ? -1 : 1;
        int ordinalToSet = (current.ordinal() + shift + RedstoneControl.values().length) % RedstoneControl.values().length;
        RedstoneControl next = RedstoneControl.values()[ordinalToSet];
        if (next == RedstoneControl.PULSE && !dataSource.canPulse()) {
            ordinalToSet = button == 1 ? RedstoneControl.values().length - 2 : 0;
            next = RedstoneControl.values()[ordinalToSet];
        }
        Mekanism.packetHandler.sendToServer(new RedstoneControlMessage(Coord4D.get(dataSource), next));
    }
}
