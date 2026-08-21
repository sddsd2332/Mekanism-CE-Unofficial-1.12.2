package mekanism.generators.common;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.mj.MjAPI;
import io.netty.buffer.ByteBuf;
import mekanism.api.MekanismAPI;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.common.*;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.fixers.MekanismDataFixers.MekFixers;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.network.PacketSimpleGui;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.util.StackUtils;
import mekanism.generators.common.content.fission.SynchronizedFissionData;
import mekanism.generators.common.content.turbine.SynchronizedTurbineData;
import mekanism.generators.common.fixers.GeneratorTEFixer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.datafix.FixTypes;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.CompoundDataFixer;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.oredict.OreDictionary;

@Mod(modid = MekanismGenerators.MODID, useMetadata = true, guiFactory = "mekanism.generators.client.gui.GeneratorsGuiFactory",customProperties = {
        @Mod.CustomProperty(k = "license", v = "EUPL-1.2"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/sddsd2332/Mekanism-CE-Unofficial-1.12.2/issues"),
        @Mod.CustomProperty(k = "iconFile", v = "assets/mekanism/icon.png"),
        @Mod.CustomProperty(k = "backgroundFile", v = "assets/mekanismgenerators/background.png")
})
@Mod.EventBusSubscriber()
public class MekanismGenerators implements IModule {

    public static final String MODID = "mekanismgenerators";

    @SidedProxy(clientSide = "mekanism.generators.client.GeneratorsClientProxy", serverSide = "mekanism.generators.common.GeneratorsCommonProxy")
    public static GeneratorsCommonProxy proxy;

    @Instance(MekanismGenerators.MODID)
    public static MekanismGenerators instance;

    /**
     * MekanismGenerators version number
     */
    public static Version versionNumber = Version.get(mekanism.mekanism.Tags.VERSION);
    public static final int DATA_VERSION = 1;
    public static CreativeTabMekanismGenerators tabMekanismGenerators = new CreativeTabMekanismGenerators();
    public static MultiblockManager<SynchronizedTurbineData> turbineManager = new MultiblockManager<>("industrialTurbine");
    public static MultiblockManager<SynchronizedFissionData> fissionManager = new MultiblockManager<>("fissionReactor");

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        // Register blocks and tile entities
        GeneratorsBlocks.registerBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Register items and itemBlocks
        GeneratorsItems.registerItems(event.getRegistry());
        GeneratorsBlocks.registerItemBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        // Register models
        proxy.registerBlockRenders();
        proxy.registerItemRenders();
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
        proxy.loadConfiguration();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        //Add this module to the core list
        Mekanism.modulesLoaded.add(this);

        //Register this module's GUI handler in the simple packet protocol
        PacketSimpleGui.handlers.add(proxy);

        //Set up the GUI handler
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GeneratorsGuiHandler());
        MinecraftForge.EVENT_BUS.register(this);

        //Load the proxy
        proxy.registerTileEntities();
        proxy.registerTESRs();

        CompoundDataFixer fixer = FMLCommonHandler.instance().getDataFixer();
        ModFixs fixes = fixer.init(MODID, DATA_VERSION);
        //Fix old tile entity names
        fixes.registerFix(FixTypes.BLOCK_ENTITY, new GeneratorTEFixer(MekFixers.TILE_ENTITIES));
        imcQueue();
        //Finalization
        Mekanism.logger.info("Loaded MekanismGenerators module.");
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (FuelHandler.BCPresent() && BuildcraftFuelRegistry.fuel != null) {
            for (IFuel s : BuildcraftFuelRegistry.fuel.getFuels()) {
                if (s.getFluid() != null && !GasRegistry.containsGas(s.getFluid().getFluid().getName())) {
                    Gas gas = new Gas(s.getFluid().getFluid());
                    GasRegistry.register(new Gas(s.getFluid().getFluid()));
                    RecipeHandler.addGasStackFuelToEnergyRecipe(new GasStack(gas, s.getTotalBurningTime() / Fluid.BUCKET_VOLUME),RFIntegration.fromRF(s.getPowerPerCycle() / (double) MjAPI.MJ * 20) );
                }
            }

            BuildcraftFuelRegistry.fuel.addFuel(MekanismFluids.Ethene.getFluid(), (RFIntegration.toRFAsLong(12 * MjAPI.MJ)), 40 * Fluid.BUCKET_VOLUME);
        }
    }


    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        //1mB hydrogen + 2*bioFuel/tick*200ticks/100mB * 20x efficiency bonus
       // FuelHandler.addGas(MekanismFluids.Ethene, MekanismConfig.current().general.ETHENE_BURN_TIME.val(), MekanismConfig.current().general.FROM_H2.val() + MekanismConfig.current().generators.bioGeneration.val() * 2 * MekanismConfig.current().general.ETHENE_BURN_TIME.val());


        RecipeHandler.addGasStackFuelToEnergyRecipe(new GasStack(MekanismFluids.Ethene, MekanismConfig.current().general.ETHENE_BURN_TIME.val()), MekanismConfig.current().general.FROM_H2.val() + MekanismConfig.current().generators.bioGeneration.val() * 2 * MekanismConfig.current().general.ETHENE_BURN_TIME.val());

        for (ItemStack ore : OreDictionary.getOres("dustGold", false)) {
            RecipeHandler.addMetallurgicInfuserRecipe(InfuseRegistry.get("CARBON"), 10, StackUtils.size(ore, 4), GeneratorsItems.Hohlraum.getEmptyItem());
        }

        RecipeHandler.addFusionCoolingRecipe(FluidRegistry.getFluidStack("water", 1), FluidRegistry.getFluidStack("steam", 1));
        RecipeHandler.addFusionCoolingRecipe(FluidRegistry.getFluidStack("liquidsodium", 1), FluidRegistry.getFluidStack("liquidsuperheatedsodium", 1));
    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }

    @Override
    public String getName() {
        return "Generators";
    }

    @Override
    public void writeConfig(ByteBuf dataStream, MekanismConfig config) {
        config.generators.write(dataStream);
    }

    @Override
    public void readConfig(ByteBuf dataStream, MekanismConfig destConfig) {
        destConfig.generators.read(dataStream);
    }

    @Override
    public void resetClient() {
        SynchronizedTurbineData.clientRotationMap.clear();
        proxy.setGasGeneratorMaxEnergy();
    }

    @SubscribeEvent
    public void onConfigChanged(OnConfigChangedEvent event) {
        if (event.getModID().equals(MekanismGenerators.MODID) || event.getModID().equals(Mekanism.MODID)) {
            proxy.loadConfiguration();
        }
    }

    @SubscribeEvent
    public void onBlacklistUpdate(MekanismAPI.BoxBlacklistEvent event) {
        // Mekanism Generators multiblock structures
        MekanismAPI.addBoxBlacklist(GeneratorsBlocks.Generator, 5); // Advanced Solar Generator
        MekanismAPI.addBoxBlacklist(GeneratorsBlocks.Generator, 6); // Wind Generator
    }

    private void imcQueue() {
        ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_HELMET, MekanismModules.SOLAR_RECHARGING_UNIT);
        ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_PANTS, GeneratorsModules.GEOTHERMAL_GENERATOR_UNIT);
    }

}
