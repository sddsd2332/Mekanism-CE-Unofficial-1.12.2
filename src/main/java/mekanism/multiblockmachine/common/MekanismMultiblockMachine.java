package mekanism.multiblockmachine.common;

import io.netty.buffer.ByteBuf;
import mekanism.api.MekanismAPI;
import mekanism.common.Mekanism;
import mekanism.common.Version;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.network.PacketSimpleGui;
import mekanism.multiblockmachine.common.registries.MultiblockMachineBlocks;
import mekanism.multiblockmachine.common.registries.MultiblockMachineItems;
import mekanism.multiblockmachine.common.processing.MultiblockMachineRecipeProviders;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = MekanismMultiblockMachine.MODID, useMetadata = true, guiFactory = "mekanism.multiblockmachine.client.gui.MultiblockMachineGuiFactory",customProperties = {
        @Mod.CustomProperty(k = "license", v = "EUPL-1.2"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/sddsd2332/Mekanism-CE-Unofficial-1.12.2/issues"),
        @Mod.CustomProperty(k = "iconFile", v = "assets/mekanism/icon.png"),
        @Mod.CustomProperty(k = "backgroundFile", v = "assets/mekanismmultiblockmachine/background.png")
})
@Mod.EventBusSubscriber()
public class MekanismMultiblockMachine implements IModule {

    public static final String MODID = "mekanismmultiblockmachine";

    @SidedProxy(clientSide = "mekanism.multiblockmachine.client.MultiblockMachineClientProxy", serverSide = "mekanism.multiblockmachine.common.MultiblockMachineCommonProxy")
    public static MultiblockMachineCommonProxy proxy;

    @Mod.Instance(MekanismMultiblockMachine.MODID)
    public static MekanismMultiblockMachine instance;

    public static Version versionNumber = new Version(999, 999, 999);

    public static CreativeTabMekanismMultiblockMachine tabMekanismMultiblockMachine = new CreativeTabMekanismMultiblockMachine();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        MultiblockMachineBlocks.registerBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        MultiblockMachineItems.registerItems(event.getRegistry());
        MultiblockMachineBlocks.registerItemBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        // Register models
        proxy.registerBlockRenders();
        proxy.registerItemRenders();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
        proxy.loadConfiguration();
        MultiblockMachineRecipeProviders.register();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        //Add this module to the core list
        Mekanism.modulesLoaded.add(this);
        //Register this module's GUI handler in the simple packet protocol
        PacketSimpleGui.handlers.add(proxy);
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new MultiblockMachineGuiHandler());
        MinecraftForge.EVENT_BUS.register(this);
        proxy.registerTileEntities();
        proxy.registerTESRs();

    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }


    @Override
    public String getName() {
        return "MultiblockMachine";
    }

    @Override
    public void writeConfig(ByteBuf dataStream, MekanismConfig config) {
        config.multiblock.write(dataStream);
    }

    @Override
    public void readConfig(ByteBuf dataStream, MekanismConfig destConfig) {
        destConfig.multiblock.read(dataStream);
    }

    @Override
    public void resetClient() {
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(MekanismMultiblockMachine.MODID)|| event.getModID().equals(Mekanism.MODID)) {
            proxy.loadConfiguration();
        }
    }

    @SubscribeEvent
    public void onBlacklistUpdate(MekanismAPI.BoxBlacklistEvent event) {
        //本mod的所有方块都不能进行搬
        MekanismAPI.addBoxBlacklistMod(MekanismMultiblockMachine.MODID);
    }


    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        MekanismMultiblockMachineRecipe.addRecipes();
    }
}
