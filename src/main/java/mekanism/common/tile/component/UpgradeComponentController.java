package mekanism.common.tile.component;

import mekanism.common.base.IUpgradeTileComponent;
import mekanism.common.tile.prefab.TileEntityBasicBlock;

/**
 * Two explicit feature slots, with pure state refresh separated from server lifecycle side effects.
 */
public final class UpgradeComponentController {
    private final Entry qio;
    private final Entry ae;
    private boolean loaded;

    public UpgradeComponentController(TileEntityBasicBlock tile, TileComponentUpgrade upgrades) {
        qio = create(UpgradeComponentRegistry.Slot.QIO, tile, upgrades);
        ae = create(UpgradeComponentRegistry.Slot.AE, tile, upgrades);
    }

    private static Entry create(UpgradeComponentRegistry.Slot slot, TileEntityBasicBlock tile, TileComponentUpgrade upgrades) {
        IUpgradeTileComponent component = UpgradeComponentRegistry.create(slot, tile);
        if (component == null) return null;
        tile.addComponent(component);
        return new Entry(component, upgrades);
    }

    public IUpgradeTileComponent get(UpgradeComponentRegistry.Slot slot) {
        Entry entry = slot == UpgradeComponentRegistry.Slot.QIO ? qio : ae;
        return entry == null ? null : entry.component;
    }

    public boolean isEmpty() {
        return qio == null && ae == null;
    }

    public void refresh(TileComponentUpgrade upgrades, boolean installedChange) {
        if (qio != null) qio.refresh(upgrades, installedChange);
        if (ae != null) ae.refresh(upgrades, installedChange);
    }

    public void tickServer() {
        if (!loaded) {
            loaded = true;
            if (qio != null) qio.component.onServerReady();
            if (ae != null) ae.component.onServerReady();
        }
        if (qio != null) qio.tick();
        if (ae != null) ae.tick();
    }

    public void restored() {
        if (qio != null) qio.dirty = true;
        if (ae != null) ae.dirty = true;
    }

    public void invalidated() {
        loaded = false;
        restored();
    }

    public void unload() {
        loaded = false;
        if (qio != null) {
            qio.component.onChunkUnload();
            qio.dirty = true;
        }
        if (ae != null) {
            ae.component.onChunkUnload();
            ae.dirty = true;
        }
    }

    public void contentsChanged() {
        if (qio != null) qio.component.onContentsChanged();
        if (ae != null) ae.component.onContentsChanged();
    }

    public void neighborChanged() {
        if (!loaded) return; // No network work while the tile is still restoring or unloaded.
        if (qio != null) qio.component.onNeighborChanged();
        if (ae != null) ae.component.onNeighborChanged();
    }

    private static final class Entry {
        private final IUpgradeTileComponent component;
        private int count;
        private java.util.List<Change> changes;
        private boolean dirty = true;

        private Entry(IUpgradeTileComponent component, TileComponentUpgrade upgrades) {
            this.component = component;
            component.onUpgradesChanged(upgrades);
            count = component.getInstalledCount(upgrades);
        }

        private void refresh(TileComponentUpgrade upgrades, boolean installedChange) {
            int beforeCount = count;
            boolean beforeEnabled = component.isEnabled();
            component.onUpgradesChanged(upgrades);
            count = component.getInstalledCount(upgrades);
            if (!installedChange) {
                changes = null; // Restoration must not replay installation events.
            } else if (count != beforeCount || beforeEnabled != component.isEnabled()) {
                if (changes == null) changes = new java.util.ArrayList<>();
                changes.add(new Change(beforeCount, count, beforeEnabled, component.isEnabled()));
            }
            dirty = true;
        }

        private void tick() {
            if (dirty) {
                dirty = false;
                java.util.List<Change> committed = changes;
                changes = null;
                if (committed != null) {
                    for (Change change : committed) {
                        if (change.after > change.before) component.onAdded(change.before == 0);
                        if (change.after < change.before) component.onRemoved(change.after == 0);
                        if (change.wasEnabled != change.enabled) component.onEnabledStateChanged(change.enabled);
                    }
                }
                component.onServerStateChanged();
            }
            if (component.isEnabled()) component.tickServer();
            else if (component.needsRecoveryTick()) component.tickRecovery();
        }
    }

    private static final class Change {
        private final int before, after;
        private final boolean wasEnabled, enabled;

        private Change(int before, int after, boolean wasEnabled, boolean enabled) {
            this.before = before;
            this.after = after;
            this.wasEnabled = wasEnabled;
            this.enabled = enabled;
        }
    }
}
