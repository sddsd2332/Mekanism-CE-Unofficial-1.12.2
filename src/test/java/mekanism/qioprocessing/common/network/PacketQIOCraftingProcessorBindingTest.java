package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.TestBootstrap;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.IdentitySerializer;
import mekanism.common.security.ISecurityTile.SecurityMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketQIOCraftingProcessorBindingTest {

    private static final Coord4D POSITION = new Coord4D(12, 34, -56, 7);

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void bindAndUnbindRoundTrip() {
        FrequencyIdentity identity = new FrequencyIdentity("processor",
              SecurityMode.PUBLIC, UUID.randomUUID());
        assertRoundTrip(PacketQIOCraftingProcessorBinding.Message.bind(10, POSITION,
              identity, UUID.randomUUID()));
        assertRoundTrip(PacketQIOCraftingProcessorBinding.Message.unbind(10, POSITION));
    }

    @Test
    void invalidFactoryArgumentsAreRejected() {
        FrequencyIdentity identity = new FrequencyIdentity("processor",
              SecurityMode.PRIVATE, UUID.randomUUID());
        assertFalse(PacketQIOCraftingProcessorBinding.Message.unbind(-1,
              POSITION).isValid());
        assertFalse(PacketQIOCraftingProcessorBinding.Message.bind(1, POSITION,
              identity, null).isValid());
    }

    @Test
    void truncatedAndMalformedPayloadsAreRejected() {
        ByteBuf truncated = Unpooled.buffer();
        ByteBuf invalidUUID = Unpooled.buffer();
        try {
            truncated.writeBoolean(false);
            truncated.writeInt(1);
            assertInvalid(truncated);

            invalidUUID.writeBoolean(true);
            invalidUUID.writeInt(1);
            POSITION.write(invalidUUID);
            IdentitySerializer.NAME.write(invalidUUID, new FrequencyIdentity("processor",
                  SecurityMode.TRUSTED, UUID.randomUUID()));
            PacketHandler.writeString(invalidUUID, "not-a-uuid");
            assertInvalid(invalidUUID);
        } finally {
            truncated.release();
            invalidUUID.release();
        }
    }

    private static void assertRoundTrip(
          PacketQIOCraftingProcessorBinding.Message original) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(),
                  encoded.readableBytes());
            PacketQIOCraftingProcessorBinding.Message decoded =
                  new PacketQIOCraftingProcessorBinding.Message();
            decoded.fromBytes(encoded);
            assertTrue(original.isValid());
            assertTrue(decoded.isValid());
            decoded.toBytes(reencoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(reencoded,
                  reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    private static void assertInvalid(ByteBuf buffer) {
        PacketQIOCraftingProcessorBinding.Message decoded =
              new PacketQIOCraftingProcessorBinding.Message();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isValid());
    }
}
