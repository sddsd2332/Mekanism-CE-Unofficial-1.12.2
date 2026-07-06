package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.api.Pos3D;
import mekanism.api.RelativeSide;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.LangUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class SideDataButton extends BasicColorButton {

    private final Supplier<DataType> dataTypeSupplier;
    private final Supplier<EnumColor> colorSupplier;
    private final int sideIndex;
    private final RelativeSide side;
    private final ItemStack otherBlockItem;
    private final boolean displayDataType;

    private List<String> lastInfo = Collections.emptyList();

    public SideDataButton(IGuiWrapper gui, int x, int y, int sideIndex, RelativeSide side, Supplier<DataType> dataTypeSupplier,
          Supplier<EnumColor> colorSupplier, TileEntityBasicBlock tile, Runnable onLeftClick, Runnable onRightClick, boolean displayDataType) {
        super(gui, x, y, 22, () -> {
            DataType dataType = dataTypeSupplier.get();
            return dataType == null || dataType == DataType.EMPTY ? null : colorSupplier.get();
        }, onLeftClick, onRightClick);
        this.sideIndex = sideIndex;
        this.side = side;
        this.dataTypeSupplier = dataTypeSupplier;
        this.colorSupplier = colorSupplier;
        this.displayDataType = displayDataType;
        World tileWorld = tile.getWorld();
        if (tileWorld != null) {
            EnumFacing facing = side.getDirection(tile.facing);
            BlockPos otherBlockPos = tile.getPos().offset(facing);
            IBlockState blockOnSide = tileWorld.getBlockState(otherBlockPos);
            RayTraceResult target = new RayTraceResult(new Pos3D(tile).centre().translate(facing, 0.501), facing, otherBlockPos);
            if (!blockOnSide.getBlock().isAir(blockOnSide, tileWorld, otherBlockPos)) {
                otherBlockItem = blockOnSide.getBlock().getPickBlock(blockOnSide, target, tileWorld, otherBlockPos, Minecraft.getMinecraft().player);
            } else {
                otherBlockItem = ItemStack.EMPTY;
            }
        } else {
            otherBlockItem = ItemStack.EMPTY;
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);

        if (!otherBlockItem.isEmpty()) {
            gui().renderItem(otherBlockItem, getButtonX() + 3, getButtonY() + 3);
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        List<String> tooltipLines = getTooltipLines();
        if (!tooltipLines.isEmpty()) {
            displayTooltips(tooltipLines, mouseX, mouseY);
        }
    }

    private List<String> getTooltipLines() {
        DataType dataType = getDataType();
        if (dataType == null || dataType == DataType.EMPTY) {
            lastInfo = Collections.emptyList();
            return lastInfo;
        }
        List<String> tooltipLines = new ArrayList<>(3);
        tooltipLines.add(side.getTranslationKey());
        if (displayDataType) {
            tooltipLines.add(dataType.getColor() + dataType.localize());
        } else {
            EnumColor color = getColor();
            tooltipLines.add(color == null ? LangUtils.localize("gui.none") : color.getColoredName());
        }
        if (!otherBlockItem.isEmpty() && otherBlockItem.getItem() != Items.AIR) {
            tooltipLines.add(otherBlockItem.getItem().getItemStackDisplayName(otherBlockItem));
        }
        lastInfo = tooltipLines;
        return lastInfo;
    }

    public int getSideIndex() {
        return sideIndex;
    }

    public DataType getDataType() {
        return dataTypeSupplier.get();
    }

    @Override
    public EnumColor getColor() {
        return colorSupplier.get();
    }

    public ItemStack getItem() {
        return otherBlockItem;
    }
}
