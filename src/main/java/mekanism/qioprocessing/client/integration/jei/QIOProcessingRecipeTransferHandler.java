package mekanism.qioprocessing.client.integration.jei;

import mekanism.client.jei.QIOCraftingTransferHandler;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IStackHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Routes JEI transfer to exactly the QIO window that owned focus before JEI opened. */
/**
 * QIO 处理模块中的 QIOProcessingRecipeTransferHandler 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingRecipeTransferHandler<CONTAINER extends QIOItemViewerContainer & QIOSmartProcessingPageContainer>
      implements IRecipeTransferHandler<CONTAINER> {

    enum TargetMode {
        CRAFTING,
        ORDER,
        NONE
    }

    private final Class<CONTAINER> containerClass;
    private final IRecipeTransferHandlerHelper helper;
    private final QIOCraftingTransferHandler<CONTAINER> crafting;

    public QIOProcessingRecipeTransferHandler(Class<CONTAINER> containerClass,
          IRecipeTransferHandlerHelper helper, IStackHelper stackHelper) {
        this.containerClass = containerClass;
        this.helper = helper;
        crafting = new QIOCraftingTransferHandler<>(containerClass, helper, stackHelper);
    }

    @Override
    public Class<CONTAINER> getContainerClass() {
        return containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(CONTAINER container,
          IRecipeLayout recipeLayout, EntityPlayer player, boolean maxTransfer,
          boolean doTransfer) {
        TargetMode mode = targetMode(container.getSelectedWindow());
        if (mode == TargetMode.CRAFTING) {
            return crafting.transferRecipe(container, recipeLayout, player, maxTransfer,
                  doTransfer);
        }
        if (mode != TargetMode.ORDER || !container.getTerminalState().isValid() ||
            container.getTerminalState().getSessionNonce() == null) {
            return helper.createUserErrorWithTooltip(I18n.translateToLocal(
                  "gui.mekanismqioprocessing.jei_no_target"));
        }
        ItemStack output = recipeOutput(recipeLayout);
        if (output.isEmpty()) return helper.createInternalError();
        PortableResourceDescriptor target;
        try {
            target = PortableResourceDescriptor.itemIgnoringCapabilities(output);
        } catch (RuntimeException e) {
            return helper.createInternalError();
        }
        if (!container.getSmartProcessingClientCache().canSelectRecipeViewerTarget(
              container.getTerminalState().getSessionNonce(), target)) {
            return helper.createUserErrorWithTooltip(I18n.translateToLocal(
                  "gui.mekanismqioprocessing.jei_unschedulable"));
        }
        if (doTransfer && !container.getSmartProcessingClientCache()
              .selectRecipeViewerTarget(container.getTerminalState().getSessionNonce(), target)) {
            return helper.createUserErrorWithTooltip(I18n.translateToLocal(
                  "gui.mekanismqioprocessing.jei_stale_session"));
        }
        return null;
    }

    static TargetMode targetMode(@Nullable SelectedWindowData selected) {
        if (selected == null) return TargetMode.NONE;
        if (selected.type == SelectedWindowData.WindowType.CRAFTING &&
            selected.extraData >= 0 && selected.extraData < 3) {
            return TargetMode.CRAFTING;
        }
        return selected.type == QIOProcessingWindowTypes.SMART_PROCESSING_ORDER ?
              TargetMode.ORDER : TargetMode.NONE;
    }

    private static ItemStack recipeOutput(IRecipeLayout layout) {
        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> ingredients =
              new ArrayList<>(layout.getItemStacks().getGuiIngredients().entrySet());
        ingredients.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : ingredients) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (!ingredient.isInput() && ingredient.getDisplayedIngredient() != null &&
                !ingredient.getDisplayedIngredient().isEmpty()) {
                return QIORecipeStackUtils.copyForRecipeSelection(
                      ingredient.getDisplayedIngredient());
            }
        }
        return ItemStack.EMPTY;
    }
}
