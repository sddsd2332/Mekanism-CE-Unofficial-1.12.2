package mekanism.qioprocessing.api.processor;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Immutable identity and logical lane capacity of a QIO workbench processor. */
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

    public QIOCraftingProcessorDefinition(@Nonnull ResourceLocation id, long laneCount) {
        this(id, laneCount, 50, 100_000, 8, 8, 8);
    }

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

    @Nonnull
    public ResourceLocation getId() {
        return id;
    }

    public long getLaneCount() {
        return laneCount;
    }

    public double getBaseEnergyUsage() {
        return baseEnergyUsage;
    }

    public double getEnergyCapacity() {
        return energyCapacity;
    }

    public int getSpeedUpgradeLimit() {
        return speedUpgradeLimit;
    }

    public int getEnergyUpgradeLimit() {
        return energyUpgradeLimit;
    }

    public int getStackingUpgradeLimit() {
        return stackingUpgradeLimit;
    }

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
