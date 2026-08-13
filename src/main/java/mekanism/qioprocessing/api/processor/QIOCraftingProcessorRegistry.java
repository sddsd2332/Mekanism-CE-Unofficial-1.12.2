package mekanism.qioprocessing.api.processor;

import mekanism.qioprocessing.common.MekanismQIOProcessing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registration boundary shared by built-in and Mekanism-addon workbench processors. */
public final class QIOCraftingProcessorRegistry {

    public static final ResourceLocation ORDINARY_ID = id("ordinary");
    public static final ResourceLocation BASIC_ID = id("basic");
    public static final ResourceLocation ADVANCED_ID = id("advanced");
    public static final ResourceLocation ELITE_ID = id("elite");
    public static final ResourceLocation ULTIMATE_ID = id("ultimate");

    private static final Map<ResourceLocation, QIOCraftingProcessorDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static long registrationLaneLimit = Long.MAX_VALUE;
    private static boolean builtinsRegistered;
    private static boolean frozen;

    private QIOCraftingProcessorRegistry() {
    }

    public static synchronized void bootstrapBuiltins(long laneLimit) {
        if (builtinsRegistered) {
            return;
        }
        setRegistrationLaneLimit(laneLimit);
        register(builtin(ORDINARY_ID, 1));
        register(builtin(BASIC_ID, 3));
        register(builtin(ADVANCED_ID, 5));
        register(builtin(ELITE_ID, 7));
        register(builtin(ULTIMATE_ID, 9));
        builtinsRegistered = true;
    }

    @Nonnull
    public static synchronized QIOCraftingProcessorDefinition register(
          @Nonnull QIOCraftingProcessorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (frozen) {
            throw new IllegalStateException("QIO crafting processor registration is already frozen");
        }
        if (definition.getLaneCount() > registrationLaneLimit) {
            throw new IllegalArgumentException("QIO processor " + definition.getId() + " declares " +
                  definition.getLaneCount() + " lanes, above the configured limit " + registrationLaneLimit);
        }
        QIOCraftingProcessorDefinition previous = DEFINITIONS.putIfAbsent(definition.getId(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate QIO crafting processor definition: " + definition.getId());
        }
        return definition;
    }

    public static synchronized void freeze() {
        if (!builtinsRegistered) {
            throw new IllegalStateException("Built-in QIO crafting processors are not registered");
        }
        frozen = true;
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    @Nullable
    public static synchronized QIOCraftingProcessorDefinition get(ResourceLocation id) {
        return id == null ? null : DEFINITIONS.get(id);
    }

    @Nonnull
    public static synchronized List<QIOCraftingProcessorDefinition> getDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(DEFINITIONS.values()));
    }

    public static synchronized long getRegistrationLaneLimit() {
        return registrationLaneLimit;
    }

    private static void setRegistrationLaneLimit(long laneLimit) {
        if (laneLimit < 9) {
            throw new IllegalArgumentException("QIO processor lane limit cannot be below the built-in ultimate tier");
        }
        registrationLaneLimit = laneLimit;
    }

    static synchronized void resetForTests() {
        DEFINITIONS.clear();
        registrationLaneLimit = Long.MAX_VALUE;
        builtinsRegistered = false;
        frozen = false;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MekanismQIOProcessing.MODID, path);
    }

    private static QIOCraftingProcessorDefinition builtin(ResourceLocation id, long lanes) {
        return new QIOCraftingProcessorDefinition(id, lanes, 50, lanes * 100_000D,
              8, 8, 8);
    }
}
