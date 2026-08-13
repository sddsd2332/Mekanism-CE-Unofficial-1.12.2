package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Versioned identity and binding data owned by one portable processing terminal stack. */
public final class PortableQIOProcessingTerminalData {

    public static final String ROOT_KEY = "qioProcessingTerminalData";
    public static final int SCHEMA_VERSION = 2;
    private static final int MAX_FREQUENCY_NAME_LENGTH = 256;

    private final UUID terminalUUID;
    private final long generation;
    private final UUID ownerUUID;
    private final SecurityMode securityMode;
    @Nullable
    private final QIOFrequencyReference frequencyReference;
    private final boolean shiftClickIntoFrequency;

    private PortableQIOProcessingTerminalData(@Nonnull UUID terminalUUID, long generation,
          @Nonnull UUID ownerUUID, @Nonnull SecurityMode securityMode,
          @Nullable QIOFrequencyReference frequencyReference,
          boolean shiftClickIntoFrequency) {
        this.terminalUUID = Objects.requireNonNull(terminalUUID, "terminalUUID");
        this.generation = QIOProcessingNbt.requireNonNegative(generation, "itemDataGeneration");
        this.ownerUUID = Objects.requireNonNull(ownerUUID, "ownerUUID");
        this.securityMode = Objects.requireNonNull(securityMode, "securityMode");
        validateReference(frequencyReference);
        this.frequencyReference = frequencyReference;
        this.shiftClickIntoFrequency = shiftClickIntoFrequency;
    }

    @Nonnull
    public static PortableQIOProcessingTerminalData create(@Nonnull UUID ownerUUID) {
        return new PortableQIOProcessingTerminalData(UUID.randomUUID(), 0,
              ownerUUID, SecurityMode.PUBLIC, null, true);
    }

    @Nonnull
    public UUID getTerminalUUID() {
        return terminalUUID;
    }

    public long getGeneration() {
        return generation;
    }

    @Nonnull
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nonnull
    public SecurityMode getSecurityMode() {
        return securityMode;
    }

    @Nullable
    public QIOFrequencyReference getFrequencyReference() {
        return frequencyReference;
    }

    public boolean shiftClickIntoFrequency() {
        return shiftClickIntoFrequency;
    }

    @Nonnull
    public PortableQIOProcessingTerminalData withOwner(@Nonnull UUID updatedOwnerUUID) {
        Objects.requireNonNull(updatedOwnerUUID, "updatedOwnerUUID");
        if (ownerUUID.equals(updatedOwnerUUID)) {
            return this;
        }
        return updated(updatedOwnerUUID, securityMode, null, shiftClickIntoFrequency);
    }

    @Nonnull
    public PortableQIOProcessingTerminalData withSecurity(@Nonnull SecurityMode updatedMode) {
        Objects.requireNonNull(updatedMode, "updatedMode");
        return securityMode == updatedMode ? this :
              updated(ownerUUID, updatedMode, frequencyReference, shiftClickIntoFrequency);
    }

    @Nonnull
    public PortableQIOProcessingTerminalData withFrequency(
          @Nullable QIOFrequencyReference updatedReference) {
        return Objects.equals(frequencyReference, updatedReference) ? this :
              updated(ownerUUID, securityMode, updatedReference, shiftClickIntoFrequency);
    }

    @Nonnull
    public PortableQIOProcessingTerminalData withShiftClickIntoFrequency(boolean value) {
        return shiftClickIntoFrequency == value ? this :
              updated(ownerUUID, securityMode, frequencyReference, value);
    }

    /** Advances generation for a retained payload changed by another terminal component. */
    @Nonnull
    public PortableQIOProcessingTerminalData advanceGeneration() {
        return updated(ownerUUID, securityMode, frequencyReference,
              shiftClickIntoFrequency);
    }

    public void writeTo(@Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Portable terminal stack cannot be empty");
        }
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() :
              new NBTTagCompound();
        NBTTagCompound data = root.hasKey(ROOT_KEY, NBT.TAG_COMPOUND) ?
              root.getCompoundTag(ROOT_KEY).copy() : new NBTTagCompound();
        data.setInteger("portableItemSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "portableTerminalUUID", terminalUUID);
        data.setLong("itemDataGeneration", generation);
        QIOProcessingNbt.writeUUID(data, "ownerUUID", ownerUUID);
        data.setString("securityMode", securityMode.name());
        if (frequencyReference == null) {
            data.removeTag("frequencyReference");
        } else {
            data.setTag("frequencyReference", frequencyReference.write());
        }
        data.setBoolean("shiftClickIntoFrequency", shiftClickIntoFrequency);
        root.setTag(ROOT_KEY, data);
        stack.setTagCompound(root);
    }

