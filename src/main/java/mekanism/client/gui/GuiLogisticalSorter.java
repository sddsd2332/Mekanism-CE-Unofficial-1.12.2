package mekanism.client.gui;

import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.filter.transporter.*;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.transporter.*;
import mekanism.common.inventory.container.ContainerFilterHolder;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.TileEntityLogisticalSorter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.IntConsumer;

public class GuiLogisticalSorter extends GuiFilterHolder<TransporterFilter, TileEntityLogisticalSorter, ContainerFilterHolder> {

    public GuiLogisticalSorter(InventoryPlayer inventory, TileEntityLogisticalSorter tile) {
        super(tile, new ContainerFilterHolder(inventory, tile));
    }

    public GuiLogisticalSorter(EntityPlayer player, TileEntityLogisticalSorter tile) {
        this(player.inventory, tile);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiSlot(SlotType.NORMAL, this, 12, 136).setRenderAboveSlots());
        addButton(new TranslationButton(this, 96, 136, 156, 20, MekanismLang.BUTTON_NEW_FILTER,
              () -> addWindow(new GuiSorterFilterSelect(this, tileEntity))));
        addButton(new MekanismImageButton(this, 12, 46, 14, getButtonLocation("single"),
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(5))))
              .setTooltip(MekanismLang.SORTER_SINGLE_ITEM_DESCRIPTION.translate()));
        addButton(new MekanismImageButton(this, 12, 76, 14, getButtonLocation("round_robin"),
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(2))))
              .setTooltip(MekanismLang.SORTER_ROUND_ROBIN_DESCRIPTION.translate()));
        addButton(new MekanismImageButton(this, 12, 106, 14, getButtonLocation("auto_eject"),
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(1))))
              .setTooltip(MekanismLang.SORTER_AUTO_EJECT_DESCRIPTION.translate()));
        addButton(new ColorButton(this, 13, 137, 16, 16, () -> tileEntity.color,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 2 : 0))),
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0, 1)))));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        super.drawForegroundText(mouseX, mouseY);
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        drawScreenText(MekanismLang.FILTER_COUNT.translate(getFilterManager().count()), 4);
        drawScreenText(MekanismLang.SORTER_SINGLE_ITEM.translate(), 19);
        drawScreenText((tileEntity.singleItem ? MekanismLang.ON : MekanismLang.OFF).translate(), 14, 32);
        drawScreenText(MekanismLang.SORTER_ROUND_ROBIN.translate(), 49);
        drawScreenText((tileEntity.roundRobin ? MekanismLang.ON : MekanismLang.OFF).translate(), 14, 62);
        drawScreenText(MekanismLang.SORTER_AUTO_EJECT.translate(), 79);
        drawScreenText((tileEntity.autoEject ? MekanismLang.ON : MekanismLang.OFF).translate(), 14, 92);
        drawScreenText(MekanismLang.SORTER_DEFAULT.translate(), 109);
    }

    @Override
    protected void onClick(IFilter filter, int index) {
        if (filter instanceof TItemStackFilter itemFilter) {
            addWindow(GuiSorterItemStackFilter.edit(this, tileEntity, itemFilter));
        } else if (filter instanceof TOreDictFilter oreFilter) {
            addWindow(GuiSorterOreDictFilter.edit(this, tileEntity, oreFilter));
        } else if (filter instanceof TMaterialFilter materialFilter) {
            addWindow(GuiSorterMaterialFilter.edit(this, tileEntity, materialFilter));
        } else if (filter instanceof TModIDFilter modIDFilter) {
            addWindow(GuiSorterModIDFilter.edit(this, tileEntity, modIDFilter));
        }
    }

    @Override
    protected IntConsumer getMoveUpSender() {
        return index -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 7 : 3, index)));
    }

    @Override
    protected IntConsumer getMoveDownSender() {
        return index -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 8 : 4, index)));
    }

    @Override
    protected IntConsumer getToggleSender() {
        return index -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(6, index)));
    }

    @Override
    protected List<ItemStack> getOreDictStacks(String oreName) {
        return OreDictCache.getOreDictStacks(oreName, false);
    }

    @Override
    protected List<ItemStack> getModIDStacks(String modID) {
        return OreDictCache.getModIDStacks(modID, false);
    }
}
