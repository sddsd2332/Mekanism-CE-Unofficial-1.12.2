package mekanism.generators.client.gui;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.math.MathUtils;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiBigLight;
import mekanism.client.gui.element.GuiGraph;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiHybridGauge;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.client.gui.element.GuiFissionReactorTab;
import mekanism.generators.client.gui.element.GuiFissionReactorTab.FissionReactorTab;
import mekanism.generators.common.content.fission.SynchronizedFissionData;
import mekanism.generators.common.inventory.container.ContainerFissionReactor;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GuiFissionReactor extends GuiMekanismTile<TileEntityFissionReactorCasing, ContainerFissionReactor> {

    private MekanismButton activateButton;
    private MekanismButton scramButton;
    private GuiGraph heatGraph;

    public GuiFissionReactor(InventoryPlayer inventory, TileEntityFissionReactorCasing tile) {
        super(tile, new ContainerFissionReactor(inventory, tile));
        xSize = 195;
        ySize += 89;
        inventoryLabelX = 6;
        inventoryLabelY = ySize - 92;
        titleLabelY = 5;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiFissionReactorTab(this, tileEntity, FissionReactorTab.STAT));
        addButton(new GuiInnerScreen(this, 45, 17, 105, 56, this::getScreenText).clearSpacing().padding(2).textScale(0.75F).centerY()
              .recipeViewerCategories(RecipeViewerRecipeType.FISSION_REACTOR));
        addButton(new GuiHybridGauge(
              () -> tileEntity.structure == null ? null : tileEntity.structure.gasCoolantTank, this::getGasTanks,
              () -> tileEntity.structure == null ? null : tileEntity.structure.coolantTank, this::getFluidTanks,
              GaugeType.STANDARD, this, 6, 13
        ).setLabel(coloredLabel(EnumColor.AQUA, "gui.coolant")));
        addButton(new GuiGasGauge(() -> tileEntity.structure == null ? null : tileEntity.structure.fuelTank, this::getGasTanks, GaugeType.STANDARD, this, 25, 13)
              .setLabel(coloredLabel(EnumColor.DARK_GREEN, "gui.fuel")));
        addButton(new GuiHybridGauge(
              () -> tileEntity.structure == null ? null : tileEntity.structure.heatedCoolantTank, this::getGasTanks,
              () -> tileEntity.structure == null ? null : tileEntity.structure.steamTank, this::getFluidTanks,
              GaugeType.STANDARD, this, 152, 13
        ).setLabel(coloredLabel(EnumColor.ORANGE, "gui.heatedCoolant")));
        addButton(new GuiGasGauge(() -> tileEntity.structure == null ? null : tileEntity.structure.wasteTank, this::getGasTanks, GaugeType.STANDARD, this, 171, 13)
              .setLabel(coloredLabel(EnumColor.BROWN, "gui.waste")));
        addButton(new GuiHeatTab(this, this::getHeatTabText));
        activateButton = addButton(new MekanismButton(this, 6, 75, 81, 16, new TextComponentString(LangUtils.localize("gui.activate")),
              this::toggleActive, getOnHover(() -> new TextComponentString(LangUtils.localize("gui.activate")))));
        scramButton = addButton(new MekanismButton(this, 89, 75, 81, 16, new TextComponentString(LangUtils.localize("gui.scram")),
              this::toggleActive, getOnHover(() -> new TextComponentString(LangUtils.localize("gui.scram")))));
        addButton(new GuiBigLight(this, 173, 76, () -> tileEntity.structure != null && tileEntity.structure.active));
        addButton(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemperatureDisplay());
            }

            @Override
            public double getLevel() {
                return tileEntity.structure == null ? 0 : Math.min(1, tileEntity.structure.temperature / SynchronizedFissionData.MAX_DAMAGE_TEMPERATURE);
            }
        }, 5, 102, xSize - 12));
        heatGraph = addButton(new GuiGraph(this, 5, 123, xSize - 10, 38, data -> LangUtils.localize("gui.temp") + ": " + data + " K"));
        heatGraph.setMinScale(1_600);
        updateButtons();
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        updateButtons();
        drawTitleText(new TextComponentString(LangUtils.localize("gui.fissionReactor")), titleLabelY);
        renderInventoryText();
        drawString(new TextComponentString(LangUtils.localize("gui.temp")), 5, 93, titleTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.fissionHeatGraph")), 5, 114, titleTextColor());
        if (tileEntity.structure != null && tileEntity.structure.isForceDisabled() && !tileEntity.structure.active) {
            int xAxis = mouseX - guiLeft;
            int yAxis = mouseY - guiTop;
            if (xAxis >= 6 && xAxis <= 87 && yAxis >= 75 && yAxis <= 91) {
                displayTooltip(LangUtils.localize("fission.force_disabled"), xAxis, yAxis);
            }
        }
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (heatGraph != null) {
            heatGraph.addData(tileEntity.structure == null ? (int) SynchronizedFissionData.BASE_TEMPERATURE :
                  MathUtils.clampToInt(Math.round(tileEntity.structure.temperature)));
        }
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }

    private List<ITextComponent> getScreenText() {
        if (tileEntity.structure == null) {
            return Collections.singletonList(new TextComponentString(LangUtils.localize("gui.status") + ": " + LangUtils.localize("gui.incomplete")));
        }
        SynchronizedFissionData data = tileEntity.structure;
        String status = data.active ? LangUtils.localize("gui.on") : LangUtils.localize("gui.off");
        if (data.isForceDisabled()) {
            status += " (" + LangUtils.localize("fission.force_disabled") + ")";
        }
        String coolingMode = data.coolantTank.getFluidAmount() > 0 ? LangUtils.localize("fission.cooling.water")
              : (data.gasCoolantTank.getStored() > 0 ? LangUtils.localize("fission.cooling.sodium") : LangUtils.localize("fission.cooling.idle"));
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.status") + ": " + status),
              new TextComponentString(LangUtils.localize("gui.mode") + ": " + coolingMode),
              new TextComponentString(LangUtils.localize("gui.burnRate") + ": " + UnitDisplayUtils.roundDecimals(data.lastBurnRate) + " /t"),
              new TextComponentString(LangUtils.localize("gui.boilRate") + ": " + data.lastBoilRate + " /t"),
              new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemperatureDisplay()),
              new TextComponentString(LangUtils.localize("gui.reactorDamage") + ": " + getDamageColor() + UnitDisplayUtils.roundDecimals(data.reactorDamage) + "%")
        );
    }

    private List<ITextComponent> getHeatTabText() {
        if (tileEntity.structure == null) {
            return Collections.emptyList();
        }
        TemperatureUnit unit = TemperatureUnit.values()[MekanismConfig.current().general.tempUnit.val().ordinal()];
        String environment = UnitDisplayUtils.getDisplayShort(tileEntity.structure.lastEnvironmentLoss * unit.intervalSize, false, unit);
        return Collections.singletonList(new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t"));
    }

    private void toggleActive() {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0)));
    }

    private void updateButtons() {
        if (activateButton == null || scramButton == null) {
            return;
        }
        if (tileEntity.structure == null) {
            activateButton.active = false;
            scramButton.active = false;
            return;
        }
        activateButton.active = !tileEntity.structure.active && !tileEntity.structure.isForceDisabled();
        scramButton.active = tileEntity.structure.active;
    }

    private List<IExtendedGasTank> getGasTanks() {
        return tileEntity.structure == null ? Collections.emptyList() : tileEntity.structure.getGasTanks(null);
    }

    private List<IExtendedFluidTank> getFluidTanks() {
        return tileEntity.structure == null ? Collections.emptyList() : tileEntity.structure.getFluidTanks(null);
    }

    private static ITextComponent coloredLabel(EnumColor color, String key) {
        return new TextComponentString(color + LangUtils.localize(key));
    }

    private String getTemperatureDisplay() {
        return tileEntity.structure == null ? "0 K" : MekanismUtils.getTemperatureDisplay(tileEntity.structure.temperature, TemperatureUnit.KELVIN);
    }

    private EnumColor getDamageColor() {
        if (tileEntity.structure == null) {
            return EnumColor.DARK_GREEN;
        }
        double damage = tileEntity.structure.reactorDamage;
        if (damage >= SynchronizedFissionData.MAX_DAMAGE) {
            return EnumColor.DARK_RED;
        } else if (damage >= SynchronizedFissionData.MAX_DAMAGE * 0.75) {
            return EnumColor.RED;
        } else if (damage >= SynchronizedFissionData.MAX_DAMAGE * 0.5) {
            return EnumColor.ORANGE;
        } else if (damage > 0) {
            return EnumColor.YELLOW;
        }
        return EnumColor.DARK_GREEN;
    }
}
