package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.Frequency;
import mekanism.common.inventory.container.MekanismContainer.ISpecificContainerTracker;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableFrequencyList;
import mekanism.common.inventory.container.sync.SyncableNBT;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.api.NBTConstants;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Container-side client mirror for one machine's QIO automation binding. */
/**
 * QIO 处理模块中的 QIOAutomationContainerState 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationContainerState implements ISpecificContainerTracker {

    public static final ResourceLocation EXTENSION_ID = new ResourceLocation("mekanismqioprocessing",
          "automation_binding");
    public static final int TRACKING_KEY = 0x51494F;
    private static boolean registered;

    private final MekanismTileContainer<?> container;
    private final TileEntity tile;
    private final Map<SecurityMode, List<QIOFrequency>> frequencyLists = new EnumMap<>(SecurityMode.class);
    @Nullable
    private QIOFrequencyReference reference;
    @Nullable
    private QIOAutomationMode mode;
    private QIOAutomationHost.State state = QIOAutomationHost.State.UNBOUND;
    private QIOAutomationHost.RecoveryState recoveryState = QIOAutomationHost.RecoveryState.NONE;
    private boolean recoveryPending;
    @Nullable
    private String recoveryDiagnostic;
    @Nullable
    private QIOFrequency displayFrequency;

    QIOAutomationContainerState(MekanismTileContainer<?> container, TileEntity tile) {
        this.container = container;
        this.tile = tile;
        for (SecurityMode securityMode : SecurityMode.values()) {
            frequencyLists.put(securityMode, Collections.emptyList());
        }
        container.track(SyncableNBT.create(this::writeSummary, this::readSummary));
    }

    public static synchronized void registerContainerExtension() {
        if (registered) {
            return;
        }
        mekanism.common.inventory.container.MekanismTileContainerExtensionRegistry.register(EXTENSION_ID,
              (container, tile) -> QIOAutomationCapabilities.AUTOMATION_HOST != null &&
                    tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null) ?
                    new QIOAutomationContainerState(container, tile) : null);
        registered = true;
    }

    @Nullable
    public static QIOAutomationContainerState get(MekanismTileContainer<?> container) {
        return container == null ? null : container.getExtension(EXTENSION_ID,
              QIOAutomationContainerState.class);
    }

    @Nullable
    public QIOFrequencyReference getReference() {
        return reference;
    }

    @Nullable
    public QIOAutomationMode getMode() {
        return mode;
    }

    @Nonnull
    public QIOAutomationHost.State getState() {
        return state;
    }

    @Nonnull
    public QIOAutomationHost.RecoveryState getRecoveryState() {
        return recoveryState;
    }

    /** True only when the server has retained an ownership quarantine that may be force-cleared. */
    public boolean isRecoveryQuarantined() {
        return recoveryState == QIOAutomationHost.RecoveryState.QUARANTINED;
    }

    public boolean hasRecoveryPending() {
        return recoveryPending;
    }

    @Nullable
    public String getRecoveryDiagnostic() {
        return recoveryDiagnostic;
    }

    @Nullable
    public QIOFrequency getFrequency() {
        if (reference == null) {
            return null;
        }
        for (QIOFrequency frequency : getFrequencies(reference.getSecurityMode())) {
            if (matchesReference(frequency, reference)) {
                return frequency;
            }
        }
        return displayFrequency;
    }

    @Nonnull
    public List<QIOFrequency> getFrequencies(SecurityMode securityMode) {
        return frequencyLists.getOrDefault(securityMode, Collections.emptyList());
    }

    @Nonnull
    public void startClientTracking() {
        container.stopTracking(TRACKING_KEY);
        container.startTracking(TRACKING_KEY, this);
    }

    public void stopClientTracking() {
        container.stopTracking(TRACKING_KEY);
    }

    @Override
    public List<ISyncableData> getSpecificSyncableData() {
        List<ISyncableData> tracked = new ArrayList<>(3);
        for (SecurityMode securityMode : SecurityMode.values()) {
            tracked.add(SyncableFrequencyList.create(FrequencyType.QIO,
                  () -> serverFrequencies(securityMode),
                  frequencies -> frequencyLists.put(securityMode,
                        Collections.unmodifiableList(new ArrayList<>(frequencies)))));
        }
        return tracked;
    }

    NBTTagCompound writeSummary() {
        QIOAutomationHost host = QIOAutomationCapabilities.AUTOMATION_HOST == null ? null :
              tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        return writeSummary(host);
    }

    NBTTagCompound writeSummary(@Nullable QIOAutomationHost host) {
        NBTTagCompound data = new NBTTagCompound();
        if (host == null) {
            return data;
        }
        data.setString("state", host.getState().name());
        data.setString("recoveryState", host.getRecoveryState().name());
        data.setBoolean("recoveryPending", host.hasRecoveryPending());
        if (host.getRecoveryDiagnostic() != null) {
            data.setString("recoveryDiagnostic", host.getRecoveryDiagnostic());
        }
        if (host.getEnabledMode() != null) {
            data.setString("mode", host.getEnabledMode().name());
        }
        if (host.getFrequencyReference() != null) {
            data.setTag("reference", host.getFrequencyReference().write());
        }
        return data;
    }

    void readSummary(NBTTagCompound data) {
        try {
            QIOAutomationHost.State reportedState = data.hasKey("state") ?
                  QIOAutomationHost.State.valueOf(data.getString("state")) :
                  QIOAutomationHost.State.UNBOUND;
            // DATA_ERROR was serialized by older hosts. It is not a lifecycle state
            // anymore; keep the diagnostic/recovery bit while showing the normal
            // access lifecycle to the client.
            recoveryPending = data.getBoolean("recoveryPending") ||
                  reportedState == QIOAutomationHost.State.DATA_ERROR;
            recoveryDiagnostic = data.hasKey("recoveryDiagnostic", 8) ?
                  data.getString("recoveryDiagnostic") : null;
            recoveryState = data.hasKey("recoveryState", 8) ?
                  QIOAutomationHost.RecoveryState.valueOf(data.getString("recoveryState")) :
                  (reportedState == QIOAutomationHost.State.DATA_ERROR ||
                        recoveryDiagnostic != null ?
                        QIOAutomationHost.RecoveryState.QUARANTINED :
                        QIOAutomationHost.RecoveryState.NONE);
            if (recoveryState == QIOAutomationHost.RecoveryState.NONE &&
                  (reportedState == QIOAutomationHost.State.DATA_ERROR ||
                        recoveryDiagnostic != null)) {
                // Be defensive about old clients/packets that carried a diagnostic without its
                // companion recovery marker.
                recoveryState = QIOAutomationHost.RecoveryState.QUARANTINED;
            }
            if (recoveryState != QIOAutomationHost.RecoveryState.NONE) {
                recoveryPending = true;
            }
            mode = data.hasKey("mode") ? QIOAutomationMode.valueOf(data.getString("mode")) : null;
            reference = data.hasKey("reference", 10) ?
                  QIOFrequencyReference.read(data.getCompoundTag("reference")) : null;
            state = reportedState == QIOAutomationHost.State.DATA_ERROR ?
                  (reference == null ? QIOAutomationHost.State.UNBOUND :
                        QIOAutomationHost.State.WAITING_ACCESS) : reportedState;
            displayFrequency = createDisplayFrequency(reference);
        } catch (RuntimeException e) {
            state = QIOAutomationHost.State.UNBOUND;
            recoveryState = QIOAutomationHost.RecoveryState.QUARANTINED;
            recoveryPending = true;
            recoveryDiagnostic = e.getMessage() == null ? "Invalid QIO automation state" :
                  e.getMessage();
            mode = null;
            reference = null;
            displayFrequency = null;
        }
    }

    private java.util.Collection<QIOFrequency> serverFrequencies(SecurityMode securityMode) {
        UUID player = container.getPlayerUUID();
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(
              securityMode == SecurityMode.PUBLIC ? null : player, securityMode);
        return manager == null ? Collections.emptyList() : manager.getFrequencies();
    }

    private static boolean matchesReference(QIOFrequency frequency, QIOFrequencyReference reference) {
        return frequency != null && frequency.getFrequencyUUID().equals(reference.getFrequencyUUID()) &&
              frequency.getName().equals(reference.getFrequencyName()) &&
              java.util.Objects.equals(frequency.getOwner(), reference.getOwnerUUID()) &&
              frequency.getSecurity() == reference.getSecurityMode();
    }

    @Nullable
    private static QIOFrequency createDisplayFrequency(@Nullable QIOFrequencyReference reference) {
        if (reference == null) {
            return null;
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setString(NBTConstants.TYPE, Frequency.QIO);
        data.setString(NBTConstants.NAME, reference.getFrequencyName());
        if (reference.getOwnerUUID() != null) {
            data.setString(NBTConstants.OWNER_UUID, reference.getOwnerUUID().toString());
        }
        data.setInteger(NBTConstants.SECURITY_MODE, reference.getSecurityMode().ordinal());
        data.setString("qioFrequencyUUID", reference.getFrequencyUUID().toString());
        return new QIOFrequency(data);
    }
}
