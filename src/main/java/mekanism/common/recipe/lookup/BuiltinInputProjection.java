package mekanism.common.recipe.lookup;

import mekanism.api.gas.GasStack;
import mekanism.common.OreDictCache;
import mekanism.common.InfuseStorage;
import mekanism.common.recipe.inputs.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Explicit main-thread projection of each core input class, including its 1.12 wildcard operation. */
public final class BuiltinInputProjection {
    private BuiltinInputProjection() { }

    public static RecipeLookupQuery query(MachineInput<?> input, ResourceSnapshotRegistry resources) {
        RecipeInputKey exact = key(input, resources);
        RecipeInputKey wildcard = null;
        if (exact.isValid() && input instanceof IWildInput<?>) {
            wildcard = key((MachineInput<?>) ((IWildInput<?>) input).wildCopy(), resources);
        }
        return new RecipeLookupQuery(exact, wildcard);
    }

    public static RecipeInputKey key(MachineInput<?> input, ResourceSnapshotRegistry resources) {
        resources.checkOwner();
        Parts parts = new Parts(resources);
        Class<?> type = input.getClass();
        RecipeInputKey.Shape shape;
        int scalar = 0;
        if (type == ItemStackInput.class) {
            shape = RecipeInputKey.Shape.ITEM; parts.item(((ItemStackInput) input).ingredient);
        } else if (type == AdvancedMachineInput.class) {
            shape = RecipeInputKey.Shape.ADVANCED;
            AdvancedMachineInput value = (AdvancedMachineInput) input;
            parts.item(value.itemStack); parts.gas(value.gasType == null ? null : new GasStack(value.gasType, 1));
        } else if (type == DoubleMachineInput.class) {
            shape = RecipeInputKey.Shape.DOUBLE_ITEM;
            DoubleMachineInput value = (DoubleMachineInput) input;
            parts.item(value.itemStack); parts.item(value.extraStack);
        } else if (type == InfusionInput.class) {
            shape = RecipeInputKey.Shape.INFUSION;
            InfusionInput value = (InfusionInput) input;
            parts.item(value.inputStack);
            parts.values.add(value.infuse == null || value.infuse.getType() == null ? ResourceIdentity.EMPTY :
                  resources.capture(new InfuseStorage(value.infuse.getType(), 1)).getIdentity());
        } else if (type == GasInput.class) {
            shape = RecipeInputKey.Shape.GAS; parts.gas(((GasInput) input).ingredient);
        } else if (type == FluidInput.class) {
            shape = RecipeInputKey.Shape.FLUID; parts.fluid(((FluidInput) input).ingredient);
        } else if (type == GasAndFluidInput.class) {
            shape = RecipeInputKey.Shape.GAS_FLUID;
            GasAndFluidInput value = (GasAndFluidInput) input;
            parts.gas(value.ingredientGas); parts.fluid(value.ingredientFluid);
        } else if (type == ChemicalPairInput.class) {
            shape = RecipeInputKey.Shape.CHEMICAL_PAIR;
            ChemicalPairInput value = (ChemicalPairInput) input;
            parts.gas(value.leftGas); parts.gas(value.rightGas);
        } else if (type == PressurizedInput.class) {
            shape = RecipeInputKey.Shape.PRESSURIZED;
            PressurizedInput value = (PressurizedInput) input;
            parts.item(value.getSolid()); parts.fluid(value.getFluid()); parts.gas(value.getGas());
        } else if (type == NucleosynthesizerInput.class) {
            shape = RecipeInputKey.Shape.NUCLEOSYNTHESIZER;
            NucleosynthesizerInput value = (NucleosynthesizerInput) input;
            parts.item(value.getSolid()); parts.gas(value.getGas());
        } else if (type == ChemicalGasInput.class) {
            shape = RecipeInputKey.Shape.CHEMICAL_TEMPLATE;
            ChemicalGasInput value = (ChemicalGasInput) input;
            parts.gas(value.input); parts.gas(value.uu);
        } else if (type == FarmInput.class) {
            FarmInput value = (FarmInput) input;
            parts.item(value.itemStack);
            if (value.isGasInput()) { shape = RecipeInputKey.Shape.FARM_GAS; parts.gas(value.gasInput); }
            else { shape = RecipeInputKey.Shape.FARM_FLUID; parts.fluid(value.fluidInput); }
        } else if (type == RotaryInput.class) {
            shape = RecipeInputKey.Shape.ROTARY;
            RotaryInput value = (RotaryInput) input;
            parts.fluid(value.fluidInput); parts.gas(value.gasInput);
        } else if (type == IntegerInput.class) {
            shape = RecipeInputKey.Shape.INTEGER; scalar = ((IntegerInput) input).ingredient;
        } else throw new IllegalArgumentException("Input requires an explicit detached projection: " + type.getName());
        boolean valid = input.isValid();
        return new RecipeInputKey(shape, parts.values, scalar, valid ? input.hashCode() : 0, parts.ignoreNbt, valid);
    }

    private static final class Parts {
        final ResourceSnapshotRegistry registry;
        final List<ResourceIdentity> values = new ArrayList<>();
        int ignoreNbt;
        Parts(ResourceSnapshotRegistry registry) { this.registry = registry; }
        void item(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                if (MachineInput.hasCustomItemMatcher(stack)) {
                    throw new IllegalArgumentException("Item matcher requires an explicit detached rule: " + stack.getItem().getRegistryName());
                }
                if (OreDictCache.getOreDictName(stack).contains("treeSapling")) ignoreNbt |= 1 << values.size();
            }
            values.add(registry.captureIngredient(stack));
        }
        void gas(GasStack stack) {
            values.add(stack == null ? ResourceIdentity.EMPTY : registry.capture(new GasStack(stack.getGas(), 1)).getIdentity());
        }
        void fluid(FluidStack stack) {
            values.add(stack == null ? ResourceIdentity.EMPTY : registry.capture(new FluidStack(stack, 1)).getIdentity());
        }
    }
}
