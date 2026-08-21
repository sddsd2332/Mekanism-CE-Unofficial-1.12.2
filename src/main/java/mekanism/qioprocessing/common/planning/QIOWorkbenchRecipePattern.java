package mekanism.qioprocessing.common.planning;

import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Main-thread execution pattern for one exact workbench Ingredient variant. */
/**
 * QIO 处理模块中的 QIOWorkbenchRecipePattern 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchRecipePattern {

    private final QIOPlanningRoute route;
    private final String stableRouteId;
    private final ResourceLocation recipeId;
    private final String variantId;
    private final String routeSignature;
    private final List<ItemStack> grid;
    private final boolean[] virtualFluidSlots;
    private final Map<PortableResourceDescriptor, Long> exactInputs;
    private final Map<PortableResourceDescriptor, Long> expectedOutputs;

    QIOWorkbenchRecipePattern(@Nonnull QIOPlanningRoute route, @Nonnull ResourceLocation recipeId,
          @Nonnull List<ItemStack> grid, @Nonnull boolean[] virtualFluidSlots) {
        this.route = Objects.requireNonNull(route, "route");
        this.stableRouteId = route.getStableId();
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        variantId = route.getVariantId();
        routeSignature = route.getSignature();
        // QIOPlanningRoute exposes immutable maps; sharing them avoids copying every retained
        // candidate variant during route preparation.
        exactInputs = route.getExactInputs();
        expectedOutputs = route.getGuaranteedOutputs();
        if (grid.size() != 9 || virtualFluidSlots.length != 9) {
            throw new IllegalArgumentException("QIO workbench pattern must contain exactly nine slots");
        }
        List<ItemStack> copy = new ArrayList<>(9);
        for (ItemStack stack : grid) {
            ItemStack checked = stack == null ? ItemStack.EMPTY : stack.copy();
            if (!checked.isEmpty()) {
                checked.setCount(1);
            }
            copy.add(checked);
        }
        this.grid = Collections.unmodifiableList(copy);
        this.virtualFluidSlots = virtualFluidSlots.clone();
    }

    @Nonnull
    QIOPlanningRoute getRoute() {
        return route;
    }

    @Nonnull
    public String getStableRouteId() {
        return stableRouteId;
    }

    @Nonnull
    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    @Nonnull
    public String getVariantId() {
        return variantId;
    }

    @Nonnull
    public String getRouteSignature() {
        return routeSignature;
    }

    @Nonnull
    public List<ItemStack> getGrid() {
        List<ItemStack> copy = new ArrayList<>(grid.size());
        grid.forEach(stack -> copy.add(stack.copy()));
        return Collections.unmodifiableList(copy);
    }

    /** Returns the recipe's primary output for display; runtime execution still revalidates it. */
    @Nonnull
    public ItemStack getDisplayOutput() {
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(recipeId);
        if (recipe != null) {
            ItemStack output = recipe.getRecipeOutput();
            if (output != null && !output.isEmpty()) {
                return output.copy();
            }
        }
        for (Map.Entry<PortableResourceDescriptor, Long> entry : expectedOutputs.entrySet()) {
            ItemStack output = entry.getKey().resolveItem();
            if (!output.isEmpty()) {
                output.setCount((int) Math.max(1, Math.min(output.getMaxStackSize(),
                      entry.getValue())));
                return output;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getExactInputs() {
        return exactInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getExpectedOutputs() {
        return expectedOutputs;
    }

    public boolean isVirtualFluidSlot(int slot) {
        return slot >= 0 && slot < virtualFluidSlots.length && virtualFluidSlots[slot];
    }

    /** Re-resolves and executes the real recipe; no planned output is trusted here. */
    @Nullable
    public CraftResult craft(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(recipeId);
        if (recipe == null || recipe.isDynamic()) {
            return null;
        }
        InventoryCrafting inventory = createInventory();
        if (!recipe.matches(inventory, world)) {
            return null;
        }
        ItemStack result = recipe.getCraftingResult(inventory);
        if (result.isEmpty()) {
            return null;
        }
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(inventory);
        Map<PortableResourceDescriptor, Long> actualOutputs = new LinkedHashMap<>();
        add(actualOutputs, result);
        for (int slot = 0; slot < remaining.size(); slot++) {
            if (!isVirtualFluidSlot(slot)) {
                add(actualOutputs, remaining.get(slot));
            }
        }
        return new CraftResult(actualOutputs);
    }

    @Nonnull
    InventoryCrafting createInventory() {
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        for (int slot = 0; slot < grid.size(); slot++) {
            inventory.setInventorySlotContents(slot, grid.get(slot).copy());
        }
        return inventory;
    }

    private static void add(Map<PortableResourceDescriptor, Long> amounts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        amounts.merge(PortableResourceDescriptor.item(stack), (long) stack.getCount(), Math::addExact);
    }

    public static final class CraftResult {

        private final Map<PortableResourceDescriptor, Long> outputs;

        private CraftResult(Map<PortableResourceDescriptor, Long> outputs) {
            this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        }

        @Nonnull
        public Map<PortableResourceDescriptor, Long> getOutputs() {
            return outputs;
        }
    }
}
