package mekanism.client.gui;

import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiMergedTankGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiContainerEditModeTab;
import mekanism.common.inventory.container.ContainerDynamicTank;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiDynamicTank extends GuiMekanismTile<TileEntityDynamicTank, ContainerDynamicTank> {

    public GuiDynamicTank(InventoryPlayer inventory, TileEntityDynamicTank tile) {
        super(tile, new ContainerDynamicTank(inventory, tile));
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        addButton(GuiSideHolder.armorHolder(this));
        addButton(new GuiElementHolder(this, 141, 16, 26, 56));
        super.addGuiElements();
        addButton(new GuiSlot(SlotType.INNER_HOLDER_SLOT, this, 145, 20));
        addButton(new GuiSlot(SlotType.INNER_HOLDER_SLOT, this, 145, 50));
        addButton(new GuiInnerScreen(this, 49, 21, 84, 46, this::getScreenText).spacing(1));
        addButton(new GuiDownArrow(this, 150, 39));
        addButton(new GuiContainerEditModeTab<>(this, tileEntity));
        addButton(new GuiMergedTankGauge(this, tileEntity, GaugeType.MEDIUM, 7, 16, 34, 56));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>();
        String storedName = null;
        int storedAmount = 0;
        if (tileEntity.structure != null) {
            FluidStack fluidStored = tileEntity.structure.fluidStored;
            if (fluidStored != null) {
                storedName = LangUtils.localizeFluidStack(fluidStored);
                storedAmount = fluidStored.amount;
            } else if (tileEntity.structure.gasstored != null && tileEntity.structure.gasstored.getGas() != null) {
                storedName = tileEntity.structure.gasstored.getGas().getLocalizedName();
                storedAmount = tileEntity.structure.gasstored.amount;
            }
        }
        text.add(new TextComponentString(storedName == null ? LangUtils.localize("gui.empty") : storedName + ":"));
        if (storedName != null) {
            text.add(new TextComponentString(storedAmount + "mB"));
        }
        text.add(new TextComponentString(LangUtils.localize("gui.capacity") + ":"));
        text.add(new TextComponentString(tileEntity.clientCapacity + "mB"));
        return text;
    }
}
