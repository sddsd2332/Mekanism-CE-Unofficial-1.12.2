package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;

import javax.annotation.Nonnull;

/** Resource-kind filter used by the smart-processing production catalog. */
/**
 * QIO 处理模块中的 QIOSmartProcessingResourceFilter 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public enum QIOSmartProcessingResourceFilter {
    ALL,
    ITEM,
    FLUID,
    GAS;

    public boolean matches(@Nonnull PortableResourceDescriptor resource) {
        return this == ALL || resource.getKind().name().equals(name());
    }

    @Nonnull
    public static QIOSmartProcessingResourceFilter byId(int id) {
        QIOSmartProcessingResourceFilter[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown smart-processing resource filter " + id);
        }
        return values[id];
    }
}
