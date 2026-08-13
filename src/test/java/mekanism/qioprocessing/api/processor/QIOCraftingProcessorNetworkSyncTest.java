package mekanism.qioprocessing.api.processor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.TileNetworkList;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.PacketHandler;
import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingProcessor;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingProcessorNetworkSyncTest {

    private static final String FREQUENCY_REFERENCE =
          "qioProcessingFrequencyReference";

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @BeforeEach
    void setup() {
        QIOCraftingProcessorRegistry.resetForTests();
        QIOCraftingProcessorRegistry.bootstrapBuiltins(Long.MAX_VALUE);
    }

    @AfterEach
    void cleanup() {
        QIOCraftingProcessorRegistry.resetForTests();
    }

    @Test
    void tileUpdatePayloadCarriesBindingAndUnbindingReference() {
        QIOFrequencyReference expected = new QIOFrequencyReference(UUID.randomUUID(),
              "processor", UUID.randomUUID(), SecurityMode.PRIVATE, UUID.randomUUID());
        TileEntityQIOCraftingProcessor bound = new TileEntityQIOCraftingProcessor();
        NBTTagCompound sustained = new NBTTagCompound();
        sustained.setTag(FREQUENCY_REFERENCE, expected.write());
        bound.readSustainedQIOData(sustained);

        NBTTagCompound boundPayload = lastCompound(bound.getNetworkedData());
        assertTrue(boundPayload.hasKey(FREQUENCY_REFERENCE));
        assertEquals(expected, QIOFrequencyReference.read(
              boundPayload.getCompoundTag(FREQUENCY_REFERENCE)));

        TileEntityQIOCraftingProcessor unbound = new TileEntityQIOCraftingProcessor();
        NBTTagCompound unboundPayload = lastCompound(unbound.getNetworkedData());
        assertFalse(unboundPayload.hasKey(FREQUENCY_REFERENCE));

        assertEncodable(bound.getNetworkedData());
        assertEncodable(unbound.getNetworkedData());
    }

    @Test
    void visualUpdateTagCarriesWorkingState() {
        TileEntityQIOCraftingProcessor processor = new TileEntityQIOCraftingProcessor();
        assertFalse(processor.isWorking());

        NBTTagCompound update = new NBTTagCompound();
        update.setBoolean("qioWorking", true);
        processor.handleUpdateTag(update);

        assertTrue(processor.isWorking());
        TileNetworkList networked = processor.getNetworkedData();
        assertEquals(Boolean.TRUE, networked.get(networked.size() - 2));
        assertEncodable(networked);
    }

    private static NBTTagCompound lastCompound(TileNetworkList data) {
        Object value = data.get(data.size() - 1);
        assertTrue(value instanceof NBTTagCompound);
        return (NBTTagCompound) value;
    }

    private static void assertEncodable(TileNetworkList data) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            PacketHandler.encode(data.toArray(), buffer);
            assertTrue(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
