package mekanism.client.gui.robit;

import mekanism.client.gui.element.progress.GuiFlame;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitSmelting;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRobitSmelting extends GuiRobit<ContainerRobitSmelting> {

    public GuiRobitSmelting(InventoryPlayer inventory, EntityRobit entity) {
        super(new ContainerRobitSmelting(inventory, entity), entity);
        inventoryLabelY += 1;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiFlame(new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return robit.currentItemBurnTime == 0 ? 0 : robit.furnaceBurnTime / (double) robit.currentItemBurnTime;
            }

            @Override
            public boolean isActive() {
                return robit.furnaceBurnTime > 0;
            }
        }, this, 56, 37));
        addButton(new GuiProgress(() -> Math.max(Math.min(robit.furnaceCookTime / 200D, 1), 0), ProgressType.BAR, this, 78, 38));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.robit.smelting")), 6);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    protected boolean shouldOpenGui(int guiId) {
        return guiId != GUI_SMELTING;
    }
}
