package mekanism.qioprocessing.common.execution;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.common.TestBootstrap;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.QIODriveStorage;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.item.ItemQIODrive;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.QIODriveTier;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.passive.QIOPassiveOperation;
import mekanism.qioprocessing.common.content.transfer.QIOConfigurationExchangeRecord;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOConfigurationExchangeIntegrationTest {

    private static Constructor<?> loadedDeviceConstructor;
    private static Constructor<?> machineEndpointConstructor;
    private static Method driveConfigurationExchanges;
    private static Method configurationStillMatches;
    private static Method contaminateConfigurationOperation;
    private static Gas targetGas;
    private static Gas originalGas;

    private File worldDirectory;
    private QIOFrequency frequency;
    private QIOFrequencyReference reference;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapMinecraft();
        targetGas = gas("qio_configuration_target_gas", 0x45A2D1);
        originalGas = gas("qio_configuration_original_gas", 0xD19A45);

        loadedDeviceConstructor = QIOAutomationDeviceRegistry.LoadedDevice.class
              .getDeclaredConstructor(DefaultQIOAutomationHost.class,
                    net.minecraft.tileentity.TileEntity.class,
                    QIOAutomationDeviceLocation.class);
        loadedDeviceConstructor.setAccessible(true);
        Class<?> endpointClass = Class.forName(
              QIOProcessingExecutionService.class.getName() + "$MachineEndpoint");
        machineEndpointConstructor = endpointClass.getDeclaredConstructor(
              QIOAutomationDeviceRegistry.LoadedDevice.class,
              MachineRecipeProviderRegistry.BoundProvider.class, MachineRecipeRoute.class,
              Map.class, long.class, List.class);
        machineEndpointConstructor.setAccessible(true);
        driveConfigurationExchanges = QIOProcessingExecutionService.class.getDeclaredMethod(
              "driveConfigurationExchanges", QIOProcessingNetworkData.class, endpointClass,
              UUID.class, UUID.class, IQIOStorageView.class);
        driveConfigurationExchanges.setAccessible(true);
        configurationStillMatches = QIOProcessingExecutionService.class.getDeclaredMethod(
              "configurationStillMatches", endpointClass);
        configurationStillMatches.setAccessible(true);
        contaminateConfigurationOperation = QIOProcessingExecutionService.class.getDeclaredMethod(
              "contaminateConfigurationOperation", QIOProcessingNetworkData.class,
              endpointClass, UUID.class, String.class);
        contaminateConfigurationOperation.setAccessible(true);
    }

    @BeforeEach
    void setup() throws Exception {
        FrequencyManager.reset();
        QIOProcessingNetworkManager.INSTANCE.resetForTests();
        worldDirectory = Files.createTempDirectory("qio-configuration-exchange-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIOProcessingNetworkManager.INSTANCE.createOrLoad(worldDirectory);
        frequency = new QIOFrequency("configuration_exchange", null, SecurityMode.PUBLIC);
        frequency.addHolder(new TestDriveHolder(new ItemStack(
              new ItemQIODrive(QIODriveTier.BASE))));
        frequency.refresh();
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(null,
              SecurityMode.PUBLIC);
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
    void emptyTemplateInstallsOneItemFluidAndGas() throws Exception {
        for (TemplateKind kind : TemplateKind.values()) {
            ExchangeFixture fixture = fixture(kind, null, 10);
            assertEquals(4, insertQio(fixture.target, 4));

            assertTrue(driveToCompletion(fixture));

            assertMachine(fixture.port, fixture.target, 1);
            assertEquals(3, stored(fixture.target));
            assertEquals(QIOConfigurationExchangeRecord.Phase.COMMITTED,
                  onlyExchange(fixture.network, fixture.operationId).getPhase());
        }
    }

    @Test
    void identicalTemplateIsReusedWithoutAnotherQioDebit() throws Exception {
        for (TemplateKind kind : TemplateKind.values()) {
            MachineResourceStack target = resource(kind, true, 23);
            ExchangeFixture fixture = fixture(kind, target, 1);
            assertEquals(4, insertQio(fixture.target, 4));

            assertTrue(driveToCompletion(fixture));

            assertMachine(fixture.port, fixture.target, 23);
            assertEquals(4, stored(fixture.target));
            QIOConfigurationExchangeRecord exchange = onlyExchange(fixture.network,
                  fixture.operationId);
            assertEquals(23, exchange.getOriginal().amount());
            assertFalse(exchange.hasOutstandingClaim());
        }
    }

    @Test
    void differentTemplateReturnsItsFullAmountBeforeInstallingOneTarget() throws Exception {
        for (TemplateKind kind : TemplateKind.values()) {
            MachineResourceStack original = resource(kind, false, 17);
            ExchangeFixture fixture = fixture(kind, original, 10);
            assertEquals(4, insertQio(fixture.target, 4));

            assertTrue(driveToCompletion(fixture));

            assertMachine(fixture.port, fixture.target, 1);
            assertEquals(3, stored(fixture.target));
            assertEquals(17, stored(original));
            assertEquals(1, fixture.route.configurationInputs().get(0).amount());
            assertEquals(10, fixture.route.inputs().get(0).amount());
        }
    }

    @Test
    void missingTargetAndFullQioLeaveTheMachineTemplateUntouched() throws Exception {
        MachineResourceStack original = resource(TemplateKind.ITEM, false, 17);
        ExchangeFixture missing = fixture(TemplateKind.ITEM, original, 1);

        assertFalse(driveOnce(missing));
        assertMachine(missing.port, original, 17);
        assertTrue(missing.network.getOperationConfigurationExchanges(
              missing.operationId).isEmpty());

        ExchangeFixture full = fixture(TemplateKind.ITEM, original, 1);
        assertEquals(1, insertQio(full.target, 1));
        long remaining = frequency.getTotalCountCapacity() - frequency.getTotalCount();
        assertEquals(remaining, frequency.massInsert(new ItemStack(Blocks.STONE),
              remaining, Action.EXECUTE));

        assertFalse(driveOnce(full));
        assertMachine(full.port, original, 17);
        assertEquals(QIOConfigurationExchangeRecord.Phase.TARGET_DEBITED,
              onlyExchange(full.network, full.operationId).getPhase());
        assertEquals(0, stored(original));
    }

    @Test
    void physicalExchangeStagesResumeAfterNetworkAndHostReload() throws Exception {
        MachineResourceStack original = resource(TemplateKind.ITEM, false, 17);
        ExchangeFixture fixture = fixture(TemplateKind.ITEM, original, 10);
        assertEquals(4, insertQio(fixture.target, 4));

        assertFalse(driveOnce(fixture));
        QIOConfigurationExchangeRecord beforeOldReceipt = onlyExchange(fixture.network,
              fixture.operationId);
        assertEquals(QIOConfigurationExchangeRecord.Phase.OLD_RETURN_PREPARED,
              beforeOldReceipt.getPhase());
        assertEquals(null, fixture.port.peek());

        fixture = reload(fixture);
        assertFalse(driveOnce(fixture));
        QIOConfigurationExchangeRecord beforeNewReceipt = onlyExchange(fixture.network,
              fixture.operationId);
        assertEquals(QIOConfigurationExchangeRecord.Phase.OLD_QIO_CREDITED,
              beforeNewReceipt.getPhase());
        assertMachine(fixture.port, fixture.target, 1);
        assertEquals(17, stored(original));

        fixture = reload(fixture);
        assertTrue(driveOnce(fixture));
        assertEquals(QIOConfigurationExchangeRecord.Phase.COMMITTED,
              onlyExchange(fixture.network, fixture.operationId).getPhase());
        assertMachine(fixture.port, fixture.target, 1);
        assertEquals(3, stored(fixture.target));
        assertEquals(17, stored(original));
    }

    @Test
    void playerTemplateMutationContaminatesThePersistentLease() throws Exception {
        ExchangeFixture fixture = fixture(TemplateKind.ITEM, null, 10);
        assertEquals(4, insertQio(fixture.target, 4));
        assertTrue(driveToCompletion(fixture));
        Object endpoint = endpoint(fixture);
        assertEquals(true, configurationStillMatches.invoke(null, endpoint));

        assertNotNull(fixture.port.extract(fixture.target));
        assertTrue(fixture.port.insert(resource(TemplateKind.ITEM, false, 1)));
        assertEquals(false, configurationStillMatches.invoke(null, endpoint));
        contaminateConfigurationOperation.invoke(null, fixture.network, endpoint,
              fixture.operationId, "player changed template");

        MachineOperationToken token = fixture.host.getOperationTokens().get(
              fixture.operationId);
        assertNotNull(token);
        assertEquals(MachineOperationToken.State.CONTAMINATED, token.state());
        assertEquals(MachineOperationLease.State.CONTAMINATED,
              fixture.host.getLeases().get(token.leaseId()).state());
        assertFalse(onlyExchange(fixture.network, fixture.operationId).hasOutstandingClaim());
    }

    private ExchangeFixture fixture(TemplateKind kind, MachineResourceStack installed,
          long operations) throws Exception {
        MachinePort configuration = configurationPort(kind);
        if (installed != null) {
            assertTrue(configuration.insert(installed.withPort("template")));
        }
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        ports.put("template", configuration);
        ports.put("input", MachinePort.item("input", MachinePort.Role.INPUT,
              BasicInventorySlot.at(null, 0, 0), "replicator", 0));
        ports.put("output", MachinePort.item("output", MachinePort.Role.OUTPUT,
              BasicInventorySlot.at(BasicInventorySlot.alwaysTrue, null, 0, 0),
              "replicator", 0));
        MachineRecipeRoute base = MachineRecipeRoute.builder("replicate-" + kind.name())
              .configurationInput(resource(kind, true, 1))
              .inputItem("input", new ItemStack(Items.COAL))
              .outputItem("output", new ItemStack(Items.IRON_INGOT))
              .build();
        MachineRecipeRoute route = QIOProcessingExecutionService.scaleRoute(base, operations);
        ItemStack inputStack = new ItemStack(Items.COAL);
        assertEquals(operations, frequency.massInsert(inputStack, operations, Action.EXECUTE));
        PortableResourceDescriptor inputResource = PortableResourceDescriptor.item(inputStack);
        QIOStorageEntry inputEntry = frequency.getExternalStorageSnapshot().getEntries().stream()
              .filter(entry -> inputResource.equals(
                    PortableResourceDescriptor.fromStorageEntry(entry)))
              .findFirst().orElseThrow(AssertionError::new);
        TileEntityFurnace tile = new TileEntityFurnace();
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(tile);
        assertTrue(host.configureBinding(reference, QIOAutomationMode.PASSIVE, true));
        List<MachinePortBaseline> baselines = new ArrayList<>();
        ports.values().forEach(port -> baselines.add(MachinePortBaseline.capture(port)));
        UUID operationId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        Map<PortableResourceDescriptor, UUID> bindings = Collections.singletonMap(
              inputResource, inputEntry.getResourceUUID());
        Map<PortableResourceDescriptor, Long> requiredInputs = Collections.singletonMap(
              inputResource, operations);
        QIOPassiveOperation passive = new QIOPassiveOperation(operationId,
              host.getPersistentDeviceUUID(), "test:replicator", route.routeId(),
              route.recipeKey(), operations, 1, frequency.getContentsRevision(),
              frequency.getClaimRevision(), bindings, requiredInputs);
        QIOClaimResult inputClaim = frequency.submitClaimRequest(
              QIOClaimRequest.createOrAdjust(passive.getClaimRequestId(), passive.getClaimId(),
                    "mekanismqioprocessing", "passive/" + operationId, 0, 0,
                    frequency.getContentsRevision(), frequency.getClaimRevision(),
                    Collections.singletonMap(inputEntry.getResourceUUID(), operations)));
        assertTrue(inputClaim.isSuccess());
        passive.markClaimed(inputClaim.getClaimRevision());
        passive.markConfiguring(leaseId, 0);
        MachineOperationLease lease = host.tryAcquireLease(leaseId, operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1, baselines);
        assertNotNull(lease);
        assertTrue(host.attachOperationToken(MachineOperationToken.passive(operationId,
              leaseId, route.routeId(), route.recipeKey(), 0)));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.LOADING,
              MachineOperationLease.State.LOADING));
        QIOProcessingNetworkData network = processingNetwork();
        network.addPassiveOperation(passive);
        return new ExchangeFixture(kind, tile, host, route, ports, baselines, configuration,
              network, operationId, null);
    }

    private boolean driveToCompletion(ExchangeFixture fixture) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (driveOnce(fixture)) return true;
            confirmReceipts(fixture);
        }
        return false;
    }

    private boolean driveOnce(ExchangeFixture fixture) throws Exception {
        Object endpoint = endpoint(fixture);
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference, null);
        assertNotNull(view);
        try {
            try {
                return (boolean) driveConfigurationExchanges.invoke(null, fixture.network,
                      endpoint, fixture.operationId, fixture.jobId, view);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error error) throw error;
                throw e;
            }
        } finally {
            view.close();
        }
    }

    private Object endpoint(ExchangeFixture fixture) throws Exception {
        Object loadedDevice = loadedDeviceConstructor.newInstance(fixture.host, fixture.tile,
              new QIOAutomationDeviceLocation(0, BlockPos.ORIGIN));
        return machineEndpointConstructor.newInstance(loadedDevice, null,
              fixture.route, fixture.ports, 0L, fixture.baselines);
    }

    private void confirmReceipts(ExchangeFixture fixture) {
        MachineOperationToken token = fixture.host.getOperationTokens().get(fixture.operationId);
        assertNotNull(token);
        token.transferReceipts().forEach(receipt ->
              fixture.host.confirmTransferReceiptPersisted(fixture.operationId, receipt));
    }

    private ExchangeFixture reload(ExchangeFixture fixture) throws Exception {
        NBTTagCompound hostData = fixture.host.serializeNBT();
        QIOProcessingNetworkManager.INSTANCE.flushNetwork(frequency.getFrequencyUUID());
        QIOProcessingNetworkManager.INSTANCE.resetForTests();
        QIOProcessingNetworkManager.INSTANCE.createOrLoad(worldDirectory);
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              frequency.getFrequencyUUID());
        assertNotNull(network);
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(fixture.tile);
        host.deserializeNBT(hostData);
        assertTrue(host.setAccessValidated(true));
        MachineOperationLease lease = host.getLeases().values().stream()
              .filter(candidate -> candidate.ownerOperationId().equals(fixture.operationId))
              .findFirst().orElseThrow(AssertionError::new);
        return new ExchangeFixture(fixture.kind, fixture.tile, host, fixture.route,
              fixture.ports, lease.baselines(), fixture.port, network,
              fixture.operationId, fixture.jobId);
    }

    private QIOProcessingNetworkData processingNetwork() {
        return QIOProcessingNetworkManager.INSTANCE.getOrCreate(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot(frequency.getName(), frequency.getOwner(),
                    frequency.getSecurity()));
    }

    private static QIOConfigurationExchangeRecord onlyExchange(
          QIOProcessingNetworkData network, UUID operationId) {
        List<QIOConfigurationExchangeRecord> exchanges =
              network.getOperationConfigurationExchanges(operationId);
        assertEquals(1, exchanges.size());
        return exchanges.get(0);
    }

    private static MachinePort configurationPort(TemplateKind kind) {
        return switch (kind) {
            case ITEM -> MachinePort.configurationItem("template",
                  BasicInventorySlot.at(null, 0, 0), "replicator", 0);
            case FLUID -> MachinePort.configurationFluid("template",
                  BasicFluidTank.create(1_000, null), "replicator", 0);
            case GAS -> MachinePort.configurationGas("template",
                  BasicGasTank.create(1_000, null), "replicator", 0);
        };
    }

    private static MachineResourceStack resource(TemplateKind kind, boolean target,
          long amount) {
        return switch (kind) {
            case ITEM -> {
                ItemStack stack = new ItemStack(target ? Items.DIAMOND : Items.EMERALD);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("template", target ? "target" : "original");
                stack.setTagCompound(tag);
                yield MachineResourceStack.item("template", stack, amount);
            }
            case FLUID -> MachineResourceStack.fluid("template",
                  new FluidStack(target ? FluidRegistry.WATER : FluidRegistry.LAVA, 1), amount);
            case GAS -> MachineResourceStack.gas("template",
                  new GasStack(target ? targetGas : originalGas, 1), amount);
        };
    }

    private long insertQio(MachineResourceStack resource, long amount) {
        return switch (resource.kind()) {
            case ITEM -> frequency.massInsert(resource.itemStack(), amount, Action.EXECUTE);
            case FLUID -> frequency.massInsert(resource.fluidStack(), amount, Action.EXECUTE);
            case GAS -> frequency.massInsert(resource.gasStack(), amount, Action.EXECUTE);
        };
    }

    private long stored(MachineResourceStack resource) {
        return switch (resource.kind()) {
            case ITEM -> frequency.getStored(resource.itemStack());
            case FLUID -> frequency.getStored(resource.fluidStack());
            case GAS -> frequency.getStored(resource.gasStack());
        };
    }

    private static void assertMachine(MachinePort port, MachineResourceStack resource,
          long amount) {
        MachineResourceStack current = port.peek();
        assertNotNull(current);
        assertTrue(current.sameResource(resource));
        assertEquals(amount, current.amount());
    }

    private static Gas gas(String name, int color) {
        Gas gas = GasRegistry.getGas(name);
        return gas == null ? GasRegistry.register(new Gas(name, color)) : gas;
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) delete(child);
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private enum TemplateKind {
        ITEM,
        FLUID,
        GAS
    }

    private static final class ExchangeFixture {

        private final TemplateKind kind;
        private final TileEntityFurnace tile;
        private final DefaultQIOAutomationHost host;
        private final MachineRecipeRoute route;
        private final Map<String, MachinePort> ports;
        private final List<MachinePortBaseline> baselines;
        private final MachinePort port;
        private final QIOProcessingNetworkData network;
        private final UUID operationId;
        private final UUID jobId;
        private final MachineResourceStack target;

        private ExchangeFixture(TemplateKind kind, TileEntityFurnace tile,
              DefaultQIOAutomationHost host, MachineRecipeRoute route,
              Map<String, MachinePort> ports, List<MachinePortBaseline> baselines,
              MachinePort port, QIOProcessingNetworkData network, UUID operationId,
              UUID jobId) {
            this.kind = kind;
            this.tile = tile;
            this.host = host;
            this.route = route;
            this.ports = ports;
            this.baselines = baselines;
            this.port = port;
            this.network = network;
            this.operationId = operationId;
            this.jobId = jobId;
            this.target = route.configurationInputs().get(0);
        }
    }

    private static final class TestDriveHolder implements IQIODriveHolder {

        private final List<ItemStack> drives;

        private TestDriveHolder(ItemStack drive) {
            drives = new ArrayList<>(Collections.singletonList(drive));
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

        @Override
        public void updateQIODriveStack(int slot, ItemStack stack) {
            drives.set(slot, stack.copy());
        }
    }
}
