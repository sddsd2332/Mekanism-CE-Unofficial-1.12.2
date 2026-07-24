package mekanism.client.gui.item;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.GuiArrowSelection;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.item.SeismicReaderContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

@SideOnly(Side.CLIENT)
public class GuiSeismicReader extends GuiMekanism<SeismicReaderContainer> {

    private final List<BlockInfo> blockList = new ArrayList<>();
    private final Map<BlockStateKey, Integer> blockFrequencies = new HashMap<>();
    private final Map<Fluid, Integer> fluidFrequencies = new HashMap<>();
    private GuiScrollBar scrollBar;
    private MekanismButton upButton;
    private MekanismButton downButton;

    public GuiSeismicReader(InventoryPlayer inventory, EnumHand hand, ItemStack stack) {
        super(new SeismicReaderContainer(inventory, hand, stack));
        init(inventory);
    }

    public GuiSeismicReader(InventoryPlayer inventory, EnumHand hand, int itemSlot, ItemStack stack) {
        super(new SeismicReaderContainer(inventory, hand, itemSlot, stack));
        init(inventory);
    }

    private void init(InventoryPlayer inventory) {
        xSize = 150;
        ySize = 182;
        calculate(inventory.player.world, inventory.player.getPosition(), inventory.player);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 5, 11, 69, 50, this::getInfoText).padding(3).clearFormat());
        addButton(new GuiInnerScreen(this, 77, 11, 51, 160));
        scrollBar = addButton(new GuiScrollBar(this, 129, 25, 132, blockList::size, () -> 1));
        addButton(new GuiArrowSelection(this, 79, 81, () -> new TextComponentString(Integer.toString(getDisplayLayer()))));
        upButton = addButton(new MekanismImageButton(this, 129, 11, 14, getButtonLocation("up"), () -> scrollBar.adjustScroll(1)));
        downButton = addButton(new MekanismImageButton(this, 129, 157, 14, getButtonLocation("down"), () -> scrollBar.adjustScroll(-1)));
        updateEnabledButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateEnabledButtons();
    }

    private void updateEnabledButtons() {
        if (scrollBar == null || upButton == null || downButton == null) {
            return;
        }
        int currentSelection = scrollBar.getCurrentSelection();
        upButton.active = currentSelection > 0;
        downButton.active = currentSelection + 1 < blockList.size();
    }

    private void calculate(World world, BlockPos playerPos, EntityPlayer player) {
        int maxY = Math.min(world.getHeight() - 1, playerPos.getY());
        for (BlockPos pos = new BlockPos(playerPos.getX(), 0, playerPos.getZ()); pos.getY() <= maxY; pos = pos.up()) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock().isAir(state, world, pos)) {
                state = Blocks.AIR.getDefaultState();
            }
            BlockInfo info = BlockInfo.create(world, pos, state, player);
            blockList.add(info);
            blockFrequencies.merge(info.getBlockStateKey(), 1, Integer::sum);
            if (info.fluid != null) {
                fluidFrequencies.merge(info.fluid, 1, Integer::sum);
            }
        }
    }

    private List<ITextComponent> getInfoText() {
        int currentLayer = getCurrentLayer();
        if (currentLayer < 0) {
            return Collections.emptyList();
        }
        BlockInfo info = blockList.get(currentLayer);
        List<ITextComponent> text = new ArrayList<>();
        if (info.fluid == null || !(info.block instanceof BlockLiquid)) {
            text.add(new TextComponentString(info.getDisplayName()));
            text.add(MekanismLang.ABUNDANCY.translate(blockFrequencies.getOrDefault(info.getBlockStateKey(), 0)));
        }
        if (info.fluid != null) {
            text.add(new TextComponentString(info.fluid.getLocalizedName(new FluidStack(info.fluid, 1))));
            text.add(MekanismLang.ABUNDANCY.translate(fluidFrequencies.getOrDefault(info.fluid, 0)));
        }
        return text;
    }

    private int getCurrentLayer() {
        if (blockList.isEmpty() || scrollBar == null) {
            return -1;
        }
        return blockList.size() - scrollBar.getCurrentSelection() - 1;
    }

    private int getDisplayLayer() {
        int layer = getCurrentLayer();
        return layer < 0 ? 0 : layer;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        int currentLayer = getCurrentLayer();
        for (int i = 0; i < 9; i++) {
            int layer = currentLayer + (i - 4);
            if (0 <= layer && layer < blockList.size()) {
                BlockInfo info = blockList.get(layer);
                int renderX = 95;
                int renderY = 146 - 16 * i;
                GlStateManager.pushMatrix();
                if (i == 4) {
                    info.render(this, renderX, renderY);
                } else {
                    GlStateManager.translate(renderX, renderY, 0);
                    if (i < 4) {
                        GlStateManager.translate(1.7F, 2.5F, 0);
                    } else {
                        GlStateManager.translate(1.5F, 0, 0);
                    }
                    GlStateManager.scale(0.8F, 0.8F, 0.8F);
                    info.render(this, 0, 0);
                }
                GlStateManager.popMatrix();
            }
        }
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int delta = org.lwjgl.input.Mouse.getEventDWheel();
        if (delta != 0 && windows.isEmpty()) {
            scrollBar.adjustScroll(delta);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class BlockStateKey {

        private final Block block;
        private final int meta;

        private BlockStateKey(IBlockState state) {
            block = state.getBlock();
            meta = block.getMetaFromState(state);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BlockStateKey)) {
                return false;
            }
            BlockStateKey other = (BlockStateKey) o;
            return meta == other.meta && block == other.block;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(block) + meta;
        }
    }

    private static class BlockInfo {

        private final IBlockState state;
        private final Block block;
        private final BlockStateKey blockStateKey;
        private final Fluid fluid;
        private final ItemStack itemStack;

        private BlockInfo(IBlockState state, Fluid fluid, ItemStack itemStack) {
            this.state = state;
            block = state.getBlock();
            blockStateKey = new BlockStateKey(state);
            this.fluid = fluid;
            this.itemStack = itemStack;
        }

        private static BlockInfo create(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
            Block block = state.getBlock();
            Fluid fluid = getFluid(block);
            ItemStack stack = getPickStack(world, pos, state, player);
            if (stack.isEmpty() && fluid == null) {
                stack = new ItemStack(block, 1, block.getMetaFromState(state));
            }
            return new BlockInfo(state, fluid, stack);
        }

        private static ItemStack getPickStack(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
            try {
                Vec3d hit = new Vec3d(pos).add(0.5, 0.5, 0.5);
                RayTraceResult target = new RayTraceResult(hit, EnumFacing.UP, pos);
                return state.getBlock().getPickBlock(state, target, world, pos, player);
            } catch (RuntimeException ignored) {
                return ItemStack.EMPTY;
            }
        }

        private static Fluid getFluid(Block block) {
            if (block instanceof IFluidBlock fluidBlock) {
                return fluidBlock.getFluid();
            } else if (block instanceof BlockLiquid) {
                if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
                    return net.minecraftforge.fluids.FluidRegistry.WATER;
                } else if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
                    return net.minecraftforge.fluids.FluidRegistry.LAVA;
                }
            }
            return null;
        }

        private BlockStateKey getBlockStateKey() {
            return blockStateKey;
        }

        private String getDisplayName() {
            if (fluid != null) {
                return fluid.getLocalizedName(new FluidStack(fluid, 1));
            }
            return itemStack.isEmpty() ? state.getBlock().getLocalizedName() : itemStack.getDisplayName();
        }

        private void render(GuiSeismicReader gui, int x, int y) {
            if (fluid != null) {
                FluidStack stack = new FluidStack(fluid, 1);
                TextureAtlasSprite texture = MekanismRenderer.getFluidTexture(stack, MekanismRenderer.FluidType.STILL);
                MekanismRenderer.color(stack);
                GuiUtils.drawTiledSprite(x, y, 16, 16, 16, texture, GuiUtils.TilingDirection.DOWN_RIGHT);
                MekanismRenderer.resetColor();
            } else if (!itemStack.isEmpty()) {
                gui.renderItem(itemStack, x, y);
            }
        }
    }
}
