package mekanism.common.tile;

import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.gas.Gas;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.GasStack;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerHandlerTransactionTest {

    private static final Item TEST_ITEM = new Item();
    private static final Gas TEST_GAS = new Gas("container_transaction_test", 0xFFFFFF);
    private static Fluid testFluid = new Fluid("container_transaction_test",
          new ResourceLocation("mekanism", "blocks/liquid/liquid"),
          new ResourceLocation("mekanism", "blocks/liquid/liquid_flow"));

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        Fluid registered = FluidRegistry.getFluid(testFluid.getName());
        if (registered == null) {
            assertTrue(FluidRegistry.registerFluid(testFluid));
        } else {
            testFluid = registered;
        }
    }

    @Test
    void externalHandlersRejectWithoutMutationWhileAsyncTransactionOwnsMachine() throws Exception {
        TestContainerTile tile = new TestContainerTile();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> tile.runContainerTransaction(() -> {
                entered.countDown();
                await(release);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            ItemStack itemInput = new ItemStack(TEST_ITEM, 5);
            IItemHandler itemHandler = tile.itemHandler();
            IGasHandler gasHandler = tile.gasHandler();
            IFluidHandler fluidHandler = tile.fluidHandler();
            assertNotNull(itemHandler);
            assertNotNull(gasHandler);
            assertNotNull(fluidHandler);
            assertEquals(5, itemHandler.insertItem(0, itemInput, false).getCount());
            assertEquals(0, gasHandler.receiveGas(EnumFacing.NORTH, new GasStack(TEST_GAS, 7), true));
            assertEquals(0, fluidHandler.fill(new FluidStack(testFluid, 11), true));
            assertEquals(13, tile.insertEnergy(0, 13, EnumFacing.NORTH, Action.EXECUTE));
            assertTrue(tile.itemSlot.isEmpty());
            assertTrue(tile.gasTank.isEmpty());
            assertTrue(tile.fluidTank.isEmpty());
            assertTrue(tile.energyContainer.isEmpty());

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            assertTrue(itemHandler.insertItem(0, itemInput, false).isEmpty());
            assertEquals(7, gasHandler.receiveGas(EnumFacing.NORTH, new GasStack(TEST_GAS, 7), true));
            assertEquals(11, fluidHandler.fill(new FluidStack(testFluid, 11), true));
            assertEquals(0, tile.insertEnergy(0, 13, EnumFacing.NORTH, Action.EXECUTE));
            assertEquals(5, tile.itemSlot.getCount());
            assertEquals(7, tile.gasTank.getGasAmount());
            assertEquals(11, tile.fluidTank.getFluidAmount());
            assertEquals(13, tile.energyContainer.getEnergy());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for handler transaction test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static class TestContainerTile extends TileEntityContainerBlock {

        private final BasicInventorySlot itemSlot = BasicInventorySlot.at(null, 0, 0);
        private final BasicGasTank gasTank = BasicGasTank.create(1_000, null);
        private final BasicFluidTank fluidTank = BasicFluidTank.create(1_000, null);
        private final BasicEnergyContainer energyContainer = BasicEnergyContainer.create(1_000, null);

        private TestContainerTile() {
            super("container_transaction_test");
            initializeInventorySlots();
        }

        @Override
        protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
            InventorySlotHelper builder = createInventorySlotHelper();
            builder.addSlot(itemSlot);
            return builder.build();
        }

        @Override
        protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
            FluidTankHelper builder = createFluidTankHelper();
            builder.addTank(fluidTank);
            return builder.build();
        }

        @Override
        protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
            GasTankHelper builder = createGasTankHelper();
            builder.addTank(gasTank);
            return builder.build();
        }

        @Override
        protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
            EnergyContainerHelper builder = createEnergyContainerHelper();
            builder.addContainer(energyContainer);
            return builder.build();
        }

        private IItemHandler itemHandler() {
            return getItemHandler(EnumFacing.NORTH);
        }

        private IGasHandler gasHandler() {
            return getGasHandler(EnumFacing.NORTH);
        }

        private IFluidHandler fluidHandler() {
            return getFluidHandler(EnumFacing.NORTH);
        }
    }
}
