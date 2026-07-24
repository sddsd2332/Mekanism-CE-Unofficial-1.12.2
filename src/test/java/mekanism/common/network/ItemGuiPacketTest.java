package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.network.PacketSecurityMode.SecurityModeMessage;
import mekanism.common.network.PacketSecurityMode.SecurityPacketType;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterMessage;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterPacketType;
import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemGuiPacketTest {

    @Test
    void itemFrequencyPacketRoundTripsWindowBinding() {
        FrequencyIdentity identity = new FrequencyIdentity("home", SecurityMode.PUBLIC, null);
        SetItemFrequencyMessage original = new SetItemFrequencyMessage(27, true, FrequencyType.TELEPORTER, identity,
              EnumHand.OFF_HAND);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        SetItemFrequencyMessage decoded = new SetItemFrequencyMessage();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(27, decoded.windowId);
        assertTrue(decoded.set);
        assertEquals(FrequencyType.TELEPORTER, decoded.frequencyType);
        assertEquals(identity, decoded.identity);
        assertEquals(EnumHand.OFF_HAND, decoded.currentHand);
    }

    @Test
    void itemFrequencyPacketRejectsMissingOrTruncatedWindowBinding() {
        FrequencyIdentity identity = new FrequencyIdentity("home", SecurityMode.PUBLIC, null);
        assertFalse(new SetItemFrequencyMessage(true, FrequencyType.TELEPORTER, identity, EnumHand.MAIN_HAND).isValid());

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(11);
        SetItemFrequencyMessage decoded = new SetItemFrequencyMessage();
        decoded.fromBytes(truncated);

        assertFalse(decoded.isValid());
        assertEquals(-1, decoded.windowId);
    }

    @Test
    void itemSecurityPacketRoundTripsWindowBinding() {
        SecurityModeMessage original = new SecurityModeMessage(31, EnumHand.MAIN_HAND, SecurityMode.PRIVATE);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        SecurityModeMessage decoded = new SecurityModeMessage();
        decoded.fromBytes(buffer);

        assertEquals(SecurityPacketType.ITEM, decoded.packetType);
        assertEquals(31, decoded.windowId);
        assertEquals(EnumHand.MAIN_HAND, decoded.currentHand);
        assertEquals(SecurityMode.PRIVATE, decoded.value);
    }

    @Test
    void portableTeleporterPacketRoundTripsFixedSlot() {
        FrequencyIdentity identity = new FrequencyIdentity("destination", SecurityMode.PUBLIC, null);
        PortableTeleporterMessage original = new PortableTeleporterMessage(PortableTeleporterPacketType.TELEPORT,
              EnumHand.MAIN_HAND, 6, identity);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        PortableTeleporterMessage decoded = new PortableTeleporterMessage();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(EnumHand.MAIN_HAND, decoded.currentHand);
        assertEquals(6, decoded.itemSlot);
        assertEquals(identity, decoded.identity);
    }

    @Test
    void portableTeleporterPacketRejectsSlotFromWrongHand() {
        FrequencyIdentity identity = new FrequencyIdentity("destination", SecurityMode.PUBLIC, null);
        PortableTeleporterMessage invalid = new PortableTeleporterMessage(PortableTeleporterPacketType.TELEPORT,
              EnumHand.OFF_HAND, 6, identity);

        assertFalse(invalid.isValid());
    }
}
