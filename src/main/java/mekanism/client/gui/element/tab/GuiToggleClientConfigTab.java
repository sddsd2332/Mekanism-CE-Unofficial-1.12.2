package mekanism.client.gui.element.tab;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.config.options.BooleanOption;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

/** Side tab backed by a persistent client boolean option. */
public class GuiToggleClientConfigTab extends GuiInsetElement<BooleanOption> {

    private final ResourceLocation falseIcon;
    private final ResourceLocation trueIcon;
    private final ITextComponent trueTooltip;
    private final ITextComponent falseTooltip;

    public GuiToggleClientConfigTab(IGuiWrapper gui, int y, boolean left, ResourceLocation falseIcon, ResourceLocation trueIcon,
          BooleanOption option, ITextComponent trueTooltip, ITextComponent falseTooltip) {
        super(falseIcon, gui, option, left ? -26 : gui.getWidth(), y, 26, 18, left);
        this.falseIcon = falseIcon;
        this.trueIcon = trueIcon;
        this.trueTooltip = trueTooltip;
        this.falseTooltip = falseTooltip;
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_JEI_REJECTS_TARGET.argb());
    }

    @Override
    protected ResourceLocation getOverlay() {
        return dataSource.val() ? trueIcon : falseIcon;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        dataSource.set(!dataSource.val());
        Mekanism.configuration.save();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(dataSource.val() ? trueTooltip : falseTooltip, mouseX, mouseY);
    }
}
