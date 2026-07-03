package mekanism.multiblockmachine.client;

import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.common.util.MekanismUtils;
import mekanism.multiblockmachine.client.gui.generator.GuiLargeGasGenerator;
import mekanism.multiblockmachine.client.gui.generator.GuiLargeWindGenerator;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeChemicalInfuser;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeChemicalWasher;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeElectrolyticSeparator;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeSolarNeutronActivator;
import mekanism.multiblockmachine.client.render.block.generator.RenderLargeGasGenerator;
import mekanism.multiblockmachine.client.render.block.generator.RenderLargeWindGenerator;
import mekanism.multiblockmachine.client.render.block.machine.RenderLargeChemicalInfuser;
import mekanism.multiblockmachine.client.render.block.machine.RenderLargeChemicalWasher;
import mekanism.multiblockmachine.client.render.block.machine.RenderLargeElectrolyticSeparator;
import mekanism.multiblockmachine.client.render.block.machine.RenderLargeSolarNeutronActivator;
import mekanism.multiblockmachine.client.render.item.generator.RenderLargeGasGeneratorItem;
import mekanism.multiblockmachine.client.render.item.generator.RenderLargeWindGeneratorItem;
import mekanism.multiblockmachine.client.render.item.machine.RenderLargeChemicalInfuserItem;
import mekanism.multiblockmachine.client.render.item.machine.RenderLargeChemicalWasherItem;
import mekanism.multiblockmachine.client.render.item.machine.RenderLargeElectrolyticSeparatorItem;
import mekanism.multiblockmachine.client.render.item.machine.RenderLargeSolarNeutronActivatorItem;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.MultiblockMachineCommonProxy;
import mekanism.multiblockmachine.common.registries.MultiblockMachineBlocks;
import mekanism.multiblockmachine.common.registries.MultiblockMachineItems;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
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
public class MultiblockMachineClientProxy extends MultiblockMachineCommonProxy {


