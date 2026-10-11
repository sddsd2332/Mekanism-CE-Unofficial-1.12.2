package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDirectoryService;
import mekanism.qioprocessing.common.recipe.RecipeDiscovery;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-thread adapter for the new Workbench data layer. Legacy configuration is not consulted.
 */
public final class WorkbenchCoreFacade {
    private WorkbenchCoreFacade() {
    }

    public static WorkbenchSnapshot load(World world, UUID frequency) throws IOException {
        return WorkbenchBookRuntime.loadOrBlank(world, frequency);
    }

    public static WorkbenchImport.Preview prepareImport(World world, UUID frequency,
                                                        Iterable<ItemStack> targets, RecipeDiscovery.Mode mode) throws IOException {
        WorkbenchSnapshot snapshot = load(world, frequency);
        List<RecipeItemKey> keys = new ArrayList<>();
        for (ItemStack target : targets) {
            if (target != null && !target.isEmpty()) keys.add(RecipeItemKey.capture(target));
        }
        return WorkbenchImport.prepare(snapshot, catalog(), keys, mode);
    }

    public static WorkbenchSnapshot commitImport(World world, UUID frequency,
                                                 WorkbenchImport.Preview preview) throws IOException {
        WorkbenchSnapshot snapshot = load(world, frequency);
        WorkbenchRecipeBook book = new WorkbenchRecipeBook(snapshot);
        WorkbenchSnapshot committed = book.commit(catalog(), preview);
        WorkbenchBookRuntime.save(world, committed);
        return committed;
    }

    public static WorkbenchSnapshot setEnabled(World world, UUID frequency,
                                               long revision, String recipeId, boolean enabled) throws IOException {
        WorkbenchRecipeBook book = new WorkbenchRecipeBook(load(world, frequency));
        WorkbenchSnapshot next = book.setEnabled(revision, recipeId, enabled);
        WorkbenchBookRuntime.save(world, next);
        return next;
    }

    public static WorkbenchSnapshot setPreferred(World world, UUID frequency,
                                                 long revision, String recipeId) throws IOException {
        WorkbenchRecipeBook book = new WorkbenchRecipeBook(load(world, frequency));
        WorkbenchSnapshot next = book.setPreferred(revision, catalog(), recipeId);
        WorkbenchBookRuntime.save(world, next);
        return next;
    }

    public static WorkbenchSnapshot clearAll(World world, UUID frequency, long revision) throws IOException {
        WorkbenchRecipeBook book = new WorkbenchRecipeBook(load(world, frequency));
        WorkbenchSnapshot next = book.clearAll(revision);
        WorkbenchBookRuntime.save(world, next);
        return next;
    }

    private static RecipeCatalog catalog() {
        RecipeDirectoryService service = RecipeDirectoryService.INSTANCE;
        if (service.state() != RecipeDirectoryService.State.READY) {
            throw new IllegalStateException("QIO recipe directory is not ready: " + service.state() +
                    (service.failure().isEmpty() ? "" : ": " + service.failure()));
        }
        return service.catalog();
    }
}
