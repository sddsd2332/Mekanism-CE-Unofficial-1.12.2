package mekanism.client.gui.element.window.filter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostItemConsumer;
import mekanism.common.content.filter.IItemStackFilter;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("deprecation")
public abstract class GuiItemStackFilter<FILTER extends IItemStackFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> extends GuiFilter<FILTER, TILE> {

    protected GuiItemStackFilter(IGuiWrapper gui, int x, int y, int width, int height, TILE tile, @Nullable FILTER origFilter) {
        super(gui, x, y, width, height, LangUtils.localize("gui.itemFilter"), tile, origFilter);
    }

    @Override
    protected boolean hasFilter() {
        return !filter.getItemStack().isEmpty();
    }

    @Override
    protected String getNoFilterSaveError() {
        return LangUtils.localize("gui.itemFilter.noItem");
    }

    @Nullable
    @Override
    protected IClickable getSlotClickHandler() {
        return (element, mouseX, mouseY) -> handleTypeSlotClick();
    }

    protected boolean handleTypeSlotClick() {
        ItemStack stack = getCarriedSingleItem(isBlockFilter());
        if (stack != null) {
            setFilterStack(stack);
            return true;
        }
        return false;
    }

    @Nullable
    protected ItemStack getCarriedSingleItem(boolean blockOnly) {
        ItemStack stack = minecraft.player.inventory.getItemStack();
        if (!stack.isEmpty() && !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && (!blockOnly || stack.getItem() instanceof ItemBlock &&
              Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK)) {
            ItemStack toUse = stack.copy();
            toUse.setCount(1);
            return toUse;
        }
        if (stack.isEmpty() && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            return ItemStack.EMPTY;
        }
        return null;
    }

    protected boolean isBlockFilter() {
        return false;
    }

    @Nullable
    @Override
    protected IGhostIngredientConsumer getGhostHandler() {
        return new IGhostItemConsumer() {
            @Nullable
            @Override
            public ItemStack supportedTarget(Object ingredient) {
                ItemStack stack = IGhostItemConsumer.super.supportedTarget(ingredient);
                return stack != null && (!isBlockFilter() || stack.getItem() instanceof ItemBlock &&
                      Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK) ? stack : null;
            }

            @Override
            public void accept(Object ingredient) {
                setFilterStack(StackUtils.size((ItemStack) ingredient, 1));
                playClickSound();
            }
        };
    }

    protected void setFilterStack(ItemStack stack) {
        filter.setItemStack(stack);
        slotDisplay.updateStackList();
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = super.getScreenText();
        ItemStack stack = filter.getItemStack();
        if (!stack.isEmpty()) {
            list.add(new TextComponentString(stack.getDisplayName()));
        }
        return list;
    }

    @Nonnull
    @Override
    protected List<ItemStack> getRenderStacks() {
        return filter.getItemStack().isEmpty() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(filter.getItemStack());
    }
}
