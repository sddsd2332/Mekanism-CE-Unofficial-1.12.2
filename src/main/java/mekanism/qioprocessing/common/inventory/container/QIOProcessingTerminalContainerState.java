package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableNBT;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Bounded client mirror of the capability header for one open terminal container. */
public final class QIOProcessingTerminalContainerState {

    private boolean valid;
    @Nullable
    private UUID sessionNonce;
    @Nullable
    private QIOProcessingTerminalSession.TargetKind targetKind;
    @Nullable
    private QIOProcessingTerminalType terminalType;
    @Nullable
    private UUID terminalUUID;
    private long targetRevision;
    @Nullable
    private UUID frequencyUUID;
    private long accessRevision = -1;
    private long deviceDirectoryRevision = -1;
    private long automationRecipeProfileRevision = -1;

    public QIOProcessingTerminalContainerState(@Nonnull MekanismContainer container,
          @Nonnull Supplier<QIOProcessingTerminalSession> sessionSupplier) {
        container.track(SyncableNBT.create(() -> write(sessionSupplier.get()),
              this::read));
    }

    public boolean isValid() {
        return valid;
    }

    @Nullable
    public UUID getSessionNonce() {
        return sessionNonce;
    }

    @Nullable
    public QIOProcessingTerminalSession.TargetKind getTargetKind() {
        return targetKind;
    }

    @Nullable
    public QIOProcessingTerminalType getTerminalType() {
        return terminalType;
    }

    @Nullable
    public UUID getTerminalUUID() {
        return terminalUUID;
    }

    public long getTargetRevision() {
        return targetRevision;
    }

    @Nullable
    public UUID getFrequencyUUID() {
        return frequencyUUID;
    }

    public long getAccessRevision() {
        return accessRevision;
    }

    public long getDeviceDirectoryRevision() {
        return deviceDirectoryRevision;
    }

    public long getAutomationRecipeProfileRevision() {
        return automationRecipeProfileRevision;
    }

    public boolean matches(@Nonnull UUID expectedSessionNonce,
          @Nonnull UUID expectedTerminalUUID, long expectedTargetRevision,
          @Nonnull UUID expectedFrequencyUUID, long expectedAccessRevision) {
        return valid && expectedSessionNonce.equals(sessionNonce) &&
              expectedTerminalUUID.equals(terminalUUID) &&
              targetRevision == expectedTargetRevision &&
              Objects.equals(frequencyUUID, expectedFrequencyUUID) &&
              accessRevision == expectedAccessRevision;
    }

    @Nonnull
    static NBTTagCompound write(@Nullable QIOProcessingTerminalSession session) {
        NBTTagCompound data = new NBTTagCompound();
        if (session == null || !session.isOpen()) {
            return data;
        }
        QIOProcessingNbt.writeUUID(data, "sessionNonce", session.getSessionNonce());
        data.setString("targetKind", session.getTargetKind().name());
        data.setString("terminalType", session.getTerminalType().getSerializedName());
        QIOProcessingNbt.writeUUID(data, "terminalUUID", session.getTerminalUUID());
        data.setLong("targetRevision", session.getTargetRevision());
        if (session.getFrequencyUUID() != null) {
            QIOProcessingNbt.writeUUID(data, "frequencyUUID", session.getFrequencyUUID());
        }
        data.setLong("accessRevision", session.getAccessRevision());
        long directoryRevision = -1;
        long recipeProfileRevision = -1;
        if (session.getFrequencyUUID() != null) {
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
                  session.getFrequencyUUID());
            if (network != null) {
                directoryRevision = network.getAutomationDevices().getRevision();
                recipeProfileRevision = network.getAutomationRecipeProfiles().getRevision();
            }
        }
        data.setLong("deviceDirectoryRevision", directoryRevision);
        data.setLong("automationRecipeProfileRevision", recipeProfileRevision);
        return data;
    }

    void read(@Nonnull NBTTagCompound data) {
        clear();
        try {
            if (!data.hasKey("sessionNonce", NBT.TAG_STRING) ||
                !data.hasKey("targetKind", NBT.TAG_STRING) ||
                !data.hasKey("terminalType", NBT.TAG_STRING) ||
                !data.hasKey("terminalUUID", NBT.TAG_STRING) ||
                !data.hasKey("targetRevision", NBT.TAG_LONG) ||
                !data.hasKey("accessRevision", NBT.TAG_LONG)) {
                return;
            }
            UUID parsedNonce = QIOProcessingNbt.readUUID(data, "sessionNonce");
            QIOProcessingTerminalSession.TargetKind parsedKind =
                  QIOProcessingTerminalSession.TargetKind.valueOf(
                        data.getString("targetKind"));
            QIOProcessingTerminalType parsedType =
                  QIOProcessingTerminalType.bySerializedName(
                        data.getString("terminalType"));
            if (parsedType == null) {
                return;
            }
            UUID parsedTerminalUUID = QIOProcessingNbt.readUUID(data, "terminalUUID");
            long parsedTargetRevision = data.getLong("targetRevision");
            UUID parsedFrequencyUUID = data.hasKey("frequencyUUID", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "frequencyUUID") : null;
            long parsedAccessRevision = data.getLong("accessRevision");
            long parsedDirectoryRevision = data.hasKey("deviceDirectoryRevision", NBT.TAG_LONG) ?
                  data.getLong("deviceDirectoryRevision") : -1;
            long parsedRecipeProfileRevision = data.hasKey(
                  "automationRecipeProfileRevision", NBT.TAG_LONG) ?
                  data.getLong("automationRecipeProfileRevision") : -1;
            if (parsedTargetRevision < 0 ||
                (parsedFrequencyUUID == null ? parsedAccessRevision != -1 :
                      parsedAccessRevision < 0) || parsedDirectoryRevision < -1) {
                return;
            }
            if (parsedRecipeProfileRevision < -1) {
                return;
            }
            sessionNonce = parsedNonce;
            targetKind = parsedKind;
            terminalType = parsedType;
            terminalUUID = parsedTerminalUUID;
            targetRevision = parsedTargetRevision;
            frequencyUUID = parsedFrequencyUUID;
            accessRevision = parsedAccessRevision;
            deviceDirectoryRevision = parsedDirectoryRevision;
            automationRecipeProfileRevision = parsedRecipeProfileRevision;
            valid = true;
        } catch (QIOProcessingDataException | RuntimeException ignored) {
            clear();
        }
    }

    private void clear() {
        valid = false;
        sessionNonce = null;
        targetKind = null;
        terminalType = null;
        terminalUUID = null;
        targetRevision = 0;
        frequencyUUID = null;
        accessRevision = -1;
        deviceDirectoryRevision = -1;
        automationRecipeProfileRevision = -1;
    }
}
