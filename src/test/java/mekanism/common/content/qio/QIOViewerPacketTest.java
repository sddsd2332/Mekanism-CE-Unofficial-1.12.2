package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.Coord4D;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.QIOGuiConstants;
import mekanism.common.network.qio.PacketQIOClearCraftingWindow;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.network.qio.PacketQIOItemViewerSlotInteract;
import mekanism.common.network.qio.PacketQIOItemViewerGuiSync;
import mekanism.common.network.qio.PacketQIOPortableGui;
import mekanism.common.network.qio.PacketQIOViewerData;
import mekanism.common.network.qio.PacketQIOViewerAction;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOViewerPacketTest {

    private File worldDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @AfterEach
    void cleanUp() throws Exception {
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void snapshotsLargerThanOnePacketAreChunkedWithoutDroppingEntries() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-viewer-packet-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(
              mekanism.common.lib.inventory.HashedItem.create(new ItemStack(Blocks.STONE)));
        QIOResourceEntry entry = QIOResourceEntry.create(resource, 1);
        List<QIOResourceEntry> entries = new ArrayList<>();
        for (int i = 0; i < PacketQIOViewerData.MAX_ENTRIES + 17; i++) {
            entries.add(entry);
        }

        int windowId = 29;
        List<PacketQIOViewerData.Message> messages = PacketQIOViewerData.createBatchMessages(windowId, entries, 9_000L, 8_192);
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).isFirstBatchChunk());
        assertFalse(messages.get(1).isFirstBatchChunk());
        assertEquals(PacketQIOViewerData.MAX_ENTRIES, messages.get(0).getEntries().size());
        assertEquals(17, messages.get(1).getEntries().size());

        int decodedEntries = 0;
        for (int i = 0; i < messages.size(); i++) {
            ByteBuf buffer = Unpooled.buffer();
            messages.get(i).toBytes(buffer);
            PacketQIOViewerData.Message decoded = new PacketQIOViewerData.Message();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isValid());
            assertEquals(windowId, decoded.getWindowId());
            assertEquals(i == 0, decoded.isFirstBatchChunk());
            assertEquals(9_000L, decoded.getTotalCountCapacity());
            assertEquals(8_192, decoded.getTotalTypeCapacity());
            decodedEntries += decoded.getEntries().size();
        }
        assertEquals(entries.size(), decodedEntries);

        List<PacketQIOViewerData.Message> updates = PacketQIOViewerData.createUpdateMessages(windowId, entries, 9_000L, 8_192);
        assertEquals(2, updates.size());
        assertEquals(PacketQIOViewerData.Mode.UPDATE, updates.get(0).getMode());
        assertEquals(PacketQIOViewerData.MAX_ENTRIES, updates.get(0).getEntries().size());
        assertEquals(17, updates.get(1).getEntries().size());
    }

    @Test
    void viewerWithoutFrequencyUsesAnEmptySnapshotAndRemainsValid() {
        List<PacketQIOViewerData.Message> messages = PacketQIOViewerData.createBatchMessages(23, null, 0, 0);
        assertEquals(1, messages.size());
        assertEquals(PacketQIOViewerData.Mode.BATCH, messages.get(0).getMode());
        assertTrue(messages.get(0).getEntries().isEmpty());
        assertEquals(0, messages.get(0).getTotalCountCapacity());
        assertEquals(0, messages.get(0).getTotalTypeCapacity());

        QIOItemViewerContainer container = new QIOItemViewerContainer(null, null);
        assertTrue(container.canInteractWith(null));
    }

    @Test
    void expandedResourceAmountsAndCapacitiesRoundTripAsOneEntry() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-viewer-expanded-amount-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(
              mekanism.common.lib.inventory.HashedItem.create(new ItemStack(Blocks.STONE)));
        QIOAmount amount = QIOAmount.LONG_MAX_VALUE.add(100);
        QIOCapacitySummary capacity = new QIOCapacitySummary(amount.add(Long.MAX_VALUE),
              QIOAmount.INT_MAX_VALUE.add(Integer.MAX_VALUE), 0, 0);
        PacketQIOViewerData.Message encoded = PacketQIOViewerData.Message.update(41,
              java.util.Collections.singletonList(QIOResourceEntry.create(resource, amount)), capacity);

        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        PacketQIOViewerData.Message decoded = new PacketQIOViewerData.Message();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(1, decoded.getEntries().size());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100)),
              decoded.getEntries().get(0).getExactAmount().toBigInteger());
        assertEquals(capacity, decoded.getCapacitySummary());
        assertEquals(Long.MAX_VALUE, decoded.getTotalCountCapacity());
        assertEquals(Integer.MAX_VALUE, decoded.getTotalTypeCapacity());
    }

    @Test
    void resourceRevisionChangesOnlyWhenTheClientSnapshotChanges() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-viewer-revision-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(
              mekanism.common.lib.inventory.HashedItem.create(new ItemStack(Blocks.STONE)));
        QIOResourceEntry entry = QIOResourceEntry.create(resource, 4);
        QIOItemViewerContainer container = new QIOItemViewerContainer(null, null) {
            @Override
            public boolean isRemote() {
                return true;
            }
        };

        assertEquals(0, container.getResourceRevision());
        container.applyBatch(java.util.Collections.singletonList(entry), 100, 10);
        assertEquals(1, container.getResourceRevision());
        container.applyUpdate(java.util.Collections.singletonList(entry), 100, 10);
        assertEquals(1, container.getResourceRevision());
        container.applyUpdate(java.util.Collections.singletonList(entry.withAmount(7)), 100, 10);
        assertEquals(2, container.getResourceRevision());
        container.applyUpdate(java.util.Collections.emptyList(), 200, 10);
        assertEquals(3, container.getResourceRevision());
        container.applyKill();
        assertEquals(4, container.getResourceRevision());
        container.applyKill();
        assertEquals(4, container.getResourceRevision());
    }

    @Test
    void clientSnapshotUsesTheSameItemFluidGasRatio() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-viewer-ratio-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        UUID item = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(
              mekanism.common.lib.inventory.HashedItem.create(new ItemStack(Blocks.STONE)));
        UUID fluid = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(new FluidStack(FluidRegistry.WATER, 1));
        UUID gas = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(new GasStack(
              new Gas("qio_viewer_ratio_test_gas", 0x55AAFF), 1));

        QIOItemViewerContainer container = new QIOItemViewerContainer(null, null) {
            @Override
            public boolean isRemote() {
                return true;
            }
        };
        container.applyBatch(java.util.Arrays.asList(
              QIOResourceEntry.create(item, 1),
              QIOResourceEntry.create(fluid, 500),
              QIOResourceEntry.create(gas, 500)), 48_000, 128);

        assertEquals(2, container.getTotalCount());
        assertEquals(48_000, container.getTotalCountCapacity());
    }

    @Test
    void viewerActionsPreserveRequestsLargerThanIntegerRange() {
        UUID resource = UUID.randomUUID();
        long requested = (long) Integer.MAX_VALUE + 1_234_567L;
        int windowId = 37;
        assertViewerActionRoundTrip(PacketQIOViewerAction.Message.take(windowId, resource, requested),
              PacketQIOViewerAction.ActionType.TAKE, windowId, resource, requested);
        assertViewerActionRoundTrip(PacketQIOViewerAction.Message.shiftTake(windowId, resource, requested),
              PacketQIOViewerAction.ActionType.SHIFT_TAKE, windowId, resource, requested);
        assertViewerActionRoundTrip(PacketQIOViewerAction.Message.put(windowId, requested),
              PacketQIOViewerAction.ActionType.PUT, windowId, null, requested);
    }

    @Test
    void viewerSnapshotsRejectInvalidHeadersAndTruncation() {
        ByteBuf unknownMode = Unpooled.buffer();
        unknownMode.writeInt(29);
        unknownMode.writeByte(255);
        PacketQIOViewerData.Message decodedUnknown = new PacketQIOViewerData.Message();
        decodedUnknown.fromBytes(unknownMode);
        assertFalse(decodedUnknown.isValid());
        assertEquals(-1, decodedUnknown.getWindowId());

        ByteBuf negativeWindow = Unpooled.buffer();
        negativeWindow.writeInt(-1);
        negativeWindow.writeByte(PacketQIOViewerData.Mode.KILL.ordinal());
        PacketQIOViewerData.Message decodedNegative = new PacketQIOViewerData.Message();
        decodedNegative.fromBytes(negativeWindow);
        assertFalse(decodedNegative.isValid());

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(29);
        truncated.writeByte(PacketQIOViewerData.Mode.UPDATE.ordinal());
        PacketQIOViewerData.Message decodedTruncated = new PacketQIOViewerData.Message();
        decodedTruncated.fromBytes(truncated);
        assertFalse(decodedTruncated.isValid());
        assertEquals(-1, decodedTruncated.getWindowId());

        ByteBuf kill = Unpooled.buffer();
        PacketQIOViewerData.Message.kill(29).toBytes(kill);
        PacketQIOViewerData.Message decodedKill = new PacketQIOViewerData.Message();
        decodedKill.fromBytes(kill);
        assertTrue(decodedKill.isValid());
        assertEquals(29, decodedKill.getWindowId());
        assertEquals(PacketQIOViewerData.Mode.KILL, decodedKill.getMode());
    }

    @Test
    void legacyViewerSnapshotsAlsoCarryAndValidateWindowIdentity() {
        ByteBuf valid = Unpooled.buffer();
        PacketQIOItemViewerGuiSync.Message.kill(31).toBytes(valid);
        PacketQIOItemViewerGuiSync.Message decodedValid = new PacketQIOItemViewerGuiSync.Message();
        decodedValid.fromBytes(valid);
        assertTrue(decodedValid.isValid());
        assertEquals(31, decodedValid.getWindowId());

        ByteBuf unknownMode = Unpooled.buffer();
        unknownMode.writeInt(31);
        unknownMode.writeByte(255);
        PacketQIOItemViewerGuiSync.Message decodedUnknown = new PacketQIOItemViewerGuiSync.Message();
        decodedUnknown.fromBytes(unknownMode);
        assertFalse(decodedUnknown.isValid());
        assertEquals(-1, decodedUnknown.getWindowId());

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(31);
        PacketQIOItemViewerGuiSync.Message decodedTruncated = new PacketQIOItemViewerGuiSync.Message();
        decodedTruncated.fromBytes(truncated);
        assertFalse(decodedTruncated.isValid());
    }

    @Test
    void malformedAndUnknownViewerActionsAreInvalidated() {
        ByteBuf unknown = Unpooled.buffer();
        unknown.writeInt(12);
        unknown.writeByte(255);
        PacketQIOViewerAction.Message decodedUnknown = new PacketQIOViewerAction.Message();
        decodedUnknown.fromBytes(unknown);
        assertFalse(decodedUnknown.isValid());
        assertEquals(-1, decodedUnknown.getWindowId());
        assertEquals(-1, decodedUnknown.getAmount());

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(12);
        truncated.writeByte(PacketQIOViewerAction.ActionType.TAKE.ordinal());
        PacketQIOViewerAction.Message decodedTruncated = new PacketQIOViewerAction.Message();
        decodedTruncated.fromBytes(truncated);
        assertFalse(decodedTruncated.isValid());
        assertEquals(-1, decodedTruncated.getWindowId());
        assertEquals(-1, decodedTruncated.getAmount());
    }

    @Test
    void takeWithoutResourceUuidIsInvalid() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(21);
        buffer.writeByte(PacketQIOViewerAction.ActionType.TAKE.ordinal());
        buffer.writeBoolean(false);
        buffer.writeLong(64);

        PacketQIOViewerAction.Message decoded = new PacketQIOViewerAction.Message();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isValid());
        assertEquals(PacketQIOViewerAction.ActionType.TAKE, decoded.getAction());
        assertEquals(21, decoded.getWindowId());
        assertNull(decoded.getResource());
        assertEquals(64, decoded.getAmount());
    }

    @Test
    void legacyViewerInteractionsRejectUnknownAndIncompleteRequests() {
        ByteBuf unknown = Unpooled.buffer();
        unknown.writeInt(12);
        unknown.writeByte(255);
        PacketQIOItemViewerSlotInteract.Message decodedUnknown = new PacketQIOItemViewerSlotInteract.Message();
        decodedUnknown.fromBytes(unknown);
        assertFalse(decodedUnknown.isValid());
        assertEquals(-1, decodedUnknown.getWindowId());
        assertEquals(-1, decodedUnknown.getAmount());

        ByteBuf missingResource = Unpooled.buffer();
        missingResource.writeInt(12);
        missingResource.writeByte(PacketQIOItemViewerSlotInteract.Type.TAKE.ordinal());
        missingResource.writeBoolean(false);
        missingResource.writeLong(64);
        PacketQIOItemViewerSlotInteract.Message decodedMissing = new PacketQIOItemViewerSlotInteract.Message();
        decodedMissing.fromBytes(missingResource);
        assertFalse(decodedMissing.isValid());

        ByteBuf valid = Unpooled.buffer();
        UUID resource = UUID.randomUUID();
        PacketQIOItemViewerSlotInteract.Message.shiftTake(12, resource, 64).toBytes(valid);
        PacketQIOItemViewerSlotInteract.Message decodedValid = new PacketQIOItemViewerSlotInteract.Message();
        decodedValid.fromBytes(valid);
        assertTrue(decodedValid.isValid());
        assertEquals(PacketQIOItemViewerSlotInteract.Type.SHIFT_TAKE, decodedValid.getType());
        assertEquals(resource, decodedValid.getResource());
    }

    @Test
    void componentConfigurationRejectsUnknownActionsAndInvalidPayloads() {
        Coord4D coord = new Coord4D(1, 2, 3, 0);
        ByteBuf unknown = Unpooled.buffer();
        coord.write(unknown);
        unknown.writeByte(255);
        PacketQIOComponentConfig.Message decodedUnknown = new PacketQIOComponentConfig.Message();
        decodedUnknown.fromBytes(unknown);
        assertFalse(decodedUnknown.isValid());
        assertNull(decodedUnknown.getCoord());

        ByteBuf missingFilter = Unpooled.buffer();
        coord.write(missingFilter);
        missingFilter.writeByte(PacketQIOComponentConfig.ConfigAction.SET_FILTER.ordinal());
        missingFilter.writeLong(0);
        missingFilter.writeInt(0);
        missingFilter.writeBoolean(true);
        missingFilter.writeBoolean(false);
        PacketQIOComponentConfig.Message decodedMissingFilter = new PacketQIOComponentConfig.Message();
        decodedMissingFilter.fromBytes(missingFilter);
        assertFalse(decodedMissingFilter.isValid());

        ByteBuf emptyFilter = Unpooled.buffer();
        coord.write(emptyFilter);
        emptyFilter.writeByte(PacketQIOComponentConfig.ConfigAction.ADD_FILTER.ordinal());
        emptyFilter.writeLong(0);
        emptyFilter.writeInt(0);
        emptyFilter.writeBoolean(true);
        emptyFilter.writeBoolean(true);
        net.minecraft.nbt.NBTTagCompound emptyFilterData = new net.minecraft.nbt.NBTTagCompound();
        emptyFilterData.setInteger("version", 1);
        emptyFilterData.setString("type", "item");
        emptyFilterData.setTag("payload", new net.minecraft.nbt.NBTTagCompound());
        mekanism.common.PacketHandler.writeNBT(emptyFilter, emptyFilterData);
        PacketQIOComponentConfig.Message decodedEmptyFilter = new PacketQIOComponentConfig.Message();
        decodedEmptyFilter.fromBytes(emptyFilter);
        assertFalse(decodedEmptyFilter.isValid());

        ByteBuf negativeIndex = Unpooled.buffer();
        coord.write(negativeIndex);
        negativeIndex.writeByte(PacketQIOComponentConfig.ConfigAction.REMOVE_FILTER.ordinal());
        negativeIndex.writeLong(0);
        negativeIndex.writeInt(-1);
        negativeIndex.writeBoolean(false);
        negativeIndex.writeBoolean(false);
        PacketQIOComponentConfig.Message decodedNegativeIndex = new PacketQIOComponentConfig.Message();
        decodedNegativeIndex.fromBytes(negativeIndex);
        assertFalse(decodedNegativeIndex.isValid());

        ByteBuf editWithoutIndex = Unpooled.buffer();
        coord.write(editWithoutIndex);
        editWithoutIndex.writeByte(PacketQIOComponentConfig.ConfigAction.EDIT_FILTER.ordinal());
        editWithoutIndex.writeLong(0);
        editWithoutIndex.writeInt(-1);
        editWithoutIndex.writeBoolean(true);
        editWithoutIndex.writeBoolean(true);
        net.minecraft.nbt.NBTTagCompound validFilter = new mekanism.common.content.qio.filter.QIOItemStackFilter(
              new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.STONE)).write();
        mekanism.common.PacketHandler.writeNBT(editWithoutIndex, validFilter);
        PacketQIOComponentConfig.Message decodedEditWithoutIndex = new PacketQIOComponentConfig.Message();
        decodedEditWithoutIndex.fromBytes(editWithoutIndex);
        assertFalse(decodedEditWithoutIndex.isValid());
    }

    @Test
    void portableGuiPacketRejectsUnknownHandsAndGuiIds() {
        ByteBuf unknownHand = Unpooled.buffer();
        unknownHand.writeInt(19);
        unknownHand.writeByte(255);
        unknownHand.writeInt(QIOGuiConstants.PORTABLE_DASHBOARD);
        PacketQIOPortableGui.Message decodedUnknownHand = new PacketQIOPortableGui.Message();
        decodedUnknownHand.fromBytes(unknownHand);
        assertFalse(decodedUnknownHand.isValid());
        assertEquals(-1, decodedUnknownHand.getGuiId());

        ByteBuf unsupportedGui = Unpooled.buffer();
        unsupportedGui.writeInt(19);
        unsupportedGui.writeByte(EnumHand.MAIN_HAND.ordinal());
        unsupportedGui.writeInt(Integer.MAX_VALUE);
        PacketQIOPortableGui.Message decodedUnsupported = new PacketQIOPortableGui.Message();
        decodedUnsupported.fromBytes(unsupportedGui);
        assertFalse(decodedUnsupported.isValid());

        ByteBuf valid = Unpooled.buffer();
        new PacketQIOPortableGui.Message(19, EnumHand.OFF_HAND, QIOGuiConstants.PORTABLE_FREQUENCY).toBytes(valid);
        PacketQIOPortableGui.Message decodedValid = new PacketQIOPortableGui.Message();
        decodedValid.fromBytes(valid);
        assertTrue(decodedValid.isValid());
        assertEquals(19, decodedValid.getWindowId());
        assertEquals(EnumHand.OFF_HAND, decodedValid.getHand());
    }

    @Test
    void clearCraftingWindowPacketRejectsInvalidWindowsAndTruncation() {
        ByteBuf invalidWindow = Unpooled.buffer();
        invalidWindow.writeInt(17);
        invalidWindow.writeByte(IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS);
        invalidWindow.writeBoolean(false);
        PacketQIOClearCraftingWindow.Message decodedInvalid = new PacketQIOClearCraftingWindow.Message();
        decodedInvalid.fromBytes(invalidWindow);
        assertFalse(decodedInvalid.isValid());

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(17);
        PacketQIOClearCraftingWindow.Message decodedTruncated = new PacketQIOClearCraftingWindow.Message();
        decodedTruncated.fromBytes(truncated);
        assertFalse(decodedTruncated.isValid());
        assertEquals(-1, decodedTruncated.getWindowId());

        ByteBuf valid = Unpooled.buffer();
        new PacketQIOClearCraftingWindow.Message(17, (byte) 2, true).toBytes(valid);
        PacketQIOClearCraftingWindow.Message decodedValid = new PacketQIOClearCraftingWindow.Message();
        decodedValid.fromBytes(valid);
        assertTrue(decodedValid.isValid());
        assertEquals(17, decodedValid.getWindowId());
        assertEquals(2, decodedValid.getWindow());
        assertTrue(decodedValid.isToPlayerInventory());
    }

    private static void assertViewerActionRoundTrip(PacketQIOViewerAction.Message encoded,
          PacketQIOViewerAction.ActionType action, int windowId, UUID resource, long amount) {
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        PacketQIOViewerAction.Message decoded = new PacketQIOViewerAction.Message();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isValid());
        assertEquals(action, decoded.getAction());
        assertEquals(windowId, decoded.getWindowId());
        assertEquals(resource, decoded.getResource());
        assertEquals(amount, decoded.getAmount());
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
