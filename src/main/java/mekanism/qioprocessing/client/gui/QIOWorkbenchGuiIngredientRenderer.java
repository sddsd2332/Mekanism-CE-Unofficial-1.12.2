package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Candidate;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Ingredient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Renders the resource consumed by a logical workbench slot, not its validation container. */
/**
 * QIO 处理模块中的 QIOWorkbenchGuiIngredientRenderer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOWorkbenchGuiIngredientRenderer {

    private QIOWorkbenchGuiIngredientRenderer() {
    }

    static void render(IGuiWrapper gui, Ingredient ingredient, int x, int y, int size) {
        render(gui, ingredient.getRepresentative(), ingredient.getVirtualFluid(), x, y, size);
    }

    static void render(IGuiWrapper gui, Candidate candidate, int x, int y, int size) {
        render(gui, candidate.getDisplayStack(), candidate.isVirtualFluid() ?
              candidate.getResource() : null, x, y, size);
    }

    private static void render(IGuiWrapper gui, ItemStack displayStack,
          @Nullable PortableResourceDescriptor virtualFluid, int x, int y, int size) {
        if (virtualFluid != null) {
            QIOGuiResourceRenderer.renderIcon(gui, virtualFluid, x, y, size);
        } else if (!displayStack.isEmpty()) {
            gui.renderItem(displayStack, x, y, size / 16F);
        }
    }

    static List<String> fluidTooltip(Ingredient ingredient) {
        return fluidTooltip(ingredient.getVirtualFluid(), ingredient.getVirtualFluidAmount(),
              ingredient.getRepresentative());
    }

    static List<String> fluidTooltip(Candidate candidate) {
        return fluidTooltip(candidate.isVirtualFluid() ? candidate.getResource() : null,
              candidate.getAmount(), candidate.getDisplayStack());
    }

    private static List<String> fluidTooltip(@Nullable PortableResourceDescriptor resource,
          long amount, ItemStack recipeContainer) {
        List<String> tooltip = new ArrayList<>();
        if (resource == null) return tooltip;
        tooltip.add(QIOGuiResourceRenderer.name(resource));
        tooltip.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_direct_fluid_amount",
              Long.toString(amount)).getFormattedText());
        tooltip.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_direct_fluid_behavior")
              .getFormattedText());
        if (!recipeContainer.isEmpty()) {
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.workbench_direct_fluid_container",
                  recipeContainer.getDisplayName()).getFormattedText());
        }
        return tooltip;
    }
}
