package mekanism.api.processing;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Small, persistent identity used to render a machine without retaining its Tile NBT.
 * Presentation data is deliberately independent from recipe and profile identity.
 */
public final class MachinePresentationDescriptor {

    public static final int MAX_NBT_BYTES = 2_048;
    public static final int MAX_NBT_DEPTH = 8;
    public static final int MAX_NBT_NODES = 128;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_DISCRIMINATOR_LENGTH = 128;
    private static final Set<String> FORBIDDEN_KEYS = new HashSet<>(Arrays.asList(
          "items", "inventory", "energy", "energystored", "energycontainers",
          "fluid", "fluids", "fluidtank", "gas", "gases", "gastank",
          "frequency", "componentfrequency", "security", "owner", "trusted",
          "upgrades", "componentupgrade", "qio", "qiofrequency",
          "qioautomation", "qioautomationhost", "contents", "storage", "stored",
          "tank", "slot", "slots", "container", "containers", "capabilities"));

    private final String itemId;
    private final int itemMetadata;
    @Nullable
    private final NBTTagCompound itemNbt;
    private final String typeDiscriminator;

    private MachinePresentationDescriptor(String itemId, int itemMetadata,
          @Nullable NBTTagCompound itemNbt, @Nullable String typeDiscriminator) {
        this.itemId = checkedId(itemId);
        if (itemMetadata < 0 || itemMetadata > Short.MAX_VALUE) {
            throw new IllegalArgumentException("itemMetadata must be in the range 0..32767");
        }
        this.itemMetadata = itemMetadata;
        this.itemNbt = checkedNbt(itemNbt);
        this.typeDiscriminator = checkedDiscriminator(typeDiscriminator);
    }

    @Nonnull
    public static MachinePresentationDescriptor of(@Nonnull String itemId,
          int itemMetadata, @Nullable NBTTagCompound itemNbt) {
        return new MachinePresentationDescriptor(itemId, itemMetadata, itemNbt, null);
    }

    @Nonnull
    public static MachinePresentationDescriptor of(@Nonnull String itemId,
          int itemMetadata, @Nullable NBTTagCompound itemNbt,
          @Nullable String typeDiscriminator) {
        return new MachinePresentationDescriptor(itemId, itemMetadata, itemNbt,
              typeDiscriminator);
    }

