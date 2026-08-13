package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import mekanism.api.gas.GasStack;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure-data, exact resource projection of one schedulable recipe route. */
public final class QIOPlanningRoute {

    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_RESOURCE_ENTRIES = 4_096;

    private final ProviderKind providerKind;
    private final String providerId;
    private final String routeId;
    private final String recipeKey;
    private final String variantId;
    private final String logicalId;
    private final String stableId;
    private final long routePriority;
    private final long variantPriority;
    private final Map<PortableResourceDescriptor, Long> configurationInputs;
    private final Map<PortableResourceDescriptor, Long> exactInputs;
    private final Map<PortableResourceDescriptor, Long> guaranteedOutputs;
    private final Map<PortableResourceDescriptor, Long> optionalOutputs;
    private final List<QIOCandidateInputGroup> candidateInputs;
    private final String signature;

    private QIOPlanningRoute(Builder builder) {
        providerKind = builder.providerKind;
        providerId = builder.providerId.toString();
        routeId = requireText(builder.routeId, "routeId");
        recipeKey = requireText(builder.recipeKey, "recipeKey");
        variantId = requireText(builder.variantId, "variantId");
        logicalId = providerId + '/' + routeId + '/' + recipeKey;
        stableId = logicalId + '/' + variantId;
        routePriority = builder.routePriority;
        variantPriority = builder.variantPriority;
        configurationInputs = QIOProcessingNbt.copyAmounts(builder.configurationInputs, true,
              "route configuration inputs");
        exactInputs = QIOProcessingNbt.copyAmounts(builder.exactInputs, false, "route inputs");
        guaranteedOutputs = QIOProcessingNbt.copyAmounts(builder.guaranteedOutputs, false,
              "route guaranteed outputs");
        optionalOutputs = QIOProcessingNbt.copyAmounts(builder.optionalOutputs, true,
              "route optional outputs");
        candidateInputs = Collections.unmodifiableList(new ArrayList<>(builder.candidateInputs));
        signature = createSignature();
    }

    private QIOPlanningRoute(QIOPlanningRoute source, long routePriority,
          long variantPriority) {
        providerKind = source.providerKind;
        providerId = source.providerId;
        routeId = source.routeId;
        recipeKey = source.recipeKey;
        variantId = source.variantId;
        logicalId = source.logicalId;
        stableId = source.stableId;
        this.routePriority = routePriority;
        this.variantPriority = variantPriority;
        configurationInputs = source.configurationInputs;
        exactInputs = source.exactInputs;
        guaranteedOutputs = source.guaranteedOutputs;
        optionalOutputs = source.optionalOutputs;
        candidateInputs = source.candidateInputs;
        signature = source.signature;
    }

    @Nonnull
    public static Builder builder(@Nonnull ProviderKind providerKind,
          @Nonnull ResourceLocation providerId, @Nonnull String routeId,
          @Nonnull String recipeKey) {
        return new Builder(providerKind, providerId, routeId, recipeKey);
    }

    @Nonnull
    public static QIOPlanningRoute fromMachineRoute(@Nonnull ResourceLocation providerId,
          @Nonnull MachineRecipeRoute route, long routePriority) {
        Objects.requireNonNull(route, "route");
        Builder builder = builder(ProviderKind.MEKANISM, providerId, route.routeId(),
              route.logicalRecipeKey()).priority(routePriority);
        for (MachineResourceStack input : route.configurationInputs()) {
            builder.configurationInput(describe(input), input.amount());
        }
        for (MachineResourceStack input : route.inputs()) {
            builder.input(describe(input), input.amount());
        }
        for (MachineResourceStack output : route.guaranteedOutputs()) {
            builder.output(describe(output), output.amount());
        }
        for (MachineResourceStack output : route.optionalOutputs()) {
            builder.optionalOutput(describe(output), output.amount());
        }
        return builder.build();
    }

    @Nonnull
    public ProviderKind getProviderKind() {
        return providerKind;
    }

    @Nonnull
    public String getProviderId() {
        return providerId;
    }

    @Nonnull
    public String getRouteId() {
        return routeId;
    }

    @Nonnull
    public String getRecipeKey() {
        return recipeKey;
    }

    @Nonnull
    public String getVariantId() {
        return variantId;
    }

    @Nonnull
    public String getLogicalId() {
        return logicalId;
    }

    @Nonnull
    public String getStableId() {
        return stableId;
    }

    public long getRoutePriority() {
        return routePriority;
    }

