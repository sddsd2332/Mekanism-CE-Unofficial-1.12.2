package mekanism.common.block;

import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MultipartUtils;
import mekanism.common.util.MultipartUtils.AdvancedRayTraceResult;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/** Shared six-direction placement and 26.2 model shapes for the thin QIO components. */
final class QIOBlockShapes {

    static final AxisAlignedBB[][] DASHBOARD = createShapes(false,
          box(1, 15, 1, 15, 16, 15));

    static final AxisAlignedBB[][] IMPORTER = createShapes(true,
          box(4, 0, 4, 12, 1, 12),
          box(5, 4, 5, 11, 5, 6),
          box(5, 4, 10, 11, 5, 11),
          box(5, 4, 6, 6, 5, 10),
          box(10, 4, 6, 11, 5, 10),
          box(10, 2, 6, 11, 3, 10),
          box(5, 2, 6, 6, 3, 10),
          box(5, 2, 10, 11, 3, 11),
          box(5, 2, 5, 11, 3, 6),
          box(9, 1, 6, 10, 6, 7),
          box(9, 1, 9, 10, 6, 10),
          box(6, 1, 9, 7, 6, 10),
          box(6, 1, 6, 7, 6, 7),
          box(9, 8, 9, 10, 9, 10),
          box(6, 9, 6, 7, 10, 7),
          box(7.5, 1, 7.5, 8.5, 6, 8.5),
          box(5, 6, 5, 11, 7, 11),
          box(9, 7, 9, 10, 8, 10),
          box(6, 7, 6, 7, 9, 7));

    static final AxisAlignedBB[][] EXPORTER = createShapes(true,
          box(4, 0, 4, 12, 1, 12),
          box(5, 4, 5, 11, 5, 6),
          box(5, 4, 10, 11, 5, 11),
          box(5, 4, 6, 6, 5, 10),
          box(10, 4, 6, 11, 5, 10),
          box(10, 2, 6, 11, 3, 10),
          box(5, 2, 6, 6, 3, 10),
          box(5, 2, 10, 11, 3, 11),
          box(5, 2, 5, 11, 3, 6),
          box(9, 1, 6, 10, 6, 7),
          box(9, 1, 9, 10, 6, 10),
          box(6, 1, 9, 7, 6, 10),
          box(6, 1, 6, 7, 6, 7),
          box(7, 7, 7, 9, 8, 9),
          box(7, 8.01, 7, 9, 9.01, 9),
          box(7.5, 1, 7.5, 8.5, 6, 8.5),
          box(5, 6, 5, 11, 7, 11),
          box(6, 8, 6, 10, 9, 10),
          box(9, 9, 9, 10, 10, 10),
          box(6, 9, 9, 7, 10, 10),
          box(6, 9, 6, 7, 10, 7),
          box(9, 9, 6, 10, 10, 7));

    static final AxisAlignedBB[][] REDSTONE_ADAPTER = createShapes(true,
          box(4, 0, 4, 12, 1, 12),
          box(5, 2, 5, 11, 3, 6),
          box(5, 2, 10, 11, 3, 11),
          box(5, 2, 6, 6, 3, 10),
          box(10, 2, 6, 11, 3, 10),
          box(9, 1, 6, 10, 4, 7),
          box(9, 1, 9, 10, 4, 10),
          box(6, 1, 9, 7, 4, 10),
          box(6, 1, 6, 7, 4, 7),
          box(7.5, 1, 7.5, 8.5, 4, 8.5),
          box(5, 4, 5, 11, 5, 11),
          box(6, 8, 6, 10, 9, 10),
          box(7, 5, 7, 9, 11, 9));

    private QIOBlockShapes() {
    }

    static IBlockState getStateForPlacement(IBlockState state, EnumFacing selectedFace) {
        return state.withProperty(mekanism.common.block.states.BlockStateFacing.facingProperty, selectedFace);
    }

    static void setPlacedFacing(World world, BlockPos pos, IBlockState state) {
        TileEntityBasicBlock tile = MekanismUtils.getTileEntitySafe(world, pos, TileEntityBasicBlock.class);
        if (tile != null) {
            tile.setFacing(state.getValue(mekanism.common.block.states.BlockStateFacing.facingProperty));
        }
    }

