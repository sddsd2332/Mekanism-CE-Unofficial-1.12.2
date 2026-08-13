package mekanism.qioprocessing.common.block;

import mekanism.common.block.BlockMekanismContainer;
import mekanism.common.block.QIOBlockShapes;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateQIOComponent;
import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingCommonProxy;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared block lifecycle for the four fixed-responsibility processing terminals. */
public final class BlockQIOProcessingTerminal extends BlockMekanismContainer {

    private final Supplier<? extends QIOProcessingTerminal> tileFactory;

    public BlockQIOProcessingTerminal(
          @Nonnull Supplier<? extends QIOProcessingTerminal> tileFactory) {
        super(Material.IRON);
        this.tileFactory = Objects.requireNonNull(tileFactory, "tileFactory");
        setHardness(3.5F);
        setResistance(8F);
        setCreativeTab(MekanismQIOProcessing.TAB);
        setDefaultState(blockState.getBaseState()
              .withProperty(BlockStateFacing.facingProperty, EnumFacing.NORTH)
              .withProperty(BlockStateQIOComponent.activeProperty, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateQIOComponent(this);
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
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
          float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer,
          EnumHand hand) {
        return QIOBlockShapes.getStateForPlacement(getDefaultState(), facing);
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state,
          IBlockAccess world, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(world, pos);
        if (tile instanceof TileEntityBasicBlock basicTile) {
            state = state.withProperty(BlockStateFacing.facingProperty,
                  basicTile.facing);
        }
        if (tile instanceof QIOProcessingTerminal terminal) {
            state = state.withProperty(BlockStateQIOComponent.activeProperty,
                  terminal.isActive());
        }
        return state;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
          EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        QIOBlockShapes.setPlacedFacing(world, pos, state);
        if (tile instanceof ISecurityTile securityTile &&
            placer instanceof EntityPlayer player &&
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
          EntityPlayer player, EnumHand hand, EnumFacing side, float hitX,
          float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof QIOProcessingTerminal)) {
            return false;
        }
        if (!SecurityUtils.canAccess(player, tile)) {
            SecurityUtils.displayNoAccess(player);
            return true;
        }
        player.openGui(MekanismQIOProcessing.instance,
              QIOProcessingCommonProxy.GUI_TERMINAL, world, pos.getX(),
              pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return tileFactory.get();
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state,
          @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        ItemStack stack = new ItemStack(this);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof QIOProcessingTerminal terminal &&
            stack.getItem() instanceof ItemBlockQIOComponent item) {
            item.setOwnerUUID(stack, terminal.getSecurity().getOwnerUUID());
            item.setSecurity(stack, terminal.getSecurity().getMode());
            if (terminal.getQIOFrequency() != null) {
                item.setFrequency(stack, terminal.getQIOFrequency().getIdentity());
            }
            item.setSustainedQIOData(terminal, stack);
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
    @Deprecated
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @Deprecated
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @Deprecated
    public boolean isFullBlock(IBlockState state) {
        return false;
    }

    @Nonnull
    @Override
    @Deprecated
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state,
          BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Nonnull
    @Override
    @Deprecated
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world,
          BlockPos pos) {
        return QIOBlockShapes.getDashboardBounds(state, world, pos);
    }

    @Override
    @Deprecated
    public void addCollisionBoxToList(IBlockState state, @Nonnull World world,
          @Nonnull BlockPos pos, @Nonnull AxisAlignedBB entityBox,
          @Nonnull List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity,
          boolean actualState) {
        QIOBlockShapes.addDashboardCollisionBoxes(state, world, pos, entityBox,
              collidingBoxes);
    }

    @Nullable
    @Override
    @Deprecated
    public RayTraceResult collisionRayTrace(IBlockState state, @Nonnull World world,
          @Nonnull BlockPos pos, @Nonnull Vec3d start, @Nonnull Vec3d end) {
        return QIOBlockShapes.dashboardCollisionRayTrace(state, world, pos, start, end);
    }

    @Override
    public EnumFacing[] getValidRotations(World world, @Nonnull BlockPos pos) {
        return QIOBlockShapes.getValidRotations(world, pos);
    }

    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis) {
        return QIOBlockShapes.rotate(world, pos, axis);
    }
}
