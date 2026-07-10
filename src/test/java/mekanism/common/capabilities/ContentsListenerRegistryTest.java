package mekanism.common.capabilities;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ContentsListenerRegistryTest {

    private static final Item TEST_ITEM = new Item();
    private static final Gas TEST_GAS = new Gas("listener_test_gas", 0xFFFFFF);
    private static Fluid testFluid = new Fluid("listener_test_fluid",
          new ResourceLocation("mekanism", "blocks/liquid/liquid"), new ResourceLocation("mekanism", "blocks/liquid/liquid_flow"));

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
    void standardContainersPreservePrimaryAndNotifyAdditionalListeners() {
        for (FixtureFactory factory : fixtureFactories()) {
            List<String> calls = new ArrayList<>();
            Fixture fixture = factory.create(() -> calls.add("primary"));
            IContentsListener additional = () -> calls.add("additional");

            assertTrue(fixture.registry.addContentsListener(additional));
            assertEquals(0, calls.size(), "registration must not immediately report current contents");
            fixture.mutate();
            assertEquals(2, calls.size());
            assertEquals("primary", calls.get(0));
            assertEquals("additional", calls.get(1));
        }
    }

    @Test
    void registrationUsesIdentityAndSupportsIndependentRemoval() {
        for (FixtureFactory factory : fixtureFactories()) {
            AtomicInteger primaryCalls = new AtomicInteger();
            AtomicInteger firstCalls = new AtomicInteger();
            AtomicInteger secondCalls = new AtomicInteger();
            IContentsListener primary = primaryCalls::incrementAndGet;
            IContentsListener first = firstCalls::incrementAndGet;
            IContentsListener second = secondCalls::incrementAndGet;
            Fixture fixture = factory.create(primary);

            assertFalse(fixture.registry.addContentsListener(null));
            assertFalse(fixture.registry.removeContentsListener(null));
            assertFalse(fixture.registry.addContentsListener((IContentsListener) fixture.container));
            assertFalse(fixture.registry.addContentsListener(primary));
            assertTrue(fixture.registry.addContentsListener(first));
            assertFalse(fixture.registry.addContentsListener(first));
            assertTrue(fixture.registry.addContentsListener(second));

            fixture.mutate();
            assertEquals(1, primaryCalls.get());
            assertEquals(1, firstCalls.get());
            assertEquals(1, secondCalls.get());

            assertTrue(fixture.registry.removeContentsListener(first));
            assertFalse(fixture.registry.removeContentsListener(first));
            fixture.mutate();
            assertEquals(2, primaryCalls.get());
            assertEquals(1, firstCalls.get());
            assertEquals(2, secondCalls.get());
        }
    }

    @Test
    void simulationsAndZeroAmountOperationsDoNotNotify() {
        for (FixtureFactory factory : fixtureFactories()) {
            AtomicInteger primaryCalls = new AtomicInteger();
            AtomicInteger additionalCalls = new AtomicInteger();
            Fixture fixture = factory.create(primaryCalls::incrementAndGet);
            fixture.registry.addContentsListener(additionalCalls::incrementAndGet);

            fixture.simulate();
            fixture.zeroAmountOperation();
            assertEquals(0, primaryCalls.get());
            assertEquals(0, additionalCalls.get());
        }
    }

    @Test
    void listenerStorageIsLazyAndReleasedAfterLastRemoval() throws Exception {
        for (FixtureFactory factory : fixtureFactories()) {
            Fixture fixture = factory.create(null);
            Field field = fixture.container.getClass().getDeclaredField("additionalListeners");
            field.setAccessible(true);
            assertNull(field.get(fixture.container));

            IContentsListener listener = () -> {
            };
            assertTrue(fixture.registry.addContentsListener(listener));
            assertNotNull(field.get(fixture.container));
            assertTrue(fixture.registry.removeContentsListener(listener));
            assertNull(field.get(fixture.container));
        }
    }

    @Test
    void reentrantChangesUseNotificationSnapshot() {
        BasicInventorySlot slot = BasicInventorySlot.at(null, 0, 0);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        IContentsListener second = secondCalls::incrementAndGet;
        IContentsListener[] first = new IContentsListener[1];
        first[0] = () -> {
            firstCalls.incrementAndGet();
            slot.removeContentsListener(first[0]);
            slot.addContentsListener(second);
        };
        slot.addContentsListener(first[0]);

        slot.onContentsChanged();
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
        slot.onContentsChanged();
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
    }

    @Test
    void concurrentRegistrationAndNotificationRemainSafe() throws Exception {
        BasicInventorySlot slot = BasicInventorySlot.at(null, 0, 0);
        IContentsListener listener = () -> {
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> notifications = executor.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    slot.onContentsChanged();
                }
            });
            Future<?> registrations = executor.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    slot.addContentsListener(listener);
                    slot.removeContentsListener(listener);
                }
            });
            notifications.get(10, TimeUnit.SECONDS);
            registrations.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mergedTankWrappersForwardRegistrationWithoutDuplicateNotifications() {
        BasicFluidTank fluidTank = BasicFluidTank.create(1_000, null);
        BasicGasTank gasTank = BasicGasTank.create(1_000, null);
        MergedTank mergedTank = MergedTank.create(fluidTank, gasTank);
        IExtendedFluidTank fluidWrapper = mergedTank.getFluidTank();
        IExtendedGasTank gasWrapper = mergedTank.getGasTank();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger gasCalls = new AtomicInteger();
        IContentsListener fluidListener = fluidCalls::incrementAndGet;
        IContentsListener gasListener = gasCalls::incrementAndGet;

        assertTrue(((IContentsListenerRegistry) fluidWrapper).addContentsListener(fluidListener));
        assertFalse(((IContentsListenerRegistry) fluidWrapper).addContentsListener(fluidWrapper));
        assertFalse(fluidTank.addContentsListener(fluidListener));
        assertTrue(((IContentsListenerRegistry) gasWrapper).addContentsListener(gasListener));
        assertFalse(((IContentsListenerRegistry) gasWrapper).addContentsListener(gasWrapper));
        assertFalse(gasTank.addContentsListener(gasListener));
        fluidTank.setStack(new FluidStack(testFluid, 100));
        gasTank.setStack(new GasStack(TEST_GAS, 100));
        assertEquals(1, fluidCalls.get());
        assertEquals(1, gasCalls.get());

        assertTrue(((IContentsListenerRegistry) fluidWrapper).removeContentsListener(fluidListener));
        assertTrue(((IContentsListenerRegistry) gasWrapper).removeContentsListener(gasListener));
    }

    private static List<FixtureFactory> fixtureFactories() {
        List<FixtureFactory> factories = new ArrayList<>();
        factories.add(primary -> {
            BasicInventorySlot slot = BasicInventorySlot.at(primary, 0, 0);
            return new Fixture(slot, slot,
                  () -> slot.setStack(new ItemStack(TEST_ITEM, slot.isEmpty() ? 1 : slot.getCount() + 1)),
                  () -> slot.insertItem(new ItemStack(TEST_ITEM), Action.SIMULATE, AutomationType.INTERNAL),
                  () -> {
                      slot.insertItem(ItemStack.EMPTY, Action.EXECUTE, AutomationType.INTERNAL);
                      slot.extractItem(0, Action.EXECUTE, AutomationType.INTERNAL);
                  });
        });
        factories.add(primary -> {
            BasicGasTank tank = BasicGasTank.create(1_000, primary);
            return new Fixture(tank, tank,
                  () -> tank.setStack(new GasStack(TEST_GAS, tank.getGasAmount() + 1)),
                  () -> tank.insert(new GasStack(TEST_GAS, 1), Action.SIMULATE, AutomationType.INTERNAL),
                  () -> {
                      tank.insert(new GasStack(TEST_GAS, 0), Action.EXECUTE, AutomationType.INTERNAL);
                      tank.extract(0, Action.EXECUTE, AutomationType.INTERNAL);
                  });
        });
        factories.add(primary -> {
            BasicFluidTank tank = BasicFluidTank.create(1_000, primary);
            return new Fixture(tank, tank,
                  () -> tank.setStack(new FluidStack(testFluid, tank.getFluidAmount() + 1)),
                  () -> tank.insert(new FluidStack(testFluid, 1), Action.SIMULATE, AutomationType.INTERNAL),
                  () -> {
                      tank.insert(new FluidStack(testFluid, 0), Action.EXECUTE, AutomationType.INTERNAL);
                      tank.extract(0, Action.EXECUTE, AutomationType.INTERNAL);
                  });
        });
        return factories;
    }

    private interface FixtureFactory {

        Fixture create(IContentsListener primary);
    }

    private static class Fixture {

        private final Object container;
        private final IContentsListenerRegistry registry;
        private final Runnable mutation;
        private final Runnable simulation;
        private final Runnable zeroAmountOperation;

        private Fixture(Object container, IContentsListenerRegistry registry, Runnable mutation, Runnable simulation, Runnable zeroAmountOperation) {
            this.container = container;
            this.registry = registry;
            this.mutation = mutation;
            this.simulation = simulation;
            this.zeroAmountOperation = zeroAmountOperation;
        }

        private void mutate() {
            mutation.run();
        }

        private void simulate() {
            simulation.run();
        }

        private void zeroAmountOperation() {
            zeroAmountOperation.run();
        }
    }
}