    @Nullable
    public static PortableQIOProcessingTerminalData read(@Nonnull ItemStack stack)
          throws QIOProcessingDataException {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || !stack.hasTagCompound() ||
            !stack.getTagCompound().hasKey(ROOT_KEY, NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound data = stack.getTagCompound().getCompoundTag(ROOT_KEY);
        try {
            if (!data.hasKey("portableItemSchemaVersion", NBT.TAG_INT) ||
                data.getInteger("portableItemSchemaVersion") != SCHEMA_VERSION) {
                throw new QIOProcessingDataException("Unsupported portable QIO terminal schema " +
                      data.getInteger("portableItemSchemaVersion"));
            }
            if (!data.hasKey("portableTerminalUUID", NBT.TAG_STRING) ||
                !data.hasKey("itemDataGeneration", NBT.TAG_LONG) ||
                !data.hasKey("ownerUUID", NBT.TAG_STRING) ||
                !data.hasKey("securityMode", NBT.TAG_STRING) ||
                !data.hasKey("shiftClickIntoFrequency", NBT.TAG_BYTE)) {
                throw new QIOProcessingDataException("Portable QIO terminal identity is incomplete");
            }
            UUID terminalUUID = QIOProcessingNbt.readUUID(data, "portableTerminalUUID");
            long generation = QIOProcessingNbt.requireNonNegative(
                  data.getLong("itemDataGeneration"), "itemDataGeneration");
            UUID ownerUUID = QIOProcessingNbt.readUUID(data, "ownerUUID");
            SecurityMode securityMode = QIOProcessingNbt.readEnum(data, "securityMode",
                  SecurityMode.class);
            QIOFrequencyReference reference = data.hasKey("frequencyReference", NBT.TAG_COMPOUND) ?
                  QIOFrequencyReference.read(data.getCompoundTag("frequencyReference")) : null;
            validateCraftingWindows(data);
            boolean shiftClick = data.getBoolean("shiftClickIntoFrequency");
            return new PortableQIOProcessingTerminalData(terminalUUID, generation,
                  ownerUUID, securityMode, reference, shiftClick);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid portable QIO terminal data", e);
        }
    }

    @Nonnull
    private PortableQIOProcessingTerminalData updated(UUID updatedOwnerUUID,
          SecurityMode updatedMode, @Nullable QIOFrequencyReference updatedReference,
          boolean updatedShiftClick) {
        if (generation == Long.MAX_VALUE) {
            throw new IllegalStateException("Portable QIO terminal generation exhausted");
        }
        return new PortableQIOProcessingTerminalData(terminalUUID, generation + 1,
              updatedOwnerUUID, updatedMode, updatedReference, updatedShiftClick);
    }

    private static void validateReference(@Nullable QIOFrequencyReference reference) {
        if (reference == null) {
            return;
        }
        String name = reference.getFrequencyName();
        if (name.isEmpty() || name.length() > MAX_FREQUENCY_NAME_LENGTH ||
            reference.getBindingPlayerUUID() == null) {
            throw new IllegalArgumentException("Portable terminal frequency reference is invalid");
        }
    }

    private static void validateCraftingWindows(NBTTagCompound data)
          throws QIOProcessingDataException {
        if (!data.hasKey("craftingWindows")) {
            return;
        }
        if (!data.hasKey("craftingWindows", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Portable terminal crafting windows have an invalid type");
        }
        NBTTagList windows = data.getTagList("craftingWindows", NBT.TAG_COMPOUND);
        if (windows.tagCount() > 3) {
            throw new QIOProcessingDataException("Portable terminal has too many crafting windows");
        }
        boolean[] seenWindows = new boolean[3];
        for (int index = 0; index < windows.tagCount(); index++) {
            NBTTagCompound window = windows.getCompoundTagAt(index);
            int windowIndex = window.getByte("Window") & 0xFF;
            if (windowIndex >= seenWindows.length || seenWindows[windowIndex]) {
                throw new QIOProcessingDataException("Portable terminal has an invalid crafting window index");
            }
            seenWindows[windowIndex] = true;
            NBTTagList items = window.getTagList("Items", NBT.TAG_COMPOUND);
            if (items.tagCount() > 9) {
                throw new QIOProcessingDataException("Portable terminal crafting window has too many slots");
            }
            boolean[] seenSlots = new boolean[9];
            for (int itemIndex = 0; itemIndex < items.tagCount(); itemIndex++) {
                int slot = items.getCompoundTagAt(itemIndex).getByte("Slot") & 0xFF;
                if (slot >= seenSlots.length || seenSlots[slot]) {
                    throw new QIOProcessingDataException("Portable terminal has an invalid crafting slot index");
                }
                seenSlots[slot] = true;
            }
        }
    }
}