    @Nonnull
    public static MachinePresentationDescriptor fromStack(@Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            throw new IllegalArgumentException("Presentation stack must name a registered item");
        }
        return of(stack.getItem().getRegistryName().toString(), stack.getMetadata(),
              stack.getTagCompound());
    }

    @Nonnull
    public static MachinePresentationDescriptor fromStack(@Nonnull ItemStack stack,
          @Nullable String typeDiscriminator) {
        MachinePresentationDescriptor descriptor = fromStack(stack);
        return of(descriptor.itemId, descriptor.itemMetadata, descriptor.itemNbt,
              typeDiscriminator);
    }

    @Nonnull
    public static MachinePresentationDescriptor fallback(@Nonnull TileEntity tile) {
        Objects.requireNonNull(tile, "tile");
        try {
            Block block = tile.getBlockType();
            ResourceLocation blockId = block == null ? null : block.getRegistryName();
            int metadata = Math.max(0, Math.min(Short.MAX_VALUE, tile.getBlockMetadata()));
            return of(blockId == null ? "minecraft:air" : blockId.toString(), metadata, null);
        } catch (RuntimeException ignored) {
            return of("minecraft:air", 0, null);
        }
    }

    @Nonnull
    public static MachinePresentationDescriptor read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "data");
        if (!data.hasKey("itemId", NBT.TAG_STRING) ||
            !data.hasKey("itemMetadata", NBT.TAG_INT)) {
            throw new IllegalArgumentException("Machine presentation is missing identity fields");
        }
        NBTTagCompound itemNbt = data.hasKey("itemNbt", NBT.TAG_COMPOUND) ?
              data.getCompoundTag("itemNbt") : null;
        String discriminator = data.hasKey("typeDiscriminator", NBT.TAG_STRING) ?
              data.getString("typeDiscriminator") : "";
        return of(data.getString("itemId"), data.getInteger("itemMetadata"), itemNbt,
              discriminator);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("itemId", itemId);
        data.setInteger("itemMetadata", itemMetadata);
        if (itemNbt != null && !itemNbt.isEmpty()) {
            data.setTag("itemNbt", itemNbt.copy());
        }
        if (!typeDiscriminator.isEmpty()) {
            data.setString("typeDiscriminator", typeDiscriminator);
        }
        return data;
    }

    @Nonnull
    public ItemStack createStack() {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(itemId));
                item = block == null ? null : Item.getItemFromBlock(block);
            }
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, 1, itemMetadata);
            if (itemNbt != null && !itemNbt.isEmpty()) {
                stack.setTagCompound(itemNbt.copy());
            }
            return stack;
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    @Nonnull
    public String getItemId() {
        return itemId;
    }

    public int getItemMetadata() {
        return itemMetadata;
    }

    @Nullable
    public NBTTagCompound getItemNbt() {
        return itemNbt == null ? null : itemNbt.copy();
    }

    @Nonnull
    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    /** Stable grouping identity; never use this as a recipe or profile key. */
    @Nonnull
    public String presentationKey() {
        String canonicalNbt = itemNbt == null ? "" : canonicalCopy(itemNbt).toString();
        UUID nbtFingerprint = UUID.nameUUIDFromBytes(
              canonicalNbt.getBytes(StandardCharsets.UTF_8));
        return itemId + '|' + itemMetadata + '|' + typeDiscriminator + '|' +
              nbtFingerprint;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof MachinePresentationDescriptor other)) return false;
        return itemMetadata == other.itemMetadata && itemId.equals(other.itemId) &&
              Objects.equals(itemNbt, other.itemNbt) &&
              typeDiscriminator.equals(other.typeDiscriminator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, itemMetadata, itemNbt, typeDiscriminator);
    }

    private static String checkedId(String value) {
        String checked = Objects.requireNonNull(value, "itemId").trim();
        if (checked.isEmpty() || checked.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("itemId has an invalid length");
        }
        ResourceLocation id = new ResourceLocation(checked);
        if (!id.toString().equals(checked)) {
            throw new IllegalArgumentException("itemId must be a canonical resource location");
        }
        return checked;
    }

    private static String checkedDiscriminator(@Nullable String value) {
        String checked = value == null ? "" : value.trim();
        if (checked.length() > MAX_DISCRIMINATOR_LENGTH) {
            throw new IllegalArgumentException("typeDiscriminator is too long");
        }
        for (int index = 0; index < checked.length(); index++) {
            char character = checked.charAt(index);
            if (!(character >= 'a' && character <= 'z') &&
                !(character >= '0' && character <= '9') && character != '_' &&
                character != '-' && character != '.' && character != '/') {
                throw new IllegalArgumentException("typeDiscriminator contains an unsupported character");
            }
        }
        return checked;
    }

    @Nullable
    private static NBTTagCompound checkedNbt(@Nullable NBTTagCompound source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        NBTTagCompound copy = source.copy();
        Counter counter = new Counter();
        validateTag(copy, 0, counter);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.write(copy, new DataOutputStream(bytes));
            if (bytes.size() > MAX_NBT_BYTES) {
                throw new IllegalArgumentException("Machine presentation NBT is too large");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to measure machine presentation NBT", e);
        }
        return copy;
    }

    private static void validateTag(NBTBase tag, int depth, Counter counter) {
        if (depth > MAX_NBT_DEPTH || ++counter.nodes > MAX_NBT_NODES) {
            throw new IllegalArgumentException("Machine presentation NBT is too complex");
        }
        if (tag instanceof NBTTagCompound compound) {
            for (String key : compound.getKeySet()) {
                if (key.length() > 64 || forbiddenKey(key)) {
                    throw new IllegalArgumentException("Machine presentation NBT contains a forbidden key: " + key);
                }
                validateTag(compound.getTag(key), depth + 1, counter);
            }
        } else if (tag instanceof NBTTagList list) {
            if (list.tagCount() > MAX_NBT_NODES) {
                throw new IllegalArgumentException("Machine presentation NBT list is too large");
            }
            for (NBTBase child : list) {
                validateTag(child, depth + 1, counter);
            }
        } else if (tag instanceof NBTTagByteArray bytes &&
                   bytes.getByteArray().length > MAX_NBT_BYTES ||
                   tag instanceof NBTTagIntArray integers &&
                   integers.getIntArray().length * Integer.BYTES > MAX_NBT_BYTES ||
                   tag instanceof NBTTagString string &&
                   string.getString().length() > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("Machine presentation NBT value is too large");
        }
    }

    private static boolean forbiddenKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (FORBIDDEN_KEYS.contains(normalized)) return true;
        return normalized.contains("inventory") || normalized.contains("frequency") ||
              normalized.contains("security") || normalized.contains("upgrade") ||
              normalized.contains("energy") || normalized.contains("owner") ||
              normalized.contains("trusted") || normalized.contains("contents") ||
              normalized.contains("storage") || normalized.contains("stored") ||
              normalized.contains("tank") || normalized.contains("slot") ||
              normalized.contains("capabilit");
    }

    private static NBTBase canonicalCopy(NBTBase value) {
        if (value instanceof NBTTagCompound compound) {
            NBTTagCompound copy = new NBTTagCompound();
            ArrayList<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            for (String key : keys) {
                copy.setTag(key, canonicalCopy(compound.getTag(key)));
            }
            return copy;
        }
        if (value instanceof NBTTagList list) {
            NBTTagList copy = new NBTTagList();
            for (NBTBase element : list) {
                copy.appendTag(canonicalCopy(element));
            }
            return copy;
        }
        return value.copy();
    }

    private static final class Counter {
        private int nodes;
    }
}
