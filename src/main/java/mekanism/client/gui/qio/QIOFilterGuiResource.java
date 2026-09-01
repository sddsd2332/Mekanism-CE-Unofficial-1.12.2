package mekanism.client.gui.qio;

import mekanism.api.EnumColor;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.client.QIOResourceRenderer;
import mekanism.api.qio.client.QIOResourceRendererRegistry;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFilterResourceHelper;
import mekanism.common.util.LangUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared presentation of exact QIO filters, including addon codec renderers. */
public final class QIOFilterGuiResource {

    private QIOFilterGuiResource() {
    }

    @Nullable
    public static QIOResourceDescriptor descriptor(@Nullable QIOFilter filter) {
        return QIOFilterResourceHelper.getDescriptor(filter);
    }

    public static void render(@Nonnull IGuiWrapper gui, @Nullable QIOFilter filter,
          int x, int y, int size) {
        render(gui, descriptor(filter), x, y, size);
    }

    public static void render(@Nonnull IGuiWrapper gui, @Nullable QIOResourceDescriptor descriptor,
          int x, int y, int size) {
        if (descriptor == null || size <= 0) {
            return;
        }
        if (renderRegistered(descriptor, x, y, size)) {
            return;
        }
        QIOResourceKind kind = QIOResourceKind.fromDescriptor(descriptor);
        boolean rendered = false;
        if (kind == QIOResourceKind.ITEM) {
            ItemStack stack = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
            if (stack != null && !stack.isEmpty()) {
                gui.renderItemWithOverlay(stack, x, y, size / 16F, "");
                rendered = true;
            }
        } else if (kind == QIOResourceKind.FLUID) {
            FluidStack stack = descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
            if (stack != null && stack.getFluid() != null) {
                GuiUtils.drawFluidBarSprite(x - 1, y - 1, size + 2, size + 2, size, stack, true);
                drawMarker(gui, "F", x, y, size);
                rendered = true;
            }
        } else if (kind == QIOResourceKind.GAS) {
            GasStack stack = descriptor.resolve(QIOResourceCodecs.GAS_STACK);
            if (stack != null && stack.getGas() != null) {
                GuiUtils.drawGasBarSprite(x - 1, y - 1, size + 2, size + 2, size, stack, true);
                drawMarker(gui, "G", x, y, size);
                rendered = true;
            }
        }
        if (!rendered) {
            renderUnknown(gui, descriptor, x, y, size);
        }
    }

    @Nonnull
    public static String name(@Nullable QIOFilter filter) {
        return name(descriptor(filter));
    }

