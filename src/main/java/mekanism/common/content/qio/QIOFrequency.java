package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOResourceClaim;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.QIOStorageChange;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.api.qio.external.QIOTransferResult;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IColorableFrequency;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.network.qio.PacketQIOViewerData;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.StackUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.Collection;
import java.util.Objects;

/**
 * Unified QIO resource frequency. Drive data remains in QIODriveStorage; this
 * class only owns the live aggregate view and transaction routing.
 */
public class QIOFrequency extends Frequency implements IColorableFrequency {

    private static final String FREQUENCY_UUID_KEY = "qioFrequencyUUID";
    private static final String CONTENTS_REVISION_KEY = "qioContentsRevision";
    private static final String CAPACITY_REVISION_KEY = "qioCapacityRevision";
    private static final String ACCESS_REVISION_KEY = "qioAccessRevision";

    private final Set<IQIODriveHolder> driveHolders = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<QIODriveMount, QIODriveData> activeDrives = new LinkedHashMap<>();
    private final Map<QIODriveMount, QIODriveSlotState> slotStates = new LinkedHashMap<>();
    private final Map<UUID, QIOAmount> resourceDataMap = new LinkedHashMap<>();
    private final Map<UUID, QIOResourceKind> resourceKinds = new HashMap<>();
    private final Set<QIODriveMount> ownedMounts = new HashSet<>();
    private final Set<UUID> missingResources = new java.util.HashSet<>();
    private final Map<QIOResourceKind, QIOAmount> kindCounts = new EnumMap<>(QIOResourceKind.class);
    private final Set<UUID> updatedResources = new HashSet<>();
    private final Set<EntityPlayerMP> viewers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<IQIOStorageListener, StorageListenerRegistration> storageListeners = new IdentityHashMap<>();
    private final Map<UUID, PendingStorageChange> pendingStorageChanges = new LinkedHashMap<>();
    private final QIOClaimLedger claimLedger = new QIOClaimLedger();
    private final QIOTransferLedger transferLedger = new QIOTransferLedger();
    private Map<UUID, Map<UUID, Long>> claimBackingCache = Collections.emptyMap();
    private boolean claimBackingCacheValid;
    private UUID frequencyUUID = UUID.randomUUID();
    private long contentsRevision;
    private long capacityRevision;
    private long accessRevision;
    private long pendingOldContentsRevision = -1;
    private long pendingOldCapacityRevision = -1;
    private long pendingOldClaimRevision = -1;
    private boolean pendingFullRescan;
    private long totalCount;
    private long totalStorageUnits;
    private long totalCountCapacity;
    private int totalTypes;
    private int totalTypeCapacity;
    private QIOAmount exactTotalCount = QIOAmount.ZERO;
    private QIOAmount exactTotalStorageUnits = QIOAmount.ZERO;
    private QIOAmount finiteCountCapacity = QIOAmount.ZERO;
    private QIOAmount finiteTypeCapacity = QIOAmount.ZERO;
    private QIOCapacitySummary capacitySummary = QIOCapacitySummary.EMPTY;
    private int unlimitedCountDrives;
    private int unlimitedTypeDrives;
    private EnumColor color = EnumColor.INDIGO;
    private boolean needsRefresh = true;
    private boolean clientSnapshot;
    private long observedMountRevision = -1;
    private boolean viewerCapacityDirty;
    private boolean applyingFrequencyTransaction;

    public QIOFrequency(String name, @Nullable UUID ownerUUID, SecurityMode securityMode) {
        super(FrequencyType.QIO, name, ownerUUID, securityMode);
    }

    public QIOFrequency(NBTTagCompound data) {
        super(FrequencyType.QIO, data);
        readQIOData(data);
    }

    public QIOFrequency(ByteBuf data) {
        super(FrequencyType.QIO, data);
        readQIOData(data);
    }

    public QIOFrequency() {
        this("", null, SecurityMode.PUBLIC);
    }

    public synchronized void addHolder(IQIODriveHolder holder) {
        if (holder != null && driveHolders.add(holder)) {
            needsRefresh = true;
        }
    }

    public synchronized void removeHolder(IQIODriveHolder holder) {
        if (holder != null && driveHolders.remove(holder)) {
            unmountOwnedBy(holder);
            needsRefresh = true;
        }
    }

    @Override
    public synchronized boolean update(Object source) {
        if (source instanceof IQIODriveHolder) {
            addHolder((IQIODriveHolder) source);
        }
        return false;
    }

    @Override
    public synchronized boolean onDeactivate(Object source) {
        if (source instanceof IQIODriveHolder) {
            removeHolder((IQIODriveHolder) source);
        }
        return false;
    }

    @Override
    public synchronized void onRemove() {
        for (EntityPlayerMP viewer : new ArrayList<>(viewers)) {
            if (viewer != null && viewer.openContainer instanceof QIOItemViewerContainer
                  && ((QIOItemViewerContainer) viewer.openContainer).getFrequency() == this) {
                PacketQIOViewerData.sendKill(viewer, viewer.openContainer.windowId);
            }
        }
        viewers.clear();
        for (QIODriveMount mount : new ArrayList<>(ownedMounts)) {
            QIODriveStorage.INSTANCE.unmount(mount);
        }
        ownedMounts.clear();
        activeDrives.clear();
        driveHolders.clear();
        invalidateStorageListeners();
        super.onRemove();
    }

    @Override
    public synchronized boolean tick(boolean tickingNormally) {
        if (!clientSnapshot && observedMountRevision != QIODriveStorage.INSTANCE.getMountRevision()) {
            needsRefresh = true;
        }
        if (needsRefresh) {
            refresh();
        }
        sendViewerUpdates();
        dispatchStorageChanges();
        return dirty;
    }

