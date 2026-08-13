package mekanism.qioprocessing.api.processor;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.processor.QIOCraftingProcessorState;
import mekanism.qioprocessing.common.content.processor.QIOProcessorLaneRuntime;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingProcessor;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingProcessorStateTest {

    private static final ResourceLocation HOST_ID = new ResourceLocation("test", "processor_host");
    private static final ResourceLocation LARGE_DEFINITION_ID =
          new ResourceLocation("test", "large_processor");

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @BeforeEach
    void setup() {
        QIOCraftingProcessorHostRegistry.resetForTests();
        QIOCraftingProcessorRegistry.resetForTests();
        QIOCraftingProcessorRegistry.bootstrapBuiltins(Long.MAX_VALUE);
    }

    @AfterEach
    void cleanup() {
        QIOCraftingProcessorHostRegistry.resetForTests();
        QIOCraftingProcessorRegistry.resetForTests();
    }

    @Test
    void longMaxLaneDefinitionUsesSparseStateAndWrapsWithoutOverflow() throws Exception {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(LARGE_DEFINITION_ID, Long.MAX_VALUE));
        QIOCraftingProcessorState empty = QIOCraftingProcessorState.create(HOST_ID, definition);
        NBTTagCompound stored = empty.write();
        stored.setLong("nextLaneCandidate", Long.MAX_VALUE - 1);

        QIOCraftingProcessorState restored = QIOCraftingProcessorState.read(stored, HOST_ID,
              LARGE_DEFINITION_ID);
        QIOProcessorLaneRuntime last = restored.acquireLane(UUID.randomUUID(), UUID.randomUUID(),
              1, "test:last");
        QIOProcessorLaneRuntime wrapped = restored.acquireLane(UUID.randomUUID(), UUID.randomUUID(),
              1, "test:wrapped");

        assertNotNull(last);
        assertNotNull(wrapped);
        assertEquals(Long.MAX_VALUE - 1, last.getLaneId());
        assertEquals(0, wrapped.getLaneId());
        assertEquals(2, restored.getActiveLanes().size());
        assertEquals(2, restored.write().getTagList("activeLanes", 10).tagCount());
    }

    @Test
    void laneBuffersAndTransferReceiptsAreDurableAndIdempotent() throws Exception {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(
              QIOCraftingProcessorRegistry.ORDINARY_ID);
        QIOCraftingProcessorState state = QIOCraftingProcessorState.create(HOST_ID, definition);
        UUID operationId = UUID.randomUUID();
        QIOProcessorLaneRuntime lane = state.acquireLane(operationId, UUID.randomUUID(), 1,
              "minecraft:stone_to_dirt");
        PortableResourceDescriptor stone = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        PortableResourceDescriptor dirt = PortableResourceDescriptor.item(new ItemStack(Blocks.DIRT));
        UUID inputTransfer = UUID.randomUUID();
        Map<PortableResourceDescriptor, Long> input = Collections.singletonMap(stone, 3L);

        assertTrue(lane.creditInput(inputTransfer, input));
        assertFalse(lane.creditInput(inputTransfer, input));
        assertThrows(IllegalStateException.class, () -> lane.creditInput(inputTransfer,
              Collections.singletonMap(stone, 2L)));
        lane.markReady();
        lane.beginProcessing(5);
        assertEquals(5, lane.advanceProcessing(20));
        lane.completeProcessing(Collections.singletonMap(dirt, 2L));

        QIOCraftingProcessorState restored = QIOCraftingProcessorState.read(state.write(), HOST_ID,
              QIOCraftingProcessorRegistry.ORDINARY_ID);
        QIOProcessorLaneRuntime restoredLane = restored.getLane(lane.getLaneId());
        assertNotNull(restoredLane);
        assertEquals(QIOProcessorLaneRuntime.State.OUTPUT_BLOCKED, restoredLane.getState());
        assertTrue(restoredLane.getInput().isEmpty());
        assertEquals(2, restoredLane.getOutput().get(dirt));
        assertEquals(1, restoredLane.getTransferReceiptCount());

        UUID outputTransfer = UUID.randomUUID();
        assertTrue(restoredLane.debitOutput(outputTransfer, Collections.singletonMap(dirt, 2L)));
        assertFalse(restoredLane.debitOutput(outputTransfer, Collections.singletonMap(dirt, 2L)));
        assertTrue(restoredLane.isSettled());
        assertEquals(QIOProcessorLaneRuntime.Settlement.OUTPUT,
              restoredLane.getSettlement());
        assertFalse(restored.isPersistedSettledOperation(operationId));
        restored.confirmSettledLanesPersisted();
        assertTrue(restored.isPersistedSettledOperation(operationId));

        NBTTagCompound missingSettlement = restored.write();
        missingSettlement.getTagList("activeLanes", 10).getCompoundTagAt(0)
              .removeTag("settlement");
        assertThrows(mekanism.qioprocessing.common.content.QIOProcessingDataException.class,
              () -> QIOCraftingProcessorState.read(missingSettlement, HOST_ID,
                    QIOCraftingProcessorRegistry.ORDINARY_ID));
        NBTTagCompound oldSchema = restored.write();
        oldSchema.setInteger("processorStateSchemaVersion", 1);
        assertThrows(mekanism.qioprocessing.common.content.QIOProcessingDataException.class,
              () -> QIOCraftingProcessorState.read(oldSchema, HOST_ID,
                    QIOCraftingProcessorRegistry.ORDINARY_ID));

        QIOCraftingProcessorState settledReload = QIOCraftingProcessorState.read(
              restored.write(), HOST_ID, QIOCraftingProcessorRegistry.ORDINARY_ID);
        assertTrue(settledReload.isPersistedSettledOperation(operationId));
        assertEquals(QIOProcessorLaneRuntime.Settlement.OUTPUT,
              settledReload.getLane(restoredLane.getLaneId()).getSettlement());
        assertTrue(settledReload.removeSettledLane(restoredLane.getLaneId(), operationId));
        assertTrue(settledReload.getActiveLanes().isEmpty());
    }

    @Test
    void missingDefinitionKeepsBuffersAndCanResolveWhenRegistrationReturns() throws Exception {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(LARGE_DEFINITION_ID, 12));
        QIOCraftingProcessorState state = QIOCraftingProcessorState.create(HOST_ID, definition);
        QIOProcessorLaneRuntime lane = state.acquireLane(UUID.randomUUID(), UUID.randomUUID(), 1,
              "test:recoverable");
        PortableResourceDescriptor stone = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        lane.creditInput(UUID.randomUUID(), Collections.singletonMap(stone, 4L));
        NBTTagCompound stored = state.write();

        QIOCraftingProcessorRegistry.resetForTests();
        QIOCraftingProcessorRegistry.bootstrapBuiltins(Long.MAX_VALUE);
        QIOCraftingProcessorState unresolved = QIOCraftingProcessorState.read(stored, HOST_ID,
              LARGE_DEFINITION_ID);

        assertEquals(QIOCraftingProcessorState.State.UNRESOLVED_DEFINITION, unresolved.getState());
        assertNull(unresolved.getResolvedDefinition());
        assertEquals(4, unresolved.getLane(0).getInput().get(stone));
        assertNull(unresolved.acquireLane(UUID.randomUUID(), UUID.randomUUID(), 1, "test:blocked"));

        QIOCraftingProcessorRegistry.register(new QIOCraftingProcessorDefinition(LARGE_DEFINITION_ID, 12));
        assertTrue(unresolved.reconcile(HOST_ID, LARGE_DEFINITION_ID));
        assertEquals(4, unresolved.getLane(0).getInput().get(stone));
    }

    @Test
    void changedDefinitionSignatureAndHostMismatchAreIsolated() throws Exception {
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(LARGE_DEFINITION_ID, 12));
        QIOCraftingProcessorState state = QIOCraftingProcessorState.create(HOST_ID, definition);
        state.acquireLane(UUID.randomUUID(), UUID.randomUUID(), 1, "test:signature");
        NBTTagCompound stored = state.write();

        QIOCraftingProcessorRegistry.resetForTests();
        QIOCraftingProcessorRegistry.bootstrapBuiltins(Long.MAX_VALUE);
        QIOCraftingProcessorRegistry.register(new QIOCraftingProcessorDefinition(LARGE_DEFINITION_ID, 13));
        QIOCraftingProcessorState changed = QIOCraftingProcessorState.read(stored, HOST_ID,
              LARGE_DEFINITION_ID);
        QIOCraftingProcessorState wrongHost = QIOCraftingProcessorState.read(stored,
              new ResourceLocation("test", "other_host"), LARGE_DEFINITION_ID);

        assertEquals(QIOCraftingProcessorState.State.UNRESOLVED_DEFINITION, changed.getState());
        assertTrue(changed.getDiagnostic().contains("signature"));
        assertEquals(1, changed.getActiveLanes().size());
        assertEquals(QIOCraftingProcessorState.State.UNRESOLVED_DEFINITION, wrongHost.getState());
        assertTrue(wrongHost.getDiagnostic().contains("does not match"));
    }

    @Test
    void processorTilePersistsLaneStateButKeepsItOutOfChunkUpdateTags() {
        TestProcessorTile tile = new TestProcessorTile();
        QIOProcessorLaneRuntime lane = tile.getProcessorState().acquireLane(UUID.randomUUID(),
              UUID.randomUUID(), 1, "test:tile_persistence");
        PortableResourceDescriptor stone = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        lane.creditInput(UUID.randomUUID(), Collections.singletonMap(stone, 7L));

        NBTTagCompound persisted = tile.writePersistentForTest();
        NBTTagCompound updateTag = tile.writeUpdateForTest();
        TestProcessorTile restored = new TestProcessorTile();
        restored.readPersistentForTest(persisted);

        assertTrue(persisted.hasKey("qioCraftingProcessorState", 10));
        assertFalse(updateTag.hasKey("qioCraftingProcessorState"));
        assertEquals(7, restored.getProcessorState().getLane(0).getInput().get(stone));
        assertEquals(tile.getProcessorState().getProcessorUUID(),
              restored.getProcessorState().getProcessorUUID());
    }

    @Test
    void hostRegistrySelectsMostSpecificTileAndRejectsLateOrDuplicateEntries() {
        ResourceLocation baseHost = new ResourceLocation("test", "base_host");
        ResourceLocation childHost = new ResourceLocation("test", "child_host");
        QIOCraftingProcessorHostRegistration<BaseTile> base =
              QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                    baseHost, "test", BaseTile.class, QIOCraftingProcessorRegistry.ORDINARY_ID));
        QIOCraftingProcessorHostRegistration<ChildTile> child =
              QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                    childHost, "test", ChildTile.class, QIOCraftingProcessorRegistry.BASIC_ID));

        QIOCraftingProcessorHostRegistry.ResolvedHost resolvedBase =
              QIOCraftingProcessorHostRegistry.resolve(new BaseTile());
        QIOCraftingProcessorHostRegistry.ResolvedHost resolvedChild =
              QIOCraftingProcessorHostRegistry.resolve(new ChildTile());

        assertEquals(base.getHostId(), resolvedBase.getHostId());
        assertEquals(child.getHostId(), resolvedChild.getHostId());
        assertSame(QIOCraftingProcessorRegistry.get(QIOCraftingProcessorRegistry.BASIC_ID),
              resolvedChild.getDefinition());
        assertThrows(IllegalArgumentException.class, () ->
              QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                    baseHost, "test", OtherTile.class, QIOCraftingProcessorRegistry.ORDINARY_ID)));
        assertThrows(IllegalArgumentException.class, () ->
              QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                    new ResourceLocation("test", "duplicate_tile"), "test", BaseTile.class,
                    QIOCraftingProcessorRegistry.ORDINARY_ID)));

        QIOCraftingProcessorHostRegistry.freeze();
        assertThrows(IllegalStateException.class, () ->
              QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                    new ResourceLocation("test", "late"), "test", OtherTile.class,
                    QIOCraftingProcessorRegistry.ORDINARY_ID)));
    }

    private static class BaseTile extends TileEntity {
    }

    private static final class ChildTile extends BaseTile {
    }

    private static final class OtherTile extends TileEntity {
    }

    private static final class TestProcessorTile extends TileEntityQIOCraftingProcessor {

        private NBTTagCompound writePersistentForTest() {
            NBTTagCompound data = new NBTTagCompound();
            writeCustomNBT(data);
            return data;
        }

        private NBTTagCompound writeUpdateForTest() {
            NBTTagCompound data = new NBTTagCompound();
            writeUpdateNBT(data);
            return data;
        }

        private void readPersistentForTest(NBTTagCompound data) {
            readCustomNBT(data);
        }
    }
}
