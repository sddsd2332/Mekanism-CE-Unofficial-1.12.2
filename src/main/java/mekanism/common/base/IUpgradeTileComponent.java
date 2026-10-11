package mekanism.common.base;

import mekanism.common.tile.component.TileComponentUpgrade;

/**
 * A machine feature enabled by installed upgrades, with the persistence and synchronization
 * contract of {@link ITileComponent}. Implementations may belong to optional modules.
 *
 * <p>The owning tile refreshes the enabled state when upgrades are restored, copied, installed
 * or removed. The hot path is {@code if (component.isEnabled()) component.tickServer();};
 * it must not rediscover a capability or scan the installed upgrades on every tick.
 *
 * <p>Enabled means that the required upgrade is installed, not that a frequency, permission,
 * recipe or resource transaction is valid. Those checks remain the feature's responsibility.
 * Persistence, synchronization and invalidation are not conditional on this flag. Disabling
 * normal ticking must not discard pending resources or prevent their separate recovery.
 *
 * <p>Implementing or registering this interface alone does not schedule it. The owner must
 * explicitly call its server phase; there is no generic tick on {@link ITileComponent}.
 */
public interface IUpgradeTileComponent extends ITileComponent {

    /**
     * Total installed count in this feature's enabling upgrade family; unrelated upgrades return no count.
     */
    int getInstalledCount(TileComponentUpgrade upgrades);

    /**
     * Deferred server lifecycle, after construction/restoration and before ordinary feature work.
     */
    default void onServerReady() {
    }

    /**
     * Reconcile a committed upgrade state, including restoration without installation callbacks.
     */
    default void onServerStateChanged() {
    }

    default void onChunkUnload() {
        invalidate();
    }

    /**
     * May run for disabled components which still own resources; never starts new normal work.
     */
    default boolean needsRecoveryTick() {
        return false;
    }

    default void tickRecovery() {
    }

    /**
     * Notification can originate on a worker; implementations must only invalidate thread-safe caches here.
     */
    default void onContentsChanged() {
    }

    default void onNeighborChanged() {
    }

    /**
     * Returns the cached upgrade-enabled boolean, without capability/world queries or mutation.
     */
    boolean isEnabled();

    /**
     * Refreshes the cached flag from the installed upgrade counts. Call after registration and
     * after the complete upgrade state has changed/restored, before normal feature ticking.
     * Pure state refresh on either logical side: no network/world mutations here. Server
     * side effects belong to onServerStateChanged after complete tile restoration.
     */
    void onUpgradesChanged(TileComponentUpgrade upgrades);

    /**
     * An installed upgrade count increased. Like Meka modules, first distinguishes activation
     * from adding more of an already installed upgrade. Called after refreshing upgrade state,
     * not while merely restoring saved NBT. A bulk count change produces one callback.
     * Delivery is deferred to the server phase; cached state already reflects the latest commit.
     */
    default void onAdded(boolean first) {
    }

    /**
     * An installed upgrade count decreased; last means the final enabling upgrade was removed.
     * Called even when isEnabled() is now false so the implementation can release its network
     * membership and arrange recovery. This is not the same as unloading the tile.
     */
    default void onRemoved(boolean last) {
    }

    /**
     * Called after an installed-upgrade change actually changes the cached enabled flag.
     * The flag remains derived from installed upgrades; this does not add a user toggle.
     */
    default void onEnabledStateChanged(boolean enabled) {
    }

    /**
     * Runs on the server thread, only while enabled, in the owner's explicit feature phase.
     */
    void tickServer();
}
