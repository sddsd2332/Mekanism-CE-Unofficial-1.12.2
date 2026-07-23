package mekanism.common;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismAPI.BoxBlacklistEvent;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.infuse.InfuseType;
import mekanism.api.transmitters.DynamicNetwork.ClientTickUpdate;
import mekanism.api.transmitters.DynamicNetwork.NetworkClientRequest;
import mekanism.api.transmitters.DynamicNetwork.TransmittersAddedEvent;
import mekanism.api.transmitters.TransmitterNetworkRegistry;
import mekanism.client.ClientTickHandler;
import mekanism.client.render.hud.MekaSuitEnergyLevel;
import mekanism.client.render.hud.MekanismHUD;
import mekanism.client.render.hud.MekanismStatusOverlay;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.base.IModule;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.chunkloading.ChunkManager;
import mekanism.common.command.CommandMek;
import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.content.gear.MekaSuitDispenseBehavior;
import mekanism.common.content.gear.ModuleDispenseBehavior;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.content.matrix.SynchronizedMatrixData;
import mekanism.common.content.qio.QIOStorageManager;
import mekanism.common.content.sps.SynchronizedSPSData;
import mekanism.common.content.tank.SynchronizedTankData;
import mekanism.common.content.transporter.PathfinderCache;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.entity.EntityBalloon;
import mekanism.common.entity.EntityFlame;
import mekanism.common.entity.EntityObsidianTNT;
import mekanism.common.entity.EntityRobit;
import mekanism.common.entity.baby.*;
import mekanism.common.fixers.MekanismDataFixers;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.integration.IMCHandler;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.crafttweaker.CrafttweakerIntegration;
import mekanism.common.integration.multipart.MultipartMekanism;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketSimpleGui;
import mekanism.common.network.PacketTransmitterUpdate.PacketType;
import mekanism.common.network.PacketTransmitterUpdate.TransmitterUpdateMessage;
import mekanism.common.recipe.BinRecipe;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.SmeltingRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.*;
import mekanism.common.tile.factory.*;
import mekanism.common.tile.laser.TileEntityLaser;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.tile.laser.TileEntityLaserTractorBeam;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.multiblock.*;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.tile.qio.TileEntityQIOImporter;
import mekanism.common.tile.qio.TileEntityQIOExporter;
import mekanism.common.tile.qio.TileEntityQIORedstoneAdapter;
import mekanism.common.tile.transmitter.*;
import mekanism.common.transmitters.grid.EnergyNetwork.EnergyTransferEvent;
import mekanism.common.transmitters.grid.FluidNetwork.FluidTransferEvent;
import mekanism.common.transmitters.grid.GasNetwork.GasTransferEvent;
import mekanism.common.voice.VoiceServerManager;
import mekanism.common.world.GenHandler;
import mekanism.mekanism.Tags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeEnd;
import net.minecraft.world.biome.BiomeHell;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeSnow;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

@Mod(modid = Tags.MOD_ID,
        useMetadata = true,
        guiFactory = "mekanism.client.gui.ConfigGuiFactory",
        acceptedMinecraftVersions = "[1.12,1.13)",
        version = Tags.VERSION,
        customProperties = {
                @Mod.CustomProperty(k = "license", v = "EUPL-1.2"),
                @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/sddsd2332/Mekanism-CE-Unofficial-1.12.2/issues"),
                @Mod.CustomProperty(k = "iconFile", v = "assets/mekanism/icon.png"),
                @Mod.CustomProperty(k = "backgroundFile", v = "assets/mekanism/background.png")
        })
@Mod.EventBusSubscriber()
public class Mekanism {

    public static final String MODID = Tags.MOD_ID;
    public static final String MOD_NAME = "Mekanism";
    public static final String LOG_TAG = '[' + MOD_NAME + ']';
    public static final PlayerState playerState = new PlayerState();
    /**
     * Mekanism Packet Pipeline
     */
    public static PacketHandler packetHandler = new PacketHandler();
    /**
     * Mekanism logger instance
     */
    public static Logger logger = LogManager.getLogger(MOD_NAME);
    /**
     * Mekanism proxy instance
     */
    @SidedProxy(clientSide = "mekanism.client.ClientProxy", serverSide = "mekanism.common.CommonProxy")
    public static CommonProxy proxy;
    /**
     * Mekanism mod instance
     */
    @Instance(MODID)
    public static Mekanism instance;
    /**
     * Mekanism hooks instance
     */
    public static MekanismHooks hooks = new MekanismHooks();
    /**
     * Mekanism configuration instances
     */
    public static Configuration configuration;
    public static Configuration configurationce;
    public static Configuration configurationgenerators;
    public static Configuration configurationtools;

