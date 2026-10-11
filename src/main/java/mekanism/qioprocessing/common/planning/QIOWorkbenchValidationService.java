package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Stack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Bounded server-thread boundary for concrete nine-slot recipes. Requests/results contain only
 * detached stack state; background jobs yield while awaiting a result and never block a worker
 * in Future.get(). No result is cached across worlds or recipe generations here.
 */
public final class QIOWorkbenchValidationService {
    public static final QIOWorkbenchValidationService INSTANCE = new QIOWorkbenchValidationService();

    public enum Status {
        VALID, WORLD_UNAVAILABLE, RECIPE_MISSING, SIGNATURE_MISMATCH, INPUT_MISMATCH,
        EMPTY_RESULT, DYNAMIC_RECIPE, CALLBACK_FAILED, OUTPUT_MISMATCH, INVALID_REMAINDERS,
        STALE_GENERATION, CANCELLED, MEMORY_LIMIT, UNSUPPORTED_STATE
    }

    public enum Admission {ACCEPTED, BUSY, STOPPED}

    /**
     * Immutable full result, including the physical remainder of each of the nine slots.
     */
    public static final class Result {
        private final Status status;
        private final String diagnostic;
        @Nullable
        private final QIOWorkbenchConfiguration.EncodedPattern pattern;
        private final List<Stack> inputs;
        private final Stack output;
        private final List<Stack> remainders;
        private final Map<PortableResourceDescriptor, Long> outputs;
        private final Map<PortableResourceDescriptor, Long> consumedInputs;
        @Nullable
        private final Request request;
        private final long retainedBytes;
        private final long queueNanos, callNanos, cpuNanos;

        private Result(Status status, String diagnostic,
                       @Nullable QIOWorkbenchConfiguration.EncodedPattern pattern, List<Stack> inputs,
                       Stack output, List<Stack> remainders, boolean[] virtualSlots, @Nullable Request request, Map<PortableResourceDescriptor, Long> consumedInputs) {
            this.request = request;
            queueNanos = 0;
            callNanos = 0;
            cpuNanos = -1;
            this.consumedInputs = Collections.unmodifiableMap(new LinkedHashMap<>(consumedInputs));
            this.status = status;
            this.diagnostic = diagnostic;
            this.pattern = pattern;
            this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
            this.output = output;
            this.remainders = Collections.unmodifiableList(new ArrayList<>(remainders));
            Map<PortableResourceDescriptor, Long> amounts = new LinkedHashMap<>();
            add(amounts, output);
            for (int slot = 0; slot < remainders.size(); slot++) {
                if (!virtualSlots[slot]) add(amounts, remainders.get(slot));
            }
            outputs = Collections.unmodifiableMap(amounts);
            retainedBytes = 384 + QIOWorkbenchMemory.text(diagnostic) + QIOWorkbenchMemory.stacks(inputs) +
                    output.getRetainedBytes() + QIOWorkbenchMemory.stacks(remainders) +
                    QIOWorkbenchMemory.resources(outputs) + QIOWorkbenchMemory.resources(this.consumedInputs) +
                    (request == null ? 0 : request.retainedBytes);
            if (retainedBytes > QIOWorkbenchMemory.RESULT_LIMIT) {
                throw new QIOWorkbenchMemory.LimitException("WORKBENCH_RESULT_MEMORY_LIMIT");
            }
        }

        public Status getStatus() {
            return status;
        }

        public long getRetainedBytes() {
            return retainedBytes;
        }

        public long getQueueNanos() {
            return queueNanos;
        }

        public long getCallNanos() {
            return callNanos;
        }

        /**
         * -1 means unavailable, never substituted with wall time.
         */
        public long getCpuNanos() {
            return cpuNanos;
        }

        private Result(Result source, long queueNanos, long callNanos, long cpuNanos) {
            status = source.status;
            diagnostic = source.diagnostic;
            pattern = source.pattern;
            inputs = source.inputs;
            output = source.output;
            remainders = source.remainders;
            outputs = source.outputs;
            consumedInputs = source.consumedInputs;
            request = source.request;
            retainedBytes = source.retainedBytes;
            this.queueNanos = queueNanos;
            this.callNanos = callNanos;
            this.cpuNanos = cpuNanos;
        }

