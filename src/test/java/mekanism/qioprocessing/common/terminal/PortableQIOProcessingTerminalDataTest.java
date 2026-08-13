package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableQIOProcessingTerminalDataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void identityAndExactFrequencyReferenceRoundTrip() throws Exception {
        UUID ownerUUID = UUID.randomUUID();
        UUID frequencyUUID = UUID.randomUUID();
        ItemStack stack = new ItemStack(Items.STICK);
        PortableQIOProcessingTerminalData created =
              PortableQIOProcessingTerminalData.create(ownerUUID);
        QIOFrequencyReference reference = new QIOFrequencyReference(frequencyUUID,
              "processing", UUID.randomUUID(), SecurityMode.TRUSTED, ownerUUID);
        PortableQIOProcessingTerminalData bound = created.withSecurity(
              SecurityMode.PRIVATE).withFrequency(reference);
        bound.writeTo(stack);

        PortableQIOProcessingTerminalData restored =
              PortableQIOProcessingTerminalData.read(stack);

        assertNotNull(restored);
        assertEquals(created.getTerminalUUID(), restored.getTerminalUUID());
        assertEquals(2, restored.getGeneration());
        assertEquals(ownerUUID, restored.getOwnerUUID());
        assertEquals(SecurityMode.PRIVATE, restored.getSecurityMode());
        assertEquals(reference, restored.getFrequencyReference());
        assertTrue(restored.shiftClickIntoFrequency());
    }

    @Test
    void onlyAuthoritativeChangesAdvanceGenerationAndOwnerChangeUnbinds() {
        UUID ownerUUID = UUID.randomUUID();
        PortableQIOProcessingTerminalData initial =
              PortableQIOProcessingTerminalData.create(ownerUUID);
        assertEquals(initial, initial.withSecurity(SecurityMode.PUBLIC));
        assertEquals(initial, initial.withShiftClickIntoFrequency(true));

        QIOFrequencyReference reference = new QIOFrequencyReference(UUID.randomUUID(),
              "trusted", UUID.randomUUID(), SecurityMode.TRUSTED, ownerUUID);
        PortableQIOProcessingTerminalData changed = initial.withFrequency(reference)
              .withShiftClickIntoFrequency(false);
        PortableQIOProcessingTerminalData transferred = changed.withOwner(UUID.randomUUID());

        assertEquals(2, changed.getGeneration());
        assertFalse(changed.shiftClickIntoFrequency());
        assertEquals(3, transferred.getGeneration());
        assertNull(transferred.getFrequencyReference());
    }

    @Test
    void writingKnownFieldsPreservesCraftingWindowPayload() throws Exception {
        ItemStack stack = new ItemStack(Items.STICK);
        PortableQIOProcessingTerminalData data =
              PortableQIOProcessingTerminalData.create(UUID.randomUUID());
        data.writeTo(stack);
        NBTTagCompound root = stack.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY);
        NBTTagList windows = new NBTTagList();
        NBTTagCompound window = new NBTTagCompound();
        window.setByte("Window", (byte) 2);
        windows.appendTag(window);
        root.setTag("craftingWindows", windows);

        data.withShiftClickIntoFrequency(false).writeTo(stack);

        NBTTagCompound written = stack.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY);
        assertEquals(1, written.getTagList("craftingWindows", 10).tagCount());
        assertEquals(2, written.getTagList("craftingWindows", 10)
              .getCompoundTagAt(0).getByte("Window"));
    }

    @Test
    void missingAndDamagedPortableDataAreDistinguished() throws Exception {
        ItemStack emptyData = new ItemStack(Items.STICK);
        assertNull(PortableQIOProcessingTerminalData.read(emptyData));

        PortableQIOProcessingTerminalData valid =
              PortableQIOProcessingTerminalData.create(UUID.randomUUID());
        valid.writeTo(emptyData);
        NBTTagCompound stored = emptyData.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY);
        stored.setLong("itemDataGeneration", -1);
        assertThrows(QIOProcessingDataException.class,
              () -> PortableQIOProcessingTerminalData.read(emptyData));

        ItemStack development = new ItemStack(Items.STICK);
        valid.writeTo(development);
        NBTTagCompound developmentData = development.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY);
        developmentData.setInteger("portableItemSchemaVersion", 1);
        assertThrows(QIOProcessingDataException.class,
              () -> PortableQIOProcessingTerminalData.read(development));

        ItemStack missingPreference = new ItemStack(Items.STICK);
        valid.writeTo(missingPreference);
        missingPreference.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY)
              .removeTag("shiftClickIntoFrequency");
        assertThrows(QIOProcessingDataException.class,
              () -> PortableQIOProcessingTerminalData.read(missingPreference));

        ItemStack future = new ItemStack(Items.STICK);
        valid.writeTo(future);
        future.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY)
              .setInteger("portableItemSchemaVersion",
                    PortableQIOProcessingTerminalData.SCHEMA_VERSION + 1);
        assertThrows(QIOProcessingDataException.class,
              () -> PortableQIOProcessingTerminalData.read(future));
    }

    @Test
    void aReferenceWithoutAnAuditableBindingPlayerIsRejected() {
        UUID ownerUUID = UUID.randomUUID();
        PortableQIOProcessingTerminalData data =
              PortableQIOProcessingTerminalData.create(ownerUUID);
        QIOFrequencyReference wrongBinding = new QIOFrequencyReference(UUID.randomUUID(),
              "wrong", null, SecurityMode.PUBLIC, null);

        assertThrows(IllegalArgumentException.class,
              () -> data.withFrequency(wrongBinding));
    }

    @Test
    void craftingWindowCountAndSlotIndicesAreBoundedBeforeLoading() throws Exception {
        PortableQIOProcessingTerminalData data =
              PortableQIOProcessingTerminalData.create(UUID.randomUUID());

        ItemStack tooManyWindows = new ItemStack(Items.STICK);
        data.writeTo(tooManyWindows);
        NBTTagList fourWindows = new NBTTagList();
        for (byte index = 0; index < 4; index++) {
            NBTTagCompound window = new NBTTagCompound();
            window.setByte("Window", index);
            fourWindows.appendTag(window);
        }
        tooManyWindows.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY)
              .setTag("craftingWindows", fourWindows);
        assertThrows(QIOProcessingDataException.class,
              () -> PortableQIOProcessingTerminalData.read(tooManyWindows));

        ItemStack duplicateSlots = new ItemStack(Items.STICK);
        data.writeTo(duplicateSlots);
        NBTTagCompound window = new NBTTagCompound();
        window.setByte("Window", (byte) 0);
        NBTTagList items = new NBTTagList();
        NBTTagCompound first = new NBTTagCompound();
        first.setByte("Slot", (byte) 1);
        items.appendTag(first);
        items.appendTag(first.copy());
        window.setTag("Items", items);
        NBTTagList windows = new NBTTagList();
        windows.appendTag(window);
        duplicateSlots.getTagCompound().getCompoundTag(
              PortableQIOProcessingTerminalData.ROOT_KEY)
              .setTag("craftingWindows", windows);
        assertThrows(QIOProcessingDataException.class,
              () -> PortableQIOProcessingTerminalData.read(duplicateSlots));
    }
}