    static EnumFacing[] getValidRotations(World world, BlockPos pos) {
        TileEntityBasicBlock tile = MekanismUtils.getTileEntitySafe(world, pos, TileEntityBasicBlock.class);
        EnumFacing[] valid = new EnumFacing[6];
        if (tile != null) {
            for (EnumFacing direction : EnumFacing.VALUES) {
                if (tile.canSetFacing(direction)) {
                    valid[direction.ordinal()] = direction;
                }
            }
        }
        return valid;
    }

    static boolean rotate(World world, BlockPos pos, EnumFacing facing) {
        TileEntityBasicBlock tile = MekanismUtils.getTileEntitySafe(world, pos, TileEntityBasicBlock.class);
        if (tile != null && tile.canSetFacing(facing)) {
            tile.setFacing(facing);
            return true;
        }
        return false;
    }

    static EnumFacing getFacing(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntityBasicBlock tile = MekanismUtils.getTileEntitySafe(world, pos, TileEntityBasicBlock.class);
        if (tile != null && tile.facing != null) {
            return tile.facing;
        }
        try {
            return state.getValue(mekanism.common.block.states.BlockStateFacing.facingProperty);
        } catch (RuntimeException ignored) {
            return EnumFacing.NORTH;
        }
    }

    @Nonnull
    static AxisAlignedBB getBounds(AxisAlignedBB[][] shapes, IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB[] boxes = getBoxes(shapes, state, world, pos);
        AxisAlignedBB bounds = boxes[0];
        for (int index = 1; index < boxes.length; index++) {
            bounds = bounds.union(boxes[index]);
        }
        return bounds;
    }

    static AxisAlignedBB[] getBoxes(AxisAlignedBB[][] shapes, IBlockState state, IBlockAccess world, BlockPos pos) {
        return shapes[getFacing(state, world, pos).ordinal()];
    }

    static void addCollisionBoxes(AxisAlignedBB[][] shapes, IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
          List<AxisAlignedBB> collidingBoxes) {
        for (AxisAlignedBB box : getBoxes(shapes, state, world, pos)) {
            AxisAlignedBB offsetBox = box.offset(pos);
            if (entityBox.intersects(offsetBox)) {
                collidingBoxes.add(offsetBox);
            }
        }
    }

    @Nullable
    static RayTraceResult collisionRayTrace(AxisAlignedBB[][] shapes, IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        AdvancedRayTraceResult result = MultipartUtils.collisionRayTrace(pos, start, end, Arrays.asList(getBoxes(shapes, state, world, pos)));
        return result == null ? null : result.hit;
    }

    private static AxisAlignedBB box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(minX / 16, minY / 16, minZ / 16, maxX / 16, maxY / 16, maxZ / 16);
    }

    private static AxisAlignedBB[][] createShapes(boolean invert, AxisAlignedBB... source) {
        AxisAlignedBB[][] shapes = new AxisAlignedBB[6][];
        for (EnumFacing facing : EnumFacing.VALUES) {
            EnumFacing rotation = invert ? facing.getOpposite() : facing;
            AxisAlignedBB[] rotated = new AxisAlignedBB[source.length];
            for (int index = 0; index < source.length; index++) {
                rotated[index] = rotate(source[index], rotation);
            }
            shapes[facing.ordinal()] = rotated;
        }
        return shapes;
    }

    private static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing facing) {
        AxisAlignedBB centered = box.offset(-0.5, -0.5, -0.5);
        AxisAlignedBB rotated = switch (facing) {
            case UP -> new AxisAlignedBB(centered.minX, -centered.minY, -centered.minZ,
                    centered.maxX, -centered.maxY, -centered.maxZ);
            case NORTH -> new AxisAlignedBB(centered.minX, -centered.minZ, centered.minY,
                    centered.maxX, -centered.maxZ, centered.maxY);
            case SOUTH -> new AxisAlignedBB(-centered.minX, -centered.minZ, -centered.minY,
                    -centered.maxX, -centered.maxZ, -centered.maxY);
            case WEST -> new AxisAlignedBB(centered.minY, -centered.minZ, -centered.minX,
                    centered.maxY, -centered.maxZ, -centered.maxX);
            case EAST -> new AxisAlignedBB(-centered.minY, -centered.minZ, centered.minX,
                    -centered.maxY, -centered.maxZ, centered.maxX);
            default -> centered;
        };
        return rotated.offset(0.5, 0.5, 0.5);
    }
}
