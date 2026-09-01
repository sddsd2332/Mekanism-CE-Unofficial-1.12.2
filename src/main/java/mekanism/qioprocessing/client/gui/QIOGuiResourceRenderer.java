package mekanism.qioprocessing.client.gui;

import mekanism.api.gas.GasStack;
import mekanism.api.qio.client.QIOResourceRenderer;
import mekanism.api.qio.client.QIOResourceRendererRegistry;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared bounded client projection for built-in and codec-rendered QIO descriptors. */
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
        if (renderRegistered(resource, x, y, size)) return;
        DisplayResource display = display(resource);
        boolean rendered = false;
        switch (resource.getKind()) {
            case ITEM -> {
                if (!display.item.isEmpty()) {
                    gui.renderItemWithOverlay(display.item, x, y, size / 16F, "");
                    rendered = true;
                }
            }
            case FLUID -> {
                if (display.fluid != null) {
                    GuiUtils.drawFluidBarSprite(x - 1, y - 1, size + 2, size + 2,
                          size, display.fluid, true);
                    rendered = true;
                }
            }
            case GAS -> {
                if (display.gas != null) {
                    GuiUtils.drawGasBarSprite(x - 1, y - 1, size + 2, size + 2,
                          size, display.gas, true);
                    rendered = true;
                }
            }
            case CUSTOM -> {
            }
        }
        if (!rendered) renderUnknown(gui, resource, x, y, size);
    }

    @Nonnull
    static String name(@Nullable PortableResourceDescriptor resource) {
        if (resource == null) {
            return new TextComponentTranslation(
                  "gui.mekanismqioprocessing.unknown_output").getFormattedText();
        }
        String renderedName = renderedDisplayName(resource);
        if (renderedName != null) return renderedName;
        DisplayResource display = display(resource);
        return switch (resource.getKind()) {
            case ITEM -> display.item.isEmpty() ? resource.getRegistryName() :
                  display.item.getDisplayName();
            case FLUID -> display.fluid == null ? resource.getRegistryName() :
                  display.fluid.getLocalizedName();
            case GAS -> display.gas == null || display.gas.getGas() == null ?
                  resource.getRegistryName() : display.gas.getGas().getLocalizedName();
            case CUSTOM -> resource.getRegistryName();
        };
    }

    @Nonnull
    static String identity(@Nullable PortableResourceDescriptor resource) {
        if (resource == null) return "-";
        String renderedIdentity = renderedRegistryName(resource);
        if (renderedIdentity != null) return renderedIdentity;
        if (resource.getKind() != PortableResourceDescriptor.Kind.ITEM) {
            return resource.getRegistryName();
        }
        return resource.getRegistryName() + '@' + resource.getMetadata() +
              (resource.getTag() == null ? "" : " [NBT]");
    }

    @Nonnull
    static List<String> tooltip(@Nullable PortableResourceDescriptor resource) {
        List<String> renderedTooltip = renderedTooltip(resource);
        if (renderedTooltip != null) return renderedTooltip;
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
        Object renderedIngredient = renderedIngredient(resource);
        if (renderedIngredient != null) return renderedIngredient;
        DisplayResource display = display(resource);
        return switch (resource.getKind()) {
            case ITEM -> display.item;
            case FLUID -> display.fluid;
            case GAS -> display.gas;
            case CUSTOM -> null;
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

    private static boolean renderRegistered(PortableResourceDescriptor resource,
          int x, int y, int size) {
        RendererResolution resolved = resolveRenderer(resource);
        if (resolved == null) return false;
        float scale = size / 16F;
        GlStateManager.pushMatrix();
        try {
            if (scale != 1) {
                GlStateManager.translate(x, y, 0);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.translate(-x, -y, 0);
            }
            resolved.renderer.render(resolved.resource, x, y);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            GlStateManager.popMatrix();
            MekanismRenderer.resetColor();
        }
    }

    private static void renderUnknown(IGuiWrapper gui, PortableResourceDescriptor resource,
          int x, int y, int size) {
        int hash = resource.hashCode();
        int color = 0xFF000000 | (80 + (hash & 0x7F)) << 16 |
              (80 + ((hash >>> 8) & 0x7F)) << 8 | 80 + ((hash >>> 16) & 0x7F);
        int inset = Math.max(1, size / 8);
        GuiUtils.fill(x + inset, y + inset, x + size - inset, y + size - inset, color);
        FontRenderer font = gui.getFont();
        if (font == null) return;
        float scale = Math.min(1, size / 16F);
        int width = font.getStringWidth("?");
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (size - width * scale) / 2F,
              y + (size - font.FONT_HEIGHT * scale) / 2F, 100);
        GlStateManager.scale(scale, scale, scale);
        font.drawString("?", 0, 0, 0xFFFFFFFF);
        GlStateManager.popMatrix();
        MekanismRenderer.resetColor();
    }

    @Nullable
    private static String renderedDisplayName(PortableResourceDescriptor resource) {
        RendererResolution resolved = resolveRenderer(resource);
        if (resolved == null) return null;
        try {
            String name = resolved.renderer.getDisplayName(resolved.resource);
            return name == null || name.isEmpty() ? null : name;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static String renderedRegistryName(PortableResourceDescriptor resource) {
        RendererResolution resolved = resolveRenderer(resource);
        if (resolved == null) return null;
        try {
            String name = resolved.renderer.getRegistryName(resolved.resource);
            return name == null || name.isEmpty() ? null : name;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static List<String> renderedTooltip(@Nullable PortableResourceDescriptor resource) {
        RendererResolution resolved = resolveRenderer(resource);
        if (resolved == null) return null;
        try {
            List<String> supplied = resolved.renderer.getTooltip(resolved.resource);
            if (supplied == null) return null;
            List<String> tooltip = new ArrayList<>(supplied.size());
            for (String line : supplied) {
                if (line != null && !line.isEmpty()) tooltip.add(line);
            }
            if (tooltip.isEmpty()) tooltip.add(name(resource));
            return tooltip;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Object renderedIngredient(PortableResourceDescriptor resource) {
        RendererResolution resolved = resolveRenderer(resource);
        if (resolved == null) return null;
        try {
            return resolved.renderer.getIngredient(resolved.resource);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static RendererResolution resolveRenderer(@Nullable PortableResourceDescriptor resource) {
        if (resource == null || !resource.isResolved()) return null;
        QIOResourceRenderer<?> renderer = QIOResourceRendererRegistry.INSTANCE.get(resource.getCodecId());
        if (renderer == null) return null;
        Object value = resource.getDescriptor().resolve();
        if (value == null || !renderer.getValueClass().isInstance(value)) return null;
        return new RendererResolution((QIOResourceRenderer<Object>) renderer, value);
    }

    private static final class RendererResolution {

        private final QIOResourceRenderer<Object> renderer;
        private final Object resource;

        private RendererResolution(QIOResourceRenderer<Object> renderer, Object resource) {
            this.renderer = renderer;
            this.resource = resource;
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
