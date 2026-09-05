package mekanism.common.concurrent;

import mekanism.api.IAsyncPlanCalculator;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.cache.ImmutableResourceSnapshot;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.RecipeResourceFlow;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import mekanism.common.recipe.cache.RecipeSemanticsSnapshot;
import mekanism.common.recipe.cache.RecipeLaneSnapshot;
import mekanism.common.recipe.cache.RecipeLanePlan;
import mekanism.common.recipe.cache.GasFuelState;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Structural gate which permits only stateless detached worker calculators. */
public final class AsyncPlanSafetyValidator {

    private AsyncPlanSafetyValidator() {
    }

    public static boolean isDetached(IAsyncPlanCalculator<?, ?> calculator) {
        if (calculator == null || calculator instanceof TileEntity || calculator instanceof World) {
            return false;
        }
        Class<?> type = calculator.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    // A zero-capture lambda or stateless singleton has no instance
                    // fields. Reject every captured/configured object so a worker
                    // cannot retain a hidden Tile/World/handler through Object.
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        return true;
    }

    /** Closed set of worker value roots supplied by the core planner. */
    public static boolean isDetachedValue(Object value) {
        return isDetachedValue(value, new IdentityHashMap<>());
    }

    private static boolean isDetachedValue(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || isScalar(value)) return true;
        if (isKnownLiveState(value)) return false;
        if (visited.put(value, Boolean.TRUE) != null) return true;
        if (value instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!isDetachedValue(entry.getKey(), visited) || !isDetachedValue(entry.getValue(), visited)) return false;
            }
            return true;
        }
        if (value instanceof Iterable<?>) {
            for (Object element : (Iterable<?>) value) if (!isDetachedValue(element, visited)) return false;
            return true;
        }
        Class<?> type = value.getClass();
        if (!(value instanceof RecipeRunSnapshot || value instanceof RecipeExecutionPlan ||
              value instanceof ImmutableResourceSnapshot || value instanceof RecipeSemanticsSnapshot ||
              value instanceof RecipeResourceFlow || value instanceof RecipeLaneSnapshot ||
              value instanceof RecipeLanePlan || value instanceof GasFuelState)) return false;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    if (!field.isAccessible()) field.setAccessible(true);
                    if (!isDetachedValue(field.get(value), visited)) return false;
                } catch (IllegalAccessException | RuntimeException e) {
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        return true;
    }

    /** Rejects known live state and callbacks even when exposed through Object. */
    public static boolean isKnownLiveState(Object value) {
        return value instanceof TileEntity || value instanceof World || value instanceof Capability<?> ||
              value instanceof IInventorySlot || value instanceof IExtendedFluidTank ||
              value instanceof IExtendedGasTank || value instanceof IEnergyContainer ||
              value instanceof MachineRecipe<?, ?, ?> || value instanceof MachineInput<?> ||
              value instanceof MachineOutput<?> || value instanceof Supplier<?> ||
              value instanceof Consumer<?> || value instanceof Runnable;
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean ||
              value instanceof Character || value instanceof Enum<?>;
    }
}
