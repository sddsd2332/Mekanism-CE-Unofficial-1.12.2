package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIODrivePersistenceTest {

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
    void eachDriveIsPersistedIndependentlyAndReloaded() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-persistence-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        QIODriveRecord firstRecord = QIODriveStorage.INSTANCE.getOrCreate(first, QIODriveTier.BASE);
        QIODriveRecord secondRecord = QIODriveStorage.INSTANCE.getOrCreate(second, QIODriveTier.BASE);
        UUID resource = UUID.randomUUID();
        assertEquals(123, firstRecord.insert(resource, 123, Action.EXECUTE));
        QIODriveStorage.INSTANCE.markDriveDirty(first);
        QIODriveStorage.INSTANCE.markDriveDirty(second);
        QIODriveStorage.INSTANCE.flush();

        assertTrue(new File(worldDirectory, "mekanism/qio/drives/" + first + ".dat").isFile());
        assertTrue(new File(worldDirectory, "mekanism/qio/drives/" + second + ".dat").isFile());
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        assertEquals(123, QIODriveStorage.INSTANCE.get(first).getStored(resource));
        assertEquals(0, QIODriveStorage.INSTANCE.get(second).getTotalCount());
    }

    @Test
    void specializedDriveTypePersistsAcrossStorageRestart() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-specialized-persistence-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID drive = UUID.randomUUID();
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(drive, QIODriveTier.TIME_DILATING,
              QIODriveType.FLUID);
        assertEquals(QIODriveType.FLUID, record.getDriveType());
        QIODriveStorage.INSTANCE.markDriveDirty(drive);
        QIODriveStorage.INSTANCE.flush();

        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIODriveRecord restored = QIODriveStorage.INSTANCE.get(drive);
        assertEquals(QIODriveType.FLUID, restored.getDriveType());
        assertEquals(QIODriveTier.TIME_DILATING, restored.getTier());
    }

    @Test
    void legacyDriveFileIsRewrittenAsCurrentMixedStorage() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-drive-migration-test").toFile();
        UUID drive = UUID.randomUUID();
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("version", 1);
        legacy.setString("uuid", drive.toString());
        legacy.setString("tier", QIODriveTier.BASE.getSerializedName());
        legacy.setLong("count", 0);
        legacy.setInteger("types", 0);
        File driveFile = new File(worldDirectory, "mekanism/qio/drives/" + drive + ".dat");
        QIOFileIO.writeAtomic(driveFile, legacy);

        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        assertEquals(QIODriveType.MIXED, QIODriveStorage.INSTANCE.get(drive).getDriveType());
        QIODriveStorage.INSTANCE.flush();

        NBTTagCompound migrated = QIOFileIO.read(driveFile);
        assertEquals(QIODriveRecord.DATA_VERSION, migrated.getInteger("version"));
        assertEquals("mixed", migrated.getString("driveType"));
    }

    @Test
    void versionTwoFluidAmountsMigrateToFixedStorageUnits() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-drive-unit-migration-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(new FluidStack(FluidRegistry.WATER, 1));
        UUID drive = UUID.randomUUID();

        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("version", 2);
        legacy.setString("uuid", drive.toString());
        legacy.setString("tier", QIODriveTier.BASE.getSerializedName());
        legacy.setString("driveType", QIODriveType.MIXED.getSerializedName());
        legacy.setLong("count", 1_500);
        legacy.setInteger("types", 1);
        NBTTagCompound content = new NBTTagCompound();
        content.setString("resource", resource.toString());
        content.setLong("amount", 1_500);
        NBTTagList contents = new NBTTagList();
        contents.appendTag(content);
        legacy.setTag("contents", contents);
        File driveFile = new File(worldDirectory, "mekanism/qio/drives/" + drive + ".dat");
        QIOFileIO.writeAtomic(driveFile, legacy);

        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIODriveRecord migratedRecord = QIODriveStorage.INSTANCE.get(drive);
        assertEquals(1_500, migratedRecord.getStored(resource));
        assertEquals(1_500, migratedRecord.getTotalStorageUnits());
        assertEquals(2, migratedRecord.getTotalCount());
        QIODriveStorage.INSTANCE.flush();

        NBTTagCompound migrated = QIOFileIO.read(driveFile);
        assertEquals(QIODriveRecord.DATA_VERSION, migrated.getInteger("version"));
        assertEquals(1_500, migrated.getLong("storageUnits"));
        assertEquals("fluid", migrated.getTagList("contents",
              net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0).getString("kind"));
    }

    @Test
    void corruptDriveDoesNotHideOtherDrive() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-corrupt-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID healthy = UUID.randomUUID();
        UUID damaged = UUID.randomUUID();
        QIODriveStorage.INSTANCE.getOrCreate(healthy, QIODriveTier.BASE);
        QIODriveStorage.INSTANCE.getOrCreate(damaged, QIODriveTier.BASE);
        QIODriveStorage.INSTANCE.markDriveDirty(healthy);
        QIODriveStorage.INSTANCE.markDriveDirty(damaged);
        QIODriveStorage.INSTANCE.flush();
        File damagedFile = new File(worldDirectory, "mekanism/qio/drives/" + damaged + ".dat");
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setInteger("version", 999);
        net.minecraft.nbt.CompressedStreamTools.write(invalid, damagedFile);
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        assertTrue(QIODriveStorage.INSTANCE.hasDrive(healthy));
        assertTrue(QIODriveStorage.INSTANCE.isDamaged(damaged));
        assertEquals(null, QIODriveStorage.INSTANCE.get(damaged));
    }

    @Test
    void invalidIndexIsRebuiltFromAuthoritativeDriveFiles() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-drive-index-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        QIODriveRecord firstRecord = QIODriveStorage.INSTANCE.getOrCreate(first, QIODriveTier.BASE);
        QIODriveRecord secondRecord = QIODriveStorage.INSTANCE.getOrCreate(second, QIODriveTier.BASE);
        assertEquals(41, firstRecord.insert(resource, 41, Action.EXECUTE));
        assertEquals(73, secondRecord.insert(resource, 73, Action.EXECUTE));
        QIODriveStorage.INSTANCE.markDriveDirty(first);
        QIODriveStorage.INSTANCE.markDriveDirty(second);
        QIODriveStorage.INSTANCE.flush();

        File index = new File(worldDirectory, "mekanism/qio/drive_index.dat");
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setInteger("version", 999);
        try (java.io.OutputStream output = Files.newOutputStream(index.toPath())) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(invalid, output);
        }

        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        assertEquals(41, QIODriveStorage.INSTANCE.get(first).getStored(resource));
        assertEquals(73, QIODriveStorage.INSTANCE.get(second).getStored(resource));
        QIODriveStorage.INSTANCE.flush();

        NBTTagCompound rebuilt;
        try (java.io.InputStream input = Files.newInputStream(index.toPath())) {
            rebuilt = net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
        }
        assertEquals(2, rebuilt.getInteger("version"));
        assertEquals(2, rebuilt.getTagList("drives", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    void restoredHealthyDriveOverridesOldQuarantineMarker() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-restored-drive-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID drive = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(drive, QIODriveTier.BASE);
        assertEquals(91, record.insert(resource, 91, Action.EXECUTE));
        QIODriveStorage.INSTANCE.markDriveDirty(drive);
        QIODriveStorage.INSTANCE.flush();

        File driveFile = new File(worldDirectory, "mekanism/qio/drives/" + drive + ".dat");
        Files.copy(driveFile.toPath(), new File(driveFile.getParentFile(), driveFile.getName() + ".damaged").toPath());
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        assertFalse(QIODriveStorage.INSTANCE.isDamaged(drive));
        assertEquals(91, QIODriveStorage.INSTANCE.get(drive).getStored(resource));
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
