package mekanism.generators.client.gui;

import mekanism.client.gui.element.GuiRecipeViewerArea;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiNumberGauge;
import mekanism.client.gui.element.gauge.GuiNumberGauge.INumberInfoHandler;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.client.gui.element.GuiFusionReactorTab;
import mekanism.generators.client.gui.element.GuiFusionReactorTab.FusionReactorTab;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fluids.FluidRegistry;

public class GuiFusionReactorHeat extends GuiFusionReactorInfo {

    private static final double MAX_LEVEL = 5E8;

    public GuiFusionReactorHeat(InventoryPlayer inventory, TileEntityReactorController tile) {
        super(inventory, tile);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiNumberGauge(new INumberInfoHandler() {
            @Override
            public TextureAtlasSprite getIcon() {
                return MekanismRenderer.getBaseFluidTexture(FluidRegistry.LAVA, MekanismRenderer.FluidType.STILL);
            }

            @Override
            public double getLevel() {
                return tileEntity.getPlasmaTemp();
            }

            @Override
            public double getScaledLevel() {
                return Math.min(1, getLevel() / MAX_LEVEL);
            }

            @Override
            public String getText() {
                return LangUtils.localize("gui.Plasma") + ": " + MekanismUtils.getTemperatureDisplay(getLevel(), TemperatureUnit.KELVIN);
            }
        }, GaugeType.STANDARD, this, 12, 50));
        addButton(new GuiProgress(() -> tileEntity.getPlasmaTemp() > tileEntity.getCaseTemp() ? 1 : 0, ProgressType.SMALL_RIGHT, this, 34, 76));
        addButton(new GuiNumberGauge(new INumberInfoHandler() {
            @Override
            public TextureAtlasSprite getIcon() {
                return MekanismRenderer.getBaseFluidTexture(FluidRegistry.LAVA, MekanismRenderer.FluidType.STILL);
            }

            @Override
            public double getLevel() {
                return tileEntity.getCaseTemp();
            }

            @Override
            public double getScaledLevel() {
                return Math.min(1, getLevel() / MAX_LEVEL);
            }

            @Override
            public String getText() {
                return LangUtils.localize("gui.Case") + ": " + MekanismUtils.getTemperatureDisplay(getLevel(), TemperatureUnit.KELVIN);
            }
        }, GaugeType.STANDARD, this, 66, 50));
        addButton(new GuiProgress(() -> tileEntity.getCaseTemp() > tileEntity.getReactor().getAmbientTemperature() ? 1 : 0, ProgressType.SMALL_RIGHT, this, 88, 61));
        addButton(new GuiProgress(() -> tileEntity.getCaseTemp() > tileEntity.getReactor().getAmbientTemperature() && tileEntity.waterTank.getFluidAmount() > 0 && tileEntity.getactivelyCooled() &&
              tileEntity.steamTank.getFluidAmount() < tileEntity.steamTank.getCapacity() ? 1 : 0,
              ProgressType.SMALL_RIGHT, this, 88, 91));
        addButton(new GuiFluidGauge(this, tileEntity.waterTank, GuiFluidGauge.Type.SMALL, 120, 84));
        addButton(new GuiRecipeViewerArea(this, 120, 84, 18, 30, RecipeViewerRecipeType.FUSION_COOLING));
        addButton(new GuiFluidGauge(this, tileEntity.steamTank, GuiFluidGauge.Type.SMALL, 156, 84));
        addButton(new GuiEnergyGauge(this, tileEntity.getEnergyContainer(), GuiEnergyGauge.Type.SMALL, 120, 46));
        addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.FUEL));
        addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.STAT));
    }
}
