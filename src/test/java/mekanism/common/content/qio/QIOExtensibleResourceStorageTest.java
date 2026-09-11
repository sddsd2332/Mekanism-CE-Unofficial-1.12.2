package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.tier.BaseTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOExtensibleResourceStorageTest {

    private static final QIOResourceCodec<String> CODEC = new StorageCodec();

    @BeforeAll
    static void registerCodec() {
        QIOResourceCodecRegistry.INSTANCE.register(CODEC);
    }

    @AfterEach
    void resetRegistry() {
        QIOResourceTypeRegistry.INSTANCE.reset();
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void customResourcesPersistAndUseTheirDeclaredStorageUnits() {
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(temporaryDirectory.toFile());
        QIOResourceDescriptor descriptor = QIOResourceDescriptor.of(CODEC, "stored-value");
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrack(descriptor);
        QIODriveRecord record = new QIODriveRecord(UUID.randomUUID(), QIODriveTier.BASE,
              QIODriveType.MIXED);

        assertEquals(7, record.insert(resource, 7, Action.EXECUTE));
        assertEquals(7, record.getStored(resource));
        assertEquals(7 * CODEC.getStorageUnitsPerUnit(), record.getTotalStorageUnits());
        QIODriveRecord restoredDrive = QIODriveRecord.read(record.getDriveId(), record.write());
        assertEquals(7, restoredDrive.getStored(resource));
        assertEquals(record.getTotalStorageUnits(), restoredDrive.getTotalStorageUnits());
        assertTrue(QIOResourceTypeRegistry.INSTANCE.flush());

        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(temporaryDirectory.toFile());
        QIOResourceType restoredType = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);

        assertNotNull(restoredType);
        assertEquals(descriptor, restoredType.getDescriptor());
        assertTrue(restoredType.isResolved());
    }

    @Test
    void unknownCodecResourcesRemainOnDriveAndCannotBeExtracted() throws Exception {
        UUID resource = UUID.randomUUID();
        ResourceLocation missingCodec = new ResourceLocation("qio_test", "removed_storage_codec");
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("value", "orphaned");
        QIOResourceDescriptor descriptor = QIOResourceDescriptor.persisted(missingCodec,
              "removed.resource", 3, 29, payload);
        Path resourceDirectory = temporaryDirectory.resolve("mekanism/qio/resource_types");
        Files.createDirectories(resourceDirectory);
        QIOFileIO.writeAtomic(QIOFileIO.dataFile(resourceDirectory.toFile(), resource),
              QIOResourceType.forDescriptor(resource, descriptor).write());
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(temporaryDirectory.toFile());

        QIOResourceType restoredType = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        assertNotNull(restoredType);
        assertFalse(restoredType.isResolved());
        QIODriveRecord record = QIODriveRecord.read(createUnknownDrive(resource, descriptor),
              unknownDriveData(createUnknownDrive(resource, descriptor), resource, descriptor, 5));

        assertEquals(QIODriveSpecializationRegistry.MIXED_ID, record.getSpecializationName());
        assertEquals(5, record.getStored(resource));
        assertEquals(145, record.getTotalStorageUnits());
        assertEquals(0, record.extract(resource, 1, Action.SIMULATE));
        NBTTagCompound rewritten = record.write();
        assertEquals(QIODriveRecord.DATA_VERSION, rewritten.getInteger("version"));
        assertEquals(QIODriveSpecializationRegistry.MIXED_ID.toString(),
              rewritten.getString("specialization"));
        assertFalse(rewritten.hasKey("driveType"));
        NBTTagCompound stored = rewritten.getTagList("contents", 10).getCompoundTagAt(0);
        assertEquals("removed.resource", stored.getString("family"));
        assertEquals(missingCodec.toString(), stored.getString("codec"));
        assertEquals(29, stored.getLong("storageUnitsPerUnit"));
    }

    @Test
    void mixedCapacityAddsEveryRegisteredSpecializationForTheSameDefinition() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        QIODriveDefinition definition = QIODriveDefinition.builder(
                    new ResourceLocation("qio_test", "mixed_capacity_" + suffix))
              .baseTier(BaseTier.BASIC)
              .maxCount(10_000)
              .maxTypes(64)
              .register();
        QIOAmount before = QIODriveSpecializations.MIXED.getExactCountCapacity(definition);
        ResourceLocation firstId = new ResourceLocation("qio_test", "specialization_a_" + suffix);
        ResourceLocation secondId = new ResourceLocation("qio_test", "specialization_b_" + suffix);

        QIODriveSpecialization first = QIODriveSpecialization.builder(firstId)
              .matcher(QIOResourceFamilyMatcher.family("qio_test.addon_a"))
              .capacity(definition, 10_000)
              .register();
        QIODriveSpecialization.builder(secondId)
              .matcher(QIOResourceFamilyMatcher.family("qio_test.addon_b"))
              .capacity(definition, 5_000)
              .register();

        assertEquals(before.add(15_000),
              QIODriveSpecializations.MIXED.getExactCountCapacity(definition));
        assertEquals(10_000, first.getCountCapacity(definition));
        assertThrows(IllegalArgumentException.class, () -> QIODriveSpecialization.builder(firstId)
              .matcher(QIOResourceFamilyMatcher.family("qio_test.duplicate"))
              .capacity(definition, 1)
              .register());
    }

    @Test
    void resizedDriveStaysReadableAndBlocksWritesUntilBackWithinCapacity() {
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(temporaryDirectory.toFile());
        QIOResourceDescriptor descriptor = QIOResourceDescriptor.of(CODEC, "resized-value");
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrack(descriptor);
        QIOAmount currentCountCapacity = QIODriveSpecializations.MIXED.getExactCountCapacity(
              QIODriveDefinition.BASE);
        BigInteger currentStorageCapacity = currentCountCapacity.multiply(
              QIOStorageUnits.UNITS_PER_ITEM).toBigInteger();
        long amount = currentStorageCapacity.divide(BigInteger.valueOf(
              CODEC.getStorageUnitsPerUnit())).longValueExact() + 1;
        UUID driveId = UUID.randomUUID();
        QIODriveRecord record = QIODriveRecord.read(driveId, resizedDriveData(driveId, resource,
              descriptor, amount, currentCountCapacity.add(1)));

        assertFalse(record.isOverCapacity());
        assertEquals(QIODriveRecord.DefinitionUpdate.RESIZED,
              record.applyDefinition(QIODriveDefinition.BASE));
        assertTrue(record.isOverCapacity());
        assertEquals(0, record.insert(resource, 1, Action.SIMULATE));
        assertEquals(2, record.extract(resource, 2, Action.EXECUTE));
        assertFalse(record.isOverCapacity());
        assertEquals(1, record.insert(resource, 1, Action.SIMULATE));
    }

    private static UUID createUnknownDrive(UUID resource, QIOResourceDescriptor descriptor) {
        return UUID.nameUUIDFromBytes((resource.toString() + descriptor.getCodecId()).getBytes(
              java.nio.charset.StandardCharsets.UTF_8));
    }

    private static NBTTagCompound unknownDriveData(UUID driveId, UUID resource,
          QIOResourceDescriptor descriptor, long amount) {
        QIOAmount countCapacity = QIODriveType.MIXED.getExactCountCapacity(QIODriveTier.BASE);
        QIOAmount storageCapacity = QIODriveType.MIXED.getExactStorageCapacity(QIODriveTier.BASE);
        long storageUnits = amount * descriptor.getStorageUnitsPerUnit();
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", 2);
        data.setString("uuid", driveId.toString());
        data.setString("definition", QIODriveDefinition.BASE.getRegistryNameString());
        data.setString("driveType", QIODriveType.MIXED.getSerializedName());
        data.setLong("countCapacity", countCapacity.longValueClamped());
        data.setLong("storageCapacity", storageCapacity.longValueClamped());
        data.setByteArray("countCapacityExact", countCapacity.toBigInteger().toByteArray());
        data.setByteArray("storageCapacityExact", storageCapacity.toBigInteger().toByteArray());
        data.setInteger("typeCapacity", QIODriveDefinition.BASE.getMaxTypes());
        data.setBoolean("unlimitedCount", false);
        data.setBoolean("unlimitedTypes", false);
        data.setLong("count", QIOStorageUnits.toItemEquivalent(storageUnits));
        data.setLong("storageUnits", storageUnits);
        data.setByteArray("storageUnitsExact", java.math.BigInteger.valueOf(storageUnits).toByteArray());
        data.setInteger("types", 1);
        NBTTagCompound stored = new NBTTagCompound();
        stored.setString("resource", resource.toString());
        stored.setString("family", descriptor.getFamily());
        stored.setString("codec", descriptor.getCodecId().toString());
        stored.setLong("storageUnitsPerUnit", descriptor.getStorageUnitsPerUnit());
        stored.setLong("amount", amount);
        NBTTagList contents = new NBTTagList();
        contents.appendTag(stored);
        data.setTag("contents", contents);
        return data;
    }

    private static NBTTagCompound resizedDriveData(UUID driveId, UUID resource,
          QIOResourceDescriptor descriptor, long amount, QIOAmount countCapacity) {
        QIOAmount storageCapacity = countCapacity.multiply(QIOStorageUnits.UNITS_PER_ITEM);
        long storageUnits = Math.multiplyExact(amount, descriptor.getStorageUnitsPerUnit());
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", QIODriveRecord.DATA_VERSION);
        data.setString("uuid", driveId.toString());
        data.setString("definition", QIODriveDefinition.BASE.getRegistryNameString());
        data.setString("specialization", QIODriveSpecializationRegistry.MIXED_ID.toString());
        data.setLong("countCapacity", countCapacity.longValueClamped());
        data.setLong("storageCapacity", storageCapacity.longValueClamped());
        data.setByteArray("countCapacityExact", countCapacity.toBigInteger().toByteArray());
        data.setByteArray("storageCapacityExact", storageCapacity.toBigInteger().toByteArray());
        data.setInteger("typeCapacity", QIODriveDefinition.BASE.getMaxTypes());
        data.setBoolean("unlimitedCount", false);
        data.setBoolean("unlimitedTypes", false);
        data.setLong("count", QIOStorageUnits.toItemEquivalent(storageUnits));
        data.setLong("storageUnits", storageUnits);
        data.setByteArray("storageUnitsExact", BigInteger.valueOf(storageUnits).toByteArray());
        data.setInteger("types", 1);
        NBTTagCompound stored = new NBTTagCompound();
        stored.setString("resource", resource.toString());
        stored.setString("family", descriptor.getFamily());
        stored.setString("codec", descriptor.getCodecId().toString());
        stored.setLong("storageUnitsPerUnit", descriptor.getStorageUnitsPerUnit());
        stored.setLong("amount", amount);
        NBTTagList contents = new NBTTagList();
        contents.appendTag(stored);
        data.setTag("contents", contents);
        return data;
    }

    private static final class StorageCodec implements QIOResourceCodec<String> {

        private static final ResourceLocation ID = new ResourceLocation("qio_test", "storage_string");

        @Override
        public ResourceLocation getCodecId() {
            return ID;
        }

        @Override
        public String getFamily() {
            return "test.storage";
        }

        @Override
        public Class<String> getValueClass() {
            return String.class;
        }

        @Override
        public String normalize(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Storage test value cannot be empty");
            }
            return value;
        }

        @Override
        public boolean sameType(String first, String second) {
            return normalize(first).equals(normalize(second));
        }

        @Override
        public int typeHash(String value) {
            return normalize(value).hashCode();
        }

        @Override
        public NBTTagCompound writeTemplate(String value) {
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("value", normalize(value));
            return payload;
        }

        @Override
        public String readTemplate(NBTTagCompound payload, int codecVersion) {
            if (codecVersion != 1) {
                throw new IllegalArgumentException("Unsupported storage test codec version");
            }
            return normalize(payload.getString("value"));
        }

        @Override
        public long getStorageUnitsPerUnit() {
            return 37;
        }
    }
}
