package mekanism.qioprocessing.common.inventory.container;

/** Independent one-request-per-tick guards for management request streams. */
/**
 * QIO 处理模块中的 QIOManagementPageRequestThrottle 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOManagementPageRequestThrottle {

    private long lastDeviceRequestTick = Long.MIN_VALUE;
    private long lastPolicyRequestTick = Long.MIN_VALUE;
    private long lastPolicyLookupRequestTick = Long.MIN_VALUE;
    private long lastDeviceCommandTick = Long.MIN_VALUE;
    private long lastRecipeRequestTick = Long.MIN_VALUE;
    private long lastDeviceGroupRequestTick = Long.MIN_VALUE;
    private long lastWorkbenchProductsRequestTick = Long.MIN_VALUE;
    private long lastWorkbenchRecipesRequestTick = Long.MIN_VALUE;
    private long lastWorkbenchCandidatesRequestTick = Long.MIN_VALUE;
    private long lastWorkbenchCommandTick = Long.MIN_VALUE;

    boolean tryDevice(long currentTick) {
        if (currentTick <= lastDeviceRequestTick) {
            return false;
        }
        lastDeviceRequestTick = currentTick;
        return true;
    }

    boolean tryPolicy(long currentTick) {
        if (currentTick <= lastPolicyRequestTick) {
            return false;
        }
        lastPolicyRequestTick = currentTick;
        return true;
    }

    boolean tryPolicyLookup(long currentTick) {
        if (currentTick <= lastPolicyLookupRequestTick) {
            return false;
        }
        lastPolicyLookupRequestTick = currentTick;
        return true;
    }

    boolean tryDeviceCommand(long currentTick) {
        if (currentTick <= lastDeviceCommandTick) return false;
        lastDeviceCommandTick = currentTick;
        return true;
    }

    boolean tryRecipe(long currentTick) {
        if (currentTick <= lastRecipeRequestTick) return false;
        lastRecipeRequestTick = currentTick;
        return true;
    }

    boolean tryDeviceGroup(long currentTick) {
        if (currentTick <= lastDeviceGroupRequestTick) return false;
        lastDeviceGroupRequestTick = currentTick;
        return true;
    }

    boolean tryWorkbenchConfiguration(long currentTick,
          QIOWorkbenchConfigurationContainer.RequestStream requestStream) {
        return switch (requestStream) {
            case PRODUCTS -> {
                if (currentTick <= lastWorkbenchProductsRequestTick) yield false;
                lastWorkbenchProductsRequestTick = currentTick;
                yield true;
            }
            case RECIPES -> {
                if (currentTick <= lastWorkbenchRecipesRequestTick) yield false;
                lastWorkbenchRecipesRequestTick = currentTick;
                yield true;
            }
            case CANDIDATES -> {
                if (currentTick <= lastWorkbenchCandidatesRequestTick) yield false;
                lastWorkbenchCandidatesRequestTick = currentTick;
                yield true;
            }
            case COMMANDS -> {
                if (currentTick <= lastWorkbenchCommandTick) yield false;
                lastWorkbenchCommandTick = currentTick;
                yield true;
            }
        };
    }
}
