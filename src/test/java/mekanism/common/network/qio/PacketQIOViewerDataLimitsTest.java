package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIONetworkResourceLimits;
import mekanism.common.content.qio.QIOResourceEntry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketQIOViewerDataLimitsTest {

    private static final ResourceLocation CODEC_ID = new ResourceLocation("qio_test", "network_resource");

    @Test
    void oversizedAndDeepPayloadsUseTheGenericDisplayFallback() {
        NBTTagCompound oversized = new NBTTagCompound();
        oversized.setByteArray("data", new byte[QIONetworkResourceLimits.MAX_DESCRIPTOR_BYTES + 1]);
        assertFalse(QIONetworkResourceLimits.isSafeDescriptorPayload(oversized));
        assertFallbackRoundTrip(oversized);

        NBTTagCompound deep = new NBTTagCompound();
        NBTTagCompound cursor = deep;
        for (int depth = 0; depth <= QIONetworkResourceLimits.MAX_NBT_DEPTH; depth++) {
            NBTTagCompound child = new NBTTagCompound();
            cursor.setTag("child", child);
            cursor = child;
        }
        assertFalse(QIONetworkResourceLimits.isSafeDescriptorPayload(deep));
        assertFallbackRoundTrip(deep);
    }

    @Test
    void entriesAreSplitByTheirActualEncodedByteSize() {
        byte[] bytes = new byte[32 * 1024];
        new Random(0x51A0BEEFL).nextBytes(bytes);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setByteArray("data", bytes);
        assertTrue(QIONetworkResourceLimits.isSafeDescriptorPayload(payload));
        QIOResourceDescriptor descriptor = descriptor(payload);
        List<QIOResourceEntry> entries = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            entries.add(QIOResourceEntry.of(UUID.randomUUID(), descriptor, QIOAmount.of(index + 1)));
        }

        List<PacketQIOViewerData.Message> messages = PacketQIOViewerData.createBatchMessages(7, entries,
              100_000, 1_000);

        assertTrue(messages.size() > 1);
        int decodedEntries = 0;
        for (PacketQIOViewerData.Message message : messages) {
            ByteBuf encoded = Unpooled.buffer();
            try {
                message.toBytes(encoded);
                assertTrue(encoded.readableBytes() <= QIONetworkResourceLimits.MAX_PACKET_BYTES);
                PacketQIOViewerData.Message decoded = new PacketQIOViewerData.Message();
                decoded.fromBytes(encoded);
                assertTrue(decoded.isValid());
                decodedEntries += decoded.getEntries().size();
            } finally {
                encoded.release();
            }
        }
        assertEquals(entries.size(), decodedEntries);
    }

    @Test
    void aFullyRejectedInputStillProducesTheAuthoritativeEmptyPacket() {
        List<QIOResourceEntry> rejected = Collections.singletonList(null);

        List<PacketQIOViewerData.Message> batches = PacketQIOViewerData.createBatchMessages(9, rejected,
              12, 3);
        List<PacketQIOViewerData.Message> updates = PacketQIOViewerData.createUpdateMessages(9, rejected,
              12, 3);

        assertEquals(1, batches.size());
        assertTrue(batches.get(0).isFirstBatchChunk());
        assertTrue(batches.get(0).getEntries().isEmpty());
        assertEquals(1, updates.size());
        assertTrue(updates.get(0).getEntries().isEmpty());
    }

    private static void assertFallbackRoundTrip(NBTTagCompound payload) {
        UUID uuid = UUID.randomUUID();
        QIOResourceEntry original = QIOResourceEntry.of(uuid, descriptor(payload), QIOAmount.of(17));
        ByteBuf encoded = Unpooled.buffer();
        try {
            original.write(encoded);
            QIOResourceEntry decoded = QIOResourceEntry.read(encoded);
            assertNotNull(decoded);
            assertEquals(uuid, decoded.getUUID());
            assertEquals(17, decoded.getAmount());
            assertFalse(decoded.hasDisplayPayload());
            assertEquals(CODEC_ID, decoded.getDescriptor().getCodecId());
            assertTrue(decoded.getDescriptor().getPayload().isEmpty());
            assertFalse(decoded.getDescriptor().isResolved());
        } finally {
            encoded.release();
        }
    }

    private static QIOResourceDescriptor descriptor(NBTTagCompound payload) {
        return QIOResourceDescriptor.persisted(CODEC_ID, "qio_test.network", 1, 1, payload);
    }
}
