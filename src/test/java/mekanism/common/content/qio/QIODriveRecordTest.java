package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIODriveRecordTest {

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
    void versionOneRecordsMigrateToMixedDriveType() {
        UUID driveId = UUID.randomUUID();
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("version", 1);
        legacy.setString("uuid", driveId.toString());
        legacy.setString("tier", QIODriveTier.BASE.getSerializedName());
        legacy.setLong("count", 0);
        legacy.setInteger("types", 0);

        QIODriveRecord record = QIODriveRecord.read(driveId, legacy);
        assertEquals(QIODriveType.MIXED, record.getDriveType());
        NBTTagCompound migrated = record.write();
        assertEquals(QIODriveRecord.DATA_VERSION, migrated.getInteger("version"));
        assertEquals("mixed", migrated.getString("driveType"));
    }
}
