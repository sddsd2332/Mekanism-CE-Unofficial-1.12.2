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

class QIOAutomationPacketTest {

    private static final Coord4D POSITION = new Coord4D(12, 34, -56, 7);

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void bindingPacketsRoundTripExactFrequencyIdentity() {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        FrequencyIdentity identity = new FrequencyIdentity("processing", SecurityMode.TRUSTED, owner);

        assertBindingRoundTrip(PacketQIOAutomationBinding.Message.bind(19, POSITION, identity, frequency));
        assertBindingRoundTrip(PacketQIOAutomationBinding.Message.unbind(19, POSITION));
    }

    @Test
    void bindingPacketsRejectDamagedPayloads() {
        ByteBuf truncated = Unpooled.buffer();
        ByteBuf negativeWindow = Unpooled.buffer();
        ByteBuf invalidUUID = Unpooled.buffer();
        try {
            truncated.writeBoolean(true);
            truncated.writeInt(9);
            assertInvalidBinding(truncated);

            negativeWindow.writeBoolean(false);
            negativeWindow.writeInt(-1);
            POSITION.write(negativeWindow);
            assertInvalidBinding(negativeWindow);

            invalidUUID.writeBoolean(true);
            invalidUUID.writeInt(9);
            POSITION.write(invalidUUID);
            IdentitySerializer.NAME.write(invalidUUID,
                  new FrequencyIdentity("processing", SecurityMode.PUBLIC, null));
            PacketHandler.writeString(invalidUUID, "not-a-uuid");
            assertInvalidBinding(invalidUUID);
        } finally {
            truncated.release();
            negativeWindow.release();
            invalidUUID.release();
        }
    }

    @Test
    void trackingPacketsRoundTripStartAndStop() {
        assertTrackingRoundTrip(new PacketQIOAutomationTracking.Message(true, 23, POSITION));
        assertTrackingRoundTrip(new PacketQIOAutomationTracking.Message(false, 23, POSITION));
    }

    @Test
    void trackingPacketsRejectDamagedPayloads() {
        ByteBuf truncated = Unpooled.buffer();
        ByteBuf negativeWindow = Unpooled.buffer();
        try {
            truncated.writeBoolean(true);
            truncated.writeInt(23);
            PacketQIOAutomationTracking.Message decodedTruncated =
                  new PacketQIOAutomationTracking.Message();
            decodedTruncated.fromBytes(truncated);
            assertFalse(decodedTruncated.isValid());

            negativeWindow.writeBoolean(true);
            negativeWindow.writeInt(-1);
            POSITION.write(negativeWindow);
            PacketQIOAutomationTracking.Message decodedNegative =
                  new PacketQIOAutomationTracking.Message();
            decodedNegative.fromBytes(negativeWindow);
            assertFalse(decodedNegative.isValid());
        } finally {
            truncated.release();
            negativeWindow.release();
        }
    }

    private static void assertBindingRoundTrip(PacketQIOAutomationBinding.Message original) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
            PacketQIOAutomationBinding.Message decoded = new PacketQIOAutomationBinding.Message();
            decoded.fromBytes(encoded);
            assertTrue(decoded.isValid());
            decoded.toBytes(reencoded);
            assertArrayEquals(expected,
                  ByteBufUtil.getBytes(reencoded, reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    private static void assertTrackingRoundTrip(PacketQIOAutomationTracking.Message original) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
            PacketQIOAutomationTracking.Message decoded = new PacketQIOAutomationTracking.Message();
            decoded.fromBytes(encoded);
            assertTrue(decoded.isValid());
            decoded.toBytes(reencoded);
            assertArrayEquals(expected,
                  ByteBufUtil.getBytes(reencoded, reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    private static void assertInvalidBinding(ByteBuf buffer) {
        PacketQIOAutomationBinding.Message decoded = new PacketQIOAutomationBinding.Message();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isValid());
    }
}
