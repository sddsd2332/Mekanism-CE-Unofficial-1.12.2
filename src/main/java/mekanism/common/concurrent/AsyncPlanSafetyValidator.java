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
import mekanism.common.recipe.cache.GasFuelSnapshot;
import mekanism.common.recipe.cache.GasFuelPlan;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Structural gate for stateless calculators and detached immutable worker values. */
public final class AsyncPlanSafetyValidator {

    private static final int MAX_DEPTH = 128;

    private AsyncPlanSafetyValidator() {
    }

    public static boolean isDetached(IAsyncPlanCalculator<?, ?> calculator) {
        if (calculator == null || calculator instanceof TileEntity || calculator instanceof World) {
            return false;
        }
        try {
            Class<?> type = calculator.getClass();
            while (type != null && type != Object.class) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        // Instance fields include lambda captures and implicit enclosing owners.
                        return false;
                    }
                }
                type = type.getSuperclass();
            }
            return true;
        } catch (LinkageError | RuntimeException error) {
            // Resolving field metadata can fail before any field value is read.
            return false;
        }
    }

    /** Closed set of worker value roots supplied by the core planner. */
    public static boolean isDetachedValue(Object value) {
        if (value == null || isScalar(value) || isTrustedCoreValue(value.getClass())) return true;
        try {
            return isDetachedValue(value, new IdentityHashMap<>(), 0);
        } catch (IllegalAccessException | LinkageError | RuntimeException error) {
            return false;
        }
    }

    private static boolean isDetachedValue(Object value, IdentityHashMap<Object, Boolean> visited,
          int depth) throws IllegalAccessException {
        if (depth > MAX_DEPTH) return false;
        if (value == null || isScalar(value)) return true;
        if (isKnownLiveState(value)) return false;
        // Core snapshot/plan classes are closed value types. Their constructors
        // defensively copy all resource values and expose no arbitrary object graph;
        // re-reflecting every field for every machine tick was the dominant cost in
        // large worlds. Exact runtime classes are required so an addon subclass
        // cannot smuggle live state through this fast path.
        Class<?> type = value.getClass();
        if (isTrustedCoreValue(type)) return true;
        if (visited.put(value, Boolean.TRUE) != null) return true;
        if (value instanceof Map<?, ?>) {
            if (type != Collections.emptyMap().getClass() && type != Collections.singletonMap(null, null).getClass()) return false;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!isDetachedValue(entry.getKey(), visited, depth + 1) ||
                    !isDetachedValue(entry.getValue(), visited, depth + 1)) return false;
            }
            return true;
        }
        if (value instanceof Iterable<?>) {
            // Unmodifiable views still retain their mutable backing collection and any hidden owner.
            if (type != Collections.emptyList().getClass() && type != Collections.singletonList(null).getClass() &&
                type != Collections.emptySet().getClass() && type != Collections.singleton(null).getClass()) return false;
            for (Object element : (Iterable<?>) value) {
                if (!isDetachedValue(element, visited, depth + 1)) return false;
            }
            return true;
        }
        // Raw stacks, NBT and registry objects are not immutable worker values. Core
        // resource constructors freeze them into ImmutableResourceSnapshot instead.
        if (!(value instanceof RecipeRunSnapshot || value instanceof RecipeExecutionPlan || value instanceof Enum<?>)) return false;
        while (type != null && type != Object.class && type != Enum.class) {
            if (isTrustedCoreValue(type)) return true;
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!Modifier.isFinal(field.getModifiers())) return false;
                if (!field.isAccessible()) field.setAccessible(true);
                if (!isDetachedValue(field.get(value), visited, depth + 1)) return false;
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

    private static boolean isTrustedCoreValue(Class<?> type) {
        return type == RecipeRunSnapshot.class || type == GasFuelSnapshot.class ||
              type == RecipeExecutionPlan.class || type == GasFuelPlan.class ||
              type == ImmutableResourceSnapshot.class || type == RecipeSemanticsSnapshot.class ||
              type == RecipeResourceFlow.class || type == RecipeLaneSnapshot.class ||
              type == RecipeLanePlan.class || type == GasFuelState.class;
    }

    private static boolean isScalar(Object value) {
        Class<?> type = value.getClass();
        return type == String.class || type == Boolean.class || type == Character.class ||
              type == Byte.class || type == Short.class || type == Integer.class || type == Long.class ||
              type == Float.class || type == Double.class || type == BigInteger.class || type == BigDecimal.class;
    }
}
