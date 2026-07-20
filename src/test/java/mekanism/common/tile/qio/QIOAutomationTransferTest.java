package mekanism.common.tile.qio;

import mekanism.api.Action;
import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.IExtendedGasHandler;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIODriveStorage;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.capabilities.DefaultLogisticalTransporter;
import mekanism.common.lib.SidedBlockPos;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationTransferTest {

    private File worldDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @AfterEach
    void cleanUp() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void importerMovesSameItemTypeAcrossMultipleSlots() throws Exception {
        QIOFrequency frequency = createFrequency();
        ItemStackHandler source = new ItemStackHandler(2);
        source.setStackInSlot(0, new ItemStack(Blocks.STONE, 32));
        source.setStackInSlot(1, new ItemStack(Blocks.STONE, 32));

        assertTrue(new TileEntityQIOImporter().importItems(frequency, source));
        assertEquals(64, frequency.getStored(new ItemStack(Blocks.STONE)));
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertTrue(source.getStackInSlot(1).isEmpty());
    }

    @Test
    void importerMovesFullFluidAndGasBatchAmounts() throws Exception {
        QIOFrequency frequency = createFrequency();
        Fluid fluid = registerFluid();
        Gas gas = GasRegistry.register(new Gas("qio_automation_import_gas_" + UUID.randomUUID(), 0x2277CC));
        FluidStack fluidStack = new FluidStack(fluid, 32_000);
        GasStack gasStack = new GasStack(gas, 32_000);
        FluidTank fluidSource = new FluidTank(64_000);
        TestGasHandler gasSource = new TestGasHandler(64_000);
        assertEquals(32_000, fluidSource.fill(fluidStack, true));
        assertEquals(32_000, gasSource.receiveGas(null, gasStack, true));

        TileEntityQIOImporter importer = new TileEntityQIOImporter();
        assertTrue(importer.importFluids(frequency, fluidSource));
        assertTrue(importer.importGases(frequency, gasSource));

        assertEquals(32_000, frequency.getStored(fluidStack));
        assertEquals(32_000, frequency.getStored(gasStack));
        assertEquals(0, fluidSource.getFluidAmount());
        assertEquals(0, gasSource.getStored());
    }

    @Test
    void importerDrainsSameGasFromMoreTanksThanTheTypeLimit() throws Exception {
        QIOFrequency frequency = createFrequency();
        Gas gas = GasRegistry.register(new Gas("qio_automation_multi_tank_gas_" + UUID.randomUUID(), 0x44BB99));
        MultiTankGasHandler source = new MultiTankGasHandler(gas, 1_000, 6);

        assertTrue(new TileEntityQIOImporter().importGases(frequency, source));

        assertEquals(6_000, frequency.getStored(new GasStack(gas, 1)));
        assertEquals(0, source.getStored());
    }

    @Test
    void importerRollsBackEveryResourceKindWhenExecutionAcceptsLessThanSimulation() throws Exception {
        LimitedExecutionFrequency frequency = createFrequency(new LimitedExecutionFrequency(20, 4_000, 3_000));
        Fluid fluid = registerFluid();
        Gas gas = GasRegistry.register(new Gas("qio_automation_import_rollback_gas_" + UUID.randomUUID(), 0x339955));
        ItemStack itemStack = new ItemStack(Blocks.STONE, 64);
        FluidStack fluidStack = new FluidStack(fluid, 10_000);
        GasStack gasStack = new GasStack(gas, 10_000);
        ItemStackHandler itemSource = new ItemStackHandler(1);
        itemSource.setStackInSlot(0, itemStack.copy());
        FluidTank fluidSource = new FluidTank(10_000);
        TestGasHandler gasSource = new TestGasHandler(10_000);
        assertEquals(10_000, fluidSource.fill(fluidStack, true));
        assertEquals(10_000, gasSource.receiveGas(null, gasStack, true));

        TileEntityQIOImporter importer = new TileEntityQIOImporter();
        assertTrue(importer.importItems(frequency, itemSource));
        assertTrue(importer.importFluids(frequency, fluidSource));
        assertTrue(importer.importGases(frequency, gasSource));

        assertEquals(20, frequency.getStored(itemStack));
        assertEquals(4_000, frequency.getStored(fluidStack));
        assertEquals(3_000, frequency.getStored(gasStack));
        assertEquals(44, itemSource.getStackInSlot(0).getCount());
        assertEquals(6_000, fluidSource.getFluidAmount());
        assertEquals(7_000, gasSource.getStored());
    }

    @Test
    void exporterMovesFullBatchAmountsForEveryResourceKind() throws Exception {
        QIOFrequency frequency = createFrequency();
        Fluid fluid = registerFluid();
        Gas gas = GasRegistry.register(new Gas("qio_automation_gas_" + UUID.randomUUID(), 0x55AAFF));
        ItemStack itemStack = new ItemStack(Blocks.STONE, 64);
        FluidStack fluidStack = new FluidStack(fluid, 32_000);
        GasStack gasStack = new GasStack(gas, 32_000);
        assertEquals(64, frequency.massInsert(itemStack, itemStack.getCount(), Action.EXECUTE));
        assertEquals(32_000, frequency.massInsert(fluidStack, fluidStack.amount, Action.EXECUTE));
        assertEquals(32_000, frequency.massInsert(gasStack, gasStack.amount, Action.EXECUTE));

        ItemStackHandler itemTarget = new ItemStackHandler(1);
        FluidTank fluidTarget = new FluidTank(64_000);
        TestGasHandler gasTarget = new TestGasHandler(64_000);
        TileEntityQIOExporter exporter = new TileEntityQIOExporter();
        assertTrue(exporter.exportItems(frequency, itemTarget));
        assertTrue(exporter.exportFluids(frequency, fluidTarget));
        assertTrue(exporter.exportGases(frequency, gasTarget));

        assertEquals(64, itemTarget.getStackInSlot(0).getCount());
        assertEquals(32_000, fluidTarget.getFluidAmount());
        assertEquals(32_000, gasTarget.getStored());
        assertEquals(0, frequency.getStored(itemStack));
        assertEquals(0, frequency.getStored(fluidStack));
        assertEquals(0, frequency.getStored(gasStack));
    }

    @Test
    void exporterRollsBackWhenExecutionAcceptsLessThanSimulation() throws Exception {
        QIOFrequency frequency = createFrequency();
        Fluid fluid = registerFluid();
        Gas gas = GasRegistry.register(new Gas("qio_automation_rollback_gas_" + UUID.randomUUID(), 0xAA5533));
        ItemStack itemStack = new ItemStack(Blocks.STONE, 64);
        FluidStack fluidStack = new FluidStack(fluid, 10_000);
        GasStack gasStack = new GasStack(gas, 10_000);
        assertEquals(64, frequency.massInsert(itemStack, itemStack.getCount(), Action.EXECUTE));
        assertEquals(10_000, frequency.massInsert(fluidStack, fluidStack.amount, Action.EXECUTE));
        assertEquals(10_000, frequency.massInsert(gasStack, gasStack.amount, Action.EXECUTE));

        TileEntityQIOExporter exporter = new TileEntityQIOExporter();
        LimitedExecutionItemHandler itemTarget = new LimitedExecutionItemHandler(17);
        LimitedExecutionFluidHandler fluidTarget = new LimitedExecutionFluidHandler(10_000, 4_000);
        LimitedExecutionGasHandler gasTarget = new LimitedExecutionGasHandler(10_000, 3_000);
        assertTrue(exporter.exportItems(frequency, itemTarget));
        assertTrue(exporter.exportFluids(frequency, fluidTarget));
        assertTrue(exporter.exportGases(frequency, gasTarget));

        assertEquals(17, itemTarget.getStackInSlot(0).getCount());
        assertEquals(4_000, fluidTarget.getStored());
        assertEquals(3_000, gasTarget.getStored());
        assertEquals(47, frequency.getStored(itemStack));
        assertEquals(6_000, frequency.getStored(fluidStack));
        assertEquals(7_000, frequency.getStored(gasStack));
    }

    @Test
    void exporterRoundRobinSimulationDoesNotAdvanceCursorAndExecutionDoes() throws Exception {
        QIOFrequency frequency = createFrequency();
        ItemStack itemStack = new ItemStack(Blocks.STONE, 20);
        assertEquals(20, frequency.massInsert(itemStack, itemStack.getCount(), Action.EXECUTE));
        TileEntityQIOExporter exporter = new TileEntityQIOExporter();
        exporter.setRoundRobin(true);
        SidedBlockPos initial = target(1);
        SidedBlockPos simulated = target(2);
        SidedBlockPos executed = target(3);
        exporter.setRoundRobinTarget(initial);
        RecordingTransporter transporter = new RecordingTransporter(simulated, executed, 10, 7);

        assertTrue(exporter.exportItems(frequency, transporter));

        assertEquals(2, transporter.calls);
        assertFalse(transporter.firstDoEmit);
        assertTrue(transporter.secondDoEmit);
        assertEquals(initial, transporter.targetAtExecution);
        assertEquals(executed, exporter.getRoundRobinTarget());
        assertEquals(13, frequency.getStored(itemStack));
    }

    @Test
    void exporterPersistsRoundRobinModeAndOnlyWorldDataPersistsCursor() {
        TileEntityQIOExporter exporter = new TileEntityQIOExporter();
        exporter.setRoundRobin(true);
        SidedBlockPos cursor = target(4);
        exporter.setRoundRobinTarget(cursor);

        NBTTagCompound worldData = new NBTTagCompound();
        exporter.writeCustomNBT(worldData);
        assertTrue(worldData.getBoolean(NBTConstants.ROUND_ROBIN));
        TileEntityQIOExporter worldLoaded = new TileEntityQIOExporter();
        worldLoaded.readCustomNBT(worldData);
        assertTrue(worldLoaded.getRoundRobin());
        assertEquals(cursor, worldLoaded.getRoundRobinTarget());

        NBTTagCompound sustained = new NBTTagCompound();
        exporter.writeSustainedQIOData(sustained);
        TileEntityQIOExporter itemLoaded = new TileEntityQIOExporter();
        itemLoaded.setRoundRobinTarget(target(5));
        itemLoaded.readSustainedQIOData(sustained);
        assertTrue(itemLoaded.getRoundRobin());
        assertNull(itemLoaded.getRoundRobinTarget());
    }

    @Test
    void exporterAcceptsTransporterReturnsBackIntoQio() throws Exception {
        QIOFrequency frequency = createFrequency();
        TestExporter exporter = new TestExporter(frequency);
        ItemStack returning = new ItemStack(Blocks.STONE, 18);
        assertTrue(exporter.canSendHome(returning));

        TransitResponse response = exporter.sendHome(TransitRequest.simple(returning));

        assertFalse(response.isEmpty());
        assertEquals(18, response.getSendingAmount());
        assertEquals(18, frequency.getStored(returning));
    }

    private static SidedBlockPos target(int x) {
        return new SidedBlockPos(new Coord4D(x, 2, 3, 0), EnumFacing.UP);
    }

    private QIOFrequency createFrequency() throws Exception {
        return createFrequency(new QIOFrequency("automation", null, SecurityMode.PUBLIC));
    }

    private <F extends QIOFrequency> F createFrequency(F frequency) throws Exception {
        worldDirectory = Files.createTempDirectory("qio-automation-transfer-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        ItemStack drive = new ItemStack(new TestDriveItem());
        frequency.addHolder(new TestHolder(Collections.singletonList(drive)));
        frequency.refresh();
        return frequency;
    }

    private static Fluid registerFluid() {
        Fluid fluid = new Fluid("qio_automation_fluid_" + UUID.randomUUID(),
              new ResourceLocation("minecraft", "blocks/water"), new ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(fluid);
        return fluid;
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

    private static final class TestDriveItem extends Item implements IQIODriveItem {

        @Override
        public QIODriveTier getDriveTier() {
            return QIODriveTier.SUPERMASSIVE;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {

        private final List<ItemStack> drives;

        private TestHolder(List<ItemStack> drives) {
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

    private static class TestGasHandler implements IGasHandler {

        private final int capacity;
        private GasStack stored;

        private TestGasHandler(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
            if (stack == null || stack.getGas() == null || stored != null && !stored.isGasEqual(stack)) {
                return 0;
            }
            int accepted = Math.min(capacity - getStored(), stack.amount);
            if (doTransfer && accepted > 0) {
                if (stored == null) {
                    stored = new GasStack(stack.getGas(), accepted);
                } else {
                    stored.amount += accepted;
                }
            }
            return accepted;
        }

        @Override
        public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
            if (stored == null || amount <= 0) {
                return null;
            }
            int extracted = Math.min(stored.amount, amount);
            GasStack result = new GasStack(stored.getGas(), extracted);
            if (doTransfer) {
                stored.amount -= extracted;
                if (stored.amount == 0) {
                    stored = null;
                }
            }
            return result;
        }

        @Override
        public boolean canReceiveGas(EnumFacing side, Gas type) {
            return type != null && getStored() < capacity && (stored == null || stored.getGas() == type);
        }

        @Override
        public boolean canDrawGas(EnumFacing side, Gas type) {
            return stored != null && (type == null || stored.getGas() == type);
        }

        @Override
        public GasTankInfo[] getTankInfo() {
            return IGasHandler.NONE;
        }

        int getStored() {
            return stored == null ? 0 : stored.amount;
        }
    }

    private static final class LimitedExecutionGasHandler extends TestGasHandler {

        private final int executionLimit;

        private LimitedExecutionGasHandler(int capacity, int executionLimit) {
            super(capacity);
            this.executionLimit = executionLimit;
        }

        @Override
        public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
            return super.receiveGas(side, doTransfer && stack != null ? new GasStack(stack.getGas(), Math.min(stack.amount, executionLimit)) : stack,
                  doTransfer);
        }
    }

    private static final class LimitedExecutionFluidHandler extends FluidTank {

        private final int executionLimit;

        private LimitedExecutionFluidHandler(int capacity, int executionLimit) {
            super(capacity);
            this.executionLimit = executionLimit;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return super.fill(doFill && resource != null ? new FluidStack(resource, Math.min(resource.amount, executionLimit)) : resource, doFill);
        }

        int getStored() {
            return getFluidAmount();
        }
    }

    private static final class MultiTankGasHandler implements IExtendedGasHandler {

        private final GasStack[] tanks;
        private final int capacity;

        private MultiTankGasHandler(Gas gas, int amountPerTank, int tankCount) {
            capacity = amountPerTank;
            tanks = new GasStack[tankCount];
            for (int tank = 0; tank < tankCount; tank++) {
                tanks[tank] = new GasStack(gas, amountPerTank);
            }
        }

        @Override
        public int getCountGasTanks() {
            return tanks.length;
        }

        @Override
        public GasStack getGasInTank(int tank) {
            GasStack stored = tanks[tank];
            return stored == null ? null : stored.copy();
        }

        @Override
        public void setGasInTank(int tank, GasStack stack) {
            tanks[tank] = stack == null ? null : stack.copy();
        }

        @Override
        public int getGasTankCapacity(int tank) {
            return capacity;
        }

        @Override
        public boolean isGasValid(int tank, GasStack stack) {
            return stack != null && stack.getGas() != null;
        }

        @Override
        public GasStack insertGas(int tank, GasStack stack, Action action) {
            if (stack == null || stack.getGas() == null || tanks[tank] != null && !tanks[tank].isGasEqual(stack)) {
                return stack;
            }
            int stored = tanks[tank] == null ? 0 : tanks[tank].amount;
            int accepted = Math.min(capacity - stored, stack.amount);
            if (accepted > 0 && action.execute()) {
                if (tanks[tank] == null) {
                    tanks[tank] = new GasStack(stack.getGas(), accepted);
                } else {
                    tanks[tank].amount += accepted;
                }
            }
            if (accepted >= stack.amount) {
                return null;
            }
            return new GasStack(stack.getGas(), stack.amount - accepted);
        }

        @Override
        public GasStack extractGas(int tank, int amount, Action action) {
            GasStack stored = tanks[tank];
            if (stored == null || amount <= 0) {
                return null;
            }
            int extracted = Math.min(amount, stored.amount);
            GasStack result = new GasStack(stored.getGas(), extracted);
            if (action.execute()) {
                stored.amount -= extracted;
                if (stored.amount == 0) {
                    tanks[tank] = null;
                }
            }
            return result;
        }

        private int getStored() {
            int stored = 0;
            for (GasStack tank : tanks) {
                stored += tank == null ? 0 : tank.amount;
            }
            return stored;
        }
    }

    private static final class LimitedExecutionItemHandler extends ItemStackHandler {

        private final int executionLimit;

        private LimitedExecutionItemHandler(int executionLimit) {
            super(1);
            this.executionLimit = executionLimit;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate || stack.isEmpty()) {
                return super.insertItem(slot, stack, simulate);
            }
            ItemStack limited = stack.copy();
            limited.setCount(Math.min(stack.getCount(), executionLimit));
            ItemStack limitedRemainder = super.insertItem(slot, limited, false);
            int inserted = limited.getCount() - limitedRemainder.getCount();
            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }
    }

    private static final class LimitedExecutionFrequency extends QIOFrequency {

        private long remainingItems;
        private long remainingFluids;
        private long remainingGases;

        private LimitedExecutionFrequency(long remainingItems, long remainingFluids, long remainingGases) {
            super("limited-automation", null, SecurityMode.PUBLIC);
            this.remainingItems = remainingItems;
            this.remainingFluids = remainingFluids;
            this.remainingGases = remainingGases;
        }

        @Override
        public synchronized long massInsert(ItemStack stack, long amount, Action action) {
            if (action != null && action.execute()) {
                long inserted = super.massInsert(stack, Math.min(amount, remainingItems), action);
                remainingItems -= inserted;
                return inserted;
            }
            return super.massInsert(stack, amount, action);
        }

        @Override
        public synchronized long massInsert(FluidStack stack, long amount, Action action) {
            if (action != null && action.execute()) {
                long inserted = super.massInsert(stack, Math.min(amount, remainingFluids), action);
                remainingFluids -= inserted;
                return inserted;
            }
            return super.massInsert(stack, amount, action);
        }

        @Override
        public synchronized long massInsert(GasStack stack, long amount, Action action) {
            if (action != null && action.execute()) {
                long inserted = super.massInsert(stack, Math.min(amount, remainingGases), action);
                remainingGases -= inserted;
                return inserted;
            }
            return super.massInsert(stack, amount, action);
        }
    }

    private static final class TestExporter extends TileEntityQIOExporter {

        private final QIOFrequency frequency;

        private TestExporter(QIOFrequency frequency) {
            this.frequency = frequency;
        }

        @Override
        protected QIOFrequency getFrequencyForTransfer() {
            return frequency;
        }
    }

    private static final class RecordingTransporter extends DefaultLogisticalTransporter {

        private final SidedBlockPos simulatedTarget;
        private final SidedBlockPos executedTarget;
        private final int simulatedAmount;
        private final int executedAmount;
        private int calls;
        private boolean firstDoEmit;
        private boolean secondDoEmit;
        private SidedBlockPos targetAtExecution;

        private RecordingTransporter(SidedBlockPos simulatedTarget, SidedBlockPos executedTarget, int simulatedAmount,
              int executedAmount) {
            this.simulatedTarget = simulatedTarget;
            this.executedTarget = executedTarget;
            this.simulatedAmount = simulatedAmount;
            this.executedAmount = executedAmount;
        }

        @Override
        public TransitResponse insertMaybeRR(IAdvancedTransportEjector outputter, Coord4D outputterCoord,
              TransitRequest request, mekanism.api.EnumColor color, boolean doEmit, int min) {
            calls++;
            if (calls == 1) {
                firstDoEmit = doEmit;
                outputter.setRoundRobinTarget(simulatedTarget);
                return response(request, simulatedAmount);
            }
            secondDoEmit = doEmit;
            targetAtExecution = outputter.getRoundRobinTarget();
            outputter.setRoundRobinTarget(executedTarget);
            return response(request, executedAmount);
        }

        private static TransitResponse response(TransitRequest request, int amount) {
            for (TransitRequest.ItemData data : request) {
                int accepted = Math.min(amount, data.getTotalCount());
                return accepted <= 0 ? request.getEmptyResponse()
                      : request.createResponse(data.getItemType().createStack(accepted), data);
            }
            return request.getEmptyResponse();
        }
    }
}
