package mekanism.api.processing;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceDescriptor;

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

    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    });

    private final String routeId;
    private final String recipeKey;
    private final String logicalRecipeKey;
    private final List<MachineResourceStack> configurationInputs;
    private final List<MachineResourceStack> inputs;
    private final List<MachineResourceStack> guaranteedOutputs;
    private final List<MachineResourceStack> optionalOutputs;

    private MachineRecipeRoute(Builder builder) {
        routeId = builder.routeId;
        configurationInputs = orderedCopy(builder.configurationInputs);
        inputs = orderedCopy(builder.inputs);
        guaranteedOutputs = orderedCopy(builder.guaranteedOutputs);
        optionalOutputs = orderedCopy(builder.optionalOutputs);
        if (inputs.isEmpty() || guaranteedOutputs.isEmpty()) {
            throw new IllegalStateException("Machine recipe routes require at least one input and guaranteed output");
        }
        recipeKey = builder.recipeKey == null || builder.recipeKey.isEmpty() ? createRecipeKey() : builder.recipeKey;
        logicalRecipeKey = builder.logicalRecipeKey == null || builder.logicalRecipeKey.isEmpty() ?
              recipeKey : builder.logicalRecipeKey;
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

    /** Stable recipe identity shared by all physical factory-lane expansions. */
    public String logicalRecipeKey() {
        return logicalRecipeKey;
    }

    public List<MachineResourceStack> inputs() {
        return inputs;
    }

    /**
     * Retained machine configuration required by this route. These resources are installed once,
     * are not consumed by an operation, and therefore are never scaled by operation count.
     */
    public List<MachineResourceStack> configurationInputs() {
        return configurationInputs;
    }

    /** Alias for integrations that describe persistent recipe inputs as retained inputs. */
    public List<MachineResourceStack> retainedInputs() {
        return configurationInputs;
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
        MessageDigest digest = SHA_256.get();
        digest.reset();
        update(digest, routeId);
        update(digest, "configuration");
        configurationInputs.forEach(stack -> update(digest, stack));
        update(digest, "inputs");
        inputs.forEach(stack -> update(digest, stack));
        update(digest, "guaranteed");
        guaranteedOutputs.forEach(stack -> update(digest, stack));
        update(digest, "optional");
        optionalOutputs.forEach(stack -> update(digest, stack));
        byte[] bytes = digest.digest();
        StringBuilder key = new StringBuilder(routeId).append(':');
        for (byte value : bytes) {
            int current = value & 0xFF;
            key.append(LOWER_HEX[current >>> 4]).append(LOWER_HEX[current & 0x0F]);
        }
        return key.toString();
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
        private final List<MachineResourceStack> configurationInputs = new ArrayList<>();
        private final List<MachineResourceStack> inputs = new ArrayList<>();
        private final List<MachineResourceStack> guaranteedOutputs = new ArrayList<>();
        private final List<MachineResourceStack> optionalOutputs = new ArrayList<>();
        private String recipeKey;
        private String logicalRecipeKey;

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

        public Builder logicalRecipeKey(@Nonnull String logicalRecipeKey) {
            this.logicalRecipeKey = Objects.requireNonNull(logicalRecipeKey,
                  "Logical recipe key cannot be null");
            return this;
        }

        public Builder input(MachineResourceStack stack) {
            inputs.add(Objects.requireNonNull(stack, "Input cannot be null"));
            return this;
        }

        public Builder inputResource(String portId, QIOResourceDescriptor descriptor, long amount) {
            return input(MachineResourceStack.resource(portId, descriptor, amount));
        }

        public Builder configurationInput(MachineResourceStack stack) {
            configurationInputs.add(Objects.requireNonNull(stack,
                  "Configuration input cannot be null"));
            return this;
        }

        public Builder configurationResource(String portId, QIOResourceDescriptor descriptor) {
            return configurationInput(MachineResourceStack.resource(portId, descriptor, 1));
        }

        public Builder retainedInput(MachineResourceStack stack) {
            return configurationInput(stack);
        }

        public Builder output(MachineResourceStack stack) {
            guaranteedOutputs.add(Objects.requireNonNull(stack, "Output cannot be null"));
            return this;
        }

        public Builder outputResource(String portId, QIOResourceDescriptor descriptor, long amount) {
            return output(MachineResourceStack.resource(portId, descriptor, amount));
        }

        public Builder optionalOutput(MachineResourceStack stack) {
            optionalOutputs.add(Objects.requireNonNull(stack, "Optional output cannot be null"));
            return this;
        }

        public Builder optionalOutputResource(String portId, QIOResourceDescriptor descriptor, long amount) {
            return optionalOutput(MachineResourceStack.resource(portId, descriptor, amount));
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

        public Builder configurationItem(String portId, ItemStack stack) {
            return configurationInput(MachineResourceStack.item(portId, stack, 1));
        }

        public Builder configurationFluid(String portId, FluidStack stack) {
            return configurationInput(MachineResourceStack.fluid(portId, stack, 1));
        }

        public Builder configurationGas(String portId, GasStack stack) {
            return configurationInput(MachineResourceStack.gas(portId, stack, 1));
        }

        public Builder retainedItem(String portId, ItemStack stack) {
            return configurationItem(portId, stack);
        }

        public Builder retainedFluid(String portId, FluidStack stack) {
            return configurationFluid(portId, stack);
        }

        public Builder retainedGas(String portId, GasStack stack) {
            return configurationGas(portId, stack);
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
