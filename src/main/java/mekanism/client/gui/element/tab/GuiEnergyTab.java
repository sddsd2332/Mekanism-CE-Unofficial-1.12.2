package mekanism.client.gui.element.tab;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;

public class GuiEnergyTab extends GuiTexturedElement {

    private static final ResourceLocation RESOURCE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "energy_info.png");

    private final GuiTexturedElement.IInfoHandler infoHandler;

    public GuiEnergyTab(IGuiWrapper gui, MachineEnergyContainer energyContainer, BooleanSupplier isActive) {
        this(gui, () -> {
            double using = isActive.getAsBoolean() ? energyContainer.getEnergyPerTick() : 0;
            List<ITextComponent> info = new ArrayList<>();
            info.add(new TextComponentString(LangUtils.localize("gui.using") + ": " + MekanismUtils.getEnergyDisplay(using) + "/t"));
            info.add(new TextComponentString(LangUtils.localize("gui.needed") + ": " + MekanismUtils.getEnergyDisplay(energyContainer.getNeeded())));
            return info;
        });
    }

    public GuiEnergyTab(IGuiWrapper gui, MachineEnergyContainer energyContainer, DoubleSupplier lastEnergyUsed) {
        this(gui, () -> {
            List<ITextComponent> info = new ArrayList<>();
            info.add(new TextComponentString(LangUtils.localize("gui.using") + ": " + MekanismUtils.getEnergyDisplay(lastEnergyUsed.getAsDouble()) + "/t"));
            info.add(new TextComponentString(LangUtils.localize("gui.needed") + ": " + MekanismUtils.getEnergyDisplay(energyContainer.getNeeded())));
            return info;
        });
    }

    public GuiEnergyTab(IGuiWrapper gui, GuiTexturedElement.IInfoHandler infoHandler) {
        super(RESOURCE, gui, -26, 137, 26, 26);
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
        info.add(LangUtils.localize("gui.unit") + ": " + MekanismConfig.current().general.energyUnit.val());
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
        updateEnergyUnit(button == 1 ? this::previousEnergyUnit : this::nextEnergyUnit);
    }

    private EnergyType nextEnergyUnit(EnergyType current) {
        EnergyType[] values = EnergyType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private EnergyType previousEnergyUnit(EnergyType current) {
        EnergyType[] values = EnergyType.values();
        return values[(current.ordinal() - 1 + values.length) % values.length];
    }

    private void updateEnergyUnit(UnaryOperator<EnergyType> converter) {
        EnergyType current = MekanismConfig.current().general.energyUnit.val();
        EnergyType updated = converter.apply(current);
        if (current != updated) {
            MekanismConfig.current().general.energyUnit.set(updated);
            Mekanism.configuration.save();
        }
    }

    private ResourceLocation getTabResource() {
        return switch (MekanismConfig.current().general.energyUnit.val()) {
            case J -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "energy_info_j.png");
            case EU -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "energy_info_eu.png");
            case FE -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "energy_info_fe.png");
            case RF -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "energy_info_rf.png");
            case T -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "energy_info_t.png");
        };
    }
}