        @Nullable
        public QIOWorkbenchConfiguration.EncodedPattern getPattern() {
            return pattern;
        }

        public Map<PortableResourceDescriptor, Long> getConsumedInputs() {
            return consumedInputs;
        }

        public Map<PortableResourceDescriptor, Long> getOutputs() {
            return outputs;
        }

        public List<Stack> getInputs() {
            return inputs;
        }

        public Stack getOutput() {
            return output;
        }

        public List<Stack> getRemainders() {
            return remainders;
        }

        public String getDiagnostic() {
            return diagnostic;
        }

        public boolean isValid() {
            return status == Status.VALID;
        }

        @Nullable
        public ResourceLocation getRecipeId() {
            return request == null ? null : request.recipe;
        }

        public long getDirectoryEpoch() {
            return request == null ? -1 : request.directoryEpoch;
        }

        public boolean[] getVirtualSlots() {
            return request == null ? new boolean[9] : request.virtualSlots.clone();
        }
    }

    public static final class Request {
        private final UUID ownerJob;
        @Nullable
        private final ResourceLocation recipe;
        private final int dimension;
        private final long directoryEpoch;
        private final List<Stack> grid;
        private final boolean[] virtualSlots;
        @Nullable
        private final PortableResourceDescriptor expectedOutput;
        private final long expectedAmount;
        private final boolean legacyOutputIdentity;
        private final long retainedBytes;

        public Request(UUID ownerJob, ResourceLocation recipe, int dimension, long directoryEpoch,
                       List<Stack> grid, boolean[] virtualSlots,
                       @Nullable PortableResourceDescriptor expectedOutput, long expectedAmount) {
            this(ownerJob, recipe, dimension, directoryEpoch, grid, virtualSlots, expectedOutput, expectedAmount, false);
        }

        private Request(UUID ownerJob, ResourceLocation recipe, int dimension, long directoryEpoch,
                        List<Stack> grid, boolean[] virtualSlots,
                        @Nullable PortableResourceDescriptor expectedOutput, long expectedAmount, boolean legacyOutputIdentity) {
            if (grid.size() != 9 || virtualSlots.length != 9 ||
                    (expectedOutput != null && expectedAmount <= 0)) {
                throw new IllegalArgumentException("Invalid concrete workbench request");
            }
            this.ownerJob = Objects.requireNonNull(ownerJob, "ownerJob");
            this.recipe = recipe;
            this.dimension = dimension;
            this.directoryEpoch = directoryEpoch;
            List<Stack> copy = new ArrayList<>(grid.size());
            for (Stack stack : grid) copy.add(Objects.requireNonNull(stack, "grid stack"));
            this.grid = Collections.unmodifiableList(copy);
            this.virtualSlots = virtualSlots.clone();
            this.expectedOutput = expectedOutput;
            this.expectedAmount = expectedAmount;
            this.legacyOutputIdentity = legacyOutputIdentity;
            retainedBytes = 256 + QIOWorkbenchMemory.stacks(grid) + QIOWorkbenchMemory.resource(expectedOutput);
        }

        public UUID getOwnerJob() {
            return ownerJob;
        }

        /**
         * Manual ghost-grid lookup uses the real server World in the same bounded queue.
         */
        public static Request manual(UUID ownerJob, int dimension, long directoryEpoch, List<Stack> grid) {
            return new Request(ownerJob, null, dimension, directoryEpoch, grid, new boolean[9], null, 0);
        }

        private Request resolved(ResourceLocation id) {
            return new Request(ownerJob, id, dimension, directoryEpoch, grid, virtualSlots,
                    expectedOutput, expectedAmount, legacyOutputIdentity);
        }
    }

    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private long serverEpoch;
    private long progressRevision;
    private boolean running;
    private long queuedBytes;
    private static final long QUEUE_BYTES = 64 * QIOWorkbenchMemory.MIB;

    private QIOWorkbenchValidationService() {
    }

    public synchronized long getServerEpoch() {
        return serverEpoch;
    }

    public synchronized long getProgressRevision() {
        return progressRevision;
    }

    public synchronized boolean isCurrentServer(long expected) {
        return running && serverEpoch == expected;
    }

