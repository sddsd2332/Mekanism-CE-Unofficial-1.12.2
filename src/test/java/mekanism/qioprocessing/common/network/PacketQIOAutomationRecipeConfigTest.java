package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mekanism.api.Coord4D;
import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigClientCache;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigSnapshot;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.network.PacketQIOAutomationRecipeConfig.QIOAutomationRecipeConfigMessage;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketQIOAutomationRecipeConfigTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void aeStyleSingleMessageRoundTripsMutation() {
        QIOAutomationRecipeProfileMutation mutation =
              QIOAutomationRecipeProfileMutation.moveRoute("product", "route", 1);
        assertRoundTrip(QIOAutomationRecipeConfigMessage.mutate(
                    new Coord4D(new BlockPos(3, 4, 5), 0),
                    QIOAutomationRecipeConfigType.PASSIVE, UUID.randomUUID(), 12L,
                    mutation, 64, 64, "recipe"));
    }

    @Test
    void sameMessageTypeRoundTripsRequestsAndBothSnapshotShapes() {
        Coord4D coord = new Coord4D(new BlockPos(-8, 70, 11), 3);
        assertRoundTrip(QIOAutomationRecipeConfigMessage.request(coord,
              QIOAutomationRecipeConfigType.SCHEDULED, UUID.randomUUID(), 0, 64, ""));

        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationRecipeConfigSnapshot snapshot = new QIOAutomationRecipeConfigSnapshot(
              QIOAutomationRecipeConfigType.SCHEDULED, UUID.randomUUID(), deviceUUID,
              "test:provider", 0, 0, 0, 0, "", false, 1,
              RouteFilterMode.BLACKLIST, true, true, 1, 1, Collections.emptyList());
        assertRoundTrip(QIOAutomationRecipeConfigMessage.snapshot(coord,
              QIOAutomationRecipeConfigType.SCHEDULED, UUID.randomUUID(),
              QIOAutomationRecipeConfigClientCache.Status.OK, snapshot));
        assertRoundTrip(QIOAutomationRecipeConfigMessage.snapshot(coord,
              QIOAutomationRecipeConfigType.SCHEDULED, UUID.randomUUID(),
              QIOAutomationRecipeConfigClientCache.Status.UNAVAILABLE, null));
    }

    @Test
    void damagedOrContradictoryProtocolShapesAreRejected() {
        ByteBuf invalidDiscriminator = Unpooled.buffer();
        try {
            invalidDiscriminator.writeInt(Integer.MAX_VALUE);
            QIOAutomationRecipeConfigMessage decoded = new QIOAutomationRecipeConfigMessage();
            decoded.fromBytes(invalidDiscriminator);
            assertFalse(decoded.isValid());
        } finally {
            invalidDiscriminator.release();
        }

        assertFalse(QIOAutomationRecipeConfigMessage.snapshot(
              new Coord4D(new BlockPos(0, 0, 0), 0),
              QIOAutomationRecipeConfigType.SCHEDULED, UUID.randomUUID(),
              QIOAutomationRecipeConfigClientCache.Status.OK, null).isValid());
    }

    private static void assertRoundTrip(QIOAutomationRecipeConfigMessage original) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            byte[] expected = ByteBufUtil.getBytes(buffer, buffer.readerIndex(),
                  buffer.readableBytes());
            QIOAutomationRecipeConfigMessage decoded =
                  new QIOAutomationRecipeConfigMessage();
            decoded.fromBytes(buffer);
            assertTrue(original.isValid());
            assertTrue(decoded.isValid());
            decoded.toBytes(reencoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(reencoded,
                  reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            buffer.release();
            reencoded.release();
        }
    }
}
