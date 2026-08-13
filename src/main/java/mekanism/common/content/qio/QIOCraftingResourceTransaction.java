package mekanism.common.content.qio;

import mekanism.api.Action;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Atomic resource reservation primitive for crafting integrations.
 *
 * <p>Vanilla 1.12 recipes currently expose item ingredients, but addon
 * recipes can use this same transaction for fluid and gas UUIDs. No conversion
 * is performed: the registry kind is checked and each UUID is rolled back in
 * its native unit if any request cannot be fulfilled.</p>
 */
public final class QIOCraftingResourceTransaction {

    private final QIOFrequency frequency;
    private final List<QIOCraftingTransferHelper.ResourceRequest> requests;
    private final Map<UUID, Long> extracted = new LinkedHashMap<>();
    private boolean executed;
    private boolean rolledBack;
    private boolean extractionComplete;
    private boolean committed;

    public QIOCraftingResourceTransaction(@Nonnull QIOFrequency frequency,
          @Nonnull List<QIOCraftingTransferHelper.ResourceRequest> requests) {
        this.frequency = frequency;
        this.requests = new ArrayList<>(requests);
    }

    public boolean simulate() {
        if (frequency.isRemoved() || !frequency.isValid()) {
            return false;
        }
        Map<UUID, Long> needed = new LinkedHashMap<>();
        for (QIOCraftingTransferHelper.ResourceRequest request : requests) {
            if (request == null || request.getResource() == null || request.getKind() == null || request.getAmount() <= 0) {
                return false;
            }
            QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(request.getResource());
            if (type == null || type.getKind() != request.getKind()) {
                return false;
            }
            long current = needed.getOrDefault(request.getResource(), 0L);
            long next = current > Long.MAX_VALUE - request.getAmount() ? Long.MAX_VALUE : current + request.getAmount();
            needed.put(request.getResource(), next);
        }
        for (Map.Entry<UUID, Long> entry : needed.entrySet()) {
            if (frequency.getAvailable(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Executes all requests or restores every already extracted amount. */
    public boolean execute() {
        if (executed || rolledBack || !simulate()) {
            return false;
        }
        executed = true;
        for (QIOCraftingTransferHelper.ResourceRequest request : requests) {
            long amount = frequency.massExtract(request.getResource(), request.getAmount(), Action.EXECUTE);
            if (amount != request.getAmount()) {
                if (amount > 0) {
                    extracted.merge(request.getResource(), amount, Long::sum);
                }
                rollback();
                return false;
            }
            extracted.merge(request.getResource(), amount, Long::sum);
        }
        extractionComplete = true;
        return true;
    }

    /** Returns all reserved resources to QIO; incomplete restores remain retryable. */
    public void rollback() {
        if (rolledBack || committed) {
            return;
        }
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(extracted.entrySet())) {
            long amount = entry.getValue();
            if (amount <= 0) {
                extracted.remove(entry.getKey());
                continue;
            }
            long restored = QIORollback.restore(frequency, entry.getKey(), amount, "crafting resource transaction");
            long unresolved = amount - restored;
            if (unresolved <= 0) {
                extracted.remove(entry.getKey());
            } else {
                extracted.put(entry.getKey(), unresolved);
            }
        }
        rolledBack = extracted.isEmpty();
    }

    public boolean isRollbackComplete() {
        return rolledBack && !committed && extracted.isEmpty();
    }

    /** Marks the transaction consumed by the caller without reinserting it. */
    public void commit() {
        if (!extractionComplete || rolledBack || committed) {
            return;
        }
        extracted.clear();
        committed = true;
    }

    @Nonnull
    public Map<UUID, Long> getExtracted() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(extracted));
    }
}
