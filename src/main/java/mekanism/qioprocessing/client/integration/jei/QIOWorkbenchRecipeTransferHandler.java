package mekanism.qioprocessing.client.integration.jei;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer;
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** JEI transfer target for the frequency-local 3x3 workbench pattern encoder. */
/**
 * QIO 处理模块中的 QIOWorkbenchRecipeTransferHandler 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchRecipeTransferHandler<CONTAINER extends MekanismContainer & QIOWorkbenchConfigurationContainer>
      implements IRecipeTransferHandler<CONTAINER> {

    enum TargetMode {
        PATTERN,
        BATCH,
        NONE
    }

    private final Class<CONTAINER> containerClass;
    private final IRecipeTransferHandlerHelper helper;

    public QIOWorkbenchRecipeTransferHandler(Class<CONTAINER> containerClass,
          IRecipeTransferHandlerHelper helper) {
        this.containerClass = containerClass;
        this.helper = helper;
    }

    @Override
    public Class<CONTAINER> getContainerClass() {
        return containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(CONTAINER container, IRecipeLayout recipeLayout,
          EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        SelectedWindowData selected = container.getSelectedWindow();
        TargetMode targetMode = targetMode(selected);
        if (targetMode == TargetMode.NONE) {
            return helper.createInternalError();
        }
        if (targetMode == TargetMode.PATTERN) {
            List<ItemStack> grid = extractGrid(recipeLayout);
            if (grid == null) {
                return helper.createUserErrorWithTooltip(I18n.translateToLocal(
                      "gui.mekanismqioprocessing.jei_invalid_workbench_pattern"));
            }
            if (doTransfer) {
                container.getWorkbenchConfigurationClientCache().setEncodingGrid(grid);
            }
            return null;
        }
        List<ItemStack> outputs = extractOutputs(recipeLayout);
        if (outputs.isEmpty()) {
            return helper.createUserErrorWithTooltip(I18n.translateToLocal(
                  "gui.mekanismqioprocessing.jei_invalid_workbench_target"));
        }
        if (doTransfer) {
            container.getWorkbenchConfigurationClientCache().addBatchTargets(maxTransfer ?
                  outputs : Collections.singletonList(outputs.get(0)));
        }
        return null;
    }

    static boolean isWorkbenchTarget(@Nullable SelectedWindowData selected) {
        return targetMode(selected) != TargetMode.NONE;
    }

    static TargetMode targetMode(@Nullable SelectedWindowData selected) {
        if (selected == null) return TargetMode.NONE;
        if (QIOProcessingWindowTypes.isWorkbenchPatternWindow(selected)) {
            return TargetMode.PATTERN;
        }
        if (QIOProcessingWindowTypes.isWorkbenchBatchWindow(selected)) {
            return TargetMode.BATCH;
        }
        return TargetMode.NONE;
    }

    @Nullable
    static List<ItemStack> extractGrid(IRecipeLayout layout) {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) grid.add(ItemStack.EMPTY);
        boolean[] seen = new boolean[9];
        boolean hasInput = false;
        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> entries =
              new ArrayList<>(layout.getItemStacks().getGuiIngredients().entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : entries) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (!ingredient.isInput()) continue;
            int slot = entry.getKey() - 1;
            if (slot < 0 || slot >= 9 || seen[slot]) return null;
            seen[slot] = true;
            ItemStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty()) continue;
            ItemStack copy = QIORecipeStackUtils.copyForRecipeSelection(displayed, 1);
            if (copy.isEmpty()) return null;
            grid.set(slot, copy);
            hasInput = true;
        }
        return hasInput ? immutableGrid(grid) : null;
    }

    @Nonnull
    static List<ItemStack> extractOutputs(IRecipeLayout layout) {
        List<ItemStack> outputs = new ArrayList<>();
        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> entries =
              new ArrayList<>(layout.getItemStacks().getGuiIngredients().entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : entries) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (ingredient.isInput()) continue;
            ItemStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty()) continue;
            ItemStack copy = QIORecipeStackUtils.copyForRecipeSelection(displayed, 1);
            if (copy.isEmpty()) continue;
            outputs.add(copy);
        }
        return immutableGrid(outputs);
    }

    @Nonnull
    private static List<ItemStack> immutableGrid(List<ItemStack> grid) {
        List<ItemStack> copy = new ArrayList<>(grid.size());
        grid.forEach(stack -> copy.add(QIORecipeStackUtils.copyForRecipeSelection(stack)));
        return java.util.Collections.unmodifiableList(copy);
    }
}
