package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOProcessingTerminal;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/** Portable screen for management, maintenance and monitor responsibilities. */
@SideOnly(Side.CLIENT)
public final class GuiPortableQIOProcessingTerminal extends
      GuiMekanism<ContainerPortableQIOProcessingTerminal> {

    private static final int MANAGEMENT_WIDTH = 384;
    private static final int MANAGEMENT_HEIGHT = 220;
    private static final int MAINTENANCE_HEIGHT = 214;
    private static final int CRAFTING_MONITOR_WIDTH = 384;
    private static final int CRAFTING_MONITOR_HEIGHT = 224;

    private GuiQIOProcessingTerminalFrequencyTab frequencyTab;
    private GuiQIOWorkbenchConfigurationTab workbenchConfigurationTab;
    private final ContainerPortableQIOProcessingTerminal container;
    private GuiQIOManagementPanel managementPanel;
    private GuiQIOMaintenanceRulesPanel maintenancePanel;
    private GuiQIOCraftingMonitorPanel craftingMonitorPanel;

    public GuiPortableQIOProcessingTerminal(InventoryPlayer inventory, EnumHand hand,
          int itemSlot, ItemStack stack) {
        super(new ContainerPortableQIOProcessingTerminal(inventory, hand, itemSlot,
              stack));
        container = (ContainerPortableQIOProcessingTerminal) inventorySlots;
        if (stack.getItem() instanceof
            mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
            item.getTerminalType() ==
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            xSize = MANAGEMENT_WIDTH;
            ySize = MANAGEMENT_HEIGHT;
        } else if (stack.getItem() instanceof
            mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
            item.getTerminalType() ==
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            xSize = 236;
            ySize = MAINTENANCE_HEIGHT;
            dynamicSlots = true;
            inventoryLabelX = 38;
            inventoryLabelY = 122;
        } else if (stack.getItem() instanceof
            mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
            item.getTerminalType() ==
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            xSize = CRAFTING_MONITOR_WIDTH;
            ySize = CRAFTING_MONITOR_HEIGHT;
        }
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOProcessingTerminalFrequencyTab(this,
              container, () -> frequencyTab));
        if (container.getTerminalState().getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT ||
            container.getPortableStack().getItem() instanceof
                  mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
                  item.getTerminalType() ==
                        mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            workbenchConfigurationTab = addButton(new GuiQIOWorkbenchConfigurationTab(this,
                  container, () -> workbenchConfigurationTab));
        }
        addButton(new GuiInnerScreen(this, 7, 15, xSize - 16, 12,
              this::frequencyText).clearFormat().clearSpacing().padding(3).textScale(0.8F));
        if (container.getTerminalState().getTerminalType() ==
            mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT ||
            container.getPortableStack().getItem() instanceof
                  mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
            item.getTerminalType() ==
                        mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            managementPanel = addButton(new GuiQIOManagementPanel(this, container,
                  container, 8, 31, xSize - 16, ySize - 39));
        } else if (container.getTerminalState().getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE ||
              container.getPortableStack().getItem() instanceof
                    mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
                    item.getTerminalType() ==
                          mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            maintenancePanel = addButton(new GuiQIOMaintenanceRulesPanel(this,
                  container, 8, 31, xSize - 16, 90));
        } else if (container.getTerminalState().getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR ||
              container.getPortableStack().getItem() instanceof
                    mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
                    item.getTerminalType() ==
                          mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            craftingMonitorPanel = addButton(new GuiQIOCraftingMonitorPanel(this,
                  container, 8, 31, xSize - 16, ySize - 39));
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        ItemStack stack = container.getPortableStack();
        drawTitleText(new TextComponentString(stack.isEmpty() ? "QIO Processing Terminal" :
              stack.getDisplayName()), 4);
        if (!(stack.getItem() instanceof
              mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item) ||
            item.getTerminalType() !=
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT &&
            item.getTerminalType() !=
                  mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.CRAFTING_MONITOR) {
            renderInventoryText();
        }
        super.drawForegroundText(mouseX, mouseY);
    }

    private boolean isMaintenance() {
        return container.getTerminalState().getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE ||
              container.getPortableStack().getItem() instanceof
                    mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
                    item.getTerminalType() ==
                          mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE;
    }

    private boolean isManagement() {
        return container.getTerminalState().getTerminalType() ==
              mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT ||
              container.getPortableStack().getItem() instanceof
                    mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal item &&
                    item.getTerminalType() ==
                          mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT;
    }

    private List<ITextComponent> frequencyText() {
        QIOFrequency frequency = container.getTerminalFrequency();
        return Collections.singletonList(frequency == null ?
              new TextComponentTranslation("frequency.mekanism.none") :
              MekanismLang.FREQUENCY.translate(frequency.getName()));
    }
}
