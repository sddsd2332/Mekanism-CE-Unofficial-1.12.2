package mekanism.generators.common.tile.turbine;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.multiblock.TileEntityInternalMultiblock;
import mekanism.common.tile.TileEntityStructuralGlass;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class TileEntityTurbineRotor extends TileEntityInternalMultiblock {

    private static final int MAX_RENDER_HORIZONTAL_RADIUS = 4;
    private static final int RENDER_MIN_Y_OFFSET = -2;
    private static final int RENDER_MAX_Y_OFFSET = 3;

    // Blades on this rotor
    public int blades = 0;

    // Position of this rotor, relative to bottom
    private int position = -1;

    // Rendering helpers
    public float rotationLower;
    public float rotationUpper;
    @SideOnly(Side.CLIENT)
    private static final int TURBINE_GLASS_RAY_MAX_STEPS = 20;
    @SideOnly(Side.CLIENT)
    private static final double TURBINE_PROBE_MARGIN = 0.03D;

    @Override
    public void onNeighborChange(Block block) {
        if (!isRemote()) {
            updateRotors();
        }
    }

    public void updateRotors() {
        // In order to render properly, each rotor has to know its position, relative to other contiguous rotors
        // along the Z axis. When a neighbor changes, rescan the rotors and figure out everyone's position
        // N.B. must be in bottom->top order.

        // Find the bottom-most rotor and start scan from there
        TileEntityTurbineRotor rotor = nextRotor(getPos().down());
        if (rotor != null) {
            rotor.updateRotors();
        } else {
            // This is the bottom-most rotor, so start scan up
            scanRotors(0);
        }
    }

    private void scanRotors(int index) {
        if (index != position) {
            // Our position has changed, update and generate an update packet for client
            position = index;
            sendUpdatePacket();
        }

        // Pass the scan along to next rotor up, along with their new index
        TileEntityTurbineRotor rotor = nextRotor(getPos().up());
        if (rotor != null) {
            rotor.scanRotors(index + 1);
        }
    }


    public boolean addBlade() {
        // If the the rotor beneath has less than two blades, add to it
        TileEntityTurbineRotor next = nextRotor(getPos().down());
        if (next != null && next.blades < 2) {
            return next.addBlade();
        } else if (blades < 2) {
            // Add the blades to this rotor
            blades++;
            // Update client state
            sendUpdatePacket();
            return true;
        }

        // This rotor and the rotor below are full up; pass the call
        // on up to the next rotor in stack
        next = nextRotor(getPos().up());
        if (next != null) {
            return next.addBlade();
        }
        return false;
    }

    public boolean removeBlade() {
        // If the the rotor above has any blades, remove them first
        TileEntityTurbineRotor next = nextRotor(getPos().up());
        if (next != null && next.blades > 0) {
            return next.removeBlade();
        } else if (blades > 0) {
            // Remove blades from this rotor
            blades--;

            // Update client state
            sendUpdatePacket();
            return true;
        }

        // This rotor and the rotor above are empty; pass the call
        // on up to the next rotor in stack
        next = nextRotor(getPos().down());
        if (next != null) {
            return next.removeBlade();
        }
        return false;
    }


    public int getHousedBlades() {
        return blades;
    }

    public int getPosition() {
        return position;
    }

    private TileEntityTurbineRotor nextRotor(BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityTurbineRotor rotor) {
            return rotor;
        }
        return null;
    }

    private void sendUpdatePacket() {
        Mekanism.packetHandler.sendUpdatePacket(this);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            int prevBlades = blades;
            int prevPosition = position;
            blades = dataStream.readInt();
            position = dataStream.readInt();

            if (prevBlades != blades || prevPosition != prevBlades) {
                rotationLower = 0;
                rotationUpper = 0;
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(blades);
        data.add(position);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        blades = nbtTags.getInteger("blades");
    }


    @Override
   public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("blades", getHousedBlades());
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (getMultiblock() != null || blades <= 0) {
            return new AxisAlignedBB(getPos());
        }
        return getStandaloneRenderBoundingBox(getPos());
    }

    public static AxisAlignedBB getStandaloneRenderBoundingBox(BlockPos pos) {
        return new AxisAlignedBB(pos.getX() - MAX_RENDER_HORIZONTAL_RADIUS, pos.getY() + RENDER_MIN_Y_OFFSET,
              pos.getZ() - MAX_RENDER_HORIZONTAL_RADIUS, pos.getX() + MAX_RENDER_HORIZONTAL_RADIUS + 1D,
              pos.getY() + RENDER_MAX_Y_OFFSET, pos.getZ() + MAX_RENDER_HORIZONTAL_RADIUS + 1D);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldCullForOcclusion() {
        if (MekanismConfig.current().client.GazeCullingTracking.val()
                && getMultiblock() != null && shouldRenderRotorModel()) {
            return false;
        }
        return super.shouldCullForOcclusion();
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldRenderRotorModel() {
        if (blades <= 0) {
            return false;
        }
        if (getMultiblock() == null) {
            return true;
        }
        if (world == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return false;
        }
        if (mc.gameSettings.thirdPersonView != 0) {
            return true;
        }
        Entity renderView = mc.getRenderViewEntity();
        if (renderView == null) {
            return false;
        }
        Vec3d eyePos = renderView.getPositionEyes(1.0F);
        for (Vec3d probePoint : buildRotorProbePoints()) {
            if (canSeePointThroughStructuralGlass(eyePos, probePoint)) {
                return true;
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    private List<Vec3d> buildRotorProbePoints() {
        double xMin = pos.getX() + TURBINE_PROBE_MARGIN;
        double yMin = pos.getY() + TURBINE_PROBE_MARGIN;
        double zMin = pos.getZ() + TURBINE_PROBE_MARGIN;
        double xMax = pos.getX() + 1D - TURBINE_PROBE_MARGIN;
        double yMax = pos.getY() + 1D - TURBINE_PROBE_MARGIN;
        double zMax = pos.getZ() + 1D - TURBINE_PROBE_MARGIN;
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double[] ys = new double[]{
                yMin + (yMax - yMin) * 0.2D,
                yMin + (yMax - yMin) * 0.5D,
                yMin + (yMax - yMin) * 0.8D
        };
        List<Vec3d> probes = new ArrayList<>(27);
        for (double y : ys) {
            probes.add(new Vec3d(centerX, y, centerZ));
            probes.add(new Vec3d(xMin, y, centerZ));
            probes.add(new Vec3d(xMax, y, centerZ));
            probes.add(new Vec3d(centerX, y, zMin));
            probes.add(new Vec3d(centerX, y, zMax));
            probes.add(new Vec3d(xMin, y, zMin));
            probes.add(new Vec3d(xMin, y, zMax));
            probes.add(new Vec3d(xMax, y, zMin));
            probes.add(new Vec3d(xMax, y, zMax));
        }
        return probes;
    }

    @SideOnly(Side.CLIENT)
    private boolean canSeePointThroughStructuralGlass(Vec3d eyePos, Vec3d target) {
        Vec3d start = eyePos;
        Vec3d direction = target.subtract(eyePos);
        double distanceSq = direction.lengthSquared();
        if (distanceSq <= 1.0E-8D) {
            return true;
        }
        Vec3d directionNorm = direction.scale(1.0D / Math.sqrt(distanceSq));
        boolean passedStructuralGlass = false;
        for (int i = 0; i < TURBINE_GLASS_RAY_MAX_STEPS; i++) {
            RayTraceResult trace = world.rayTraceBlocks(start, target, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) {
                return passedStructuralGlass;
            }
            BlockPos hitPos = trace.getBlockPos();
            if (pos.equals(hitPos)) {
                return passedStructuralGlass;
            }
            IBlockState hitState = world.getBlockState(hitPos);
            if (isStructuralGlass(hitPos)) {
                passedStructuralGlass = true;
            }
            if (!isTransparentForRotorRay(hitState, hitPos)) {
                return false;
            }
            if (trace.hitVec == null) {
                return false;
            }
            start = trace.hitVec.add(directionNorm.scale(0.01D));
            if (start.squareDistanceTo(target) < 1.0E-6D) {
                return passedStructuralGlass;
            }
        }
        return passedStructuralGlass;
    }

    @SideOnly(Side.CLIENT)
    private boolean isStructuralGlass(BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityStructuralGlass;
    }

    @SideOnly(Side.CLIENT)
    private boolean isTransparentForRotorRay(IBlockState state, BlockPos pos) {
        if (state == null) {
            return false;
        }
        if (isStructuralGlass(pos) || state.getMaterial().isLiquid()) {
            return true;
        }
        if (!state.isFullCube()) {
            return true;
        }
        if (!state.isOpaqueCube()) {
            return true;
        }
        return state.getBlock().isTranslucent(state);
    }


    @Override
    public void setMultiblock(String id) {
        // Override the multiblock setter so that we can be sure to relay the ID down to the client; otherwise,
        // the rendering won't work properly
        super.setMultiblock(id);
        sendUpdatePacket();
    }
}
