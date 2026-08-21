package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.MekanismItems;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ISustainedData;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import mekanism.generators.common.slot.FluidFuelInventorySlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityBioGenerator extends TileEntityGenerator implements ISustainedData, IComparatorSupport, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getBioFuel", "getBioFuelNeeded"};
    private static final int TANK_CAPACITY = 24000;
    public static final int RENDER_STAGES = 40;
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_180 = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180.0D, 0.5D, 0.5D, 0.5D)
    };
    public BasicFluidTank bioFuelTank;
    private int lastBioFuelRenderLevel = -1;
    private int currentRedstoneLevel;
    private boolean activeChanged;
    private FluidFuelInventorySlot fuelSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityBioGenerator() {
        super("bio", "BioGenerator", MekanismConfig.current().generators.bioGeneratorStorage.val(), MekanismConfig.current().generators.bioGeneration.val() * 2);
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        bioFuelTank = builder.addTank(BasicFluidTank.input(TANK_CAPACITY, TileEntityBioGenerator::isBioFuel, listener),
              RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        fuelSlot = builder.addSlot(FluidFuelInventorySlot.forFuel(bioFuelTank, this::getFuel, TileEntityBioGenerator::getBioFuelStack, listener, 17, 35),
              RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        fuelSlot.setSlotOverlay(SlotOverlay.MINUS);
        energySlot = builder.addSlot(EnergyInventorySlot.drain(this, listener, 143, 35), RelativeSide.RIGHT);
        return builder.build();
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.drainContainer();
        fuelSlot.fillOrBurn();
        if (canOperate()) {
            setActive(true);
            MekanismUtils.logMismatchedStackSize(bioFuelTank.shrinkStack(1, Action.EXECUTE), 1);
            getEnergyContainer().insert(MekanismConfig.current().generators.bioGeneration.val(), Action.EXECUTE, AutomationType.INTERNAL);
        } else {
            setActive(false);
        }
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
        int renderLevel = bioFuelTank.isEmpty() ? -1 : getScaledFuelLevel(RENDER_STAGES - 1);
        if (activeChanged || renderLevel != lastBioFuelRenderLevel) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        activeChanged = false;
        lastBioFuelRenderLevel = renderLevel;
    }

    @Override
    public boolean canOperate() {
        return MekanismUtils.canFunction(this) && !bioFuelTank.isEmpty()
              && getEnergyContainer().insert(MekanismConfig.current().generators.bioGeneration.val(), Action.SIMULATE, AutomationType.INTERNAL) == 0;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("bioFuelTank")) {
            bioFuelTank.readFromNBT(nbtTags.getCompoundTag("bioFuelTank"));
        } else if (nbtTags.hasKey("bioFuelStored")) {
            setBioFuel(nbtTags.getInteger("bioFuelStored"));
        }
        sanitizeBioFuelTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }

    public int getFuel(ItemStack itemstack) {
        return itemstack.getItem() == MekanismItems.BioFuel ? 200 : 0;
    }

    public static boolean isBioFuel(FluidStack stack) {
        return stack != null && isBioFuel(stack.getFluid());
    }

    public static boolean isBioFuel(Fluid fluid) {
        return fluid != null && fluid == FluidRegistry.getFluid("bioethanol");
    }

    private static FluidStack getBioFuelStack(int amount) {
        return new FluidStack(FluidRegistry.getFluid("bioethanol"), amount);
    }

    private void setBioFuel(int amount) {
        int clamped = Math.max(Math.min(amount, bioFuelTank.getCapacity()), 0);
        if (clamped == 0) {
            bioFuelTank.setEmpty();
            return;
        }
        FluidStack bioFuel = getBioFuelStack(clamped);
        if (bioFuelTank.isFluidEqual(bioFuel)) {
            bioFuelTank.setStackSize(clamped, Action.EXECUTE);
        } else {
            bioFuelTank.setEmpty();
            bioFuelTank.insert(bioFuel, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    /**
     * Gets the scaled fuel level for the GUI.
     *
     * @param i - multiplier
     * @return Scaled fuel level
     */
    public int getScaledFuelLevel(int i) {
        return bioFuelTank.getFluidAmount() * i / bioFuelTank.getCapacity();
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return super.canSetFacing(facing);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, bioFuelTank);
            MekanismUtils.updateBlock(world, getPos());
        }
    }

    @Override
    public void setActive(boolean active) {
        if (isActive != active) {
            isActive = active;
            activeChanged = true;
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, bioFuelTank);
        return data;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{electricityStored};
            case 1 -> new Object[]{output};
            case 2 -> new Object[]{BASE_MAX_ENERGY};
            case 3 -> new Object[]{BASE_MAX_ENERGY - electricityStored.get()};
            case 4 -> new Object[]{bioFuelTank.getFluidAmount()};
            case 5 -> new Object[]{bioFuelTank.getNeeded()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        ItemDataUtils.setLegacyFluidTank(itemStack, "bioFuelTank", bioFuelTank);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (readSustainedFluidTanks(itemStack)) {
            return;
        } else if (ItemDataUtils.readLegacyFluidTank(itemStack, "bioFuelTank", bioFuelTank)) {
            return;
        } else if (ItemDataUtils.hasData(itemStack, "fluidStored")) {
            setBioFuel(ItemDataUtils.getInt(itemStack, "fluidStored"));
        }
        sanitizeBioFuelTank();
    }

    private void sanitizeBioFuelTank() {
        FluidStack stored = bioFuelTank.getFluid();
        if (stored != null && (stored.amount <= 0 || !isBioFuel(stored.getFluid()))) {
            bioFuelTank.setEmpty();
        } else if (stored != null) {
            bioFuelTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(bioFuelTank.getFluidAmount(), bioFuelTank.getCapacity());
    }
@Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.generators.client.model.ModelBioGenerator.class;
    }

    @Override
    public ISpecialSelectionWireframeTile.SelectionTransform[] getSelectionWireframeTransforms(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SELECTION_ROTATE_180;
    }
}
