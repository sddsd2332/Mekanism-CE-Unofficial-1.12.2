package mekanism.client.gui;

import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.gauge.GuiEnergyGauge.GaugeColor;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiContainerEditModeTab;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.ContainerHybridStorage;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.tile.TileEntityHybridStorage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Arrays;

@SideOnly(Side.CLIENT)
public class GuiHybridStorage extends GuiConfigurableTile<TileEntityHybridStorage, ContainerHybridStorage> {

    private static final int STORAGE_SLOT_COUNT = 120;
    private static final int GAS_FILL_SLOT_1 = STORAGE_SLOT_COUNT;
    private static final int GAS_DRAIN_SLOT_1 = GAS_FILL_SLOT_1 + 1;
    private static final int GAS_FILL_SLOT_2 = GAS_DRAIN_SLOT_1 + 1;
    private static final int GAS_DRAIN_SLOT_2 = GAS_FILL_SLOT_2 + 1;
    private static final int FLUID_INPUT_SLOT = GAS_DRAIN_SLOT_2 + 1;
    private static final int FLUID_OUTPUT_SLOT = FLUID_INPUT_SLOT + 1;
    private static final int ENERGY_CHARGE_SLOT = FLUID_OUTPUT_SLOT + 1;
    private static final int ENERGY_DISCHARGE_SLOT = ENERGY_CHARGE_SLOT + 1;

    public GuiHybridStorage(InventoryPlayer inventory, TileEntityHybridStorage tile) {
        super(tile, new ContainerHybridStorage(inventory, tile));
        xSize += 108;
        ySize += 117;
        inventoryLabelX = 61;
        inventoryLabelY = ySize - 94;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiContainerEditModeTab<>(this, tileEntity, 6));
        addButton(new GuiEnergyTab(this, () -> Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())),
              new TextComponentString(LangUtils.localize("gui.maxOutput") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t")
        )));
        addButton(new GuiGasGauge(this, tileEntity.gasTank1, 7, 197).withColor(GuiGasGauge.GaugeColor.BLUE));
        addButton(new GuiGasGauge(this, tileEntity.gasTank2, 34, 197).withColor(GuiGasGauge.GaugeColor.ORANGE));
        addButton(new GuiFluidGauge(this, tileEntity.fluidTank, GuiFluidGauge.Type.STANDARD, 232, 197).withColor(GuiFluidGauge.GaugeColor.YELLOW));
        addButton(new GuiEnergyGauge(this, tileEntity.getMainEnergyContainer(), GuiEnergyGauge.Type.STANDARD, 259, 197).withColor(GaugeColor.AQUA));
    }

    @Override
    protected void addSlots() {
        for (int slotIndex = 0; slotIndex < inventorySlots.inventorySlots.size(); slotIndex++) {
            Slot slot = inventorySlots.inventorySlots.get(slotIndex);
            if (slotIndex < STORAGE_SLOT_COUNT) {
                addSlotElement(slot, SlotType.NORMAL, null);
            } else if (slotIndex == GAS_FILL_SLOT_1 || slotIndex == GAS_FILL_SLOT_2) {
                addSlotElement(slot, SlotType.INPUT, SlotOverlay.PLUS);
            } else if (slotIndex == GAS_DRAIN_SLOT_1 || slotIndex == GAS_DRAIN_SLOT_2) {
                addSlotElement(slot, SlotType.OUTPUT, SlotOverlay.MINUS);
            } else if (slotIndex == FLUID_INPUT_SLOT) {
                addSlotElement(slot, SlotType.NORMAL, SlotOverlay.INPUT);
            } else if (slotIndex == FLUID_OUTPUT_SLOT) {
                addSlotElement(slot, SlotType.NORMAL, SlotOverlay.OUTPUT);
            } else if (slotIndex == ENERGY_CHARGE_SLOT) {
                addSlotElement(slot, SlotType.POWER, SlotOverlay.MINUS);
            } else if (slotIndex == ENERGY_DISCHARGE_SLOT) {
                addSlotElement(slot, SlotType.POWER, SlotOverlay.PLUS);
            } else {
                addSlotElement(slot, SlotType.NORMAL, null);
            }
        }
    }

    private void addSlotElement(Slot slot, SlotType type, @Nullable SlotOverlay overlay) {
        GuiSlot guiSlot = new GuiSlot(type, this, slot.xPos - 1, slot.yPos - 1).visibility(slot::isEnabled);
        if (overlay != null) {
            guiSlot.with(overlay);
        }
        addButton(guiSlot);
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
