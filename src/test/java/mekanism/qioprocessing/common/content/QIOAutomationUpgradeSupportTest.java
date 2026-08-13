package mekanism.qioprocessing.common.content;

import mekanism.api.NBTConstants;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.TestBootstrap;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.factory.TileEntityBasicFactory;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.tile.prefab.MekanismMachineRecipeProviders;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationUpgradeSupportTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void reconcileRestoresModeAfterUpgradeComponentNbtLoadsWithoutCallbacks() {
        TestUpgradeTile tile = new TestUpgradeTile();
        tile.loadUpgradeSilently(QIOProcessingUpgrades.QIO_AUTO_CRAFTING);

        assertNull(tile.host.getEnabledMode());
        QIOAutomationUpgradeSupport.reconcile(tile, tile.host);

        assertEquals(QIOAutomationMode.SCHEDULED, tile.host.getEnabledMode());
        assertEquals(QIOAutomationHost.State.UNBOUND, tile.host.getState());
    }

    @Test
    void reconcileClearsAStaleModeWhenItsPhysicalUpgradeIsMissing() {
        TestUpgradeTile tile = new TestUpgradeTile();
        assertTrue(tile.host.selectMode(QIOAutomationMode.PASSIVE));

        QIOAutomationUpgradeSupport.reconcile(tile, tile.host);

        assertNull(tile.host.getEnabledMode());
        assertNull(tile.host.getFrequencyReference());
        assertEquals(QIOAutomationHost.State.UNBOUND, tile.host.getState());
    }

    @Test
    void reconcilePreservesDetachedOperationsWhileAnUpgradeRemovalDrains() {
        TestUpgradeTile tile = new TestUpgradeTile();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "draining", null, SecurityMode.PUBLIC, UUID.randomUUID());
        assertTrue(tile.host.configureBinding(frequency, QIOAutomationMode.PASSIVE, true));
        MachinePortBaseline baseline = new MachinePortBaseline("input", "input",
              MachineResourceKind.ITEM, null);
        MachineOperationLease lease = tile.host.tryAcquireLease(UUID.randomUUID(),
              UUID.randomUUID(), MachineOperationLease.Mode.PROCESSING_EXCLUSIVE,
              0, 1, Collections.singletonList(baseline));
        assertNotNull(lease);
        assertTrue(tile.host.clearMode(true));

        QIOAutomationUpgradeSupport.reconcile(tile, tile.host);

        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, tile.host.getState());
        assertEquals(QIOAutomationMode.PASSIVE, tile.host.getEnabledMode());
        assertNull(tile.host.getFrequencyReference());
        assertFalse(QIOAutomationUpgradeSupport.canInstall(tile, tile,
              QIOAutomationMode.PASSIVE));
    }

    @Test
    void uninstallCallbackClearsBindingAndAllowsAnotherAutomationMode() {
        TestUpgradeTile tile = new TestUpgradeTile();
        assertEquals(1, tile.upgrades.setUpgrades(
              QIOProcessingUpgrades.QIO_AUTO_PROCESSING, 1));
        assertEquals(QIOAutomationMode.PASSIVE, tile.host.getEnabledMode());
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "upgrade-removal", null, SecurityMode.PUBLIC, UUID.randomUUID());
        assertTrue(tile.host.configureBinding(frequency, QIOAutomationMode.PASSIVE, true));

        assertEquals(0, tile.upgrades.setUpgrades(
              QIOProcessingUpgrades.QIO_AUTO_PROCESSING, 0));
        assertNull(tile.host.getEnabledMode());
        assertNull(tile.host.getFrequencyReference());
        NBTTagCompound cleared = tile.host.serializeNBT();
        assertFalse(cleared.hasKey("enabledMode"));
        assertFalse(cleared.hasKey("frequency"));

        assertEquals(1, tile.upgrades.setUpgrades(
              QIOProcessingUpgrades.QIO_AUTO_CRAFTING, 1));
        assertEquals(QIOAutomationMode.SCHEDULED, tile.host.getEnabledMode());
    }

    @Test
    void factoryCanAcceptUpgradeBeforeRuntimeRecipeRoutesAreAvailable() {
        MekanismMachineRecipeProviders.register();
        EmptyRouteFactory tile = new EmptyRouteFactory();

        assertTrue(QIOAutomationUpgradeSupport.canInstall(tile, tile, QIOAutomationMode.SCHEDULED));
    }

    private static final class TestUpgradeTile extends TileEntityContainerBlock
          implements IUpgradeTile {

        private final TileComponentUpgrade upgrades = new TileComponentUpgrade(this);
        private final DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(this);

        private TestUpgradeTile() {
            super("qio_automation_upgrade_test");
            upgrades.setSupported(QIOProcessingUpgrades.QIO_AUTO_CRAFTING,
                  QIOProcessingUpgrades.QIO_AUTO_PROCESSING,
                  QIOProcessingUpgrades.QIO_AUTO_OUTPUT);
        }

        private void loadUpgradeSilently(Upgrade upgrade) {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagCompound stored = new NBTTagCompound();
            Upgrade.saveMap(Collections.singletonMap(upgrade, 1), stored);
            root.setTag(NBTConstants.COMPONENT_UPGRADE, stored);
            upgrades.read(root, false);
        }

        @Override
        public TileComponentUpgrade getComponent() {
            return upgrades;
        }

        @Override
        public int getBlockGuiID(Block block, int metadata) {
            return -1;
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability,
              @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ||
                  super.hasCapability(capability, side);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCapability(@Nonnull Capability<T> capability,
              @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ?
                  (T) host : super.getCapability(capability, side);
        }
    }

    private static final class EmptyRouteFactory extends TileEntityBasicFactory {

        private final DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(this);

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST || super.hasCapability(capability, side);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ?
                  (T) host : super.getCapability(capability, side);
        }
    }
}