    @Override
    public void registerTESRs() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeElectrolyticSeparator.class, new RenderLargeElectrolyticSeparator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeChemicalInfuser.class, new RenderLargeChemicalInfuser());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeChemicalWasher.class, new RenderLargeChemicalWasher());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeWindGenerator.class, new RenderLargeWindGenerator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeGasGenerator.class, new RenderLargeGasGenerator());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeSolarNeutronActivator.class,new RenderLargeSolarNeutronActivator());
        //提前注册屏幕的贴图，防止在运行游戏的时候卡下
        MekanismRenderer.bindTexture(MekanismUtils.getResource(MekanismMultiblockMachine.MODID, MekanismUtils.ResourceType.RENDER_MACHINE, "LargeSolarNeutronActivator/SCREEN.png"));
    }

    @Override
    public void registerItemRenders() {
        registerItemRender(MultiblockMachineItems.gas_adsorption_fractionation_module);
        registerItemRender(MultiblockMachineItems.high_frequency_fusion_molding_module);
        registerItemRender(MultiblockMachineItems.LaserLenses);
        registerItemRender(MultiblockMachineItems.advanced_electrolysis_core);
        Item.getItemFromBlock(MultiblockMachineBlocks.LargeElectrolyticSeparator).setTileEntityItemStackRenderer(new RenderLargeElectrolyticSeparatorItem());
        Item.getItemFromBlock(MultiblockMachineBlocks.LargeChemicalInfuser).setTileEntityItemStackRenderer(new RenderLargeChemicalInfuserItem());
        Item.getItemFromBlock(MultiblockMachineBlocks.LargeChemicalWasher).setTileEntityItemStackRenderer(new RenderLargeChemicalWasherItem());
        Item.getItemFromBlock(MultiblockMachineBlocks.LargeWindGenerator).setTileEntityItemStackRenderer(new RenderLargeWindGeneratorItem());
        Item.getItemFromBlock(MultiblockMachineBlocks.LargeGasGenerator).setTileEntityItemStackRenderer(new RenderLargeGasGeneratorItem());
        Item.getItemFromBlock(MultiblockMachineBlocks.LargeSolarNeutronActivator).setTileEntityItemStackRenderer(new RenderLargeSolarNeutronActivatorItem());
    }

    @Override
    public void registerBlockRenders() {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MultiblockMachineBlocks.LargeElectrolyticSeparator), 0, getInventoryMRL("LargeElectrolyticSeparator"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MultiblockMachineBlocks.LargeChemicalInfuser), 0, getInventoryMRL("LargeChemicalInfuser"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MultiblockMachineBlocks.LargeChemicalWasher), 0, getInventoryMRL("LargeChemicalWasher"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MultiblockMachineBlocks.LargeWindGenerator), 0, getInventoryMRL("LargeWindGenerator"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MultiblockMachineBlocks.LargeGasGenerator), 0, getInventoryMRL("LargeGasGenerator"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MultiblockMachineBlocks.LargeSolarNeutronActivator),0, getInventoryMRL("LargeSolarNeutronActivator"));
    }

    private ModelResourceLocation getInventoryMRL(String type) {
        return new ModelResourceLocation(new ResourceLocation(MekanismMultiblockMachine.MODID, type), "inventory");
    }

    public void registerItemRender(Item item) {
        MekanismRenderer.registerItemRender(MekanismMultiblockMachine.MODID, item);
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        IRegistry<ModelResourceLocation, IBakedModel> modelRegistry = event.getModelRegistry();
        ModelResourceLocation LargeElectrolyticSeparator = getInventoryMRL("LargeElectrolyticSeparator");
        modelRegistry.putObject(LargeElectrolyticSeparator, RenderLargeElectrolyticSeparatorItem.model = new ItemLayerWrapper(modelRegistry.getObject(LargeElectrolyticSeparator)));

        ModelResourceLocation LargeChemicalInfuser = getInventoryMRL("LargeChemicalInfuser");
        modelRegistry.putObject(LargeChemicalInfuser, RenderLargeChemicalInfuserItem.model = new ItemLayerWrapper(modelRegistry.getObject(LargeChemicalInfuser)));

        ModelResourceLocation LargeChemicalWasher = getInventoryMRL("LargeChemicalWasher");
        modelRegistry.putObject(LargeChemicalWasher, RenderLargeChemicalWasherItem.model = new ItemLayerWrapper(modelRegistry.getObject(LargeChemicalWasher)));

        ModelResourceLocation LargeWindGenerator = getInventoryMRL("LargeWindGenerator");
        modelRegistry.putObject(LargeWindGenerator, RenderLargeWindGeneratorItem.model = new ItemLayerWrapper(modelRegistry.getObject(LargeWindGenerator)));

        ModelResourceLocation LargeGasGenerator = getInventoryMRL("LargeGasGenerator");
        modelRegistry.putObject(LargeGasGenerator, RenderLargeGasGeneratorItem.model = new ItemLayerWrapper(modelRegistry.getObject(LargeGasGenerator)));

        ModelResourceLocation LargeSolarNeutronActivator = getInventoryMRL("LargeSolarNeutronActivator");
        modelRegistry.putObject(LargeSolarNeutronActivator, RenderLargeSolarNeutronActivatorItem.model = new ItemLayerWrapper(modelRegistry.getObject(LargeSolarNeutronActivator)));
    }


    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public GuiScreen getClientGui(int ID, EntityPlayer player, World world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        return switch (ID) {
            case 0 -> new GuiLargeElectrolyticSeparator(player.inventory, (TileEntityLargeElectrolyticSeparator) tileEntity);
            case 1 -> new GuiLargeChemicalInfuser(player.inventory, (TileEntityLargeChemicalInfuser) tileEntity);
            case 2 -> new GuiLargeChemicalWasher(player.inventory, (TileEntityLargeChemicalWasher) tileEntity);
            case 3 -> new GuiLargeWindGenerator(player.inventory, (TileEntityLargeWindGenerator) tileEntity);
            case 4 -> new GuiLargeGasGenerator(player.inventory, (TileEntityLargeGasGenerator) tileEntity);
            case 5 -> new GuiLargeSolarNeutronActivator(player.inventory,(TileEntityLargeSolarNeutronActivator) tileEntity);
            default -> null;
        };
    }


    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre event) {

    }
}