    public synchronized void refresh() {
        if (clientSnapshot) {
            return;
        }
        Map<UUID, QIOAmount> previousResources = new HashMap<>(resourceDataMap);
        QIOCapacitySummary previousCapacity = capacitySummary;
        if (!QIOStorageManager.isLoaded()) {
            Set<UUID> staleResources = new HashSet<>(resourceDataMap.keySet());
            for (QIODriveMount mount : new ArrayList<>(ownedMounts)) {
                QIODriveStorage.INSTANCE.unmount(mount);
            }
            ownedMounts.clear();
            activeDrives.clear();
            slotStates.clear();
            resourceDataMap.clear();
            resourceKinds.clear();
            missingResources.clear();
            kindCounts.clear();
            totalCount = 0;
            totalStorageUnits = 0;
            totalCountCapacity = 0;
            totalTypes = 0;
            totalTypeCapacity = 0;
            exactTotalCount = QIOAmount.ZERO;
            exactTotalStorageUnits = QIOAmount.ZERO;
            finiteCountCapacity = QIOAmount.ZERO;
            finiteTypeCapacity = QIOAmount.ZERO;
            capacitySummary = QIOCapacitySummary.EMPTY;
            unlimitedCountDrives = 0;
            unlimitedTypeDrives = 0;
            updatedResources.addAll(staleResources);
            viewerCapacityDirty = true;
            needsRefresh = false;
            observedMountRevision = QIODriveStorage.INSTANCE.getMountRevision();
            recordRefreshChanges(previousResources, previousCapacity, true);
            return;
        }

        List<IQIODriveHolder> holders = new ArrayList<>(driveHolders);
        holders.sort(Comparator.comparingInt(IQIODriveHolder::getQIODimension)
              .thenComparingInt(holder -> holder.getQIOPosition().getX())
              .thenComparingInt(holder -> holder.getQIOPosition().getY())
              .thenComparingInt(holder -> holder.getQIOPosition().getZ()));
        Map<QIODriveMount, ItemStack> scannedDrives = new LinkedHashMap<>();
        for (IQIODriveHolder holder : holders) {
            List<ItemStack> stacks = holder.getQIODriveStacks();
            if (stacks == null) {
                continue;
            }
            for (int slot = 0; slot < stacks.size(); slot++) {
                scannedDrives.put(new QIODriveMount(holder, slot), stacks.get(slot));
            }
        }

        // Preserve unchanged mounts. Releasing every drive during every
        // aggregate pass causes independent frequencies to invalidate each
        // other forever through the global mount revision.
        for (Map.Entry<QIODriveMount, QIODriveData> previous : new ArrayList<>(activeDrives.entrySet())) {
            ItemStack current = scannedDrives.get(previous.getKey());
            if (!previous.getValue().isCompatibleWith(current)) {
                QIODriveStorage.INSTANCE.unmount(previous.getKey());
            }
        }
        ownedMounts.clear();
        activeDrives.clear();
        slotStates.clear();
        resourceDataMap.clear();
        resourceKinds.clear();
        missingResources.clear();
        kindCounts.clear();
        totalCount = 0;
        totalStorageUnits = 0;
        totalCountCapacity = 0;
        totalTypes = 0;
        totalTypeCapacity = 0;
        exactTotalCount = QIOAmount.ZERO;
        exactTotalStorageUnits = QIOAmount.ZERO;
        finiteCountCapacity = QIOAmount.ZERO;
        finiteTypeCapacity = QIOAmount.ZERO;
        unlimitedCountDrives = 0;
        unlimitedTypeDrives = 0;

        for (Map.Entry<QIODriveMount, ItemStack> candidate : scannedDrives.entrySet()) {
                QIODriveMount mount = candidate.getKey();
                IQIODriveHolder holder = mount.getHolder();
                int slot = mount.getSlot();
                ItemStack stack = candidate.getValue();
                if (stack == null || stack.isEmpty()) {
                    slotStates.put(mount, QIODriveSlotState.EMPTY);
                    holder.setQIODriveSlotState(slot, QIODriveSlotState.EMPTY);
                    continue;
                }
                QIODriveData.MountAttempt attempt = QIODriveData.tryMount(stack, mount);
                slotStates.put(mount, attempt.getState());
                holder.setQIODriveSlotState(slot, attempt.getState());
                if (attempt.getState() != QIODriveSlotState.ACTIVE || attempt.getData() == null) {
                    continue;
                }
                QIODriveData data = attempt.getData().withUpdateListener(() -> {
                    if (!applyingFrequencyTransaction) {
                        needsRefresh = true;
                    }
                });
                activeDrives.put(mount, data);
                ownedMounts.add(mount);
                addCapacity(data.getRecord());
                for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<UUID> entry : data.getRecord().getContents().object2LongEntrySet()) {
                    UUID resource = entry.getKey();
                    long amount = entry.getLongValue();
                    if (!resourceDataMap.containsKey(resource)) {
                        totalTypes = safeIntAdd(totalTypes, 1);
                    }
                    resourceDataMap.put(resource, resourceDataMap.getOrDefault(resource, QIOAmount.ZERO).add(amount));
                    QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
                    if (type == null) {
                        missingResources.add(resource);
                    } else {
                        resourceKinds.put(resource, type.getKind());
                        kindCounts.put(type.getKind(), kindCounts.getOrDefault(type.getKind(), QIOAmount.ZERO).add(amount));
                    }
                }
        }
        capacitySummary = new QIOCapacitySummary(finiteCountCapacity, finiteTypeCapacity,
              unlimitedCountDrives, unlimitedTypeDrives);
        totalCountCapacity = capacitySummary.getCountCapacityClamped();
        totalTypeCapacity = capacitySummary.getTypeCapacityClamped();
        totalStorageUnits = exactTotalStorageUnits.longValueClamped();
        exactTotalCount = exactTotalStorageUnits.divideRoundUp(QIOStorageUnits.UNITS_PER_ITEM);
        totalCount = exactTotalCount.longValueClamped();
        needsRefresh = false;
        observedMountRevision = QIODriveStorage.INSTANCE.getMountRevision();
        Set<UUID> changedResources = new HashSet<>(previousResources.keySet());
        changedResources.addAll(resourceDataMap.keySet());
        for (UUID resource : changedResources) {
            if (!Objects.equals(previousResources.get(resource), resourceDataMap.get(resource))) {
                updatedResources.add(resource);
            }
        }
        viewerCapacityDirty |= !previousCapacity.equals(capacitySummary);
        recordRefreshChanges(previousResources, previousCapacity, false);
    }

    /** Requests a remount/aggregate pass on the next frequency tick or query. */
    public synchronized void requestRefresh() {
        needsRefresh = true;
    }

    @Nonnull
    public synchronized UUID getFrequencyUUID() {
        return frequencyUUID;
    }

    public synchronized long getContentsRevision() {
        return contentsRevision;
    }

    public synchronized long getCapacityRevision() {
        return capacityRevision;
    }

    public synchronized long getClaimRevision() {
        return claimLedger.getRevision();
    }

    public synchronized long getAccessRevision() {
        return accessRevision;
    }

    @Nonnull
    public synchronized QIOStorageSnapshot getExternalStorageSnapshot() {
        ensureFresh();
        List<QIOStorageEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, QIOAmount> resource : resourceDataMap.entrySet()) {
            QIOStorageEntry entry = createExternalEntry(resource.getKey(), resource.getValue());
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.getResourceUUID().toString()));
        return new QIOStorageSnapshot(frequencyUUID, getName(), contentsRevision, capacityRevision,
              claimLedger.getRevision(), accessRevision, entries, finiteCountCapacity.toBigInteger(), finiteTypeCapacity.toBigInteger(),
              unlimitedCountDrives, unlimitedTypeDrives);
    }

    @Nullable
    public synchronized QIOStorageEntry getExternalStorageEntry(@Nullable UUID resource) {
        ensureFresh();
        if (resource == null) {
            return null;
        }
        return createExternalEntry(resource, resourceDataMap.getOrDefault(resource, QIOAmount.ZERO));
    }

    @Nullable
    public synchronized QIOResourceClaim getResourceClaim(@Nullable UUID claimId) {
        return claimLedger.getClaim(claimId);
    }

    @Nullable
    public synchronized QIOClaimBacking getResourceClaimBacking(@Nullable UUID claimId) {
        ensureFresh();
        QIOResourceClaim claim = claimLedger.getClaim(claimId);
        if (claim == null) {
            return null;
        }
        if (!claimBackingCacheValid) {
            claimBackingCache = claimLedger.calculateBackedAmounts(resource ->
                  resourceDataMap.getOrDefault(resource, QIOAmount.ZERO));
            claimBackingCacheValid = true;
        }
        return new QIOClaimBacking(claim, contentsRevision, claimLedger.getRevision(),
              claimBackingCache.getOrDefault(claim.getClaimId(), Collections.emptyMap()));
    }

    @Nonnull
    public synchronized List<QIOResourceClaim> getResourceClaims() {
        return claimLedger.getClaims();
    }

    @Nonnull
    public synchronized QIOClaimResult submitClaimRequest(@Nonnull QIOClaimRequest request) {
        ensureFresh();
        long oldClaimRevision = claimLedger.getRevision();
        QIOClaimLedger.Submission submission = claimLedger.submit(request, contentsRevision,
              new QIOClaimLedger.Storage() {
                  @Override
                  public boolean isClaimable(UUID resource) {
                      return resource != null && QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource) != null;
                  }

                  @Nonnull
                  @Override
                  public QIOAmount getStored(UUID resource) {
                      return resourceDataMap.getOrDefault(resource, QIOAmount.ZERO);
                  }

                  @Override
                  public boolean extractClaimed(Map<UUID, Long> resources) {
                      return extractClaimedBatch(resources);
                  }

                  @Override
                  public boolean persistPhysical() {
                      return QIOStorageManager.flush();
                  }
              });
        // Request receipts are persistent even when a request is stale or a replay makes no resource change.
        dirty = true;
        if (claimLedger.getRevision() != oldClaimRevision) {
            markClaimsChanged(oldClaimRevision, submission.getChangedResources());
        }
        return submission.getResult();
    }

    @Nonnull
    public synchronized QIOTransferResult insertExternalTransfer(@Nonnull UUID transferId,
          @Nullable ItemStack stack, long amount, @Nonnull BigInteger expectedStoredAmount) {
        ensureFresh();
        if (stack == null || stack.isEmpty() || amount <= 0 || expectedStoredAmount == null ||
              expectedStoredAmount.signum() < 0) {
            return invalidTransfer(transferId, amount);
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(HashedItem.create(normalized));
        return submitExternalTransfer(transferId, QIOResourceKind.ITEM, resource, amount,
              expectedStoredAmount, requested -> massInsert(normalized, requested, Action.EXECUTE));
    }

    @Nonnull
    public synchronized QIOTransferResult insertExternalTransfer(@Nonnull UUID transferId,
          @Nullable FluidStack stack, long amount, @Nonnull BigInteger expectedStoredAmount) {
        ensureFresh();
        if (stack == null || stack.getFluid() == null || amount <= 0 || expectedStoredAmount == null ||
              expectedStoredAmount.signum() < 0) {
            return invalidTransfer(transferId, amount);
        }
        FluidStack normalized = stack.copy();
        normalized.amount = 1;
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(normalized);
        return submitExternalTransfer(transferId, QIOResourceKind.FLUID, resource, amount,
              expectedStoredAmount, requested -> massInsert(normalized, requested, Action.EXECUTE));
    }

    @Nonnull
    public synchronized QIOTransferResult insertExternalTransfer(@Nonnull UUID transferId,
          @Nullable GasStack stack, long amount, @Nonnull BigInteger expectedStoredAmount) {
        ensureFresh();
        if (stack == null || stack.getGas() == null || amount <= 0 || expectedStoredAmount == null ||
              expectedStoredAmount.signum() < 0) {
            return invalidTransfer(transferId, amount);
        }
        GasStack normalized = stack.copy();
        normalized.amount = 1;
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(normalized);
        return submitExternalTransfer(transferId, QIOResourceKind.GAS, resource, amount,
              expectedStoredAmount, requested -> massInsert(normalized, requested, Action.EXECUTE));
    }

    public synchronized boolean addExternalStorageListener(@Nullable IQIOStorageListener listener) {
        if (listener == null || storageListeners.containsKey(listener) || clientSnapshot || isRemoved()) {
            return false;
        }
        storageListeners.put(listener, new StorageListenerRegistration(contentsRevision, capacityRevision,
              claimLedger.getRevision(), accessRevision));
        return true;
    }

    public synchronized boolean removeExternalStorageListener(@Nullable IQIOStorageListener listener) {
        return listener != null && storageListeners.remove(listener) != null;
    }

    @Override
    public synchronized void setSecurityMode(SecurityMode securityMode) {
        SecurityMode previous = getSecurity();
        super.setSecurityMode(securityMode);
        if (previous != getSecurity()) {
            markAccessChanged();
        }
    }

    @Override
    public synchronized void setValid(boolean valid) {
        if (isValid() != valid) {
            super.setValid(valid);
            markAccessChanged();
        }
    }

    public synchronized long massInsert(ItemStack stack, long amount, Action action) {
        ensureFresh();
        if (stack == null || stack.isEmpty() || amount <= 0 || action == null) {
            return 0;
        }
        if (action.simulate()) {
            UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(stack));
            return simulateInsert(resource, QIOResourceKind.ITEM, amount);
        }
        long inserted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                long current = drive.insert(stack, amount - inserted, action);
                inserted += current;
                if (inserted >= amount) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(stack));
        finishTransaction(resource, QIOResourceKind.ITEM, inserted, true);
        return inserted;
    }

    public synchronized long massInsert(FluidStack stack, long amount, Action action) {
        ensureFresh();
        if (stack == null || stack.getFluid() == null || amount <= 0 || action == null) {
            return 0;
        }
        if (action.simulate()) {
            UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack);
            return simulateInsert(resource, QIOResourceKind.FLUID, amount);
        }
        long inserted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                long current = drive.insert(stack, amount - inserted, action);
                inserted += current;
                if (inserted >= amount) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack);
        finishTransaction(resource, QIOResourceKind.FLUID, inserted, true);
        return inserted;
    }

    public synchronized long massInsert(GasStack stack, long amount, Action action) {
        ensureFresh();
        if (stack == null || stack.getGas() == null || amount <= 0 || action == null) {
            return 0;
        }
        if (action.simulate()) {
            UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack);
            return simulateInsert(resource, QIOResourceKind.GAS, amount);
        }
        long inserted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                long current = drive.insert(stack, amount - inserted, action);
                inserted += current;
                if (inserted >= amount) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack);
        finishTransaction(resource, QIOResourceKind.GAS, inserted, true);
        return inserted;
    }

    /**
     * Simulates a group of item insertions against one shared drive snapshot.
     * This is used by crafting transfers where independent simulations would
     * otherwise reuse the same count or type capacity for every remainder.
     */
    public synchronized boolean canInsertAllItems(@Nullable Collection<ItemStack> stacks) {
        ensureFresh();
        if (stacks == null) {
            return false;
        }
        List<ItemInsertionSimulation> drives = new ArrayList<>(activeDrives.size());
        for (QIODriveData drive : activeDrives.values()) {
            if (drive.accepts(QIOResourceKind.ITEM)) {
                drives.add(new ItemInsertionSimulation(drive.getRecord()));
            }
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            HashedItem item = HashedItem.create(stack);
            UUID uuid = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(item);
            Object resourceKey = uuid == null ? item : uuid;
            long remaining = stack.getCount();
            for (ItemInsertionSimulation drive : drives) {
                remaining = drive.insert(resourceKey, remaining);
                if (remaining <= 0) {
                    break;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    public synchronized long massExtract(ItemStack stack, long amount, Action action) {
        ensureFresh();
        if (stack == null || stack.isEmpty() || amount <= 0 || action == null) {
            return 0;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(stack));
        long extractable = simulateExtract(resource, amount);
        if (action.simulate()) {
            return extractable;
        }
        if (extractable <= 0) {
            return 0;
        }
        long extracted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                long current = drive.extract(stack, extractable - extracted, action);
                extracted += current;
                if (extracted >= extractable) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        finishTransaction(resource, QIOResourceKind.ITEM, extracted, false);
        return extracted;
    }

    public synchronized long massExtract(FluidStack stack, long amount, Action action) {
        ensureFresh();
        if (stack == null || stack.getFluid() == null || amount <= 0 || action == null) {
            return 0;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack);
        long extractable = simulateExtract(resource, amount);
        if (action.simulate()) {
            return extractable;
        }
        if (extractable <= 0) {
            return 0;
        }
        long extracted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                long current = drive.extract(stack, extractable - extracted, action);
                extracted += current;
                if (extracted >= extractable) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        finishTransaction(resource, QIOResourceKind.FLUID, extracted, false);
        return extracted;
    }

    public synchronized long massExtract(GasStack stack, long amount, Action action) {
        ensureFresh();
        if (stack == null || stack.getGas() == null || amount <= 0 || action == null) {
            return 0;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack);
        long extractable = simulateExtract(resource, amount);
        if (action.simulate()) {
            return extractable;
        }
        if (extractable <= 0) {
            return 0;
        }
        long extracted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                long current = drive.extract(stack, extractable - extracted, action);
                extracted += current;
                if (extracted >= extractable) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        finishTransaction(resource, QIOResourceKind.GAS, extracted, false);
        return extracted;
    }

    /** Extracts a registry-resolved resource without trusting a client payload. */
    public synchronized long massExtract(@Nullable UUID resource, long amount, Action action) {
        ensureFresh();
        if (resource == null || amount <= 0 || action == null || !resourceDataMap.containsKey(resource)) {
            return 0;
        }
        long extractable = simulateExtract(resource, amount);
        if (action.simulate()) {
            return extractable;
        }
        if (extractable <= 0) {
            return 0;
        }
        QIOResourceKind kind = resourceKinds.get(resource);
        if (kind == null) {
            kind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
        }
        long extracted = 0;
        applyingFrequencyTransaction = true;
        try {
            for (QIODriveData drive : activeDrives.values()) {
                extracted += drive.extract(resource, extractable - extracted, action);
                if (extracted >= extractable) {
                    break;
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        finishTransaction(resource, kind, extracted, false);
        return extracted;
    }

    /** Inserts an already registered resource through the same unified drive path. */
    public synchronized long massInsert(@Nullable UUID resource, long amount, Action action) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type == null || amount <= 0) {
            return 0;
        }
        return switch (type.getKind()) {
            case ITEM -> massInsert(type.createItemStack(1), amount, action);
            case FLUID -> massInsert(type.createFluidStack(1), amount, action);
            case GAS -> massInsert(type.createGasStack(1), amount, action);
        };
    }

    /** Consumes resources owned by a claim, bypassing only that claim's ordinary availability deduction. */
    private boolean extractClaimedBatch(Map<UUID, Long> resources) {
        if (resources == null || resources.isEmpty()) {
            return true;
        }
        for (Map.Entry<UUID, Long> entry : resources.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0 ||
                  resourceDataMap.getOrDefault(entry.getKey(), QIOAmount.ZERO)
                        .compareTo(QIOAmount.of(entry.getValue())) < 0) {
                return false;
            }
        }
        Map<UUID, Long> extracted = new LinkedHashMap<>();
        boolean complete = true;
        applyingFrequencyTransaction = true;
        try {
            for (Map.Entry<UUID, Long> entry : resources.entrySet()) {
                long amount = extractPhysical(entry.getKey(), entry.getValue());
                if (amount > 0) {
                    extracted.put(entry.getKey(), amount);
                }
                if (amount != entry.getValue()) {
                    complete = false;
                    break;
                }
            }
            if (!complete) {
                for (Map.Entry<UUID, Long> entry : extracted.entrySet()) {
                    if (insertPhysical(entry.getKey(), entry.getValue()) != entry.getValue()) {
                        needsRefresh = true;
                        QIOLog.LOGGER.error("Unable to roll back an incomplete QIO claimed extraction for {}", entry.getKey());
                    }
                }
            }
        } finally {
            applyingFrequencyTransaction = false;
        }
        if (!complete) {
            if (needsRefresh) {
                refresh();
            }
            return false;
        }
        Map<UUID, AmountChange> changes = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : resources.entrySet()) {
            QIOResourceKind kind = resourceKinds.get(entry.getKey());
            if (kind == null) {
                kind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(entry.getKey());
            }
            AmountChange change = mutateAggregate(entry.getKey(), kind, entry.getValue(), false);
            if (change == null) {
                needsRefresh = true;
                refresh();
                return false;
            }
            changes.put(entry.getKey(), change);
        }
        queueStorageChanges(changes);
        return true;
    }

    private long extractPhysical(UUID resource, long amount) {
        long extracted = 0;
        for (QIODriveData drive : activeDrives.values()) {
            extracted = safeAdd(extracted, drive.extract(resource, amount - extracted, Action.EXECUTE));
            if (extracted >= amount) {
                break;
            }
        }
        return extracted;
    }

    private long insertPhysical(UUID resource, long amount) {
        long inserted = 0;
        for (QIODriveData drive : activeDrives.values()) {
            inserted = safeAdd(inserted, drive.insert(resource, amount - inserted, Action.EXECUTE));
            if (inserted >= amount) {
                break;
            }
        }
        return inserted;
    }

    public synchronized long getStored(ItemStack stack) {
        ensureFresh();
        UUID resource = stack == null || stack.isEmpty() ? null :
              QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(stack));
        return getStored(resource);
    }

    public synchronized long getStored(FluidStack stack) {
        ensureFresh();
        return getStored(stack == null ? null : QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(stack));
    }

    public synchronized long getStored(GasStack stack) {
        ensureFresh();
        return getStored(stack == null ? null : QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(stack));
    }

    public synchronized long getStored(@Nullable UUID resource) {
        if (resource == null) {
            return 0;
        }
        // UUID-based callers are predominantly server packet handlers. Keep
        // their view authoritative even when a drive changed between ticks.
        ensureFresh();
        return resourceDataMap.getOrDefault(resource, QIOAmount.ZERO).longValueClamped();
    }

    @Nonnull
    public synchronized QIOAmount getStoredExact(@Nullable UUID resource) {
        if (resource == null) {
            return QIOAmount.ZERO;
        }
        ensureFresh();
        return resourceDataMap.getOrDefault(resource, QIOAmount.ZERO);
    }

    public synchronized long getCommitted(@Nullable UUID resource) {
        return getCommittedExact(resource).longValueClamped();
    }

    @Nonnull
    public synchronized QIOAmount getCommittedExact(@Nullable UUID resource) {
        return claimLedger.getCommitted(resource);
    }

    public synchronized long getAvailable(@Nullable UUID resource) {
        return getAvailableExact(resource).longValueClamped();
    }

    @Nonnull
    public synchronized QIOAmount getAvailableExact(@Nullable UUID resource) {
        if (resource == null) {
            return QIOAmount.ZERO;
        }
        ensureFresh();
        return claimLedger.getAvailable(resource, resourceDataMap.getOrDefault(resource, QIOAmount.ZERO));
    }

    public synchronized long getStored(@Nullable QIOFilter filter) {
        ensureFresh();
        if (filter == null || !filter.isEnabled()) {
            return 0;
        }
        long stored = 0;
        for (QIOResourceEntry entry : getResourceEntries()) {
            if (filter.test(entry)) {
                stored = safeAdd(stored, entry.getAmount());
            }
        }
        return stored;
    }

    public synchronized long getTotalCount() {
        ensureFresh();
        return totalCount;
    }

    @Nonnull
    public synchronized QIOAmount getExactTotalCount() {
        ensureFresh();
        return exactTotalCount;
    }

    public synchronized long getTotalCountCapacity() {
        ensureFresh();
        return totalCountCapacity;
    }

    /** Exact finite capacity contribution; use {@link #getCapacitySummary()} to detect unlimited capacity. */
    @Nonnull
    public synchronized QIOAmount getExactCountCapacity() {
        ensureFresh();
        return finiteCountCapacity;
    }

    public synchronized int getTotalTypes() {
        ensureFresh();
        return totalTypes;
    }

    public synchronized int getTotalTypeCapacity() {
        ensureFresh();
        return totalTypeCapacity;
    }

    /** Exact finite type-capacity contribution; use {@link #getCapacitySummary()} to detect unlimited capacity. */
    @Nonnull
    public synchronized QIOAmount getExactTypeCapacity() {
        ensureFresh();
        return finiteTypeCapacity;
    }

    public synchronized int getUnlimitedCountDriveCount() {
        ensureFresh();
        return unlimitedCountDrives;
    }

    public synchronized int getUnlimitedTypeDriveCount() {
        ensureFresh();
        return unlimitedTypeDrives;
    }

    @Nonnull
    public synchronized QIOCapacitySummary getCapacitySummary() {
        ensureFresh();
        return capacitySummary;
    }

    @Nonnull
    public synchronized Map<UUID, Long> getResourceDataMap() {
        ensureFresh();
        Map<UUID, Long> projected = new LinkedHashMap<>();
        resourceDataMap.forEach((resource, amount) -> projected.put(resource, amount.longValueClamped()));
        return Collections.unmodifiableMap(projected);
    }

    @Nonnull
    public synchronized List<QIOResourceEntry> getResourceEntries() {
        ensureFresh();
        return createEntries(resourceDataMap.keySet());
    }

    @Nonnull
    public synchronized List<QIOResourceEntry> getResourceEntries(@Nullable QIOResourceKind kind) {
        ensureFresh();
        if (kind == null) {
            return getResourceEntries();
        }
        List<QIOResourceEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, QIOResourceKind> entry : resourceKinds.entrySet()) {
            if (entry.getValue() == kind) {
                QIOResourceEntry resource = QIOResourceEntry.create(entry.getKey(),
                      resourceDataMap.getOrDefault(entry.getKey(), QIOAmount.ZERO));
                if (resource != null) {
                    entries.add(resource);
                }
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.getUUID().toString()));
        return Collections.unmodifiableList(entries);
    }

    @Nonnull
    public synchronized Map<HashedItem, Long> getItemDataMap() {
        ensureFresh();
        Map<HashedItem, Long> items = new LinkedHashMap<>();
        for (Map.Entry<UUID, QIOAmount> entry : resourceDataMap.entrySet()) {
            QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(entry.getKey());
            if (type != null && type.getKind() == QIOResourceKind.ITEM && type.getItemType() != null) {
                items.put(type.getItemType(), entry.getValue().longValueClamped());
            }
        }
        return Collections.unmodifiableMap(items);
    }

    public synchronized long getStored(HashedItem item) {
        return item == null ? 0 : getStored(QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(item));
    }

    /** Compatibility helper used by the crafting window and recipe transfer layer. */
    public synchronized boolean isStoring(@Nullable HashedItem item) {
        return item != null && getStored(item) > 0;
    }

    /**
     * Returns every stored item type for a concrete item id. NBT and metadata
     * remain part of the {@link HashedItem} equality, so visually similar
     * stacks are never merged accidentally.
     */
    @Nonnull
    public synchronized List<HashedItem> getTypesForItem(@Nullable net.minecraft.item.Item item) {
        ensureFresh();
        if (item == null) {
            return Collections.emptyList();
        }
        List<HashedItem> types = new ArrayList<>();
        for (Map.Entry<HashedItem, Long> entry : getItemDataMap().entrySet()) {
            if (entry.getKey().getInternalStack().getItem() == item && entry.getValue() > 0) {
                types.add(entry.getKey());
            }
        }
        return types;
    }

    /** Removes up to {@code amount} of the requested item type. */
    @Nonnull
    public synchronized ItemStack removeItem(@Nonnull ItemStack requested, int amount) {
        if (requested.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(requested));
        long extracted = massExtract(resource, Math.min(Integer.MAX_VALUE, amount), Action.EXECUTE);
        return resource == null ? ItemStack.EMPTY : QIOResourceTypeRegistry.INSTANCE.createItemStack(resource,
              (int) Math.min(Integer.MAX_VALUE, extracted));
    }

    @Nonnull
    public synchronized ItemStack addItem(@Nonnull ItemStack stack) {
        long inserted = massInsert(stack, stack.getCount(), Action.EXECUTE);
        return StackUtils.size(stack, stack.getCount() - (int) Math.min(Integer.MAX_VALUE, inserted));
    }

    @Nonnull
    public synchronized ItemStack removeByType(@Nullable HashedItem type, int amount) {
        ensureFresh();
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        UUID resource = type == null ? getFirstItemResource() : QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(type);
        if (resource == null) {
            return ItemStack.EMPTY;
        }
        long extracted = massExtract(resource, amount, Action.EXECUTE);
        return QIOResourceTypeRegistry.INSTANCE.createItemStack(resource, (int) extracted);
    }

    public synchronized long getTotalItemCount() {
        return getTotalCount();
    }

    public synchronized long getTotalItemCountCapacity() {
        return getTotalCountCapacity();
    }

    public synchronized int getTotalItemTypes(boolean remote) {
        return getTotalTypes();
    }

    public synchronized int getTotalItemTypeCapacity() {
        return getTotalTypeCapacity();
    }

    public synchronized void openViewer(EntityPlayerMP player) {
        if (player != null) {
            viewers.add(player);
        }
    }

    public synchronized void closeViewer(EntityPlayerMP player) {
        viewers.remove(player);
    }

    @Nonnull
    public synchronized Map<QIODriveMount, QIODriveSlotState> getSlotStates() {
        ensureFresh();
        return Collections.unmodifiableMap(new LinkedHashMap<>(slotStates));
    }

    @Nullable
    public synchronized QIODriveData getDriveData(@Nullable QIODriveMount mount) {
        ensureFresh();
        return mount == null ? null : activeDrives.get(mount);
    }

    public synchronized QIODriveSlotState getSlotState(@Nullable QIODriveMount mount) {
        ensureFresh();
        return mount == null ? QIODriveSlotState.UNLOADED : slotStates.getOrDefault(mount, QIODriveSlotState.UNLOADED);
    }

    @Nonnull
    public synchronized Set<UUID> getMissingResources() {
        ensureFresh();
        return Collections.unmodifiableSet(new java.util.HashSet<>(missingResources));
    }

    public synchronized long getStoredCount(QIOResourceKind kind) {
        ensureFresh();
        return kindCounts.getOrDefault(kind, QIOAmount.ZERO).longValueClamped();
    }

    @Override
    public synchronized EnumColor getColor() {
        return color;
    }

    @Override
    public synchronized void setColor(EnumColor color) {
        EnumColor resolved = color == null ? EnumColor.INDIGO : color;
        if (this.color != resolved) {
            this.color = resolved;
            dirty = true;
        }
    }

    @Override
    public synchronized int getSyncHash() {
        ensureFresh();
        int hash = super.getSyncHash();
        hash = 31 * hash + Long.hashCode(totalCount);
        hash = 31 * hash + Long.hashCode(totalCountCapacity);
        hash = 31 * hash + totalTypes;
        hash = 31 * hash + totalTypeCapacity;
        hash = 31 * hash + exactTotalCount.hashCode();
        hash = 31 * hash + capacitySummary.hashCode();
        hash = 31 * hash + Long.hashCode(claimLedger.getRevision());
        return 31 * hash + color.ordinal();
    }

    @Override
    public synchronized void write(NBTTagCompound data) {
        super.write(data);
        data.setString(FREQUENCY_UUID_KEY, frequencyUUID.toString());
        data.setLong(CONTENTS_REVISION_KEY, contentsRevision);
        data.setLong(CAPACITY_REVISION_KEY, capacityRevision);
        data.setLong(ACCESS_REVISION_KEY, accessRevision);
        claimLedger.write(data);
        transferLedger.write(data);
        data.setInteger(NBTConstants.COLOR, color.ordinal());
        // Keep the temporary key readable for worlds written by the initial
        // port; new saves use the shared frequency color field.
        data.setInteger("qioColor", color.ordinal());
    }

    private synchronized void readQIOData(NBTTagCompound data) {
        if (data.hasKey(FREQUENCY_UUID_KEY)) {
            try {
                frequencyUUID = UUID.fromString(data.getString(FREQUENCY_UUID_KEY));
            } catch (IllegalArgumentException ignored) {
                frequencyUUID = UUID.randomUUID();
                dirty = true;
            }
        } else {
            frequencyUUID = UUID.randomUUID();
            dirty = true;
        }
        contentsRevision = Math.max(0, data.getLong(CONTENTS_REVISION_KEY));
        capacityRevision = Math.max(0, data.getLong(CAPACITY_REVISION_KEY));
        accessRevision = Math.max(0, data.getLong(ACCESS_REVISION_KEY));
        claimLedger.read(data);
        transferLedger.read(data);
        if (data.hasKey(NBTConstants.COLOR) || data.hasKey("qioColor")) {
            int index = data.hasKey(NBTConstants.COLOR) ? data.getInteger(NBTConstants.COLOR) : data.getInteger("qioColor");
            if (index >= 0 && index < EnumColor.values().length) {
                color = EnumColor.values()[index];
            }
        }
    }

    private synchronized void readQIOData(ByteBuf data) {
        UUID parsedUUID = mekanism.common.util.MekanismUtils.parseUUID(mekanism.common.PacketHandler.readString(data));
        frequencyUUID = parsedUUID == null ? UUID.randomUUID() : parsedUUID;
        contentsRevision = Math.max(0, data.readLong());
        capacityRevision = Math.max(0, data.readLong());
        claimLedger.setClientRevision(data.readLong());
        accessRevision = Math.max(0, data.readLong());
        totalCount = data.readLong();
        totalCountCapacity = data.readLong();
        totalTypes = data.readInt();
        totalTypeCapacity = data.readInt();
        for (QIOResourceKind kind : QIOResourceKind.values()) {
            kindCounts.put(kind, QIOAmount.of(data.readLong()));
        }
        exactTotalCount = QIOAmount.parse(mekanism.common.PacketHandler.readString(data));
        finiteCountCapacity = QIOAmount.parse(mekanism.common.PacketHandler.readString(data));
        finiteTypeCapacity = QIOAmount.parse(mekanism.common.PacketHandler.readString(data));
        unlimitedCountDrives = Math.max(0, data.readInt());
        unlimitedTypeDrives = Math.max(0, data.readInt());
        capacitySummary = new QIOCapacitySummary(finiteCountCapacity, finiteTypeCapacity,
              unlimitedCountDrives, unlimitedTypeDrives);
        exactTotalStorageUnits = QIOAmount.of(totalStorageUnits);
        color = EnumColor.values()[Math.max(0, Math.min(EnumColor.values().length - 1, data.readInt()))];
        clientSnapshot = true;
        needsRefresh = false;
    }

    @Override
    public synchronized void write(TileNetworkList data) {
        ensureFresh();
        super.write(data);
        data.add(frequencyUUID.toString());
        data.add(contentsRevision);
        data.add(capacityRevision);
        data.add(claimLedger.getRevision());
        data.add(accessRevision);
        data.add(totalCount);
        data.add(totalCountCapacity);
        data.add(totalTypes);
        data.add(totalTypeCapacity);
        for (QIOResourceKind kind : QIOResourceKind.values()) {
            data.add(kindCounts.getOrDefault(kind, QIOAmount.ZERO).longValueClamped());
        }
        data.add(exactTotalCount.toString());
        data.add(finiteCountCapacity.toString());
        data.add(finiteTypeCapacity.toString());
        data.add(unlimitedCountDrives);
        data.add(unlimitedTypeDrives);
        data.add(color.ordinal());
    }

    private void ensureFresh() {
        if (!clientSnapshot) {
            if (observedMountRevision != QIODriveStorage.INSTANCE.getMountRevision()) {
                needsRefresh = true;
            }
            if (needsRefresh) {
                refresh();
            }
        }
    }

    private void finishTransaction(@Nullable UUID resource, @Nullable QIOResourceKind kind, long amount,
          boolean insert) {
        if (amount <= 0) {
            return;
        }
        if (resource == null || kind == null) {
            needsRefresh = true;
            pendingFullRescan = true;
            return;
        }
        applyAggregateChange(resource, kind, amount, insert);
    }

    private void applyAggregateChange(UUID resource, QIOResourceKind kind, long amount, boolean insert) {
        AmountChange change = mutateAggregate(resource, kind, amount, insert);
        if (change != null) {
            queueStorageChanges(Collections.singletonMap(resource, change));
        }
    }

    @Nullable
    private AmountChange mutateAggregate(UUID resource, @Nullable QIOResourceKind kind, long amount, boolean insert) {
        if (resource == null || kind == null || amount <= 0) {
            return null;
        }
        QIOAmount oldAmount = resourceDataMap.getOrDefault(resource, QIOAmount.ZERO);
        if (!insert && oldAmount.compareTo(QIOAmount.of(amount)) < 0) {
            return null;
        }
        QIOAmount newAmount = insert ? oldAmount.add(amount) : oldAmount.subtract(amount);
        QIOAmount storageUnits = QIOAmount.of(amount).multiply(QIOStorageUnits.getUnitsPerResource(kind));
        QIOAmount oldKindAmount = kindCounts.getOrDefault(kind, QIOAmount.ZERO);
        if (newAmount.isZero()) {
            resourceDataMap.remove(resource);
            resourceKinds.remove(resource);
            missingResources.remove(resource);
        } else {
            resourceDataMap.put(resource, newAmount);
            resourceKinds.put(resource, kind);
            if (QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource) == null) {
                missingResources.add(resource);
            } else {
                missingResources.remove(resource);
            }
        }
        QIOAmount newKindAmount = insert ? oldKindAmount.add(amount) : oldKindAmount.subtract(amount);
        if (newKindAmount.isZero()) {
            kindCounts.remove(kind);
        } else {
            kindCounts.put(kind, newKindAmount);
        }
        exactTotalStorageUnits = insert ? exactTotalStorageUnits.add(storageUnits) : exactTotalStorageUnits.subtract(storageUnits);
        totalStorageUnits = exactTotalStorageUnits.longValueClamped();
        exactTotalCount = exactTotalStorageUnits.divideRoundUp(QIOStorageUnits.UNITS_PER_ITEM);
        totalCount = exactTotalCount.longValueClamped();
        totalTypes = resourceDataMap.size();
        updatedResources.add(resource);
        return new AmountChange(oldAmount, newAmount);
    }

    private void recordRefreshChanges(Map<UUID, QIOAmount> previousResources,
          QIOCapacitySummary previousCapacity, boolean fullRescan) {
        Set<UUID> resources = new HashSet<>(previousResources.keySet());
        resources.addAll(resourceDataMap.keySet());
        Map<UUID, AmountChange> changes = new LinkedHashMap<>();
        for (UUID resource : resources) {
            QIOAmount oldAmount = previousResources.getOrDefault(resource, QIOAmount.ZERO);
            QIOAmount newAmount = resourceDataMap.getOrDefault(resource, QIOAmount.ZERO);
            if (!oldAmount.equals(newAmount)) {
                changes.put(resource, new AmountChange(oldAmount, newAmount));
            }
        }
        if (!changes.isEmpty()) {
            queueStorageChanges(changes);
        }
        if (!previousCapacity.equals(capacitySummary)) {
            beginPendingStorageBatch();
            capacityRevision = nextRevision(capacityRevision);
            dirty = true;
        }
        pendingFullRescan |= fullRescan;
    }

    private void queueStorageChanges(Map<UUID, AmountChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        claimBackingCacheValid = false;
        beginPendingStorageBatch();
        contentsRevision = nextRevision(contentsRevision);
        dirty = true;
        for (Map.Entry<UUID, AmountChange> change : changes.entrySet()) {
            PendingStorageChange pending = pendingStorageChanges.get(change.getKey());
            if (pending == null) {
                pendingStorageChanges.put(change.getKey(), new PendingStorageChange(
                      change.getValue().oldAmount, change.getValue().newAmount));
            } else {
                pending.newAmount = change.getValue().newAmount;
                if (pending.oldAmount.equals(pending.newAmount)) {
                    pendingStorageChanges.remove(change.getKey());
                }
            }
        }
    }

    private void beginPendingStorageBatch() {
        if (pendingOldContentsRevision < 0) {
            pendingOldContentsRevision = contentsRevision;
            pendingOldCapacityRevision = capacityRevision;
            pendingOldClaimRevision = claimLedger.getRevision();
        }
    }

    private void markClaimsChanged(long oldClaimRevision, Set<UUID> resources) {
        claimBackingCacheValid = false;
        if (pendingOldContentsRevision < 0) {
            pendingOldContentsRevision = contentsRevision;
            pendingOldCapacityRevision = capacityRevision;
            pendingOldClaimRevision = oldClaimRevision;
        }
        pendingFullRescan = true;
        if (resources != null) {
            updatedResources.addAll(resources);
        }
        dirty = true;
    }

    private void dispatchStorageChanges() {
        if (pendingOldContentsRevision < 0) {
            return;
        }
        List<QIOStorageChange> changes = new ArrayList<>();
        boolean requireFullRescan = pendingFullRescan;
        for (Map.Entry<UUID, PendingStorageChange> pending : pendingStorageChanges.entrySet()) {
            QIOStorageEntry resource = createExternalEntry(pending.getKey(), QIOAmount.ZERO);
            if (resource == null) {
                requireFullRescan = true;
                continue;
            }
            changes.add(new QIOStorageChange(resource, pending.getValue().oldAmount.toBigInteger(),
                  pending.getValue().newAmount.toBigInteger()));
        }
        QIOStorageChangeBatch batch = new QIOStorageChangeBatch(pendingOldContentsRevision,
              contentsRevision, pendingOldCapacityRevision, capacityRevision, pendingOldClaimRevision,
              claimLedger.getRevision(), accessRevision, changes, requireFullRescan, false);
        for (Map.Entry<IQIOStorageListener, StorageListenerRegistration> entry :
              new ArrayList<>(storageListeners.entrySet())) {
            IQIOStorageListener listener = entry.getKey();
            StorageListenerRegistration registration = entry.getValue();
            if (storageListeners.get(listener) != registration) {
                continue;
            }
            if (contentsRevision <= registration.contentsRevision && capacityRevision <= registration.capacityRevision &&
                  claimLedger.getRevision() <= registration.claimRevision &&
                  accessRevision <= registration.accessRevision) {
                continue;
            }
            QIOStorageChangeBatch listenerBatch = registration.contentsRevision > pendingOldContentsRevision ||
                  registration.capacityRevision > pendingOldCapacityRevision ||
                  registration.claimRevision > pendingOldClaimRevision ? batch.requiringFullRescan() : batch;
            try {
                listener.onQIOStorageChanged(listenerBatch);
                registration.contentsRevision = contentsRevision;
                registration.capacityRevision = capacityRevision;
                registration.claimRevision = claimLedger.getRevision();
                registration.accessRevision = accessRevision;
            } catch (RuntimeException e) {
                storageListeners.remove(listener);
                QIOLog.LOGGER.error("Removing a failing QIO external storage listener", e);
            }
        }
        pendingStorageChanges.clear();
        pendingOldContentsRevision = -1;
        pendingOldCapacityRevision = -1;
        pendingOldClaimRevision = -1;
        pendingFullRescan = false;
    }

    private void markAccessChanged() {
        beginPendingStorageBatch();
        accessRevision = nextRevision(accessRevision);
        pendingFullRescan = true;
        dirty = true;
    }

    private void invalidateStorageListeners() {
        accessRevision = nextRevision(accessRevision);
        QIOStorageChangeBatch invalidation = QIOStorageChangeBatch.invalidated(contentsRevision,
              capacityRevision, claimLedger.getRevision(), accessRevision);
        for (IQIOStorageListener listener : new ArrayList<>(storageListeners.keySet())) {
            try {
                listener.onQIOStorageChanged(invalidation);
            } catch (RuntimeException e) {
                QIOLog.LOGGER.error("A QIO external storage listener failed during invalidation", e);
            }
        }
        storageListeners.clear();
        pendingStorageChanges.clear();
        pendingOldContentsRevision = -1;
        pendingOldCapacityRevision = -1;
        pendingOldClaimRevision = -1;
        pendingFullRescan = false;
    }

    @Nullable
    private QIOStorageEntry createExternalEntry(UUID resource, QIOAmount amount) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type == null) {
            return null;
        }
        BigInteger exactAmount = amount.toBigInteger();
        BigInteger committedAmount = claimLedger.getCommitted(resource).toBigInteger();
        try {
            return switch (type.getKind()) {
                case ITEM -> QIOStorageEntry.item(resource, exactAmount, committedAmount, type.createItemStack(1));
                case FLUID -> QIOStorageEntry.fluid(resource, exactAmount, committedAmount, type.createFluidStack(1));
                case GAS -> QIOStorageEntry.gas(resource, exactAmount, committedAmount, type.createGasStack(1));
            };
        } catch (RuntimeException e) {
            QIOLog.LOGGER.error("Unable to expose QIO resource {} through the external storage API", resource, e);
            return null;
        }
    }

    private static long nextRevision(long revision) {
        return revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1;
    }

    private QIOTransferResult submitExternalTransfer(UUID transferId, QIOResourceKind kind,
          UUID resource, long amount, BigInteger expectedStoredAmount,
          java.util.function.LongUnaryOperator insertion) {
        Objects.requireNonNull(transferId, "Transfer id cannot be null");
        if (resource == null || expectedStoredAmount == null || expectedStoredAmount.signum() < 0) {
            return invalidTransfer(transferId, amount);
        }
        String digest = kind.name() + '|' + resource + '|' + amount;
        QIOTransferResult result = transferLedger.submitReconciled(transferId, digest, amount,
              QIOAmount.of(expectedStoredAmount),
              () -> resourceDataMap.getOrDefault(resource, QIOAmount.ZERO), insertion,
              QIOStorageManager::flush);
        if (result.isSuccess()) {
            dirty = true;
        }
        return result;
    }

    private static QIOTransferResult invalidTransfer(UUID transferId, long amount) {
        return new QIOTransferResult(Objects.requireNonNull(transferId, "Transfer id cannot be null"),
              QIOTransferResult.Status.INVALID_REQUEST, Math.max(1, amount), 0);
    }

    /**
     * Calculates a multi-drive simulation without mutating records.  Calling
     * each drive's individual simulation independently would count the same
     * capacity repeatedly because simulated writes are intentionally
     * side-effect free.
     */
    private long simulateInsert(@Nullable UUID resource, QIOResourceKind kind, long amount) {
        if (kind == null || amount <= 0) {
            return 0;
        }
        long inserted = 0;
        for (QIODriveData drive : activeDrives.values()) {
            if (!drive.accepts(kind)) {
                continue;
            }
            QIODriveRecord record = drive.getRecord();
            boolean newType = resource == null || record.getStored(resource) <= 0;
            if (!record.hasUnlimitedTypeCapacity() && newType && record.getTotalTypes() >= record.getTypeCapacity()) {
                continue;
            }
            long current = record.getInsertable(kind, amount - inserted, newType);
            if (resource != null) {
                current = Math.min(current, Long.MAX_VALUE - record.getStored(resource));
            }
            inserted = safeAdd(inserted, current);
            if (inserted >= amount) {
                break;
            }
        }
        return inserted;
    }

    private long simulateExtract(@Nullable UUID resource, long amount) {
        if (resource == null || amount <= 0) {
            return 0;
        }
        return Math.min(amount, claimLedger.getAvailable(resource,
              resourceDataMap.getOrDefault(resource, QIOAmount.ZERO)).longValueClamped());
    }

    private void sendViewerUpdates() {
        List<EntityPlayerMP> staleViewers = new ArrayList<>();
        // Resolving a container frequency can close this viewer and mutate the
        // set, so iterate a snapshot and remove stale entries afterwards.
        for (EntityPlayerMP player : new ArrayList<>(viewers)) {
            if (player == null || player.isDead || !(player.openContainer instanceof QIOItemViewerContainer)
                  || ((QIOItemViewerContainer) player.openContainer).getFrequency() != this) {
                staleViewers.add(player);
            }
        }
        viewers.removeAll(staleViewers);
        if (viewers.isEmpty()) {
            updatedResources.clear();
            viewerCapacityDirty = false;
            return;
        }
        if (!updatedResources.isEmpty() || viewerCapacityDirty) {
            List<QIOResourceEntry> entries = createEntries(updatedResources);
            for (EntityPlayerMP viewer : viewers) {
                PacketQIOViewerData.sendUpdate(viewer, PacketQIOViewerData.getViewerWindowId(viewer), entries,
                      capacitySummary);
            }
            updatedResources.clear();
            viewerCapacityDirty = false;
        }
    }

    @Nonnull
    private List<QIOResourceEntry> createEntries(Collection<UUID> resources) {
        List<QIOResourceEntry> entries = new ArrayList<>();
        for (UUID resource : resources) {
            QIOResourceEntry entry = QIOResourceEntry.create(resource,
                  resourceDataMap.getOrDefault(resource, QIOAmount.ZERO));
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.getUUID().toString()));
        return entries;
    }

    @Nullable
    private UUID getFirstItemResource() {
        for (UUID resource : resourceDataMap.keySet()) {
            QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
            if (type != null && type.getKind() == QIOResourceKind.ITEM) {
                return resource;
            }
        }
        return null;
    }

    private void addCapacity(QIODriveRecord record) {
        if (record.hasUnlimitedCountCapacity()) {
            unlimitedCountDrives = safeIntAdd(unlimitedCountDrives, 1);
        } else {
            finiteCountCapacity = finiteCountCapacity.add(record.getExactCountCapacity());
        }
        if (record.hasUnlimitedTypeCapacity()) {
            unlimitedTypeDrives = safeIntAdd(unlimitedTypeDrives, 1);
        } else {
            finiteTypeCapacity = finiteTypeCapacity.add(record.getTypeCapacity());
        }
        exactTotalStorageUnits = exactTotalStorageUnits.add(record.getExactTotalStorageUnits());
    }

    private void unmountOwnedBy(IQIODriveHolder holder) {
        for (QIODriveMount mount : new ArrayList<>(ownedMounts)) {
            if (mount.getHolder() == holder) {
                QIODriveStorage.INSTANCE.unmount(mount);
                ownedMounts.remove(mount);
                activeDrives.remove(mount);
            }
        }
    }

    private static long safeAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static int safeIntAdd(int first, int second) {
        return second > Integer.MAX_VALUE - first ? Integer.MAX_VALUE : first + second;
    }

    private static final class AmountChange {

        private final QIOAmount oldAmount;
        private final QIOAmount newAmount;

        private AmountChange(QIOAmount oldAmount, QIOAmount newAmount) {
            this.oldAmount = oldAmount;
            this.newAmount = newAmount;
        }
    }

    private static final class PendingStorageChange {

        private final QIOAmount oldAmount;
        private QIOAmount newAmount;

        private PendingStorageChange(QIOAmount oldAmount, QIOAmount newAmount) {
            this.oldAmount = oldAmount;
            this.newAmount = newAmount;
        }
    }

    private static final class StorageListenerRegistration {

        private long contentsRevision;
        private long capacityRevision;
        private long claimRevision;
        private long accessRevision;

        private StorageListenerRegistration(long contentsRevision, long capacityRevision, long claimRevision,
              long accessRevision) {
            this.contentsRevision = contentsRevision;
            this.capacityRevision = capacityRevision;
            this.claimRevision = claimRevision;
            this.accessRevision = accessRevision;
        }
    }

    private static final class ItemInsertionSimulation {

        private final QIOAmount storageCapacity;
        private final int typeCapacity;
        private final boolean unlimitedTypes;
        private final Map<Object, Long> resources = new HashMap<>();
        private QIOAmount storageUnits;
        private int types;

        private ItemInsertionSimulation(QIODriveRecord record) {
            storageCapacity = record.getExactStorageCapacity();
            typeCapacity = record.getTypeCapacity();
            unlimitedTypes = record.hasUnlimitedTypeCapacity();
            storageUnits = record.getExactTotalStorageUnits();
            types = record.getTotalTypes();
            record.getContents().object2LongEntrySet().forEach(entry -> resources.put(entry.getKey(), entry.getLongValue()));
        }

        private long insert(Object resource, long amount) {
            if (amount <= 0 || storageUnits.compareTo(storageCapacity) >= 0) {
                return Math.max(0, amount);
            }
            long previous = resources.getOrDefault(resource, 0L);
            boolean newType = previous <= 0;
            if (!unlimitedTypes && newType && types >= typeCapacity) {
                return amount;
            }
            long inserted = Math.min(amount, Long.MAX_VALUE - previous);
            BigInteger available = storageCapacity.toBigInteger().subtract(storageUnits.toBigInteger());
            inserted = Math.min(inserted,
                  QIOStorageUnits.getInsertableAmount(QIOResourceKind.ITEM, inserted, available));
            if (inserted > 0) {
                storageUnits = storageUnits.add(QIOAmount.of(inserted).multiply(QIOStorageUnits.UNITS_PER_ITEM));
                resources.put(resource, previous + inserted);
                if (newType) {
                    types++;
                }
            }
            return amount - inserted;
        }
    }
}