    public static Configuration configurationMultiblockMachine;
    public static Configuration configurationMeka;
    /**
     * Mekanism version number
     */
    public static Version versionNumber = new Version(999, 999, 999);
    /**
     * MultiblockManagers for various structrures
     */
    public static MultiblockManager<SynchronizedTankData> tankManager = new MultiblockManager<>("dynamicTank");
    public static MultiblockManager<SynchronizedMatrixData> matrixManager = new MultiblockManager<>("inductionMatrix");
    public static MultiblockManager<SynchronizedBoilerData> boilerManager = new MultiblockManager<>(
            "thermoelectricBoiler");
    public static MultiblockManager<SynchronizedSPSData> spsManager = new MultiblockManager<>("supercriticalPhaseShifter");
    /**
     * FrequencyManagers for various networks
     */
    public static FrequencyManager<TeleporterFrequency> publicTeleporters = FrequencyType.TELEPORTER.getManager(null, SecurityMode.PUBLIC);
    public static FrequencyManager<InventoryFrequency> publicEntangloporters = FrequencyType.INVENTORY.getManager(null, SecurityMode.PUBLIC);
    public static FrequencyManager<SecurityFrequency> securityFrequencies = FrequencyType.SECURITY.getManager(null, SecurityMode.PUBLIC);
    /**
     * Mekanism creative tab
     */
    public static CreativeTabMekanism tabMekanism = new CreativeTabMekanism();
    public static CreativeTabMekanismAddition tabMekanismAddition = new CreativeTabMekanismAddition();
    /**
     * List of Mekanism modules loaded
     */
    public static List<IModule> modulesLoaded = new ArrayList<>();
    /**
     * The server's world tick handler.
     */
    public static CommonWorldTickHandler worldTickHandler = new CommonWorldTickHandler();
    /**
     * The Mekanism world generation handler.
     */
    public static GenHandler genHandler = new GenHandler();
    /**
     * The version of ore generation in this version of Mekanism. Increment this every time the default ore generation changes.
     */
    public static int baseWorldGenVersion = 0;
    /**
     * The VoiceServer manager for walkie talkies
     */
    public static VoiceServerManager voiceManager;
    /**
     * The GameProfile used by the dummy Mekanism player
     */
    public static GameProfile gameProfile = new GameProfile(UUID.nameUUIDFromBytes("mekanism.common".getBytes()), Mekanism.LOG_TAG);
    public static KeySync keyMap = new KeySync();
    public static Set<Coord4D> activeVibrators = new ObjectOpenHashSet<>();

    public static final TaskExecutor EXECUTE_MANAGER = new TaskExecutor();

