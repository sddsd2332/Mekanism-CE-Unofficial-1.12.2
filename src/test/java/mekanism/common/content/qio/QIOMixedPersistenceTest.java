package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOMixedPersistenceTest {

    private File worldDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @AfterEach
    void cleanUp() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void mixedResourcesAcrossTwoDrivesSurviveBothStorageRestarts() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-mixed-persistence-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        ItemStack item = new ItemStack(Blocks.STONE);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("variant", "persistent");
        item.setTagCompound(itemTag);
        Fluid fluid = registerFluid();
        FluidStack fluidStack = new FluidStack(fluid, 1);
        fluidStack.tag = new NBTTagCompound();
        fluidStack.tag.setInteger("temperature", 725);
        Gas gas = GasRegistry.register(new Gas("qio_mixed_persistence_gas_" + UUID.randomUUID(), 0x66CCAA));
        GasStack gasStack = new GasStack(gas, 1);

        UUID itemResource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(HashedItem.create(item));
        UUID fluidResource = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(fluidStack);
        UUID gasResource = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(gasStack);
        UUID firstDrive = UUID.randomUUID();
        UUID secondDrive = UUID.randomUUID();
        QIODriveRecord first = QIODriveStorage.INSTANCE.getOrCreate(firstDrive, QIODriveTier.SUPERMASSIVE);
        QIODriveRecord second = QIODriveStorage.INSTANCE.getOrCreate(secondDrive, QIODriveTier.SUPERMASSIVE);
        assertNotNull(first);
        assertNotNull(second);
        insert(first, itemResource, 40);
        insert(first, fluidResource, 5_000);
        insert(first, gasResource, 3_000);
        insert(second, itemResource, 24);
        insert(second, fluidResource, 7_000);
        insert(second, gasResource, 6_000);
        QIODriveStorage.INSTANCE.markDriveDirty(firstDrive);
        QIODriveStorage.INSTANCE.markDriveDirty(secondDrive);
        QIOResourceTypeRegistry.INSTANCE.flush();
        QIODriveStorage.INSTANCE.flush();

        assertTrue(new File(worldDirectory, "mekanism/qio/resource_types/" + itemResource + ".dat").isFile());
        assertTrue(new File(worldDirectory, "mekanism/qio/drives/" + firstDrive + ".dat").isFile());
        assertTrue(new File(worldDirectory, "mekanism/qio/drives/" + secondDrive + ".dat").isFile());

        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        assertEquals(itemResource, QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(item)));
        assertEquals(fluidResource, QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(fluidStack));
        assertEquals(gasResource, QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(gasStack));
        assertEquals(48, QIODriveStorage.INSTANCE.get(firstDrive).getTotalCount());
        assertEquals(37, QIODriveStorage.INSTANCE.get(secondDrive).getTotalCount());
        assertEquals(48_000, QIODriveStorage.INSTANCE.get(firstDrive).getTotalStorageUnits());
        assertEquals(37_000, QIODriveStorage.INSTANCE.get(secondDrive).getTotalStorageUnits());

        TestDriveItem driveItem = new TestDriveItem();
        ItemStack firstStack = new ItemStack(driveItem);
        ItemStack secondStack = new ItemStack(driveItem);
        driveItem.setDriveId(firstStack, firstDrive);
        driveItem.setDriveId(secondStack, secondDrive);
        QIOFrequency frequency = new QIOFrequency("persistent", null, SecurityMode.PUBLIC);
        frequency.addHolder(new TestHolder(Arrays.asList(firstStack, secondStack)));
        frequency.refresh();

        assertEquals(64, frequency.getStored(item));
        assertEquals(12_000, frequency.getStored(fluidStack));
        assertEquals(9_000, frequency.getStored(gasStack));
        assertEquals(85, frequency.getTotalCount());
        assertEquals(3, frequency.getTotalTypes());
        assertEquals(64, frequency.getStoredCount(QIOResourceKind.ITEM));
        assertEquals(12_000, frequency.getStoredCount(QIOResourceKind.FLUID));
        assertEquals(9_000, frequency.getStoredCount(QIOResourceKind.GAS));
    }

    private static void insert(QIODriveRecord record, UUID resource, long amount) {
        assertEquals(amount, record.insert(resource, amount, Action.EXECUTE));
    }

    private static Fluid registerFluid() {
        Fluid fluid = new Fluid("qio_mixed_persistence_fluid_" + UUID.randomUUID(),
              new ResourceLocation("minecraft", "blocks/water"), new ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(fluid);
        return fluid;
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

        @Override
        public QIODriveTier getDriveTier() {
            return QIODriveTier.SUPERMASSIVE;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {

        private final List<ItemStack> drives;

        private TestHolder(List<ItemStack> drives) {
            this.drives = drives;
        }

        @Override
        public int getQIODimension() {
            return 0;
        }

        @Override
        public BlockPos getQIOPosition() {
            return BlockPos.ORIGIN;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return drives;
        }
    }
}
