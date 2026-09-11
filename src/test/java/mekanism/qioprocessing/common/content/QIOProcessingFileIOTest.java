package mekanism.qioprocessing.common.content;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingFileIOTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsStringsThatCannotBeWrittenAsModifiedUtf() {
        NBTTagCompound root = new NBTTagCompound();
        StringBuilder value = new StringBuilder(70_000);
        for (int index = 0; index < 70_000; index++) {
            value.append('x');
        }
        root.setTag("payload", new NBTTagString(value.toString()));

        IOException error = assertThrows(IOException.class,
              () -> QIOProcessingFileIO.validateNbtForTests(root));
        assertTrue(error.getMessage().contains("65,535"));
    }

    @Test
    void rejectsNbtThatExceedsTheNestingBudget() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound current = root;
        for (int depth = 0; depth < 520; depth++) {
            NBTTagCompound next = new NBTTagCompound();
            current.setTag("next", next);
            current = next;
        }

        IOException error = assertThrows(IOException.class,
              () -> QIOProcessingFileIO.validateNbtForTests(root));
        assertTrue(error.getMessage().contains("nesting"));
    }

    @Test
    void rejectsTrailingExpandedDataAfterTheRootTag() throws Exception {
        Path file = temporaryDirectory.resolve("trailing.dat");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file));
             DataOutputStream output = new DataOutputStream(gzip)) {
            CompressedStreamTools.write(new NBTTagCompound(), output);
            output.writeInt(0x51494F21);
        }

        IOException error = assertThrows(IOException.class,
              () -> QIOProcessingFileIO.read(file.toFile()));
        assertTrue(error.getMessage().contains("trailing"));
    }

    @Test
    void rejectsDeclaredArrayLengthBeforeAllocatingIt() throws Exception {
        Path file = temporaryDirectory.resolve("hostile-array.dat");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file));
             DataOutputStream output = new DataOutputStream(gzip)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(7);
            output.writeUTF("payload");
            output.writeInt(Integer.MAX_VALUE);
        }

        IOException error = assertThrows(IOException.class,
              () -> QIOProcessingFileIO.read(file.toFile()));
        assertTrue(error.getMessage().contains("array"));
    }

    @Test
    void validatesLongArraysWithoutRenderingTheirContents() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("longs", new NBTTagLongArray(new long[]{1, 2, 3}) {
            @Override
            public String toString() {
                throw new AssertionError("long-array validation must not render the array");
            }
        });

        QIOProcessingFileIO.validateNbtForTests(root);
    }

    @Test
    void rejectsMixedListPayloadTypesBeforeWriting() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList mixed = new NBTTagList();
        mixed.appendTag(new NBTTagString("first"));
        mixed.tagList.add(new NBTTagInt(2));
        root.setTag("mixed", mixed);

        IOException error = assertThrows(IOException.class,
              () -> QIOProcessingFileIO.validateNbtForTests(root));
        assertTrue(error.getMessage().contains("mixed"));
    }

    @Test
    void roundTripsEverySupportedPayloadType() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("byte", new NBTTagByte((byte) 1));
        root.setTag("short", new NBTTagShort((short) 2));
        root.setTag("int", new NBTTagInt(3));
        root.setTag("long", new NBTTagLong(4));
        root.setTag("float", new NBTTagFloat(5.5F));
        root.setTag("double", new NBTTagDouble(6.5D));
        root.setByteArray("bytes", new byte[]{7, 8});
        root.setString("string", "QIO");
        root.setIntArray("ints", new int[]{9, 10});
        root.setTag("longs", new NBTTagLongArray(new long[]{11, 12}));
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("first"));
        list.appendTag(new NBTTagString("second"));
        root.setTag("list", list);
        NBTTagCompound child = new NBTTagCompound();
        child.setBoolean("present", true);
        root.setTag("compound", child);
        Path file = temporaryDirectory.resolve("round-trip.dat");

        QIOProcessingFileIO.replaceAtomicWithoutBackup(file.toFile(), root);

        assertEquals(root, QIOProcessingFileIO.read(file.toFile()));
    }
}
