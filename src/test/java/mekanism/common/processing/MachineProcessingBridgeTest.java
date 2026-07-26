package mekanism.common.processing;

import mekanism.api.AutomationType;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.MachineTransferPlan;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.infuse.InfuseType;
import mekanism.common.Upgrade;
import mekanism.common.TestBootstrap;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.recipe.inputs.ChemicalGasInput;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mekanism.common.recipe.machines.ReplicatorFluidStackRecipe;
import mekanism.common.recipe.machines.ReplicatorGasStackRecipe;
import mekanism.common.recipe.machines.ReplicatorItemStackRecipe;
import mekanism.common.recipe.machines.SawmillRecipe;
import mekanism.common.recipe.processing.MachineRecipeRouteCollectors;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.machine.TileEntityCrusher;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.tile.prefab.MekanismMachineRecipeProviders;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import mekanism.common.upgrade.ITileUpgradeAdapter;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.TileUpgradeRegistry;
import mekanism.common.tier.BaseTier;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MachineProcessingBridgeTest {

    private static final ResourceLocation BASE_PROVIDER = new ResourceLocation("mekanism", "test_processing_base");
    private static final ResourceLocation CHILD_PROVIDER = new ResourceLocation("mekanism", "test_processing_child");
    private static final ResourceLocation EXTERNAL_SUPPORT = new ResourceLocation("mekanism", "test_external_upgrade_support");
    private static final ResourceLocation BASE_ADAPTER = new ResourceLocation("mekanism", "test_upgrade_base");
    private static final ResourceLocation CHILD_ADAPTER = new ResourceLocation("mekanism", "test_upgrade_child");
    private static Gas testGas;
    private static Gas testUUGas;
    private static Fluid testFluid;
    private static InfuseType testInfuseType;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        testGas = GasRegistry.getGas("machine_processing_test_gas");
        if (testGas == null) {
            testGas = GasRegistry.register(new Gas("machine_processing_test_gas", 0x3F7FBF));
        }
        testUUGas = GasRegistry.getGas("machine_processing_test_uu_gas");
        if (testUUGas == null) {
            testUUGas = GasRegistry.register(new Gas("machine_processing_test_uu_gas", 0x7F3FBF));
        }
        testFluid = FluidRegistry.getFluid("machine_processing_test_fluid");
        if (testFluid == null) {
            Fluid fluid = new Fluid("machine_processing_test_fluid",
                  new ResourceLocation("mekanism", "blocks/liquid/liquid"),
                  new ResourceLocation("mekanism", "blocks/liquid/liquid_flow"));
            assertTrue(FluidRegistry.registerFluid(fluid));
            testFluid = fluid;
        }
        testInfuseType = new InfuseType("MACHINE_PROCESSING_TEST", new ResourceLocation("mekanism", "blocks/infuse/Redstone"))
              .setTranslationKey("machine_processing_test");
        InfuseRegistry.registerInfuseType(testInfuseType);
        InfuseRegistry.registerInfuseObject(new ItemStack(Items.NETHER_STAR), new InfuseObject(testInfuseType, 50));
    }

    @Test
    void typedResourcesAreImmutableAndRoundTripLongAmounts() {
        ItemStack tagged = new ItemStack(Items.IRON_INGOT, 3);
        tagged.setTagInfo("marker", new NBTTagCompound());
        MachineResourceStack item = MachineResourceStack.item("item_input", tagged, (long) Integer.MAX_VALUE + 10).withOrder(2);
        MachineResourceStack fluid = MachineResourceStack.fluid("fluid_input", new FluidStack(testFluid, 250));
        MachineResourceStack gas = MachineResourceStack.gas("gas_output", new GasStack(testGas, 125));

        tagged.setCount(1);
        assertEquals((long) Integer.MAX_VALUE + 10, item.amount());
        assertTrue(item.itemStack().isEmpty(), "amounts too large for a 1.12 ItemStack must not be truncated");
        assertEquals(item, MachineResourceStack.read(item.write(new NBTTagCompound())));
        assertEquals(fluid, MachineResourceStack.read(fluid.write(new NBTTagCompound())));
        assertEquals(gas, MachineResourceStack.read(gas.write(new NBTTagCompound())));
        assertEquals(MachineResourceKind.ITEM, item.kind());
    }

    @Test
    void recipeRoutesSeparateGuaranteedAndOptionalOutputs() {
        MachineRecipeRoute route = MachineRecipeRoute.builder("test:item_to_item")
              .recipeKey("mekanism:test_recipe")
              .inputItem("input", new ItemStack(Items.IRON_INGOT, 2))
              .outputItem("output", new ItemStack(Items.GOLD_INGOT))
              .optionalOutputItem("secondary", new ItemStack(Items.DIAMOND))
              .build();

        assertEquals("mekanism:test_recipe", route.recipeKey());
        assertEquals(2, route.inputs().get(0).amount());
        assertEquals(1, route.guaranteedOutputs().size());
        assertEquals(1, route.optionalOutputs().size());
        assertThrows(UnsupportedOperationException.class,
              () -> route.inputs().add(MachineResourceStack.item("other", new ItemStack(Items.COAL))));
    }

    @Test
    void chanceCollectorKeepsSecondaryOutputOptional() {
        SawmillRecipe recipe = new SawmillRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT),
              new ItemStack(Items.DIAMOND), 0.25);
        Map<ItemStackInput, SawmillRecipe> recipes = new LinkedHashMap<>();
        recipes.put(recipe.getInput(), recipe);

        List<MachineRecipeRoute> routes = MachineRecipeRouteCollectors.collectChanceItem(recipes);

        assertEquals(1, routes.size());
        assertEquals(1, routes.get(0).guaranteedOutputs().size());
        assertEquals(Items.GOLD_INGOT, routes.get(0).guaranteedOutputs().get(0).itemStack().getItem());
        assertEquals(1, routes.get(0).optionalOutputs().size());
        assertEquals(Items.DIAMOND, routes.get(0).optionalOutputs().get(0).itemStack().getItem());
    }

    @Test
    void laneExpansionPreservesSharedPortsAndOptionalOutputs() {
        MachineRecipeRoute route = MachineRecipeRoute.builder("test:lanes")
              .inputItem("item_input", new ItemStack(Items.IRON_INGOT))
              .inputGas("gas_input", new GasStack(testGas, 10))
              .outputItem("item_output", new ItemStack(Items.GOLD_INGOT))
              .optionalOutputItem("secondary_item_output", new ItemStack(Items.DIAMOND))
              .build();

        List<MachineRecipeRoute> lanes = MachineRecipeRouteCollectors.expandLanes(
              Collections.singletonList(route), 2, "gas_input");

        assertEquals(2, lanes.size());
        assertEquals("item_input_0", lanes.get(0).inputs().get(0).portId());
        assertEquals("gas_input", lanes.get(0).inputs().get(1).portId());
        assertEquals("item_output_1", lanes.get(1).guaranteedOutputs().get(0).portId());
        assertEquals("secondary_item_output_1", lanes.get(1).optionalOutputs().get(0).portId());
        assertNotEquals(lanes.get(0).recipeKey(), lanes.get(1).recipeKey());
    }

    @Test
    void infusionCollectorScalesSourceItemsToWholeOperations() {
        MetallurgicInfuserRecipe recipe = new MetallurgicInfuserRecipe(
              new InfusionInput(testInfuseType, 20, new ItemStack(Items.IRON_INGOT)), new ItemStack(Items.GOLD_INGOT));
        Map<InfusionInput, MetallurgicInfuserRecipe> recipes = new LinkedHashMap<>();
        recipes.put(recipe.getInput(), recipe);

        List<MachineRecipeRoute> routes = MachineRecipeRouteCollectors.collectInfusionItem(recipes);

        assertEquals(1, routes.size());
        assertEquals(5, routes.get(0).inputs().get(0).amount());
        assertEquals("item_input", routes.get(0).inputs().get(0).portId());
        assertEquals(2, routes.get(0).inputs().get(1).amount());
        assertEquals(Items.NETHER_STAR, routes.get(0).inputs().get(1).itemStack().getItem());
        assertEquals("extra_item_input", routes.get(0).inputs().get(1).portId());
        assertEquals(5, routes.get(0).guaranteedOutputs().get(0).amount());
    }

    @Test
    void replicatorCollectorsKeepConfiguredTemplatesOutOfConsumableInputs() {
        ReplicatorItemStackRecipe itemRecipe = new ReplicatorItemStackRecipe(new ItemStack(Items.IRON_INGOT),
              new GasStack(testUUGas, 25), new ItemStack(Items.IRON_INGOT), 0, 20);
        Map<mekanism.common.recipe.inputs.NucleosynthesizerInput, ReplicatorItemStackRecipe> itemRecipes = new LinkedHashMap<>();
        itemRecipes.put(itemRecipe.getInput(), itemRecipe);

        List<MachineRecipeRoute> itemRoutes = MachineRecipeRouteCollectors.collectReplicatorItemTemplate(
              itemRecipes, new ItemStack(Items.IRON_INGOT));

        assertEquals(1, itemRoutes.size());
        assertEquals(1, itemRoutes.get(0).inputs().size());
        assertEquals("uu_input", itemRoutes.get(0).inputs().get(0).portId());
        assertEquals(MachineResourceKind.GAS, itemRoutes.get(0).inputs().get(0).kind());
        assertTrue(MachineRecipeRouteCollectors.collectReplicatorItemTemplate(
              itemRecipes, new ItemStack(Items.GOLD_INGOT)).isEmpty());

        ReplicatorGasStackRecipe gasRecipe = new ReplicatorGasStackRecipe(new GasStack(testGas, 10),
              new GasStack(testUUGas, 5), new GasStack(testGas, 1), 0, 20);
        Map<ChemicalGasInput, ReplicatorGasStackRecipe> gasRecipes = new LinkedHashMap<>();
        gasRecipes.put(gasRecipe.getInput(), gasRecipe);
        List<MachineRecipeRoute> gasRoutes = MachineRecipeRouteCollectors.collectReplicatorGasTemplate(
              gasRecipes, new GasStack(testGas, 10));

        assertEquals(1, gasRoutes.size());
        assertEquals("uu_input", gasRoutes.get(0).inputs().get(0).portId());
        assertEquals("gas_output", gasRoutes.get(0).guaranteedOutputs().get(0).portId());

        ReplicatorFluidStackRecipe fluidRecipe = new ReplicatorFluidStackRecipe(new FluidStack(testFluid, 100),
              new GasStack(testUUGas, 5), new FluidStack(testFluid, 1), 0, 20);
        Map<GasAndFluidInput, ReplicatorFluidStackRecipe> fluidRecipes = new LinkedHashMap<>();
        fluidRecipes.put(fluidRecipe.getInput(), fluidRecipe);
        List<MachineRecipeRoute> fluidRoutes = MachineRecipeRouteCollectors.collectReplicatorFluidTemplate(
              fluidRecipes, new FluidStack(testFluid, 100));

        assertEquals(1, fluidRoutes.size());
        assertEquals("uu_input", fluidRoutes.get(0).inputs().get(0).portId());
        assertEquals("fluid_output", fluidRoutes.get(0).guaranteedOutputs().get(0).portId());
    }

    @Test
    void builtInMachineProviderExposesStablePhysicalPorts() {
        MekanismMachineRecipeProviders.register();
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(new TileEntityCrusher());

        assertNotNull(provider);
        assertEquals(new ResourceLocation("mekanism", "electric_machine"), provider.id());
        assertEquals(2, provider.getPorts().size());
        assertEquals("item_input", provider.getPorts().get(0).portId());
        assertEquals(MachinePort.Role.INPUT, provider.getPorts().get(0).role());
        assertEquals("item_output", provider.getPorts().get(1).portId());
        assertEquals(MachinePort.Role.OUTPUT, provider.getPorts().get(1).role());
    }

    @Test
    void digitalMinerProviderUsesExternalOutputExtractionRules() {
        MekanismMachineRecipeProviders.register();
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(miner);

        assertNotNull(provider);
        assertEquals(new ResourceLocation("mekanism", "digital_miner"), provider.id());
        assertEquals(27, provider.getPorts().size());
        assertEquals("item_output_0", provider.getPorts().get(0).portId());
        assertEquals(MachinePort.Role.OUTPUT, provider.getPorts().get(0).role());

        BasicInventorySlot restricted = BasicInventorySlot.at(
              (stack, automation) -> automation != AutomationType.EXTERNAL,
              (stack, automation) -> true, null, 0, 0);
        restricted.setStack(new ItemStack(Items.IRON_INGOT));
        MachineResourceStack expected = MachineResourceStack.item("output", new ItemStack(Items.IRON_INGOT));

        assertTrue(MachinePort.item("output", MachinePort.Role.OUTPUT, restricted).canExtract(expected));
        assertFalse(MachinePort.item("output", MachinePort.Role.OUTPUT, restricted, AutomationType.EXTERNAL)
              .canExtract(expected));
        assertEquals(1, restricted.getCount());
    }

    @Test
    void transferPlanCommitsMixedInputsAndExactOutputs() {
        BasicInventorySlot itemSlot = BasicInventorySlot.at(null, 0, 0);
        BasicGasTank gasTank = BasicGasTank.create(1_000, null);
        BasicFluidTank fluidTank = BasicFluidTank.create(1_000, null);
        MachinePort itemPort = MachinePort.item("item", MachinePort.Role.INPUT, itemSlot);
        MachinePort gasPort = MachinePort.gas("gas", MachinePort.Role.INPUT, gasTank);
        MachinePort fluidPort = MachinePort.fluid("fluid", MachinePort.Role.INPUT, fluidTank);

        MachineTransferPlan insert = MachineTransferPlan.create()
              .addInsert(itemPort, MachineResourceStack.item("item", new ItemStack(Items.IRON_INGOT, 2)))
              .addInsert(gasPort, MachineResourceStack.gas("gas", new GasStack(testGas, 100)))
              .addInsert(fluidPort, MachineResourceStack.fluid("fluid", new FluidStack(testFluid, 250)));

        assertTrue(insert.canExecute());
        assertTrue(insert.execute());
        assertEquals(2, itemSlot.getCount());
        assertEquals(100, gasTank.getGasAmount());
        assertEquals(250, fluidTank.getFluidAmount());

        MachinePort output = MachinePort.item("output", MachinePort.Role.OUTPUT, itemSlot);
        MachineTransferPlan extract = MachineTransferPlan.create()
              .addExtract(output, MachineResourceStack.item("output", new ItemStack(Items.IRON_INGOT, 2)));
        assertTrue(extract.execute());
        assertTrue(itemSlot.isEmpty());
        assertEquals(1, extract.getExtracted().size());
        assertEquals(2, extract.getExtracted().get(0).amount());
    }

    @Test
    void failedCommitRestoresEveryTouchedContainer() {
        BasicInventorySlot second = BasicInventorySlot.at(null, 0, 0);
        BasicInventorySlot first = BasicInventorySlot.at(() -> second.setStack(new ItemStack(Items.DIAMOND)), 0, 0);
        MachinePort firstPort = MachinePort.item("first", MachinePort.Role.INPUT, first);
        MachinePort secondPort = MachinePort.item("second", MachinePort.Role.INPUT, second);
        MachineTransferPlan plan = MachineTransferPlan.create()
              .addInsert(firstPort, MachineResourceStack.item("first", new ItemStack(Items.IRON_INGOT)))
              .addInsert(secondPort, MachineResourceStack.item("second", new ItemStack(Items.GOLD_INGOT)));

        assertTrue(plan.canExecute());
        assertFalse(plan.execute(), "the first insert deliberately invalidates the second insert");
        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
        assertTrue(plan.getExtracted().isEmpty());
    }

    @Test
    void mergedTankWrapperSnapshotsRestoreWithoutNotifications() {
        AtomicInteger gasChanges = new AtomicInteger();
        MergedTank gasMergedTank = MergedTank.create(BasicFluidTank.create(1_000, null),
              BasicGasTank.create(1_000, gasChanges::incrementAndGet));
        MachinePort gasPort = MachinePort.gas("gas", MachinePort.Role.INPUT, gasMergedTank.getGasTank());
        assertTrue(gasPort.insert(MachineResourceStack.gas("gas", new GasStack(testGas, 100))));
        MachinePort.Snapshot gasSnapshot = gasPort.snapshot();
        assertTrue(gasPort.insert(MachineResourceStack.gas("gas", new GasStack(testGas, 50))));
        int gasChangesBeforeRestore = gasChanges.get();

        gasSnapshot.restore();

        assertEquals(gasChangesBeforeRestore, gasChanges.get());
        assertEquals(100, gasMergedTank.getGasTank().getStored());

        AtomicInteger fluidChanges = new AtomicInteger();
        MergedTank fluidMergedTank = MergedTank.create(BasicFluidTank.create(1_000, fluidChanges::incrementAndGet),
              BasicGasTank.create(1_000, null));
        MachinePort fluidPort = MachinePort.fluid("fluid", MachinePort.Role.INPUT, fluidMergedTank.getFluidTank());
        assertTrue(fluidPort.insert(MachineResourceStack.fluid("fluid", new FluidStack(testFluid, 100))));
        MachinePort.Snapshot fluidSnapshot = fluidPort.snapshot();
        assertTrue(fluidPort.insert(MachineResourceStack.fluid("fluid", new FluidStack(testFluid, 50))));
        int fluidChangesBeforeRestore = fluidChanges.get();

        fluidSnapshot.restore();

        assertEquals(fluidChangesBeforeRestore, fluidChanges.get());
        assertEquals(100, fluidMergedTank.getFluidTank().getFluidAmount());
    }

    @Test
    void duplicateUnderlyingContainerIsRejected() {
        BasicInventorySlot slot = BasicInventorySlot.at(null, 0, 0);
        MachinePort firstView = MachinePort.item("first", MachinePort.Role.INPUT, slot);
        MachinePort secondView = MachinePort.item("second", MachinePort.Role.INPUT, slot);
        MachineTransferPlan plan = MachineTransferPlan.create()
              .addInsert(firstView, MachineResourceStack.item("first", new ItemStack(Items.IRON_INGOT)))
              .addInsert(secondView, MachineResourceStack.item("second", new ItemStack(Items.IRON_INGOT)));

        assertFalse(plan.canExecute());
        assertFalse(plan.execute());
        assertTrue(slot.isEmpty());
    }

    @Test
    void directPortInsertNeverLeavesAPartialCommit() {
        BasicInventorySlot slot = BasicInventorySlot.at(null, 0, 0);
        MachinePort port = MachinePort.item("input", MachinePort.Role.INPUT, slot);
        ItemStack oversized = new ItemStack(Items.IRON_INGOT, 65);

        assertFalse(port.insert(MachineResourceStack.item("input", oversized)));
        assertTrue(slot.isEmpty());
    }

    @Test
    void providerRegistrySelectsTheMostSpecificTileClass() {
        MachineRecipeProviderRegistry.unregister(BASE_PROVIDER);
        MachineRecipeProviderRegistry.unregister(CHILD_PROVIDER);
        try {
            MachineRecipeProviderRegistry.register(BASE_PROVIDER, BaseTile.class, new MarkerProvider<>("base"));
            MachineRecipeProviderRegistry.register(CHILD_PROVIDER, ChildTile.class, new MarkerProvider<>("child"));

            MachineRecipeProviderRegistry.BoundProvider base = MachineRecipeProviderRegistry.find(new BaseTile());
            MachineRecipeProviderRegistry.BoundProvider child = MachineRecipeProviderRegistry.find(new ChildTile());
            assertNotNull(base);
            assertNotNull(child);
            assertEquals(BASE_PROVIDER, base.id());
            assertEquals(CHILD_PROVIDER, child.id());
            assertEquals("child", child.getRecipeSourceKey());
            assertThrows(IllegalArgumentException.class,
                  () -> MachineRecipeProviderRegistry.register(CHILD_PROVIDER, ChildTile.class, new MarkerProvider<>("duplicate")));
        } finally {
            MachineRecipeProviderRegistry.unregister(BASE_PROVIDER);
            MachineRecipeProviderRegistry.unregister(CHILD_PROVIDER);
        }
    }

    @Test
    void externalUpgradeSupportIsDynamicAndDoesNotReplaceLocalSupport() {
        ExternalUpgradeSupportRegistry.unregister(EXTERNAL_SUPPORT);
        TileEntityContainerBlock tile = new TileEntityContainerBlock("test") {
        };
        TileComponentUpgrade component = new TileComponentUpgrade(tile);
        boolean locallySupported = component.supports(Upgrade.SPEED);
        boolean stoneWasSupported = component.supports(Upgrade.STONE_GENERATOR);
        try {
            ExternalUpgradeSupportRegistry.register(EXTERNAL_SUPPORT, candidate -> candidate == tile,
                  Upgrade.STONE_GENERATOR);
            assertTrue(component.supports(Upgrade.STONE_GENERATOR));
            assertTrue(component.getSupportedTypes().contains(Upgrade.STONE_GENERATOR));
            assertEquals(locallySupported, component.supports(Upgrade.SPEED));
        } finally {
            ExternalUpgradeSupportRegistry.unregister(EXTERNAL_SUPPORT);
        }
        assertEquals(stoneWasSupported, component.supports(Upgrade.STONE_GENERATOR));
    }

    @Test
    void tileUpgradeRegistrySelectsSpecificAdaptersAndInvalidatesItsCache() {
        TileUpgradeRegistry.unregister(BASE_ADAPTER);
        TileUpgradeRegistry.unregister(CHILD_ADAPTER);
        try {
            TileUpgradeRegistry.register(BASE_ADAPTER, BaseTile.class, new MarkerUpgradeAdapter<>(BaseTier.BASIC));
            BaseTile base = new BaseTile();
            ChildTile child = new ChildTile();
            assertEquals(BASE_ADAPTER, TileUpgradeRegistry.find(base).id());
            assertEquals(BASE_ADAPTER, TileUpgradeRegistry.find(child).id());

            TileUpgradeRegistry.register(CHILD_ADAPTER, ChildTile.class, new MarkerUpgradeAdapter<>(BaseTier.ADVANCED));
            assertEquals(CHILD_ADAPTER, TileUpgradeRegistry.find(child).id());
            assertTrue(TileUpgradeRegistry.find(child).canInstallUpgrade(BaseTier.ADVANCED));
            assertFalse(TileUpgradeRegistry.find(child).canInstallUpgrade(BaseTier.BASIC));

            assertTrue(TileUpgradeRegistry.unregister(CHILD_ADAPTER));
            assertEquals(BASE_ADAPTER, TileUpgradeRegistry.find(child).id());
        } finally {
            TileUpgradeRegistry.unregister(BASE_ADAPTER);
            TileUpgradeRegistry.unregister(CHILD_ADAPTER);
        }
    }

    private static class BaseTile extends TileEntity {
    }

    private static class ChildTile extends BaseTile {
    }

    private static class MarkerProvider<TILE extends TileEntity> implements MachineRecipeProvider<TILE> {

        private final String marker;

        private MarkerProvider(String marker) {
            this.marker = marker;
        }

        @Override
        public Object getRecipeSourceKey(TILE tile) {
            return marker;
        }

        @Override
        public java.util.List<MachineRecipeRoute> getRecipeRoutes(TILE tile) {
            return Collections.emptyList();
        }
    }

    private static class MarkerUpgradeAdapter<TILE extends TileEntity> implements ITileUpgradeAdapter<TILE> {

        private final BaseTier supportedTier;

        private MarkerUpgradeAdapter(BaseTier supportedTier) {
            this.supportedTier = supportedTier;
        }

        @Override
        public boolean canInstallUpgrade(TILE tile, BaseTier upgradeTier) {
            return upgradeTier == supportedTier;
        }

        @Override
        public IUpgradeData getUpgradeData(TILE tile, BaseTier upgradeTier) {
            return null;
        }
    }
}
