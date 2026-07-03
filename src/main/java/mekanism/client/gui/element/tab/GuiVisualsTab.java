package mekanism.client.gui.element.tab;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.base.IHasVisualization;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;

public class GuiVisualsTab<TILE extends TileEntity & IHasVisualization> extends GuiInsetElement<TILE> {

    private static final ResourceLocation VISUALS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "visuals.png");

    public GuiVisualsTab(IGuiWrapper gui, TILE tile) {
        super(VISUALS, gui, tile, -26, 6, 26, 18, true);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_VISUALS.argb());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        String visuals = LangUtils.localize("gui.visuals") + ": " + LangUtils.transOnOff(dataSource.isClientRendering());
        if (dataSource.canDisplayVisuals()) {
            displayTooltips(Arrays.asList(visuals), mouseX, mouseY);
        } else {
            displayTooltips(Arrays.asList(visuals, TextFormatting.RED + LangUtils.localize("mekanism.gui.visuals.toobig")), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        dataSource.toggleClientRendering();
    }
}
