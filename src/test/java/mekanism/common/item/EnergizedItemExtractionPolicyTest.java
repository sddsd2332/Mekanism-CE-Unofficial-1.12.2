package mekanism.common.item;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.DefaultStrictEnergyHandler;
import mekanism.common.util.StorageUtils;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergizedItemExtractionPolicyTest {

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        Loader loader = Loader.instance();
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(loader) == null) {
            namedMods.set(loader, Collections.emptyMap());
        }
        Bootstrap.register();
        Capability<IStrictEnergyHandler> strictEnergy = getRegisteredCapability(IStrictEnergyHandler.class);
        if (strictEnergy == null) {
            DefaultStrictEnergyHandler.register();
            strictEnergy = getRegisteredCapability(IStrictEnergyHandler.class);
        }
        Capabilities.STRICT_ENERGY_CAPABILITY = strictEnergy;

        Capability<IEnergyStorage> forgeEnergy = getRegisteredCapability(IEnergyStorage.class);
        if (forgeEnergy == null) {
            CapabilityEnergy.register();
            forgeEnergy = getRegisteredCapability(IEnergyStorage.class);
        }
        CapabilityEnergy.ENERGY = forgeEnergy;
    }

    @Test
    void energyTabletRemainsExternallyExtractable() {
        ItemEnergized item = new ItemEnergized();
        ItemStack stack = new ItemStack(item);
        IEnergyContainer container = getContainer(stack);
        container.setEnergy(500);

        assertTrue(item.canSendEnergy(stack));
        assertEquals(50, container.extract(50, Action.SIMULATE, AutomationType.EXTERNAL));
        IEnergyStorage forgeEnergy = getForgeEnergy(stack);
        assertTrue(forgeEnergy.canExtract());
        assertTrue(forgeEnergy.extractEnergy(50, true) > 0);
    }

    @Test
    void poweredItemsOnlyAllowManualExtraction() {
        ItemEnergized item = new ItemEnergized(1_000);
        ItemStack stack = new ItemStack(item);
        IEnergyContainer container = getContainer(stack);
        container.setEnergy(500);

        assertFalse(item.canSendEnergy(stack));
        assertEquals(0, container.extract(50, Action.SIMULATE, AutomationType.EXTERNAL));
        assertEquals(0, StorageUtils.extractEnergy(stack, 50, Action.SIMULATE));
        IEnergyStorage forgeEnergy = getForgeEnergy(stack);
        assertFalse(forgeEnergy.canExtract());
        assertEquals(0, forgeEnergy.extractEnergy(50, true));
        assertEquals(50, StorageUtils.extractFromContainer(stack, 50, Action.EXECUTE));
        assertEquals(450, StorageUtils.getStoredEnergy(stack));
    }

    @Test
    void mekaFishingRodDoesNotActAsAnEnergySource() {
        ItemMekaFishingRod item = new ItemMekaFishingRod() {
            @Override
            public double getEnergyCapacity(ItemStack stack) {
                return 1_000;
            }

            @Override
            public double getEnergyTransfer(ItemStack stack) {
                return 100;
            }
        };
        ItemStack stack = new ItemStack(item);
        IEnergyContainer container = getContainer(stack);
        container.setEnergy(500);

        assertFalse(item.canSendEnergy(stack));
        assertEquals(0, container.extract(50, Action.SIMULATE, AutomationType.EXTERNAL));
        assertFalse(getForgeEnergy(stack).canExtract());
        assertEquals(50, StorageUtils.extractFromContainer(stack, 50, Action.EXECUTE));
        assertEquals(450, StorageUtils.getStoredEnergy(stack));
    }

    private static IEnergyContainer getContainer(ItemStack stack) {
        IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
        assertNotNull(container);
        return container;
    }

    private static IEnergyStorage getForgeEnergy(ItemStack stack) {
        IEnergyStorage energyStorage = stack.getCapability(CapabilityEnergy.ENERGY, null);
        assertNotNull(energyStorage);
        return energyStorage;
    }

    @SuppressWarnings("unchecked")
    private static <T> Capability<T> getRegisteredCapability(Class<T> type) throws ReflectiveOperationException {
        Field providers = CapabilityManager.class.getDeclaredField("providers");
        providers.setAccessible(true);
        Map<String, Capability<?>> registered = (Map<String, Capability<?>>) providers.get(CapabilityManager.INSTANCE);
        return (Capability<T>) registered.get(type.getName().intern());
    }
}
