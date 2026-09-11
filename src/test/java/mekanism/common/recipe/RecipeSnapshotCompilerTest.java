package mekanism.common.recipe;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseType;
import mekanism.api.recipes.IRecipeSignatureSource;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mekanism.common.recipe.machines.OsmiumCompressorRecipe;
import mekanism.common.recipe.machines.WasherRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSnapshotCompilerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void infusionSignatureIgnoresPresentationButTracksRecipeChanges() {
        InfuseType type = new InfuseType("carbon", new ResourceLocation("mekanism", "carbon"));
        MetallurgicInfuserRecipe recipe = new MetallurgicInfuserRecipe(
              new InfusionInput(type, 10, new ItemStack(Items.IRON_INGOT)), new ItemStack(Items.DIAMOND));
        String original = RecipeSnapshotCompiler.semanticSignature(recipe);

        type.iconResource = new ResourceLocation("mekanism", "different_icon");
        type.setTranslationKey("different_translation");
        assertEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
        assertEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe.copy()));

        recipe.getInput().infuse.setAmount(20);
        assertNotEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
        recipe.getInput().infuse.setAmount(10);
        recipe.getInput().infuse.setType(new InfuseType("redstone", type.iconResource));
        assertNotEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
        recipe.getInput().infuse.setType(null);
        assertNotEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
        recipe.getInput().infuse.setType(type);
        recipe.getInput().inputStack.setCount(2);
        assertNotEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
        recipe.getInput().inputStack.setCount(1);
        recipe.getOutput().output.setCount(2);
        assertNotEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
    }

    @Test
    void advancedRecipeSignatureUsesGasIdentityInsteadOfRenderingState() {
        Gas gas = new Gas("osmium", 0xFFFFFF);
        OsmiumCompressorRecipe recipe = new OsmiumCompressorRecipe(
              new AdvancedMachineInput(new ItemStack(Items.IRON_INGOT), gas),
              new ItemStackOutput(new ItemStack(Items.DIAMOND)));
        String original = RecipeSnapshotCompiler.semanticSignature(recipe);

        gas.setTint(0);
        gas.setVisible(false);
        assertEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
        assertEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe.copy()));
        recipe.getInput().gasType = new Gas("oxygen", 0);
        assertNotEquals(original, RecipeSnapshotCompiler.semanticSignature(recipe));
    }

    @Test
    void resourceStacksAndProbabilitiesRetainTheirSemanticData() {
        Gas gas = new Gas("oxygen", 0xFFFFFF);
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(new GasStack(gas, 1)),
              RecipeSnapshotCompiler.semanticSignature(new GasStack(gas, 2)));

        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 100);
        String fluidSignature = RecipeSnapshotCompiler.semanticSignature(fluid);
        fluid.amount++;
        assertNotEquals(fluidSignature, RecipeSnapshotCompiler.semanticSignature(fluid));
        fluid.amount--;
        fluid.tag = new NBTTagCompound();
        fluid.tag.setString("recipe_variant", "other");
        assertNotEquals(fluidSignature, RecipeSnapshotCompiler.semanticSignature(fluid));

        ChanceOutput output = new ChanceOutput(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 0.5);
        String outputSignature = RecipeSnapshotCompiler.semanticSignature(output);
        output.secondaryChance = 0.75;
        assertNotEquals(outputSignature, RecipeSnapshotCompiler.semanticSignature(output));
    }

    @Test
    void washerSignatureTracksBothInputsAndOutput() {
        WasherRecipe recipe = new WasherRecipe(new GasStack(new Gas("dirty", 0), 1),
              new FluidStack(FluidRegistry.WATER, 5), new GasStack(new Gas("clean", 0), 1));
        String signature = RecipeSnapshotCompiler.semanticSignature(recipe);
        assertEquals(signature, RecipeSnapshotCompiler.semanticSignature(recipe.copy()));
        recipe.getInput().ingredientGas.amount++;
        assertNotEquals(signature, RecipeSnapshotCompiler.semanticSignature(recipe));
        recipe.getInput().ingredientGas.amount--;
        recipe.getInput().ingredientFluid.amount++;
        assertNotEquals(signature, RecipeSnapshotCompiler.semanticSignature(recipe));
        recipe.getInput().ingredientFluid.amount--;
        recipe.getOutput().output.amount++;
        assertNotEquals(signature, RecipeSnapshotCompiler.semanticSignature(recipe));
    }

    @Test
    void sharedRegistryValuesDoNotIncludeMutableImplementationFields() {
        Fluid first = new Fluid("signature_test", new ResourceLocation("test", "first"), new ResourceLocation("test", "flow"));
        Fluid second = new Fluid("signature_test", new ResourceLocation("test", "second"), new ResourceLocation("test", "other"));
        assertEquals(RecipeSnapshotCompiler.semanticSignature(first), RecipeSnapshotCompiler.semanticSignature(second));
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(first), RecipeSnapshotCompiler.semanticSignature(FluidRegistry.WATER));
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(Items.IRON_INGOT), RecipeSnapshotCompiler.semanticSignature(Items.DIAMOND));
        assertNotEquals(RecipeSnapshotCompiler.semanticSignature(Blocks.STONE), RecipeSnapshotCompiler.semanticSignature(Blocks.DIRT));
        assertEquals(RecipeSnapshotCompiler.semanticSignature(new ResourceLocation("test", "value")),
              RecipeSnapshotCompiler.semanticSignature(new ResourceLocation("test:value")));
    }

    @Test
    void compiledInfusionDefinitionRemainsDetachedFromLiveRecipe() {
        InfuseType type = new InfuseType("carbon", new ResourceLocation("mekanism", "carbon"));
        MetallurgicInfuserRecipe recipe = new MetallurgicInfuserRecipe(
              new InfusionInput(type, 10, new ItemStack(Items.IRON_INGOT)), new ItemStack(Items.DIAMOND));
        RecipeDefinitionSnapshot compiled = RecipeSnapshotCompiler.compile("infuse", new RecipeGeneration(1, 1), recipe);
        String captured = compiled.getSemanticSignature();
        recipe.getInput().infuse.setAmount(20);
        assertEquals(captured, compiled.getSemanticSignature());
        assertNotEquals(captured, RecipeSnapshotCompiler.semanticSignature(recipe));
    }

    @Test
    void factoryInfusionSignaturesWorkWithoutClientClasses() throws Exception {
        assertServerSignature("infusionRecipes", InfuseType.class.getName());
    }

    @Test
    void factoryAdvancedRecipeSignaturesWorkWithoutClientClasses() throws Exception {
        assertServerSignature("advancedRecipes", Gas.class.getName());
    }

    @Test
    void unknownClientBearingObjectIsRejectedWithoutReflectingOrStringifyingIt() throws Exception {
        try (URLClassLoader loader = serverLoader()) {
            Class<?> type = loader.loadClass(ServerOpaqueRecipe.class.getName());
            assertThrows(NoClassDefFoundError.class, () -> type.getDeclaredFields());
            Object value = type.getConstructor().newInstance();
            Class<?> compiler = loader.loadClass(RecipeSnapshotCompiler.class.getName());
            InvocationTargetException failure = assertThrows(InvocationTargetException.class, () ->
                  compiler.getMethod("semanticSignature", Object.class).invoke(null, Collections.singletonMap("recipe", value)));
            assertEquals(UnsupportedRecipeSignatureException.class.getName(), failure.getCause().getClass().getName());
            assertTrue(failure.getCause().getMessage().contains(type.getName()));
        }
    }

    @Test
    void explicitAdapterDoesNotInspectUndeclaredClientState() throws Exception {
        try (URLClassLoader loader = serverLoader()) {
            Class<?> type = loader.loadClass(ServerAdaptedRecipe.class.getName());
            assertThrows(NoClassDefFoundError.class, () -> type.getDeclaredFields());
            Object value = type.getConstructor().newInstance();
            Class<?> compiler = loader.loadClass(RecipeSnapshotCompiler.class.getName());
            String signature = (String) compiler.getMethod("semanticSignature", Object.class).invoke(null, value);
            assertEquals(RecipeSnapshotCompiler.semanticSignature("test:recipe_v1", Collections.singletonMap("amount", 2)), signature);
        }
    }

    private static URLClassLoader serverLoader() {
        return new ServerClassLoader(new URL[]{
              RecipeSnapshotCompiler.class.getProtectionDomain().getCodeSource().getLocation(),
              RecipeSnapshotCompilerTest.class.getProtectionDomain().getCodeSource().getLocation()
        });
    }

    private static void assertServerSignature(String fixtureMethod, String registryType) throws Exception {
        URL[] locations = {
              RecipeSnapshotCompiler.class.getProtectionDomain().getCodeSource().getLocation(),
              RecipeSnapshotCompilerTest.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (URLClassLoader loader = new ServerClassLoader(locations)) {
            Class<?> type = loader.loadClass(registryType);
            // Reproduce the original failure before testing the semantic projection.
            assertThrows(NoClassDefFoundError.class, () -> type.getDeclaredFields());
            Class<?> fixture = loader.loadClass(ServerRecipes.class.getName());
            Object recipes = fixture.getMethod(fixtureMethod).invoke(null);
            Class<?> compiler = loader.loadClass(RecipeSnapshotCompiler.class.getName());
            String actual = (String) compiler.getMethod("semanticSignature", Object.class).invoke(null, recipes);
            Object clientRecipes = ServerRecipes.class.getMethod(fixtureMethod).invoke(null);
            assertEquals(RecipeSnapshotCompiler.semanticSignature(clientRecipes), actual);
            assertTrue(actual.contains(fixtureMethod.equals("infusionRecipes") ? "infuse_type(6:carbon)" : "gas_type(6:osmium)"));
        }
    }

    // Child-load Mekanism to prevent the normal test classpath from satisfying its client type references.
    private static final class ServerClassLoader extends URLClassLoader {
        private ServerClassLoader(URL[] locations) {
            super(locations, RecipeSnapshotCompilerTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.")) throw new ClassNotFoundException(name);
            if (!name.startsWith("mekanism.")) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) type = findClass(name);
            if (resolve) resolveClass(type);
            return type;
        }
    }

    public static final class ServerRecipes {
        public static Object infusionRecipes() {
            InfuseType type = new InfuseType("carbon", new ResourceLocation("mekanism", "carbon"));
            MetallurgicInfuserRecipe recipe = new MetallurgicInfuserRecipe(
                  new InfusionInput(type, 10, new ItemStack(Items.IRON_INGOT)), new ItemStack(Items.DIAMOND));
            return Arrays.asList(recipe.copy(), null, recipe.copy(), recipe.copy(), null, recipe.copy(), recipe.copy());
        }

        public static Object advancedRecipes() {
            Gas gas = new Gas("osmium", 0xFFFFFF);
            OsmiumCompressorRecipe recipe = new OsmiumCompressorRecipe(
                  new AdvancedMachineInput(new ItemStack(Items.IRON_INGOT), gas),
                  new ItemStackOutput(new ItemStack(Items.DIAMOND)));
            return Collections.singletonList(recipe.copy());
        }
    }

    public static final class ServerOpaqueRecipe {
        public TextureAtlasSprite icon;

        @Override
        public String toString() {
            throw new AssertionError("Unknown recipes must not be stringified");
        }
    }

    public static final class ServerAdaptedRecipe implements IRecipeSignatureSource {
        public TextureAtlasSprite icon;

        @Override
        public String getRecipeSignatureType() { return "test:recipe_v1"; }

        @Override
        public Map<String, ?> getRecipeSignatureData() { return Collections.singletonMap("amount", 2); }
    }
}
