package mekanism.qioprocessing.common.planning;

import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Main-thread execution pattern for one exact workbench Ingredient variant.
 */

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
    private final List<QIOWorkbenchDeclarations.Stack> grid;
    private final boolean[] virtualFluidSlots;
    private final Map<PortableResourceDescriptor, Long> exactInputs;
    private final Map<PortableResourceDescriptor, Long> expectedOutputs;
    private final long retainedBytes;

    QIOWorkbenchRecipePattern(@Nonnull QIOPlanningRoute route, @Nonnull ResourceLocation recipeId,
                              @Nonnull List<ItemStack> grid, @Nonnull boolean[] virtualFluidSlots) {
        this(route, recipeId, freeze(grid), virtualFluidSlots);
    }

    public static QIOWorkbenchRecipePattern frozen(QIOPlanningRoute route, ResourceLocation recipeId,
                                                   List<QIOWorkbenchDeclarations.Stack> grid, boolean[] virtualFluidSlots) {
        return new QIOWorkbenchRecipePattern(route, recipeId, grid, virtualFluidSlots);
    }

    private static List<QIOWorkbenchDeclarations.Stack> freeze(List<ItemStack> grid) {
        List<QIOWorkbenchDeclarations.Stack> copy = new ArrayList<>(9);
        for (ItemStack stack : grid) copy.add(QIOWorkbenchDeclarations.Stack.capture(stack, 1));
        return copy;
    }

    private QIOWorkbenchRecipePattern(QIOPlanningRoute route, ResourceLocation recipeId,
                                      java.util.Collection<QIOWorkbenchDeclarations.Stack> grid, boolean[] virtualFluidSlots) {
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
        List<QIOWorkbenchDeclarations.Stack> copy = new ArrayList<>(grid);
        this.grid = Collections.unmodifiableList(copy);
        this.virtualFluidSlots = virtualFluidSlots.clone();
        retainedBytes = 1024 + QIOWorkbenchMemory.text(stableRouteId) + QIOWorkbenchMemory.stacks(this.grid) +
                QIOWorkbenchMemory.resources(exactInputs) + QIOWorkbenchMemory.resources(expectedOutputs);
    }

    long getRetainedBytes() {
        // This cache can retain an unresolved variant. Reserve its entire result allowance;
        // the callback may grow the variant without consulting the execution pattern cache.
        return retainedBytes + (route.verification() == null ? 0 : route.verification().getMaximumRetainedBytes());
    }

    @Nonnull
    QIOPlanningRoute getRoute() {
        return route;
    }

    @Nullable
    QIOWorkbenchRecipePattern validatedExecution() {
        QIOWorkbenchVariant verification = route.verification();
        return verification == null ? this : verification.currentEpoch() ? verification.execution() : null;
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
        grid.forEach(stack -> copy.add(stack.thaw()));
        return Collections.unmodifiableList(copy);
    }

    /** Returns the recipe's primary output for display; runtime execution still revalidates it. */
    @Nonnull
    public ItemStack getDisplayOutput() {
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(recipeId);
        if (recipe != null) {
            ItemStack output = recipe.getRecipeOutput();
            if (output != null && !output.isEmpty()) {
                return QIORecipeStackUtils.copyForRecipeSelection(output);
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
        QIOWorkbenchDirectoryService.requireServerThread(world);
        if (route.verification() != null && (!route.verification().resolved() ||
                !route.verification().valid() || !route.verification().currentEpoch())) return null;
        QIOWorkbenchValidationService.Request request = new QIOWorkbenchValidationService.Request(
                java.util.UUID.randomUUID(), recipeId, world.provider.getDimension(),
                QIOWorkbenchDirectoryService.INSTANCE.getEpoch(), grid, virtualFluidSlots, null, 0);
        QIOWorkbenchValidationService.Result result = QIOWorkbenchValidationService.validateProduction(world, request);
        return result.isValid() && exactInputs.equals(result.getConsumedInputs()) &&
                expectedOutputs.equals(result.getOutputs()) ? new CraftResult(result.getOutputs(), result.getOutput(), result.getRemainders()) : null;
    }

    @Nonnull
    InventoryCrafting createInventory() {
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        for (int slot = 0; slot < grid.size(); slot++) {
            inventory.setInventorySlotContents(slot,
                    grid.get(slot).thaw());
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

        private final QIOWorkbenchDeclarations.Stack primaryOutput;
        private final List<QIOWorkbenchDeclarations.Stack> remainders;

        private CraftResult(Map<PortableResourceDescriptor, Long> outputs, QIOWorkbenchDeclarations.Stack primaryOutput,
                            List<QIOWorkbenchDeclarations.Stack> remainders) {
            this.primaryOutput = primaryOutput;
            this.remainders = remainders;
            this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        }

        @Nonnull
        public QIOWorkbenchDeclarations.Stack getPrimaryOutput() {
            return primaryOutput;
        }

        public List<QIOWorkbenchDeclarations.Stack> getRemainders() {
            return remainders;
        }

        public Map<PortableResourceDescriptor, Long> getOutputs() {
            return outputs;
        }
    }
}
