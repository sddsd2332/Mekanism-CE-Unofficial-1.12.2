package mekanism.generators.common.tile.reactor;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.base.IActiveState;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.capabilities.holder.slot.ProxiedInventorySlotHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import mekanism.generators.common.FusionReactor;
import mekanism.generators.common.item.ItemHohlraum;
import mekanism.generators.common.slot.ReactorInventorySlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TileEntityReactorController extends TileEntityReactorBlock implements IActiveState {

    private static final String FUSION_PLASMA_TEMPERATURE = "fusionPlasmaTemperature";

    public VariableCapacityFluidTank waterTank = VariableCapacityFluidTank.input(this::getWaterTankCapacity,
          fluid -> RecipeHandler.Recipe.FUSION_COOLING.containsRecipe(fluid.getFluid()), this);
    public VariableCapacityFluidTank steamTank = VariableCapacityFluidTank.output(this::getSteamTankCapacity, BasicFluidTank.alwaysTrue, this);

    public BasicGasTank deuteriumTank = BasicGasTank.input(MekanismConfig.current().generators.FusionReactorsDeuteriumTank.val(),
          gas -> gas == MekanismFluids.Deuterium, this);
    public BasicGasTank tritiumTank = BasicGasTank.input(MekanismConfig.current().generators.FusionReactorsTritiumTank.val(),
          gas -> gas == MekanismFluids.Tritium, this);

    public BasicGasTank fuelTank = BasicGasTank.input(MekanismConfig.current().generators.FusionReactorsFuelTank.val(),
          gas -> gas == MekanismFluids.FusionFuel, this);

    public AxisAlignedBB box;
    public double clientTemp = 0;
    public boolean clientBurning = false;
    private SoundEvent soundEvent = new SoundEvent(new ResourceLocation(Mekanism.MODID, "tile.machine.fusionreactor"));
    @SideOnly(Side.CLIENT)
    private ISound activeSound;
    private int playSoundCooldown = 0;
    @SideOnly(Side.CLIENT)
    private static final int REACTOR_WINDOW_RAY_MAX_STEPS = 24;
    @SideOnly(Side.CLIENT)
    private static final double REACTOR_CORE_PROBE_RADIUS = 1.25D;
    @SideOnly(Side.CLIENT)
    private static final double REACTOR_CORE_PROBE_VERTICAL_RADIUS = 1.05D;
    private ReactorInventorySlot hohlraumSlot;

    public TileEntityReactorController() {
        super("ReactorController", MekanismConfig.current().generators.reactorGeneratorStorage.val());
        initializeInventorySlots();
    }

    private int getFusionTankCapacityMultiplier() {
        if (getReactor() == null) {
            return 1;
        }
        int rate = getReactor().getInjectionRate();
        int capRate = Math.min(Math.min(Math.max(1, rate), MekanismConfig.current().generators.reactorGeneratorInjectionRate.val()), 1000);
        return capRate - capRate % 2;
    }

    private int getWaterTankCapacity() {
        return getScaledTankCapacity(MekanismConfig.current().generators.FusionReactorsWaterTank.val());
    }

    private int getSteamTankCapacity() {
        return getScaledTankCapacity(MekanismConfig.current().generators.FusionReactorsSteamTank.val());
    }

    private int getScaledTankCapacity(int baseCapacity) {
        long capacity = (long) Math.max(0, baseCapacity) * Math.max(0, getFusionTankCapacityMultiplier());
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        hohlraumSlot = ReactorInventorySlot.at(
              stack -> stack.getItem() instanceof ItemHohlraum,
              (stack, automationType) -> automationType == AutomationType.INTERNAL ||
                    stack.getItem() instanceof ItemHohlraum hohlraum && hohlraum.isReadyForReaction(stack),
              listener, 80, 39
        );
        hohlraumSlot.setEnabledSupplier(this::isFormed);
        builder.addSlot(hohlraumSlot);
        IInventorySlotHolder slotHolder = builder.build();
        return ProxiedInventorySlotHolder.create(side -> isFormed() && slotHolder.canInsert(side), side -> isFormed() && slotHolder.canExtract(side),
              side -> side == null || isFormed() ? slotHolder.getInventorySlots(side) : Collections.emptyList());
    }

    public ReactorInventorySlot getHohlraumSlot() {
        return hohlraumSlot;
    }

    @Override
    public boolean isFrame() {
        return false;
    }

    public void radiateNeutrons(int neutrons) {
        //future impl
    }

    public void formMultiblock(boolean keepBurning) {
        if (getReactor() == null) {
            setReactor(new FusionReactor(this));
        }
        getReactor().formMultiblock(keepBurning);
    }

    public double getPlasmaTemp() {
        if (getReactor() == null || !getReactor().isFormed()) {
            return 0;
        }
        return getReactor().getPlasmaTemp();
    }

    public double getCaseTemp() {
        if (getReactor() == null || !getReactor().isFormed()) {
            return 0;
        }
        return getReactor().getCaseTemp();
    }

    public boolean getactivelyCooled() {
        return steamTank.getFluidAmount() < steamTank.getCapacity() && steamTank.getFluidAmount() != steamTank.getCapacity();
    }

    public void sanitizeStoredContents() {
        sanitizeGasTank(fuelTank, MekanismFluids.FusionFuel);
        sanitizeGasTank(deuteriumTank, MekanismFluids.Deuterium);
        sanitizeGasTank(tritiumTank, MekanismFluids.Tritium);
        sanitizeFluidTank(waterTank, true);
        sanitizeFluidTank(steamTank, false);
    }

    public void clampTanksToCapacity() {
        clampGasTank(fuelTank);
        clampGasTank(deuteriumTank);
        clampGasTank(tritiumTank);
        clampFluidTank(waterTank);
        clampFluidTank(steamTank);
    }

    public void sanitizeAndClampTanks() {
        sanitizeStoredContents();
        clampTanksToCapacity();
    }

    private void sanitizeGasTank(BasicGasTank tank, Gas expectedGas) {
        GasStack stored = tank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() != expectedGas)) {
            tank.setEmpty();
        }
    }

    private void clampGasTank(BasicGasTank tank) {
        GasStack stored = tank.getGas();
        if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    private void sanitizeFluidTank(VariableCapacityFluidTank tank, boolean validateRecipeInput) {
        FluidStack stored = tank.getFluid();
        Fluid fluid = stored == null ? null : stored.getFluid();
        if (stored != null && (stored.amount <= 0 || fluid == null || validateRecipeInput && !RecipeHandler.Recipe.FUSION_COOLING.containsRecipe(fluid))) {
            tank.setEmpty();
        }
    }

    private void clampFluidTank(VariableCapacityFluidTank tank) {
        FluidStack stored = tank.getFluid();
        if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (isFormed()) {
            getReactor().simulate();
            if (!isRemote() && (getReactor().isBurning() != clientBurning || Math.abs(getReactor().getPlasmaTemp() - clientTemp) > 1000000)) {
                Mekanism.packetHandler.sendUpdatePacket(this);
                clientBurning = getReactor().isBurning();
                clientTemp = getReactor().getPlasmaTemp();
            }
        }
    }

    @Override
    public void onUpdateClient(){
        super.onUpdateClient();
        updateSound();
    }

    @SideOnly(Side.CLIENT)
    private void updateSound() {
        // If machine sounds are disabled, noop
        if (!MekanismConfig.current().client.enableMachineSounds.val()) {
            return;
        }
        if (isBurning() && !isInvalid()) {
            // If sounds are being muted, we can attempt to start them on every tick, only to have them
            // denied by the event bus, so use a cooldown period that ensures we're only trying once every
            // second or so to start a sound.
            if (--playSoundCooldown > 0) {
                return;
            }
            if (activeSound == null || !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(activeSound)) {
                activeSound = SoundHandler.startTileSound(soundEvent.getSoundName(), 1.0f, getPos());
                playSoundCooldown = 20;
            }
        } else if (activeSound != null) {
            SoundHandler.stopTileSound(getPos());
            activeSound = null;
            playSoundCooldown = 0;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (isRemote()) {
            updateSound();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        formMultiblock(true);
    }

    @Override
    public void onAdded() {
        super.onAdded();
        formMultiblock(true);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound tag) {
        super.writeCustomNBT(tag);
        tag.setBoolean("formed", isFormed());
        if (getReactor() != null) {
            tag.setDouble(FUSION_PLASMA_TEMPERATURE, getReactor().getPlasmaTemp());
            tag.setTag(NBTConstants.HEAT_STORED, getReactor().getHeatCapacitor().serializeNBT());
            tag.setInteger("injectionRate", getReactor().getInjectionRate());
            tag.setBoolean("burning", getReactor().isBurning());
        } else {
            tag.setInteger("injectionRate", 0);
            tag.setBoolean("burning", false);
        }
        tag.setTag("fuelTank", fuelTank.write(new NBTTagCompound()));
        tag.setTag("deuteriumTank", deuteriumTank.write(new NBTTagCompound()));
        tag.setTag("tritiumTank", tritiumTank.write(new NBTTagCompound()));
        tag.setTag("waterTank", waterTank.writeToNBT(new NBTTagCompound()));
        tag.setTag("steamTank", steamTank.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void readCustomNBT(NBTTagCompound tag) {
        super.readCustomNBT(tag);
        boolean formed = tag.getBoolean("formed");
        boolean hasReactorState = formed || tag.hasKey(FUSION_PLASMA_TEMPERATURE) ||
              tag.hasKey(NBTConstants.HEAT_STORED, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND) || tag.hasKey("injectionRate");
        if (hasReactorState) {
            setReactor(new FusionReactor(this));
            if (tag.hasKey(FUSION_PLASMA_TEMPERATURE)) {
                getReactor().setPlasmaTemp(tag.getDouble(FUSION_PLASMA_TEMPERATURE));
            }
            if (tag.hasKey(NBTConstants.HEAT_STORED, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
                getReactor().getHeatCapacitor().deserializeNBT(tag.getCompoundTag(NBTConstants.HEAT_STORED));
            }
            if (tag.hasKey("injectionRate")) {
                getReactor().setInjectionRate(tag.getInteger("injectionRate"));
            }
            getReactor().setBurning(tag.getBoolean("burning"));
            // Structure members do not persist their reactor reference. Keep the restored reactor unformed so
            // the normal server-side validation pass rebinds every block and refreshes adjacent connections.
            getReactor().updateTemperatures();
        }
        fuelTank.read(tag.getCompoundTag("fuelTank"));
        deuteriumTank.read(tag.getCompoundTag("deuteriumTank"));
        tritiumTank.read(tag.getCompoundTag("tritiumTank"));
        waterTank.readFromNBT(tag.getCompoundTag("waterTank"));
        steamTank.readFromNBT(tag.getCompoundTag("steamTank"));
        sanitizeAndClampTanks();
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        boolean formed = getReactor() != null && getReactor().isFormed();
        data.add(formed);
        if (formed) {
            data.add(getReactor().getPlasmaTemp());
            data.add(getReactor().getHeatCapacitor().getHeatCapacity());
            data.add(getReactor().getHeatCapacitor().getHeat());
            data.add(sanitizeHeatMetric(getReactor().lastTransferLoss));
            data.add(sanitizeHeatMetric(getReactor().lastEnvironmentLoss));
            data.add(getReactor().getInjectionRate());
            data.add(getReactor().isBurning());

            data.add(fuelTank.getStored());
            data.add(deuteriumTank.getStored());
            data.add(tritiumTank.getStored());
            /*
            TileUtils.addTankData(data,fuelTank);
            TileUtils.addTankData(data,deuteriumTank);
            TileUtils.addTankData(data,tritiumTank);
            */
            TileUtils.addTankData(data, waterTank);
            TileUtils.addTankData(data, steamTank);
        }
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                if (getReactor() != null) {
                    getReactor().setInjectionRate(dataStream.readInt());
                }
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            boolean formed = dataStream.readBoolean();
            if (formed) {
                if (getReactor() == null || !getReactor().formed) {
                    BlockPos corner = getPos().subtract(new Vec3i(2, 4, 2));
                    Mekanism.proxy.doMultiblockSparkle(this, corner, 5, 5, 6, tile -> tile instanceof TileEntityReactorBlock);
                }
                if (getReactor() == null) {
                    setReactor(new FusionReactor(this));
                    MekanismUtils.updateBlock(world, getPos());
                }

                getReactor().formed = true;
                getReactor().setPlasmaTemp(dataStream.readDouble());
                getReactor().getHeatCapacitor().setHeatCapacityFromPacket(dataStream.readDouble());
                getReactor().getHeatCapacitor().setHeat(dataStream.readDouble());
                double transferLoss = dataStream.readDouble();
                getReactor().lastTransferLoss = sanitizeHeatMetric(transferLoss);
                double environmentLoss = dataStream.readDouble();
                getReactor().lastEnvironmentLoss = sanitizeHeatMetric(environmentLoss);
                getReactor().setInjectionRate(dataStream.readInt());
                getReactor().setBurning(dataStream.readBoolean());

                fuelTank.setGas(new GasStack(MekanismFluids.FusionFuel, dataStream.readInt()));
                deuteriumTank.setGas(new GasStack(MekanismFluids.Deuterium, dataStream.readInt()));
                tritiumTank.setGas(new GasStack(MekanismFluids.Tritium, dataStream.readInt()));
               /*
                TileUtils.readTankData(dataStream,fuelTank);
                TileUtils.readTankData(dataStream,deuteriumTank);
                TileUtils.readTankData(dataStream,tritiumTank);
                */
                TileUtils.readTankData(dataStream, waterTank);
                TileUtils.readTankData(dataStream, steamTank);
                getReactor().updateTemperatures();
            } else if (getReactor() != null) {
                setReactor(null);
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    public boolean isFormed() {
        return getReactor() != null && getReactor().isFormed();
    }

    private static double sanitizeHeatMetric(double value) {
        return mekanism.api.heat.HeatAPI.isFinite(value) ?
              Math.max(0, Math.min(mekanism.api.heat.HeatAPI.MAX_HEAT, value)) : 0;
    }

    public boolean isBurning() {
        return getActive() && getReactor().isBurning();
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    @Override
    public boolean getActive() {
        return isFormed();
    }

    @Override
    public void setActive(boolean active) {
        if (active == (getReactor() == null)) {
            setReactor(active ? new FusionReactor(this) : null);
        }
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldCullForOcclusion() {
        if (MekanismConfig.current().client.GazeCullingTracking.val() && shouldRenderPlasmaCore()) {
            return false;
        }
        return super.shouldCullForOcclusion();
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldRenderPlasmaCore() {
        if (!isBurning() || !isFormed() || world == null) {
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
        List<Vec3d> probePoints = buildCoreProbePoints();
        for (Vec3d probePoint : probePoints) {
            if (canSeePointThroughReactorWindow(eyePos, probePoint)) {
                return true;
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    private List<Vec3d> buildCoreProbePoints() {
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() - 1.5D;
        double centerZ = pos.getZ() + 0.5D;
        double[] xs = new double[]{centerX - REACTOR_CORE_PROBE_RADIUS, centerX, centerX + REACTOR_CORE_PROBE_RADIUS};
        double[] ys = new double[]{centerY - REACTOR_CORE_PROBE_VERTICAL_RADIUS, centerY, centerY + REACTOR_CORE_PROBE_VERTICAL_RADIUS};
        double[] zs = new double[]{centerZ - REACTOR_CORE_PROBE_RADIUS, centerZ, centerZ + REACTOR_CORE_PROBE_RADIUS};
        List<Vec3d> probes = new ArrayList<>(27);
        for (double y : ys) {
            for (double x : xs) {
                for (double z : zs) {
                    probes.add(new Vec3d(x, y, z));
                }
            }
        }
        return probes;
    }

    @SideOnly(Side.CLIENT)
    private boolean canSeePointThroughReactorWindow(Vec3d eyePos, Vec3d target) {
        Vec3d start = eyePos;
        Vec3d direction = target.subtract(eyePos);
        double distanceSq = direction.lengthSquared();
        if (distanceSq <= 1.0E-8D) {
            return true;
        }
        Vec3d directionNorm = direction.scale(1.0D / Math.sqrt(distanceSq));
        boolean passedWindow = false;
        for (int i = 0; i < REACTOR_WINDOW_RAY_MAX_STEPS; i++) {
            RayTraceResult trace = world.rayTraceBlocks(start, target, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) {
                return passedWindow;
            }
            BlockPos hitPos = trace.getBlockPos();
            if (isReactorWindow(hitPos)) {
                passedWindow = true;
            }
            IBlockState hitState = world.getBlockState(hitPos);
            if (!isTransparentForReactorRay(hitState, hitPos)) {
                return false;
            }
            if (trace.hitVec == null) {
                return false;
            }
            start = trace.hitVec.add(directionNorm.scale(0.01D));
            if (start.squareDistanceTo(target) < 1.0E-6D) {
                return passedWindow;
            }
        }
        return passedWindow;
    }

    @SideOnly(Side.CLIENT)
    private boolean isReactorWindow(BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityReactorGlass || tile instanceof TileEntityReactorLaserFocusMatrix;
    }

    @SideOnly(Side.CLIENT)
    private boolean isTransparentForReactorRay(IBlockState state, BlockPos pos) {
        if (state == null) {
            return false;
        }
        if (isReactorWindow(pos) || state.getMaterial().isLiquid()) {
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

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (box == null) {
            box = new AxisAlignedBB(getPos().getX() - 1, getPos().getY() - 3, getPos().getZ() - 1, getPos().getX() + 2, getPos().getY(), getPos().getZ() + 2);
        }
        return box;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return getInventorySlotIdsForSide(side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return !isFormed();
        }
        return super.isCapabilityDisabled(capability, side);
    }
}
