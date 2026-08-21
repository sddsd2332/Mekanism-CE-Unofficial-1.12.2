package mekanism.qioprocessing.client.gui;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared bounded client projection for QIO item, fluid, and gas descriptors. */
/**
 * QIO 处理模块中的 QIOGuiResourceRenderer 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOGuiResourceRenderer {

    private static final int MAX_CACHE_SIZE = 2_048;
    private static final Map<PortableResourceDescriptor, DisplayResource> CACHE =
          new LinkedHashMap<>(256, 0.75F, true) {
              @Override
              protected boolean removeEldestEntry(
                    Map.Entry<PortableResourceDescriptor, DisplayResource> eldest) {
                  return size() > MAX_CACHE_SIZE;
              }
          };

    private QIOGuiResourceRenderer() {
    }

    static void renderIcon(IGuiWrapper gui, @Nullable PortableResourceDescriptor resource,
          int x, int y, int size) {
        if (resource == null || size <= 0) return;
        DisplayResource display = display(resource);
        switch (resource.getKind()) {
            case ITEM -> {
                if (!display.item.isEmpty()) {
                    gui.renderItemWithOverlay(display.item, x, y, size / 16F, "");
                }
            }
            case FLUID -> GuiUtils.drawFluidBarSprite(x - 1, y - 1, size + 2, size + 2,
                  size, display.fluid, true);
            case GAS -> GuiUtils.drawGasBarSprite(x - 1, y - 1, size + 2, size + 2,
                  size, display.gas, true);
        }
    }

    @Nonnull
    static String name(@Nullable PortableResourceDescriptor resource) {
        if (resource == null) {
            return new TextComponentTranslation(
                  "gui.mekanismqioprocessing.unknown_output").getFormattedText();
        }
        DisplayResource display = display(resource);
        return switch (resource.getKind()) {
            case ITEM -> display.item.isEmpty() ? resource.getRegistryName() :
                  display.item.getDisplayName();
            case FLUID -> display.fluid == null ? resource.getRegistryName() :
                  display.fluid.getLocalizedName();
            case GAS -> display.gas == null || display.gas.getGas() == null ?
                  resource.getRegistryName() : display.gas.getGas().getLocalizedName();
        };
    }

    @Nonnull
    static String identity(@Nullable PortableResourceDescriptor resource) {
        if (resource == null) return "-";
        if (resource.getKind() != PortableResourceDescriptor.Kind.ITEM) {
            return resource.getRegistryName();
        }
        return resource.getRegistryName() + '@' + resource.getMetadata() +
              (resource.getTag() == null ? "" : " [NBT]");
    }

    @Nonnull
    static List<String> tooltip(@Nullable PortableResourceDescriptor resource) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(name(resource));
        if (resource != null && resource.getKind() == PortableResourceDescriptor.Kind.FLUID) {
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_unit_fluid").getFormattedText());
        } else if (resource != null &&
              resource.getKind() == PortableResourceDescriptor.Kind.GAS) {
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_unit_gas").getFormattedText());
        }
        return tooltip;
    }

    @Nullable
    static Object ingredient(@Nullable PortableResourceDescriptor resource) {
        if (resource == null) return null;
        DisplayResource display = display(resource);
        return switch (resource.getKind()) {
            case ITEM -> display.item;
            case FLUID -> display.fluid;
            case GAS -> display.gas;
        };
    }

    @Nonnull
    static ItemStack item(@Nullable PortableResourceDescriptor resource) {
        return resource == null ? ItemStack.EMPTY : display(resource).item;
    }

    @Nonnull
    private static DisplayResource display(@Nonnull PortableResourceDescriptor resource) {
        synchronized (CACHE) {
            DisplayResource display = CACHE.get(resource);
            if (display == null) {
                ItemStack item = ItemStack.EMPTY;
                FluidStack fluid = null;
                GasStack gas = null;
                try {
                    item = resource.resolveItem();
                    fluid = resource.resolveFluid();
                    gas = resource.resolveGas();
                } catch (RuntimeException ignored) {
                }
                display = new DisplayResource(item, fluid, gas);
                CACHE.put(resource, display);
            }
            return display;
        }
    }

    private static final class DisplayResource {
        private final ItemStack item;
        @Nullable private final FluidStack fluid;
        @Nullable private final GasStack gas;

        private DisplayResource(ItemStack item, @Nullable FluidStack fluid,
              @Nullable GasStack gas) {
            this.item = item == null ? ItemStack.EMPTY : item;
            this.fluid = fluid;
            this.gas = gas;
        }
    }
}
