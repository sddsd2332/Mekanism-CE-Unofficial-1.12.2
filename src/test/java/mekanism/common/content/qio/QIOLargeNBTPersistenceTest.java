package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.Action;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.network.qio.PacketQIOViewerData;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOLargeNBTPersistenceTest {

    private static final int PAYLOAD_SIZE = 256 * 1024;

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
    void largeItemNbtSurvivesRegistryDriveAndViewerRoundTrips() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-large-nbt-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        byte[] payload = new byte[PAYLOAD_SIZE];
        new Random(0x51494F4CL).nextBytes(payload);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("marker", "qio-large-nbt");
        tag.setByteArray("payload", payload);
        ItemStack item = new ItemStack(Blocks.STONE);
        item.setTagCompound(tag);

        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(HashedItem.create(item));
        UUID drive = UUID.randomUUID();
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(drive, QIODriveTier.SUPERMASSIVE);
        assertNotNull(record);
        assertEquals(321, record.insert(resource, 321, Action.EXECUTE));
        QIODriveStorage.INSTANCE.markDriveDirty(drive);
        QIOResourceTypeRegistry.INSTANCE.flush();
        QIODriveStorage.INSTANCE.flush();

        File resourceFile = new File(worldDirectory, "mekanism/qio/resource_types/" + resource + ".dat");
        File driveFile = new File(worldDirectory, "mekanism/qio/drives/" + drive + ".dat");
        assertTrue(resourceFile.length() > 200_000);
        assertTrue(driveFile.isFile());

        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        assertEquals(resource, QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(item)));
        assertEquals(321, QIODriveStorage.INSTANCE.get(drive).getStored(resource));
        QIOResourceType restoredType = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        assertNotNull(restoredType);
        assertArrayEquals(payload, restoredType.createItemStack(1).getTagCompound().getByteArray("payload"));

        QIOResourceEntry entry = QIOResourceEntry.create(resource, 321);
        assertNotNull(entry);
        PacketQIOViewerData.Message encoded = PacketQIOViewerData.Message.batch(41, Collections.singletonList(entry), 16_000, 128);
        ByteBuf buffer = Unpooled.buffer();
        try {
            encoded.toBytes(buffer);
            assertTrue(buffer.readableBytes() > 200_000);
            PacketQIOViewerData.Message decoded = new PacketQIOViewerData.Message();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isValid());
            assertEquals(41, decoded.getWindowId());
            assertEquals(1, decoded.getEntries().size());
            QIOResourceEntry decodedEntry = decoded.getEntries().get(0);
            assertEquals(resource, decodedEntry.getUUID());
            assertEquals(321, decodedEntry.getAmount());
            assertArrayEquals(payload, decodedEntry.getItem().getTagCompound().getByteArray("payload"));
        } finally {
            buffer.release();
        }
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
