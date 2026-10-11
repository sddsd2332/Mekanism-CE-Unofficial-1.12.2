package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.qioprocessing.api.machine.QIORecipeFamilyRegistry;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Logical recipe family for policy entries; never a device or transaction identity.
 */

/**
 * QIO 处理模块中的 QIOAutomationRecipeProfileScope 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationRecipeProfileScope {

    private static final int MAX_SCOPE_ID_LENGTH = 1_536;

    private QIOAutomationRecipeProfileScope() {
    }

    /** Single machines and factories using the same declared recipe source share policy entries. */
    @Nonnull
    public static String resolve(@Nonnull MachineRecipeProviderRegistry.BoundProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String family = QIORecipeFamilyRegistry.resolve(provider.getRecipeSourceKey());
        if (family != null) {
            String discriminator = provider.getRecipeProfileScopeDiscriminator();
            String scope = discriminator.isEmpty() ? family :
                    family.length() + ":" + family + discriminator;
            if (scope.length() > MAX_SCOPE_ID_LENGTH) {
                throw new IllegalArgumentException("QIO recipe family is too long");
            }
            return scope;
        }
        // Unknown integrations remain isolated until they explicitly declare a shared source.
        return resolve(provider.tile(), provider.id(),
                provider.getRecipeProfileScopeDiscriminator());
    }

    @Nonnull
    static String resolve(@Nonnull TileEntity tile, @Nonnull ResourceLocation providerId) {
        return resolve(tile, providerId, "");
    }

    private static String resolve(@Nonnull TileEntity tile,
                                  @Nonnull ResourceLocation providerId, @Nonnull String discriminator) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(providerId, "providerId");
        Block block = safeBlock(tile);
        ResourceLocation blockId = block == null ? null : block.getRegistryName();
        StringBuilder scope = new StringBuilder(providerId.toString()).append('|')
                .append(blockId == null ? tile.getClass().getName() : blockId.toString());
        if (blockId != null) {
            int metadata = safeMetadata(tile);
            MachineType machineType = MachineType.get(block, metadata);
            scope.append('/').append(machineType == null ? Integer.toString(metadata) :
                    machineType.getName());
        }
        if (tile instanceof TileEntityFactory factory) {
            scope.append('/').append(factory.getRecipeType().getName());
        }
        if (!discriminator.isEmpty()) {
            scope.append("/scope/").append(discriminator);
        }
        String resolved = scope.toString();
        if (resolved.length() > MAX_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException("QIO automation profile scope id is too long");
        }
        return resolved;
    }

    private static Block safeBlock(TileEntity tile) {
        try {
            return tile.getBlockType();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int safeMetadata(TileEntity tile) {
        try {
            return Math.max(0, tile.getBlockMetadata());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