    public synchronized void start() {
        queue.clear();
        queuedBytes = 0;
        serverEpoch++;
        running = true;
    }

    public synchronized void stop() {
        running = false;
        serverEpoch++;
        queue.clear();
        queuedBytes = 0;
    }

    /**
     * Submission is nonblocking and invokes no World or registry callbacks.
     */
    public synchronized Admission submit(Request request, Consumer<Result> completion) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(completion, "completion");
        if (!running) return Admission.STOPPED;
        // Oversized requests enter as small rejection records. BUSY would retry them forever.
        long weight = Math.min(request.retainedBytes, QIOWorkbenchMemory.REQUEST_LIMIT);
        if (queue.size() >= MekanismConfig.local().qioProcessing.workbenchValidationQueueCapacity.val() ||
                weight > QUEUE_BYTES - queuedBytes) {
            return Admission.BUSY;
        }
        queue.addLast(new Pending(serverEpoch, request, completion));
        queuedBytes += weight;
        return Admission.ACCEPTED;
    }

    /**
     * Cancellation discards pending continuations; their owning job records CANCELLED itself.
     */
    public synchronized void cancel(UUID ownerJob) {
        queue.removeIf(pending -> {
            if (!pending.ownerJob.equals(ownerJob)) return false;
            queuedBytes -= pending.weight;
            return true;
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long started = System.nanoTime();
        long budget = MekanismConfig.local().qioProcessing.workbenchValidationTimePerTick.val() * 1_000_000L;
        int maximum = MekanismConfig.local().qioProcessing.workbenchValidationsPerTick.val();
        for (int count = 0; count < maximum && System.nanoTime() - started < budget; count++) {
            Pending pending;
            synchronized (this) {
                if (!running || (pending = queue.pollFirst()) == null) return;
                queuedBytes -= pending.weight;
            }
            Request request = pending.request;
            long queueNanos = Math.max(0, System.nanoTime() - pending.queuedAt);
            Result result;
            if (request == null) {
                result = invalid(Status.MEMORY_LIMIT, "WORKBENCH_REQUEST_MEMORY_LIMIT");
            } else if (request.directoryEpoch != QIOWorkbenchDirectoryService.INSTANCE.getEpoch()) {
                result = invalid(Status.STALE_GENERATION, "Workbench declaration generation changed");
            } else {
                World world = DimensionManager.getWorld(request.dimension);
                result = world == null ? invalid(Status.WORLD_UNAVAILABLE, "World is not loaded") :
                        validate(world, request);
            }
            result = new Result(result, queueNanos, result.callNanos, result.cpuNanos);
            synchronized (this) {
                if (!running || pending.serverEpoch != serverEpoch) continue;
                progressRevision++;
            }
            try {
                pending.completion.accept(result);
            } catch (RuntimeException error) {
                Mekanism.logger.error("Workbench validation continuation failed", error);
            }
        }
    }

    /**
     * Compatibility entry for old persisted patterns during migration. No full-catalog scan.
     */
    public static Result validate(World world, QIOWorkbenchConfiguration.EncodedPattern pattern) {
        QIOWorkbenchDirectoryService.requireServerThread(world);
        Request request = request(pattern.getPatternUUID(), world.provider.getDimension(),
                QIOWorkbenchDirectoryService.INSTANCE.getEpoch(), pattern);
        Result result = validate(world, request);
        return result.isValid() ? new Result(Status.VALID, "", pattern, result.inputs,
                result.output, result.remainders, request.virtualSlots, request, result.consumedInputs) : result;
    }

    public static Request request(UUID owner, int dimension, long epoch, QIOWorkbenchConfiguration.EncodedPattern pattern) {
        return new Request(owner, pattern.getRecipeId(), dimension, epoch, pattern.getFrozenGrid(),
                pattern.getVerified() == null ? new boolean[9] : pattern.getVerified().getVirtualSlots(),
                pattern.getOutput(), pattern.getOutputAmount(), pattern.getVerified() == null);
    }

    /**
     * All real recipe calls, including capability reconstruction, occur on the server thread.
     */
    public static Result validate(World world, Request request) {
        long started = System.nanoTime(), cpuStarted = QIOPlanningMetrics.cpuNow();
        Result result = validateNow(world, request);
        long cpuEnded = QIOPlanningMetrics.cpuNow();
        return new Result(result, 0, Math.max(0, System.nanoTime() - started),
                cpuStarted < 0 || cpuEnded < cpuStarted ? -1 : cpuEnded - cpuStarted);
    }

    /**
     * Production performs the same checks without planning-only timing probes per operation.
     */
    public static Result validateProduction(World world, Request request) {
        return validateNow(world, request);
    }

    private static Result validateNow(World world, Request request) {
        QIOWorkbenchDirectoryService.requireServerThread(world);
        Objects.requireNonNull(request, "request");
        if (request.retainedBytes > QIOWorkbenchMemory.REQUEST_LIMIT) {
            return invalid(Status.MEMORY_LIMIT, "WORKBENCH_REQUEST_MEMORY_LIMIT");
        }
        if (request.directoryEpoch != QIOWorkbenchDirectoryService.INSTANCE.getEpoch()) {
            return invalid(Status.STALE_GENERATION, "Workbench declaration generation changed");
        }
        try {
            InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
            for (int slot = 0; slot < 9; slot++) {
                Stack input = request.grid.get(slot);
                ItemStack thawed = input.thaw();
                // Registry remapping may make a saved identity unavailable. Do not let an empty
                // reconstruction accidentally satisfy a different recipe.
                if (!input.isEmpty() && (thawed.isEmpty() ||
                        !PortableResourceDescriptor.item(thawed).equals(input.getResource()))) {
                    return invalid(Status.INPUT_MISMATCH, "Input identity unavailable at slot " + slot);
                }
                // IC2 crafting tools call attemptDamageItem with IC2.random. Unbreaking makes
                // the returned state probabilistic, so one sampled result cannot be a plan.
                if (!thawed.isEmpty() && isIC2CraftingTool(thawed) &&
                        net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                                net.minecraft.init.Enchantments.UNBREAKING, thawed) > 0) {
                    return invalid(Status.UNSUPPORTED_STATE,
                            "NONDETERMINISTIC_TOOL_REMAINDER: Unbreaking IC2 tool at slot " + slot);
                }
                inventory.setInventorySlotContents(slot, thawed);
            }
            boolean manual = request.recipe == null;
            IRecipe recipe = manual ? net.minecraft.item.crafting.CraftingManager.findMatchingRecipe(inventory, world) :
                    net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(request.recipe);
            if (recipe == null || recipe.getRegistryName() == null)
                return invalid(Status.RECIPE_MISSING, String.valueOf(request.recipe));
            if (manual) request = request.resolved(recipe.getRegistryName());
            if (recipe.isDynamic()) return invalid(Status.DYNAMIC_RECIPE, request.recipe.toString());
            if (!manual && !recipe.matches(inventory, world))
                return invalid(Status.INPUT_MISMATCH, request.recipe.toString());
            ItemStack actual = recipe.getCraftingResult(inventory);
            if (actual == null || actual.isEmpty()) return invalid(Status.EMPTY_RESULT, request.recipe.toString());
            if (PLAYER_CRAFTING_OUTPUTS.get(actual.getItem().getClass())) {
                return invalid(Status.UNSUPPORTED_STATE,
                        "PLAYER_CRAFTING_CALLBACK_REQUIRES_ADAPTER: " + request.recipe);
            }
            // Capture before invoking the next third-party callback; never retain its mutable stack.
            Stack output = Stack.capture(actual);
            if (request.expectedOutput != null &&
                    (!(request.legacyOutputIdentity ? request.expectedOutput.withoutCapabilities().equals(output.getResource().withoutCapabilities()) :
                            request.expectedOutput.equals(output.getResource())) ||
                            request.expectedAmount != output.getAmount())) {
                return invalid(Status.OUTPUT_MISMATCH, request.recipe.toString());
            }
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(inventory);
            if (remaining == null || remaining.size() != 9) {
                return invalid(Status.INVALID_REMAINDERS, request.recipe.toString());
            }
            List<Stack> remainders = new ArrayList<>(9);
            for (ItemStack remainder : remaining) remainders.add(Stack.capture(remainder));
            Map<PortableResourceDescriptor, Long> consumedInputs = new LinkedHashMap<>();
            for (int slot = 0; slot < 9; slot++) {
                if (!request.virtualSlots[slot]) {
                    add(consumedInputs, request.grid.get(slot));
                    continue;
                }
                ItemStack physical = request.grid.get(slot).thaw();
                net.minecraftforge.fluids.FluidStack fluid = QIOFluidContainerSnapshot.capture(physical);
                net.minecraftforge.fluids.capability.IFluidHandlerItem handler =
                        net.minecraftforge.fluids.FluidUtil.getFluidHandler(physical);
                if (fluid == null || handler == null)
                    return invalid(Status.INPUT_MISMATCH, "Virtual fluid container unavailable at slot " + slot);
                net.minecraftforge.fluids.FluidStack drained = handler.drain(fluid.copy(), true);
                if (drained == null || drained.amount != fluid.amount || !drained.isFluidEqual(fluid)) {
                    return invalid(Status.INPUT_MISMATCH, "Virtual fluid drain changed at slot " + slot);
                }
                consumedInputs.merge(PortableResourceDescriptor.fluid(drained), (long) drained.amount, Math::addExact);
                Stack remainder = remainders.get(slot);
                // Suppression is valid only for a consumed container or its exact emptied form.
                // A transformed tool/container or leftover fluid is a real output, not discardable.
                if (!remainder.isEmpty() && !remainder.getKey().equals(Stack.capture(handler.getContainer()).getKey())) {
                    return invalid(Status.INVALID_REMAINDERS, "Virtual fluid has a non-container remainder at slot " + slot);
                }
            }
            return new Result(Status.VALID, "", null, request.grid, output, remainders, request.virtualSlots, request, consumedInputs);
        } catch (QIOWorkbenchMemory.LimitException error) {
            return invalid(Status.MEMORY_LIMIT, error.getMessage());
        } catch (RuntimeException | LinkageError error) {
            return invalid(Status.CALLBACK_FAILED, request.recipe + ": " + error.getClass().getSimpleName());
        }
    }

    private static void add(Map<PortableResourceDescriptor, Long> amounts, Stack stack) {
        if (!stack.isEmpty()) amounts.merge(stack.getResource(), (long) stack.getAmount(), Math::addExact);
    }

    private static boolean isIC2CraftingTool(ItemStack stack) {
        return IC2_CRAFTING_TOOLS.get(stack.getItem().getClass());
    }

    private static final ClassValue<Boolean> IC2_CRAFTING_TOOLS = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (; type != null; type = type.getSuperclass()) {
                if (type.getName().equals("ic2.core.item.tool.ItemToolCrafting")) return true;
            }
            return false;
        }
    };

    // onCreated may alter output state and/or the world. Do not invoke it during speculative
    // validation or silently pretend its IRecipe output is the complete player-crafted result.
    private static final ClassValue<Boolean> PLAYER_CRAFTING_OUTPUTS = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (String name : new String[]{"onCreated", "func_77622_d"}) {
                try {
                    return type.getMethod(name, ItemStack.class, World.class,
                            net.minecraft.entity.player.EntityPlayer.class).getDeclaringClass() != net.minecraft.item.Item.class;
                } catch (NoSuchMethodException ignored) {
                    // Try the runtime SRG name after the development name.
                }
            }
            return true; // Unknown mappings cannot certify a callback-free output.
        }
    };

    private static Result invalid(Status status, String message) {
        return new Result(status, message, null, Collections.emptyList(), Stack.EMPTY,
                Collections.emptyList(), new boolean[9], null, Collections.emptyMap());
    }

    private static final class Pending {
        private final long queuedAt = System.nanoTime();
        private final long serverEpoch;
        @Nullable
        private final Request request;
        private final UUID ownerJob;
        private final long weight;
        private final Consumer<Result> completion;

        private Pending(long serverEpoch, Request request, Consumer<Result> completion) {
            this.serverEpoch = serverEpoch;
            this.ownerJob = request.ownerJob;
            this.weight = Math.min(request.retainedBytes, QIOWorkbenchMemory.REQUEST_LIMIT);
            this.request = request.retainedBytes > QIOWorkbenchMemory.REQUEST_LIMIT ? null : request;
            this.completion = completion;
        }
    }
}
