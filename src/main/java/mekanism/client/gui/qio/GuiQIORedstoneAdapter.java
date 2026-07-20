package mekanism.client.gui.qio;

import mekanism.api.EnumColor;
import mekanism.api.gas.GasStack;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFluidFilter;
import mekanism.common.content.qio.filter.QIOGasFilter;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.inventory.container.ContainerQIORedstoneAdapter;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIORedstoneAdapter;
import mekanism.common.util.LangUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiQIORedstoneAdapter extends GuiMekanismTile<TileEntityQIORedstoneAdapter, ContainerQIORedstoneAdapter> {

    private GuiTextField countField;
    private GuiQIOFrequencyTab<?> frequencyTab;

    public GuiQIORedstoneAdapter(InventoryPlayer inventory, TileEntityQIORedstoneAdapter tile) {
        super(tile, new ContainerQIORedstoneAdapter(inventory, tile));
        dynamicSlots = true;
        ySize = 192;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOFrequencyTab.Tile(this, tileEntity, () -> frequencyTab));
        addButton(new GuiSlot(SlotType.NORMAL, this, 7, 30).setRenderHover(true)
              .click((element, mouseX, mouseY) -> updateTarget())
              .setGhostHandler(createTargetGhostHandler()));
        addButton(new ToggleButton(this, 9, 64, 14, tileEntity::isInverted,
              () -> PacketQIOComponentConfig.toggleRedstoneInverted(tileEntity),
              new TextComponentTranslation("gui.qio.redstone.inverted.enabled"),
              new TextComponentTranslation("gui.qio.redstone.inverted.disabled")));
        addButton(new MekanismImageButton(this, 9, 80, 14, getButtonLocation("fuzzy"),
              () -> PacketQIOComponentConfig.toggleRedstoneFuzzy(tileEntity))
              .setTooltip(() -> new TextComponentTranslation("gui.qio.redstone.fuzzy", tileEntity.getFuzzyMode() ?
                    MekanismLang.ON.translate() : MekanismLang.OFF.translate())));
        addButton(new GuiInnerScreen(this, 7, 16, xSize - 15, 12, this::getFrequencyText)
              .tooltip(this::getFrequencyTooltip));
        addButton(new GuiInnerScreen(this, 27, 30, xSize - 35, 64, this::getDetails).clearFormat());
        countField = addButton(new GuiTextField(this, 29, 80, xSize - 39, 12)
              .setMaxLength(19)
              .setInputValidator(this::isDigitOrTextKey)
              .configureDigitalInput(this::setCount));
        setFocused(countField);
    }

    private boolean updateTarget() {
        ItemStack carried = mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
        if (carried.isEmpty()) {
            if (GuiScreen.isShiftKeyDown()) {
                PacketQIOComponentConfig.clearFilter(tileEntity);
                return true;
            }
            return false;
        }
        // Match the 26.2 slot interaction: a held ingredient is assigned with
        // a normal click, while Shift-click is reserved for clearing.
        if (GuiScreen.isShiftKeyDown()) {
            return false;
        }
        QIOFilter filter = QIOFilter.fromItemStack(carried);
        if (filter != null) {
            PacketQIOComponentConfig.setFilter(tileEntity, filter);
            return true;
        }
        return false;
    }

    private IGhostIngredientConsumer createTargetGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Override
            public Object supportedTarget(Object ingredient) {
                if (ingredient instanceof ItemStack) {
                    return ((ItemStack) ingredient).isEmpty() ? null : ingredient;
                } else if (ingredient instanceof FluidStack) {
                    FluidStack fluid = (FluidStack) ingredient;
                    return fluid.amount > 0 && fluid.getFluid() != null ? ingredient : null;
                } else if (ingredient instanceof GasStack) {
                    GasStack gas = (GasStack) ingredient;
                    return gas.amount > 0 && gas.getGas() != null ? ingredient : null;
                }
                return null;
            }

            @Override
            public void accept(Object ingredient) {
                QIOFilter filter = null;
                if (ingredient instanceof ItemStack) {
                    filter = QIOFilter.fromItemStack((ItemStack) ingredient);
                } else if (ingredient instanceof FluidStack) {
                    filter = new QIOFluidFilter((FluidStack) ingredient);
                } else if (ingredient instanceof GasStack) {
                    filter = new QIOGasFilter((GasStack) ingredient);
                }
                if (filter != null) {
                    PacketQIOComponentConfig.setFilter(tileEntity, filter);
                }
            }
        };
    }

    private boolean isDigitOrTextKey(char character, int keyCode) {
        return Character.isDigit(character) || GuiMekanism.isTextboxKey(character, keyCode);
    }

    private void setCount() {
        if (countField.isEmpty()) {
            return;
        }
        try {
            PacketQIOComponentConfig.setThreshold(tileEntity, Long.parseLong(countField.getText()));
            countField.clear();
        } catch (NumberFormatException ignored) {
            countField.setTextSilently(Long.toString(Long.MAX_VALUE));
        }
    }

    private List<ITextComponent> getFrequencyText() {
        mekanism.common.content.qio.QIOFrequency frequency = tileEntity.getQIOFrequency();
        return Collections.singletonList(frequency == null ? new TextComponentTranslation("frequency.mekanism.none") :
              MekanismLang.FREQUENCY.translate(frequency.getName()));
    }

    private List<ITextComponent> getFrequencyTooltip() {
        mekanism.common.content.qio.QIOFrequency frequency = tileEntity.getQIOFrequency();
        if (frequency == null) {
            return Collections.emptyList();
        }
        return java.util.Arrays.asList(MekanismLang.QIO_ITEMS_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
                    TextUtils.format(frequency.getTotalCount()), TextUtils.format(frequency.getTotalCountCapacity())),
              MekanismLang.QIO_TYPES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
                    TextUtils.format(frequency.getTotalTypes()), TextUtils.format(frequency.getTotalTypeCapacity())));
    }

    private List<ITextComponent> getDetails() {
        List<ITextComponent> details = new ArrayList<>();
        QIOFilter target = tileEntity.getTargetFilter();
        details.add(new TextComponentString(target == null ? LangUtils.localize("gui.qio.redstone.undefined") : getResourceName(target)));
        details.add(new TextComponentTranslation(tileEntity.isInverted() ? "gui.qio.redstone.trigger.less" :
              "gui.qio.redstone.trigger.greater", TextUtils.format(tileEntity.getThreshold())));
        if (target != null && tileEntity.getQIOFrequency() != null) {
            details.add(new TextComponentTranslation("gui.qio.redstone.stored", TextUtils.format(tileEntity.getStoredCount())));
        }
        details.add(new TextComponentTranslation("gui.qio.redstone.fuzzy", tileEntity.getFuzzyMode() ?
              MekanismLang.ON.translate() : MekanismLang.OFF.translate()));
        return details;
    }

    private String getResourceName(QIOFilter filter) {
        if (filter instanceof QIOItemStackFilter) {
            ItemStack stack = ((QIOItemStackFilter) filter).getItemStack();
            return stack.isEmpty() ? LangUtils.localize("gui.qio.filter.unknown") : stack.getDisplayName();
        } else if (filter instanceof QIOFluidFilter) {
            FluidStack fluid = ((QIOFluidFilter) filter).getFluid();
            return fluid == null ? LangUtils.localize("gui.qio.filter.unknown") : fluid.getLocalizedName();
        } else if (filter instanceof QIOGasFilter) {
            GasStack gas = ((QIOGasFilter) filter).getGas();
            return gas == null || gas.getGas() == null ? LangUtils.localize("gui.qio.filter.unknown") : gas.getGas().getLocalizedName();
        }
        return LangUtils.localize("gui.qio.filter.unknown");
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        renderTarget();
        super.drawForegroundText(mouseX, mouseY);
    }

    private void renderTarget() {
        QIOFilter target = tileEntity.getTargetFilter();
        if (target instanceof QIOItemStackFilter) {
            renderItem(((QIOItemStackFilter) target).getItemStack(), 8, 31);
        } else if (target instanceof QIOFluidFilter) {
            FluidStack fluid = ((QIOFluidFilter) target).getFluid();
            if (fluid != null && fluid.getFluid() != null) {
                GuiUtils.drawFluidBarSprite(7, 30, 18, 18, 16, fluid, true);
            }
            drawTextExact(new TextComponentString("F"), 13, 35, 0xFFFFFFFF);
        } else if (target instanceof QIOGasFilter) {
            GasStack gas = ((QIOGasFilter) target).getGas();
            if (gas != null && gas.getGas() != null) {
                GuiUtils.drawGasBarSprite(7, 30, 18, 18, 16, gas, true);
            }
            drawTextExact(new TextComponentString("G"), 13, 35, 0xFFFFFFFF);
        }
    }
}
