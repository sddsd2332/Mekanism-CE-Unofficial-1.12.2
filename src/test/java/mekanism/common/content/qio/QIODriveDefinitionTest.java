package mekanism.common.content.qio;

import mekanism.common.item.ItemQIODrive;
import mekanism.common.tier.BaseTier;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.RecipeUtils;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIODriveDefinitionTest {

    private static final QIODriveDefinition ADDON_LARGE = QIODriveDefinition.builder("qio_test", "large")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(64_000_000_000L)
          .maxTypes(32_768)
          .register();
    private static final QIODriveDefinition ADDON_LARGER = QIODriveDefinition.builder("qio_test", "larger")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(128_000_000_000L)
          .maxTypes(65_536)
          .register();
    private static final QIODriveDefinition ADDON_RESIZED = QIODriveDefinition.builder("qio_test", "resized")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(1_000L)
          .maxTypes(10)
          .register();
    private static final QIODriveDefinition UNLIMITED_COUNT = QIODriveDefinition.builder("qio_test", "unlimited_count")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(Long.MAX_VALUE)
          .maxTypes(1)
          .register();
    private static final QIODriveDefinition UNLIMITED_TYPES = QIODriveDefinition.builder("qio_test", "unlimited_types")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(1_000)
          .maxTypes(Integer.MAX_VALUE)
          .register();
    private static final QIODriveDefinition CREATIVE_CAPACITY = QIODriveDefinition.builder("qio_test", "creative_capacity")
          .baseTier(BaseTier.ULTIMATE)
          .creativeCapacity()
          .register();

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
    void addonCanReuseGenericDriveItemWithOnlyARegisteredDefinition() {
        ItemQIODrive mixed = new ItemQIODrive(ADDON_LARGE, QIODriveType.MIXED);
        ItemStack stack = new ItemStack(mixed);

        assertSame(ADDON_LARGE, mixed.getDriveDefinition(stack));
        assertNull(mixed.getDriveTier());
        assertEquals(ADDON_LARGE.getMaxCount() * 3L, mixed.getCountCapacity(stack));
        assertEquals(ADDON_LARGE.getMaxCount() * 3_000L, mixed.getStorageCapacity(stack));
        assertEquals(ADDON_LARGE.getMaxTypes(), mixed.getTypeCapacity(stack));
    }

    @Test
    void maximumSentinelsAndBuilderMethodsResolveToIndependentUnlimitedDimensions() {
        assertTrue(UNLIMITED_COUNT.isCountUnlimited());
        assertFalse(UNLIMITED_COUNT.isTypesUnlimited());
        assertEquals(Long.MAX_VALUE, QIODriveType.MIXED.getCountCapacity(UNLIMITED_COUNT));
        assertEquals(Long.MAX_VALUE, QIODriveType.MIXED.getStorageCapacity(UNLIMITED_COUNT));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)),
              QIODriveType.MIXED.getExactCountCapacity(UNLIMITED_COUNT).toBigInteger());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3_000)),
              QIODriveType.MIXED.getExactStorageCapacity(UNLIMITED_COUNT).toBigInteger());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE),
              QIODriveType.ITEM.getExactCountCapacity(UNLIMITED_COUNT).toBigInteger());

        assertFalse(UNLIMITED_TYPES.isCountUnlimited());
        assertTrue(UNLIMITED_TYPES.isTypesUnlimited());
        assertEquals(Integer.MAX_VALUE, UNLIMITED_TYPES.getMaxTypes());

        assertTrue(CREATIVE_CAPACITY.isCreativeCapacity());
        ItemQIODrive creative = new ItemQIODrive(CREATIVE_CAPACITY, QIODriveType.MIXED);
        ItemStack stack = new ItemStack(creative);
        assertTrue(creative.hasCreativeCapacity(stack));
        assertEquals(Long.MAX_VALUE, creative.getCountCapacity(stack));
        assertEquals(Integer.MAX_VALUE, creative.getTypeCapacity(stack));
    }

    @Test
    void finiteAndUnlimitedModesCannotBeMixedForTheSameDimension() {
        QIODriveDefinition.Builder count = QIODriveDefinition.builder("qio_test", "conflicting_count")
              .baseTier(BaseTier.BASIC)
              .maxCount(100);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, count::unlimitedCount);

        QIODriveDefinition.Builder types = QIODriveDefinition.builder("qio_test", "conflicting_types")
              .baseTier(BaseTier.BASIC)
              .maxTypes(100);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, types::unlimitedTypes);
    }

    @Test
    void genericAddonDriveInitializesWithoutAnyLegacyEnumTier() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-addon-item-initialize-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        ItemQIODrive driveItem = new ItemQIODrive(ADDON_LARGE, QIODriveType.MIXED);
        ItemStack stack = new ItemStack(driveItem);

        assertTrue(QIODriveData.initialize(stack));
        UUID driveId = driveItem.getDriveId(stack);
        QIODriveRecord record = QIODriveStorage.INSTANCE.get(driveId);
        assertSame(ADDON_LARGE, record.getDefinition());
        assertEquals(driveItem.getCountCapacity(stack), record.getCountCapacity());
        assertEquals(driveItem.getStorageCapacity(stack), record.getStorageCapacity());
        assertEquals(driveItem.getTypeCapacity(stack), record.getTypeCapacity());
    }

    @Test
    void customDefinitionPersistsAndLargerDefinitionUpgradesWithoutDowngrading() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-custom-definition-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID driveId = UUID.randomUUID();

        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(driveId, ADDON_LARGE, QIODriveType.MIXED);
        assertSame(ADDON_LARGE, record.getDefinition());
        assertEquals(ADDON_LARGE.getMaxCount() * 3L, record.getCountCapacity());
        assertSame(record, QIODriveStorage.INSTANCE.getOrCreate(driveId, ADDON_LARGER, QIODriveType.MIXED));
        assertSame(ADDON_LARGER, record.getDefinition());
        assertEquals(ADDON_LARGER.getMaxCount() * 3L, record.getCountCapacity());
        assertNull(QIODriveStorage.INSTANCE.getOrCreate(driveId, ADDON_LARGE, QIODriveType.MIXED));
        ItemQIODrive smallerItem = new ItemQIODrive(ADDON_LARGE, QIODriveType.MIXED);
        ItemStack forgedDowngrade = new ItemStack(smallerItem);
        smallerItem.setDriveId(forgedDowngrade, driveId);
        assertFalse(QIODriveData.initialize(forgedDowngrade));

        QIODriveStorage.INSTANCE.markDriveDirty(driveId);
        QIODriveStorage.INSTANCE.flush();
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        QIODriveRecord restored = QIODriveStorage.INSTANCE.get(driveId);
        assertSame(ADDON_LARGER, restored.getDefinition());
        assertEquals("qio_test:larger", restored.getDefinitionName().toString());
        assertEquals(ADDON_LARGER.getMaxCount() * 3L, restored.getCountCapacity());
        assertEquals(ADDON_LARGER.getMaxTypes(), restored.getTypeCapacity());
    }

    @Test
    void unavailableAddonDefinitionPreservesDriveFileUntilDefinitionReturns() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-missing-definition-test").toFile();
        UUID driveId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        ResourceLocation definitionName = new ResourceLocation("missing_qio_addon", "restored_drive");
        long countCapacity = 999_999L;

        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", QIODriveRecord.DATA_VERSION);
        data.setString("uuid", driveId.toString());
        data.setString("definition", definitionName.toString());
        data.setString("driveType", QIODriveType.MIXED.getSerializedName());
        data.setLong("countCapacity", countCapacity);
        data.setLong("storageCapacity", countCapacity * QIOStorageUnits.UNITS_PER_ITEM);
        data.setByteArray("countCapacityExact", BigInteger.valueOf(countCapacity).toByteArray());
        data.setByteArray("storageCapacityExact", BigInteger.valueOf(countCapacity)
              .multiply(BigInteger.valueOf(QIOStorageUnits.UNITS_PER_ITEM)).toByteArray());
        data.setInteger("typeCapacity", 512);
        data.setLong("count", 42);
        data.setLong("storageUnits", 42_000L);
        data.setInteger("types", 1);
        NBTTagCompound content = new NBTTagCompound();
        content.setString("resource", resourceId.toString());
        content.setString("kind", QIOResourceKind.ITEM.getSerializedName());
        content.setLong("amount", 42);
        NBTTagList contents = new NBTTagList();
        contents.appendTag(content);
        data.setTag("contents", contents);
        QIOFileIO.writeAtomic(new File(worldDirectory, "mekanism/qio/drives/" + driveId + ".dat"), data);

        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIODriveRecord unresolved = QIODriveStorage.INSTANCE.get(driveId);
        assertFalse(QIODriveStorage.INSTANCE.isDamaged(driveId));
        assertNull(unresolved.getDefinition());
        assertEquals(definitionName, unresolved.getDefinitionName());
        assertEquals(42, unresolved.getStored(resourceId));

        QIODriveDefinition restoredDefinition = QIODriveDefinition.builder(definitionName)
              .baseTier(BaseTier.ULTIMATE)
              .maxCount(countCapacity / 3L)
              .maxTypes(512)
              .register();
        assertSame(unresolved, QIODriveStorage.INSTANCE.getOrCreate(driveId, restoredDefinition, QIODriveType.MIXED));
        assertSame(restoredDefinition, unresolved.getDefinition());
        assertEquals(42, unresolved.getStored(resourceId));
    }

    @Test
    void customDriveUpgradeRecipePreservesUuidOnlyWhenEveryCapacityIsNonDecreasing() {
        ItemQIODrive inputDrive = new ItemQIODrive(ADDON_LARGE, QIODriveType.ITEM);
        ItemQIODrive outputDrive = new ItemQIODrive(ADDON_LARGER, QIODriveType.ITEM);
        ItemStack input = new ItemStack(inputDrive);
        UUID driveId = UUID.randomUUID();
        inputDrive.setDriveId(input, driveId);
        inputDrive.setDriveMetadata(input, 12_345, 27, 12_345_000L);
        InventoryCrafting crafting = MekanismUtils.getDummyCraftingInv();
        crafting.setInventorySlotContents(0, input);

        ItemStack upgraded = RecipeUtils.getCraftingResult(crafting, new ItemStack(outputDrive));
        assertEquals(driveId, outputDrive.getDriveId(upgraded));
        assertEquals(12_345, outputDrive.getDriveMetadata(upgraded).getCount());

        ItemStack downgrade = RecipeUtils.getCraftingResult(crafting, new ItemStack(inputDrive));
        assertNull(inputDrive.getDriveId(downgrade));
        ItemQIODrive wrongType = new ItemQIODrive(ADDON_LARGER, QIODriveType.GAS);
        ItemStack changedType = RecipeUtils.getCraftingResult(crafting, new ItemStack(wrongType));
        assertNull(wrongType.getDriveId(changedType));
    }

    @Test
    void driveUpgradeRecipeRejectsAmbiguousOrDuplicatingUuidInheritance() {
        ItemQIODrive inputDrive = new ItemQIODrive(ADDON_LARGE, QIODriveType.ITEM);
        ItemQIODrive outputDrive = new ItemQIODrive(ADDON_LARGER, QIODriveType.ITEM);
        ItemStack first = new ItemStack(inputDrive);
        ItemStack second = new ItemStack(inputDrive);
        inputDrive.setDriveId(first, UUID.randomUUID());
        inputDrive.setDriveId(second, UUID.randomUUID());

        InventoryCrafting crafting = MekanismUtils.getDummyCraftingInv();
        crafting.setInventorySlotContents(0, first);
        crafting.setInventorySlotContents(1, second);
        assertTrue(RecipeUtils.getCraftingResult(crafting, new ItemStack(outputDrive)).isEmpty());

        crafting.setInventorySlotContents(1, ItemStack.EMPTY);
        assertTrue(RecipeUtils.getCraftingResult(crafting, new ItemStack(outputDrive, 2)).isEmpty());

        ItemStack stackedSource = first.copy();
        stackedSource.setCount(2);
        crafting.setInventorySlotContents(0, stackedSource);
        assertTrue(RecipeUtils.getCraftingResult(crafting, new ItemStack(outputDrive)).isEmpty());
    }

    @Test
    void sameDefinitionIdResizesOnlyWhenExistingContentsFit() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-same-definition-resize-test").toFile();
        UUID fittingDrive = UUID.randomUUID();
        UUID oversizedDrive = UUID.randomUUID();
        writeSnapshot(fittingDrive, 6_000L, 20, 42L);
        writeSnapshot(oversizedDrive, 6_000L, 20, 4_000L);

        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIODriveRecord fitting = QIODriveStorage.INSTANCE.get(fittingDrive);
        QIODriveRecord oversized = QIODriveStorage.INSTANCE.get(oversizedDrive);
        assertNull(fitting.getDefinition());
        assertNull(oversized.getDefinition());
        long revision = QIODriveStorage.INSTANCE.getMountRevision();

        assertSame(fitting, QIODriveStorage.INSTANCE.getOrCreate(fittingDrive, ADDON_RESIZED, QIODriveType.MIXED));
        assertSame(ADDON_RESIZED, fitting.getDefinition());
        assertEquals(3_000L, fitting.getCountCapacity());
        assertEquals(10, fitting.getTypeCapacity());
        assertTrue(QIODriveStorage.INSTANCE.getMountRevision() > revision);

        assertNull(QIODriveStorage.INSTANCE.getOrCreate(oversizedDrive, ADDON_RESIZED, QIODriveType.MIXED));
        assertEquals(6_000L, oversized.getCountCapacity());
        assertEquals(4_000L, oversized.getTotalCount());
    }

    @Test
    void finiteDefinitionsSupportExactCapacitiesBeyondLong() {
        QIODriveDefinition expanded = QIODriveDefinition.builder("qio_test", "expanded_finite")
              .baseTier(BaseTier.ULTIMATE)
              .maxCount(Long.MAX_VALUE - 1)
              .maxTypes(1)
              .register();

        ItemQIODrive mixed = new ItemQIODrive(expanded, QIODriveType.MIXED);
        BigInteger expectedCount = BigInteger.valueOf(Long.MAX_VALUE - 1).multiply(BigInteger.valueOf(3));
        assertFalse(expanded.isCountUnlimited());
        assertEquals(Long.MAX_VALUE, mixed.getCountCapacity());
        assertEquals(Long.MAX_VALUE, mixed.getStorageCapacity());
        assertEquals(expectedCount, mixed.getExactCountCapacity().toBigInteger());
        assertEquals(expectedCount.multiply(BigInteger.valueOf(QIOStorageUnits.UNITS_PER_ITEM)),
              mixed.getExactStorageCapacity().toBigInteger());

        UUID driveId = UUID.randomUUID();
        QIODriveRecord restored = QIODriveRecord.read(driveId,
              new QIODriveRecord(driveId, expanded, QIODriveType.MIXED).write());
        assertFalse(restored.hasUnlimitedCountCapacity());
        assertEquals(expectedCount, restored.getExactCountCapacity().toBigInteger());
    }

    private void writeSnapshot(UUID driveId, long countCapacity, int typeCapacity, long storedItems) throws Exception {
        UUID resourceId = UUID.randomUUID();
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", QIODriveRecord.DATA_VERSION);
        data.setString("uuid", driveId.toString());
        data.setString("definition", ADDON_RESIZED.getRegistryNameString());
        data.setString("driveType", QIODriveType.MIXED.getSerializedName());
        data.setLong("countCapacity", countCapacity);
        data.setLong("storageCapacity", countCapacity * QIOStorageUnits.UNITS_PER_ITEM);
        data.setByteArray("countCapacityExact", BigInteger.valueOf(countCapacity).toByteArray());
        data.setByteArray("storageCapacityExact", BigInteger.valueOf(countCapacity)
              .multiply(BigInteger.valueOf(QIOStorageUnits.UNITS_PER_ITEM)).toByteArray());
        data.setInteger("typeCapacity", typeCapacity);
        data.setLong("count", storedItems);
        data.setLong("storageUnits", storedItems * QIOStorageUnits.UNITS_PER_ITEM);
        data.setInteger("types", 1);
        NBTTagCompound content = new NBTTagCompound();
        content.setString("resource", resourceId.toString());
        content.setString("kind", QIOResourceKind.ITEM.getSerializedName());
        content.setLong("amount", storedItems);
        NBTTagList contents = new NBTTagList();
        contents.appendTag(content);
        data.setTag("contents", contents);
        QIOFileIO.writeAtomic(new File(worldDirectory, "mekanism/qio/drives/" + driveId + ".dat"), data);
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
}
