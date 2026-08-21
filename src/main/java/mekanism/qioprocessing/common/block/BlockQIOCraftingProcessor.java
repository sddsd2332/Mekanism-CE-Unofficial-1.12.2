package mekanism.qioprocessing.common.block;

import mekanism.common.block.BlockMekanismContainer;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateQIOComponent;
import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingCommonProxy;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;
import mekanism.common.util.SecurityUtils;

/** Shared block implementation for ordinary and tiered QIO workbench processors. */
/**
 * QIO 处理模块中的 BlockQIOCraftingProcessor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class BlockQIOCraftingProcessor extends BlockMekanismContainer {

    private final Supplier<? extends QIOCraftingProcessor> tileFactory;

    public BlockQIOCraftingProcessor(@Nonnull Supplier<? extends QIOCraftingProcessor> tileFactory) {
        super(Material.IRON);
        this.tileFactory = Objects.requireNonNull(tileFactory, "tileFactory");
        setHardness(3.5F);
        setResistance(8F);
        setCreativeTab(MekanismQIOProcessing.TAB);
        setDefaultState(blockState.getBaseState()
              .withProperty(BlockStateFacing.facingProperty, EnumFacing.NORTH)
              .withProperty(BlockStateQIOComponent.activeProperty, false)
              .withProperty(BlockStateQIOCraftingProcessor.workingProperty, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateQIOCraftingProcessor(this);
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(world, pos);
        if (tile instanceof QIOCraftingProcessor processor) {
            return state.withProperty(BlockStateFacing.facingProperty, processor.facing)
                  .withProperty(BlockStateQIOComponent.activeProperty, processor.isActive())
                  .withProperty(BlockStateQIOCraftingProcessor.workingProperty,
                        processor.isWorking());
        }
        return state;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
          EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (placer != null && tile instanceof TileEntityBasicBlock basicTile) {
            int side = MathHelper.floor(placer.rotationYaw * 4F / 360F + 0.5D) & 3;
            basicTile.setFacing(switch (side) {
                case 1 -> EnumFacing.EAST;
                case 2 -> EnumFacing.SOUTH;
                case 3 -> EnumFacing.WEST;
                default -> EnumFacing.NORTH;
            });
        }
        if (tile instanceof ISecurityTile securityTile && placer instanceof EntityPlayer player &&
              securityTile.getSecurity().getOwnerUUID() == null) {
            securityTile.getSecurity().setOwnerUUID(player.getUniqueID());
        }
        MekanismPlacementData.apply(world, pos, placer, stack);
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
          net.minecraft.block.Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityBasicBlock basicTile) {
                basicTile.onNeighborChange(neighborBlock);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
          EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY,
          float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof QIOCraftingProcessor)) {
            return false;
        }
        if (!SecurityUtils.canAccess(player, tile)) {
            SecurityUtils.displayNoAccess(player);
            return true;
        }
        player.openGui(MekanismQIOProcessing.instance,
              QIOProcessingCommonProxy.GUI_CRAFTING_PROCESSOR, world, pos.getX(), pos.getY(),
              pos.getZ());
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return tileFactory.get();
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world,
          @Nonnull BlockPos pos) {
        ItemStack stack = new ItemStack(this);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof QIOCraftingProcessor processor &&
              stack.getItem() instanceof ItemBlockQIOComponent item) {
            item.setOwnerUUID(stack, processor.getSecurity().getOwnerUUID());
            item.setSecurity(stack, processor.getSecurity().getMode());
            if (processor.getQIOFrequency() != null) {
                item.setFrequency(stack, processor.getQIOFrequency().getIdentity());
            }
            item.setSustainedInventory(processor.getInventory(), stack);
            item.setSustainedQIOData(processor, stack);
        }
        return stack;
    }

    @Nonnull
    @Override
    @Deprecated
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityBasicBlock basicTile && axis.getAxis().isHorizontal()) {
            basicTile.setFacing(axis);
            return true;
        }
        return false;
    }
}
