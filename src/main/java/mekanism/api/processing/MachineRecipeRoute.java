package mekanism.api.processing;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import mekanism.api.gas.GasStack;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, network-independent description of one machine recipe route.
 */
public final class MachineRecipeRoute {

    private final String routeId;
    private final String recipeKey;
    private final List<MachineResourceStack> inputs;
    private final List<MachineResourceStack> guaranteedOutputs;
    private final List<MachineResourceStack> optionalOutputs;

    private MachineRecipeRoute(Builder builder) {
        routeId = builder.routeId;
        inputs = orderedCopy(builder.inputs);
        guaranteedOutputs = orderedCopy(builder.guaranteedOutputs);
        optionalOutputs = orderedCopy(builder.optionalOutputs);
        if (inputs.isEmpty() || guaranteedOutputs.isEmpty()) {
            throw new IllegalStateException("Machine recipe routes require at least one input and guaranteed output");
        }
        recipeKey = builder.recipeKey == null || builder.recipeKey.isEmpty() ? createRecipeKey() : builder.recipeKey;
    }

    public static Builder builder(String routeId) {
        return new Builder(routeId);
    }

    public String routeId() {
        return routeId;
    }

    public String recipeKey() {
        return recipeKey;
    }

    public List<MachineResourceStack> inputs() {
        return inputs;
    }

    public List<MachineResourceStack> guaranteedOutputs() {
        return guaranteedOutputs;
    }

    public List<MachineResourceStack> optionalOutputs() {
        return optionalOutputs;
    }

    public long getMaxOperations() {
        long max = Long.MAX_VALUE;
        for (MachineResourceStack stack : allStacks()) {
            max = Math.min(max, Long.MAX_VALUE / stack.amount());
        }
        return Math.max(1, max);
    }

    private List<MachineResourceStack> allStacks() {
        List<MachineResourceStack> stacks = new ArrayList<>(inputs.size() + guaranteedOutputs.size() + optionalOutputs.size());
        stacks.addAll(inputs);
        stacks.addAll(guaranteedOutputs);
        stacks.addAll(optionalOutputs);
        return stacks;
    }

    private String createRecipeKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, routeId);
            update(digest, "inputs");
            inputs.forEach(stack -> update(digest, stack));
            update(digest, "guaranteed");
            guaranteedOutputs.forEach(stack -> update(digest, stack));
            update(digest, "optional");
            optionalOutputs.forEach(stack -> update(digest, stack));
            StringBuilder key = new StringBuilder(routeId).append(':');
            for (byte value : digest.digest()) {
                key.append(String.format("%02x", value & 0xFF));
            }
            return key.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void update(MessageDigest digest, MachineResourceStack stack) {
        update(digest, stack.write(new net.minecraft.nbt.NBTTagCompound()).toString());
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static List<MachineResourceStack> orderedCopy(List<MachineResourceStack> stacks) {
        List<MachineResourceStack> copy = new ArrayList<>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            copy.add(Objects.requireNonNull(stacks.get(i), "Route stack cannot be null").withOrder(i));
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class Builder {

        private final String routeId;
        private final List<MachineResourceStack> inputs = new ArrayList<>();
        private final List<MachineResourceStack> guaranteedOutputs = new ArrayList<>();
        private final List<MachineResourceStack> optionalOutputs = new ArrayList<>();
        private String recipeKey;

        private Builder(String routeId) {
            this.routeId = Objects.requireNonNull(routeId, "Route id cannot be null");
            if (routeId.isEmpty()) {
                throw new IllegalArgumentException("Route id cannot be empty");
            }
        }

        public Builder recipeKey(@Nonnull String recipeKey) {
            this.recipeKey = Objects.requireNonNull(recipeKey, "Recipe key cannot be null");
            return this;
        }

        public Builder input(MachineResourceStack stack) {
            inputs.add(Objects.requireNonNull(stack, "Input cannot be null"));
            return this;
        }

        public Builder output(MachineResourceStack stack) {
            guaranteedOutputs.add(Objects.requireNonNull(stack, "Output cannot be null"));
            return this;
        }

        public Builder optionalOutput(MachineResourceStack stack) {
            optionalOutputs.add(Objects.requireNonNull(stack, "Optional output cannot be null"));
            return this;
        }

        public Builder inputItem(String portId, ItemStack stack) {
            return input(MachineResourceStack.item(portId, stack));
        }

        public Builder inputFluid(String portId, FluidStack stack) {
            return input(MachineResourceStack.fluid(portId, stack));
        }

        public Builder inputGas(String portId, GasStack stack) {
            return input(MachineResourceStack.gas(portId, stack));
        }

        public Builder outputItem(String portId, ItemStack stack) {
            return output(MachineResourceStack.item(portId, stack));
        }

        public Builder outputFluid(String portId, FluidStack stack) {
            return output(MachineResourceStack.fluid(portId, stack));
        }

        public Builder outputGas(String portId, GasStack stack) {
            return output(MachineResourceStack.gas(portId, stack));
        }

        public Builder optionalOutputItem(String portId, ItemStack stack) {
            return optionalOutput(MachineResourceStack.item(portId, stack));
        }

        public Builder optionalOutputFluid(String portId, FluidStack stack) {
            return optionalOutput(MachineResourceStack.fluid(portId, stack));
        }

        public Builder optionalOutputGas(String portId, GasStack stack) {
            return optionalOutput(MachineResourceStack.gas(portId, stack));
        }

        public MachineRecipeRoute build() {
            return new MachineRecipeRoute(this);
        }
    }
}
