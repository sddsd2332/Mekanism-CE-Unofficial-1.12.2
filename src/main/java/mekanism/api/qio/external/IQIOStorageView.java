package mekanism.api.qio.external;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.UUID;

/** Authorized, lifecycle-bound access to one QIO frequency. */
public interface IQIOStorageView {

    @Nonnull
    UUID getFrequencyUUID();

    @Nonnull
    String getFrequencyName();

    long getContentsRevision();

    long getCapacityRevision();

    long getClaimRevision();

    long getAccessRevision();

    @Nonnull
    QIOStorageSnapshot getSnapshot();

    @Nullable
    QIOStorageEntry getResource(UUID resourceUUID);

    @Nullable
    QIOResourceClaim getClaim(UUID claimId);

    /**
     * Returns the physical support currently assigned to a logical claim.
     * Implementations predating claim backing may return {@code null}; callers must then wait
     * for a storage implementation that can prove ownership rather than assume full backing.
     */
    @Nullable
    default QIOClaimBacking getClaimBacking(UUID claimId) {
        return null;
    }

    @Nonnull
    QIOClaimResult submitClaim(QIOClaimRequest request);

    long insert(ItemStack stack, long amount, Action action);

    long insert(FluidStack stack, long amount, Action action);

    long insert(GasStack stack, long amount, Action action);

    /** Generic codec-backed insertion. Legacy implementations fail closed until they override it. */
    default long insert(@Nonnull QIOResourceDescriptor descriptor, long amount, @Nonnull Action action) {
        return 0;
    }

    /**
     * Executes or repairs an insertion using the exact amount observed before its durable intent
     * was saved. Implementations that cannot prove the physical state must fail closed.
     */
    @Nonnull
    default QIOTransferResult insertIdempotent(@Nonnull UUID transferId, ItemStack stack,
          long amount, @Nonnull BigInteger expectedStoredAmount) {
        return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
              Math.max(1, amount), 0);
    }

    @Nonnull
    default QIOTransferResult insertIdempotent(@Nonnull UUID transferId, FluidStack stack,
          long amount, @Nonnull BigInteger expectedStoredAmount) {
        return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
              Math.max(1, amount), 0);
    }

    @Nonnull
    default QIOTransferResult insertIdempotent(@Nonnull UUID transferId, GasStack stack,
          long amount, @Nonnull BigInteger expectedStoredAmount) {
        return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
              Math.max(1, amount), 0);
    }

    /** Generic idempotent insertion used by QIO processing for custom codecs. */
    @Nonnull
    default QIOTransferResult insertIdempotent(@Nonnull UUID transferId,
          @Nonnull QIOResourceDescriptor descriptor, long amount,
          @Nonnull BigInteger expectedStoredAmount) {
        return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
              Math.max(1, amount), 0);
    }

    long extract(ItemStack stack, long amount, Action action);

    long extract(FluidStack stack, long amount, Action action);

    long extract(GasStack stack, long amount, Action action);

    /** Generic codec-backed extraction. Legacy implementations fail closed until they override it. */
    default long extract(@Nonnull QIOResourceDescriptor descriptor, long amount,
          @Nonnull Action action) {
        return 0;
    }

    boolean addListener(IQIOStorageListener listener);

    boolean removeListener(IQIOStorageListener listener);

    boolean isValid();

    void close();
}
