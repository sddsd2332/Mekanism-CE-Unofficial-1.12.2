package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Immutable request passed to a QIO planning worker.
 */

/**
 * QIO 处理模块中的 QIOPlanningRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanningRequest {

    private final UUID planId;
    private final int planRevision;
    private final QIOPlanningSnapshot snapshot;
    private final PortableResourceDescriptor rootResource;
    private final long rootAmount;
    private final Map<RecipeItemKey, Long> modernTargets;
    private final Map<RecipeItemKey, Long> modernInventory;
    private final int maximumDepth, maximumNodes, maximumOperations;
    private final BooleanSupplier cancellation;

    /** 创建一次带快照和根产物需求的规划请求。 */
    public QIOPlanningRequest(@Nonnull UUID planId, int planRevision,
                              @Nonnull QIOPlanningSnapshot snapshot,
                              @Nonnull PortableResourceDescriptor rootResource, long rootAmount) {
        this.planId = Objects.requireNonNull(planId, "planId");
        this.planRevision = QIOProcessingNbt.requirePositive(planRevision, "planRevision");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.rootResource = Objects.requireNonNull(rootResource, "rootResource");
        if (rootAmount <= 0) {
            throw new IllegalArgumentException("rootAmount must be positive");
        }
        this.rootAmount = rootAmount;
        modernTargets = Collections.emptyMap();
        modernInventory = Collections.emptyMap();
        maximumDepth = maximumNodes = maximumOperations = 0;
        cancellation = () -> false;
    }

    public QIOPlanningRequest(RecipeItemKey target, long amount, Map<RecipeItemKey, Long> inventory,
                              int maximumDepth, int maximumNodes, int maximumOperations, BooleanSupplier cancellation) {
        if (target == null || amount <= 0 || maximumDepth <= 0 || maximumNodes <= 0 || maximumOperations <= 0) {
            throw new IllegalArgumentException("Invalid modern planning request");
        }
        Map<RecipeItemKey, Long> targets = new LinkedHashMap<>();
        targets.put(target, amount);
        modernTargets = Collections.unmodifiableMap(targets);
        modernInventory = immutableModernInventory(inventory);
        this.maximumDepth = maximumDepth;
        this.maximumNodes = maximumNodes;
        this.maximumOperations = maximumOperations;
        this.cancellation = cancellation == null ? () -> false : cancellation;
        planId = UUID.randomUUID();
        planRevision = 1;
        snapshot = null;
        rootResource = null;
        rootAmount = 0;
    }

    public QIOPlanningRequest(Map<RecipeItemKey, Long> targets, Map<RecipeItemKey, Long> inventory) {
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("Missing modern targets");
        RecipeItemKey first = targets.keySet().iterator().next();
        this.modernTargets = immutableModernTargets(targets);
        this.modernInventory = immutableModernInventory(inventory);
        maximumDepth = 128;
        maximumNodes = 100_000;
        maximumOperations = 100_000;
        cancellation = () -> false;
        planId = UUID.randomUUID();
        planRevision = 1;
        snapshot = null;
        rootResource = null;
        rootAmount = 0;
    }

    public QIOPlanningRequest(RecipeItemKey target, long amount, Map<RecipeItemKey, Long> inventory) {
        this(target, amount, inventory, 128, 100_000, 100_000, null);
    }

    public QIOPlanningRequest(Map<RecipeItemKey, Long> targets, Map<RecipeItemKey, Long> inventory,
                              int maximumDepth, int maximumNodes, int maximumOperations, BooleanSupplier cancellation) {
        if (targets == null || targets.isEmpty() || maximumDepth <= 0 || maximumNodes <= 0 || maximumOperations <= 0) {
            throw new IllegalArgumentException("Invalid modern planning request");
        }
        modernTargets = immutableModernTargets(targets);
        modernInventory = immutableModernInventory(inventory);
        this.maximumDepth = maximumDepth;
        this.maximumNodes = maximumNodes;
        this.maximumOperations = maximumOperations;
        this.cancellation = cancellation == null ? () -> false : cancellation;
        planId = UUID.randomUUID();
        planRevision = 1;
        snapshot = null;
        rootResource = null;
        rootAmount = 0;
    }

    private static Map<RecipeItemKey, Long> immutableModernTargets(Map<RecipeItemKey, Long> values) {
        Map<RecipeItemKey, Long> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || value == null || value <= 0) throw new IllegalArgumentException("Invalid target");
            result.put(key, value);
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<RecipeItemKey, Long> immutableModernInventory(Map<RecipeItemKey, Long> values) {
        Map<RecipeItemKey, Long> result = new LinkedHashMap<>();
        if (values != null) values.forEach((key, value) -> {
            if (key != null && value != null && value > 0) result.put(key, value);
        });
        return Collections.unmodifiableMap(result);
    }

    @Nonnull
    /** 返回规划请求标识。 */
    public UUID getPlanId() {
        return planId;
    }

    /** 返回计划版本。 */
    public int getPlanRevision() {
        return planRevision;
    }

    @Nonnull
    /** 返回规划使用的只读网络快照。 */
    public QIOPlanningSnapshot getSnapshot() {
        return snapshot;
    }

    @Nonnull
    /** 返回根产物资源身份。 */
    public PortableResourceDescriptor getRootResource() {
        return rootResource;
    }

    /** 返回根产物需求数量。 */
    public long getRootAmount() {
        return rootAmount;
    }

    public RecipeItemKey target() {
        return modernTargets.keySet().iterator().next();
    }

    public long amount() {
        return modernTargets.get(target());
    }

    public Map<RecipeItemKey, Long> targets() {
        return modernTargets;
    }

    public Map<RecipeItemKey, Long> inventory() {
        return modernInventory;
    }

    public int maximumDepth() {
        return maximumDepth;
    }

    public int maximumNodes() {
        return maximumNodes;
    }

    public int maximumOperations() {
        return maximumOperations;
    }

    public boolean cancelled() {
        return cancellation.getAsBoolean();
    }
}
