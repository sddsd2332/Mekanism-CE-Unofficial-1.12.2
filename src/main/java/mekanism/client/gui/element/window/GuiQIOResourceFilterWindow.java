package mekanism.client.gui.element.window;

import mekanism.api.EnumColor;
import mekanism.api.gas.GasStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFluidFilter;
import mekanism.common.content.qio.filter.QIOGasFilter;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.GasUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** 26.2-shaped editor for item, fluid, and gas QIO filters. */
public class GuiQIOResourceFilterWindow extends GuiWindow {

    private final TileEntityQIOFilterHandler tile;
    private final QIOResourceKind kind;
    private final int index;
    @Nullable
    private QIOFilter filter;
    private String status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
    private int statusTicks;

    public GuiQIOResourceFilterWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile, QIOResourceKind kind) {
        this(gui, tile, createEmpty(kind), -1);
    }

    public GuiQIOResourceFilterWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile, QIOFilter filter, int index) {
        super(gui, (gui.getWidth() - 185) / 2, 15, 185, 90, SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        QIOFilter copied = filter == null ? null : filter.copy();
        this.filter = copied == null ? filter : copied;
        this.kind = filter == null ? QIOResourceKind.ITEM : filter.getKind();
        this.index = index;
        interactionStrategy = InteractionStrategy.CONTAINER;

        addChild(new GuiInnerScreen(gui, relativeX + 29, relativeY + 18, 116, 42, this::getScreenText).clearFormat());
        GuiSlot resourceSlot = addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 7, relativeY + 18).setRenderHover(true)
              .click((element, mouseX, mouseY) -> updateFromCarried()));
        resourceSlot.setGhostHandler(createGhostHandler());
        addChild(new TranslationButton(gui, relativeX + 29, relativeY + 62, 60, 20,
              index < 0 ? MekanismLang.BUTTON_CANCEL : MekanismLang.BUTTON_DELETE, this::cancelOrDelete));
        addChild(new TranslationButton(gui, relativeX + 91, relativeY + 62, 60, 20, MekanismLang.BUTTON_SAVE, this::save));
        if (this.filter instanceof QIOItemStackFilter) {
            addChild(new TooltipToggleButton(gui, relativeX + 148, relativeY + 18, 11, getButtonLocation("fuzzy"),
                  () -> ((QIOItemStackFilter) this.filter).isFuzzyMode(),
                  () -> ((QIOItemStackFilter) this.filter).toggleFuzzyMode(),
                  MekanismLang.FUZZY_MODE.translate(), MekanismLang.FUZZY_MODE.translate()));
        }
        if (index < 0) {
            addChild(new MekanismImageButton(gui, relativeX + 6, relativeY + 6, 11, 14, getButtonLocation("back"), () -> {
                IGuiWrapper parent = gui();
                parent.addWindow(new GuiQIOFilterSelectWindow(parent, tile));
                close();
            }).setTooltip(MekanismLang.BACK.translate()));
        } else {
            super.addCloseButton();
        }
    }

    @Override
    protected void addCloseButton() {
        // New filters use a back button; existing filters add the standard close button after initialization.
    }

    @Nullable
    private static QIOFilter createEmpty(QIOResourceKind kind) {
        return switch (kind) {
            case ITEM -> new QIOItemStackFilter();
            case FLUID -> new QIOFluidFilter();
            case GAS -> new QIOGasFilter();
        };
    }

    private boolean updateFromCarried() {
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.inventory.getItemStack();
        if (carried.isEmpty()) {
            if (GuiScreen.isShiftKeyDown()) {
                filter = createEmpty(kind);
                setStatusOk();
                return true;
            }
            return false;
        }
        switch (kind) {
            case ITEM:
                boolean fuzzy = filter instanceof QIOItemStackFilter && ((QIOItemStackFilter) filter).isFuzzyMode();
                QIOItemStackFilter itemFilter = new QIOItemStackFilter(carried);
                itemFilter.setFuzzyMode(fuzzy);
                filter = itemFilter;
                setStatusOk();
                return true;
            case FLUID:
                FluidStack fluid = FluidContainerUtils.getFluidContained(carried);
                if (fluid != null && fluid.amount > 0) {
                    filter = new QIOFluidFilter(fluid);
                    setStatusOk();
                    return true;
                }
                break;
            case GAS:
                GasStack gas = GasUtils.getCapabilityStoredGas(carried);
                if (gas != null && gas.amount > 0 && gas.getGas() != null) {
                    filter = new QIOGasFilter(gas);
                    setStatusOk();
                    return true;
                }
                break;
        }
        setInvalid();
        return false;
    }

    private IGhostIngredientConsumer createGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Override
            public Object supportedTarget(Object ingredient) {
                switch (kind) {
                    case ITEM:
                        return ingredient instanceof ItemStack && !((ItemStack) ingredient).isEmpty() ? ingredient : null;
                    case FLUID:
                        if (ingredient instanceof FluidStack) {
                            FluidStack fluid = (FluidStack) ingredient;
                            return fluid.amount > 0 && fluid.getFluid() != null ? ingredient : null;
                        }
                        return null;
                    case GAS:
                        if (ingredient instanceof GasStack) {
                            GasStack gas = (GasStack) ingredient;
                            return gas.amount > 0 && gas.getGas() != null ? ingredient : null;
                        }
                        return null;
                    default:
                        return null;
                }
            }

            @Override
            public void accept(Object ingredient) {
                switch (kind) {
                    case ITEM:
                        if (ingredient instanceof ItemStack && !((ItemStack) ingredient).isEmpty()) {
                            boolean fuzzy = filter instanceof QIOItemStackFilter && ((QIOItemStackFilter) filter).isFuzzyMode();
                            QIOItemStackFilter itemFilter = new QIOItemStackFilter((ItemStack) ingredient);
                            itemFilter.setFuzzyMode(fuzzy);
                            filter = itemFilter;
                        }
                        break;
                    case FLUID:
                        if (ingredient instanceof FluidStack) {
                            filter = new QIOFluidFilter((FluidStack) ingredient);
                        }
                        break;
                    case GAS:
                        if (ingredient instanceof GasStack) {
                            filter = new QIOGasFilter((GasStack) ingredient);
                        }
                        break;
                    default:
                        break;
                }
                if (filter != null && filter.hasFilter()) {
                    setStatusOk();
                    playClickSound();
                }
            }
        };
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>();
        text.add(new TextComponentTranslation("gui.qio.filter.status", status));
        text.add(new TextComponentString(getResourceName()));
        text.add(new TextComponentTranslation("gui.qio.resource." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        if (filter instanceof QIOItemStackFilter) {
            text.add(MekanismLang.QIO_FUZZY_MODE.translate(((QIOItemStackFilter) filter).isFuzzyMode() ?
                  MekanismLang.ON.translate() : MekanismLang.OFF.translate()));
        }
        return text;
    }

    private String getResourceName() {
        if (filter instanceof QIOItemStackFilter) {
            ItemStack stack = ((QIOItemStackFilter) filter).getItemStack();
            return stack.isEmpty() ? LangUtils.localize("gui.qio.filter.unknown") : stack.getDisplayName();
        } else if (filter instanceof QIOFluidFilter) {
            FluidStack fluid = ((QIOFluidFilter) filter).getFluid();
            return fluid == null || fluid.getFluid() == null ? LangUtils.localize("gui.qio.filter.unknown") : fluid.getLocalizedName();
        } else if (filter instanceof QIOGasFilter) {
            GasStack gas = ((QIOGasFilter) filter).getGas();
            return gas == null || gas.getGas() == null ? LangUtils.localize("gui.qio.filter.unknown") : gas.getGas().getLocalizedName();
        }
        return LangUtils.localize("gui.qio.filter.unknown");
    }

    private void cancelOrDelete() {
        if (index >= 0) {
            PacketQIOComponentConfig.removeFilter(tile, index);
        }
        close();
    }

    private void save() {
        if (filter == null || !filter.hasFilter()) {
            setInvalid();
            return;
        }
        if (index < 0) {
            PacketQIOComponentConfig.addFilter(tile, filter);
        } else {
            PacketQIOComponentConfig.editFilter(tile, index, filter);
        }
        close();
    }

    private void setStatusOk() {
        status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
        statusTicks = 0;
    }

    private void setInvalid() {
        status = EnumColor.DARK_RED + LangUtils.localize("gui.qio.filter.invalid_resource");
        statusTicks = 100;
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0 && --statusTicks == 0) {
            setStatusOk();
        }
    }

    private void renderResource() {
        int x = relativeX + 8;
        int y = relativeY + 19;
        if (filter instanceof QIOItemStackFilter) {
            ItemStack stack = ((QIOItemStackFilter) filter).getItemStack();
            if (!stack.isEmpty()) {
                gui().renderItem(stack, x, y);
            }
        } else if (filter instanceof QIOFluidFilter) {
            FluidStack fluid = ((QIOFluidFilter) filter).getFluid();
            if (fluid != null && fluid.getFluid() != null) {
                GuiUtils.drawFluidBarSprite(x, y, 18, 18, 16, fluid, true);
            }
            drawResourceMarker("F", x + 5, y + 4);
        } else if (filter instanceof QIOGasFilter) {
            GasStack gas = ((QIOGasFilter) filter).getGas();
            if (gas != null && gas.getGas() != null) {
                GuiUtils.drawGasBarSprite(x, y, 18, 18, 16, gas, true);
            }
            drawResourceMarker("G", x + 5, y + 4);
        }
    }

    private void drawResourceMarker(String marker, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 100);
        GlStateManager.scale(0.75F, 0.75F, 0.75F);
        getFont().drawString(marker, 0, 0, 0xFFFFFFFF);
        GlStateManager.popMatrix();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        renderResource();
        if (filter instanceof QIOItemStackFilter) {
            String state = ((QIOItemStackFilter) filter).isFuzzyMode() ?
                  MekanismLang.ON.translate().getFormattedText() : MekanismLang.OFF.translate().getFormattedText();
            drawScaledScrollingString(new TextComponentString(state), 159, 20, TextAlignment.LEFT, titleTextColor(),
                  width - 161, 2, false, 0.6F, getTimeOpened());
        }
        drawTitleText(new TextComponentTranslation(index < 0 ? "gui.qio.filter.new" : "gui.qio.filter.edit"), 6);
    }
}
