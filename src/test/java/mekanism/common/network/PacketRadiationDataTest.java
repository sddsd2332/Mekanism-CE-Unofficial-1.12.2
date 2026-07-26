package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.common.network.PacketRadiationData.PacketRadiationDataMessage;
import mekanism.common.network.PacketRadiationData.RadiationPacketType;
import mekanism.common.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketRadiationDataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void environmentalPacketRoundTripsTotalAndMaximumMagnitude() {
        PacketRadiationDataMessage original = PacketRadiationData.createEnvironmental(1.25, 9.5);
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketRadiationDataMessage decoded = new PacketRadiationDataMessage();
            decoded.fromBytes(buffer);

            assertEquals(RadiationPacketType.ENVIRONMENTAL, decoded.getType());
            assertEquals(1.25, decoded.getRadiation());
            assertEquals(9.5, decoded.getMaxMagnitude());
        } finally {
            buffer.release();
        }
    }

    @Test
    void playerPacketUsesDoseForBothValues() {
        PacketRadiationDataMessage original = PacketRadiationData.createPlayer(0.25);
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketRadiationDataMessage decoded = new PacketRadiationDataMessage();
            decoded.fromBytes(buffer);

            assertEquals(RadiationPacketType.PLAYER, decoded.getType());
            assertEquals(0.25, decoded.getRadiation());
            assertEquals(0.25, decoded.getMaxMagnitude());
        } finally {
            buffer.release();
        }
    }
}
