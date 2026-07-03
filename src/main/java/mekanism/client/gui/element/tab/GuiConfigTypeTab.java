package mekanism.client.gui.element.tab;

import mekanism.api.transmitters.TransmissionType;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.window.GuiSideConfiguration;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

public class GuiConfigTypeTab extends GuiInsetElement<Void> {

    private final TransmissionType transmission;
    private final GuiSideConfiguration<?> config;
    private final Runnable onClick;

    public GuiConfigTypeTab(IGuiWrapper gui, TransmissionType type, int x, int y, GuiSideConfiguration<?> config, boolean left) {
        super(getResource(type), gui, null, x, y, 26, 18, left);
        this.config = config;
        this.onClick = null;
        transmission = type;
    }

    public GuiConfigTypeTab(IGuiWrapper gui, TransmissionType type, int y, boolean left, Runnable onClick) {
        super(getResource(type), gui, null, left ? -26 : gui.getWidth(), y, 26, 18, left);
        this.config = null;
        this.onClick = onClick;
        transmission = type;
    }

    private static ResourceLocation getResource(TransmissionType type) {
        return MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, type.getTransmission().toLowerCase() + ".png");
    }

    public TransmissionType getTransmissionType() {
        return transmission;
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(switch (transmission) {
            case ENERGY -> SpecialColors.TAB_ENERGY_CONFIG.argb();
            case FLUID -> SpecialColors.TAB_FLUID_CONFIG.argb();
            case GAS -> SpecialColors.TAB_GAS_CONFIG.argb();
            case ITEM -> SpecialColors.TAB_ITEM_CONFIG.argb();
            case HEAT -> SpecialColors.TAB_HEAT_CONFIG.argb();
        });
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentString(transmission.localize()), mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (config == null) {
            onClick.run();
        } else {
            config.setCurrentType(transmission);
            config.updateTabs();
        }
    }
}
