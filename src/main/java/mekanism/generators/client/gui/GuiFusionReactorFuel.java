package mekanism.generators.client.gui;

import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.generators.client.gui.element.GuiFusionReactorTab;
import mekanism.generators.client.gui.element.GuiFusionReactorTab.FusionReactorTab;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.util.function.BiPredicate;

public class GuiFusionReactorFuel extends GuiFusionReactorInfo {

    private GuiTextField injectionRateField;

    public GuiFusionReactorFuel(InventoryPlayer inventory, TileEntityReactorController tile) {
        super(inventory, tile);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiGasGauge(this, tileEntity.deuteriumTank, GuiGasGauge.Type.SMALL, 30, 64));
        addButton(new GuiGasGauge(this, tileEntity.fuelTank, GuiGasGauge.Type.STANDARD, 84, 50));
        addButton(new GuiGasGauge(this, tileEntity.tritiumTank, GuiGasGauge.Type.SMALL, 138, 64));
        addButton(new GuiProgress(() -> tileEntity.isBurning() ? 1 : 0, ProgressType.SMALL_RIGHT, this, 52, 76));
        addButton(new GuiProgress(() -> tileEntity.isBurning() ? 1 : 0, ProgressType.SMALL_LEFT, this, 106, 76));
        addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.HEAT));
        addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.STAT));
        injectionRateField = addButton(new GuiTextField(this, 0, 103, 115, 26, 11)
              .setMaxLength(Integer.toString(MekanismConfig.current().generators.reactorGeneratorInjectionRate.val()).length())
              .setInputValidator(isDigitOrTextboxKey())
              .setEnterHandler(this::setInjection));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        String injectionRate = LangUtils.localize("gui.reactor.injectionRate") + ": " + (tileEntity.getReactor() == null ? "None" : tileEntity.getReactor().getInjectionRate());
        drawScrollingString(new TextComponentString(injectionRate), 0, 35, TextAlignment.CENTER, titleTextColor(), 16, false);
        drawScrollingString(new TextComponentString(LangUtils.localize("gui.editrate") + ":"), 4, 117, TextAlignment.RIGHT, titleTextColor(), 99, 2, false);
        super.drawForegroundText(mouseX, mouseY);
    }

    private BiPredicate<Character, Integer> isDigitOrTextboxKey() {
        return (c, keyCode) -> Character.isDigit(c) || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT ||
              keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END;
    }

    private void setInjection() {
        if (injectionRateField != null && !injectionRateField.isEmpty()) {
            int toUse = Math.max(0, Math.min(Integer.parseInt(injectionRateField.getText()), MekanismConfig.current().generators.reactorGeneratorInjectionRate.val()));
            toUse -= toUse % 2;
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0, toUse)));
            injectionRateField.clear();
        }
    }
}
