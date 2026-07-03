package mekanism.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.Pos3D;
import mekanism.client.SparkleAnimation.INodeChecker;
import mekanism.client.entity.ParticleLaser;
import mekanism.client.gui.GuiModuleTweaker;
import mekanism.client.gui.robit.*;
import mekanism.client.render.*;
import mekanism.client.render.bloom.MekaSuitBloomRenderer;
import mekanism.client.render.entity.RenderBalloon;
import mekanism.client.render.entity.RenderFlame;
import mekanism.client.render.entity.RenderObsidianTNTPrimed;
import mekanism.client.render.entity.RenderRobit;
import mekanism.client.render.entity.baby.RenderBabyCreeper;
import mekanism.client.render.entity.baby.RenderBabyEnderman;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.client.render.item.RenderEnergyCubeItem;
import mekanism.client.render.item.basicblock.RenderBasicBlockItem;
import mekanism.client.render.item.gear.*;
import mekanism.client.render.item.machine.RenderMachineItem;
import mekanism.client.render.layer.MekaSuitModuleElytra;
import mekanism.client.render.layer.MekanismElytraLayer;
import mekanism.client.render.obj.MekanismOBJLoader;
import mekanism.client.render.tileentity.*;
import mekanism.client.render.transmitter.*;
import mekanism.client.sound.SoundHandler;
import mekanism.common.CommonProxy;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.MekanismItems;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.base.ITierItem;
import mekanism.common.block.BlockMachine;
import mekanism.common.block.BlockPlasticFence.PlasticFenceStateMapper;
import mekanism.common.block.states.BlockStateBasic.BasicBlockStateMapper;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.block.states.BlockStateCardboardBox.CardboardBoxStateMapper;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineBlockStateMapper;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.block.states.BlockStateOre.EnumOreType;
import mekanism.common.block.states.BlockStatePlastic.PlasticBlockStateMapper;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterStateMapper;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.entity.EntityBalloon;
import mekanism.common.entity.EntityFlame;
import mekanism.common.entity.EntityObsidianTNT;
import mekanism.common.entity.EntityRobit;
import mekanism.common.entity.baby.*;
import mekanism.common.inventory.InventoryPersonalChest;
import mekanism.common.item.*;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.recipe.machines.*;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.*;
import mekanism.common.tile.factory.*;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.tile.laser.TileEntityLaserTractorBeam;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.multiblock.*;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mekanism.common.tile.prefab.TileEntityDoubleElectricMachine;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import mekanism.common.tile.transmitter.*;
import mekanism.common.util.TextComponentGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderSkeleton;
import net.minecraft.client.renderer.entity.RenderStray;
import net.minecraft.client.renderer.entity.RenderWitherSkeleton;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static mekanism.common.block.states.BlockStatePlastic.colorProperty;

