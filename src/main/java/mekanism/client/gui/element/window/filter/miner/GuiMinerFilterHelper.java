package mekanism.client.gui.element.window.filter.miner;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.filter.GuiFilterHelper;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostBlockItemConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public interface GuiMinerFilterHelper extends GuiFilterHelper<TileEntityDigitalMiner> {

    int MINER_FILTER_WIDTH = 173;

    MinerFilter getMinerFilter();

    TileEntityDigitalMiner getMinerTile();

    int getRelativeX();

    int getRelativeY();

    IGuiWrapper gui();

    <ELEMENT extends GuiElement> ELEMENT addMinerChild(ELEMENT element);

    @Override
    default MinerFilter getFilter() {
        return getMinerFilter();
    }

    @Override
    default GuiMinerFilterSelect getFilterSelect(IGuiWrapper gui, TileEntityDigitalMiner tile) {
        return new GuiMinerFilterSelect(gui, tile);
    }

    default void addMinerDefaults(int slotOffset) {
        addMinerChild(new GuiSlot(SlotType.NORMAL, gui(), getRelativeX() + 148, getRelativeY() + slotOffset).setRenderHover(true)
              .stored(() -> getMinerFilter().replaceStack).click((element, mouseX, mouseY) -> {
                  handleReplaceSlotClick();
                  return true;
              }).setGhostHandler(new IGhostBlockItemConsumer() {
                  @Override
                  public ItemStack supportedTarget(Object ingredient) {
                      ItemStack stack = IGhostBlockItemConsumer.super.supportedTarget(ingredient);
                      return stack != null && Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK ? stack : null;
                  }

                  @Override
                  public void accept(Object ingredient) {
                      getMinerFilter().replaceStack = StackUtils.size((ItemStack) ingredient, 1);
                  }
              }));
        addMinerChild(new TooltipToggleButton(gui(), getRelativeX() + 148, getRelativeY() + 45, 14, 16,
              MekanismUtils.getResource(ResourceType.GUI_BUTTON, "exclamation.png"),
              () -> getMinerFilter().requireStack,
              () -> getMinerFilter().requireStack = !getMinerFilter().requireStack,
              MekanismLang.MINER_REQUIRE_REPLACE.translate(), MekanismLang.MINER_REQUIRE_REPLACE_INVERSE.translate()));
    }

    default void handleReplaceSlotClick() {
        ItemStack stack = mekanism.client.gui.element.GuiElement.minecraft.player.inventory.getItemStack();
        ItemStack toUse = null;
        if (!stack.isEmpty() && !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && stack.getItem() instanceof ItemBlock &&
              Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK) {
            toUse = stack.copy();
            toUse.setCount(1);
        } else if (stack.isEmpty() && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            toUse = ItemStack.EMPTY;
        }
        if (toUse != null) {
            getMinerFilter().replaceStack = toUse;
        }
    }

}
