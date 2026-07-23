package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIODriveRecordTest {

    private static final QIODriveDefinition CREATIVE = QIODriveDefinition.builder("qio_record_test", "creative")
          .baseTier(mekanism.common.tier.BaseTier.ULTIMATE)
          .creativeCapacity()
          .register();
    private static final QIODriveDefinition UNLIMITED_COUNT_ONE_TYPE = QIODriveDefinition.builder("qio_record_test", "unlimited_count_one_type")
          .baseTier(mekanism.common.tier.BaseTier.ULTIMATE)
          .unlimitedCount()
          .maxTypes(1)
          .register();
    private static final QIODriveDefinition UNLIMITED_TYPES_ONE_ITEM = QIODriveDefinition.builder("qio_record_test", "unlimited_types_one_item")
          .baseTier(mekanism.common.tier.BaseTier.ULTIMATE)
          .maxCount(1)
          .unlimitedTypes()
          .register();

    @Test
    void sharedCountAndTypeCapacityApplyToEveryResourceKind() {
        QIODriveRecord record = new QIODriveRecord(UUID.randomUUID(), QIODriveTier.BASE);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(10, record.insert(first, 10, Action.EXECUTE));
        assertEquals(5, record.insert(first, 5, Action.SIMULATE));
        assertEquals(10, record.getTotalCount());
        assertEquals(1, record.getTotalTypes());

        assertEquals(7, record.extract(first, 7, Action.EXECUTE));
        assertEquals(3, record.getStored(first));
        assertEquals(3, record.getTotalCount());

        assertEquals(3, record.insert(second, 3, Action.EXECUTE));
        assertEquals(6, record.getTotalCount());
        assertEquals(2, record.getTotalTypes());
    }

    @Test
    void zeroEntriesAreRemovedAndLongArithmeticDoesNotWrap() {
        QIODriveRecord record = new QIODriveRecord(UUID.randomUUID(), QIODriveTier.SUPERMASSIVE);
        UUID resource = UUID.randomUUID();
        assertEquals(48_000_000_000L, record.insert(resource, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(48_000_000_000L, record.getTotalCount());
        assertEquals(48_000_000_000L, record.extract(resource, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(0, record.getTotalCount());
        assertEquals(0, record.getTotalTypes());
    }

    @Test
    void unlimitedRecordKeepsPerResourceLongsAndAnExactAggregate() {
        UUID driveId = UUID.randomUUID();
        QIODriveRecord record = new QIODriveRecord(driveId, CREATIVE, QIODriveType.MIXED);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        BigInteger expectedCapacity = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3));

        assertEquals(expectedCapacity, record.getExactCountCapacity().toBigInteger());
        assertEquals(expectedCapacity.multiply(BigInteger.valueOf(QIOStorageUnits.UNITS_PER_ITEM)),
              record.getExactStorageCapacity().toBigInteger());
        assertEquals(Long.MAX_VALUE, record.insert(first, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(Long.MAX_VALUE, record.insert(second, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(Long.MAX_VALUE, record.insert(third, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(0, record.insert(fourth, 1, Action.EXECUTE));
        assertEquals(Long.MAX_VALUE, record.getStored(first));
        assertEquals(Long.MAX_VALUE, record.getTotalCount());
        assertEquals(expectedCapacity, record.getExactTotalCount().toBigInteger());
        assertEquals(Long.MAX_VALUE, record.getTotalStorageUnits());
        assertEquals(3, record.getTotalTypes());
        assertEquals(true, record.hasCreativeCapacity());

        NBTTagCompound written = record.write();
        assertEquals(QIODriveRecord.DATA_VERSION, written.getInteger("version"));
        QIODriveRecord restored = QIODriveRecord.read(driveId, written);
        assertEquals(true, restored.hasCreativeCapacity());
        assertEquals(expectedCapacity, restored.getExactCountCapacity().toBigInteger());
        assertEquals(record.getExactTotalCount(), restored.getExactTotalCount());
        assertEquals(record.getExactTotalStorageUnits(), restored.getExactTotalStorageUnits());
        assertEquals(0, restored.insert(fourth, 1, Action.SIMULATE));
    }

    @Test
    void unreleasedDevelopmentVersionsAreRejected() {
        UUID driveId = UUID.randomUUID();
        NBTTagCompound unsupported = new QIODriveRecord(driveId, CREATIVE, QIODriveType.MIXED).write();
        unsupported.setInteger("version", 5);

        assertThrows(IllegalArgumentException.class, () -> QIODriveRecord.read(driveId, unsupported));
    }

    @Test
    void unlimitedDimensionsRemainIndependentDuringInsertion() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        QIODriveRecord unlimitedCount = new QIODriveRecord(UUID.randomUUID(), UNLIMITED_COUNT_ONE_TYPE, QIODriveType.MIXED);
        assertEquals(Long.MAX_VALUE, unlimitedCount.insert(first, Long.MAX_VALUE, Action.EXECUTE));
        assertEquals(0, unlimitedCount.insert(second, 1, Action.EXECUTE));
        assertEquals(true, unlimitedCount.hasUnlimitedCountCapacity());
        assertEquals(false, unlimitedCount.hasUnlimitedTypeCapacity());

        QIODriveRecord unlimitedTypes = new QIODriveRecord(UUID.randomUUID(), UNLIMITED_TYPES_ONE_ITEM, QIODriveType.MIXED);
        assertEquals(3, unlimitedTypes.insert(first, 3, Action.EXECUTE));
        assertEquals(0, unlimitedTypes.insert(second, 1, Action.EXECUTE));
        assertEquals(false, unlimitedTypes.hasUnlimitedCountCapacity());
        assertEquals(true, unlimitedTypes.hasUnlimitedTypeCapacity());
    }

    @Test
    void mixedDriveHasThreeTimesTheSpecializedCountCapacity() {
        for (QIODriveTier tier : QIODriveTier.values()) {
            QIODriveRecord mixed = new QIODriveRecord(UUID.randomUUID(), tier, QIODriveType.MIXED);
            QIODriveRecord item = new QIODriveRecord(UUID.randomUUID(), tier, QIODriveType.ITEM);
            QIODriveRecord fluid = new QIODriveRecord(UUID.randomUUID(), tier, QIODriveType.FLUID);
            QIODriveRecord gas = new QIODriveRecord(UUID.randomUUID(), tier, QIODriveType.GAS);

            assertEquals(tier.getMaxCount() * 3L, mixed.getCountCapacity());
            assertEquals(tier.getMaxCount(), item.getCountCapacity());
            assertEquals(tier.getMaxCount(), fluid.getCountCapacity());
            assertEquals(tier.getMaxCount(), gas.getCountCapacity());
            assertEquals(mixed.getCountCapacity() * QIOStorageUnits.UNITS_PER_ITEM, mixed.getStorageCapacity());
            assertEquals(item.getCountCapacity() * QIOStorageUnits.UNITS_PER_ITEM, item.getStorageCapacity());
            assertEquals(fluid.getCountCapacity() * QIOStorageUnits.UNITS_PER_ITEM, fluid.getStorageCapacity());
            assertEquals(gas.getCountCapacity() * QIOStorageUnits.UNITS_PER_ITEM, gas.getStorageCapacity());
            assertEquals(tier.getMaxTypes(), mixed.getTypeCapacity());
        }
    }

    @Test
    void initialVersionRoundTripsTheCurrentDefinitionFormat() {
        UUID driveId = UUID.randomUUID();
        QIODriveRecord original = new QIODriveRecord(driveId, QIODriveTier.BASE, QIODriveType.MIXED);
        NBTTagCompound written = original.write();
        QIODriveRecord restored = QIODriveRecord.read(driveId, written);

        assertEquals(1, written.getInteger("version"));
        assertEquals(QIODriveType.MIXED, restored.getDriveType());
        assertEquals(QIODriveDefinition.BASE, restored.getDefinition());
        assertEquals("mixed", written.getString("driveType"));
        assertEquals("mekanism:base", written.getString("definition"));
        assertEquals(QIODriveType.MIXED.getCountCapacity(QIODriveDefinition.BASE),
              written.getLong("countCapacity"));
    }
}
