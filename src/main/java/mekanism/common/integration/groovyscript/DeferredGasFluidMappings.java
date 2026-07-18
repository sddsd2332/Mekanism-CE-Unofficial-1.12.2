package mekanism.common.integration.groovyscript;

import com.cleanroommc.groovyscript.api.GroovyLog;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.LinkedHashMap;
import java.util.Map;

final class DeferredGasFluidMappings {

    private static final DeferredGasFluidMappings INSTANCE = new DeferredGasFluidMappings();
    private static final Map<String, String> PENDING_MAPPINGS = new LinkedHashMap<>();
    private static boolean eventHandlerRegistered;

    private DeferredGasFluidMappings() {
    }

    static synchronized void registerEventHandler() {
        if (!eventHandlerRegistered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            eventHandlerRegistered = true;
        }
    }

    static void defer(String gasName, String fluidName) {
        PENDING_MAPPINGS.put(gasName, fluidName);
    }

    static void cancel(String gasName) {
        PENDING_MAPPINGS.remove(gasName);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void applyMappings(RegistryEvent.Register<IRecipe> event) {
        PENDING_MAPPINGS.forEach((gasName, fluidName) -> {
            Gas gas = GasRegistry.getGas(gasName);
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (gas == null) {
                GroovyLog.get().errorMC("Could not apply deferred Mekanism gas fluid mapping: gas '{}' is not registered", gasName);
            } else if (fluid == null) {
                GroovyLog.get().errorMC("Could not apply deferred Mekanism gas fluid mapping: fluid '{}' is not registered", fluidName);
            } else {
                gas.setFluid(fluid);
            }
        });
        PENDING_MAPPINGS.clear();
    }
}
