package mekanism.client.gui;

import mekanism.client.gui.element.GuiPlayerSlot;
import mekanism.client.gui.element.GuiScrollList;
import mekanism.client.gui.element.GuiSlot;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.sound.SoundHandler;
import mekanism.common.OreDictCache;
import mekanism.common.inventory.container.ContainerDictionary;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public class GuiDictionary extends GuiMekanism {

    private final GuiScrollList scrollList;
    public ItemStack itemType = ItemStack.EMPTY;

    public GuiDictionary(InventoryPlayer inventory) {
        super(new ContainerDictionary(inventory));
        addGuiElement(scrollList = new GuiScrollList(this, getGuiLocation(), 8, 30, 160, 4));
        addGuiElement(new GuiPlayerSlot(this, getGuiLocation()));
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 5, 5));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String stats = LangUtils.localize("item.Dictionary.name");
        fontRenderer.drawString(stats, (xSize / 2) - (fontRenderer.getStringWidth(stats) / 2), 4, 0x404040);
        fontRenderer.drawString(LangUtils.localize("container.inventory"), 8, ySize - 96 + 2, 0x404040);
        renderItem(itemType, 6, 6);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(int xAxis, int yAxis) {
        super.drawGuiContainerBackgroundLayer(xAxis, yAxis);
        if (xAxis >= 6 && xAxis <= 22 && yAxis >= 6 && yAxis <= 22) {
            int x = guiLeft + 6;
            int y = guiTop + 6;
            drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
            MekanismRenderer.resetColor();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int xAxis = mouseX - guiLeft;
        int yAxis = mouseY - guiTop;
        if (button == 0) {
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                Slot hovering = null;
                for (int i = 0; i < inventorySlots.inventorySlots.size(); i++) {
                    Slot slot = inventorySlots.inventorySlots.get(i);
                    if (isMouseOverSlot(slot, mouseX, mouseY)) {
                        hovering = slot;
                        break;
                    }
                }

                if (hovering != null) {
                    ItemStack stack = hovering.getStack();
                    if (!stack.isEmpty()) {
                        itemType = stack.copy();
                        itemType.setCount(1);
                        scrollList.setText(OreDictCache.getOreDictName(itemType));
                        SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
                        return;
                    }
                }
            }

            if (xAxis >= 6 && xAxis <= 22 && yAxis >= 6 && yAxis <= 22) {
                ItemStack stack = mc.player.inventory.getItemStack();
                if (!stack.isEmpty() && !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    itemType = stack.copy();
                    itemType.setCount(1);
                    scrollList.setText(OreDictCache.getOreDictName(itemType));
                } else if (stack.isEmpty() && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    itemType = ItemStack.EMPTY;
                    scrollList.setText(null);
                }
                SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }


}
