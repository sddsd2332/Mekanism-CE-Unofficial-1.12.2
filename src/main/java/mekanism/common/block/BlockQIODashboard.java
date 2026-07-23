package mekanism.common.block;

import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.QIOGuiConstants;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateQIOComponent;
import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.SecurityUtils;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** Wall-mounted-capable QIO network dashboard. */
public class BlockQIODashboard extends BlockMekanismContainer {

    public BlockQIODashboard() {
        super(Material.IRON);
        setHardness(3.5F);
        setResistance(8F);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateQIOComponent(this);
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockStateFacing.facingProperty, EnumFacing.NORTH);
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
        if (tile instanceof TileEntityBasicBlock && ((TileEntityBasicBlock) tile).facing != null) {
            state = state.withProperty(BlockStateFacing.facingProperty, ((TileEntityBasicBlock) tile).facing);
        }
        return tile instanceof TileEntityQIOComponent
              ? state.withProperty(BlockStateQIOComponent.activeProperty, ((TileEntityQIOComponent) tile).isActive()) : state;
    }

    @Nonnull
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
          int meta, EntityLivingBase placer, EnumHand hand) {
        return QIOBlockShapes.getStateForPlacement(getDefaultState(), facing);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        QIOBlockShapes.setPlacedFacing(world, pos, state);
        if (tile instanceof ISecurityTile && placer instanceof EntityPlayer && ((ISecurityTile) tile).getSecurity().getOwnerUUID() == null) {
            ((ISecurityTile) tile).getSecurity().setOwnerUUID(((EntityPlayer) placer).getUniqueID());
        }
        MekanismPlacementData.apply(world, pos, placer, stack);
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityBasicBlock) {
                ((TileEntityBasicBlock) tile).onNeighborChange(neighborBlock);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
          EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ISecurityTile && !SecurityUtils.canAccess(player, tile)) {
            SecurityUtils.displayNoAccess(player);
            return true;
        }
        player.openGui(Mekanism.instance, QIOGuiConstants.DASHBOARD, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return new TileEntityQIODashboard();
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        ItemStack stack = new ItemStack(MekanismBlocks.QIO_DASHBOARD);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityQIODashboard && stack.getItem() instanceof ItemBlockQIOComponent) {
            TileEntityQIODashboard dashboard = (TileEntityQIODashboard) tile;
            ItemBlockQIOComponent item = (ItemBlockQIOComponent) stack.getItem();
            item.setOwnerUUID(stack, dashboard.getSecurity().getOwnerUUID());
            item.setSecurity(stack, dashboard.getSecurity().getMode());
            if (dashboard.getQIOFrequency() != null) {
                item.setFrequency(stack, dashboard.getQIOFrequency().getIdentity());
            }
            item.setSustainedInventory(dashboard.getInventory(), stack);
            item.setSustainedQIOData(dashboard, stack);
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
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Nonnull
    @Override
    @Deprecated
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return QIOBlockShapes.getBounds(QIOBlockShapes.DASHBOARD, state, world, pos);
    }

    @Override
    @Deprecated
    public void addCollisionBoxToList(IBlockState state, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull AxisAlignedBB entityBox, @Nonnull List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean actualState) {
        QIOBlockShapes.addCollisionBoxes(QIOBlockShapes.DASHBOARD, state, world, pos, entityBox, collidingBoxes);
    }

    @Nullable
    @Override
    @Deprecated
    public RayTraceResult collisionRayTrace(IBlockState state, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull Vec3d start, @Nonnull Vec3d end) {
        return QIOBlockShapes.collisionRayTrace(QIOBlockShapes.DASHBOARD, state, world, pos, start, end);
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
