package mekanism.client.gui.element.tab;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TempType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class GuiHeatTab extends GuiTexturedElement {

    private static final ResourceLocation RESOURCE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "heat_info.png");

    private final GuiTexturedElement.IInfoHandler infoHandler;

    public GuiHeatTab(IGuiWrapper gui, GuiTexturedElement.IInfoHandler infoHandler) {
        super(RESOURCE, gui, -26, 109, 26, 26);
        this.infoHandler = infoHandler;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, width, height);
        MekanismRenderer.resetColor();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        List<String> info = new ArrayList<>();
        for (ITextComponent component : infoHandler.getInfo()) {
            info.add(component.getFormattedText());
        }
        info.add(LangUtils.localize("gui.unit") + ": " + MekanismConfig.current().general.tempUnit.val());
        displayTooltips(info, mouseX, mouseY);
    }

    @Override
    protected ResourceLocation getResource() {
        return getTabResource();
    }

    @Override
    public boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        updateTemperatureUnit(button == 1 ? this::previousTemperatureUnit : this::nextTemperatureUnit);
    }

    private TempType nextTemperatureUnit(TempType current) {
        TempType[] values = TempType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private TempType previousTemperatureUnit(TempType current) {
        TempType[] values = TempType.values();
        return values[(current.ordinal() - 1 + values.length) % values.length];
    }

    private void updateTemperatureUnit(UnaryOperator<TempType> converter) {
        TempType current = MekanismConfig.current().general.tempUnit.val();
        TempType updated = converter.apply(current);
        if (current != updated) {
            MekanismConfig.current().general.tempUnit.set(updated);
            Mekanism.configuration.save();
        }
    }

    private ResourceLocation getTabResource() {
        return switch (MekanismConfig.current().general.tempUnit.val()) {
            case K -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "heat_info_k.png");
            case C -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "heat_info_c.png");
            case R -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "heat_info_r.png");
            case F -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "heat_info_f.png");
            case STP -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "heat_info_stp.png");
        };
    }
}
