package mekanism.client.gui.element.window.filter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.content.filter.IOreDictFilter;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public abstract class GuiOreDictFilter<FILTER extends IOreDictFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> extends GuiTextFilter<FILTER, TILE> {

    private List<ItemStack> iterStacks;

    protected GuiOreDictFilter(IGuiWrapper gui, int x, int y, int width, int height, TILE tile, @Nullable FILTER origFilter) {
        super(gui, x, y, width, height, MekanismLang.TAG_FILTER.translate().getFormattedText(), tile, origFilter);
        updateStackList(filter.getOreDictName());
        slotDisplay.updateStackList();
    }

    @Override
    protected boolean hasFilter() {
        return filter.getOreDictName() != null && !filter.getOreDictName().isEmpty();
    }

    @Override
    protected String getNoFilterSaveError() {
        return MekanismLang.TAG_FILTER_NO_TAG.translate().getFormattedText();
    }

    @Override
    protected boolean setText() {
        return setFilterName(text.getText(), false);
    }

    private boolean setFilterName(String name, boolean click) {
        boolean success = false;
        if (name.isEmpty()) {
            filterSaveFailed(MekanismLang.TAG_FILTER_NO_TAG);
        } else if (name.equals(filter.getOreDictName())) {
            filterSaveFailed(MekanismLang.TAG_FILTER_SAME_TAG);
        } else if (!hasMatchingTargets(name)) {
            filterSaveFailed(MekanismLang.TEXT_FILTER_NO_MATCHES);
        } else {
            updateStackList(name);
            filter.setOreDictName(name);
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
    protected IGhostIngredientConsumer getGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Nullable
            @Override
            public String supportedTarget(Object ingredient) {
                if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                    List<String> names = OreDictCache.getOreDictName(stack);
                    return names.isEmpty() ? null : names.get(0);
                }
                return null;
            }

            @Override
            public void accept(Object ingredient) {
                if (ingredient instanceof String oreName) {
                    setFilterName(oreName, true);
                }
            }
        };
    }

    protected void updateStackList(String oreName) {
        iterStacks = oreName == null || oreName.isEmpty() ? Collections.emptyList() : OreDictCache.getOreDictStacks(oreName, false);
    }

    protected boolean hasMatchingTargets(String oreName) {
        return !OreDictCache.getOreDictStacks(oreName, false).isEmpty();
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = super.getScreenText();
        list.add(MekanismLang.TAG_FILTER_TAG.translate(filter.getOreDictName()));
        return list;
    }

    @Nonnull
    @Override
    protected List<ItemStack> getRenderStacks() {
        return iterStacks == null ? Collections.emptyList() : iterStacks;
    }
}
