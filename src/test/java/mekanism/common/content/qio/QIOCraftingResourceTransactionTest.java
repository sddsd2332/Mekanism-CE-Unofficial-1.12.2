package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingResourceTransactionTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private File worldDirectory;

    @AfterEach
    void cleanup() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void reservesAndRollsBackMixedItemFluidAndGasUnits() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-crafting-transaction").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestDriveItem driveItem = new TestDriveItem();
        TestHolder holder = new TestHolder(new ItemStack(driveItem));
        QIOFrequency frequency = new QIOFrequency("crafting", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        ItemStack item = new ItemStack(new Item());
        Fluid fluid = new Fluid("qio_crafting_tx_fluid", new ResourceLocation("minecraft", "blocks/water"),
              new ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(fluid);
        FluidStack fluidStack = new FluidStack(fluid, 1);
        GasStack gas = new GasStack(new Gas("qio_crafting_tx_gas", 0x336699), 1);
        assertEquals(20, frequency.massInsert(item, 20, Action.EXECUTE));
        assertEquals(500, frequency.massInsert(fluidStack, 500, Action.EXECUTE));
        assertEquals(700, frequency.massInsert(gas, 700, Action.EXECUTE));

        UUID itemId = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item));
        UUID fluidId = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(fluidStack);
        UUID gasId = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(gas);
        List<QIOCraftingTransferHelper.ResourceRequest> requests = Arrays.asList(
              new QIOCraftingTransferHelper.ResourceRequest(itemId, QIOResourceKind.ITEM, 10),
              new QIOCraftingTransferHelper.ResourceRequest(fluidId, QIOResourceKind.FLUID, 250),
              new QIOCraftingTransferHelper.ResourceRequest(gasId, QIOResourceKind.GAS, 300));
        QIOCraftingResourceTransaction transaction = new QIOCraftingResourceTransaction(frequency, requests);
        assertTrue(transaction.simulate());
        assertTrue(transaction.execute());
        assertEquals(10, frequency.getStored(item));
        assertEquals(250, frequency.getStored(fluidStack));
        assertEquals(400, frequency.getStored(gas));
        transaction.rollback();
        assertTrue(transaction.isRollbackComplete());
        assertEquals(20, frequency.getStored(item));
        assertEquals(500, frequency.getStored(fluidStack));
        assertEquals(700, frequency.getStored(gas));
    }

    @Test
    void incompleteRollbackRemainsAccountedAndCanBeRetried() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-crafting-transaction-retry").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestHolder holder = new TestHolder(new ItemStack(new TestDriveItem()));
        RetryRollbackFrequency frequency = new RetryRollbackFrequency();
        frequency.addHolder(holder);
        frequency.refresh();

        ItemStack item = new ItemStack(new Item());
        assertEquals(20, frequency.massInsert(item, 20, Action.EXECUTE));
        UUID itemId = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item));
        QIOCraftingResourceTransaction transaction = new QIOCraftingResourceTransaction(frequency,
              Collections.singletonList(new QIOCraftingTransferHelper.ResourceRequest(itemId, QIOResourceKind.ITEM, 10)));
        assertTrue(transaction.execute());

        frequency.setRollbackLimit(4);
        transaction.rollback();
        assertFalse(transaction.isRollbackComplete());
        assertEquals(6, transaction.getExtracted().get(itemId));
        assertEquals(14, frequency.getStored(item));

        frequency.setRollbackLimit(Long.MAX_VALUE);
        transaction.rollback();
        assertTrue(transaction.isRollbackComplete());
        assertTrue(transaction.getExtracted().isEmpty());
        assertEquals(20, frequency.getStored(item));
    }

    private static void delete(File file) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private static final class TestDriveItem extends Item implements IQIODriveItem {
        private TestDriveItem() {
            setMaxStackSize(1);
        }

        @Override
        public QIODriveTier getDriveTier() {
            return QIODriveTier.BASE;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {
        private final List<ItemStack> drives;

        private TestHolder(ItemStack drive) {
            drives = Collections.singletonList(drive);
        }

        @Override
        public int getQIODimension() {
            return 0;
        }

        @Override
        public BlockPos getQIOPosition() {
            return new BlockPos(0, 0, 0);
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return drives;
        }
    }

    private static final class RetryRollbackFrequency extends QIOFrequency {

        private long rollbackLimit = Long.MAX_VALUE;

        private RetryRollbackFrequency() {
            super("crafting-retry", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        }

        private void setRollbackLimit(long rollbackLimit) {
            this.rollbackLimit = rollbackLimit;
        }

        @Override
        public synchronized long massInsert(UUID resource, long amount, Action action) {
            return super.massInsert(resource, Math.min(amount, rollbackLimit), action);
        }
    }
}
