package mekanism.qioprocessing.common;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.Version;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkService;
import mekanism.qioprocessing.common.content.QIOProcessingFrequencyLifecycle;
import mekanism.common.content.qio.QIOFrequencyLifecycleRegistry;
import mekanism.qioprocessing.common.content.material.QIOClaimWakeService;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import mekanism.qioprocessing.common.machine.QIOAutomationEventHandler;
import mekanism.qioprocessing.common.machine.QIOAutomationRecipeConfigService;
import mekanism.qioprocessing.common.machine.QIOAutomationRecipeConfigCardData;
import mekanism.qioprocessing.common.machine.QIOAutomaticOutputService;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import mekanism.qioprocessing.common.registries.QIOProcessingBlocks;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import mekanism.qioprocessing.common.planning.QIOPlanningService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOReplanningService;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.execution.QIOEndpointPersistenceService;
import net.minecraftforge.common.DimensionManager;
import mekanism.qioprocessing.common.order.QIOOrderService;
import mekanism.qioprocessing.common.maintenance.QIOMaintenanceService;
import mekanism.qioprocessing.common.terminal.QIOPortableTerminalIdentityRegistry;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalDeviceRegistry;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosureService;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = MekanismQIOProcessing.MODID, useMetadata = true, acceptedMinecraftVersions = "[1.12,1.13)",
      dependencies = "required-after:mekanism",
      customProperties = {
            @Mod.CustomProperty(k = "license", v = "EUPL-1.2"),
            @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/sddsd2332/Mekanism-CE-Unofficial-1.12.2/issues"),
            @Mod.CustomProperty(k = "iconFile", v = "assets/mekanism/icon.png")
      })
@Mod.EventBusSubscriber(modid = MekanismQIOProcessing.MODID)
public class MekanismQIOProcessing implements IModule {

    public static final String MODID = "mekanismqioprocessing";

    @SidedProxy(clientSide = "mekanism.qioprocessing.client.QIOProcessingClientProxy",
          serverSide = "mekanism.qioprocessing.common.QIOProcessingCommonProxy")
    public static QIOProcessingCommonProxy proxy;

    @Mod.Instance(MODID)
    public static MekanismQIOProcessing instance;

    public static  CreativeTabQIOProcessing TAB = new CreativeTabQIOProcessing();
    public static Version versionNumber = new Version(999, 999, 999);

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        QIOProcessingBlocks.registerBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        QIOProcessingItems.registerItems(event.getRegistry());
        QIOProcessingBlocks.registerItemBlocks(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        proxy.registerBlockRenders();
        proxy.registerItemRenders();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
        QIOProcessingWindowTypes.bootstrap();
        QIOAutomationCapabilities.register();
        QIOProcessingPacketHandler.INSTANCE.initialize();
        QIOAutomationEventHandler.INSTANCE.register();
        QIOAutomationContainerState.registerContainerExtension();
        QIOAutomationRecipeConfigCardData.register();
        QIOAutomationUpgradeSupport.registerUpgradeSupport();
        QIOCraftingProcessorRegistry.bootstrapBuiltins(
              MekanismConfig.local().qioProcessing.processorLaneLimit.val());
        QIOProcessingProcessorHosts.registerBuiltins();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.registerTileEntities();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new QIOProcessingGuiHandler());
        QIOCraftingProcessorRegistry.freeze();
        QIOCraftingProcessorHostRegistry.freeze();
        Mekanism.modulesLoaded.add(this);
        QIOFrequencyLifecycleRegistry.register(QIOProcessingFrequencyLifecycle.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOPlanningService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOWorkbenchClosureService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOProcessingNetworkService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOAutomaticOutputService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOCraftingProcessorDeviceRegistry.INSTANCE);
        MinecraftForge.EVENT_BUS.register(this);
        proxy.registerClientHandlers();
        Mekanism.logger.info("Loaded Mekanism QIO Processing module foundation.");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        QIOPlanningService.INSTANCE.start();
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        QIORecipeCatalogService.INSTANCE.refresh(DimensionManager.getWorld(0));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        QIOMaintenanceService.INSTANCE.shutdown();
        QIOReplanningService.INSTANCE.shutdown();
        QIOPlanningService.INSTANCE.stop();
        QIOWorkbenchClosureService.shutdown();
        QIORecipeCatalogService.INSTANCE.clear();
        QIOOrderService.INSTANCE.clear();
        QIOClaimWakeService.INSTANCE.shutdown();
        QIOProcessingExecutionService.INSTANCE.shutdown();
        QIOEndpointPersistenceService.INSTANCE.clear();
        QIOProcessingNetworkManager.INSTANCE.shutdown();
        QIOAutomationDeviceDirectoryCleanupService.clear();
        QIOAutomationDeviceRegistry.INSTANCE.shutdown();
        QIOAutomationRecipeConfigService.clearRouteDirectoryCache();
        QIOCraftingProcessorDeviceRegistry.INSTANCE.clear();
        QIOPortableTerminalIdentityRegistry.INSTANCE.shutdown();
        QIOProcessingTerminalDeviceRegistry.INSTANCE.shutdown();
    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }

    @Override
    public String getName() {
        return "QIOProcessing";
    }

    @Override
    public void writeConfig(ByteBuf dataStream, MekanismConfig config) {
        config.qioProcessing.write(dataStream);
    }

    @Override
    public void readConfig(ByteBuf dataStream, MekanismConfig destConfig) {
        destConfig.qioProcessing.read(dataStream);
    }

    @Override
    public void resetClient() {
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MODID.equals(event.getModID())) {
            proxy.loadConfiguration();
        }
    }
}
