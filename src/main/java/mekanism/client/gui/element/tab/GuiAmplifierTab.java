package mekanism.client.gui.element.tab;

import mekanism.api.TileNetworkList;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

public class GuiAmplifierTab extends GuiInsetElement<TileEntityLaserAmplifier> {

    private static final ResourceLocation OFF = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "amplifier_off.png");
    private static final ResourceLocation ENTITY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "amplifier_entity.png");
    private static final ResourceLocation CONTENTS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "amplifier_contents.png");

    public GuiAmplifierTab(IGuiWrapper gui, TileEntityLaserAmplifier tile) {
        super(OFF, gui, tile, gui.getWidth(), 109, 26, 18, false);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_LASER_AMPLIFIER.argb());
    }

    @Override
    protected ResourceLocation getOverlay() {
        return switch (dataSource.getOutputMode()) {
            case ENTITY_DETECTION -> ENTITY;
            case ENERGY_CONTENTS -> CONTENTS;
            default -> super.getOverlay();
        };
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentString(LangUtils.localize("gui.redstoneOutput") + ": " + dataSource.getOutputMode().getName()), mouseX, mouseY);
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
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(dataSource, TileNetworkList.withContents(button == 1 ? 4 : 3)));
    }
}
