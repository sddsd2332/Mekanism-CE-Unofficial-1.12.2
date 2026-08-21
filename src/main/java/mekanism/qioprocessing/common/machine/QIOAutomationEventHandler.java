package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;

/** Attaches QIO state without modifying any machine TileEntity class. */
/**
 * QIO 处理模块中的 QIOAutomationEventHandler 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationEventHandler {

    public static final QIOAutomationEventHandler INSTANCE = new QIOAutomationEventHandler();

    private QIOAutomationEventHandler() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(QIOAutomationDeviceRegistry.INSTANCE);
    }

    @SubscribeEvent
    public void attachCapabilities(@Nonnull AttachCapabilitiesEvent<TileEntity> event) {
        TileEntity tile = event.getObject();
        if (!(tile instanceof TileEntityContainerBlock) || !(tile instanceof IUpgradeTile)) {
            return;
        }
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        if (provider == null) {
            return;
        }
        ProviderConformanceDescriptor descriptor = provider.getQIOConformance();
        if (!descriptor.isRegistered()) {
            return;
        }
        QIOAutomationHostProvider hostProvider = new QIOAutomationHostProvider(tile);
        event.addCapability(QIOAutomationCapabilities.NAME, hostProvider);
        QIOAutomationDeviceRegistry.INSTANCE.trackPending(hostProvider.host());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(@Nonnull BlockEvent.BreakEvent event) {
        if (!event.getWorld().isRemote) {
            QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(
                  event.getWorld().getTileEntity(event.getPos()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosionDetonate(@Nonnull ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) {
            return;
        }
        for (BlockPos position : event.getAffectedBlocks()) {
            QIOAutomationDeviceDirectoryCleanupService.forgetLoadedTile(
                  event.getWorld().getTileEntity(position));
        }
    }

    @SubscribeEvent
    public void onChunkLoad(@Nonnull ChunkEvent.Load event) {
        if (!event.getWorld().isRemote) {
            QIOAutomationDeviceDirectoryCleanupService.queueChunk(event.getWorld(),
                  event.getChunk().x, event.getChunk().z);
        }
    }

    @SubscribeEvent
    public void onChunkUnload(@Nonnull ChunkEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            QIOAutomationDeviceDirectoryCleanupService.discardChunk(event.getWorld(),
                  event.getChunk().x, event.getChunk().z);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(@Nonnull WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            QIOAutomationDeviceDirectoryCleanupService.discardDimension(
                  event.getWorld().provider.getDimension());
        }
    }
}
