package mekanism.qioprocessing.common.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.base.IUpgradeTileComponent;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Tile-owned QIO state. The capability is only its API and legacy persistence facade.
 */
public final class QIOUpgradeComponent implements IUpgradeTileComponent {
    private final TileEntity tile;
    private final DefaultQIOAutomationHost host;
    private volatile boolean enabled;
    private QIOAutomationMode installedMode;
    private World lastWorld;
    private long lastTick = Long.MIN_VALUE;
    private volatile boolean recoveryPending;

    QIOUpgradeComponent(TileEntity tile) {
        this.tile = tile;
        host = new DefaultQIOAutomationHost(tile);
        host.upgradeComponent = this;
    }

    public static IUpgradeTileComponent find(TileEntityBasicBlock tile) {
        if (QIOAutomationCapabilities.AUTOMATION_HOST == null) return null;
        QIOAutomationHost exposed = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        return exposed instanceof DefaultQIOAutomationHost mutable ? mutable.upgradeComponent : null;
    }

    public DefaultQIOAutomationHost host() {
        return host;
    }

    @Override
    public int getInstalledCount(TileComponentUpgrade upgrades) {
        return upgrades.getUpgrades(QIOProcessingUpgrades.QIO_AUTO_CRAFTING)
                + upgrades.getUpgrades(QIOProcessingUpgrades.QIO_AUTO_PROCESSING)
                + upgrades.getUpgrades(QIOProcessingUpgrades.QIO_AUTO_OUTPUT);
    }

    @Override
    public void onUpgradesChanged(TileComponentUpgrade upgrades) {
        installedMode = upgrades.getUpgrades(QIOProcessingUpgrades.QIO_AUTO_CRAFTING) > 0 ? QIOAutomationMode.SCHEDULED
                : upgrades.getUpgrades(QIOProcessingUpgrades.QIO_AUTO_PROCESSING) > 0 ? QIOAutomationMode.PASSIVE
                : upgrades.getUpgrades(QIOProcessingUpgrades.QIO_AUTO_OUTPUT) > 0 ? QIOAutomationMode.OUTPUT_ONLY : null;
        enabled = installedMode != null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onServerReady() {
        lastWorld = null;
        lastTick = Long.MIN_VALUE;
        QIOAutomationDeviceRegistry.INSTANCE.trackPending(host);
    }

    @Override
    public void onAdded(boolean first) {
        if (installedMode != null && !(host.hasRecoveryPending() && host.rearmAfterUpgrade(installedMode))) {
            host.selectMode(installedMode);
        }
    }

    @Override
    public void onRemoved(boolean last) {
        if (last) {
            host.clearMode();
        }
    }

    @Override
    public void onServerStateChanged() {
        QIOAutomationUpgradeSupport.reconcile(tile, host);
        recoveryPending = host.hasPotentialExternalOwnership();
        QIOAutomationDeviceRegistry.INSTANCE.trackPending(host);
    }

    @Override
    public void tickServer() {
        World world = tile.getWorld();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        long tick = world.getTotalWorldTime();
        if (lastWorld == world && lastTick == tick) return;
        lastWorld = world;
        lastTick = tick;
        QIOAutomationTileTickService.INSTANCE.tick(host, tile, tick);
    }

    @Override
    public boolean needsRecoveryTick() {
        return recoveryPending;
    }

    @Override
    public void tickRecovery() {
        // Keep old ownership routable while disabled, without acquiring any fresh output/input lease.
        QIOAutomationUpgradeSupport.reconcile(tile, host);
        try {
            if (tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid()
                    && host.getRecoveryState() != QIOAutomationHost.RecoveryState.QUARANTINED) {
                if (host.getEnabledMode() == QIOAutomationMode.OUTPUT_ONLY) {
                    QIOAutomaticOutputService.INSTANCE.tickRecoveryDevice(host, tile, tile.getWorld().getTotalWorldTime());
                } else if (host.getEnabledMode() != null) {
                    QIOProcessingExecutionService.INSTANCE.onMachinePreComponentTick(host);
                }
            }
        } catch (RuntimeException failure) {
            host.markRecoveryPending("Disabled component recovery failed: " + failure.getClass().getSimpleName());
            mekanism.common.Mekanism.logger.error("QIO disabled component retained ownership after recovery failure for {}",
                    host.getPersistentDeviceUUID(), failure);
        }
        recoveryPending = host.hasPotentialExternalOwnership();
    }

    @Override
    public void onContentsChanged() {
        if (enabled || recoveryPending) QIOAutomationDeviceRegistry.INSTANCE.notifyContentsChanged(host);
    }

    @Override
    public void invalidate() {
        lastWorld = null;
        lastTick = Long.MIN_VALUE;
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        QIOProcessingExecutionService.INSTANCE.clearPreEjectionObservation(host);
        QIOAutomationDeviceRegistry.INSTANCE.unregister(host);
    }

    public NBTTagCompound serializeCapabilityNBT() {
        return host.shouldPersistCapabilityNbt() ? host.serializeNBT() : new NBTTagCompound();
    }

    public void deserializeCapabilityNBT(NBTTagCompound nbt) {
        host.deserializeNBT(nbt);
    }

    // Keep exactly one authoritative NBT envelope: ForgeCaps/mekanismqioprocessing:automation_host.
    // Adding a second tile-root copy would let an old/empty copy overwrite restored transactions.
    @Override
    public void read(NBTTagCompound nbt) {
    }

    @Override
    public void write(NBTTagCompound nbt) {
    }

    @Override
    public void read(ByteBuf data) {
    }

    @Override
    public void write(TileNetworkList data) {
    }
}
