package mekanism.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Optional-module hooks for extending configuration card data without a core-to-module link. */
public final class ConfigurationCardDataExtensionRegistry {

    private static final Map<ResourceLocation, Handler> HANDLERS = new LinkedHashMap<>();

    private ConfigurationCardDataExtensionRegistry() {
    }

    public static synchronized void register(@Nonnull ResourceLocation id,
          @Nonnull Handler handler) {
        Objects.requireNonNull(id, "Configuration card extension id cannot be null");
        Objects.requireNonNull(handler, "Configuration card extension handler cannot be null");
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalArgumentException("Duplicate configuration card extension " + id);
        }
    }

    public static synchronized boolean unregister(@Nullable ResourceLocation id) {
        return id != null && HANDLERS.remove(id) != null;
    }

    @Nonnull
    static NBTTagCompound collect(@Nonnull TileEntity tile, @Nonnull EntityPlayer player,
          @Nonnull NBTTagCompound data) {
        NBTTagCompound current = Objects.requireNonNull(data, "Configuration card data cannot be null");
        for (Handler handler : snapshot()) {
            current = Objects.requireNonNull(handler.collect(tile, player, current),
                  "Configuration card extension returned null while collecting data");
        }
        return current;
    }

    @Nonnull
    static NBTTagCompound filterForApply(@Nonnull TileEntity tile,
          @Nonnull EntityPlayer player, @Nonnull NBTTagCompound data) {
        NBTTagCompound current = Objects.requireNonNull(data, "Configuration card data cannot be null");
        for (Handler handler : snapshot()) {
            current = Objects.requireNonNull(handler.filterForApply(tile, player, current),
                  "Configuration card extension returned null while filtering data");
        }
        return current;
    }

    static void apply(@Nonnull TileEntity tile, @Nonnull EntityPlayer player,
          @Nonnull NBTTagCompound data) {
        for (Handler handler : snapshot()) {
            handler.apply(tile, player, data);
        }
    }

    @Nonnull
    private static synchronized List<Handler> snapshot() {
        return new ArrayList<>(HANDLERS.values());
    }

    public interface Handler {

        /** Adds module-owned data while copying a machine configuration to the card. */
        @Nonnull
        default NBTTagCompound collect(@Nonnull TileEntity tile,
              @Nonnull EntityPlayer player, @Nonnull NBTTagCompound data) {
            return data;
        }

        /** Removes module-owned data the current player may not apply. */
        @Nonnull
        default NBTTagCompound filterForApply(@Nonnull TileEntity tile,
              @Nonnull EntityPlayer player, @Nonnull NBTTagCompound data) {
            return data;
        }

        /** Applies module-owned data after Mekanism's base and special configuration. */
        default void apply(@Nonnull TileEntity tile, @Nonnull EntityPlayer player,
              @Nonnull NBTTagCompound data) {
        }
    }
}
