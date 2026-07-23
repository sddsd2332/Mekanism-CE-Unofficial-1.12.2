package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.item.ItemQIODrive;
import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.RecipeUtils;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class QIOFrequencyTest {

    private static final QIODriveDefinition SMALL_DEFINITION = QIODriveDefinition.builder("qio_frequency_test", "small")
          .baseTier(BaseTier.BASIC)
          .maxCount(1_000L)
          .maxTypes(10)
          .register();
    private static final QIODriveDefinition LARGE_DEFINITION = QIODriveDefinition.builder("qio_frequency_test", "large")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(2_000L)
          .maxTypes(20)
          .register();
    private static final QIODriveDefinition CREATIVE_DEFINITION = QIODriveDefinition.builder("qio_frequency_test", "creative")
          .baseTier(BaseTier.ULTIMATE)
          .creativeCapacity()
          .register();
    private static final QIODriveDefinition MAX_FINITE_DEFINITION = QIODriveDefinition.builder("qio_frequency_test", "max_finite")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(Long.MAX_VALUE / QIOStorageUnits.UNITS_PER_ITEM)
          .maxTypes(1)
          .register();
    private static final QIODriveDefinition EXPANDED_FINITE_DEFINITION = QIODriveDefinition.builder("qio_frequency_test", "expanded_finite")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(Long.MAX_VALUE - 1)
          .maxTypes(4)
          .register();

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    private File worldDirectory;

    @AfterEach
    void cleanUp() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void aggregatesItemsAndGasesUsingFixedStorageUnits() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        ItemStack drive = new ItemStack(driveItem);
        TestHolder holder = new TestHolder(Collections.singletonList(drive), 0, new BlockPos(1, 2, 3));
        QIOFrequency frequency = new QIOFrequency("test", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        ItemStack item = new ItemStack(new Item());
        GasStack gas = new GasStack(new Gas("qio_test_gas", 0xFFFFFF), 1);
        assertEquals(100, frequency.massInsert(item, 100, Action.EXECUTE));
        assertEquals(250, frequency.massInsert(gas, 250, Action.EXECUTE));
        assertEquals(100, frequency.getStored(item));
        assertEquals(250, frequency.getStored(gas));
        assertEquals(101, frequency.getTotalCount());
        assertEquals(2, frequency.getTotalTypes());
        assertEquals(100, frequency.massExtract(item, 100, Action.EXECUTE));
        assertEquals(250, frequency.getStored(gas));
    }

    @Test
    void duplicateDriveIsNotCountedTwice() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-duplicate-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        ItemStack first = new ItemStack(driveItem);
        QIODriveData.initialize(first);
        ItemStack second = first.copy();
        TestHolder firstHolder = new TestHolder(Collections.singletonList(first), 0, new BlockPos(1, 2, 3));
        TestHolder secondHolder = new TestHolder(Collections.singletonList(second), 0, new BlockPos(4, 5, 6));
        QIOFrequency frequency = new QIOFrequency("test", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(firstHolder);
        frequency.addHolder(secondHolder);
        frequency.refresh();

        assertEquals(1, frequency.getSlotStates().values().stream().filter(state -> state == QIODriveSlotState.ACTIVE).count());
        assertEquals(1, frequency.getSlotStates().values().stream().filter(state -> state == QIODriveSlotState.DUPLICATE_UUID).count());
        assertEquals(QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE), frequency.getTotalCountCapacity());
    }

    @Test
    void laterLargerDuplicateCannotUpgradeTheActiveRecord() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-capacity-duplicate-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        ItemQIODrive smallItem = new ItemQIODrive(SMALL_DEFINITION, QIODriveType.MIXED);
        ItemQIODrive largeItem = new ItemQIODrive(LARGE_DEFINITION, QIODriveType.MIXED);
        ItemStack smallDrive = new ItemStack(smallItem);
        assertEquals(true, QIODriveData.initialize(smallDrive));
        UUID driveId = smallItem.getDriveId(smallDrive);
        ItemStack largeCopy = new ItemStack(largeItem);
        largeItem.setDriveId(largeCopy, driveId);

        TestHolder first = new TestHolder(Collections.singletonList(smallDrive), 0, new BlockPos(1, 0, 0));
        TestHolder duplicate = new TestHolder(Collections.singletonList(largeCopy), 0, new BlockPos(10, 0, 0));
        QIOFrequency frequency = new QIOFrequency("capacity-duplicate", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(first);
        frequency.refresh();

        assertEquals(true, QIODriveData.initialize(largeCopy));
        assertSame(SMALL_DEFINITION, QIODriveStorage.INSTANCE.get(driveId).getDefinition());
        frequency.addHolder(duplicate);
        frequency.refresh();

        assertEquals(QIODriveSlotState.ACTIVE, frequency.getSlotState(new QIODriveMount(first, 0)));
        assertEquals(QIODriveSlotState.DUPLICATE_UUID, frequency.getSlotState(new QIODriveMount(duplicate, 0)));
        assertSame(SMALL_DEFINITION, QIODriveStorage.INSTANCE.get(driveId).getDefinition());
        assertEquals(QIODriveType.MIXED.getCountCapacity(SMALL_DEFINITION), frequency.getTotalCountCapacity());
        assertEquals(first, QIODriveStorage.INSTANCE.getActiveMount(driveId).getHolder());
    }

    @Test
    void earlierLargerDriveUpgradesOnlyAfterWinningAndInvalidatesOtherFrequency() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-capacity-handoff-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        ItemQIODrive smallItem = new ItemQIODrive(SMALL_DEFINITION, QIODriveType.MIXED);
        ItemQIODrive largeItem = new ItemQIODrive(LARGE_DEFINITION, QIODriveType.MIXED);
        ItemStack smallDrive = new ItemStack(smallItem);
        assertEquals(true, QIODriveData.initialize(smallDrive));
        UUID driveId = smallItem.getDriveId(smallDrive);
        ItemStack largeCopy = new ItemStack(largeItem);
        largeItem.setDriveId(largeCopy, driveId);

        TestHolder later = new TestHolder(Collections.singletonList(smallDrive), 0, new BlockPos(10, 0, 0));
        TestHolder earlier = new TestHolder(Collections.singletonList(largeCopy), 0, new BlockPos(1, 0, 0));
        QIOFrequency laterFrequency = new QIOFrequency("capacity-later", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        QIOFrequency earlierFrequency = new QIOFrequency("capacity-earlier", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        laterFrequency.addHolder(later);
        laterFrequency.refresh();

        assertEquals(true, QIODriveData.initialize(largeCopy));
        assertSame(SMALL_DEFINITION, QIODriveStorage.INSTANCE.get(driveId).getDefinition());
        earlierFrequency.addHolder(earlier);
        earlierFrequency.refresh();

        assertSame(LARGE_DEFINITION, QIODriveStorage.INSTANCE.get(driveId).getDefinition());
        assertEquals(QIODriveType.MIXED.getCountCapacity(LARGE_DEFINITION), earlierFrequency.getTotalCountCapacity());
        assertEquals(0, laterFrequency.getTotalCountCapacity());
        assertEquals(QIODriveSlotState.DUPLICATE_UUID,
              laterFrequency.getSlotState(new QIODriveMount(later, 0)));
        assertEquals(earlier, QIODriveStorage.INSTANCE.getActiveMount(driveId).getHolder());
    }

    @Test
    void fluidsAndGasesUseDistinctResourceTypes() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-fluid-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0, new BlockPos(1, 2, 3));
        QIOFrequency frequency = new QIOFrequency("fluid", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        Fluid fluid = new Fluid("qio_frequency_test_fluid", new net.minecraft.util.ResourceLocation("minecraft", "blocks/water"),
              new net.minecraft.util.ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(fluid);
        FluidStack stack = new FluidStack(fluid, 1_000);
        GasStack gas = new GasStack(new Gas("qio_frequency_fluid_test_gas", 0xAABBCC), 1);
        assertEquals(1_000, frequency.massInsert(stack, 1_000, Action.EXECUTE));
        assertEquals(500, frequency.massInsert(gas, 500, Action.EXECUTE));
        assertEquals(1_000, frequency.getStored(stack));
        assertEquals(500, frequency.getStored(gas));
        assertEquals(2, frequency.getTotalCount());
        assertEquals(2, frequency.getTotalTypes());
    }

    @Test
    void partialFluidAndGasAmountsShareOneItemEquivalent() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-ratio-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(new TestDriveItem(QIODriveTier.BASE))),
              0, new BlockPos(1, 2, 3));
        QIOFrequency frequency = new QIOFrequency("ratio", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1);
        GasStack gas = new GasStack(new Gas("qio_ratio_test_gas", 0x44CCFF), 1);
        assertEquals(500, frequency.massInsert(fluid, 500, Action.EXECUTE));
        assertEquals(500, frequency.massInsert(gas, 500, Action.EXECUTE));
        assertEquals(1, frequency.getTotalCount());

        assertEquals(1, frequency.massInsert(new ItemStack(Blocks.STONE), 1, Action.EXECUTE));
        QIODriveRecord record = frequency.getDriveData(new QIODriveMount(holder, 0)).getRecord();
        assertEquals(2_000, record.getTotalStorageUnits());
        assertEquals(2, record.getTotalCount());
        assertEquals(record.getStorageCapacity() - record.getTotalStorageUnits(),
              frequency.massInsert(fluid, Long.MAX_VALUE, Action.SIMULATE));
    }

    @Test
    void simulatedInsertAndExtractDoNotDoubleCountAcrossDrives() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-simulation-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        TestHolder holder = new TestHolder(Arrays.asList(new ItemStack(driveItem), new ItemStack(driveItem)), 0, new BlockPos(1, 2, 3));
        QIOFrequency frequency = new QIOFrequency("simulation", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        ItemStack item = new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE));
        long capacity = QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE) * 2L;
        assertEquals(capacity, frequency.massInsert(item, Long.MAX_VALUE, Action.SIMULATE));
        assertEquals(capacity, frequency.massInsert(item, capacity, Action.EXECUTE));
        assertEquals(capacity, frequency.massExtract(item, Long.MAX_VALUE, Action.SIMULATE));
    }

    @Test
    void multipleCreativeDrivesKeepOneExactResourceEntryAndRebuildAfterRemoval() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-creative-overflow-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        ItemQIODrive driveItem = new ItemQIODrive(CREATIVE_DEFINITION, QIODriveType.MIXED);
        java.util.ArrayList<ItemStack> drives = new java.util.ArrayList<>(Arrays.asList(
              new ItemStack(driveItem), new ItemStack(driveItem)));
        TestHolder holder = new TestHolder(drives, 0, new BlockPos(1, 2, 3));
        QIOFrequency frequency = new QIOFrequency("creative-overflow", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        ItemStack item = new ItemStack(Blocks.STONE);
        assertEquals(Long.MAX_VALUE, frequency.massInsert(item, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(100, frequency.massInsert(item, 100, Action.SIMULATE));
        assertEquals(100, frequency.massInsert(item, 100, Action.EXECUTE));
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100));
        assertEquals(Long.MAX_VALUE, frequency.getStored(item));
        assertEquals(expected, frequency.getStoredExact(
              QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item))).toBigInteger());
        assertEquals(1, frequency.getResourceEntries().size());
        assertEquals(expected, frequency.getResourceEntries().get(0).getExactAmount().toBigInteger());
        assertEquals(2, frequency.getUnlimitedCountDriveCount());
        assertEquals(2, frequency.getUnlimitedTypeDriveCount());
        assertEquals(Long.MAX_VALUE, frequency.getTotalCountCapacity());
        assertEquals(Integer.MAX_VALUE, frequency.getTotalTypeCapacity());
        assertEquals(Long.MAX_VALUE, frequency.massExtract(item, Long.MAX_VALUE, Action.SIMULATE));
        assertEquals(Long.MAX_VALUE, frequency.massExtract(item, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(100, frequency.getStored(item));

        drives.set(0, ItemStack.EMPTY);
        frequency.requestRefresh();
        frequency.refresh();
        assertEquals(100, frequency.getStored(item));
        assertEquals(BigInteger.valueOf(100), frequency.getResourceEntries().get(0).getExactAmount().toBigInteger());
        assertEquals(1, frequency.getUnlimitedCountDriveCount());
        assertEquals(Long.MAX_VALUE, frequency.getTotalCountCapacity());
    }

    @Test
    void oneCreativeMixedDriveUsesThreeLongMaxPhysicalCapacity() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-creative-mixed-capacity-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        ItemQIODrive driveItem = new ItemQIODrive(CREATIVE_DEFINITION, QIODriveType.MIXED);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0,
              new BlockPos(4, 5, 6));
        QIOFrequency frequency = new QIOFrequency("creative-mixed-capacity", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        BigInteger expectedCapacity = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3));
        QIODriveData drive = frequency.getDriveData(new QIODriveMount(holder, 0));
        assertNotNull(drive);
        assertEquals(expectedCapacity, drive.getRecord().getExactCountCapacity().toBigInteger());
        assertEquals(Long.MAX_VALUE, frequency.massInsert(new ItemStack(Blocks.STONE), Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(Long.MAX_VALUE, frequency.massInsert(new ItemStack(Blocks.DIRT), Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(Long.MAX_VALUE, frequency.massInsert(new ItemStack(Blocks.COBBLESTONE), Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(0, frequency.massInsert(new ItemStack(Blocks.SAND), 1, Action.SIMULATE));
        assertEquals(0, frequency.massInsert(new ItemStack(Blocks.SAND), 1, Action.EXECUTE));
        assertEquals(expectedCapacity, frequency.getExactTotalCount().toBigInteger());
        assertEquals(3, frequency.getTotalTypes());
    }

    @Test
    void expandedFiniteMixedCapacityRemainsFiniteInFrequencySummary() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-expanded-finite-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        ItemQIODrive driveItem = new ItemQIODrive(EXPANDED_FINITE_DEFINITION, QIODriveType.MIXED);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0,
              new BlockPos(7, 8, 9));
        QIOFrequency frequency = new QIOFrequency("expanded-finite", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE - 1).multiply(BigInteger.valueOf(3));
        assertEquals(expected, frequency.getExactCountCapacity().toBigInteger());
        assertEquals(Long.MAX_VALUE, frequency.getTotalCountCapacity());
        assertEquals(0, frequency.getUnlimitedCountDriveCount());
        assertEquals(false, frequency.getCapacitySummary().hasUnlimitedCount());
    }

    @Test
    void manyFiniteDrivesExposeAnExactCapacityAfterTheLegacyLongSaturates() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-finite-capacity-overflow-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        ItemQIODrive driveItem = new ItemQIODrive(MAX_FINITE_DEFINITION, QIODriveType.ITEM);
        java.util.ArrayList<ItemStack> drives = new java.util.ArrayList<>();
        for (int i = 0; i < 1_001; i++) {
            drives.add(new ItemStack(driveItem));
        }
        TestHolder holder = new TestHolder(drives, 0, new BlockPos(3, 4, 5));
        QIOFrequency frequency = new QIOFrequency("finite-overflow", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        BigInteger expected = BigInteger.valueOf(MAX_FINITE_DEFINITION.getMaxCount()).multiply(BigInteger.valueOf(1_001));
        assertEquals(expected, frequency.getExactCountCapacity().toBigInteger());
        assertEquals(Long.MAX_VALUE, frequency.getTotalCountCapacity());
        assertEquals(0, frequency.getUnlimitedCountDriveCount());
        assertEquals(BigInteger.valueOf(1_001), frequency.getExactTypeCapacity().toBigInteger());

        drives.subList(1, drives.size()).clear();
        frequency.requestRefresh();
        frequency.refresh();
        assertEquals(BigInteger.valueOf(MAX_FINITE_DEFINITION.getMaxCount()),
              frequency.getExactCountCapacity().toBigInteger());
        assertEquals(MAX_FINITE_DEFINITION.getMaxCount(), frequency.getTotalCountCapacity());
    }

    @Test
    void lowerWorldPositionWinsAcrossFrequenciesAndDuplicateTakesOver() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-world-lock-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        ItemStack highDrive = new ItemStack(driveItem);
        QIODriveData.initialize(highDrive);
        ItemStack lowDrive = highDrive.copy();
        TestHolder high = new TestHolder(Collections.singletonList(highDrive), 0, new BlockPos(10, 0, 0));
        TestHolder low = new TestHolder(Collections.singletonList(lowDrive), 0, new BlockPos(1, 0, 0));
        QIOFrequency highFrequency = new QIOFrequency("high", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        QIOFrequency lowFrequency = new QIOFrequency("low", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);

        highFrequency.addHolder(high);
        highFrequency.refresh();
        lowFrequency.addHolder(low);
        lowFrequency.refresh();
        highFrequency.tick(true);

        assertEquals(QIODriveSlotState.DUPLICATE_UUID, highFrequency.getSlotStates().values().iterator().next());
        assertEquals(QIODriveSlotState.ACTIVE, lowFrequency.getSlotStates().values().iterator().next());

        lowFrequency.removeHolder(low);
        highFrequency.tick(true);
        assertEquals(QIODriveSlotState.ACTIVE, highFrequency.getSlotStates().values().iterator().next());
    }

    @Test
    void unchangedFrequenciesDoNotChurnTheGlobalMountRevision() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-stable-mount-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        TestHolder firstHolder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0, new BlockPos(1, 0, 0));
        TestHolder secondHolder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0, new BlockPos(2, 0, 0));
        QIOFrequency first = new QIOFrequency("first", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        QIOFrequency second = new QIOFrequency("second", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        first.addHolder(firstHolder);
        second.addHolder(secondHolder);
        first.refresh();
        second.refresh();
        long mountedRevision = QIODriveStorage.INSTANCE.getMountRevision();

        first.requestRefresh();
        first.refresh();
        second.requestRefresh();
        second.refresh();
        assertEquals(mountedRevision, QIODriveStorage.INSTANCE.getMountRevision());

        assertEquals(8, first.massInsert(new ItemStack(Blocks.STONE), 8, Action.EXECUTE));
        assertEquals(mountedRevision, QIODriveStorage.INSTANCE.getMountRevision());
        first.tick(true);
        second.tick(true);
        assertEquals(mountedRevision, QIODriveStorage.INSTANCE.getMountRevision());
        assertEquals(QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE), first.getTotalCountCapacity());
        assertEquals(QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE), second.getTotalCountCapacity());
    }

    @Test
    void unloadedHolderReleasesCapacityAndDuplicateMountLock() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-holder-unload-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        ItemStack firstDrive = new ItemStack(driveItem);
        QIODriveData.initialize(firstDrive);
        ItemStack duplicateDrive = firstDrive.copy();
        TestHolder first = new TestHolder(Collections.singletonList(firstDrive), 0, new BlockPos(1, 0, 0));
        TestHolder duplicate = new TestHolder(Collections.singletonList(duplicateDrive), 0, new BlockPos(10, 0, 0));
        QIOFrequency frequency = new QIOFrequency("unload", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(first);
        frequency.addHolder(duplicate);
        frequency.refresh();

        assertEquals(QIODriveSlotState.ACTIVE, frequency.getSlotState(new QIODriveMount(first, 0)));
        assertEquals(QIODriveSlotState.DUPLICATE_UUID, frequency.getSlotState(new QIODriveMount(duplicate, 0)));
        assertEquals(QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE), frequency.getTotalCountCapacity());

        frequency.removeHolder(first);
        frequency.refresh();
        assertEquals(QIODriveSlotState.ACTIVE, frequency.getSlotState(new QIODriveMount(duplicate, 0)));
        assertEquals(QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE), frequency.getTotalCountCapacity());

        frequency.removeHolder(duplicate);
        frequency.refresh();
        assertEquals(0, frequency.getTotalCountCapacity());
        assertEquals(0, frequency.getTotalTypeCapacity());
    }

    @Test
    void driveIdentityAndMetadataAreWrittenBackToDefensiveCopyHolder() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-stack-writeback-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        CopyingHolder holder = new CopyingHolder(new ItemStack(driveItem), 0, new BlockPos(1, 2, 3));
        QIOFrequency frequency = new QIOFrequency("test", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        assertNotNull(driveItem.getDriveId(holder.getActualDrive()));
        assertEquals(32, frequency.massInsert(new ItemStack(new Item()), 32, Action.EXECUTE));
        assertEquals(32, driveItem.getDriveMetadata(holder.getActualDrive()).getCount());
        assertEquals(1, driveItem.getDriveMetadata(holder.getActualDrive()).getTypes());
    }

    @Test
    void uuidLookupRefreshesPendingDriveChanges() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-uuid-refresh-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0, new BlockPos(2, 3, 4));
        QIOFrequency frequency = new QIOFrequency("refresh", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        QIODriveData drive = frequency.getDriveData(new QIODriveMount(holder, 0));
        assertNotNull(drive);

        ItemStack item = new ItemStack(new Item());
        assertEquals(12, drive.insert(item, 12, Action.EXECUTE));
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item));
        assertNotNull(resource);
        assertEquals(12, frequency.getStored(resource));
    }

    @Test
    void batchSimulationConsumesSharedCountCapacity() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-batch-simulation-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0, new BlockPos(7, 8, 9));
        QIOFrequency frequency = new QIOFrequency("batch", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        ItemStack stored = new ItemStack(new Item());
        long capacity = QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE);
        assertEquals(capacity - 5, frequency.massInsert(stored, capacity - 5, Action.EXECUTE));

        ItemStack first = new ItemStack(new Item(), 4);
        ItemStack second = new ItemStack(new Item(), 4);
        assertEquals(false, frequency.canInsertAllItems(Arrays.asList(first, second)));
        first.setCount(2);
        second.setCount(3);
        assertEquals(true, frequency.canInsertAllItems(Arrays.asList(first, second)));
    }

    @Test
    void specializedDrivesOnlyAcceptTheirResourceKind() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-specialized-drive-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem itemDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.ITEM);
        TestDriveItem fluidDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.FLUID);
        TestDriveItem gasDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.GAS);
        TestHolder holder = new TestHolder(Arrays.asList(new ItemStack(itemDrive), new ItemStack(fluidDrive),
              new ItemStack(gasDrive)), 0, new BlockPos(3, 4, 5));
        QIOFrequency frequency = new QIOFrequency("specialized", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        ItemStack item = new ItemStack(Blocks.STONE);
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1);
        GasStack gas = new GasStack(new Gas("qio_specialized_test_gas", 0x66CCFF), 1);
        assertEquals(QIODriveTier.BASE.getMaxCount(), frequency.massInsert(item, Long.MAX_VALUE, Action.SIMULATE));
        assertEquals(QIODriveTier.BASE.getMaxCount() * QIOStorageUnits.UNITS_PER_ITEM,
              frequency.massInsert(fluid, Long.MAX_VALUE, Action.SIMULATE));
        assertEquals(QIODriveTier.BASE.getMaxCount() * QIOStorageUnits.UNITS_PER_ITEM,
              frequency.massInsert(gas, Long.MAX_VALUE, Action.SIMULATE));

        assertEquals(20, frequency.massInsert(item, 20, Action.EXECUTE));
        assertEquals(30, frequency.massInsert(fluid, 30, Action.EXECUTE));
        assertEquals(40, frequency.massInsert(gas, 40, Action.EXECUTE));
        QIODriveData itemData = frequency.getDriveData(new QIODriveMount(holder, 0));
        QIODriveData fluidData = frequency.getDriveData(new QIODriveMount(holder, 1));
        QIODriveData gasData = frequency.getDriveData(new QIODriveMount(holder, 2));
        assertNotNull(itemData);
        assertNotNull(fluidData);
        assertNotNull(gasData);
        assertEquals(20, itemData.getRecord().getTotalCount());
        assertEquals(1, fluidData.getRecord().getTotalCount());
        assertEquals(1, gasData.getRecord().getTotalCount());
        assertEquals(20_000, itemData.getRecord().getTotalStorageUnits());
        assertEquals(30, fluidData.getRecord().getTotalStorageUnits());
        assertEquals(40, gasData.getRecord().getTotalStorageUnits());

        assertEquals(0, itemData.insert(fluid, 1, Action.SIMULATE));
        assertEquals(0, itemData.insert(gas, 1, Action.SIMULATE));
        assertEquals(0, fluidData.insert(item, 1, Action.SIMULATE));
        assertEquals(0, fluidData.insert(gas, 1, Action.SIMULATE));
        assertEquals(0, gasData.insert(item, 1, Action.SIMULATE));
        assertEquals(0, gasData.insert(fluid, 1, Action.SIMULATE));

        UUID itemResource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(
              mekanism.common.lib.inventory.HashedItem.create(item));
        assertNotNull(itemResource);
        assertEquals(0, fluidData.getRecord().insert(itemResource, 1, Action.SIMULATE));
        assertEquals(0, gasData.getRecord().insert(itemResource, 1, Action.SIMULATE));
    }

    @Test
    void itemBatchSimulationIgnoresNonItemDriveCapacity() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-specialized-batch-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestHolder holder = new TestHolder(Arrays.asList(
              new ItemStack(new TestDriveItem(QIODriveTier.BASE, QIODriveType.ITEM)),
              new ItemStack(new TestDriveItem(QIODriveTier.BASE, QIODriveType.FLUID))), 0, new BlockPos(8, 9, 10));
        QIOFrequency frequency = new QIOFrequency("specialized-batch", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        ItemStack tooMany = new ItemStack(Blocks.STONE, 1);
        assertEquals(true, frequency.canInsertAllItems(Collections.singletonList(tooMany)));
        assertEquals(QIODriveTier.BASE.getMaxCount(), frequency.massInsert(tooMany,
              QIODriveTier.BASE.getMaxCount(), Action.EXECUTE));
        assertEquals(false, frequency.canInsertAllItems(Collections.singletonList(new ItemStack(Blocks.DIRT))));
        assertEquals(0, frequency.massInsert(new ItemStack(Blocks.DIRT), 1, Action.SIMULATE));
    }

    @Test
    void driveTypeCannotBeChangedByReusingAnExistingUuid() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-specialized-uuid-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem itemDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.ITEM);
        ItemStack original = new ItemStack(itemDrive);
        assertEquals(true, QIODriveData.initialize(original));
        UUID driveId = itemDrive.getDriveId(original);
        assertNotNull(driveId);

        TestDriveItem gasDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.GAS);
        ItemStack forged = new ItemStack(gasDrive);
        gasDrive.setDriveId(forged, driveId);
        TestHolder holder = new TestHolder(Collections.singletonList(forged), 0, new BlockPos(11, 12, 13));
        QIOFrequency frequency = new QIOFrequency("forged-type", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        assertEquals(QIODriveSlotState.INVALID_DRIVE, frequency.getSlotState(new QIODriveMount(holder, 0)));
        assertEquals(0, frequency.getTotalCountCapacity());
        assertEquals(QIODriveType.ITEM, QIODriveStorage.INSTANCE.get(driveId).getDriveType());
    }

    @Test
    void replacingMountedDriveWithForgedTypeReleasesTheOldMount() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-specialized-remount-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem itemDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.ITEM);
        ItemStack original = new ItemStack(itemDrive);
        java.util.ArrayList<ItemStack> drives = new java.util.ArrayList<>(Collections.singletonList(original));
        TestHolder holder = new TestHolder(drives, 0, new BlockPos(17, 18, 19));
        QIOFrequency frequency = new QIOFrequency("forged-remount", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        UUID driveId = itemDrive.getDriveId(drives.get(0));
        assertNotNull(driveId);
        assertNotNull(QIODriveStorage.INSTANCE.getActiveMount(driveId));

        TestDriveItem gasDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.GAS);
        ItemStack forged = new ItemStack(gasDrive);
        gasDrive.setDriveId(forged, driveId);
        drives.set(0, forged);
        frequency.requestRefresh();
        frequency.refresh();

        assertEquals(QIODriveSlotState.INVALID_DRIVE, frequency.getSlotState(new QIODriveMount(holder, 0)));
        assertEquals(null, QIODriveStorage.INSTANCE.getActiveMount(driveId));

        drives.set(0, original);
        frequency.requestRefresh();
        frequency.refresh();
        assertEquals(QIODriveSlotState.ACTIVE, frequency.getSlotState(new QIODriveMount(holder, 0)));
    }

    @Test
    void specializedDriveWithKnownMismatchedContentsIsRejectedOnMount() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-specialized-content-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        UUID itemResource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(
              mekanism.common.lib.inventory.HashedItem.create(new ItemStack(Blocks.STONE)));
        UUID driveId = UUID.randomUUID();

        NBTTagCompound drive = new QIODriveRecord(driveId, QIODriveTier.BASE, QIODriveType.FLUID).write();
        drive.setLong("count", 1);
        drive.setLong("storageUnits", QIOStorageUnits.UNITS_PER_ITEM);
        drive.setInteger("types", 1);
        NBTTagCompound content = new NBTTagCompound();
        content.setString("resource", itemResource.toString());
        content.setString("kind", QIOResourceKind.ITEM.getSerializedName());
        content.setLong("amount", 1);
        net.minecraft.nbt.NBTTagList contents = new net.minecraft.nbt.NBTTagList();
        contents.appendTag(content);
        drive.setTag("contents", contents);
        QIOFileIO.writeAtomic(new File(worldDirectory, "mekanism/qio/drives/" + driveId + ".dat"), drive);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestDriveItem fluidDrive = new TestDriveItem(QIODriveTier.BASE, QIODriveType.FLUID);
        ItemStack stack = new ItemStack(fluidDrive);
        fluidDrive.setDriveId(stack, driveId);
        TestHolder holder = new TestHolder(Collections.singletonList(stack), 0, new BlockPos(14, 15, 16));
        QIOFrequency frequency = new QIOFrequency("mismatched-content", null,
              mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();

        assertEquals(QIODriveSlotState.INVALID_DRIVE, frequency.getSlotState(new QIODriveMount(holder, 0)));
        assertEquals(0, frequency.getTotalCountCapacity());
        assertEquals(1, QIODriveStorage.INSTANCE.get(driveId).getTotalCount());
    }

    @Test
    void driveTierUpgradeCraftingPreservesIdentityAndMetadata() {
        TestDriveItem base = new TestDriveItem(QIODriveTier.BASE, QIODriveType.FLUID);
        TestDriveItem upgraded = new TestDriveItem(QIODriveTier.HYPER_DENSE, QIODriveType.FLUID);
        ItemStack input = new ItemStack(base);
        UUID driveId = UUID.randomUUID();
        base.setDriveId(input, driveId);
        base.setDriveMetadata(input, 1_234, 7);
        InventoryCrafting crafting = MekanismUtils.getDummyCraftingInv();
        crafting.setInventorySlotContents(4, input);

        ItemStack result = RecipeUtils.getCraftingResult(crafting, new ItemStack(upgraded));
        assertEquals(driveId, upgraded.getDriveId(result));
        assertEquals(1_234, upgraded.getDriveMetadata(result).getCount());
        assertEquals(7, upgraded.getDriveMetadata(result).getTypes());
    }

    @Test
    void unloadedStorageClearsTheFrequencySnapshot() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-unload-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestDriveItem driveItem = new TestDriveItem(QIODriveTier.BASE);
        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(driveItem)), 0, new BlockPos(2, 3, 4));
        QIOFrequency frequency = new QIOFrequency("unload", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        ItemStack item = new ItemStack(Blocks.STONE);
        assertEquals(16, frequency.massInsert(item, 16, Action.EXECUTE));
        assertEquals(16, frequency.getStored(item));

        QIOStorageManager.shutdown();
        frequency.requestRefresh();
        assertEquals(0, frequency.getStored(item));
        assertEquals(0, frequency.getTotalCount());
        assertEquals(0, frequency.getTotalCountCapacity());
    }

    @Test
    void frequencyColorPersistsAndQioItemsCacheIt() {
        QIOFrequency frequency = new QIOFrequency("color", null, mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC);
        frequency.setColor(EnumColor.AQUA);

        NBTTagCompound data = new NBTTagCompound();
        frequency.write(data);
        assertEquals(EnumColor.AQUA.ordinal(), data.getInteger(NBTConstants.COLOR));
        assertEquals(EnumColor.AQUA, new QIOFrequency(data).getColor());

        NBTTagCompound legacy = new NBTTagCompound();
        frequency.write(legacy);
        legacy.removeTag(NBTConstants.COLOR);
        assertEquals(EnumColor.AQUA, new QIOFrequency(legacy).getColor());

        NBTTagCompound uncolored = new NBTTagCompound();
        assertEquals(EnumColor.INDIGO, new QIOFrequency(uncolored).getColor());

        ItemPortableQIODashboard dashboard = new ItemPortableQIODashboard();
        ItemStack stack = new ItemStack(dashboard);
        dashboard.setFrequencyAware(stack, new FrequencyAware<QIOFrequency>(frequency));
        assertEquals(EnumColor.AQUA, dashboard.getColor(stack));
        dashboard.setFrequencyAware(stack, null);
        assertEquals(null, dashboard.getColor(stack));

        ItemBlockQIOComponent blockComponent = new ItemBlockQIOComponent(Blocks.CHEST);
        ItemStack blockStack = new ItemStack(blockComponent);
        blockComponent.setFrequencyAware(blockStack, new FrequencyAware<QIOFrequency>(frequency));
        assertEquals(EnumColor.AQUA, blockComponent.getColor(blockStack));
        blockComponent.setFrequencyAware(blockStack, null);
        assertEquals(null, blockComponent.getColor(blockStack));
    }

    @Test
    void tileNetworkSerializationIncludesTheCompleteQIOSnapshot() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-frequency-network-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        TestHolder holder = new TestHolder(Collections.singletonList(new ItemStack(new TestDriveItem(QIODriveTier.BASE))),
              0, new BlockPos(4, 5, 6));
        QIOFrequency frequency = new QIOFrequency("network", UUID.randomUUID(),
              mekanism.common.security.ISecurityTile.SecurityMode.TRUSTED);
        frequency.addHolder(holder);
        frequency.refresh();
        assertEquals(12, frequency.massInsert(new ItemStack(Blocks.STONE), 12, Action.EXECUTE));
        assertEquals(250, frequency.massInsert(new FluidStack(FluidRegistry.WATER, 250), 250, Action.EXECUTE));
        assertEquals(80, frequency.massInsert(new GasStack(new Gas("qio_network_test_gas", 0x44AAFF), 80), 80, Action.EXECUTE));
        frequency.setColor(EnumColor.AQUA);

        TileNetworkList tileData = new TileNetworkList();
        frequency.write(tileData);
        ByteBuf tileBuffer = Unpooled.buffer();
        ByteBuf directBuffer = Unpooled.buffer();
        try {
            PacketHandler.encode(tileData.toArray(), tileBuffer);
            assertNetworkSnapshot(frequency, new QIOFrequency(tileBuffer), tileBuffer);

            frequency.write(directBuffer);
            assertNetworkSnapshot(frequency, new QIOFrequency(directBuffer), directBuffer);
        } finally {
            tileBuffer.release();
            directBuffer.release();
        }
    }

    private static void assertNetworkSnapshot(QIOFrequency expected, QIOFrequency actual, ByteBuf buffer) {
        assertEquals(expected.getTotalCount(), actual.getTotalCount());
        assertEquals(expected.getTotalCountCapacity(), actual.getTotalCountCapacity());
        assertEquals(expected.getTotalTypes(), actual.getTotalTypes());
        assertEquals(expected.getTotalTypeCapacity(), actual.getTotalTypeCapacity());
        for (QIOResourceKind kind : QIOResourceKind.values()) {
            assertEquals(expected.getStoredCount(kind), actual.getStoredCount(kind));
        }
        assertEquals(expected.getColor(), actual.getColor());
        assertEquals(0, buffer.readableBytes());
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

        private final QIODriveTier tier;
        private final QIODriveType driveType;

        private TestDriveItem(QIODriveTier tier) {
            this(tier, QIODriveType.MIXED);
        }

        private TestDriveItem(QIODriveTier tier, QIODriveType driveType) {
            this.tier = tier;
            this.driveType = driveType;
            setMaxStackSize(1);
        }

        @Override
        public QIODriveTier getDriveTier() {
            return tier;
        }

        @Override
        public QIODriveType getDriveType() {
            return driveType;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {

        private final List<ItemStack> drives;
        private final int dimension;
        private final BlockPos position;

        private TestHolder(List<ItemStack> drives, int dimension, BlockPos position) {
            this.drives = drives;
            this.dimension = dimension;
            this.position = position;
        }

        @Override
        public int getQIODimension() {
            return dimension;
        }

        @Override
        public BlockPos getQIOPosition() {
            return position;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return drives;
        }
    }

    private static final class CopyingHolder implements IQIODriveHolder {

        private ItemStack drive;
        private final int dimension;
        private final BlockPos position;

        private CopyingHolder(ItemStack drive, int dimension, BlockPos position) {
            this.drive = drive;
            this.dimension = dimension;
            this.position = position;
        }

        @Override
        public int getQIODimension() {
            return dimension;
        }

        @Override
        public BlockPos getQIOPosition() {
            return position;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return Collections.singletonList(drive.copy());
        }

        @Override
        public void updateQIODriveStack(int slot, ItemStack stack) {
            drive = stack.copy();
        }

        private ItemStack getActualDrive() {
            return drive;
        }
    }
}
