package mekanism.generators.client.gui;

import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiFluidBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.TextUtils;
import mekanism.generators.common.inventory.container.ContainerBioGenerator;
import mekanism.generators.common.tile.TileEntityBioGenerator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.Arrays;
import java.util.List;

public class GuiBioGenerator extends GuiGenerator<TileEntityBioGenerator, ContainerBioGenerator> {

    public GuiBioGenerator(InventoryPlayer inventory, TileEntityBioGenerator tile) {
        super(tile, new ContainerBioGenerator(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 48, 23, 80, 40, this::getScreenText));
        addButton(new GuiEnergyTab(this, () -> getEnergyTabText(tileEntity.getActive() ? MekanismConfig.current().generators.bioGeneration.val() : 0)));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15));
        addButton(new GuiFluidBar(this, GuiFluidBar.getProvider(tileEntity.bioFuelTank, tileEntity.getFluidTanks(null)), 7, 15, 4, 52, false));
    }

    private List<ITextComponent> getScreenText() {
        return Arrays.asList(
              energy(tileEntity.getEnergy()),
              text(LangUtils.localize("gui.bioGenerator.bioFuel") + ": " + TextUtils.format(tileEntity.bioFuelTank.getFluidAmount())),
              text(LangUtils.localize("gui.out") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t")
        );
    }
}
