package mekanism.client.gui.element.gauge;

import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.content.tank.SynchronizedTankData;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.List;

public class GuiMergedTankGauge extends GuiGauge<Void> {

    private final GuiFluidGauge fluidGauge;
    private final GuiGasGauge gasGauge;

    private final TileEntityDynamicTank tileEntity;
    @Nullable
    private ITextComponent label;

    public GuiMergedTankGauge(IGuiWrapper gui, TileEntityDynamicTank tileEntity, int x, int y) {
        this(gui, tileEntity, GaugeType.MEDIUM, x, y, 34, 60);
    }

    public GuiMergedTankGauge(IGuiWrapper gui, TileEntityDynamicTank tileEntity, GaugeType type, int x, int y, int width, int height) {
        super(type, gui, x, y, width, height);
        this.tileEntity = tileEntity;
        fluidGauge = addPositionOnlyChild(new GuiFluidGauge(this::getFluidTank, this::getFluidTanks, type, gui, x, y, width, height));
        gasGauge = addPositionOnlyChild(new GuiGasGauge(this::getGasTank, this::getGasTanks, type, gui, x, y, width, height));
    }

    public GuiMergedTankGauge setLabel(ITextComponent label) {
        this.label = label;
        return this;
    }

    @Override
    public GaugeOverlay getGaugeOverlay() {
        return getCurrentGauge().getGaugeOverlay();
    }

    @Override
    protected GaugeInfo getGaugeColor() {
        return getCurrentGauge().getGaugeColor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GuiTankGauge<?, ?> currentGauge = getCurrentGaugeNoFallback();
        if (currentGauge == null) {
            return fluidGauge.mouseClicked(mouseX, mouseY, button) | gasGauge.mouseClicked(mouseX, mouseY, button);
        }
        return currentGauge.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void applyRenderColor() {
        GuiTankGauge<?, ?> currentGauge = getCurrentGaugeNoFallback();
        if (currentGauge != null) {
            currentGauge.applyRenderColor();
        }
    }

    @Override
    public int getScaledLevel() {
        GuiTankGauge<?, ?> currentGauge = getCurrentGaugeNoFallback();
        return currentGauge == null ? 0 : currentGauge.getScaledLevel();
    }

    @Nullable
    @Override
    public TextureAtlasSprite getIcon() {
        return getCurrentGauge().getIcon();
    }

    @Nullable
    @Override
    public ITextComponent getLabel() {
        return label;
    }

    @Override
    public List<String> getTooltipText() {
        return getCurrentGauge().getTooltipText();
    }

    @Nullable
    @Override
    public TransmissionType getTransmission() {
        return getCurrentGauge().getTransmission();
    }

    private GuiTankGauge<?, ?> getCurrentGauge() {
        GuiTankGauge<?, ?> currentGauge = getCurrentGaugeNoFallback();
        return currentGauge == null ? fluidGauge : currentGauge;
    }

    @Nullable
    private GuiTankGauge<?, ?> getCurrentGaugeNoFallback() {
        SynchronizedTankData structure = tileEntity.structure;
        if (structure == null) {
            return null;
        }
        MergedTank.CurrentType currentType = structure.inventoryMergedTank.getCurrentType();
        if (currentType == MergedTank.CurrentType.FLUID) {
            return fluidGauge;
        }
        if (currentType.isGas()) {
            return gasGauge;
        }
        return null;
    }

    @Nullable
    private mekanism.api.fluid.IExtendedFluidTank getFluidTank() {
        SynchronizedTankData structure = tileEntity.structure;
        return structure == null ? null : structure.inventoryMergedTank.getFluidTank();
    }

    @Nullable
    private mekanism.api.gas.IExtendedGasTank getGasTank() {
        SynchronizedTankData structure = tileEntity.structure;
        return structure == null ? null : structure.inventoryMergedTank.getGasTank();
    }

    private List<mekanism.api.fluid.IExtendedFluidTank> getFluidTanks() {
        return java.util.Collections.singletonList(getFluidTank());
    }

    private List<mekanism.api.gas.IExtendedGasTank> getGasTanks() {
        return java.util.Collections.singletonList(getGasTank());
    }

    @Override
    public boolean isValidClickButton(int button) {
        return fluidGauge.isValidClickButton(button) || gasGauge.isValidClickButton(button);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return super.clicked(mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        if (getCurrentGaugeNoFallback() == null) {
            ItemStack stack = minecraft.player.inventory.getItemStack();
            if (!stack.isEmpty() && GasInventorySlot.getContainedGas(stack) != null) {
                gasGauge.renderToolTip(mouseX, mouseY);
                return;
            }
        }
        super.renderToolTip(mouseX, mouseY);
    }
}