/**
 * Client proxy for the Mekanism mod.
 *
 * @author AidanBrady
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    private static final IStateMapper machineMapper = new MachineBlockStateMapper();
    private static final IStateMapper basicMapper = new BasicBlockStateMapper();
    private static final IStateMapper plasticMapper = new PlasticBlockStateMapper();
    private static final IStateMapper fenceMapper = new PlasticFenceStateMapper();
    private static final IStateMapper boxMapper = new CardboardBoxStateMapper();
    private static final IStateMapper transmitterMapper = new TransmitterStateMapper();
    public static Map<String, ModelResourceLocation> machineResources = new Object2ObjectOpenHashMap<>();
    public static Map<String, ModelResourceLocation> basicResources = new Object2ObjectOpenHashMap<>();
    public static Map<String, ModelResourceLocation> transmitterResources = new Object2ObjectOpenHashMap<>();

    @Override
    public void loadConfiguration() {
        super.loadConfiguration();
        MekanismConfig.current().client.load(Mekanism.configuration);
        if (Mekanism.configuration.hasChanged()) {
            Mekanism.configuration.save();
        }
    }

    @Override
    public void registerTESRs() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBasicFactory.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAdvancedFactory.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityEliteFactory.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityUltimateFactory.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBin.class, new RenderBin());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBoilerCasing.class, new RenderThermoelectricBoiler());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBoilerValve.class, new RenderThermoelectricBoiler());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChemicalCrystallizer.class, RenderChemicalCrystallizer.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChemicalDissolutionChamber.class, RenderChemicalDissolutionChamber.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChemicalInjectionChamber.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCombiner.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCrusher.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDigitalMiner.class, new RenderDigitalMiner());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDiversionTransporter.class, new RenderLogisticalTransporter());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDynamicTank.class, new RenderDynamicTank());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDynamicValve.class, new RenderDynamicTank());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityEnergizedSmelter.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityEnergyCube.class, new RenderEnergyCube());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityEnrichmentChamber.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityFluidTank.class, RenderFluidTank.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityFormulaicAssemblicator.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGasTank.class, new RenderGasTank());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLogisticalTransporter.class, new RenderLogisticalTransporter());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMechanicalPipe.class, new RenderMechanicalPipe());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMetallurgicInfuser.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOsmiumCompressor.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPRC.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPersonalChest.class, new RenderPersonalChest());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPrecisionSawmill.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPressurizedTube.class, new RenderPressurizedTube());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPurificationChamber.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityQuantumEntangloporter.class, new RenderQuantumEntangloporter());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityResistiveHeater.class, new RenderResistiveHeater());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRestrictiveTransporter.class, new RenderLogisticalTransporter());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySecurityDesk.class, new RenderSecurityDesk());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySeismicVibrator.class, new RenderSeismicVibrator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySolarNeutronActivator.class, new RenderSolarNeutronActivator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTeleporter.class, new RenderTeleporter());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityThermalEvaporationController.class, new RenderThermalEvaporationController());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityThermodynamicConductor.class, new RenderThermodynamicConductor());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityUniversalCable.class, new RenderUniversalCable());

        /**
         * ADD Start
         */
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityIsotopicCentrifuge.class, RenderIsotopicCentrifuge.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityNutritionalLiquifier.class, RenderNutritionalLiquifier.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRotaryCondensentrator.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChemicalOxidizer.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChemicalInfuser.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityElectrolyticSeparator.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChemicalWasher.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOredictionificator.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOrganicFarm.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAntiprotonicNucleosynthesizer.class, new RenderAntiprotonicNucleosynthesizer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityStamping.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRolling.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBrushed.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTurning.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAlloy.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCellExtractor.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCellSeparator.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRecycler.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAmbientAccumulator.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAmbientAccumulatorEnergy.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHybridStorage.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySPS.class, new RenderConfigurableMachine<>());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySPSCasing.class, new RenderSPS());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySPSPort.class, new RenderSPS());
    }

    @Override
    public void registerItemRenders() {
        registerItemRender(MekanismItems.ElectricBow);
        registerItemRender(MekanismItems.Dust);
        registerItemRender(MekanismItems.Ingot);
        registerItemRender(MekanismItems.Nugget);
        registerItemRender(MekanismItems.EnergyTablet);
        registerItemRender(MekanismItems.SpeedUpgrade);
        registerItemRender(MekanismItems.EnergyUpgrade);
        registerItemRender(MekanismItems.FilterUpgrade);
        registerItemRender(MekanismItems.MufflingUpgrade);
        registerItemRender(MekanismItems.GasUpgrade);
        registerItemRender(MekanismItems.AnchorUpgrade);
        registerItemRender(MekanismItems.StoneGeneratorUpgrade);
        registerItemRender(MekanismItems.ThreadUpgrade);
        registerItemRender(MekanismItems.Robit);
        registerItemRender(MekanismItems.AtomicDisassembler);
        registerItemRender(MekanismItems.EnrichedAlloy);
        registerItemRender(MekanismItems.ReinforcedAlloy);
        registerItemRender(MekanismItems.AtomicAlloy);
        registerItemRender(MekanismItems.ItemProxy);
        registerItemRender(MekanismItems.ControlCircuit);
        registerItemRender(MekanismItems.EnrichedIron);
        registerItemRender(MekanismItems.CompressedCarbon);
        registerItemRender(MekanismItems.CompressedRedstone);
        registerItemRender(MekanismItems.CompressedDiamond);
        registerItemRender(MekanismItems.CompressedObsidian);
        registerItemRender(MekanismItems.PortableTeleporter);
        registerItemRender(MekanismItems.TeleportationCore);
        registerItemRender(MekanismItems.Clump);
        registerItemRender(MekanismItems.DirtyDust);
        registerItemRender(MekanismItems.Configurator);
        registerItemRender(MekanismItems.NetworkReader);
        registerItemRender(MekanismItems.Jetpack);
        registerItemRender(MekanismItems.Dictionary);
        registerItemRender(MekanismItems.GasMask);
        registerItemRender(MekanismItems.ScubaTank);
        registerItemRender(MekanismItems.Balloon);
        registerItemRender(MekanismItems.Shard);
        registerItemRender(MekanismItems.ElectrolyticCore);
        registerItemRender(MekanismItems.Sawdust);
        registerItemRender(MekanismItems.Salt);
        registerItemRender(MekanismItems.Crystal);
        registerItemRender(MekanismItems.FreeRunners);
        registerItemRender(MekanismItems.ArmoredJetpack);
        registerItemRender(MekanismItems.ConfigurationCard);
        registerItemRender(MekanismItems.SeismicReader);
        registerItemRender(MekanismItems.Substrate);
        registerItemRender(MekanismItems.BioFuel);
        registerItemRender(MekanismItems.Flamethrower);
        registerItemRender(MekanismItems.GaugeDropper);
        registerItemRender(MekanismItems.TierInstaller);
        registerItemRender(MekanismItems.OtherDust);
        /**
         * ADD START
         */
        registerItemRender(MekanismItems.Canteen);
        registerItemRender(MekanismItems.CosmicAlloy);
        registerItemRender(MekanismItems.PlutoniumPellet);
        registerItemRender(MekanismItems.AntimatterPellet);
        registerItemRender(MekanismItems.ReprocessedFissileFragment);
        registerItemRender(MekanismItems.YellowCakeUranium);
        registerItemRender(MekanismItems.PoloniumPellet);

        registerItemRender(MekanismItems.CosmicMatter);
        registerItemRender(MekanismItems.Scrap);
        registerItemRender(MekanismItems.ScrapBox);
        registerItemRender(MekanismItems.EmptyCrystals);
        registerItemRender(MekanismItems.FluoriteClump);
        registerItemRender(MekanismItems.ArmoredFreeRunners);
        //     registerItemRender(MekanismItems.MekAsuitHelmet);
        //    registerItemRender(MekanismItems.MekAsuitChestplate);
        //     registerItemRender(MekanismItems.MekAsuitLeggings);
        //      registerItemRender(MekanismItems.MekAsuitBoots);
        registerItemRender(MekanismItems.ModuleBase);

        registerItemRender(MekanismItems.MEKASUIT_HELMET);
        registerItemRender(MekanismItems.MEKASUIT_BODYARMOR);
        registerItemRender(MekanismItems.MEKASUIT_PANTS);
        registerItemRender(MekanismItems.MEKASUIT_BOOTS);


        registerItemRender(MekanismItems.MODULE_ENERGY);
        registerItemRender(MekanismItems.MODULE_MAGNETIC);
        registerItemRender(MekanismItems.MODULE_COLOR_MODULATION);
        registerItemRender(MekanismItems.MODULE_LASER_DISSIPATION);
        registerItemRender(MekanismItems.MODULE_RADIATION_SHIELDING);

        registerItemRender(MekanismItems.MODULE_EXCAVATION_ESCALATION);
        registerItemRender(MekanismItems.MODULE_ATTACK_AMPLIFICATION);
        registerItemRender(MekanismItems.MODULE_FARMING);
        registerItemRender(MekanismItems.MODULE_SHEARING);
        registerItemRender(MekanismItems.MODULE_SILK_TOUCH);
        registerItemRender(MekanismItems.MODULE_FORTUNE);
        registerItemRender(MekanismItems.MODULE_BLASTING);
        registerItemRender(MekanismItems.MODULE_VEIN_MINING);
        registerItemRender(MekanismItems.MODULE_TELEPORTATION);

        if (Mekanism.hooks.FarmersDelightLegacyLoaded){
            registerItemRender(MekanismItems.MODULE_LASER_CUTTER);
        }

        registerItemRender(MekanismItems.MODULE_ELECTROLYTIC_BREATHING);
        registerItemRender(MekanismItems.MODULE_INHALATION_PURIFICATION);
        registerItemRender(MekanismItems.MODULE_VISION_ENHANCEMENT);
        registerItemRender(MekanismItems.MODULE_NUTRITIONAL_INJECTION);
        registerItemRender(MekanismItems.MODULE_DOSIMETER);
        registerItemRender(MekanismItems.MODULE_GEIGER);
        registerItemRender(MekanismItems.MODULE_JETPACK);
        registerItemRender(MekanismItems.MODULE_CHARGE_DISTRIBUTION);
        registerItemRender(MekanismItems.MODULE_GRAVITATIONAL_MODULATING);

        registerItemRender(MekanismItems.MODULE_GYROSCOPIC_STABILIZATION);
        registerItemRender(MekanismItems.MODULE_HYDROSTATIC_REPULSOR);
        registerItemRender(MekanismItems.MODULE_LOCOMOTIVE_BOOSTING);
        registerItemRender(MekanismItems.MODULE_MOTORIZED_SERVO);
        registerItemRender(MekanismItems.MODULE_HYDRAULIC_PROPULSION);
        registerItemRender(MekanismItems.MODULE_MAGNETIC_ATTRACTION);
        registerItemRender(MekanismItems.MODULE_FROST_WALKER);
        if (Mekanism.hooks.MekanismMixinHelp) {
            registerItemRender(MekanismItems.MODULE_ELYTRA);
            registerItemRender(MekanismItems.HDPE_REINFORCED_ELYTRA);
        }

        registerItemRender(MekanismItems.MODULE_HEALTH_REGENERATION);
        registerItemRender(MekanismItems.MEKA_TOOL);
        registerItemRender(MekanismItems.MEKA_FISHING_ROD);
        registerItemRender(MekanismItems.HDPE_PELLET);
        registerItemRender(MekanismItems.HDPE_ROD);
        registerItemRender(MekanismItems.HDPE_SHEET);
        registerItemRender(MekanismItems.HDPE_STICK);

        registerItemRender(MekanismItems.CARDBOARD_HELMET);
        registerItemRender(MekanismItems.CARDBOARD_CHESTPLATE);
        registerItemRender(MekanismItems.CARDBOARD_LEGGINGS);
        registerItemRender(MekanismItems.CARDBOARD_BOOTS);

        registerItemRender(MekanismItems.DOSIMETER);
        registerItemRender(MekanismItems.GEIGER_COUNTER);

        registerItemRender(MekanismItems.HAZMAT_MASK);
        registerItemRender(MekanismItems.HAZMAT_GOWN);
        registerItemRender(MekanismItems.HAZMAT_PANTS);
        registerItemRender(MekanismItems.HAZMAT_BOOTS);

        registerItemRender(MekanismItems.MODULE_FISHING_COLLECTING);
        registerItemRender(MekanismItems.MODULE_FISHING_SPEED);
        registerItemRender(MekanismItems.MODULE_FISHING_INTELLIGENT);
        registerItemRender(MekanismItems.MODULE_FISHING_MULTIPLE);

        /**
         * ADD END
         */
        ModelBakery.registerItemVariants(MekanismItems.WalkieTalkie, ItemWalkieTalkie.OFF_MODEL);

        for (int i = 1; i <= 9; i++) {
            ModelBakery.registerItemVariants(MekanismItems.WalkieTalkie, ItemWalkieTalkie.getModel(i));
        }

        ModelBakery.registerItemVariants(MekanismItems.CraftingFormula, ItemCraftingFormula.MODEL, ItemCraftingFormula.INVALID_MODEL, ItemCraftingFormula.ENCODED_MODEL);

        MekanismItems.Jetpack.setTileEntityItemStackRenderer(new RenderJetpack());
        MekanismItems.ArmoredJetpack.setTileEntityItemStackRenderer(new RenderArmoredJetpack());
        MekanismItems.GasMask.setTileEntityItemStackRenderer(new RenderGasMask());
        MekanismItems.Robit.setTileEntityItemStackRenderer(new RenderItemRobit());
        MekanismItems.ScubaTank.setTileEntityItemStackRenderer(new RenderScubaTank());
        MekanismItems.FreeRunners.setTileEntityItemStackRenderer(new RenderFreeRunners());
        MekanismItems.AtomicDisassembler.setTileEntityItemStackRenderer(new RenderAtomicDisassembler());
        MekanismItems.Flamethrower.setTileEntityItemStackRenderer(new RenderFlameThrower());
        Item.getItemFromBlock(MekanismBlocks.EnergyCube).setTileEntityItemStackRenderer(new RenderEnergyCubeItem());
        Item.getItemFromBlock(MekanismBlocks.MachineBlock).setTileEntityItemStackRenderer(new RenderMachineItem());
        Item.getItemFromBlock(MekanismBlocks.MachineBlock2).setTileEntityItemStackRenderer(new RenderMachineItem());
        Item.getItemFromBlock(MekanismBlocks.MachineBlock3).setTileEntityItemStackRenderer(new RenderMachineItem());
        Item.getItemFromBlock(MekanismBlocks.BasicBlock2).setTileEntityItemStackRenderer(new RenderBasicBlockItem());
        Item.getItemFromBlock(MekanismBlocks.BasicBlock3).setTileEntityItemStackRenderer(new RenderBasicBlockItem());
        Item.getItemFromBlock(MekanismBlocks.MachineBlock4).setTileEntityItemStackRenderer(new RenderMachineItem());
        MekanismItems.ArmoredFreeRunners.setTileEntityItemStackRenderer(new RenderArmoredFreeRunners());
        MekanismItems.MEKA_TOOL.setTileEntityItemStackRenderer(new RenderMekaTool());
        MekanismItems.MEKA_FISHING_ROD.setTileEntityItemStackRenderer(new RenderMekaFishingRod());

    }

    private ModelResourceLocation getInventoryMRL(String type) {
        return new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, type), "inventory");
    }

    @Override
    public void registerBlockRenders() {
        ModelLoader.setCustomStateMapper(MekanismBlocks.MachineBlock, machineMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.MachineBlock2, machineMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.MachineBlock3, machineMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.BasicBlock, basicMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.BasicBlock2, basicMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.PlasticBlock, plasticMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.SlickPlasticBlock, plasticMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.GlowPlasticBlock, plasticMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.ReinforcedPlasticBlock, plasticMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.RoadPlasticBlock, plasticMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.PlasticFence, fenceMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.CardboardBox, boxMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.Transmitter, transmitterMapper);

        ModelLoader.setCustomStateMapper(MekanismBlocks.BasicBlock3, basicMapper);
        ModelLoader.setCustomStateMapper(MekanismBlocks.MachineBlock4, machineMapper);

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.ObsidianTNT), 0, getInventoryMRL("ObsidianTNT"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.SaltBlock), 0, getInventoryMRL("SaltBlock"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.CardboardBox), 0, new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "CardboardBox"), "storage=false"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.CardboardBox), 1, new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "CardboardBox"), "storage=true"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.EnergyCube), 0, getInventoryMRL("EnergyCube"));

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock), 4, getInventoryMRL("digital_miner"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock), 13, getInventoryMRL("personal_chest"));

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock2), 6, getInventoryMRL("chemical_dissolution_chamber"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock2), 8, getInventoryMRL("chemical_crystallizer"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock2), 9, getInventoryMRL("seismic_vibrator"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock2), 11, getInventoryMRL("fluid_tank"));

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), 0, getInventoryMRL("quantum_entangloporter"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), 1, getInventoryMRL("solar_neutron_activator"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), 4, getInventoryMRL("resistive_heater"));

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.BasicBlock2), 9, getInventoryMRL("security_desk"));

        for (int i = 0; i < EnumColor.DYES.length; i++) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.GlowPanel), i, getInventoryMRL("glowpanel"));
        }

        /**
         * ADD START
         * */
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), 9, getInventoryMRL("isotopic_centrifuge"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), 10, getInventoryMRL("nutritional_liquifier"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), 13, getInventoryMRL("antiprotonic_nucleosynthesizer"));

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.SuperFumo), 0, getInventoryMRL("SuperFumo"));
        /**
         * ADD END
         * */

        for (MachineType type : MachineType.values()) {
            if (!type.isValidMachine()) {
                continue;
            }

            List<ModelResourceLocation> modelsToAdd = new ArrayList<>();
            String resource = "mekanism:" + type.getName();
            RecipeType recipePointer = null;

            if (type.isFactory()) {
                recipePointer = RecipeType.values()[0];
                resource = "mekanism:" + type.getName() + "_" + recipePointer.getName();
            }

            while (true) {
                if (machineResources.get(resource) == null) {
                    List<String> entries = new ArrayList<>();
                    if (type.hasActiveTexture()) {
                        entries.add("active=false");
                    }
                    if (type.hasRotations()) {
                        entries.add("facing=north");
                    }

                    String properties = getProperties(entries);
                    ModelResourceLocation model = new ModelResourceLocation(resource, properties);
                    machineResources.put(resource, model);
                    modelsToAdd.add(model);

                    if (type.isFactory()) {
                        if (recipePointer.ordinal() < RecipeType.values().length - 1) {
                            recipePointer = RecipeType.values()[recipePointer.ordinal() + 1];
                            resource = "mekanism:" + type.getName() + "_" + recipePointer.getName();
                            continue;
                        }
                    }
                }
                break;
            }

            ModelLoader.registerItemVariants(Item.getItemFromBlock(type.typeBlock.getBlock()), modelsToAdd.toArray(new ModelResourceLocation[]{}));
        }

        for (BasicBlockType type : BasicBlockType.values()) {
            List<ModelResourceLocation> modelsToAdd = new ArrayList<>();
            String resource = "mekanism:" + type.getName();
            BaseTier tierPointer = null;

            if (type.tiers) {
                tierPointer = BaseTier.values()[0];
                resource = "mekanism:" + type.getName() + "_" + tierPointer.getName();
            }

            while (true) {
                if (basicResources.get(resource) == null) {
                    List<String> entries = new ArrayList<>();
                    if (type.hasActiveTexture()) {
                        entries.add("active=false");
                    }
                    if (type.hasRotations() || type == BasicBlockType.THERMAL_EVAPORATION_CONTROLLER) {
                        entries.add("facing=north");
                    }
                    if (type == BasicBlockType.BOILER_VALVE) {
                        entries.add("mode=input");
                    }

                    //TODO: Is this check against bin's needed
                    String properties = type == BasicBlockType.BIN ? "inventory" : getProperties(entries);
                    ModelResourceLocation model = new ModelResourceLocation(resource, properties);
                    basicResources.put(resource, model);
                    modelsToAdd.add(model);

                    if (type.tiers) {
                        if (tierPointer.ordinal() < BaseTier.values().length - 1) {
                            tierPointer = BaseTier.values()[tierPointer.ordinal() + 1];
                            if (type == BasicBlockType.BIN || tierPointer.isObtainable()) {
                                resource = "mekanism:" + type.getName() + "_" + tierPointer.getName();
                                continue;
                            }
                        }
                    }
                }
                break;
            }
            ModelLoader.registerItemVariants(Item.getItemFromBlock(type.blockType.getBlock()), modelsToAdd.toArray(new ModelResourceLocation[]{}));
        }

        for (TransmitterType type : TransmitterType.values()) {
            List<ModelResourceLocation> modelsToAdd = new ArrayList<>();
            String resource = "mekanism:" + type.getName();
            BaseTier tierPointer = null;

            if (type.hasTiers()) {
                tierPointer = BaseTier.values()[0];
                resource = "mekanism:" + type.getName() + "_" + tierPointer.getName();
            }

            while (true) {
                if (transmitterResources.get(resource) == null) {
                    String properties = "inventory";
                    ModelResourceLocation model = new ModelResourceLocation(resource, properties);
                    transmitterResources.put(resource, model);
                    modelsToAdd.add(model);
                    if (type.hasTiers()) {
                        if (tierPointer.ordinal() < BaseTier.values().length - 1) {
                            tierPointer = BaseTier.values()[tierPointer.ordinal() + 1];
                            if (tierPointer.isObtainable()) {
                                resource = "mekanism:" + type.getName() + "_" + tierPointer.getName();
                                continue;
                            }
                        }
                    }
                }
                break;
            }

            ModelLoader.registerItemVariants(Item.getItemFromBlock(MekanismBlocks.Transmitter), modelsToAdd.toArray(new ModelResourceLocation[]{}));
        }

        for (EnumColor color : EnumColor.DYES) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.PlasticBlock), color.getMetaValue(), new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "plastic_block"), "type=plastic"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.SlickPlasticBlock), color.getMetaValue(), new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "plastic_block"), "type=slick"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.GlowPlasticBlock), color.getMetaValue(), new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "plastic_block"), "type=glow"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.ReinforcedPlasticBlock), color.getMetaValue(), new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "plastic_block"), "type=reinforced"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.RoadPlasticBlock), color.getMetaValue(), new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "plastic_block"), "type=road"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.PlasticFence), color.getMetaValue(), getInventoryMRL("PlasticFence"));
        }

        for (EnumOreType ore : EnumOreType.values()) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MekanismBlocks.OreBlock), ore.ordinal(), new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "OreBlock"), "type=" + ore.getName()));
        }

        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.GasTank), stack -> {
            BaseTier baseTier = stack.getItem() instanceof ITierItem tierItem ? tierItem.getBaseTier(stack) : BaseTier.BASIC;
            GasTankTier tier = GasTankTier.values()[baseTier.ordinal()];
            ResourceLocation baseLocation = new ResourceLocation(Mekanism.MODID, "GasTank");
            return new ModelResourceLocation(baseLocation, "facing=north,tier=" + tier);
        });

        ItemMeshDefinition machineMesher = stack -> {
            MachineType type = MachineType.get(stack);
            if (type != null) {
                String resource = "mekanism:" + type.getName();
                if (type.isFactory()) {
                    RecipeType recipe = ((ItemBlockMachine) stack.getItem()).getRecipeTypeOrNull(stack);
                    if (recipe != null) {
                        resource = "mekanism:" + type.getName() + "_" + recipe.getName();
                    }
                }
                return machineResources.get(resource);
            }
            return null;
        };

        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.MachineBlock), machineMesher);
        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.MachineBlock2), machineMesher);
        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), machineMesher);
        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.MachineBlock4), machineMesher);
        ItemMeshDefinition basicMesher = stack -> {
            BasicBlockType type = BasicBlockType.get(stack);
            if (type != null) {
                String resource = "mekanism:" + type.getName();
                if (type.tiers) {
                    BaseTier tier = ((ItemBlockBasic) stack.getItem()).getBaseTier(stack);
                    resource = "mekanism:" + type.getName() + "_" + tier.getName();
                }
                return basicResources.get(resource);
            }
            return null;
        };

        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.BasicBlock), basicMesher);
        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.BasicBlock2), basicMesher);
        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.BasicBlock3), basicMesher);

        ItemMeshDefinition transmitterMesher = stack -> {
            TransmitterType type = TransmitterType.get(stack.getItemDamage());

            if (type != null) {
                String resource = "mekanism:" + type.getName();
                if (type.hasTiers()) {
                    BaseTier tier = ((ItemBlockTransmitter) stack.getItem()).getBaseTier(stack);
                    resource = "mekanism:" + type.getName() + "_" + tier.getName();
                }
                return transmitterResources.get(resource);
            }
            return null;
        };

        ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(MekanismBlocks.Transmitter), transmitterMesher);

        //Walkie Talkie dynamic texture
        ModelLoader.setCustomMeshDefinition(MekanismItems.WalkieTalkie, stack -> {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemWalkieTalkie item) {
                if (item.getOn(stack)) {
                    return ItemWalkieTalkie.CHANNEL_MODELS.get(item.getChannel(stack));
                }
            }
            return ItemWalkieTalkie.OFF_MODEL;
        });

        //Crafting Formula dynamic texture
        ModelLoader.setCustomMeshDefinition(MekanismItems.CraftingFormula, stack -> {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemCraftingFormula item) {
                if (item.getInventory(stack) == null) {
                    return ItemCraftingFormula.MODEL;
                }
                return item.isInvalid(stack) ? ItemCraftingFormula.INVALID_MODEL : ItemCraftingFormula.ENCODED_MODEL;
            }
            return ItemCraftingFormula.MODEL;
        });

        OBJLoader.INSTANCE.addDomain(Mekanism.MODID);
    }

    public void registerItemRender(Item item) {
        MekanismRenderer.registerItemRender(Mekanism.MODID, item);
    }

    private String getProperties(List<String> entries) {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            properties.append(entries.get(i));
            if (i < entries.size() - 1) {
                properties.append(",");
            }
        }
        return properties.toString();
    }

    private GuiScreen getClientItemGui(EntityPlayer player, BlockPos pos) {
        int currentItem = pos.getX();
        int handOrdinal = pos.getY();
        if (currentItem < 0 || currentItem >= player.inventory.mainInventory.size() || handOrdinal < 0 || handOrdinal >= EnumHand.values().length) {
            //If it is out of bounds don't do anything
            return null;
        }
        EnumHand hand = EnumHand.values()[handOrdinal];
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) {
            return null;
        }
        int guiID = pos.getZ();
        switch (guiID) {
            case 0 -> {
                if (stack.getItem() instanceof ItemDictionary) {
                    return new mekanism.client.gui.item.GuiDictionary(player.inventory, hand, stack);
                }
            }
            case 14 -> {
                if (stack.getItem() instanceof ItemPortableTeleporter) {
                    return new mekanism.client.gui.item.GuiPortableTeleporter(player, hand, stack);
                }
            }
            case 19 -> {
                if (MachineType.get(stack) == MachineType.PERSONAL_CHEST) {
                    //Ensure the item didn't change. From testing even if it did things still seemed to work properly but better safe than sorry
                    return new mekanism.client.gui.item.GuiPersonalStorageItem(player.inventory, new InventoryPersonalChest(stack, hand));
                }
            }
            case 38 -> {
                if (stack.getItem() instanceof ItemSeismicReader) {
                    return new mekanism.client.gui.item.GuiSeismicReader(player.inventory, hand, stack);
                }
            }
        }
        return null;
    }

    private GuiScreen getClientEntityGui(EntityPlayer player, World world, BlockPos pos) {
        int entityID = pos.getX();
        Entity entity = world.getEntityByID(entityID);
        if (entity == null) {
            return null;
        }
        int guiID = pos.getY();
        switch (guiID) {
            case 21 -> {
                if (entity instanceof EntityRobit) {
                    return new GuiRobitMain(player.inventory, (EntityRobit) entity);
                }
            }
            case 22 -> {
                if (entity instanceof EntityRobit) {
                    return new GuiRobitCrafting(player.inventory, (EntityRobit) entity);
                }
            }
            case 23 -> {
                if (entity instanceof EntityRobit) {
                    return new GuiRobitInventory(player.inventory, (EntityRobit) entity);
                }
            }
            case 24 -> {
                if (entity instanceof EntityRobit) {
                    return new GuiRobitSmelting(player.inventory, (EntityRobit) entity);
                }
            }
            case 25 -> {
                if (entity instanceof EntityRobit) {
                    return new GuiRobitRepair(player.inventory, (EntityRobit) entity);
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GuiScreen getClientGui(int ID, EntityPlayer player, World world, BlockPos pos) {
        //TODO: Replace magic numbers here and in sub methods with static lookup ints
        if (ID == 0) {
            return getClientItemGui(player, pos);
        } else if (ID == 1) {
            return getClientEntityGui(player, world, pos);
        }
        TileEntity tileEntity = world.getTileEntity(pos);
        return switch (ID) {
            //0, 1 USED BEFORE SWITCH
            case 2 -> new mekanism.client.gui.machine.GuiDigitalMiner(player.inventory, (TileEntityDigitalMiner) tileEntity);
            case 3 ->
                    new mekanism.client.gui.machine.GuiEnrichmentChamber(player.inventory, (TileEntityElectricMachine<EnrichmentRecipe>) tileEntity);
            case 4 ->
                    new mekanism.client.gui.machine.GuiOsmiumCompressor(player.inventory, (TileEntityAdvancedElectricMachine<OsmiumCompressorRecipe>) tileEntity);
            case 5 -> new mekanism.client.gui.machine.GuiCombiner(player.inventory, (TileEntityDoubleElectricMachine<CombinerRecipe>) tileEntity);
            case 6 -> new mekanism.client.gui.machine.GuiCrusher(player.inventory, (TileEntityElectricMachine<CrusherRecipe>) tileEntity);
            case 7 -> new mekanism.client.gui.machine.GuiRotaryCondensentrator(player.inventory, (TileEntityRotaryCondensentrator) tileEntity);
            case 8 -> new mekanism.client.gui.GuiEnergyCube(player.inventory, (TileEntityEnergyCube) tileEntity);
            case 10 -> new mekanism.client.gui.GuiGasTank(player.inventory, (TileEntityGasTank) tileEntity);
            case 11 -> new mekanism.client.gui.machine.GuiFactory(player.inventory, (TileEntityFactory) tileEntity);
            case 12 -> new mekanism.client.gui.machine.GuiMetallurgicInfuser(player.inventory, (TileEntityMetallurgicInfuser) tileEntity);
            case 13 -> new mekanism.client.gui.GuiTeleporter(player.inventory, (TileEntityTeleporter) tileEntity);
            //EMPTY 14
            case 15 ->
                    new mekanism.client.gui.machine.GuiPurificationChamber(player.inventory, (TileEntityAdvancedElectricMachine<PurificationRecipe>) tileEntity);
            case 16 ->
                    new mekanism.client.gui.machine.GuiEnergizedSmelter(player.inventory, (TileEntityElectricMachine<SmeltingRecipe>) tileEntity);
            case 17 -> new mekanism.client.gui.machine.GuiElectricPump(player.inventory, (TileEntityElectricPump) tileEntity);
            case 18 -> new mekanism.client.gui.GuiDynamicTank(player.inventory, (TileEntityDynamicTank) tileEntity);
            case 19 -> new mekanism.client.gui.GuiPersonalStorageTile(player.inventory, (TileEntityPersonalChest) tileEntity);
            //EMPTY 20, 21, 22, 23, 24, 25
            case 29 -> new mekanism.client.gui.machine.GuiChemicalOxidizer(player.inventory, (TileEntityChemicalOxidizer) tileEntity);
            case 30 -> new mekanism.client.gui.machine.GuiChemicalInfuser(player.inventory, (TileEntityChemicalInfuser) tileEntity);
            case 31 ->
                    new mekanism.client.gui.machine.GuiChemicalInjectionChamber(player.inventory, (TileEntityAdvancedElectricMachine<InjectionRecipe>) tileEntity);
            case 32 -> new mekanism.client.gui.machine.GuiElectrolyticSeparator(player.inventory, (TileEntityElectrolyticSeparator) tileEntity);
            case 33 ->
                    new mekanism.client.gui.GuiThermalEvaporationController(player.inventory, (TileEntityThermalEvaporationController) tileEntity);
            case 34 -> new mekanism.client.gui.machine.GuiPrecisionSawmill(player.inventory, (TileEntityPrecisionSawmill) tileEntity);
            case 35 ->
                    new mekanism.client.gui.machine.GuiChemicalDissolutionChamber(player.inventory, (TileEntityChemicalDissolutionChamber) tileEntity);
            case 36 -> new mekanism.client.gui.machine.GuiChemicalWasher(player.inventory, (TileEntityChemicalWasher) tileEntity);
            case 37 -> new mekanism.client.gui.machine.GuiChemicalCrystallizer(player.inventory, (TileEntityChemicalCrystallizer) tileEntity);
            //EMPTY 38
            case 39 -> new mekanism.client.gui.machine.GuiSeismicVibrator(player.inventory, (TileEntitySeismicVibrator) tileEntity);
            case 40 -> new mekanism.client.gui.machine.GuiPRC(player.inventory, (TileEntityPRC) tileEntity);
            case 41 -> new mekanism.client.gui.GuiFluidTank(player.inventory, (TileEntityFluidTank) tileEntity);
            case 42 -> new mekanism.client.gui.machine.GuiFluidicPlenisher(player.inventory, (TileEntityFluidicPlenisher) tileEntity);
            case 44 -> new mekanism.client.gui.GuiLaserAmplifier(player.inventory, (TileEntityLaserAmplifier) tileEntity);
            case 45 -> new mekanism.client.gui.GuiLaserTractorBeam(player.inventory, (TileEntityLaserTractorBeam) tileEntity);
            case 46 -> new mekanism.client.gui.GuiQuantumEntangloporter(player.inventory, (TileEntityQuantumEntangloporter) tileEntity);
            case 47 -> new mekanism.client.gui.machine.GuiSolarNeutronActivator(player.inventory, (TileEntitySolarNeutronActivator) tileEntity);
            case 48 -> new mekanism.client.gui.machine.GuiAmbientAccumulator(player.inventory, (TileEntityAmbientAccumulator) tileEntity);
            case 49 -> new mekanism.client.gui.GuiInductionMatrix(player.inventory, (TileEntityInductionCasing) tileEntity);
            case 50 -> new mekanism.client.gui.GuiMatrixStats(player.inventory, (TileEntityInductionCasing) tileEntity);
            case 52 -> new mekanism.client.gui.machine.GuiOredictionificator(player.inventory, (TileEntityOredictionificator) tileEntity);
            case 53 -> new mekanism.client.gui.machine.GuiResistiveHeater(player.inventory, (TileEntityResistiveHeater) tileEntity);
            case 54 -> new mekanism.client.gui.GuiThermoelectricBoiler(player.inventory, (TileEntityBoilerCasing) tileEntity);
            case 55 -> new mekanism.client.gui.GuiBoilerStats(player.inventory, (TileEntityBoilerCasing) tileEntity);
            case 56 -> new mekanism.client.gui.machine.GuiFormulaicAssemblicator(player.inventory, (TileEntityFormulaicAssemblicator) tileEntity);
            case 57 -> new mekanism.client.gui.GuiSecurityDesk(player.inventory, (TileEntitySecurityDesk) tileEntity);
            case 58 -> new mekanism.client.gui.machine.GuiFuelwoodHeater(player.inventory, (TileEntityFuelwoodHeater) tileEntity);
            case 59 -> new mekanism.client.gui.GuiLogisticalSorter(player.inventory, (TileEntityLogisticalSorter) tileEntity);
            case 60 -> new mekanism.client.gui.machine.GuiIsotopicCentrifuge(player.inventory, (TileEntityIsotopicCentrifuge) tileEntity);
            case 61 -> new mekanism.client.gui.machine.GuiNutritionalLiquifier(player.inventory, (TileEntityNutritionalLiquifier) tileEntity);
            case 62 -> new mekanism.client.gui.machine.GuiOrganicFarm(player.inventory, (TileEntityOrganicFarm) tileEntity);
            case 63 ->
                    new mekanism.client.gui.machine.GuiAntiprotonicNucleosynthesizer(player.inventory, (TileEntityAntiprotonicNucleosynthesizer) tileEntity);
            case 64 -> new mekanism.client.gui.machine.GuiStamping(player.inventory, (TileEntityElectricMachine<StampingRecipe>) tileEntity);
            case 65 -> new mekanism.client.gui.machine.GuiRolling(player.inventory, (TileEntityElectricMachine<RollingRecipe>) tileEntity);
            case 66 -> new mekanism.client.gui.machine.GuiBrushed(player.inventory, (TileEntityElectricMachine<BrushedRecipe>) tileEntity);
            case 67 -> new mekanism.client.gui.machine.GuiTurning(player.inventory, (TileEntityElectricMachine<TurningRecipe>) tileEntity);
            case 68 -> new mekanism.client.gui.machine.GuiAlloy(player.inventory, (TileEntityDoubleElectricMachine<AlloyRecipe>) tileEntity);
           /*
            case 69:
                return new GuiCellCultivate(player.inventory, (TileEntityCellCultivate) tileEntity);
            */
            case 70 -> new mekanism.client.gui.machine.GuiCellExtractor(player.inventory, (TileEntityCellExtractor) tileEntity);
            case 71 -> new mekanism.client.gui.machine.GuiCellSeparator(player.inventory, (TileEntityCellSeparator) tileEntity);
            case 72 -> new mekanism.client.gui.machine.GuiRecycler(player.inventory, (TileEntityRecycler) tileEntity);
            case 73 ->
                    new mekanism.client.gui.machine.GuiAmbientAccumulatorEnergy(player.inventory, (TileEntityAmbientAccumulatorEnergy) tileEntity);
            case 74 -> new mekanism.client.gui.GuiHybridStorage(player.inventory, (TileEntityHybridStorage) tileEntity);
            case 75 -> new mekanism.client.gui.GuiModificationStation(player.inventory, (TileEntityModificationStation) tileEntity);
            case 76 -> new mekanism.client.gui.GuiSPS(player.inventory, (TileEntitySPS) tileEntity);
            case 77 -> new GuiModuleTweaker(player.inventory);
            case 78 -> new mekanism.client.gui.GuiSPSMultiblock(player.inventory, (TileEntitySPSCasing) tileEntity);
            case 79 -> new mekanism.client.gui.GuiDimensionalStabilizer(player.inventory, (TileEntityDimensionalStabilizer) tileEntity);
            default -> null;
        };
    }

    @Override
    public void addHitEffects(Coord4D coord, RayTraceResult mop) {
        if (Minecraft.getMinecraft().world != null) {
            Minecraft.getMinecraft().effectRenderer.addBlockHitEffects(coord.getPos(), mop);
        }
    }

    private void doSparkle(TileEntity tileEntity, SparkleAnimation anim) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        // If player is within 16 blocks (256 = 16^2), show the status message/sparkles
        if (tileEntity.getPos().distanceSq(player.getPosition()) <= 256) {
            if (MekanismConfig.current().client.enableMultiblockFormationParticles.val()) {
                anim.run();
            } else {
                player.sendStatusMessage(new TextComponentGroup(TextFormatting.BLUE).translation("chat.mek.multiblockformed"), true);
            }
        }
    }

    @Override
    public void doMultiblockSparkle(TileEntity tileEntity, BlockPos renderLoc, int length, int width, int height, INodeChecker checker) {
        doSparkle(tileEntity, new SparkleAnimation(tileEntity, renderLoc, length, width, height, checker));
    }

    @Override
    public void doMultiblockSparkle(TileEntity tileEntity, BlockPos corner1, BlockPos corner2, INodeChecker checker) {
        doSparkle(tileEntity, new SparkleAnimation(tileEntity, corner1, corner2, checker));
    }

    @Override
    public void init() {
        super.init();

        Minecraft.getMinecraft().getBlockColors().registerBlockColorHandler((state, worldIn, pos, tintIndex) -> {
            BlockMachine machine = (BlockMachine) state.getBlock();
            if (state.getValue(machine.getMachineBlock().getProperty()) == MachineType.FLUID_TANK) {
                EnumColor color = state.getValue(BlockStateMachine.tierProperty).getColor();
                return (int) (color.getColor(0) * 255) << 16 | (int) (color.getColor(1) * 255) << 8 | (int) (color.getColor(2) * 255);
            }
            return -1;
        }, MekanismBlocks.MachineBlock, MekanismBlocks.MachineBlock2, MekanismBlocks.MachineBlock3, MekanismBlocks.MachineBlock4);
        Minecraft.getMinecraft().getBlockColors().registerBlockColorHandler((state, worldIn, pos, tintIndex) -> {
                    EnumDyeColor color = state.getValue(colorProperty);
                    EnumColor dye = EnumColor.DYES[color.getDyeDamage()];
                    return (int) (dye.getColor(0) * 255) << 16 | (int) (dye.getColor(1) * 255) << 8 | (int) (dye.getColor(2) * 255);
                }, MekanismBlocks.PlasticBlock, MekanismBlocks.GlowPlasticBlock, MekanismBlocks.RoadPlasticBlock, MekanismBlocks.ReinforcedPlasticBlock,
                MekanismBlocks.SlickPlasticBlock, MekanismBlocks.PlasticFence);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack, tintIndex) -> {
            if (MachineType.get(stack) == MachineType.FLUID_TANK) {
                EnumColor color = ((ItemBlockMachine) stack.getItem()).getBaseTier(stack).getColor();
                return (int) (color.getColor(0) * 255) << 16 | (int) (color.getColor(1) * 255) << 8 | (int) (color.getColor(2) * 255);
            }
            return -1;
        }, MekanismBlocks.MachineBlock, MekanismBlocks.MachineBlock2, MekanismBlocks.MachineBlock3, MekanismBlocks.MachineBlock4);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack, tintIndex) -> {
                    EnumDyeColor dyeColor = EnumDyeColor.byDyeDamage(stack.getItemDamage() & 15);
                    EnumColor dye = EnumColor.DYES[dyeColor.getDyeDamage()];
                    return (int) (dye.getColor(0) * 255) << 16 | (int) (dye.getColor(1) * 255) << 8 | (int) (dye.getColor(2) * 255);
                }, MekanismBlocks.PlasticBlock, MekanismBlocks.GlowPlasticBlock, MekanismBlocks.RoadPlasticBlock, MekanismBlocks.ReinforcedPlasticBlock,
                MekanismBlocks.SlickPlasticBlock, MekanismBlocks.PlasticFence);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack, tintIndex) -> {
            EnumColor dye = EnumColor.DYES[stack.getItemDamage()];
            return (int) (dye.getColor(0) * 255) << 16 | (int) (dye.getColor(1) * 255) << 8 | (int) (dye.getColor(2) * 255);
        }, MekanismItems.Balloon);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack, tintIndex) -> {
            if (tintIndex == 0 && stack.getItem() instanceof ItemMekaSuitArmor && stack.getItem() instanceof ItemArmor armor) {
                return armor.getColor(stack);
            }
            return -1;
        }, MekanismItems.MEKASUIT_HELMET, MekanismItems.MEKASUIT_BODYARMOR, MekanismItems.MEKASUIT_PANTS, MekanismItems.MEKASUIT_BOOTS);

        MinecraftForge.EVENT_BUS.register(new ClientConnectionHandler());
        //  MinecraftForge.EVENT_BUS.register(new ClientPlayerTracker());
        MinecraftForge.EVENT_BUS.register(new ClientTickHandler());
        //单检查到Cleanroom的时候，使用Cleanroom的盔甲渲染事件
        if (Mekanism.hooks.CLEANROOM) {
            MinecraftForge.EVENT_BUS.register(new RenderArm());
        } else {
            MinecraftForge.EVENT_BUS.register(new RenderFirstPersonMekaSuitArms());
        }
        if (Mekanism.hooks.Bloom) {
            try {
                new MekaSuitBloomRenderer();
            } catch (LinkageError e) {
                mekanism.common.util.BloomDependencyHelper.disableBloom("MekaSuitBloomRenderer", e);
            }
        }

        MinecraftForge.EVENT_BUS.register(new RenderTickHandler());
        MinecraftForge.EVENT_BUS.register(new MekanismRenderSelection());
        MinecraftForge.EVENT_BUS.register(SoundHandler.class);

        new MekanismKeyHandler();

        HolidayManager.init();
    }


    @Override
    public void onConfigSync(boolean fromPacket) {
        super.onConfigSync(fromPacket);
        if (fromPacket && MekanismConfig.current().general.voiceServerEnabled.val() && MekanismClient.voiceClient != null) {
            MekanismClient.voiceClient.start();
        }
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        IRegistry<ModelResourceLocation, IBakedModel> modelRegistry = event.getModelRegistry();
        ModelResourceLocation ERL = getInventoryMRL("EnergyCube");
        modelRegistry.putObject(ERL, RenderEnergyCubeItem.model = new ItemLayerWrapper(modelRegistry.getObject(ERL)));

        ModelResourceLocation JetpackRL = getInventoryMRL("Jetpack");
        modelRegistry.putObject(JetpackRL, RenderJetpack.model = new ItemLayerWrapper(modelRegistry.getObject(JetpackRL)));

        ModelResourceLocation ArmorJetpackRL = getInventoryMRL("ArmoredJetpack");
        modelRegistry.putObject(ArmorJetpackRL, RenderArmoredJetpack.model = new ItemLayerWrapper(modelRegistry.getObject(ArmorJetpackRL)));

        ModelResourceLocation GasMaskRL = getInventoryMRL("GasMask");
        modelRegistry.putObject(GasMaskRL, RenderGasMask.model = new ItemLayerWrapper(modelRegistry.getObject(GasMaskRL)));

        ModelResourceLocation RobitRL = getInventoryMRL("Robit");
        modelRegistry.putObject(RobitRL, RenderItemRobit.model = new ItemLayerWrapper(modelRegistry.getObject(RobitRL)));

        ModelResourceLocation ScubaTankRL = getInventoryMRL("ScubaTank");
        modelRegistry.putObject(ScubaTankRL, RenderScubaTank.model = new ItemLayerWrapper(modelRegistry.getObject(ScubaTankRL)));

        ModelResourceLocation FreeRunnerRL = getInventoryMRL("FreeRunners");
        modelRegistry.putObject(FreeRunnerRL, RenderFreeRunners.model = new ItemLayerWrapper(modelRegistry.getObject(FreeRunnerRL)));

        ModelResourceLocation AtomicDisassemblerRL = getInventoryMRL("AtomicDisassembler");
        modelRegistry.putObject(AtomicDisassemblerRL, RenderAtomicDisassembler.model = new ItemLayerWrapper(modelRegistry.getObject(AtomicDisassemblerRL)));

        ModelResourceLocation FlamethrowerRL = getInventoryMRL("Flamethrower");
        modelRegistry.putObject(FlamethrowerRL, RenderFlameThrower.model = new ItemLayerWrapper(modelRegistry.getObject(FlamethrowerRL)));

        machineModelBake(modelRegistry, "digital_miner", MachineType.DIGITAL_MINER);
        machineModelBake(modelRegistry, "solar_neutron_activator", MachineType.SOLAR_NEUTRON_ACTIVATOR);
        machineModelBake(modelRegistry, "chemical_dissolution_chamber", MachineType.CHEMICAL_DISSOLUTION_CHAMBER);
        machineModelBake(modelRegistry, "chemical_crystallizer", MachineType.CHEMICAL_CRYSTALLIZER);
        machineModelBake(modelRegistry, "seismic_vibrator", MachineType.SEISMIC_VIBRATOR);
        machineModelBake(modelRegistry, "quantum_entangloporter", MachineType.QUANTUM_ENTANGLOPORTER);
        machineModelBake(modelRegistry, "resistive_heater", MachineType.RESISTIVE_HEATER);
        machineModelBake(modelRegistry, "personal_chest", MachineType.PERSONAL_CHEST);

        machineModelBake(modelRegistry, "fluid_tank", MachineType.FLUID_TANK);

        /**
         * ADD START
         */
        machineModelBake(modelRegistry, "isotopic_centrifuge", MachineType.ISOTOPIC_CENTRIFUGE);
        machineModelBake(modelRegistry, "nutritional_liquifier", MachineType.NUTRITIONAL_LIQUIFIER);
        machineModelBake(modelRegistry, "antiprotonic_nucleosynthesizer", MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER);
        ModelResourceLocation ArmorFreeRunnerRL = getInventoryMRL("ArmoredFreeRunners");
        modelRegistry.putObject(ArmorFreeRunnerRL, RenderArmoredFreeRunners.model = new ItemLayerWrapper(modelRegistry.getObject(FreeRunnerRL)));


        ModelResourceLocation MekaFishingRod = getInventoryMRL("mekafishingrod");
        modelRegistry.putObject(MekaFishingRod, RenderMekaFishingRod.model = new ItemLayerWrapper(modelRegistry.getObject(MekaFishingRod)));

        ModelResourceLocation Meka_Tool = getInventoryMRL("meka_tool");
        modelRegistry.putObject(Meka_Tool, RenderMekaTool.model = new ItemLayerWrapper(modelRegistry.getObject(Meka_Tool)));

        /**
         * ADD END
         */

        //basicBlockModelBake(modelRegistry, "bin", BasicBlockType.BIN);
        basicBlockModelBake(modelRegistry, "security_desk", BasicBlockType.SECURITY_DESK);
    }

    private void machineModelBake(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, String type, MachineType machineType) {
        ModelResourceLocation modelResourceLocation = getInventoryMRL(type);
        ItemLayerWrapper itemLayerWrapper = new ItemLayerWrapper(modelRegistry.getObject(modelResourceLocation));
        RenderMachineItem.modelMap.put(machineType, itemLayerWrapper);
        modelRegistry.putObject(modelResourceLocation, itemLayerWrapper);
    }

    private void basicBlockModelBake(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, String type, BasicBlockType basicType) {
        ModelResourceLocation modelResourceLocation = getInventoryMRL(type);
        ItemLayerWrapper itemLayerWrapper = new ItemLayerWrapper(modelRegistry.getObject(modelResourceLocation));
        RenderBasicBlockItem.modelMap.put(basicType, itemLayerWrapper);
        modelRegistry.putObject(modelResourceLocation, itemLayerWrapper);
    }

    @Override
    public void preInit() {
        MekanismRenderer.init();

        ModelLoaderRegistry.registerLoader(MekanismOBJLoader.INSTANCE);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(MekanismOBJLoader.INSTANCE);

        //Register entity rendering handlers
        RenderingRegistry.registerEntityRenderingHandler(EntityObsidianTNT.class, RenderObsidianTNTPrimed::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityRobit.class, RenderRobit::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBalloon.class, RenderBalloon::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBabySkeleton.class, RenderSkeleton::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityFlame.class, RenderFlame::new);

        RenderingRegistry.registerEntityRenderingHandler(EntityBabyCreeper.class, RenderBabyCreeper::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBabyEnderman.class, RenderBabyEnderman::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBabyStray.class, RenderStray::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBabyWitherSkeleton.class, RenderWitherSkeleton::new);
    }

    @Override
    public double getReach(EntityPlayer player) {
        return Minecraft.getMinecraft().playerController.getBlockReachDistance();
    }


    @Override
    public boolean isPaused() {
        if (FMLClientHandler.instance().getClient().isSingleplayer() && !FMLClientHandler.instance().getClient().getIntegratedServer().getPublic()) {
            GuiScreen screen = FMLClientHandler.instance().getClient().currentScreen;
            return screen != null && screen.doesGuiPauseGame();
        }
        return false;
    }

    @Override
    public File getMinecraftDir() {
        return Minecraft.getMinecraft().gameDir;
    }

    @Override
    public EntityPlayer getPlayer(MessageContext context) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            return context.getServerHandler().player;
        }
        return Minecraft.getMinecraft().player;
    }

    @Override
    public void handlePacket(Runnable runnable, EntityPlayer player) {
        if (player == null || player.world.isRemote) {
            Minecraft.getMinecraft().addScheduledTask(runnable);
        } else {
            //Single player
            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).addScheduledTask(runnable);
            } else {
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    server.addScheduledTask(runnable);
                } else {
                    Mekanism.logger.error("Packet handler wanted to set a scheduled task, but we couldn't find a way to set one.");
                    Mekanism.logger.error("Player = {}, World = {}", player, player.world);
                }
            }
        }
    }

    @Override
    public void renderLaser(World world, Pos3D from, Pos3D to, EnumFacing direction, double energy) {
        Minecraft.getMinecraft().effectRenderer.addEffect(new ParticleLaser(world, from, to, direction, energy));
    }

    @Override
    public FontRenderer getFontRenderer() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    @Override
    public void throwApiPresentException() {
        throw new ApiJarPresentException(API_PRESENT_MESSAGE);
    }

    @Override
    protected void postInit() {
        Minecraft.getMinecraft().getRenderManager().entityRenderMap.values().forEach(renderer -> {
            if (renderer instanceof RenderLivingBase<?> base) {
                if (Mekanism.hooks.MekanismMixinHelp) {
                    base.addLayer(new MekanismElytraLayer(base));
                    base.addLayer(new MekaSuitModuleElytra(base));
                }
            }
        });
        Minecraft.getMinecraft().getRenderManager().getSkinMap().values().forEach(renderer -> {
                    if (Mekanism.hooks.MekanismMixinHelp) {
                        renderer.addLayer(new MekanismElytraLayer(renderer));
                        renderer.addLayer(new MekaSuitModuleElytra(renderer));
                    }
                }
        );

        Particle.registerParticles();
    }


}
