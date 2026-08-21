package mekanism.qioprocessing.common.network;

import mekanism.qioprocessing.common.network.PacketQIOAutomationRecipeConfig.QIOAutomationRecipeConfigMessage;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * QIO 处理模块中的 QIOProcessingPacketHandler 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingPacketHandler {

    public static final QIOProcessingPacketHandler INSTANCE = new QIOProcessingPacketHandler();
    private final SimpleNetworkWrapper channel = NetworkRegistry.INSTANCE.newSimpleChannel("MEKQIOPROC");
    private int packetId;
    private boolean initialized;

    private QIOProcessingPacketHandler() {
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        channel.registerMessage(PacketQIOAutomationBinding.class,
              PacketQIOAutomationBinding.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOAutomationRecovery.class,
              PacketQIOAutomationRecovery.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOAutomationTracking.class,
              PacketQIOAutomationTracking.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOProcessingTerminalBinding.class,
              PacketQIOProcessingTerminalBinding.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOCraftingProcessorBinding.class,
              PacketQIOCraftingProcessorBinding.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOProcessingTerminalPreference.class,
              PacketQIOProcessingTerminalPreference.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementDevicePageRequest.class,
              PacketQIOManagementDevicePageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementDevicePageData.class,
              PacketQIOManagementDevicePageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOManagementDeviceGroupPageRequest.class,
              PacketQIOManagementDeviceGroupPageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementDeviceGroupPageData.class,
              PacketQIOManagementDeviceGroupPageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOManagementPolicyPageRequest.class,
              PacketQIOManagementPolicyPageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementPolicyPageData.class,
              PacketQIOManagementPolicyPageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOManagementPolicyMutation.class,
              PacketQIOManagementPolicyMutation.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementPolicyMutationResult.class,
              PacketQIOManagementPolicyMutationResult.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOManagementPolicyLookupRequest.class,
              PacketQIOManagementPolicyLookupRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementPolicyLookupData.class,
              PacketQIOManagementPolicyLookupData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOMaintenanceRulePageRequest.class,
              PacketQIOMaintenanceRulePageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOMaintenanceRulePageData.class,
              PacketQIOMaintenanceRulePageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOMaintenanceRuleMutation.class,
              PacketQIOMaintenanceRuleMutation.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOMaintenanceRuleMutationResult.class,
              PacketQIOMaintenanceRuleMutationResult.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOMaintenanceResourcePageRequest.class,
              PacketQIOMaintenanceResourcePageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOMaintenanceResourcePageData.class,
              PacketQIOMaintenanceResourcePageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOCraftingMonitorPageRequest.class,
              PacketQIOCraftingMonitorPageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOCraftingMonitorPageData.class,
              PacketQIOCraftingMonitorPageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOCraftingMonitorCancel.class,
              PacketQIOCraftingMonitorCancel.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOCraftingMonitorCancelResult.class,
              PacketQIOCraftingMonitorCancelResult.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOSmartProcessingResourcePageRequest.class,
              PacketQIOSmartProcessingResourcePageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOSmartProcessingResourcePageData.class,
              PacketQIOSmartProcessingResourcePageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOSmartProcessingPreviewAction.class,
              PacketQIOSmartProcessingPreviewAction.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOSmartProcessingPreviewData.class,
              PacketQIOSmartProcessingPreviewData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOCraftingMonitorPlanPageRequest.class,
              PacketQIOCraftingMonitorPlanPageRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOCraftingMonitorPlanPageData.class,
              PacketQIOCraftingMonitorPlanPageData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOCraftingMonitorRuntimeRequest.class,
              PacketQIOCraftingMonitorRuntimeRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOCraftingMonitorRuntimeData.class,
              PacketQIOCraftingMonitorRuntimeData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIODeviceCommand.class,
              PacketQIODeviceCommand.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIODeviceCommandResult.class,
              PacketQIODeviceCommandResult.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOCraftingMonitorMutation.class,
              PacketQIOCraftingMonitorMutation.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOCraftingMonitorMutationResult.class,
              PacketQIOCraftingMonitorMutationResult.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOAutomationRecipeConfig.class,
              QIOAutomationRecipeConfigMessage.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOAutomationRecipeConfig.class,
              QIOAutomationRecipeConfigMessage.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOManagementRecipeRequest.class,
              PacketQIOManagementRecipeRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOManagementRecipeData.class,
              PacketQIOManagementRecipeData.Message.class, nextPacketId(), Side.CLIENT);
        channel.registerMessage(PacketQIOWorkbenchConfigurationRequest.class,
              PacketQIOWorkbenchConfigurationRequest.Message.class, nextPacketId(), Side.SERVER);
        channel.registerMessage(PacketQIOWorkbenchConfigurationData.class,
              PacketQIOWorkbenchConfigurationData.Message.class, nextPacketId(), Side.CLIENT);
    }

    private int nextPacketId() {
        return packetId++;
    }

    public void sendToServer(IMessage message) {
        channel.sendToServer(message);
    }

    public void sendTo(IMessage message, EntityPlayerMP player) {
        channel.sendTo(message, player);
    }
}
