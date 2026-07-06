package mekanism.client.gui.machine;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.inventory.container.ContainerElectricPump;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiElectricPump extends GuiMekanismTile<TileEntityElectricPump, ContainerElectricPump> {

    public GuiElectricPump(InventoryPlayer inventory, TileEntityElectricPump tile) {
        super(tile, new ContainerElectricPump(inventory, tile));
        titleLabelY = 5;
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 54, 23, 80, 42, this::getScreenText));
        addButton(new GuiDownArrow(this, 32, 39));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.getEnergy() < tileEntity.energyPerTick || tileEntity.getEnergy() == 0);
        addButton(new GuiFluidGauge(this, tileEntity.fluidTank, GuiFluidGauge.Type.STANDARD, 6, 13))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, () -> tileEntity.fluidTank.getNeeded() < tileEntity.estimateIncrementAmount());
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::usedEnergy));
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(new TextComponentString(MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())));
        FluidStack fluid = tileEntity.fluidTank.getFluid();
        if (fluid != null) {
            list.add(new TextComponentString(LangUtils.localizeFluidStack(fluid) + ": " + fluid.amount));
        } else {
            FluidStack activeType = tileEntity.getActiveType();
            list.add(new TextComponentString(activeType == null ? LangUtils.localize("gui.noFluid") : LangUtils.localizeFluidStack(activeType)));
        }
        return list;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}