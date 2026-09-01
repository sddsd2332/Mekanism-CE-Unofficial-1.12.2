package mekanism.client.gui.qio;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFilterResourceHelper;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiQIORedstoneAdapter extends GuiMekanismTile<TileEntityQIORedstoneAdapter, ContainerQIORedstoneAdapter> {

    private GuiTextField countField;
    private GuiQIOFrequencyTab<?> frequencyTab;
    private MekanismImageButton fuzzyButton;

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
              .click((element, mouseX, mouseY, button) -> updateTarget(button))
              .tooltip(() -> QIOFilterGuiResource.tooltip(tileEntity.getTargetFilter()))
              .setGhostHandler(createTargetGhostHandler()));
        addButton(new ToggleButton(this, 9, 64, 14, tileEntity::isInverted,
              () -> PacketQIOComponentConfig.toggleRedstoneInverted(tileEntity),
              new TextComponentTranslation("gui.qio.redstone.inverted.enabled"),
              new TextComponentTranslation("gui.qio.redstone.inverted.disabled")));
        fuzzyButton = addButton(new MekanismImageButton(this, 9, 80, 14, getButtonLocation("fuzzy"),
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
        updateFuzzyVisibility();
    }

    private boolean updateTarget(int button) {
        ItemStack carried = mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
        if (carried.isEmpty()) {
            if (button == 0 && GuiScreen.isShiftKeyDown()) {
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
        return setTarget(QIOResourceSelection.fromCarried(carried, button));
    }

    private IGhostIngredientConsumer createTargetGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Override
            public Object supportedTarget(Object ingredient) {
                return QIOResourceSelection.fromIngredient(ingredient).isUnique() ? ingredient : null;
            }

            @Override
            public void accept(Object ingredient) {
                setTarget(QIOResourceSelection.fromIngredient(ingredient));
            }
        };
    }

    private boolean setTarget(QIOResourceSelection.Resolution selection) {
        QIOResourceDescriptor descriptor = selection.getDescriptor();
        if (descriptor == null) {
            return false;
        }
        QIOFilter filter = QIOFilterResourceHelper.createFilter(descriptor);
        if (filter == null) {
            return false;
        }
        PacketQIOComponentConfig.setFilter(tileEntity, filter);
        return true;
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
        return QIOGuiCapacityText.forFrequency(frequency);
    }

    private List<ITextComponent> getDetails() {
        List<ITextComponent> details = new ArrayList<>();
        QIOFilter target = tileEntity.getTargetFilter();
        details.add(new TextComponentString(target == null ? LangUtils.localize("gui.qio.redstone.undefined") :
              QIOFilterGuiResource.name(target)));
        details.add(new TextComponentTranslation(tileEntity.isInverted() ? "gui.qio.redstone.trigger.less" :
              "gui.qio.redstone.trigger.greater", TextUtils.format(tileEntity.getThreshold())));
        if (target != null && tileEntity.getQIOFrequency() != null) {
            details.add(new TextComponentTranslation("gui.qio.redstone.stored", TextUtils.format(tileEntity.getStoredCount())));
        }
        if (isItemTarget()) {
            details.add(new TextComponentTranslation("gui.qio.redstone.fuzzy", tileEntity.getFuzzyMode() ?
                  MekanismLang.ON.translate() : MekanismLang.OFF.translate()));
        }
        return details;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        renderTarget();
        super.drawForegroundText(mouseX, mouseY);
    }

    private void renderTarget() {
        QIOFilterGuiResource.render(this, tileEntity.getTargetFilter(), 8, 31, 16);
    }

    private boolean isItemTarget() {
        QIOResourceDescriptor descriptor = QIOFilterGuiResource.descriptor(tileEntity.getTargetFilter());
        return descriptor != null && descriptor.getCodecId().equals(
              mekanism.api.qio.resource.QIOResourceCodecs.ITEM_STACK_ID);
    }

    private void updateFuzzyVisibility() {
        if (fuzzyButton != null) {
            fuzzyButton.visible = isItemTarget();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateFuzzyVisibility();
    }
}
