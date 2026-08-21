package mekanism.qioprocessing.common.tile;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

/** Shared inheritance point for the four built-in tiered variants. */
/**
 * QIO 处理模块中的 TieredQIOCraftingProcessor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public abstract class TieredQIOCraftingProcessor extends QIOCraftingProcessor {

    protected TieredQIOCraftingProcessor(@Nonnull String name, @Nonnull ResourceLocation hostId,
          @Nonnull ResourceLocation definitionId) {
        super(name, hostId, definitionId);
    }
}
