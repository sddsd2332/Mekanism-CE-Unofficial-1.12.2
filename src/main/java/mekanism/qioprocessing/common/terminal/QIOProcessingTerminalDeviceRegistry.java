package mekanism.qioprocessing.common.terminal;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager.IsolatedNetworkException;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Online identity and persistent directory bridge for the four terminal block types. */
/**
 * QIO 处理模块中的 QIOProcessingTerminalDeviceRegistry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingTerminalDeviceRegistry {

    public static final QIOProcessingTerminalDeviceRegistry INSTANCE =
          new QIOProcessingTerminalDeviceRegistry();

    private final Map<UUID, Set<QIOProcessingTerminal>> terminals =
          new LinkedHashMap<>();
    private final Map<QIOProcessingTerminal, UUID> identities =
          new IdentityHashMap<>();
    private final Map<QIOProcessingTerminal, PublishedEndpoint> published =
          new IdentityHashMap<>();
    private final Set<UUID> quarantinedUUIDs = new LinkedHashSet<>();
    private final Set<UUID> directoryRejectedDevices = new LinkedHashSet<>();

    private QIOProcessingTerminalDeviceRegistry() {
    }

    public synchronized void register(@Nonnull QIOProcessingTerminal terminal) {
        UUID uuid = terminal.getPersistentTerminalUUID();
        UUID previous = identities.get(terminal);
        if (previous != null && !previous.equals(uuid)) {
            forgetPublished(terminal);
            remove(previous, terminal);
        }
        identities.put(terminal, uuid);
        terminals.computeIfAbsent(uuid,
              ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(terminal);
        Set<QIOProcessingTerminal> matches = terminals.get(uuid);
        if (matches.size() > 1) {
            quarantinedUUIDs.add(uuid);
        }
        refreshIdentityGroup(uuid);
    }

    public synchronized void unregister(@Nonnull QIOProcessingTerminal terminal) {
        UUID uuid = identities.remove(terminal);
        if (uuid != null) {
            publishOffline(terminal);
            remove(uuid, terminal);
            refreshIdentityGroup(uuid);
        }
    }

    /** Called from TileEntity.invalidate; a loaded invalid tile has been physically replaced. */
    public synchronized void unregisterRemoved(@Nonnull QIOProcessingTerminal terminal) {
        UUID uuid = identities.remove(terminal);
        if (uuid != null) {
            if (isLoadedInvalidation(terminal)) {
                forgetPublished(terminal);
            } else {
                publishOffline(terminal);
            }
            remove(uuid, terminal);
            refreshIdentityGroup(uuid);
        }
    }

    public synchronized void refresh(@Nonnull QIOProcessingTerminal terminal) {
        UUID uuid = identities.get(terminal);
        if (uuid != null) {
            terminal.setIdentityConflict(quarantinedUUIDs.contains(uuid));
            publish(terminal);
        }
    }

    public synchronized boolean isQuarantined(@Nonnull UUID terminalUUID) {
        return quarantinedUUIDs.contains(terminalUUID);
    }

    public synchronized void shutdown() {
        terminals.clear();
        identities.clear();
        published.clear();
        quarantinedUUIDs.clear();
        directoryRejectedDevices.clear();
    }

    private void remove(UUID uuid, QIOProcessingTerminal terminal) {
        Set<QIOProcessingTerminal> matches = terminals.get(uuid);
        if (matches != null) {
            matches.remove(terminal);
            if (matches.isEmpty()) {
                terminals.remove(uuid);
            }
        }
    }

    private void refreshIdentityGroup(UUID uuid) {
        Set<QIOProcessingTerminal> matches = terminals.get(uuid);
        if (matches == null) {
            return;
        }
        boolean conflicted = quarantinedUUIDs.contains(uuid);
        for (QIOProcessingTerminal terminal : matches) {
            terminal.setIdentityConflict(conflicted);
            publish(terminal);
        }
    }

    private void publish(QIOProcessingTerminal terminal) {
        if (terminal == null || terminal.isInvalid() || terminal.getWorld() == null ||
            terminal.getWorld().isRemote ||
            !terminal.getWorld().isBlockLoaded(terminal.getPos(), false) ||
            terminal.getWorld().getTileEntity(terminal.getPos()) != terminal) {
            publishOffline(terminal);
            return;
        }
        mekanism.api.qio.external.QIOFrequencyReference reference =
              terminal.getFrequencyReference();
        QIOFrequency frequency = QIOProcessingFrequencyAccess.resolve(reference);
        if (reference == null || frequency == null) {
            forgetPublished(terminal);
            return;
        }
        if (!QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
            publishOffline(terminal);
            return;
        }
        UUID terminalUUID = terminal.getPersistentTerminalUUID();
        QIOAutomationDeviceLocation location = QIOAutomationDeviceLocation.of(
              terminal.getWorld(), terminal.getPos());
        PublishedEndpoint previous = published.get(terminal);
        if (previous != null && (!previous.frequencyUUID.equals(reference.getFrequencyUUID()) ||
            !previous.location.equals(location) || !previous.deviceUUID.equals(terminalUUID))) {
            forgetPublished(previous);
        }
        PublishedEndpoint endpoint = new PublishedEndpoint(terminalUUID,
              reference.getFrequencyUUID(), location);
        published.put(terminal, endpoint);
        boolean accessible = QIOProcessingFrequencyAccess.resolveAccessible(reference,
              reference.getBindingPlayerUUID()) != null;
        String state = terminal.hasIdentityConflict() ? "IDENTITY_CONFLICT" :
              terminal.hasDataError() ? "DATA_ERROR" : accessible ? "ACTIVE" :
                    "WAITING_ACCESS";
        Block block = terminal.getBlockType();
        ResourceLocation blockId = block == null ? null : block.getRegistryName();
        if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
              reference.getFrequencyUUID()) != null) {
            directoryRejectedDevices.add(terminalUUID);
            return;
        }
        try {
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.getOrCreate(
                  reference.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                        reference.getFrequencyName(), reference.getOwnerUUID(),
                        reference.getSecurityMode()));
            QIOAutomationDeviceSnapshot snapshot = new QIOAutomationDeviceSnapshot(
                  terminalUUID, location, QIOAutomationDeviceSnapshot.Kind.TERMINAL,
                  blockId == null ? "minecraft:air" : blockId.toString(),
                  Math.max(0, terminal.getBlockMetadata()),
                  "mekanismqioprocessing:" + terminal.getTerminalType().getSerializedName(),
                  terminal.getTerminalType().getSerializedName(), state, true,
                  Math.max(0, terminal.getWorld().getTotalWorldTime()),
                  terminal.getConfigurationRevision(), 0,
                  terminal.getTerminalType().hasCraftingWindows() ? 3 : 0, 0,
                  terminal.getDataError());
            network.observeAutomationDevice(snapshot,
                  MekanismConfig.current().qioProcessing.deviceRecordsPerFrequency.val());
            directoryRejectedDevices.remove(terminalUUID);
        } catch (IsolatedNetworkException e) {
            directoryRejectedDevices.add(terminalUUID);
        } catch (RuntimeException e) {
            if (directoryRejectedDevices.add(terminalUUID)) {
                Mekanism.logger.warn("Unable to publish QIO processing terminal {} to frequency {}",
                      terminalUUID, reference.getFrequencyUUID(), e);
            }
        }
    }

    private void publishOffline(QIOProcessingTerminal terminal) {
        PublishedEndpoint endpoint = published.remove(terminal);
        if (endpoint == null) {
            return;
        }
        long currentTick = terminal != null && terminal.getWorld() != null ?
              Math.max(0, terminal.getWorld().getTotalWorldTime()) : 0;
        publishOffline(endpoint, currentTick);
    }

    private static void publishOffline(PublishedEndpoint endpoint, long currentTick) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              endpoint.frequencyUUID);
        if (network != null) {
            network.markAutomationDeviceOffline(endpoint.deviceUUID, endpoint.location,
                  currentTick);
        }
    }

    private void forgetPublished(QIOProcessingTerminal terminal) {
        PublishedEndpoint endpoint = published.remove(terminal);
        if (endpoint != null) {
            forgetPublished(endpoint);
        }
    }

    private static void forgetPublished(PublishedEndpoint endpoint) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              endpoint.frequencyUUID);
        if (network != null) {
            network.forgetAutomationDeviceMembership(endpoint.deviceUUID,
                  endpoint.location);
        }
    }

    private static boolean isLoadedInvalidation(QIOProcessingTerminal terminal) {
        return terminal.getWorld() != null && !terminal.getWorld().isRemote &&
              terminal.getWorld().isBlockLoaded(terminal.getPos(), false) &&
              (terminal.isInvalid() || terminal.getWorld().getTileEntity(
                    terminal.getPos()) != terminal);
    }

    private static final class PublishedEndpoint {

        private final UUID deviceUUID;
        private final UUID frequencyUUID;
        private final QIOAutomationDeviceLocation location;

        private PublishedEndpoint(UUID deviceUUID, UUID frequencyUUID,
              QIOAutomationDeviceLocation location) {
            this.deviceUUID = deviceUUID;
            this.frequencyUUID = frequencyUUID;
            this.location = location;
        }
    }
}
