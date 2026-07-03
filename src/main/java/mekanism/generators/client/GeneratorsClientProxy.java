package mekanism.generators.client;

import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.generators.client.render.*;
import mekanism.generators.client.render.item.RenderGeneratorItem;
import mekanism.generators.common.GeneratorsBlocks;
import mekanism.generators.common.GeneratorsCommonProxy;
import mekanism.generators.common.GeneratorsItems;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorBlockStateMapper;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorType;
import mekanism.generators.common.block.states.BlockStateReactor.ReactorBlockStateMapper;
import mekanism.generators.common.block.states.BlockStateReactor.ReactorBlockType;
import mekanism.generators.common.tile.*;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorLogicAdapter;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import mekanism.generators.common.tile.reactor.TileEntityReactorLogicAdapter;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import mekanism.generators.common.tile.turbine.TileEntityTurbineRotor;
import mekanism.generators.common.tile.turbine.TileEntityTurbineValve;
import mekanism.generators.common.tile.turbine.TileEntityTurbineVent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GeneratorsClientProxy extends GeneratorsCommonProxy {

    private static final IStateMapper generatorMapper = new GeneratorBlockStateMapper();
    private static final IStateMapper reactorMapper = new ReactorBlockStateMapper();

    @Override
    public void registerTESRs() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAdvancedSolarGenerator.class, new RenderAdvancedSolarGenerator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBioGenerator.class, RenderBioGenerator.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGasGenerator.class, new RenderGasGenerator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHeatGenerator.class, new RenderHeatGenerator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityReactorController.class, new RenderReactor());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySolarGenerator.class, new RenderSolarGenerator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTurbineCasing.class, new RenderIndustrialTurbine());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTurbineRotor.class, new RenderTurbineRotor());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTurbineValve.class, new RenderIndustrialTurbine());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTurbineVent.class, new RenderIndustrialTurbine());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWindGenerator.class, new RenderWindGenerator());
    }

    @Override
    public void registerItemRenders() {
        registerItemRender(GeneratorsItems.SolarPanel);
        registerItemRender(GeneratorsItems.Hohlraum);
        registerItemRender(GeneratorsItems.TurbineBlade);
        registerItemRender(GeneratorsItems.MODULE_SOLAR_RECHARGING);
        registerItemRender(GeneratorsItems.MODULE_GEOTHERMAL_GENERATOR);

        Item.getItemFromBlock(GeneratorsBlocks.Generator).setTileEntityItemStackRenderer(new RenderGeneratorItem());
        Item.getItemFromBlock(GeneratorsBlocks.Generator2).setTileEntityItemStackRenderer(new RenderGeneratorItem());
    }

    @Override
    public void registerBlockRenders() {
        ModelLoader.setCustomStateMapper(GeneratorsBlocks.Generator, generatorMapper);
        ModelLoader.setCustomStateMapper(GeneratorsBlocks.Generator2, generatorMapper);
        ModelLoader.setCustomStateMapper(GeneratorsBlocks.Reactor, reactorMapper);
        ModelLoader.setCustomStateMapper(GeneratorsBlocks.ReactorGlass, reactorMapper);

        for (GeneratorType type : GeneratorType.values()) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(type.blockType.getBlock()), type.meta, new ModelResourceLocation(new ResourceLocation(MekanismGenerators.MODID, type.getName()), "inventory"));
        }

        for (ReactorBlockType type : ReactorBlockType.values()) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(type.blockType.getBlock()), type.meta, new ModelResourceLocation(new ResourceLocation(MekanismGenerators.MODID, type.getName()), "inventory"));
        }
    }

    public void registerItemRender(Item item) {
        MekanismRenderer.registerItemRender(MekanismGenerators.MODID, item);
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        IRegistry<ModelResourceLocation, IBakedModel> modelRegistry = event.getModelRegistry();
        generatorModelBake(modelRegistry, GeneratorType.HEAT_GENERATOR);
        generatorModelBake(modelRegistry, GeneratorType.SOLAR_GENERATOR);
        generatorModelBake(modelRegistry, GeneratorType.BIO_GENERATOR);
        generatorModelBake(modelRegistry, GeneratorType.WIND_GENERATOR);
        generatorModelBake(modelRegistry, GeneratorType.GAS_GENERATOR);
        generatorModelBake(modelRegistry, GeneratorType.ADVANCED_SOLAR_GENERATOR);
    }

    private void generatorModelBake(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, GeneratorType type) {
        ModelResourceLocation modelResourceLocation = new ModelResourceLocation(new ResourceLocation(MekanismGenerators.MODID, type.getName()), "inventory");
        ItemLayerWrapper itemLayerWrapper = new ItemLayerWrapper(modelRegistry.getObject(modelResourceLocation));
        RenderGeneratorItem.modelMap.put(type, itemLayerWrapper);
        modelRegistry.putObject(modelResourceLocation, itemLayerWrapper);
    }

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public GuiScreen getClientGui(int ID, EntityPlayer player, World world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);

        return switch (ID) {
            case 0 -> new mekanism.generators.client.gui.GuiHeatGenerator(player.inventory, (TileEntityHeatGenerator) tileEntity);
            case 1 -> new mekanism.generators.client.gui.GuiSolarGenerator(player.inventory, (TileEntitySolarGenerator) tileEntity);
            case 3 -> new mekanism.generators.client.gui.GuiGasGenerator(player.inventory, (TileEntityGasGenerator) tileEntity);
            case 4 -> new mekanism.generators.client.gui.GuiBioGenerator(player.inventory, (TileEntityBioGenerator) tileEntity);
            case 5 -> new mekanism.generators.client.gui.GuiWindGenerator(player.inventory, (TileEntityWindGenerator) tileEntity);
            case 6 -> new mekanism.generators.client.gui.GuiIndustrialTurbine(player.inventory, (TileEntityTurbineCasing) tileEntity);
            case 7 -> new mekanism.generators.client.gui.GuiTurbineStats(player.inventory, (TileEntityTurbineCasing) tileEntity);
            case 10 -> new mekanism.generators.client.gui.GuiFusionReactorController(player.inventory, (TileEntityReactorController) tileEntity);
            case 11 -> new mekanism.generators.client.gui.GuiFusionReactorHeat(player.inventory, (TileEntityReactorController) tileEntity);
            case 12 -> new mekanism.generators.client.gui.GuiFusionReactorFuel(player.inventory, (TileEntityReactorController) tileEntity);
            case 13 -> new mekanism.generators.client.gui.GuiFusionReactorStats(player.inventory, (TileEntityReactorController) tileEntity);
            case 15 -> new mekanism.generators.client.gui.GuiFusionReactorLogicAdapter(player.inventory, (TileEntityReactorLogicAdapter) tileEntity);
            case 16 -> new mekanism.generators.client.gui.GuiFissionReactor(player.inventory, (TileEntityFissionReactorCasing) tileEntity);
            case 17 -> new mekanism.generators.client.gui.GuiFissionReactorStats(player.inventory, (TileEntityFissionReactorCasing) tileEntity);
            case 18 -> new mekanism.generators.client.gui.GuiFissionReactorLogicAdapter(player.inventory, (TileEntityFissionReactorLogicAdapter) tileEntity);
            default -> null;
        };

    }

    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre event) {
        RenderBioGenerator.resetDisplayInts();
    }
}
