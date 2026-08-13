package mekanism.qioprocessing.common.content.transfer;

import mekanism.api.processing.MachineResourceKind;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIODurableTransferRecordTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void forwardTransferPersistsEveryOwnershipReceiptAndDigest() throws Exception {
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        UUID resourceUUID = UUID.randomUUID();
        QIODurableTransferRecord record = QIODurableTransferRecord.qioToJob(UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
              Collections.singletonMap(resource, 12L), Collections.singletonMap(resource, resourceUUID),
              Collections.singletonMap(resource, BigInteger.valueOf(20)), 4, 7);
        record.markSourceDebited("core-claim-receipt", 8);
        record.markDestinationCredited("job-buffer-receipt");
        record.commitForward();

        QIODurableTransferRecord restored = QIODurableTransferRecord.read(record.write());

        assertEquals(QIODurableTransferRecord.Phase.COMMITTED, restored.getPhase());
        assertEquals(QIODurableTransferRecord.Resolution.FORWARD_COMMITTED,
              restored.getResolution());
        assertEquals(record.getRequestDigest(), restored.getRequestDigest());
        assertEquals(12, restored.getResources().get(resource));
    }

    @Test
    void invalidPhaseReceiptCombinationAndBufferOverflowAreRejected() {
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        QIODurableTransferRecord record = QIODurableTransferRecord.qioToJob(UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
              Collections.singletonMap(resource, 1L),
              Collections.singletonMap(resource, UUID.randomUUID()),
              Collections.singletonMap(resource, BigInteger.ONE), 0, 0);
        NBTTagCompound invalid = record.write();
        invalid.setString("phase", QIODurableTransferRecord.Phase.SOURCE_DEBITED.name());
        assertThrows(QIOProcessingDataException.class, () -> QIODurableTransferRecord.read(invalid));
        NBTTagCompound missingBaseline = record.write();
        missingBaseline.removeTag("qioBaselines");
        assertThrows(QIOProcessingDataException.class,
              () -> QIODurableTransferRecord.read(missingBaseline));
        NBTTagCompound developmentSchema = record.write();
        developmentSchema.setInteger("durableTransferSchemaVersion", 1);
        assertThrows(QIOProcessingDataException.class,
              () -> QIODurableTransferRecord.read(developmentSchema));
        NBTTagCompound missingBindings = record.write();
        missingBindings.removeTag("qioResourceUUIDs");
        assertThrows(QIOProcessingDataException.class,
              () -> QIODurableTransferRecord.read(missingBindings));

        QIOJobBuffer buffer = new QIOJobBuffer(UUID.randomUUID());
        buffer.add(QIOJobBuffer.Compartment.RESERVED, resource, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () ->
              buffer.add(QIOJobBuffer.Compartment.RESERVED, resource, 1));
        NBTTagCompound missingCompartments = buffer.write();
        missingCompartments.removeTag("compartments");
        assertThrows(QIOProcessingDataException.class,
              () -> QIOJobBuffer.read(missingCompartments));
    }

    @Test
    void machineInputTransferPersistsItsPreInsertionBaselines() throws Exception {
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        MachinePortBaseline baseline = new MachinePortBaseline("input", "lane-0",
              MachineResourceKind.ITEM, null);
        QIODurableTransferRecord record = new QIODurableTransferRecord(UUID.randomUUID(),
              UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_MACHINE,
              UUID.randomUUID(), null, 1, "1/" + UUID.randomUUID(), UUID.randomUUID(),
              "job/input", "machine/input", Collections.singletonMap(resource, 2L),
              Collections.singletonList(baseline));

        QIODurableTransferRecord restored = QIODurableTransferRecord.read(record.write());

        assertEquals(Collections.singletonList(baseline), restored.getMachineBaselines());
        assertEquals(record.getRequestDigest(), restored.getRequestDigest());
    }

    @Test
    void qioEndpointBaselinesRoundTripAndProtectTheDigest() throws Exception {
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        Map<PortableResourceDescriptor, BigInteger> baselines = Collections.singletonMap(
              resource, BigInteger.valueOf(30));
        QIODurableTransferRecord extraction = QIODurableTransferRecord.qioToJob(
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
              Collections.singletonMap(resource, 12L),
              Collections.singletonMap(resource, UUID.randomUUID()), baselines, 4, 7);
        QIODurableTransferRecord insertion = new QIODurableTransferRecord(UUID.randomUUID(),
              UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_QIO,
              UUID.randomUUID(), null, 1, "delivery", null, "job/buffer", "qio/frequency",
              Collections.singletonMap(resource, 12L), baselines);

        QIODurableTransferRecord restoredExtraction = QIODurableTransferRecord.read(
              extraction.write());
        QIODurableTransferRecord restoredInsertion = QIODurableTransferRecord.read(
              insertion.write());

        assertEquals(baselines, restoredExtraction.getQIOBaselines());
        assertEquals(baselines, restoredInsertion.getQIOBaselines());
        assertEquals(extraction.getRequestDigest(), restoredExtraction.getRequestDigest());
        assertEquals(insertion.getRequestDigest(), restoredInsertion.getRequestDigest());
    }
}