    @Nonnull
    public static String name(@Nullable QIOResourceDescriptor descriptor) {
        if (descriptor == null) {
            return LangUtils.localize("gui.qio.filter.unknown");
        }
        RendererResolution resolved = resolveRenderer(descriptor);
        if (resolved != null) {
            try {
                String name = resolved.renderer.getDisplayName(resolved.resource);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        QIOResourceKind kind = QIOResourceKind.fromDescriptor(descriptor);
        if (kind == QIOResourceKind.ITEM) {
            ItemStack stack = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
            if (stack != null && !stack.isEmpty()) {
                return stack.getDisplayName();
            }
        } else if (kind == QIOResourceKind.FLUID) {
            FluidStack stack = descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
            if (stack != null && stack.getFluid() != null) {
                return stack.getLocalizedName();
            }
        } else if (kind == QIOResourceKind.GAS) {
            GasStack stack = descriptor.resolve(QIOResourceCodecs.GAS_STACK);
            if (stack != null && stack.getGas() != null) {
                return stack.getGas().getLocalizedName();
            }
        }
        return descriptor.getCodecId().toString();
    }

    @Nonnull
    public static String typeName(@Nullable QIOFilter filter) {
        return typeName(descriptor(filter));
    }

    @Nonnull
    public static String typeName(@Nullable QIOResourceDescriptor descriptor) {
        QIOResourceKind kind = QIOResourceKind.fromDescriptor(descriptor);
        return kind == null ? descriptor == null ? LangUtils.localize("gui.qio.filter.unknown") :
              descriptor.getCodecId().toString() :
              LangUtils.localize("gui.qio.resource." + kind.getSerializedName());
    }

    @Nonnull
    public static EnumColor color(@Nullable QIOFilter filter) {
        QIOResourceKind kind = QIOResourceKind.fromDescriptor(descriptor(filter));
        if (kind == QIOResourceKind.ITEM) {
            return EnumColor.INDIGO;
        } else if (kind == QIOResourceKind.FLUID) {
            return EnumColor.DARK_AQUA;
        } else if (kind == QIOResourceKind.GAS) {
            return EnumColor.PURPLE;
        }
        return EnumColor.GREY;
    }

    @Nonnull
    public static List<String> tooltip(@Nullable QIOFilter filter) {
        QIOResourceDescriptor descriptor = descriptor(filter);
        if (descriptor == null) {
            return Collections.singletonList(LangUtils.localize("gui.qio.filter.unknown"));
        }
        RendererResolution resolved = resolveRenderer(descriptor);
        if (resolved != null) {
            try {
                List<String> supplied = resolved.renderer.getTooltip(resolved.resource);
                if (supplied != null) {
                    List<String> tooltip = new ArrayList<>(supplied.size());
                    for (String line : supplied) {
                        if (line != null && !line.isEmpty()) {
                            tooltip.add(line);
                        }
                    }
                    if (!tooltip.isEmpty()) {
                        return tooltip;
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        List<String> tooltip = new ArrayList<>(2);
        tooltip.add(name(descriptor));
        tooltip.add(typeName(descriptor));
        return tooltip;
    }

    @Nullable
    public static Object ingredient(@Nullable QIOFilter filter) {
        QIOResourceDescriptor descriptor = descriptor(filter);
        if (descriptor == null) {
            return null;
        }
        RendererResolution resolved = resolveRenderer(descriptor);
        if (resolved != null) {
            try {
                Object ingredient = resolved.renderer.getIngredient(resolved.resource);
                if (ingredient != null) {
                    return ingredient;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return descriptor.resolve();
    }

    private static boolean renderRegistered(QIOResourceDescriptor descriptor,
          int x, int y, int size) {
        RendererResolution resolved = resolveRenderer(descriptor);
        if (resolved == null) {
            return false;
        }
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

    private static void renderUnknown(IGuiWrapper gui, QIOResourceDescriptor descriptor,
          int x, int y, int size) {
        int hash = descriptor.hashCode();
        int color = 0xFF000000 | (80 + (hash & 0x7F)) << 16 |
              (80 + ((hash >>> 8) & 0x7F)) << 8 | 80 + ((hash >>> 16) & 0x7F);
        int inset = Math.max(1, size / 8);
        GuiUtils.fill(x + inset, y + inset, x + size - inset, y + size - inset, color);
        drawMarker(gui, "?", x, y, size);
    }

    private static void drawMarker(IGuiWrapper gui, String marker, int x, int y, int size) {
        FontRenderer font = gui.getFont();
        if (font == null) {
            return;
        }
        float scale = Math.min(0.75F, size / 16F * 0.75F);
        int width = font.getStringWidth(marker);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (size - width * scale) / 2F,
              y + (size - font.FONT_HEIGHT * scale) / 2F, 100);
        GlStateManager.scale(scale, scale, scale);
        font.drawString(marker, 0, 0, 0xFFFFFFFF);
        GlStateManager.popMatrix();
        MekanismRenderer.resetColor();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static RendererResolution resolveRenderer(@Nullable QIOResourceDescriptor descriptor) {
        if (descriptor == null || !descriptor.isResolved()) {
            return null;
        }
        QIOResourceRenderer<?> renderer = QIOResourceRendererRegistry.INSTANCE.get(descriptor.getCodecId());
        if (renderer == null) {
            return null;
        }
        Object resource = descriptor.resolve();
        if (resource == null || !renderer.getValueClass().isInstance(resource)) {
            return null;
        }
        return new RendererResolution((QIOResourceRenderer<Object>) renderer, resource);
    }

    private static final class RendererResolution {

        private final QIOResourceRenderer<Object> renderer;
        private final Object resource;

        private RendererResolution(QIOResourceRenderer<Object> renderer, Object resource) {
            this.renderer = renderer;
            this.resource = resource;
        }
    }
}