    /** Runtime preference among exact variants of the same logical recipe. */
    public long getVariantPriority() {
        return variantPriority;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getExactInputs() {
        return exactInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getConfigurationInputs() {
        return configurationInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getGuaranteedOutputs() {
        return guaranteedOutputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getOptionalOutputs() {
        return optionalOutputs;
    }

    @Nonnull
    public List<QIOCandidateInputGroup> getCandidateInputs() {
        return candidateInputs;
    }

    @Nonnull
    public String getSignature() {
        return signature;
    }

    public long getGuaranteedOutputAmount(PortableResourceDescriptor resource) {
        return guaranteedOutputs.getOrDefault(resource, 0L);
    }

    @Nonnull
    public QIOPlanningRoute withPriority(long priority) {
        if (priority == routePriority) {
            return this;
        }
        return new QIOPlanningRoute(this, priority, variantPriority);
    }

    @Nonnull
    public QIOPlanningRoute withVariantPriority(long priority) {
        if (priority == variantPriority) {
            return this;
        }
        return new QIOPlanningRoute(this, routePriority, priority);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("planningRouteSchemaVersion", SCHEMA_VERSION);
        data.setString("providerKind", providerKind.name());
        data.setString("providerId", providerId);
        data.setString("routeId", routeId);
        data.setString("recipeKey", recipeKey);
        data.setString("variantId", variantId);
        data.setLong("routePriority", routePriority);
        data.setTag("configurationInputs", QIOProcessingNbt.writeAmounts(configurationInputs));
        data.setTag("exactInputs", QIOProcessingNbt.writeAmounts(exactInputs));
        data.setTag("guaranteedOutputs", QIOProcessingNbt.writeAmounts(guaranteedOutputs));
        data.setTag("optionalOutputs", QIOProcessingNbt.writeAmounts(optionalOutputs));
        NBTTagList storedCandidateInputs = new NBTTagList();
        candidateInputs.forEach(group -> storedCandidateInputs.appendTag(group.write()));
        data.setTag("candidateInputs", storedCandidateInputs);
        data.setString("signature", signature);
        return data;
    }

    @Nonnull
    public static QIOPlanningRoute read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            int schema = data.getInteger("planningRouteSchemaVersion");
            if (schema != 2 && schema != SCHEMA_VERSION) {
                throw new QIOProcessingDataException(
                      "Unsupported QIO planning route schema");
            }
            Builder builder = builder(QIOProcessingNbt.readEnum(data, "providerKind",
                        ProviderKind.class), new ResourceLocation(data.getString("providerId")),
                  data.getString("routeId"), data.getString("recipeKey"))
                  .variantId(data.getString("variantId"))
                  .priority(data.getLong("routePriority"));
            if (schema >= 3) {
                QIOProcessingNbt.readAmounts(data, "configurationInputs", MAX_RESOURCE_ENTRIES)
                      .forEach(builder::configurationInput);
            }
            QIOProcessingNbt.readAmounts(data, "exactInputs", MAX_RESOURCE_ENTRIES)
                  .forEach(builder::input);
            QIOProcessingNbt.readAmounts(data, "guaranteedOutputs", MAX_RESOURCE_ENTRIES)
                  .forEach(builder::output);
            QIOProcessingNbt.readAmounts(data, "optionalOutputs", MAX_RESOURCE_ENTRIES)
                  .forEach(builder::optionalOutput);
            if (!data.hasKey("candidateInputs", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException(
                      "Persisted provider route is missing candidate inputs");
            }
            NBTTagList storedCandidateInputs = data.getTagList("candidateInputs",
                  NBT.TAG_COMPOUND);
            if (storedCandidateInputs.tagCount() > 9) {
                throw new QIOProcessingDataException(
                      "Persisted provider route has too many candidate input groups");
            }
            for (int index = 0; index < storedCandidateInputs.tagCount(); index++) {
                builder.candidateInput(QIOCandidateInputGroup.read(
                      storedCandidateInputs.getCompoundTagAt(index)));
            }
            QIOPlanningRoute route = builder.build();
            if (!route.getSignature().equals(data.getString("signature"))) {
                throw new QIOProcessingDataException("Persisted provider route signature disagrees with its contents");
            }
            return route;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid persisted provider route", e);
        }
    }

    private String createSignature() {
        StringBuilder canonical = new StringBuilder(providerKind.name()).append('|')
              .append(providerId).append('|').append(routeId).append('|').append(recipeKey)
              .append('|').append(variantId);
        if (!configurationInputs.isEmpty()) {
            appendAmounts(canonical, "configuration", configurationInputs);
        }
        appendAmounts(canonical, "input", exactInputs);
        appendAmounts(canonical, "output", guaranteedOutputs);
        appendAmounts(canonical, "optional", optionalOutputs);
        for (QIOCandidateInputGroup group : candidateInputs) {
            canonical.append("|candidateGroup=").append(group.getGroupId());
            appendAmounts(canonical, "candidatePlanned", group.getPlannedInputs());
        }
        return QIOHashing.sha256(canonical);
    }

    private static void appendAmounts(StringBuilder canonical, String prefix,
          Map<PortableResourceDescriptor, Long> amounts) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : amounts.entrySet()) {
            canonical.append('|').append(prefix).append('=').append(entry.getKey())
                  .append('@').append(entry.getValue());
        }
    }

    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        Objects.requireNonNull(stack, "route stack");
        return switch (stack.kind()) {
            case ITEM -> {
                ItemStack item = stack.itemStack();
                if (item.isEmpty()) {
                    throw new IllegalArgumentException("Machine route item amount cannot be represented");
                }
                yield PortableResourceDescriptor.item(item);
            }
            case FLUID -> {
                FluidStack fluid = stack.fluidStack();
                if (fluid == null) {
                    throw new IllegalArgumentException("Machine route fluid amount cannot be represented");
                }
                yield PortableResourceDescriptor.fluid(fluid);
            }
            case GAS -> {
                GasStack gas = stack.gasStack();
                if (gas == null) {
                    throw new IllegalArgumentException("Machine route gas amount cannot be represented");
                }
                yield PortableResourceDescriptor.gas(gas);
            }
        };
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 512) {
            throw new IllegalArgumentException(name + " must contain 1..512 characters");
        }
        return trimmed;
    }

    public static final class Builder {

        private final ProviderKind providerKind;
        private final ResourceLocation providerId;
        private final String routeId;
        private final String recipeKey;
        private String variantId = "exact";
        private final Map<PortableResourceDescriptor, Long> configurationInputs = new LinkedHashMap<>();
        private final Map<PortableResourceDescriptor, Long> exactInputs = new LinkedHashMap<>();
        private final Map<PortableResourceDescriptor, Long> guaranteedOutputs = new LinkedHashMap<>();
        private final Map<PortableResourceDescriptor, Long> optionalOutputs = new LinkedHashMap<>();
        private final List<QIOCandidateInputGroup> candidateInputs = new ArrayList<>();
        private long routePriority;
        private long variantPriority;

        private Builder(ProviderKind providerKind, ResourceLocation providerId, String routeId,
              String recipeKey) {
            this.providerKind = Objects.requireNonNull(providerKind, "providerKind");
            this.providerId = Objects.requireNonNull(providerId, "providerId");
            this.routeId = routeId;
            this.recipeKey = recipeKey;
        }

        public Builder priority(long priority) {
            routePriority = priority;
            return this;
        }

        public Builder variantPriority(long priority) {
            variantPriority = priority;
            return this;
        }

        public Builder variantId(@Nonnull String variantId) {
            this.variantId = requireText(variantId, "variantId");
            return this;
        }

        public Builder input(@Nonnull PortableResourceDescriptor resource, long amount) {
            add(exactInputs, resource, amount, "input");
            return this;
        }

        public Builder configurationInput(@Nonnull PortableResourceDescriptor resource,
              long amount) {
            add(configurationInputs, resource, amount, "configuration input");
            return this;
        }

        public Builder output(@Nonnull PortableResourceDescriptor resource, long amount) {
            add(guaranteedOutputs, resource, amount, "output");
            return this;
        }

        public Builder optionalOutput(@Nonnull PortableResourceDescriptor resource, long amount) {
            add(optionalOutputs, resource, amount, "optional output");
            return this;
        }

        public Builder candidateInput(@Nonnull QIOCandidateInputGroup group) {
            if (providerKind != ProviderKind.WORKBENCH) {
                throw new IllegalStateException(
                      "Only workbench routes may declare candidate inputs");
            }
            if (candidateInputs.size() >= 9) {
                throw new IllegalStateException("Too many workbench candidate input groups");
            }
            candidateInputs.add(Objects.requireNonNull(group, "candidate input group"));
            return this;
        }

        @Nonnull
        public QIOPlanningRoute build() {
            return new QIOPlanningRoute(this);
        }

        private static void add(Map<PortableResourceDescriptor, Long> destination,
              PortableResourceDescriptor resource, long amount, String name) {
            Objects.requireNonNull(resource, name + " resource");
            if (amount <= 0) {
                throw new IllegalArgumentException("Route " + name + " amount must be positive");
            }
            try {
                destination.merge(resource, amount, Math::addExact);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Route " + name + " amount overflow", e);
            }
        }
    }
}
