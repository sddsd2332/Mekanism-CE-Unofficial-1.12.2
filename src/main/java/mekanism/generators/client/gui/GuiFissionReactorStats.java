package mekanism.generators.client.gui;

import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.client.gui.element.GuiFissionReactorTab;
import mekanism.generators.client.gui.element.GuiFissionReactorTab.FissionReactorTab;
import mekanism.generators.common.content.fission.SynchronizedFissionData;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.List;

public class GuiFissionReactorStats extends GuiMekanismTile<TileEntityFissionReactorCasing, ContainerNull> {

    private GuiTextField rateLimitField;

    public GuiFissionReactorStats(InventoryPlayer inventory, TileEntityFissionReactorCasing tile) {
        super(tile, new ContainerNull(inventory.player, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiFissionReactorTab(this, tileEntity, FissionReactorTab.MAIN));
        addButton(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                double rate = tileEntity.structure == null ? 0 : tileEntity.structure.lastBurnRate;
                return new TextComponentString(LangUtils.localize("gui.burnRate") + ": " + UnitDisplayUtils.roundDecimals(rate) + " /t");
            }

            @Override
            public double getLevel() {
                if (tileEntity.structure == null) {
                    return 0;
                }
                double max = tileEntity.structure.getMaxBurnRate();
                return max <= 0 ? 0 : Math.min(1, tileEntity.structure.lastBurnRate / max);
            }
        }, 5, 114, xSize - 12));
        addButton(new GuiHeatTab(this, this::getHeatTabText));
        rateLimitField = addButton(new GuiTextField(this, 0, 77, 128, 54, 12)
              .setMaxLength(getRateLimitMaxLength())
              .setInputValidator(this::isRateInput)
              .setEnterHandler(this::setRateLimit)
              .addCheckmarkButton(this::setRateLimit));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.fissionReactorStats")), 5);
        if (tileEntity.structure == null) {
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.status") + ": " + LangUtils.localize("gui.incomplete")), 0, 25,
                  TextAlignment.LEFT, titleTextColor(), 6, false);
            super.drawForegroundText(mouseX, mouseY);
            return;
        }

        SynchronizedFissionData data = tileEntity.structure;
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.fissionHeatStatistics")), 0, 20, TextAlignment.LEFT, headingTextColor(), 6, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("tooltip.heatCapacity") + ": " + UnitDisplayUtils.roundDecimals(data.casingHeatCapacity)), 0, 32,
              TextAlignment.LEFT, titleTextColor(), 6, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.surfaceArea") + ": " + data.surfaceArea), 0, 42,
              TextAlignment.LEFT, titleTextColor(), 6, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.boilEfficiency") + ": " + UnitDisplayUtils.roundDecimals(data.getBoilEfficiency() * 100) + "%"),
              0, 52, TextAlignment.LEFT, titleTextColor(), 6, false);

        drawScrollingString(new TextComponentString(LangUtils.localize("gui.fissionFuelStatistics")), 0, 68, TextAlignment.LEFT, headingTextColor(), 6, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxBurnRate") + ": " + UnitDisplayUtils.roundDecimals(data.getMaxBurnRate()) + " /t"), 0, 80,
              TextAlignment.LEFT, titleTextColor(), 6, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.rateLimit") + ": " + UnitDisplayUtils.roundDecimals(data.rateLimit) + " /t"), 0, 90,
              TextAlignment.LEFT, titleTextColor(), 6, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.currentBurnRate")), 0, 104, TextAlignment.LEFT, titleTextColor(), 6, false);
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.setRateLimit")), 3, 130, TextAlignment.RIGHT, titleTextColor(),
              rateLimitField.getRelativeX() - 2, 3, false, 1, getTimeOpened());
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getHeatTabText() {
        if (tileEntity.structure == null) {
            return Collections.emptyList();
        }
        TemperatureUnit unit = TemperatureUnit.values()[MekanismConfig.current().general.tempUnit.val().ordinal()];
        String environment = UnitDisplayUtils.getDisplayShort(tileEntity.structure.lastEnvironmentLoss * unit.intervalSize, false, unit);
        return Collections.singletonList(new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t"));
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }

    private boolean isRateInput(char c, int keyCode) {
        return Character.isDigit(c) || c == '.' && !rateLimitField.getText().contains(".") || keyCode == Keyboard.KEY_BACK ||
              keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT ||
              keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    private int getRateLimitMaxLength() {
        double maxBurnRate = tileEntity.structure == null ? 0 : tileEntity.structure.getMaxBurnRate();
        long adjustedMaxBurn = Math.max(0, (long) Math.ceil(maxBurnRate) - 1);
        return Long.toString(adjustedMaxBurn).length() + 3;
    }

    private void setRateLimit() {
        if (tileEntity.structure == null || rateLimitField == null || rateLimitField.isEmpty()) {
            return;
        }
        try {
            double target = Double.parseDouble(rateLimitField.getText());
            target = Math.max(0, Math.min(target, tileEntity.structure.getMaxBurnRate()));
            target = UnitDisplayUtils.roundDecimals(target);
            double delta = target - tileEntity.structure.rateLimit;
            if (Math.abs(delta) > 0.0001) {
                Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(1, delta)));
            }
            rateLimitField.clear();
        } catch (NumberFormatException ignored) {
            rateLimitField.clear();
        }
    }
}
