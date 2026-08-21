package mekanism.qioprocessing.common.content.job;

/**
 * QIO 处理模块中的 QIOCraftingJobSource 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public enum QIOCraftingJobSource {
    MANUAL,
    MAINTENANCE,
    MACHINE_REQUEST
}
