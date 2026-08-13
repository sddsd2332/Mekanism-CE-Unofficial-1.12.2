package mekanism.qioprocessing.common.machine;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOTransferResult;
import mekanism.common.TestBootstrap;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.QIODriveStorage;
import mekanism.common.content.qio.QIODriveType;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.item.ItemQIODrive;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.machine.TileEntityAmbientAccumulator;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.tile.prefab.MekanismMachineRecipeProviders;
import mekanism.common.tier.QIODriveTier;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.api.machine.QIOOutputBufferEntry;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomaticOutputServiceTest {

    private static Gas testGas;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        MekanismMachineRecipeProviders.register();
        testGas = GasRegistry.getGas("qio_automatic_output_test_gas");
        if (testGas == null) {
            testGas = GasRegistry.register(new Gas("qio_automatic_output_test_gas", 0x4AA3DF));
        }
    }

    private File worldDirectory;
    private QIOFrequency frequency;
    private QIOFrequencyReference reference;

    @BeforeEach
    void setup() throws Exception {
        FrequencyManager.reset();
        QIOProcessingNetworkManager.INSTANCE.resetForTests();
        worldDirectory = Files.createTempDirectory("qio-automatic-output-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIOProcessingNetworkManager.INSTANCE.createOrLoad(worldDirectory);

        frequency = new QIOFrequency("automatic_output", null, SecurityMode.PUBLIC);
        frequency.addHolder(new TestDriveHolder(Collections.singletonList(
              new ItemStack(new ItemQIODrive(QIODriveTier.BASE)))));
        frequency.refresh();
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(null, SecurityMode.PUBLIC);
        assertNotNull(manager);
        manager.addFrequency(frequency);
        reference = QIOFrequencyStorageAccess.INSTANCE.createReference(frequency, null);
    }

    @AfterEach
    void cleanup() throws Exception {
        FrequencyManager.reset();
        QIOProcessingNetworkManager.INSTANCE.resetForTests();
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        delete(worldDirectory);
    }

    @Test
    void realDigitalMinerProviderMovesItemsInBoundedSteps() {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.IRON_INGOT, 9));
        DefaultQIOAutomationHost host = boundHost(miner);

        runStep(host, miner, 1, 4);
        assertEquals(5, miner.getMiningOutputSlots().get(0).getCount());
        assertEquals(0, frequency.getStored(new ItemStack(Items.IRON_INGOT)));
        assertEquals(QIOOutputBufferEntry.Phase.HELD, onlyBuffer(host).phase());

        runStep(host, miner, 2, 4);
        assertEquals(4, frequency.getStored(new ItemStack(Items.IRON_INGOT)));
        assertTrue(host.getOutputBufferEntries().isEmpty());

        runStep(host, miner, 3, 4);
        runStep(host, miner, 4, 4);
        runStep(host, miner, 5, 4);
        runStep(host, miner, 6, 4);

        assertEquals(0, miner.getMiningOutputSlots().get(0).getCount());
        assertEquals(9, frequency.getStored(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void realElectricPumpProviderMovesFluids() {
        TileEntityElectricPump pump = new TileEntityElectricPump();
        pump.fluidTank.setStack(new FluidStack(FluidRegistry.WATER, 2_500));
        DefaultQIOAutomationHost host = boundHost(pump);

        runStep(host, pump, 1, 1_024);
        runStep(host, pump, 2, 1_024);

        assertEquals(1_476, pump.fluidTank.getFluidAmount());
        assertEquals(1_024, frequency.getStored(new FluidStack(FluidRegistry.WATER, 1)));
    }

    @Test
    void realAmbientAccumulatorProviderMovesGases() {
        TileEntityAmbientAccumulator accumulator = new TileEntityAmbientAccumulator();
        accumulator.collectedGas.setStack(new GasStack(testGas, 1_500));
        DefaultQIOAutomationHost host = boundHost(accumulator);

        runStep(host, accumulator, 1, 700);
        runStep(host, accumulator, 2, 700);

        assertEquals(800, accumulator.collectedGas.getStored());
        assertEquals(700, frequency.getStored(new GasStack(testGas, 1)));
    }

    @Test
    void fullQioRetainsThePersistentIntermediateBuffer() {
        long capacity = QIODriveType.MIXED.getCountCapacity(QIODriveTier.BASE);
        assertEquals(capacity, frequency.massInsert(new ItemStack(Blocks.STONE), capacity, Action.EXECUTE));
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.GOLD_INGOT, 6));
        DefaultQIOAutomationHost host = boundHost(miner);

        runStep(host, miner, 1, 4);
        runStep(host, miner, 2, 4);

        QIOOutputBufferEntry blocked = onlyBuffer(host);
        assertEquals(QIOOutputBufferEntry.Phase.HELD, blocked.phase());
        assertEquals(4, blocked.amount());
        assertEquals(2, miner.getMiningOutputSlots().get(0).getCount());
        assertEquals(0, frequency.getStored(new ItemStack(Items.GOLD_INGOT)));

        assertEquals(4, frequency.massExtract(new ItemStack(Blocks.STONE), 4, Action.EXECUTE));
        runStep(host, miner, 3, 4);
        assertTrue(host.getOutputBufferEntries().isEmpty());
        assertEquals(4, frequency.getStored(new ItemStack(Items.GOLD_INGOT)));
    }

    @Test
    void preparedRestartUsesThePersistedBoundedExtraction() throws Exception {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.DIAMOND, 10));
        DefaultQIOAutomationHost host = boundHost(miner);
        PreparedTransfer prepared = prepareTransfer(host, miner, 4, 7);
        QIOProcessingNetworkManager.INSTANCE.flushNetwork(frequency.getFrequencyUUID());

        DefaultQIOAutomationHost restored = restoreHost(host, miner);
        reloadProcessingNetwork();
        runStep(restored, miner, 8, 4);

        assertEquals(6, miner.getMiningOutputSlots().get(0).getCount());
        assertEquals(QIOOutputBufferEntry.Phase.HELD, onlyBuffer(restored).phase());
        QIODurableTransferRecord restoredTransfer = processingNetwork().getDurableTransfer(prepared.bufferId);
        assertNotNull(restoredTransfer);
        assertEquals(QIODurableTransferRecord.Resolution.FORWARD_COMMITTED,
              restoredTransfer.getResolution());

        runStep(restored, miner, 9, 4);
        assertEquals(4, frequency.getStored(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void heldRestartFlushesTheBufferBeforeExtractingAgain() throws Exception {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.EMERALD, 7));
        DefaultQIOAutomationHost host = boundHost(miner);
        runStep(host, miner, 1, 4);
        assertEquals(QIOOutputBufferEntry.Phase.HELD, onlyBuffer(host).phase());

        DefaultQIOAutomationHost restored = restoreHost(host, miner);
        reloadProcessingNetwork();
        runStep(restored, miner, 2, 4);

        assertEquals(4, frequency.getStored(new ItemStack(Items.EMERALD)));
        assertEquals(3, miner.getMiningOutputSlots().get(0).getCount());
        assertTrue(restored.getOutputBufferEntries().isEmpty());
    }

    @Test
    void deliveringRestartReplaysTheQioReceiptWithoutDuplicatingResources() throws Exception {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.QUARTZ, 5));
        DefaultQIOAutomationHost host = boundHost(miner);
        runStep(host, miner, 1, 5);
        QIOOutputBufferEntry held = onlyBuffer(host);
        UUID transferId = UUID.randomUUID();
        assertTrue(host.beginOutputDelivery(held.bufferId(), transferId, held.amount()));
        QIOOutputBufferEntry delivering = onlyBuffer(host);

        QIODurableTransferRecord delivery = new QIODurableTransferRecord(transferId, UUID.randomUUID(),
              QIODurableTransferRecord.Type.JOB_TO_QIO, null, delivering.operationId(), 0,
              "automatic-output", delivering.leaseId(),
              "output-buffer/" + host.getPersistentDeviceUUID() + '/' + delivering.bufferId(),
              "qio/" + frequency.getFrequencyUUID(),
              Collections.singletonMap(delivering.resource(), delivering.qioRequestedAmount()),
              Collections.singletonMap(delivering.resource(), BigInteger.ZERO));
        processingNetwork().addDurableTransfer(delivery);
        delivery.markSourceDebited("output-buffer/" + host.getPersistentDeviceUUID() + '/' + delivering.bufferId(),
              host.getConfigurationRevision());
        processingNetwork().markTransferChanged(transferId);
        try (StorageViewHandle handle = openView()) {
            QIOTransferResult result = handle.view.insertIdempotent(transferId,
                  delivering.resource().resolveItem(), delivering.qioRequestedAmount(),
                  BigInteger.ZERO);
            assertTrue(result.isSuccess());
            delivery.markDestinationCredited("qio/" + frequency.getFrequencyUUID() + "/receipt/" + transferId);
            processingNetwork().markTransferChanged(transferId);
        }
        QIOProcessingNetworkManager.INSTANCE.flushNetwork(frequency.getFrequencyUUID());
        assertEquals(5, frequency.getStored(new ItemStack(Items.QUARTZ)));

        DefaultQIOAutomationHost restored = restoreHost(host, miner);
        reloadProcessingNetwork();
        runStep(restored, miner, 2, 5);

        assertEquals(5, frequency.getStored(new ItemStack(Items.QUARTZ)));
        assertTrue(restored.getOutputBufferEntries().isEmpty());
        QIODurableTransferRecord replayed = processingNetwork().getDurableTransfer(transferId);
        assertNotNull(replayed);
        assertEquals(QIODurableTransferRecord.Resolution.FORWARD_COMMITTED, replayed.getResolution());
    }

    @Test
    void heldTileRollbackReusesTheCommittedQioTransfer() throws Exception {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.DIAMOND, 5));
        DefaultQIOAutomationHost host = boundHost(miner);

        runStep(host, miner, 1, 5);
        NBTTagCompound heldState = host.serializeNBT().copy();
        runStep(host, miner, 2, 5);
        assertEquals(5, frequency.getStored(new ItemStack(Items.DIAMOND)));
        UUID deliveryId = processingNetwork().getDurableTransfers().stream()
              .filter(transfer -> transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_QIO)
              .findFirst().orElseThrow(AssertionError::new).getTransferId();

        DefaultQIOAutomationHost restored = restoreHost(heldState, miner);
        reloadProcessingNetwork();
        runStep(restored, miner, 3, 5);

        assertEquals(5, frequency.getStored(new ItemStack(Items.DIAMOND)));
        assertTrue(restored.getOutputBufferEntries().isEmpty());
        assertTrue(restored.getOperationTokens().values().iterator().next()
              .hasTransferReceipt(deliveryId));
        assertEquals(1, processingNetwork().getDurableTransfers().stream()
              .filter(transfer -> transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_QIO)
              .count());
    }

    @Test
    void wholeTileRollbackRebuildsTheOriginalOutputOperation() throws Exception {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.EMERALD, 5));
        DefaultQIOAutomationHost host = boundHost(miner);
        NBTTagCompound beforeOperation = host.serializeNBT().copy();

        runStep(host, miner, 1, 5);
        UUID originalBufferId = onlyBuffer(host).bufferId();
        runStep(host, miner, 2, 5);
        assertEquals(5, frequency.getStored(new ItemStack(Items.EMERALD)));

        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.EMERALD, 5));
        DefaultQIOAutomationHost restored = restoreHost(beforeOperation, miner);
        reloadProcessingNetwork();
        runStep(restored, miner, 3, 5);

        assertEquals(0, miner.getMiningOutputSlots().get(0).getCount());
        assertEquals(originalBufferId, onlyBuffer(restored).bufferId());
        assertEquals(5, frequency.getStored(new ItemStack(Items.EMERALD)));

        runStep(restored, miner, 4, 5);
        assertEquals(0, miner.getMiningOutputSlots().get(0).getCount());
        assertEquals(5, frequency.getStored(new ItemStack(Items.EMERALD)));
        assertTrue(restored.getOutputBufferEntries().isEmpty());
        assertEquals(1, processingNetwork().getDurableTransfers().stream()
              .filter(transfer -> transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_QIO)
              .count());
    }

    @Test
    void persistedCompletionPrunesItsDurableTransferHistory() throws Exception {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        miner.getMiningOutputSlots().get(0).setStack(new ItemStack(Items.QUARTZ, 4));
        DefaultQIOAutomationHost host = boundHost(miner);
        runStep(host, miner, 1, 4);
        runStep(host, miner, 2, 4);
        assertFalse(processingNetwork().getDurableTransfers().isEmpty());

        DefaultQIOAutomationHost restored = restoreHost(host, miner);
        reloadProcessingNetwork();
        runStep(restored, miner, 3, 4);

        assertTrue(restored.getOperationTokens().isEmpty());
        assertTrue(processingNetwork().getDurableTransfers().isEmpty());
        assertEquals(4, frequency.getStored(new ItemStack(Items.QUARTZ)));
    }

    @Test
    void preparedBufferWithoutExtractionIntentIsRejected() {
        MachineResourceStack stack = MachineResourceStack.item("output", new ItemStack(Items.IRON_INGOT, 3));
        MachinePortBaseline baseline = new MachinePortBaseline("output", "output",
              stack.kind(), stack);
        QIOOutputBufferEntry entry = QIOOutputBufferEntry.prepared(UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), baseline, 1);
        NBTTagCompound invalid = entry.write();
        invalid.removeTag("extraction");

        assertThrows(IllegalArgumentException.class, () -> QIOOutputBufferEntry.read(invalid));
    }

    @Test
    void developmentAutomationHostSchemaEntersDataError() {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(miner);
        NBTTagCompound oldSchema = host.serializeNBT();
        oldSchema.setInteger("schema", 1);

        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost(miner);
        restored.deserializeNBT(oldSchema);

        assertEquals(QIOAutomationHost.State.DATA_ERROR, restored.getState());
        assertTrue(restored.serializeNBT().hasKey("quarantinedData", 10));
    }

    private DefaultQIOAutomationHost boundHost(TileEntity tile) {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(tile);
        assertTrue(host.configureBinding(reference, QIOAutomationMode.OUTPUT_ONLY, true));
        return host;
    }

    private void runStep(DefaultQIOAutomationHost host, TileEntity tile, long tick, long limit) {
        QIOAutomaticOutputService.INSTANCE.processDevice(host, tile, tick, limit);
    }

    private PreparedTransfer prepareTransfer(DefaultQIOAutomationHost host, TileEntity tile,
          long amount, long tick) {
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        assertNotNull(provider);
        MachinePort port = provider.getPorts().stream().filter(candidate -> candidate.peek() != null &&
              candidate.role().allowsOutput()).findFirst().orElseThrow(AssertionError::new);
        MachineResourceStack extraction = port.peek().withAmount(amount);
        MachinePortBaseline baseline = MachinePortBaseline.capture(port);
        UUID operationId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID bufferId = UUID.randomUUID();
        MachineOperationLease lease = host.tryAcquireLease(leaseId, operationId,
              MachineOperationLease.Mode.OUTPUT_DRAIN, Math.max(0, port.laneId()), tick,
              Collections.singletonList(baseline));
        assertNotNull(lease);
        assertTrue(host.attachOperationToken(MachineOperationToken.outputDrain(operationId, leaseId,
              Math.max(0, port.laneId()))));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
              MachineOperationLease.State.COLLECTING));
        assertTrue(host.prepareOutputBuffer(QIOOutputBufferEntry.prepared(bufferId, operationId, leaseId,
              baseline, extraction, tick)));
        PortableResourceDescriptor resource = describe(extraction);
        QIODurableTransferRecord transfer = new QIODurableTransferRecord(bufferId, UUID.randomUUID(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, null, operationId, 0,
              "output/" + port.portId(), leaseId,
              "machine/" + host.getPersistentDeviceUUID() + '/' + port.portGroupId(),
              "output-buffer/" + host.getPersistentDeviceUUID() + '/' + bufferId,
              Collections.singletonMap(resource, amount));
        processingNetwork().addDurableTransfer(transfer);
        return new PreparedTransfer(bufferId);
    }

    private DefaultQIOAutomationHost restoreHost(DefaultQIOAutomationHost original, TileEntity tile) {
        return restoreHost(original.serializeNBT(), tile);
    }

    private DefaultQIOAutomationHost restoreHost(NBTTagCompound data, TileEntity tile) {
        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost(tile);
        restored.deserializeNBT(data);
        assertEquals(QIOAutomationHost.State.WAITING_ACCESS, restored.getState());
        assertTrue(restored.setAccessValidated(true));
        return restored;
    }

    private void reloadProcessingNetwork() {
        QIOProcessingNetworkManager.INSTANCE.resetForTests();
        QIOProcessingNetworkManager.INSTANCE.createOrLoad(worldDirectory);
        assertNotNull(QIOProcessingNetworkManager.INSTANCE.get(frequency.getFrequencyUUID()));
    }

    private QIOProcessingNetworkData processingNetwork() {
        return QIOProcessingNetworkManager.INSTANCE.getOrCreate(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot(frequency.getName(), frequency.getOwner(), frequency.getSecurity()));
    }

    private StorageViewHandle openView() {
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference, null);
        assertNotNull(view);
        return new StorageViewHandle(view);
    }

    private static QIOOutputBufferEntry onlyBuffer(DefaultQIOAutomationHost host) {
        assertEquals(1, host.getOutputBufferEntries().size());
        return host.getOutputBufferEntries().values().iterator().next();
    }

    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        return switch (stack.kind()) {
            case ITEM -> PortableResourceDescriptor.item(stack.itemStack());
            case FLUID -> PortableResourceDescriptor.fluid(stack.fluidStack());
            case GAS -> PortableResourceDescriptor.gas(stack.gasStack());
        };
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
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

    private static final class PreparedTransfer {

        private final UUID bufferId;

        private PreparedTransfer(UUID bufferId) {
            this.bufferId = bufferId;
        }
    }

    private static final class TestDriveHolder implements IQIODriveHolder {

        private final List<ItemStack> drives;

        private TestDriveHolder(List<ItemStack> drives) {
            this.drives = drives;
        }

        @Override
        public int getQIODimension() {
            return 0;
        }

        @Override
        public BlockPos getQIOPosition() {
            return BlockPos.ORIGIN;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return drives;
        }
    }

    private static final class StorageViewHandle implements AutoCloseable {

        private final IQIOStorageView view;

        private StorageViewHandle(IQIOStorageView view) {
            this.view = view;
        }

        @Override
        public void close() {
            view.close();
        }
    }
}
