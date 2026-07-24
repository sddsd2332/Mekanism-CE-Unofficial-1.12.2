package mekanism.client.gui.item;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.custom.GuiDictionaryTarget;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.inventory.container.item.DictionaryContainer;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public class GuiDictionary extends GuiMekanism<DictionaryContainer> {

    private GuiDictionaryTarget target;

    public GuiDictionary(InventoryPlayer inventory, EnumHand hand, ItemStack stack) {
        super(new DictionaryContainer(inventory, hand, stack));
        init();
    }

    public GuiDictionary(InventoryPlayer inventory, EnumHand hand, int itemSlot, ItemStack stack) {
        super(new DictionaryContainer(inventory, hand, itemSlot, stack));
        init();
    }

    private void init() {
        dynamicSlots = true;
        ySize += 5;
        inventoryLabelY = ySize - 96;
        titleLabelY = 5;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        GuiTextScrollList scrollList = addButton(new GuiTextScrollList(this, 7, 29, 162, 42));
        addButton(new GuiSlot(SlotType.NORMAL, this, 5, 5).setRenderHover(true));
        target = addButton(new GuiDictionaryTarget(this, 6, 6, scrollList::setText));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        if (target == null) {
            drawTitleText(new TextComponentString(LangUtils.localize("item.Dictionary.name")), titleLabelY);
        } else {
            drawTitleTextWithOffset(new TextComponentString(LangUtils.localize("item.Dictionary.name")), target.getRelativeRight(), titleLabelY, getXSize());
        }
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 0 && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && target != null && !target.hasTarget()) {
            Slot slot = getSlotAtPosition(mouseX, mouseY);
            if (slot != null) {
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty()) {
                    target.setTarget(stack);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

}
