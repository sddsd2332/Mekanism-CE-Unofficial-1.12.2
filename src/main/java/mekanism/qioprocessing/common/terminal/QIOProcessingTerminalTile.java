package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.SecurityUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared exact-frequency state host for the four restored processing terminal families.
 */
public abstract class QIOProcessingTerminalTile extends TileEntityContainerBlock
        implements IFrequencyHandler, ISecurityTile, QIOProcessingTerminalAccess {
    private static final String REFERENCE_KEY = "qioProcessingTerminalReference";
    private static final String ID_KEY = "qioProcessingTerminalId";
    private static final String REVISION_KEY = "qioProcessingTerminalRevision";
    private static final String ERROR_KEY = "qioProcessingTerminalError";

    private final QIOProcessingTerminalType terminalType;
    private UUID terminalId = UUID.randomUUID();
    private long configurationRevision;
    private QIOFrequencyReference endpointReference;
    private String dataError = "";
    protected final TileComponentSecurity securityComponent;

    protected QIOProcessingTerminalTile(String name, QIOProcessingTerminalType terminalType) {
        super(name);
        this.terminalType = Objects.requireNonNull(terminalType, "terminalType");
        getFrequencyComponent().track(FrequencyType.QIO, true, true, true);
        securityComponent = new TileComponentSecurity(this);
    }

    @Override
    public final QIOProcessingTerminalType terminalType() {
        return terminalType;
    }

    public final UUID terminalId() {
        return terminalId;
    }

    @Override
    public final long configurationRevision() {
        return configurationRevision;
    }

    @Override
    public final QIOFrequencyReference endpointReference() {
        return endpointReference;
    }

    public final String dataError() {
        return dataError;
    }

    public final QIOFrequency qioFrequency() {
        return getFrequencyComponent().getFrequency(FrequencyType.QIO);
    }

    @Override
    public final TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    public final boolean bind(QIOFrequencyReference reference, UUID playerId) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(playerId, "playerId");
        if (!playerId.equals(reference.getBindingPlayerUUID())) return false;
        if (endpointReference != null && !endpointReference.equals(reference)) return false;
        endpointReference = reference;
        dataError = "";
        incrementRevision();
        markDirty();
        return true;
    }

    public final boolean bindCurrentFrequency(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        QIOFrequency frequency = qioFrequency();
        if (frequency == null || !frequency.isValid() || frequency.isRemoved()
                || !SecurityUtils.canAccess(frequency.getSecurity(), playerId, frequency.getOwner())) {
            return false;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(frequency, playerId);
        return QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, playerId) && bind(reference, playerId);
    }

    public final boolean unbind(UUID playerId) {
        if (endpointReference == null || playerId == null
                || !playerId.equals(endpointReference.getBindingPlayerUUID())) return false;
        endpointReference = null;
        incrementRevision();
        markDirty();
        return true;
    }

    public final void quarantine(String reason) {
        dataError = reason == null || reason.isEmpty() ? "terminal data error" :
                reason.length() > 512 ? reason.substring(0, 512) : reason;
        markDirty();
    }

    public final void markPatternConfigurationChanged() {
        incrementRevision();
        markDirty();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound tag) {
        super.writeCustomNBT(tag);
        tag.setUniqueId(ID_KEY, terminalId);
        tag.setLong(REVISION_KEY, configurationRevision);
        if (endpointReference != null) tag.setTag(REFERENCE_KEY, endpointReference.write());
        if (!dataError.isEmpty()) tag.setString(ERROR_KEY, dataError);
    }

    @Override
    public void readCustomNBT(NBTTagCompound tag) {
        super.readCustomNBT(tag);
        try {
            if (tag.hasUniqueId(ID_KEY)) terminalId = tag.getUniqueId(ID_KEY);
            configurationRevision = Math.max(0, tag.getLong(REVISION_KEY));
            dataError = tag.getString(ERROR_KEY);
            endpointReference = tag.hasKey(REFERENCE_KEY, 10)
                    ? QIOFrequencyReference.read(tag.getCompoundTag(REFERENCE_KEY)) : null;
            if (endpointReference != null && endpointReference.getBindingPlayerUUID() == null) {
                throw new IllegalArgumentException("Terminal reference has no binding player");
            }
        } catch (RuntimeException error) {
            endpointReference = null;
            dataError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
    }

    private void incrementRevision() {
        configurationRevision = configurationRevision == Long.MAX_VALUE ? 0 : configurationRevision + 1;
    }

    public static final class Management extends QIOProcessingTerminalTile {
        public Management() {
            super("qio_management_terminal", QIOProcessingTerminalType.MANAGEMENT);
        }
    }

    public static final class SmartProcessing extends QIOProcessingTerminalTile
            implements QIOProcessingSmartTerminalAccess {
        private final QIOProcessingCraftingWindow[] craftingWindows = new QIOProcessingCraftingWindow[3];

        public SmartProcessing() {
            super("qio_smart_processing_terminal", QIOProcessingTerminalType.SMART_PROCESSING);
            for (int index = 0; index < craftingWindows.length; index++) {
                craftingWindows[index] = new QIOProcessingCraftingWindow(this::getWorld, this::markDirty);
            }
        }

        public QIOProcessingCraftingWindow craftingWindow(int index) {
            if (index < 0 || index >= craftingWindows.length)
                throw new IllegalArgumentException("Invalid crafting window");
            return craftingWindows[index];
        }

        @Override
        public List<ItemStack> craftingGrid(int window) {
            return craftingWindow(window).grid();
        }

        @Override
        public void setCraftingSlot(int window, int slot, ItemStack value) {
            craftingWindow(window).setSlot(slot, value);
        }

        @Override
        public mekanism.qioprocessing.common.recipe.RecipeItemKey craftingOutputKey(int window) {
            return craftingWindow(window).outputKey();
        }

        @Override
        public void writeCustomNBT(NBTTagCompound tag) {
            super.writeCustomNBT(tag);
            net.minecraft.nbt.NBTTagList stored = new net.minecraft.nbt.NBTTagList();
            for (int index = 0; index < craftingWindows.length; index++) {
                NBTTagCompound row = craftingWindows[index].write();
                row.setByte("window", (byte) index);
                stored.appendTag(row);
            }
            tag.setTag("qioProcessingCraftingWindows", stored);
        }

        @Override
        public void readCustomNBT(NBTTagCompound tag) {
            super.readCustomNBT(tag);
            net.minecraft.nbt.NBTTagList stored = tag.getTagList("qioProcessingCraftingWindows",
                    net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            boolean[] seen = new boolean[craftingWindows.length];
            for (int index = 0; index < stored.tagCount(); index++) {
                NBTTagCompound row = stored.getCompoundTagAt(index);
                int window = row.getByte("window") & 0xFF;
                if (window < craftingWindows.length && !seen[window]) {
                    seen[window] = true;
                    craftingWindows[window].read(row);
                }
            }
        }
    }

    public static final class Maintenance extends QIOProcessingTerminalTile {
        public Maintenance() {
            super("qio_maintenance_terminal", QIOProcessingTerminalType.MAINTENANCE);
        }
    }

    public static final class CraftingMonitor extends QIOProcessingTerminalTile {
        public CraftingMonitor() {
            super("qio_crafting_monitor", QIOProcessingTerminalType.CRAFTING_MONITOR);
        }
    }
}
