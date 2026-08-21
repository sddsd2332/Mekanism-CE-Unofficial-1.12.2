package mekanism.qioprocessing.api.processor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable registration describing one concrete family of QIO crafting processor hosts.
 * The resolver may select between registered definitions, but cannot create definitions at runtime.
 */
/**
 * QIO 处理模块中的 QIOCraftingProcessorHostRegistration 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingProcessorHostRegistration<T extends TileEntity> {

    private final ResourceLocation hostId;
    private final String ownerModId;
    private final Class<T> tileClass;
    private final Function<? super T, ResourceLocation> definitionResolver;

    /**
     * 创建一个处理器主机注册描述。
     *
     * @param hostId 主机注册标识
     * @param ownerModId 所属模组 ID
     * @param tileClass 主机方块类型
     * @param definitionResolver 根据方块解析处理器定义的函数
     */
    public QIOCraftingProcessorHostRegistration(@Nonnull ResourceLocation hostId,
          @Nonnull String ownerModId, @Nonnull Class<T> tileClass,
          @Nonnull Function<? super T, ResourceLocation> definitionResolver) {
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.ownerModId = validateOwnerModId(ownerModId);
        if (!hostId.getNamespace().equals(this.ownerModId)) {
            throw new IllegalArgumentException("QIO processor host namespace must match its owner mod id");
        }
        this.tileClass = Objects.requireNonNull(tileClass, "tileClass");
        this.definitionResolver = Objects.requireNonNull(definitionResolver, "definitionResolver");
    }

    /** 为固定处理器定义创建注册描述。 */
    @Nonnull
    public static <T extends TileEntity> QIOCraftingProcessorHostRegistration<T> fixed(
          @Nonnull ResourceLocation hostId, @Nonnull String ownerModId,
          @Nonnull Class<T> tileClass, @Nonnull ResourceLocation definitionId) {
        Objects.requireNonNull(definitionId, "definitionId");
        return new QIOCraftingProcessorHostRegistration<>(hostId, ownerModId, tileClass,
              ignored -> definitionId);
    }

    /** 返回主机注册标识。 */
    @Nonnull
    public ResourceLocation getHostId() {
        return hostId;
    }

    /** 返回所属模组 ID。 */
    @Nonnull
    public String getOwnerModId() {
        return ownerModId;
    }

    /** 返回主机方块类型。 */
    @Nonnull
    public Class<T> getTileClass() {
        return tileClass;
    }

    /** 根据实际方块解析定义标识，并校验类型匹配。 */
    @Nonnull
    ResourceLocation resolveDefinitionId(@Nonnull TileEntity tile) {
        if (!tileClass.isInstance(tile)) {
            throw new IllegalArgumentException("Tile does not match QIO processor host " + hostId);
        }
        ResourceLocation definitionId = definitionResolver.apply(tileClass.cast(tile));
        return Objects.requireNonNull(definitionId,
              "QIO processor definition resolver returned null for " + hostId);
    }

    private static String validateOwnerModId(String ownerModId) {
        String checked = Objects.requireNonNull(ownerModId, "ownerModId").toLowerCase(Locale.ROOT);
        if (!checked.equals(ownerModId) || checked.isEmpty()) {
            throw new IllegalArgumentException("QIO processor owner mod id must be canonical lowercase");
        }
        // ResourceLocation performs Forge/Minecraft's namespace validation.
        new ResourceLocation(checked, "processor_host_validation");
        return checked;
    }
}
