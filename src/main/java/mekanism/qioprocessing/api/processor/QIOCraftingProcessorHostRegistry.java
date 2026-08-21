package mekanism.qioprocessing.api.processor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit, frozen registration boundary for built-in and addon processor TileEntity families. */
/**
 * QIO 处理模块中的 QIOCraftingProcessorHostRegistry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingProcessorHostRegistry {

    public static final class ResolvedHost {

        private final QIOCraftingProcessorHostRegistration<?> registration;
        private final QIOCraftingProcessorDefinition definition;

        private ResolvedHost(QIOCraftingProcessorHostRegistration<?> registration,
              QIOCraftingProcessorDefinition definition) {
            this.registration = registration;
            this.definition = definition;
        }

        /** 返回已解析主机的注册标识。 */
        @Nonnull
        public ResourceLocation getHostId() {
            return registration.getHostId();
        }

        /** 返回已解析主机的所属模组。 */
        @Nonnull
        public String getOwnerModId() {
            return registration.getOwnerModId();
        }

        /** 返回已解析主机的方块类型。 */
        @Nonnull
        public Class<? extends TileEntity> getTileClass() {
            return registration.getTileClass();
        }

        /** 返回已解析的处理器定义。 */
        @Nonnull
        public QIOCraftingProcessorDefinition getDefinition() {
            return definition;
        }
    }

    private static final Map<ResourceLocation, QIOCraftingProcessorHostRegistration<?>> BY_ID =
          new LinkedHashMap<>();
    private static final Map<Class<? extends TileEntity>, QIOCraftingProcessorHostRegistration<?>> BY_TILE =
          new LinkedHashMap<>();
    private static boolean frozen;

    private QIOCraftingProcessorHostRegistry() {
    }

    /** 注册一个主机描述；注册冻结后或重复标识会抛出异常。 */
    @Nonnull
    public static synchronized <T extends TileEntity> QIOCraftingProcessorHostRegistration<T> register(
          @Nonnull QIOCraftingProcessorHostRegistration<T> registration) {
        Objects.requireNonNull(registration, "registration");
        if (frozen) {
            throw new IllegalStateException("QIO crafting processor host registration is already frozen");
        }
        if (BY_ID.containsKey(registration.getHostId())) {
            throw new IllegalArgumentException("Duplicate QIO crafting processor host id: " +
                  registration.getHostId());
        }
        if (BY_TILE.containsKey(registration.getTileClass())) {
            throw new IllegalArgumentException("Duplicate QIO crafting processor TileEntity registration: " +
                  registration.getTileClass().getName());
        }
        BY_ID.put(registration.getHostId(), registration);
        BY_TILE.put(registration.getTileClass(), registration);
        return registration;
    }

    /** 冻结注册表，阻止运行时继续新增主机。 */
    public static synchronized void freeze() {
        frozen = true;
    }

    /** 返回注册表是否已经冻结。 */
    public static synchronized boolean isFrozen() {
        return frozen;
    }

    /** 按主机标识查找注册描述。 */
    @Nullable
    public static synchronized QIOCraftingProcessorHostRegistration<?> get(ResourceLocation hostId) {
        return hostId == null ? null : BY_ID.get(hostId);
    }

    /** 返回当前注册描述的不可修改快照。 */
    @Nonnull
    public static synchronized List<QIOCraftingProcessorHostRegistration<?>> getRegistrations() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    /** 根据方块实际类型解析最具体的处理器主机及其定义。 */
    @Nullable
    public static synchronized ResolvedHost resolve(@Nullable TileEntity tile) {
        if (tile == null) {
            return null;
        }
        QIOCraftingProcessorHostRegistration<?> selected = null;
        Class<?> actualClass = tile.getClass();
        for (QIOCraftingProcessorHostRegistration<?> candidate : BY_ID.values()) {
            Class<? extends TileEntity> candidateClass = candidate.getTileClass();
            if (!candidateClass.isAssignableFrom(actualClass)) {
                continue;
            }
            if (selected == null || selected.getTileClass().isAssignableFrom(candidateClass)) {
                selected = candidate;
            }
        }
        if (selected == null) {
            return null;
        }
        ResourceLocation definitionId = selected.resolveDefinitionId(tile);
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(definitionId);
        if (definition == null) {
            throw new IllegalStateException("QIO processor host " + selected.getHostId() +
                  " resolved an unregistered definition " + definitionId);
        }
        return new ResolvedHost(selected, definition);
    }

    static synchronized void resetForTests() {
        BY_ID.clear();
        BY_TILE.clear();
        frozen = false;
    }
}
