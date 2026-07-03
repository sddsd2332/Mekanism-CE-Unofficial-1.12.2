package mekanism.client.gui.element.window.filter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostBlockItemConsumer;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.content.filter.IMaterialFilter;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.StackUtils;
import mekanism.common.util.LangUtils;
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

public abstract class GuiMaterialFilter<FILTER extends IMaterialFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> extends GuiFilter<FILTER, TILE> {

    protected GuiMaterialFilter(IGuiWrapper gui, int x, int y, int width, int height, TILE tile, @Nullable FILTER origFilter) {
        super(gui, x, y, width, height, LangUtils.localize("gui.materialFilter"), tile, origFilter);
    }

    @Override
    protected boolean hasFilter() {
        return !filter.getMaterialItem().isEmpty();
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
        ItemStack stack = minecraft.player.inventory.getItemStack();
        ItemStack toUse = null;
        if (!stack.isEmpty() && !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && stack.getItem() instanceof ItemBlock &&
              Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK) {
            toUse = stack.copy();
            toUse.setCount(1);
        } else if (stack.isEmpty() && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            toUse = ItemStack.EMPTY;
        }
        if (toUse != null) {
            setMaterialStack(toUse);
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    protected IGhostIngredientConsumer getGhostHandler() {
        return new IGhostBlockItemConsumer() {
            @Nullable
            @Override
            public ItemStack supportedTarget(Object ingredient) {
                ItemStack stack = IGhostBlockItemConsumer.super.supportedTarget(ingredient);
                return stack != null && Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK ? stack : null;
            }

            @Override
            public void accept(Object ingredient) {
                setMaterialStack(StackUtils.size((ItemStack) ingredient, 1));
                playClickSound();
            }
        };
    }

    private void setMaterialStack(ItemStack stack) {
        filter.setMaterialItem(stack);
        slotDisplay.updateStackList();
    }

    @Nonnull
    @Override
    protected List<ItemStack> getRenderStacks() {
        return filter.getMaterialItem().isEmpty() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(filter.getMaterialItem());
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = super.getScreenText();
        list.add(new TextComponentString(LangUtils.localize("gui.materialFilter.details") + ":"));
        ItemStack stack = filter.getMaterialItem();
        if (!stack.isEmpty()) {
            list.add(new TextComponentString(stack.getDisplayName()));
        }
        return list;
    }
}
