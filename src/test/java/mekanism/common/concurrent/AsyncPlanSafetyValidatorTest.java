package mekanism.common.concurrent;

import mekanism.api.IAsyncPlanCalculator;
import mekanism.common.recipe.cache.ImmutableResourceSnapshot;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPlanSafetyValidatorTest {

    @BeforeAll
    static void bootstrap() { Bootstrap.register(); }

    @Test
    void unavailableClientFieldTypesRejectBothSnapshotAndCalculatorWithoutThrowing() throws Exception {
        URL[] locations = {AsyncPlanSafetyValidator.class.getProtectionDomain().getCodeSource().getLocation(),
              AsyncPlanSafetyValidatorTest.class.getProtectionDomain().getCodeSource().getLocation()};
        try (URLClassLoader loader = new URLClassLoader(locations, getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.")) throw new ClassNotFoundException(name);
                if (!name.startsWith("mekanism.")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }) {
            Class<?> validator = loader.loadClass(AsyncPlanSafetyValidator.class.getName());
            Class<?> snapshot = loader.loadClass(ClientSnapshot.class.getName());
            Class<?> calculator = loader.loadClass(ClientCalculator.class.getName());
            assertThrows(NoClassDefFoundError.class, snapshot::getDeclaredFields);
            assertThrows(NoClassDefFoundError.class, calculator::getDeclaredFields);
            assertEquals(false, validator.getMethod("isDetachedValue", Object.class)
                  .invoke(null, snapshot.getConstructor().newInstance()));
            assertEquals(false, validator.getMethod("isDetached", loader.loadClass(IAsyncPlanCalculator.class.getName()))
                  .invoke(null, calculator.getConstructor().newInstance()));
        }
    }

    @Test
    void rawResourcesAndResourceSubclassesCannotCrossTheWorkerBoundary() {
        Object[] resources = {new ItemStack(Items.IRON_INGOT), new FluidStack(FluidRegistry.WATER, 1),
              new OwnerFluid(), new NBTTagCompound(), Items.IRON_INGOT, FluidRegistry.WATER};
        for (Object resource : resources) {
            assertFalse(AsyncPlanSafetyValidator.isDetachedValue(resource), resource.getClass().getName());
            assertFalse(AsyncPlanSafetyValidator.isDetachedValue(new Extension(resource)));
        }
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(new Extension(
              ImmutableResourceSnapshot.of(new ItemStack(Items.IRON_INGOT)))));
    }

    @Test
    void implicitEnclosingOwnerIsCheckedForSnapshotsAndPlans() {
        OwnerTile owner = new OwnerTile();
        assertFalse(AsyncPlanSafetyValidator.isDetachedValue(owner.new InnerSnapshot()));
        assertFalse(AsyncPlanSafetyValidator.isDetachedValue(owner.new InnerPlan()));
    }

    @Test
    void mutableFieldsNumbersEnumsAndCollectionsCannotHideLiveState() {
        Object[] values = {new AtomicInteger(1), new OwnerNumber(), OwnerEnum.VALUE,
              new OwnerMap(), new OwnerList(), new HashMap<>(), new ArrayList<>(),
              Collections.unmodifiableMap(new OwnerMap()), Collections.unmodifiableList(new OwnerList()),
              Collections.singletonMap("nested", new OwnerFluid())};
        for (Object value : values) {
            assertFalse(AsyncPlanSafetyValidator.isDetachedValue(new Extension(value)), value.getClass().getName());
        }
        assertFalse(AsyncPlanSafetyValidator.isDetachedValue(new MutableExtension()));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(new Extension(Collections.singletonMap("frozen",
              Collections.singletonList(ImmutableResourceSnapshot.descriptor("test:value", 2))))));
        assertTrue(AsyncPlanSafetyValidator.isDetachedValue(new Extension(PlainEnum.VALUE)));
    }

    @Test
    void excessiveExtensionNestingFailsClosed() {
        Object value = 1;
        for (int i = 0; i < 300; i++) value = new Extension(value);
        assertFalse(AsyncPlanSafetyValidator.isDetachedValue(value));
    }

    public static final class ClientSnapshot extends RecipeRunSnapshot {
        public final TextureAtlasSprite icon = null;
        public ClientSnapshot() { super("test:client", 0, 0, 0); }
    }

    public static final class ClientCalculator implements IAsyncPlanCalculator<Integer, Integer> {
        public static TextureAtlasSprite icon;
        public Integer calculate(Integer snapshot) { return snapshot; }
    }

    private static class Extension extends RecipeRunSnapshot {
        private final Object value;
        Extension(Object value) { super("test:extension", 0, 0, 0); this.value = value; }
    }

    private static final class MutableExtension extends RecipeRunSnapshot {
        private Object value = "initially safe";
        MutableExtension() { super("test:mutable", 0, 0, 0); }
    }

    private static final class OwnerTile extends TileEntity {
        final class InnerSnapshot extends RecipeRunSnapshot {
            InnerSnapshot() { super("test:inner", 0, 0, 0); }
            TileEntity owner() { return OwnerTile.this; }
        }
        final class InnerPlan extends RecipeExecutionPlan {
            InnerPlan() { super(RecipeExecutionPlan.builder("test:inner")); }
            TileEntity owner() { return OwnerTile.this; }
        }
    }

    private static final class OwnerFluid extends FluidStack {
        private final TileEntity owner = new OwnerTile();
        OwnerFluid() { super(FluidRegistry.WATER, 1); }
    }

    private static final class OwnerMap extends HashMap<String, Object> {
        private final TileEntity owner = new OwnerTile();
    }

    private static final class OwnerList extends ArrayList<Object> {
        private final TileEntity owner = new OwnerTile();
    }

    private static final class OwnerNumber extends Number {
        private final TileEntity owner = new OwnerTile();
        public int intValue() { return 1; }
        public long longValue() { return 1; }
        public float floatValue() { return 1; }
        public double doubleValue() { return 1; }
    }

    private enum OwnerEnum {
        VALUE;
        private final TileEntity owner = new OwnerTile();
    }

    private enum PlainEnum { VALUE }
}
