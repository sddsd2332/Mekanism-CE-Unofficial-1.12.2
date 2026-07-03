package mekanism.client.gui;

import mekanism.api.Coord4D;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.scroll.GuiModuleScrollList;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.content.gear.Module;
import mekanism.common.inventory.container.ContainerModificationStation;
import mekanism.common.network.PacketRemoveModule.RemoveModuleMessage;
import mekanism.common.tile.TileEntityModificationStation;
import mekanism.common.util.LangUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiModificationStation extends GuiMekanismTile<TileEntityModificationStation, ContainerModificationStation> {

    private MekanismButton removeButton;
    private Module<?> selectedModule;

    public GuiModificationStation(InventoryPlayer inventory, TileEntityModificationStation tile) {
        super(tile, new ContainerModificationStation(inventory, tile));
        dynamicSlots = true;
        ySize += 64;
        inventoryLabelY = ySize - 92;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 156, 40))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> {
                  MachineEnergyContainer energyContainer = tileEntity.getEnergyContainer();
                  return energyContainer.getEnergyPerTick() > energyContainer.getEnergy();
              });
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::usedEnergy));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.LARGE_RIGHT, this, 65, 123));
        removeButton = addButton(new TranslationButton(this, 28, 96, 120, 17, MekanismLang.BUTTON_REMOVE, this::removeSelectedModule,
              (element, mouseX, mouseY) -> {
                  if (selectedModule != null) {
                      element.displayTooltip(new TextComponentString(LangUtils.localize("tooltip.remove_all_modules")), mouseX, mouseY);
                  }
              }));
        removeButton.active = selectedModule != null;
        addButton(new GuiModuleScrollList(this, 28, 20, 74, () -> tileEntity.getContainerStack().copy(), this::onModuleSelected));
    }

    private void onModuleSelected(Module<?> module) {
        selectedModule = module;
        if (removeButton != null) {
            removeButton.active = selectedModule != null;
        }
    }

    private void removeSelectedModule() {
        if (selectedModule != null) {
            Mekanism.packetHandler.sendToServer(new RemoveModuleMessage(Coord4D.get(tileEntity), selectedModule.getData(), GuiScreen.isShiftKeyDown()));
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), 24, 6, getXSize());
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
