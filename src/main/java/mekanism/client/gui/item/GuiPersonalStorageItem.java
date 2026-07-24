package mekanism.client.gui.item;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.common.inventory.InventoryPersonalChest;
import mekanism.common.inventory.container.item.PersonalStorageItemContainer;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiPersonalStorageItem extends GuiMekanism<PersonalStorageItemContainer> {

    public GuiPersonalStorageItem(InventoryPlayer inventory, InventoryPersonalChest inv) {
        super(new PersonalStorageItemContainer(inventory, inv));
        init();
    }

    public GuiPersonalStorageItem(InventoryPlayer inventory, EnumHand hand, int itemSlot, ItemStack stack) {
        super(new PersonalStorageItemContainer(inventory, hand, itemSlot, stack));
        init();
    }

    private void init() {
        dynamicSlots = true;
        ySize += 56;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiSecurityTab<>(this, (PersonalStorageItemContainer) inventorySlots));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("tile.MachineBlock.PersonalChest.name")), 6);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
