package mekanism.qioprocessing.common.tile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.TestBootstrap;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalDeviceRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingTerminalTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @AfterEach
    void clearTerminalRegistry() {
        QIOProcessingTerminalDeviceRegistry.INSTANCE.shutdown();
    }

    @Test
    void stableIdentityAndRevisionRoundTripThroughTileAndItemData() {
        TestTerminal original = new TestTerminal();
        NBTTagCompound tileData = new NBTTagCompound();
        original.writeCustomNBT(tileData);
        TestTerminal restored = new TestTerminal();
        restored.readCustomNBT(tileData);

        assertEquals(original.getPersistentTerminalUUID(),
              restored.getPersistentTerminalUUID());
        assertEquals(0, restored.getConfigurationRevision());
        assertFalse(restored.hasDataError());
        assertEquals(QIOProcessingTerminalType.MANAGEMENT,
              restored.getTerminalType());

        NBTTagCompound sustained = new NBTTagCompound();
        restored.writeSustainedQIOData(sustained);
        TestTerminal placed = new TestTerminal();
        placed.readSustainedQIOData(sustained);
        assertEquals(restored.getPersistentTerminalUUID(),
              placed.getPersistentTerminalUUID());
    }

    @Test
    void damagedIdentityFailsClosedWithoutInventingAFrequencyBinding() {
        TestTerminal terminal = new TestTerminal();
        UUID initialIdentity = terminal.getPersistentTerminalUUID();
        NBTTagCompound damaged = new NBTTagCompound();
        terminal.writeCustomNBT(damaged);
        damaged.setString("qioProcessingTerminalUUID", "not-a-uuid");
        damaged.setLong("qioProcessingTerminalConfigRevision", -1);

        terminal.readCustomNBT(damaged);

        assertTrue(terminal.hasDataError());
        assertEquals(initialIdentity, terminal.getPersistentTerminalUUID());
        assertNull(terminal.getQIOFrequency());
    }

    @Test
    void frequencyBindingChangesArePresentInImmediateTileUpdatePayload() {
        TestTerminal terminal = new TestTerminal();
        QIOFrequency frequency = new QIOFrequency("sync", null, SecurityMode.PUBLIC);
        UUID bindingPlayer = UUID.randomUUID();

        assertTrue(terminal.applyAuthorizedBinding(frequency, bindingPlayer));
        NBTTagCompound boundPayload = terminal.updatePayload();
        assertTrue(boundPayload.hasKey(
              "qioProcessingFrequencyReference"));
        NBTTagCompound boundNetworkPayload = lastCompound(terminal.getNetworkedData());
        assertTrue(boundNetworkPayload.hasKey("qioProcessingFrequencyReference"));
        assertEncodable(terminal.getNetworkedData());

        assertTrue(terminal.applyAuthorizedBinding(null, bindingPlayer));
        NBTTagCompound unboundPayload = terminal.updatePayload();
        assertFalse(unboundPayload.hasKey(
              "qioProcessingFrequencyReference"));
        NBTTagCompound unboundNetworkPayload = lastCompound(terminal.getNetworkedData());
        assertFalse(unboundNetworkPayload.hasKey("qioProcessingFrequencyReference"));
        assertEncodable(terminal.getNetworkedData());
    }

    @Test
    void smartTerminalOwnsExactlyThreeIndependentPersistentCraftingWindows() {
        TileEntityQIOSmartProcessingTerminal original =
              new TileEntityQIOSmartProcessingTerminal();
        assertEquals(3, original.getCraftingWindows().length);
        original.getCraftingWindows()[0].getCraftingInventory()
              .setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT, 3));
        original.getCraftingWindows()[2].getCraftingInventory()
              .setInventorySlotContents(8, new ItemStack(Items.GOLD_INGOT, 2));
        NBTTagCompound data = new NBTTagCompound();
        original.writeCustomNBT(data);

        TileEntityQIOSmartProcessingTerminal restored =
              new TileEntityQIOSmartProcessingTerminal();
        restored.readCustomNBT(data);

        assertEquals(3, restored.getCraftingWindows()[0].getCraftingInventory()
              .getStackInSlot(0).getCount());
        assertTrue(restored.getCraftingWindows()[1].getCraftingInventory()
              .getStackInSlot(0).isEmpty());
        assertEquals(2, restored.getCraftingWindows()[2].getCraftingInventory()
              .getStackInSlot(8).getCount());
        assertEquals(QIOProcessingTerminalType.SMART_PROCESSING,
              restored.getTerminalType());
        assertTrue(restored.getTerminalType().hasCraftingWindows());
        assertFalse(QIOProcessingTerminalType.MANAGEMENT.hasCraftingWindows());
    }

    @Test
    void duplicatePersistentTerminalIdentityQuarantinesEveryLoadedCopy() {
        TestTerminal first = new TestTerminal();
        NBTTagCompound copiedIdentity = new NBTTagCompound();
        first.writeCustomNBT(copiedIdentity);
        TestTerminal copy = new TestTerminal();
        copy.readCustomNBT(copiedIdentity);

        QIOProcessingTerminalDeviceRegistry.INSTANCE.register(first);
        assertFalse(first.hasIdentityConflict());
        QIOProcessingTerminalDeviceRegistry.INSTANCE.register(copy);

        assertTrue(first.hasIdentityConflict());
        assertTrue(copy.hasIdentityConflict());
        assertTrue(QIOProcessingTerminalDeviceRegistry.INSTANCE.isQuarantined(
              first.getPersistentTerminalUUID()));
        QIOProcessingTerminalDeviceRegistry.INSTANCE.unregister(copy);
        assertTrue(first.hasIdentityConflict());
    }

    private static final class TestTerminal extends QIOProcessingTerminal {

        private TestTerminal() {
            super("QIOProcessingTerminalTest", QIOProcessingTerminalType.MANAGEMENT);
        }

        private NBTTagCompound updatePayload() {
            NBTTagCompound data = new NBTTagCompound();
            writeUpdateNBT(data);
            return data;
        }
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
