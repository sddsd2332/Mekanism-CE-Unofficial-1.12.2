package mekanism.qioprocessing.common;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.Version;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequencyLifecycleRegistry;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.content.QIOProcessingFrequencyLifecycle;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkService;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.content.material.QIOClaimWakeService;
import mekanism.qioprocessing.common.execution.QIOEndpointPersistenceService;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.machine.*;
import mekanism.qioprocessing.common.maintenance.QIOMaintenanceService;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.order.QIOOrderService;
import mekanism.qioprocessing.common.planning.*;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry;
import mekanism.qioprocessing.common.recipe.RecipeDirectoryService;
import mekanism.qioprocessing.common.registries.QIOProcessingBlocks;
import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;
import mekanism.qioprocessing.common.terminal.QIOPortableTerminalIdentityRegistry;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalDeviceRegistry;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosureService;
import mekanism.qioprocessing.common.workbench.WorkbenchBookRuntime;
import mekanism.qioprocessing.common.workbench.WorkbenchRuntime;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = MekanismQIOProcessing.MODID, useMetadata = true, acceptedMinecraftVersions = "[1.12,1.13)",
        guiFactory = "mekanism.qioprocessing.client.gui.QIOProcessingGuiFactory",
        dependencies = "required-after:mekanism",
        customProperties = {
                @Mod.CustomProperty(k = "license", v = "EUPL-1.2"),
                @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/sddsd2332/Mekanism-CE-Unofficial-1.12.2/issues"),
                @Mod.CustomProperty(k = "iconFile", v = "assets/mekanism/icon.png")
        })
@Mod.EventBusSubscriber(modid = MekanismQIOProcessing.MODID)
/**
 * QIO 处理模块中的 MekanismQIOProcessing 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class MekanismQIOProcessing implements IModule {

    public static final String MODID = "mekanismqioprocessing";

    @SidedProxy(clientSide = "mekanism.qioprocessing.client.QIOProcessingClientProxy",
            serverSide = "mekanism.qioprocessing.common.QIOProcessingCommonProxy")
    public static QIOProcessingCommonProxy proxy;

    @Mod.Instance(MODID)
    public static MekanismQIOProcessing instance;

    public static CreativeTabQIOProcessing TAB = new CreativeTabQIOProcessing();
    public static Version versionNumber = Version.get(mekanism.mekanism.Tags.VERSION);

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
        TileEntityContainerBlock.setContainerExtractionGuard(QIOAutomationPortGuard::isExtractionBlocked);
        mekanism.common.tile.component.UpgradeComponentRegistry.register(
                mekanism.common.tile.component.UpgradeComponentRegistry.Slot.QIO,
                mekanism.qioprocessing.common.machine.QIOUpgradeComponent::find);
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
        MinecraftForge.EVENT_BUS.register(WorkbenchRuntime.class);
        MinecraftForge.EVENT_BUS.register(QIORecipeCatalogService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOWorkbenchValidationService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(mekanism.qioprocessing.common.terminal.QIOWorkbenchEncodingJobService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOProcessingNetworkService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(QIOCraftingProcessorDeviceRegistry.INSTANCE);
        MinecraftForge.EVENT_BUS.register(this);
        proxy.registerClientHandlers();
        Mekanism.logger.info("Loaded Mekanism QIO Processing module foundation.");
    }

    /**
     * The first frozen Forge mapping is the only automatic global-catalog trigger. Mapping events
     * also fire while worlds and clients are remapped; those later notifications are ignored.
     */
    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        QIOWorkbenchDirectoryService.INSTANCE.armInitialFreeze();
        RecipeDirectoryService.INSTANCE.armInitialFreeze();
    }

    @Mod.EventHandler
    public void onModIdMapping(FMLModIdMappingEvent event) {
        RecipeDirectoryService.INSTANCE.onMapping(event.isFrozen);
        QIOWorkbenchDirectoryService.INSTANCE.firstMapping(event.isFrozen);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        QIOWorkbenchDirectoryService.INSTANCE.closeInitialWindow();
        RecipeDirectoryService.INSTANCE.closeInitialWindow();
        QIOPlanningService.INSTANCE.start();
        QIOWorkbenchValidationService.INSTANCE.start();
        WorkbenchRuntime.start(event.getServer());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        WorkbenchBookRuntime.open(DimensionManager.getWorld(0));
        QIORecipeCatalogService.INSTANCE.beginRefresh(DimensionManager.getWorld(0));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        WorkbenchRuntime.stop();
        WorkbenchBookRuntime.close();
        QIOWorkbenchClosureService.shutdown();
        QIOWorkbenchValidationService.INSTANCE.stop();
        QIOMaintenanceService.INSTANCE.shutdown();
        QIOReplanningService.INSTANCE.shutdown();
        QIOPlanningService.INSTANCE.stop();
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
