package mekanism.qioprocessing.api.processor;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Immutable identity and logical lane capacity of a QIO workbench processor. */
/**
 * QIO 处理模块中的 QIOCraftingProcessorDefinition 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingProcessorDefinition {

    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    });

    private final ResourceLocation id;
    private final long laneCount;
    private final double baseEnergyUsage;
    private final double energyCapacity;
    private final int speedUpgradeLimit;
    private final int energyUpgradeLimit;
    private final int stackingUpgradeLimit;
    private final String signature;

    /** 使用默认能耗和升级上限创建处理器定义。 */
    public QIOCraftingProcessorDefinition(@Nonnull ResourceLocation id, long laneCount) {
        this(id, laneCount, 50, 100_000, 8, 8, 8);
    }

    /**
     * 创建完整的处理器定义。
     *
     * @param id 定义注册标识
     * @param laneCount 逻辑通道数量
     * @param baseEnergyUsage 每通道基础能耗
     * @param energyCapacity 能量容量
     * @param speedUpgradeLimit 速度升级上限
     * @param energyUpgradeLimit 节能升级上限
     * @param stackingUpgradeLimit 堆叠升级上限
     */
    public QIOCraftingProcessorDefinition(@Nonnull ResourceLocation id, long laneCount,
          double baseEnergyUsage, double energyCapacity, int speedUpgradeLimit,
          int energyUpgradeLimit, int stackingUpgradeLimit) {
        this.id = Objects.requireNonNull(id, "id");
        if (laneCount <= 0) {
            throw new IllegalArgumentException("QIO processor lane count must be positive");
        }
        if (!Double.isFinite(baseEnergyUsage) || baseEnergyUsage <= 0 ||
              !Double.isFinite(energyCapacity) || energyCapacity < baseEnergyUsage) {
            throw new IllegalArgumentException("QIO processor energy values are invalid");
        }
        if (speedUpgradeLimit < 0 || energyUpgradeLimit < 0 || stackingUpgradeLimit < 0) {
            throw new IllegalArgumentException("QIO processor upgrade limits cannot be negative");
        }
        this.laneCount = laneCount;
        this.baseEnergyUsage = baseEnergyUsage;
        this.energyCapacity = energyCapacity;
        this.speedUpgradeLimit = speedUpgradeLimit;
        this.energyUpgradeLimit = energyUpgradeLimit;
        this.stackingUpgradeLimit = stackingUpgradeLimit;
        signature = createSignature(id, laneCount, baseEnergyUsage, energyCapacity,
              speedUpgradeLimit, energyUpgradeLimit, stackingUpgradeLimit);
    }

    /** 返回处理器定义标识。 */
    @Nonnull
    public ResourceLocation getId() {
        return id;
    }

    /** 返回逻辑通道数量。 */
    public long getLaneCount() {
        return laneCount;
    }

    /** 返回基础能耗。 */
    public double getBaseEnergyUsage() {
        return baseEnergyUsage;
    }

    /** 返回能量容量。 */
    public double getEnergyCapacity() {
        return energyCapacity;
    }

    /** 返回速度升级上限。 */
    public int getSpeedUpgradeLimit() {
        return speedUpgradeLimit;
    }

    /** 返回节能升级上限。 */
    public int getEnergyUpgradeLimit() {
        return energyUpgradeLimit;
    }

    /** 返回堆叠升级上限。 */
    public int getStackingUpgradeLimit() {
        return stackingUpgradeLimit;
    }

    /** 返回由全部定义字段计算出的稳定签名。 */
    @Nonnull
    public String getSignature() {
        return signature;
    }

    private static String createSignature(ResourceLocation id, long laneCount,
          double baseEnergyUsage, double energyCapacity, int speedUpgradeLimit,
          int energyUpgradeLimit, int stackingUpgradeLimit) {
        String canonical = id + "|lanes=" + laneCount + "|usage=" +
              Double.toHexString(baseEnergyUsage) + "|capacity=" +
              Double.toHexString(energyCapacity) + "|speed=" + speedUpgradeLimit +
              "|energy=" + energyUpgradeLimit + "|stacking=" + stackingUpgradeLimit;
        MessageDigest digest = SHA_256.get();
        digest.reset();
        byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int current = bytes[index] & 0xFF;
            encoded[index * 2] = LOWER_HEX[current >>> 4];
            encoded[index * 2 + 1] = LOWER_HEX[current & 0x0F];
        }
        return new String(encoded);
    }
}
