package mekanism.qioprocessing.common.content.device;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Persistent last-known management view of one QIO Processing device. */
public final class QIOAutomationDeviceSnapshot {

    public enum Kind {
        AUTOMATION_MACHINE,
        CRAFTING_PROCESSOR,
        TERMINAL
    }

    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_PROFILE_SCOPE_ID_LENGTH = 1_536;
    private static final int MAX_DIAGNOSTIC_LENGTH = 512;

    private final UUID deviceUUID;
    private final QIOAutomationDeviceLocation location;
    private final Kind kind;
    private final String blockId;
    private final int blockMetadata;
    private final MachinePresentationDescriptor presentation;
    private final String providerId;
    private final String profileScopeId;
    private final String modeName;
    private final String stateName;
    private final boolean online;
    private final long lastSeenTick;
    private final long configurationRevision;
    private final int providerConfigurationRevision;
    private final long routeCount;
    private final int activeOperationCount;
    private final boolean managementPaused;
    private final boolean recipeProfileAvailable;
    private final boolean individualRecipeProfile;
    private final int globalRecipeProfileSlot;
    @Nullable
    private final RouteFilterMode recipeRouteFilterMode;
    private final String diagnostic;

    public QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull String blockId,
          int blockMetadata, @Nonnull String providerId, @Nonnull QIOAutomationMode mode,
          @Nonnull QIOAutomationHost.State state, boolean online, long lastSeenTick,
          long configurationRevision, int providerConfigurationRevision, int routeCount,
          int activeOperationCount, @Nullable String diagnostic) {
        this(deviceUUID, location, Kind.AUTOMATION_MACHINE, blockId, blockMetadata,
              legacyPresentation(blockId, blockMetadata), providerId, providerId,
              mode.name(), state.name(), online, lastSeenTick,
              configurationRevision, providerConfigurationRevision, routeCount,
              activeOperationCount, false, diagnostic);
    }

    public QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull String blockId,
          int blockMetadata, @Nonnull String providerId, @Nonnull String profileScopeId,
          @Nonnull QIOAutomationMode mode, @Nonnull QIOAutomationHost.State state,
          boolean online, long lastSeenTick, long configurationRevision,
          int providerConfigurationRevision, int routeCount, int activeOperationCount,
          @Nullable String diagnostic) {
        this(deviceUUID, location, Kind.AUTOMATION_MACHINE, blockId, blockMetadata,
              legacyPresentation(blockId, blockMetadata), providerId, profileScopeId,
              mode.name(), state.name(), online, lastSeenTick,
              configurationRevision, providerConfigurationRevision, routeCount,
              activeOperationCount, false, diagnostic);
    }

    public QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull Kind kind,
          @Nonnull String blockId, int blockMetadata, @Nonnull String implementationId,
          @Nonnull String modeName, @Nonnull String stateName, boolean online,
          long lastSeenTick, long configurationRevision,
          int providerConfigurationRevision, long routeCount,
          int activeOperationCount, @Nullable String diagnostic) {
        this(deviceUUID, location, kind, blockId, blockMetadata,
              legacyPresentation(blockId, blockMetadata), implementationId,
              implementationId, modeName, stateName, online, lastSeenTick,
              configurationRevision,
              providerConfigurationRevision, routeCount, activeOperationCount, false,
              diagnostic);
    }

    public QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull Kind kind,
          @Nonnull String blockId, int blockMetadata, @Nonnull String implementationId,
          @Nonnull String modeName, @Nonnull String stateName, boolean online,
          long lastSeenTick, long configurationRevision,
          int providerConfigurationRevision, long routeCount,
          int activeOperationCount, boolean managementPaused, @Nullable String diagnostic) {
        this(deviceUUID, location, kind, blockId, blockMetadata,
              legacyPresentation(blockId, blockMetadata), implementationId,
              implementationId, modeName, stateName, online, lastSeenTick,
              configurationRevision, providerConfigurationRevision, routeCount,
              activeOperationCount, managementPaused, diagnostic);
    }

    public QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull Kind kind,
          @Nonnull String blockId, int blockMetadata, @Nonnull String implementationId,
          @Nonnull String profileScopeId, @Nonnull String modeName,
          @Nonnull String stateName, boolean online, long lastSeenTick,
          long configurationRevision, int providerConfigurationRevision, long routeCount,
          int activeOperationCount, boolean managementPaused, @Nullable String diagnostic) {
        this(deviceUUID, location, kind, blockId, blockMetadata,
              legacyPresentation(blockId, blockMetadata), implementationId,
              profileScopeId, modeName, stateName, online, lastSeenTick,
              configurationRevision, providerConfigurationRevision, routeCount,
              activeOperationCount, managementPaused, false, false, 0, null, diagnostic);
    }

    public QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull Kind kind,
          @Nonnull String blockId, int blockMetadata,
          @Nonnull MachinePresentationDescriptor presentation,
          @Nonnull String implementationId, @Nonnull String profileScopeId,
          @Nonnull String modeName, @Nonnull String stateName, boolean online,
          long lastSeenTick, long configurationRevision,
          int providerConfigurationRevision, long routeCount,
          int activeOperationCount, boolean managementPaused,
          @Nullable String diagnostic) {
        this(deviceUUID, location, kind, blockId, blockMetadata, presentation,
              implementationId, profileScopeId, modeName, stateName, online,
              lastSeenTick, configurationRevision, providerConfigurationRevision,
              routeCount, activeOperationCount, managementPaused, false, false, 0,
              null, diagnostic);
    }

    private QIOAutomationDeviceSnapshot(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation location, @Nonnull Kind kind,
          @Nonnull String blockId, int blockMetadata,
          @Nonnull MachinePresentationDescriptor presentation,
          @Nonnull String implementationId,
          @Nonnull String profileScopeId, @Nonnull String modeName,
          @Nonnull String stateName, boolean online, long lastSeenTick,
          long configurationRevision, int providerConfigurationRevision, long routeCount,
          int activeOperationCount, boolean managementPaused, boolean recipeProfileAvailable,
          boolean individualRecipeProfile, int globalRecipeProfileSlot,
          @Nullable RouteFilterMode recipeRouteFilterMode, @Nullable String diagnostic) {
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.location = Objects.requireNonNull(location, "location");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.blockId = requireId(blockId, "blockId");
        if (blockMetadata < 0 || blockMetadata > 15) {
            throw new IllegalArgumentException("blockMetadata must be in the range 0..15");
        }
        this.blockMetadata = blockMetadata;
        this.presentation = MachinePresentationDescriptor.read(
              Objects.requireNonNull(presentation, "presentation").write());
        this.providerId = requireId(implementationId, "implementationId");
        this.profileScopeId = requireProfileScopeId(profileScopeId);
        this.modeName = requireId(modeName, "modeName");
        this.stateName = requireId(stateName, "stateName");
        this.online = online;
        this.lastSeenTick = QIOProcessingNbt.requireNonNegative(lastSeenTick, "lastSeenTick");
        this.configurationRevision = QIOProcessingNbt.requireNonNegative(configurationRevision,
              "configurationRevision");
        if (providerConfigurationRevision < 0 || routeCount < 0 || activeOperationCount < 0) {
            throw new IllegalArgumentException("Negative QIO device snapshot counters");
        }
        this.providerConfigurationRevision = providerConfigurationRevision;
        this.routeCount = routeCount;
        this.activeOperationCount = activeOperationCount;
        this.managementPaused = managementPaused;
        if (recipeProfileAvailable) {
            if (kind != Kind.AUTOMATION_MACHINE || globalRecipeProfileSlot < 1 ||
                globalRecipeProfileSlot > 10 || recipeRouteFilterMode == null) {
                throw new IllegalArgumentException("Invalid QIO recipe profile selection");
            }
            QIOAutomationMode mode = QIOAutomationMode.valueOf(this.modeName);
            if (mode == QIOAutomationMode.OUTPUT_ONLY ||
                mode == QIOAutomationMode.PASSIVE &&
                      recipeRouteFilterMode != RouteFilterMode.WHITELIST) {
                throw new IllegalArgumentException("Invalid QIO recipe profile mode");
            }
        } else if (individualRecipeProfile || globalRecipeProfileSlot != 0 ||
                   recipeRouteFilterMode != null) {
            throw new IllegalArgumentException("Unexpected QIO recipe profile selection");
        }
        this.recipeProfileAvailable = recipeProfileAvailable;
        this.individualRecipeProfile = individualRecipeProfile;
        this.globalRecipeProfileSlot = globalRecipeProfileSlot;
        this.recipeRouteFilterMode = recipeRouteFilterMode;
        this.diagnostic = boundedDiagnostic(diagnostic);
    }

    @Nonnull
    public UUID getDeviceUUID() {
        return deviceUUID;
    }

    @Nonnull
    public QIOAutomationDeviceLocation getLocation() {
        return location;
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    @Nonnull
    public String getBlockId() {
        return blockId;
    }

    public int getBlockMetadata() {
        return blockMetadata;
    }

    @Nonnull
    public MachinePresentationDescriptor getPresentation() {
        return MachinePresentationDescriptor.read(presentation.write());
    }

    @Nonnull
    public String getProviderId() {
        return providerId;
    }

    @Nonnull
    public String getProfileScopeId() {
        return profileScopeId;
    }

    @Nullable
    public QIOAutomationMode getMode() {
        return kind == Kind.AUTOMATION_MACHINE ?
              QIOAutomationMode.valueOf(modeName) : null;
    }

    @Nullable
    public QIOAutomationHost.State getState() {
        return kind == Kind.AUTOMATION_MACHINE ?
              QIOAutomationHost.State.valueOf(stateName) : null;
    }

    @Nonnull
    public String getModeName() {
        return modeName;
    }

    @Nonnull
    public String getStateName() {
        return stateName;
    }

    public boolean isOnline() {
        return online;
    }

    public long getLastSeenTick() {
        return lastSeenTick;
    }

    public long getConfigurationRevision() {
        return configurationRevision;
    }

    public int getProviderConfigurationRevision() {
        return providerConfigurationRevision;
    }

    public long getRouteCount() {
        return routeCount;
    }

    public int getActiveOperationCount() {
        return activeOperationCount;
    }

    public boolean isManagementPaused() {
        return managementPaused;
    }

    public boolean hasRecipeProfile() {
        return recipeProfileAvailable;
    }

    public boolean isIndividualRecipeProfile() {
        return individualRecipeProfile;
    }

    public int getGlobalRecipeProfileSlot() {
        return globalRecipeProfileSlot;
    }

    @Nullable
    public RouteFilterMode getRecipeRouteFilterMode() {
        return recipeRouteFilterMode;
    }

    @Nonnull
    public String getDiagnostic() {
        return diagnostic;
    }

    @Nonnull
    public QIOAutomationDeviceSnapshot withRecipeProfileSelection(boolean individual,
          int globalSlot, @Nonnull RouteFilterMode filterMode) {
        return new QIOAutomationDeviceSnapshot(deviceUUID, location, kind, blockId,
              blockMetadata, presentation, providerId, profileScopeId, modeName,
              stateName, online,
              lastSeenTick, configurationRevision, providerConfigurationRevision, routeCount,
              activeOperationCount, managementPaused, true, individual, globalSlot,
              Objects.requireNonNull(filterMode, "filterMode"), diagnostic);
    }

    @Nonnull
    public QIOAutomationDeviceSnapshot offline(long currentTick) {
        long seen = Math.max(lastSeenTick,
              QIOProcessingNbt.requireNonNegative(currentTick, "currentTick"));
        return new QIOAutomationDeviceSnapshot(deviceUUID, location, kind, blockId,
              blockMetadata, presentation, providerId, profileScopeId, modeName,
              stateName, false, seen,
              configurationRevision,
              providerConfigurationRevision, routeCount, activeOperationCount,
              managementPaused, recipeProfileAvailable, individualRecipeProfile,
              globalRecipeProfileSlot, recipeRouteFilterMode, diagnostic);
    }

    @Nonnull
    public QIOAutomationDeviceSnapshot withManagementState(boolean paused,
          long updatedConfigurationRevision, long currentTick) {
        return new QIOAutomationDeviceSnapshot(deviceUUID, location, kind, blockId,
              blockMetadata, presentation, providerId, profileScopeId, modeName,
              stateName, true,
              Math.max(lastSeenTick, QIOProcessingNbt.requireNonNegative(currentTick,
                    "currentTick")), updatedConfigurationRevision,
              providerConfigurationRevision, routeCount, activeOperationCount, paused,
              recipeProfileAvailable, individualRecipeProfile, globalRecipeProfileSlot,
              recipeRouteFilterMode, diagnostic);
    }

    /** Ignores lastSeen while a device remains online to avoid periodic persistence churn. */
    public boolean sameLiveObservation(@Nullable QIOAutomationDeviceSnapshot other) {
        return other != null && online && other.online && deviceUUID.equals(other.deviceUUID) &&
              location.equals(other.location) && blockId.equals(other.blockId) &&
              blockMetadata == other.blockMetadata && providerId.equals(other.providerId) &&
              presentation.equals(other.presentation) &&
              profileScopeId.equals(other.profileScopeId) &&
              kind == other.kind && modeName.equals(other.modeName) &&
              stateName.equals(other.stateName) &&
              configurationRevision == other.configurationRevision &&
              providerConfigurationRevision == other.providerConfigurationRevision &&
              routeCount == other.routeCount && activeOperationCount == other.activeOperationCount &&
              managementPaused == other.managementPaused &&
              recipeProfileAvailable == other.recipeProfileAvailable &&
              individualRecipeProfile == other.individualRecipeProfile &&
              globalRecipeProfileSlot == other.globalRecipeProfileSlot &&
              recipeRouteFilterMode == other.recipeRouteFilterMode &&
              diagnostic.equals(other.diagnostic);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        data.setTag("location", location.write());
        data.setString("kind", kind.name());
        data.setString("blockId", blockId);
        data.setInteger("blockMetadata", blockMetadata);
        data.setTag("presentation", presentation.write());
        data.setString("providerId", providerId);
        data.setString("profileScopeId", profileScopeId);
        data.setString("mode", modeName);
        data.setString("state", stateName);
        data.setBoolean("online", online);
        data.setLong("lastSeenTick", lastSeenTick);
        data.setLong("configurationRevision", configurationRevision);
        data.setInteger("providerConfigurationRevision", providerConfigurationRevision);
        data.setLong("routeCount", routeCount);
        data.setInteger("activeOperationCount", activeOperationCount);
        data.setBoolean("managementPaused", managementPaused);
        data.setBoolean("recipeProfileAvailable", recipeProfileAvailable);
        data.setBoolean("individualRecipeProfile", individualRecipeProfile);
        data.setInteger("globalRecipeProfileSlot", globalRecipeProfileSlot);
        data.setString("recipeRouteFilterMode", recipeRouteFilterMode == null ? "" :
              recipeRouteFilterMode.name());
        if (!diagnostic.isEmpty()) {
            data.setString("diagnostic", diagnostic);
        }
        return data;
    }

    @Nonnull
    public static QIOAutomationDeviceSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (!data.hasKey("kind", net.minecraftforge.common.util.Constants.NBT.TAG_STRING) ||
                  !data.hasKey("routeCount", net.minecraftforge.common.util.Constants.NBT.TAG_LONG) ||
                  !data.hasKey("profileScopeId",
                        net.minecraftforge.common.util.Constants.NBT.TAG_STRING) ||
                  !data.hasKey("recipeProfileAvailable",
                        net.minecraftforge.common.util.Constants.NBT.TAG_BYTE) ||
                  !data.hasKey("individualRecipeProfile",
                        net.minecraftforge.common.util.Constants.NBT.TAG_BYTE) ||
                  !data.hasKey("globalRecipeProfileSlot",
                        net.minecraftforge.common.util.Constants.NBT.TAG_INT) ||
                  !data.hasKey("recipeRouteFilterMode",
                        net.minecraftforge.common.util.Constants.NBT.TAG_STRING)) {
                throw new QIOProcessingDataException(
                      "QIO automation device snapshot is missing current-schema fields");
            }
            Kind kind = QIOProcessingNbt.readEnum(data, "kind", Kind.class);
            long routeCount = data.getLong("routeCount");
            boolean profileAvailable = data.getBoolean("recipeProfileAvailable");
            RouteFilterMode filterMode = profileAvailable ? RouteFilterMode.valueOf(
                  data.getString("recipeRouteFilterMode")) : null;
            MachinePresentationDescriptor presentation = readPresentation(data,
                  data.getString("blockId"), data.getInteger("blockMetadata"));
            return new QIOAutomationDeviceSnapshot(QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  QIOAutomationDeviceLocation.read(data.getCompoundTag("location")),
                  kind, data.getString("blockId"), data.getInteger("blockMetadata"),
                  presentation, data.getString("providerId"),
                  data.getString("profileScopeId"),
                  data.getString("mode"), data.getString("state"),
                  data.getBoolean("online"), data.getLong("lastSeenTick"),
                  data.getLong("configurationRevision"),
                  data.getInteger("providerConfigurationRevision"),
                  routeCount, data.getInteger("activeOperationCount"),
                  data.getBoolean("managementPaused"),
                  profileAvailable, data.getBoolean("individualRecipeProfile"),
                  data.getInteger("globalRecipeProfileSlot"), filterMode,
                  data.getString("diagnostic"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation device snapshot", e);
        }
    }

    private static String requireId(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty() || checked.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must contain 1.." + MAX_ID_LENGTH +
                  " characters");
        }
        return checked;
    }

    private static MachinePresentationDescriptor readPresentation(NBTTagCompound data,
          String blockId, int blockMetadata) {
        if (data.hasKey("presentation",
              net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
            try {
                return MachinePresentationDescriptor.read(
                      data.getCompoundTag("presentation"));
            } catch (RuntimeException ignored) {
                // A damaged optional presentation must not discard the device directory.
            }
        }
        return legacyPresentation(blockId, blockMetadata);
    }

    private static MachinePresentationDescriptor legacyPresentation(String blockId,
          int blockMetadata) {
        return MachinePresentationDescriptor.of(blockId, blockMetadata, null);
    }

    private static String requireProfileScopeId(String value) {
        String checked = Objects.requireNonNull(value, "profileScopeId").trim();
        if (checked.isEmpty() || checked.length() > MAX_PROFILE_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException(
                  "profileScopeId must contain 1.." + MAX_PROFILE_SCOPE_ID_LENGTH +
                        " characters");
        }
        return checked;
    }

    private static String boundedDiagnostic(@Nullable String value) {
        String checked = value == null ? "" : value.trim();
        return checked.substring(0, Math.min(MAX_DIAGNOSTIC_LENGTH, checked.length()));
    }
}
