package mekanism.client.gui.element.window.filter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.content.filter.IModIDFilter;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.ItemRegistryUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public abstract class GuiModIDFilter<FILTER extends IModIDFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> extends GuiTextFilter<FILTER, TILE> {

    private List<ItemStack> iterStacks;

    protected GuiModIDFilter(IGuiWrapper gui, int x, int y, int width, int height, TILE tile, @Nullable FILTER origFilter) {
        super(gui, x, y, width, height, MekanismLang.MODID_FILTER.translate().getFormattedText(), tile, origFilter);
        updateStackList(filter.getModID());
        slotDisplay.updateStackList();
    }

    @Override
    protected boolean hasFilter() {
        return filter.getModID() != null && !filter.getModID().isEmpty();
    }

    @Override
    protected String getNoFilterSaveError() {
        return MekanismLang.MODID_FILTER_NO_ID.translate().getFormattedText();
    }

    @Override
    protected boolean setText() {
        return setFilterName(text.getText(), false);
    }

    private boolean setFilterName(String name, boolean click) {
        boolean success = false;
        if (name.isEmpty()) {
            filterSaveFailed(MekanismLang.MODID_FILTER_NO_ID);
        } else if (name.equals(filter.getModID())) {
            filterSaveFailed(MekanismLang.MODID_FILTER_SAME_ID);
        } else if (!hasMatchingTargets(name)) {
            filterSaveFailed(MekanismLang.TEXT_FILTER_NO_MATCHES);
        } else {
            updateStackList(name);
            filter.setModID(name);
            text.clear();
            slotDisplay.updateStackList();
            filterSaveSuccess();
            success = true;
        }
        if (click) {
            playClickSound();
        }
        return success;
    }

    @Nullable
    @Override
    protected IClickable getSlotClickHandler() {
        return (element, mouseX, mouseY) -> {
            if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                ItemStack stack = minecraft.player.inventory.getItemStack();
                if (!stack.isEmpty()) {
                    return setFilterName(ItemRegistryUtils.getMod(stack), false);
                }
            }
            return false;
        };
    }

    @Nullable
    @Override
    protected IGhostIngredientConsumer getGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Nullable
            @Override
            public String supportedTarget(Object ingredient) {
                if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                    String modID = ItemRegistryUtils.getMod(stack);
                    return modID.equals("null") ? null : modID;
                }
                return null;
            }

            @Override
            public void accept(Object ingredient) {
                if (ingredient instanceof String modID) {
                    setFilterName(modID, true);
                }
            }
        };
    }

    protected void updateStackList(String modName) {
        iterStacks = modName == null || modName.isEmpty() ? Collections.emptyList() : OreDictCache.getModIDStacks(modName, false);
    }

    protected boolean hasMatchingTargets(String modName) {
        return !OreDictCache.getModIDStacks(modName, false).isEmpty();
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = super.getScreenText();
        list.add(MekanismLang.MODID_FILTER_ID.translate(filter.getModID()));
        return list;
    }

    @Nonnull
    @Override
    protected List<ItemStack> getRenderStacks() {
        return iterStacks == null ? Collections.emptyList() : iterStacks;
    }
}