    static {
        MekanismFluids.register();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        // Register blocks and tile entities
        MekanismBlocks.registerBlocks(event.getRegistry());
       // event.getRegistry().register(PortalHelper.BlockPortalOverride.instance);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Register items and itemBlocks
        MekanismItems.registerItems(event.getRegistry());
        MekanismBlocks.registerItemBlocks(event.getRegistry());
        //Integrate certain OreDictionary recipes
        MekanismOreDict.registerOreDict();
    }


    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        int id = 0;
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "ObsidianTNT"), EntityObsidianTNT.class, "ObsidianTNT", id++, Mekanism.instance, 64, 5, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "Robit"), EntityRobit.class, "Robit", id++, Mekanism.instance, 64, 2, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "Balloon"), EntityBalloon.class, "Balloon", id++, Mekanism.instance, 64, 1, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "BabySkeleton"), EntityBabySkeleton.class, "BabySkeleton", id++, Mekanism.instance, 64, 5, true, 0xFFFFFF, 0x800080);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "Flame"), EntityFlame.class, "Flame", id++, Mekanism.instance, 64, 5, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "BabyCreeper"), EntityBabyCreeper.class, "BabyCreeper", id++, Mekanism.instance, 64, 5, true, 0x31E02F, 0x1E1E1E);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "BabyEnderman"), EntityBabyEnderman.class, "BabyEnderman", id++, Mekanism.instance, 64, 5, true, 0x242424, 0x1E1E1E);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "BabyStray"), EntityBabyStray.class, "BabyStray", id++, Mekanism.instance, 64, 5, true, 0x7B9394, 0xF2FAFA);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "BabyWitherSkeleton"), EntityBabyWitherSkeleton.class, "BabyWitherSkeleton", id++, Mekanism.instance, 64, 5, true, 0x303030, 0x525454);
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        // Register models
        proxy.registerBlockRenders();
        proxy.registerItemRenders();
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        MekanismSounds.register(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (hooks.CraftTweakerLoaded) {
            CrafttweakerIntegration.finishRegistryRegistration();
        }
        if (!MekanismConfig.current().mekce.BinRecipeClosed.val()) {
            event.getRegistry().register(new BinRecipe());
        }
        MekanismRecipe.addRecipes();
        GasConversionHandler.addDefaultGasMappings();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removeNuclearCraftShieldingRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!hooks.NuclearCraft) {
            return;
        }
        if (!(event.getRegistry() instanceof IForgeRegistryModifiable)) {
            return;
        }
        @SuppressWarnings("unchecked")
        IForgeRegistryModifiable<IRecipe> recipeRegistry = (IForgeRegistryModifiable<IRecipe>) event.getRegistry();
        int removed = removeNuclearCraftShieldingRecipes(recipeRegistry);
        if (removed > 0) {
            logger.info("Removed {} NC shielding recipes for Mek armor during recipe registration.", removed);
        }
    }

    private static final String NC_SHIELDING_RECIPE_CLASS = "nc.recipe.vanilla.recipe.ShapelessArmorRadShieldingRecipe";
    private static final String NC_MOD_ID = "nuclearcraft";
    private static final String NC_RAD_SHIELDING_ITEM = "rad_shielding";

    private static int removeNuclearCraftShieldingRecipes(IForgeRegistryModifiable<IRecipe> recipeRegistry) {
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (IRecipe recipe : recipeRegistry.getValuesCollection()) {
            if (isNuclearCraftShieldingRecipeForBlockedMekArmor(recipe)) {
                ResourceLocation recipeName = recipe.getRegistryName();
                if (recipeName != null) {
                    toRemove.add(recipeName);
                }
            }
        }
        for (ResourceLocation recipeName : toRemove) {
            recipeRegistry.remove(recipeName);
        }
        return toRemove.size();
    }

    private static boolean isNuclearCraftShieldingRecipeForBlockedMekArmor(IRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        ItemStack output = recipe.getRecipeOutput();
        if (output.isEmpty() || !isMekArmorBlockedForNCShielding(output.getItem())) {
            return false;
        }
        if (NC_SHIELDING_RECIPE_CLASS.equals(recipe.getClass().getName())) {
            return true;
        }
        ResourceLocation recipeName = recipe.getRegistryName();
        if (recipeName == null || !NC_MOD_ID.equals(recipeName.getNamespace())) {
            return false;
        }
        return containsNuclearCraftRadShieldingIngredient(recipe) || recipe.getClass().getName().contains("RadShielding");
    }

    private static boolean containsNuclearCraftRadShieldingIngredient(IRecipe recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) {
                continue;
            }
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation itemName = stack.getItem().getRegistryName();
                if (itemName != null && NC_MOD_ID.equals(itemName.getNamespace()) && NC_RAD_SHIELDING_ITEM.equals(itemName.getPath())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMekArmorBlockedForNCShielding(Item item) {
        return item == MekanismItems.HAZMAT_MASK || item == MekanismItems.HAZMAT_GOWN || item == MekanismItems.HAZMAT_PANTS || item == MekanismItems.HAZMAT_BOOTS ||
                item == MekanismItems.MEKASUIT_HELMET || item == MekanismItems.MEKASUIT_BODYARMOR || item == MekanismItems.MEKASUIT_PANTS || item == MekanismItems.MEKASUIT_BOOTS;
    }


    /**
     * Registers specified items with the Ore Dictionary.
     */


    private static void registerTileEntity(Class<? extends TileEntity> clazz, String name) {
        GameRegistry.registerTileEntity(clazz, new ResourceLocation(MODID, name));
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(Mekanism.MODID, path);
    }

    /**
     * Adds and registers all tile entities.
     */
    private void registerTileEntities() {
        //Tile entities
        registerTileEntity(TileEntityAdvancedBoundingBlock.class, "advanced_bounding_block");
        registerTileEntity(TileEntityAdvancedFactory.class, "advanced_smelting_factory");
        registerTileEntity(TileEntityAmbientAccumulator.class, "ambient_accumulator");
        registerTileEntity(TileEntityBin.class, "bin");
        registerTileEntity(TileEntityBoilerCasing.class, "boiler_casing");
        registerTileEntity(TileEntityBoilerValve.class, "boiler_valve");
        registerTileEntity(TileEntityBoundingBlock.class, "bounding_block");
        registerTileEntity(TileEntityCardboardBox.class, "cardboard_box");
        registerTileEntity(TileEntityChargepad.class, "chargepad");
        registerTileEntity(TileEntityChemicalCrystallizer.class, "chemical_crystallizer");
        registerTileEntity(TileEntityChemicalDissolutionChamber.class, "chemical_dissolution_chamber");
        registerTileEntity(TileEntityChemicalInfuser.class, "chemical_infuser");
        registerTileEntity(TileEntityChemicalInjectionChamber.class, "chemical_injection_chamber");
        registerTileEntity(TileEntityChemicalOxidizer.class, "chemical_oxidizer");
        registerTileEntity(TileEntityChemicalWasher.class, "chemical_washer");
        registerTileEntity(TileEntityCombiner.class, "combiner");
        registerTileEntity(TileEntityCrusher.class, "crusher");
        registerTileEntity(TileEntityDigitalMiner.class, "digital_miner");
        registerTileEntity(TileEntityDiversionTransporter.class, "diversion_transporter");
        registerTileEntity(TileEntityDynamicTank.class, "dynamic_tank");
        registerTileEntity(TileEntityDynamicValve.class, "dynamic_valve");
        registerTileEntity(TileEntityElectricPump.class, "electric_pump");
        registerTileEntity(TileEntityElectrolyticSeparator.class, "electrolytic_separator");
        registerTileEntity(TileEntityEliteFactory.class, "ultimate_smelting_factory");
        //Why did the original author of Mekanism write "elite_smelting_factory" as "ultimate_smelting_factory"?
        registerTileEntity(TileEntityEnergizedSmelter.class, "energized_smelter");
        registerTileEntity(TileEntityEnergyCube.class, "energy_cube");
        registerTileEntity(TileEntityEnrichmentChamber.class, "enrichment_chamber");
        registerTileEntity(TileEntityBasicFactory.class, "smelting_factory");
        registerTileEntity(TileEntityFluidTank.class, "fluid_tank");
        registerTileEntity(TileEntityFluidicPlenisher.class, "fluidic_plenisher");
        registerTileEntity(TileEntityFormulaicAssemblicator.class, "formulaic_assemblicator");
        registerTileEntity(TileEntityFuelwoodHeater.class, "fuelwood_heater");
        registerTileEntity(TileEntityGasTank.class, "gas_tank");
        registerTileEntity(TileEntityGlowPanel.class, "glow_panel");
        registerTileEntity(TileEntityInductionCasing.class, "induction_casing");
        registerTileEntity(TileEntityInductionCell.class, "induction_cell");
        registerTileEntity(TileEntityInductionPort.class, "induction_port");
        registerTileEntity(TileEntityInductionProvider.class, "induction_provider");
        registerTileEntity(TileEntityLaser.class, "laser");
        registerTileEntity(TileEntityLaserAmplifier.class, "laser_amplifier");
        registerTileEntity(TileEntityLaserTractorBeam.class, "laser_tractor_beam");
        registerTileEntity(TileEntityLogisticalSorter.class, "logistical_sorter");
        registerTileEntity(TileEntityLogisticalTransporter.class, "logistical_transporter");
        registerTileEntity(TileEntityMechanicalPipe.class, "mechanical_pipe");
        registerTileEntity(TileEntityMetallurgicInfuser.class, "metallurgic_infuser");
        registerTileEntity(TileEntityOredictionificator.class, "oredictionificator");
        registerTileEntity(TileEntityOsmiumCompressor.class, "osmium_compressor");
        registerTileEntity(TileEntityPRC.class, "pressurized_reaction_chamber");
        registerTileEntity(TileEntityPersonalChest.class, "personal_chest");
        registerTileEntity(TileEntityPrecisionSawmill.class, "precision_sawmill");
        registerTileEntity(TileEntityPressureDisperser.class, "pressure_disperser");
        registerTileEntity(TileEntityPressurizedTube.class, "pressurized_tube");
        registerTileEntity(TileEntityPurificationChamber.class, "purification_chamber");
        registerTileEntity(TileEntityQuantumEntangloporter.class, "quantum_entangloporter");
        registerTileEntity(TileEntityResistiveHeater.class, "resistive_heater");
        registerTileEntity(TileEntityRestrictiveTransporter.class, "restrictive_transporter");
        registerTileEntity(TileEntityRotaryCondensentrator.class, "rotary_condensentrator");
        registerTileEntity(TileEntitySecurityDesk.class, "security_desk");
        registerTileEntity(TileEntitySeismicVibrator.class, "seismic_vibrator");
        registerTileEntity(TileEntitySolarNeutronActivator.class, "solar_neutron_activator");
        registerTileEntity(TileEntityStructuralGlass.class, "structural_glass");
        registerTileEntity(TileEntitySuperheatingElement.class, "superheating_element");
        registerTileEntity(TileEntityTeleporter.class, "mekanism_teleporter");
        registerTileEntity(TileEntityThermalEvaporationBlock.class, "thermal_evaporation_block");
        registerTileEntity(TileEntityThermalEvaporationController.class, "thermal_evaporation_controller");
        registerTileEntity(TileEntityThermalEvaporationValve.class, "thermal_evaporation_valve");
        registerTileEntity(TileEntityThermodynamicConductor.class, "thermodynamic_conductor");
        registerTileEntity(TileEntityUniversalCable.class, "universal_cable");

        /**
         * Start adding new machines
         * Why don't I make new modules because my java is not very good
         * There is no way to split them
         */

        registerTileEntity(TileEntityUltimateFactory.class, "elite_smelting_factory");
        //Because it was registered as a ultimate_smelting_factory by the original elite factory
        //So the ultimate factory here is registered as elite_smelting_factory
        registerTileEntity(TileEntityIsotopicCentrifuge.class, "isotopic_centrifuge");
        registerTileEntity(TileEntityNutritionalLiquifier.class, "nutritional_liquifier");
        registerTileEntity(TileEntitySuperchargedCoil.class, "supercharged_coil");
        registerTileEntity(TileEntitySPSCasing.class, "sps_casing");
        registerTileEntity(TileEntitySPSPort.class, "sps_port");
        registerTileEntity(TileEntityOrganicFarm.class, "organic_farm");
        registerTileEntity(TileEntityAntiprotonicNucleosynthesizer.class, "antiprotonic_nucleosynthesizer");
        registerTileEntity(TileEntityStamping.class, "stamping");
        registerTileEntity(TileEntityRolling.class, "rolling");
        registerTileEntity(TileEntityBrushed.class, "brushed");
        registerTileEntity(TileEntityTurning.class, "turning");
        registerTileEntity(TileEntityAlloy.class, "alloy");
        registerTileEntity(TileEntityCellExtractor.class, "cell_extractor");
        registerTileEntity(TileEntityCellSeparator.class, "cell_separator");
        registerTileEntity(TileEntityRecycler.class, "Recycler");
        registerTileEntity(TileEntityIndustrialAlarm.class, "industrial_alarm");
        registerTileEntity(TileEntityAmbientAccumulatorEnergy.class, "ambient_accumulator_energy");
        registerTileEntity(TileEntityHybridStorage.class, "Hybrid_storage");
        registerTileEntity(TileEntityModificationStation.class, "Modification_Station");
        registerTileEntity(TileEntityRadioactiveWasteBarrel.class, "radioactive_waste_barrel");
        registerTileEntity(TileEntitySPS.class, "sps");
        registerTileEntity(TileEntityDimensionalStabilizer.class, "dimensional_stabilizer");
        registerTileEntity(TileEntityQIODriveArray.class, "qio_drive_array");
        registerTileEntity(TileEntityQIODashboard.class, "qio_dashboard");
        registerTileEntity(TileEntityQIOImporter.class, "qio_importer");
        registerTileEntity(TileEntityQIOExporter.class, "qio_exporter");
        registerTileEntity(TileEntityQIORedstoneAdapter.class, "qio_redstone_adapter");
        /**
         * End of adding machine
         */

        //Register the TESRs
        proxy.registerTESRs();

        MekanismDataFixers.register();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (MekanismConfig.current().general.voiceServerEnabled.val()) {
            voiceManager.start();
        }
        CommandMek.register(event);
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (MekanismConfig.current().general.voiceServerEnabled.val()) {
            voiceManager.stop();
        }

        //Clear all cache data
        playerState.clear(false);
        activeVibrators.clear();
        worldTickHandler.resetRegenChunks();
        //Reset consistent managers
        MultiblockManager.reset();
        SynchronizedBoilerData.hotMap.clear();
        FrequencyManager.reset();
        TransporterManager.reset();
        PathfinderCache.reset();
        TransmitterNetworkRegistry.reset();
     //   ModuleHelper.get().resetSupportedContainers();

        RadiationManager.INSTANCE.reset();
        QIOStorageManager.shutdown();
    }

    @EventHandler
    public void loadComplete(FMLInterModComms.IMCEvent event) {
        new IMCHandler().onIMCEvent(event.getMessages());
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        if (!hooks.NuclearCraft) {
            return;
        }
        if (!(ForgeRegistries.RECIPES instanceof IForgeRegistryModifiable)) {
            return;
        }
        @SuppressWarnings("unchecked")
        IForgeRegistryModifiable<IRecipe> recipeRegistry = (IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES;
        int removed = removeNuclearCraftShieldingRecipes(recipeRegistry);
        if (removed > 0) {
            logger.info("Removed {} NC shielding recipes for Mek armor during load complete.", removed);
        }
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MekanismCriteriaTriggers.init();

        //sanity check the api location if not deobf
        if (!((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment"))) {
            String apiLocation = MekanismAPI.class.getProtectionDomain().getCodeSource().getLocation().toString();
            if (apiLocation.toLowerCase(Locale.ROOT).contains("-api.jar")) {
                proxy.throwApiPresentException();
            }
        }

        //Set the mod's configuration
        configuration = new Configuration(new File("config/mekanism/Mekanism.cfg"));
        configurationce = new Configuration(new File("config/mekanism/MekanismCE.cfg"));
        configurationgenerators = new Configuration(new File("config/mekanism/MekanismGenerators.cfg"));
        configurationtools = new Configuration(new File("config/mekanism/MekanismTools.cfg"));
        configurationMultiblockMachine = new Configuration(new File("config/mekanism/MekanismMultiblockMachine.cfg"));
        configurationMeka = new Configuration(new File("config/mekanism/MekaSuitArmor.cfg"));


        //Load configuration
        proxy.loadConfiguration();

        proxy.onConfigSync(false);

        MinecraftForge.EVENT_BUS.register(MekanismItems.GasMask);
        MinecraftForge.EVENT_BUS.register(MekanismItems.FreeRunners);

        if (MekanismHooks.Mods.MCMP.isPresent()) {
            //Set up multiparts
            new MultipartMekanism();
        } else {
            logger.info("Didn't detect MCMP, ignoring compatibility package");
        }

        Mekanism.proxy.preInit();
        //Register infuses
        InfuseRegistry.registerInfuseType(new InfuseType("CARBON", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Carbon")).setTranslationKey("carbon"));
        InfuseRegistry.registerInfuseType(new InfuseType("TIN", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Tin")).setTranslationKey("tin"));
        InfuseRegistry.registerInfuseType(new InfuseType("DIAMOND", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Diamond")).setTranslationKey("diamond"));
        InfuseRegistry.registerInfuseType(new InfuseType("REDSTONE", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Redstone")).setTranslationKey("redstone"));
        InfuseRegistry.registerInfuseType(new InfuseType("FUNGI", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Fungi")).setTranslationKey("fungi"));
        InfuseRegistry.registerInfuseType(new InfuseType("BIO", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Bio")).setTranslationKey("bio"));
        InfuseRegistry.registerInfuseType(new InfuseType("OBSIDIAN", new ResourceLocation(Mekanism.MODID, "blocks/infuse/Obsidian")).setTranslationKey("obsidian"));

        Capabilities.registerCapabilities();
        hooks.hookPreInit();
        if (hooks.CraftTweakerLoaded) {
            CrafttweakerIntegration.loadRegistryScripts();
        }
        MinecraftForge.EVENT_BUS.register(Mekanism.EXECUTE_MANAGER);
        Mekanism.EXECUTE_MANAGER.init();
        Mekanism.logger.info(String.format("Parallel executor is ready (%s Threads), Let's get started!!!", TaskExecutor.THREAD_COUNT));

    }


    @EventHandler
    public void init(FMLInitializationEvent event) {
        //Register the mod's world generators
        GameRegistry.registerWorldGenerator(genHandler, 1);

        //Register the mod's GUI handler
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new CoreGuiHandler());


        //Register player tracker
        MinecraftForge.EVENT_BUS.register(new CommonPlayerTracker());
        MinecraftForge.EVENT_BUS.register(new CommonPlayerTickHandler());

        MinecraftForge.EVENT_BUS.register(RadiationManager.INSTANCE);

        //Initialization notification
        logger.info("Version " + versionNumber + " initializing...");

        //Register with ForgeChunkManager
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new ChunkManager());

        registerDispenseBehavior(new ModuleDispenseBehavior(), MekanismItems.MEKA_TOOL);
        registerDispenseBehavior(new MekaSuitDispenseBehavior(), MekanismItems.MEKASUIT_HELMET, MekanismItems.MEKASUIT_BODYARMOR, MekanismItems.MEKASUIT_PANTS, MekanismItems.MEKASUIT_BOOTS);

        //Register to receive subscribed events
        MinecraftForge.EVENT_BUS.register(this);

        //Register this module's GUI handler in the simple packet protocol
        PacketSimpleGui.handlers.add(0, proxy);

        //Set up VoiceServerManager
        if (MekanismConfig.current().general.voiceServerEnabled.val()) {
            voiceManager = new VoiceServerManager();
        }

        //Register with TransmitterNetworkRegistry
        TransmitterNetworkRegistry.initiate();

        //Add baby skeleton spawner
        if (MekanismConfig.current().general.spawnBabySkeletons.val()) {
            BiomeProvider.allowedBiomes.forEach(biome -> {
                if (biome.getSpawnableList(EnumCreatureType.MONSTER) != null && !biome.getSpawnableList(EnumCreatureType.MONSTER).isEmpty()) {
                    EntityRegistry.addSpawn(EntityBabySkeleton.class, 40, 1, 3, EnumCreatureType.MONSTER, biome);
                    EntityRegistry.addSpawn(EntityBabyCreeper.class, 40, 1, 3, EnumCreatureType.MONSTER, biome);
                    EntityRegistry.addSpawn(EntityBabyEnderman.class, 5, 1, 3, EnumCreatureType.MONSTER, biome);
                }
            });
            ForgeRegistries.BIOMES.forEach(biome -> {
                if (biome.getSpawnableList(EnumCreatureType.MONSTER) != null && !biome.getSpawnableList(EnumCreatureType.MONSTER).isEmpty()) {
                    if (biome instanceof BiomeSnow) {
                        EntityRegistry.addSpawn(EntityBabyStray.class, 40, 1, 3, EnumCreatureType.MONSTER, biome);
                    }
                    if (biome instanceof BiomeEnd) {
                        EntityRegistry.addSpawn(EntityBabyEnderman.class, 5, 1, 3, EnumCreatureType.MONSTER, biome);
                    }
                    if (biome instanceof BiomeHell) {
                        EntityRegistry.addSpawn(EntityBabyEnderman.class, 1, 1, 3, EnumCreatureType.MONSTER, biome);
                        EntityRegistry.addSpawn(EntityBabyWitherSkeleton.class, 1, 1, 3, EnumCreatureType.MONSTER, biome);
                    }
                }
            });
        }

        //Load this module
        registerTileEntities();

        hooks.hookInit();
        imcQueue();

        //Packet registrations
        packetHandler.initialize();

        //Load proxy
        Mekanism.proxy.init();

        //Completion notification
        logger.info("Loading complete.");

        //Success message
        logger.info("Mod loaded.");

        if (!hooks.MekanismMixinHelp) {
            logger.info("The Mekanism Mixin Help mod is not installed, which causes some modifications to not be implemented");
        }

    }

    private static void registerDispenseBehavior(IBehaviorDispenseItem behavior, Item... itemProviders) {
        for (Item itemProvider : itemProviders) {
            BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(itemProvider, behavior);
        }
    }

    private void imcQueue() {
        Item[] addModulesToAll = {MekanismItems.MEKASUIT_HELMET, MekanismItems.MEKASUIT_BODYARMOR, MekanismItems.MEKASUIT_PANTS, MekanismItems.MEKASUIT_BOOTS, MekanismItems.MEKA_TOOL, MekanismItems.MEKA_FISHING_ROD};
        Item[] addMekaSuitModules = new Item[]{MekanismItems.MEKASUIT_HELMET, MekanismItems.MEKASUIT_BODYARMOR, MekanismItems.MEKASUIT_PANTS, MekanismItems.MEKASUIT_BOOTS};

        for (Item stack : addModulesToAll) {
            ModuleHelper.get().setSupported(stack, MekanismModules.ENERGY_UNIT, MekanismModules.MAGNETIC_UNIT);
        }


        for (Item stack : addMekaSuitModules) {
            ModuleHelper.get().setSupported(stack, MekanismModules.COLOR_MODULATION_UNIT, MekanismModules.LASER_DISSIPATION_UNIT, MekanismModules.RADIATION_SHIELDING_UNIT);
        }


        ModuleHelper.get().setSupported(MekanismItems.MEKA_TOOL, MekanismModules.ATTACK_AMPLIFICATION_UNIT, MekanismModules.SILK_TOUCH_UNIT, MekanismModules.FORTUNE_UNIT, MekanismModules.BLASTING_UNIT, MekanismModules.VEIN_MINING_UNIT, MekanismModules.FARMING_UNIT, MekanismModules.SHEARING_UNIT, MekanismModules.TELEPORTATION_UNIT, MekanismModules.EXCAVATION_ESCALATION_UNIT);
        if (Mekanism.hooks.FarmersDelightLegacyLoaded) {
            ModuleHelper.get().setSupported(MekanismItems.MEKA_TOOL,MekanismModules.LASER_CUTTER_UNIT);
        }

        ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_HELMET, MekanismModules.ELECTROLYTIC_BREATHING_UNIT, MekanismModules.INHALATION_PURIFICATION_UNIT, MekanismModules.VISION_ENHANCEMENT_UNIT, MekanismModules.NUTRITIONAL_INJECTION_UNIT);

        ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_BODYARMOR, MekanismModules.JETPACK_UNIT, MekanismModules.GRAVITATIONAL_MODULATING_UNIT, MekanismModules.CHARGE_DISTRIBUTION_UNIT, MekanismModules.DOSIMETER_UNIT, MekanismModules.GEIGER_UNIT, MekanismModules.HEALTH_REGENERATION_UNIT);

        if (Mekanism.hooks.MekanismMixinHelp) {
            ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_BODYARMOR, MekanismModules.ELYTRA_UNIT);
        }

        ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_PANTS, MekanismModules.LOCOMOTIVE_BOOSTING_UNIT, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT, MekanismModules.HYDROSTATIC_REPULSOR_UNIT, MekanismModules.MOTORIZED_SERVO_UNIT);

        ModuleHelper.get().setSupported(MekanismItems.MEKASUIT_BOOTS, MekanismModules.HYDRAULIC_PROPULSION_UNIT, MekanismModules.MAGNETIC_ATTRACTION_UNIT, MekanismModules.FROST_WALKER_UNIT);

        ModuleHelper.get().setSupported(MekanismItems.MEKA_FISHING_ROD, MekanismModules.FISHING_COLLECTING_UNIT, MekanismModules.FISHING_SPEED_UNIT, MekanismModules.FISHING_INTELLIGENT_UNIT, MekanismModules.FISHING_MULTIPLE_UNIT);
    }


    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        logger.info("Fake player readout: UUID = " + gameProfile.getId().toString() + ", name = " + gameProfile.getName());

        // Add all furnace recipes to the energized smelter
        // Must happen after CraftTweaker for vanilla stuff has run.
        FurnaceRecipes.instance().getSmeltingList().forEach((key, value) -> {
            SmeltingRecipe recipe = new SmeltingRecipe(new ItemStackInput(key), new ItemStackOutput(value));
            Recipe.ENERGIZED_SMELTER.put(recipe);
        });
        ModuleHelper.get().processSupportedContainers();
        hooks.hookPostInit();
        if (MekanismConfig.current().mekce.EnableSuperFumoRecipe.val()) {
            MekanismRecipe.SuperFumoReciperRegister();
        }
        MinecraftForge.EVENT_BUS.post(new BoxBlacklistEvent());
        Mekanism.proxy.postInit();
        logger.info("Hooking complete.");
    }

    @SubscribeEvent
    public void onEnergyTransferred(EnergyTransferEvent event) {
        try {
            packetHandler.sendToReceivers(new TransmitterUpdateMessage(PacketType.ENERGY, event.energyNetwork.firstTransmitter().coord(), event.power),
                    event.energyNetwork.getPacketRange());
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onGasTransferred(GasTransferEvent event) {
        try {
            packetHandler.sendToReceivers(new TransmitterUpdateMessage(PacketType.GAS, event.gasNetwork.firstTransmitter().coord(), event.transferType, event.didTransfer),
                    event.gasNetwork.getPacketRange());
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onLiquidTransferred(FluidTransferEvent event) {
        try {
            packetHandler.sendToReceivers(new TransmitterUpdateMessage(PacketType.FLUID, event.fluidNetwork.firstTransmitter().coord(), event.fluidType, event.didTransfer),
                    event.fluidNetwork.getPacketRange());
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onTransmittersAddedEvent(TransmittersAddedEvent event) {
        try {
            packetHandler.sendToReceivers(new TransmitterUpdateMessage(PacketType.UPDATE, event.network.firstTransmitter().coord(), event.newNetwork, event.newTransmitters),
                    event.network.getPacketRange());
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onNetworkClientRequest(NetworkClientRequest event) {
        try {
            packetHandler.sendToServer(new DataRequestMessage(Coord4D.get(event.tileEntity)));
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onClientTickUpdate(ClientTickUpdate event) {
        try {
            if (event.operation == 0) {
                ClientTickHandler.tickingSet.remove(event.network);
            } else {
                ClientTickHandler.tickingSet.add(event.network);
            }
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onBlacklistUpdate(BoxBlacklistEvent event) {
        event.blacklistWildcard(MekanismBlocks.CardboardBox);

        // Mekanism multiblock structures
        event.blacklistWildcard(MekanismBlocks.BoundingBlock);
        event.blacklist(MekanismBlocks.BasicBlock2, 9);   // Security Desk
        event.blacklist(MekanismBlocks.MachineBlock, 4);  // Digital Miner
        event.blacklist(MekanismBlocks.MachineBlock2, 9); // Seismic Vibrator
        event.blacklist(MekanismBlocks.MachineBlock3, 1); // Solar Neutron Activator

        // Minecraft unobtainable
        event.blacklist(Blocks.BEDROCK, 0);
        event.blacklistWildcard(Blocks.PORTAL);
        event.blacklistWildcard(Blocks.END_PORTAL);
        event.blacklistWildcard(Blocks.END_PORTAL_FRAME);

        // Minecraft multiblock structures
        event.blacklistWildcard(Blocks.BED);
        event.blacklistWildcard(Blocks.OAK_DOOR);
        event.blacklistWildcard(Blocks.SPRUCE_DOOR);
        event.blacklistWildcard(Blocks.BIRCH_DOOR);
        event.blacklistWildcard(Blocks.JUNGLE_DOOR);
        event.blacklistWildcard(Blocks.ACACIA_DOOR);
        event.blacklistWildcard(Blocks.DARK_OAK_DOOR);
        event.blacklistWildcard(Blocks.IRON_DOOR);

        //Extra Utils 2
        event.blacklistWildcard(new ResourceLocation("extrautils2", "machine"));

        //ImmEng multiblocks
        event.blacklistWildcard(new ResourceLocation("immersiveengineering", "metal_device0"));
        event.blacklistWildcard(new ResourceLocation("immersiveengineering", "metal_device1"));
        event.blacklistWildcard(new ResourceLocation("immersiveengineering", "wooden_device0"));
        event.blacklistWildcard(new ResourceLocation("immersiveengineering", "wooden_device1"));
        event.blacklistWildcard(new ResourceLocation("immersiveengineering", "connector"));
        event.blacklistWildcard(new ResourceLocation("immersiveengineering", "metal_multiblock"));

        //IC2
        event.blacklistWildcard(new ResourceLocation("ic2", "te"));

        event.blacklistMod("storagedrawers");//without packing tape, you're gonna have a bad time
        event.blacklistMod("colossalchests");

        /**
         * ADD START
         */
        event.blacklist(MekanismBlocks.MachineBlock3, 9); // Isotopic Centrifuge
        /**
         * ADD END
         */

        BoxBlacklistParser.load();
    }

    @SubscribeEvent
    public void chunkSave(ChunkDataEvent.Save event) {
        if (!event.getWorld().isRemote) {
            NBTTagCompound nbtTags = event.getData();

            nbtTags.setInteger("MekanismWorldGen", baseWorldGenVersion);
            nbtTags.setInteger("MekanismUserWorldGen", MekanismConfig.current().general.userWorldGenVersion.val());
        }
    }

    @SubscribeEvent
    public synchronized void onChunkDataLoad(ChunkDataEvent.Load event) {
        if (!event.getWorld().isRemote) {
            if (MekanismConfig.current().general.enableWorldRegeneration.val()) {
                NBTTagCompound loadData = event.getData();
                if (loadData.getInteger("MekanismWorldGen") == baseWorldGenVersion &&
                        loadData.getInteger("MekanismUserWorldGen") == MekanismConfig.current().general.userWorldGenVersion.val()) {
                    return;
                }
                ChunkPos coordPair = event.getChunk().getPos();
                worldTickHandler.addRegenChunk(event.getWorld().provider.getDimension(), coordPair);
            }
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(Mekanism.MODID)) {
            proxy.loadConfiguration();
            proxy.onConfigSync(false);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        playerState.init(event.getWorld());
        if (!event.getWorld().isRemote) {
            QIOStorageManager.load(event.getWorld());
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            QIOStorageManager.flush();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        // Make sure the global fake player drops its reference to the World
        // when the server shuts down
        if (event.getWorld() instanceof WorldServer) {
            MekFakePlayer.releaseInstance(event.getWorld());
        }
    }

    @SubscribeEvent
    public void onDrawScreenPre(RenderGameOverlayEvent.Post event) {
        MekaSuitEnergyLevel.onDrawScreenPre(event);
        MekanismHUD.onDrawScreenPre(event);
        MekanismStatusOverlay.INSTANCE.render(event);
    }

}
