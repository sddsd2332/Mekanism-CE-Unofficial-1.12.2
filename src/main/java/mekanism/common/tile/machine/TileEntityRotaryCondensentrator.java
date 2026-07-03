package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.recipe.cache.RecipeCacheLookupMonitor;
import mekanism.common.recipe.cache.RotaryCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.machines.RotaryRecipe;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

public class TileEntityRotaryCondensentrator extends TileEntityMachine implements ISustainedData, IUpgradeInfoHandler, ITankManager,
        IComparatorSupport, ISideConfiguration, ISpecialConfigData, ISpecialSelectionWireframeTile, IRecipeLookupHandler<RotaryRecipe> {

    public static final int MAX_FLUID = 10000;
    public static final RecipeError NOT_ENOUGH_FLUID_INPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_GAS_INPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_SPACE_FLUID_OUTPUT_ERROR = RecipeError.create();
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          NOT_ENOUGH_FLUID_INPUT_ERROR,
          NOT_ENOUGH_GAS_INPUT_ERROR,
          NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR,
          NOT_ENOUGH_SPACE_FLUID_OUTPUT_ERROR,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    /**
     * 0: gas -> fluid; 1: fluid -> gas
     */
    public int mode;
    public BasicGasTank gasTank;
    public BasicFluidTank fluidTank;

    public double clientEnergyUsed;

    public TileComponentEjector ejectorComponent;

    public TileComponentConfig configComponent;

    private final RecipeCacheLookupMonitor<RotaryRecipe> recipeCacheLookupMonitor = new RecipeCacheLookupMonitor<>(this);
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];
    private int currentRedstoneLevel;
    private RotaryRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;
    private GasInventorySlot gasOutputSlot;
    private GasInventorySlot gasInputSlot;
    private FluidInventorySlot fluidSlot;
    private OutputInventorySlot fluidContainerOutputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityRotaryCondensentrator() {
        super("machine.rotarycondensentrator", MachineType.ROTARY_CONDENSENTRATOR, 5);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS, TransmissionType.FLUID);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(Arrays.asList(gasInputSlot, fluidSlot), Arrays.asList(gasOutputSlot, fluidContainerOutputSlot), energySlot, true);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.INPUT, DataType.OUTPUT, DataType.OUTPUT);
        configComponent.setupIOConfig(TransmissionType.GAS, gasTank, RelativeSide.LEFT, true).setEjecting(true);
        configComponent.setupFluidIOConfig(fluidTank, RelativeSide.RIGHT, true).setEjecting(true);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.GAS, TransmissionType.FLUID)
              .setCanEject(transmissionType -> {
                  if (transmissionType == TransmissionType.GAS) {
                      return mode == 1;
                  } else if (transmissionType == TransmissionType.FLUID) {
                      return mode == 0;
                  }
                  return true;
              });
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        BooleanSupplier modeSupplier = () -> mode == 1;
        gasOutputSlot = builder.addSlot(GasInventorySlot.rotaryDrain(gasTank, modeSupplier, listener, 5, 25));
        gasOutputSlot.setSlotType(ContainerSlotType.INPUT);
        gasOutputSlot.setSlotOverlay(SlotOverlay.PLUS);
        gasInputSlot = builder.addSlot(GasInventorySlot.rotaryFill(gasTank, modeSupplier, listener, 5, 56));
        gasInputSlot.setSlotType(ContainerSlotType.OUTPUT);
        gasInputSlot.setSlotOverlay(SlotOverlay.MINUS);
        fluidSlot = builder.addSlot(FluidInventorySlot.rotary(fluidTank, modeSupplier, listener, 155, 25));
        fluidSlot.setSlotType(ContainerSlotType.INPUT);
        fluidContainerOutputSlot = builder.addSlot(OutputInventorySlot.at(listener, 155, 56));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 155, 5));
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateGasTank(listener));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        builder.addTank(getOrCreateFluidTank(listener));
        return builder.build();
    }

    private BasicGasTank getOrCreateGasTank(IContentsListener listener) {
        if (gasTank == null) {
            gasTank = BasicGasTank.create(MAX_FLUID,
                  (gas, automationType) -> automationType == AutomationType.MANUAL || automationType == AutomationType.INTERNAL || mode == 1,
                  (gas, automationType) -> automationType == AutomationType.INTERNAL || mode == 0,
                  gas -> isValidGas(new GasStack(gas, 1)), recipeCacheLookupMonitor);
        }
        return gasTank;
    }

    private BasicFluidTank getOrCreateFluidTank(IContentsListener listener) {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.create(MAX_FLUID,
                  (fluid, automationType) -> automationType == AutomationType.MANUAL || automationType == AutomationType.INTERNAL || mode == 0,
                  (fluid, automationType) -> automationType == AutomationType.INTERNAL || mode == 1,
                  this::isValidFluid, recipeCacheLookupMonitor);
        }
        return fluidTank;
    }


    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        if (mode == 0) {
            gasInputSlot.fillTank();

            fluidSlot.drainTank(fluidContainerOutputSlot);
            //I don't know why, mode 0 [gas-> liquid] causes the fluid to be transplanted into the tank using a slot, which retains the liquid but no quantity, and here it is repaired by setting the fluid to null when it is judged that there is fluid but the quantity is 0
            if (fluidTank.getFluid() != null && fluidTank.getFluidAmount() == 0) {
                fluidTank.setEmpty();
            }
        } else if (mode == 1) {
            gasOutputSlot.drainTank();
            fluidSlot.fillTank(fluidContainerOutputSlot);
        }
        clientEnergyUsed = recipeCacheLookupMonitor.updateAndProcess(getMainEnergyContainer());
        if (recipeCacheLookupMonitor.getCachedRecipe(0) == null) {
            if (prevEnergy >= getEnergy()) {
                setActive(false);
            }
        }
        prevEnergy = getEnergy();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;

        }
    }

    public int getUpgradedUsage() {
        return Math.max(1, Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val()));
    }

    public boolean isValidGas(GasStack g) {
        return RecipeHandler.isRotaryGasValid(g);

    }

    public boolean isValidFluid(@Nonnull Fluid f) {
        return RecipeHandler.isRotaryFluidValid(new FluidStack(f, 1));
    }

    public boolean isValidFluid(FluidStack f) {
        return f != null && isValidFluid(f.getFluid());
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean usedEnergy() {
        return clientEnergyUsed > 0;
    }

    public double getEnergyUsed() {
        return clientEnergyUsed;
    }

    public boolean hasWarningNoMatchingGasInput() {
        if (hasWarning(NOT_ENOUGH_GAS_INPUT_ERROR)) {
            return true;
        }
        if (mode != 0) {
            return false;
        }
        GasStack gas = gasTank.getGas();
        return gas == null ? !gasInputSlot.isEmpty() : getRecipe() == null;
    }

    public boolean hasWarningNoMatchingFluidInput() {
        if (hasWarning(NOT_ENOUGH_FLUID_INPUT_ERROR)) {
            return true;
        }
        if (mode != 1) {
            return false;
        }
        FluidStack fluid = fluidTank.getFluid();
        return fluid == null ? !fluidSlot.isEmpty() : getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInGasOutput() {
        if (hasWarning(NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR)) {
            return true;
        }
        if (mode != 1) {
            return false;
        }
        GasStack output = getCurrentGasOutput();
        return output != null && gasTank.canReceiveType(output.getGas()) && gasTank.getNeeded() < output.amount;
    }

    public boolean hasWarningNoSpaceInFluidOutput() {
        if (hasWarning(NOT_ENOUGH_SPACE_FLUID_OUTPUT_ERROR)) {
            return true;
        }
        if (mode != 0) {
            return false;
        }
        FluidStack output = getCurrentFluidOutput();
        return output != null && fluidTank.getFluid() != null && fluidTank.getFluid().isFluidEqual(output) && fluidTank.getNeeded() < output.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        if (mode == 0) {
            FluidStack output = getCurrentFluidOutput();
            return output != null && fluidTank.getFluid() != null && !fluidTank.getFluid().isFluidEqual(output);
        }
        GasStack output = getCurrentGasOutput();
        return output != null && !gasTank.canReceiveType(output.getGas());
    }

    private FluidStack getCurrentFluidOutput() {
        RotaryRecipe recipe = getRecipe();
        return recipe == null ? null : recipe.getFluidOutput(gasTank.getGas());
    }

    private GasStack getCurrentGasOutput() {
        RotaryRecipe recipe = getRecipe();
        return recipe == null ? null : recipe.getGasOutput(fluidTank.getFluid());
    }

    public RotaryRecipe getRecipe() {
        int recipeVersion = RecipeHandler.Recipe.ROTARY_CONDENSENTRATOR.getRecipeVersion();
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
        if (mode == 0) {
            GasStack input = gasTank.getGas();
            if (cachedRecipe == null || !cachedRecipe.test(input)) {
                cachedRecipe = RecipeHandler.getRotaryRecipe(input);
            }
        } else {
            FluidStack input = fluidTank.getFluid();
            if (cachedRecipe == null || !cachedRecipe.test(input)) {
                cachedRecipe = RecipeHandler.getRotaryRecipe(input);
            }
        }
        return cachedRecipe;
    }

    @Override
    public RotaryRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public CachedRecipe<RotaryRecipe> createNewCachedRecipe(RotaryRecipe recipe, int cacheIndex) {
        return new RotaryCachedRecipe(recipe, () -> false,
              InputHelper.getFluidInputHandler(fluidTank, NOT_ENOUGH_FLUID_INPUT_ERROR),
              InputHelper.getGasInputHandler(gasTank, NOT_ENOUGH_GAS_INPUT_ERROR),
              OutputHelper.getGasOutputHandler(gasTank, NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR),
              OutputHelper.getOutputHandler(fluidTank, NOT_ENOUGH_SPACE_FLUID_OUTPUT_ERROR),
              () -> mode == 1)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(() -> energyPerTick, getMainEnergyContainer())
              .setBaselineMaxOperations(this::getUpgradedUsage)
              .setErrorsChanged(errors -> {
                  for (int i = 0; i < trackedErrors.length; i++) {
                      trackedErrors[i] = errors.contains(TRACKED_ERROR_TYPES.get(i));
                  }
              })
              .setOnFinish(this::markNoUpdateSync);
    }

    @Override
    public void clearRecipeErrors(int cacheIndex) {
        Arrays.fill(trackedErrors, false);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.trackArray(trackedErrors);
    }

    public boolean hasWarning(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex != -1 && trackedErrors[errorIndex];
    }

    public java.util.function.BooleanSupplier getWarningCheck(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex == -1 ? () -> false : () -> trackedErrors[errorIndex];
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                mode = mode == 0 ? 1 : 0;
                cachedRecipe = null;
                recipeCacheLookupMonitor.onChange();
            }
            playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player));
            return;
        }

        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            mode = dataStream.readInt();
            clientEnergyUsed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, gasTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(mode);
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, gasTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        mode = nbtTags.getInteger("mode");
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank")) {
            gasTank.read(nbtTags.getCompoundTag("gasTank"));
        }
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        sanitizeAndClampTanks();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("mode", mode);

    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        } else if (capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "fluidTank", fluidTank.getFluid());
        ItemDataUtils.setLegacyGas(itemStack, "gasTank", gasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            fluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "fluidTank"));
        }
        if (!readSustainedGasTanks(itemStack)) {
            gasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(fluidTank);
        sanitizeAndClampTank(gasTank);
    }

    private void sanitizeAndClampTank(BasicFluidTank tank) {
        FluidStack stored = tank.getFluid();
        if (stored != null && (stored.amount <= 0 || stored.getFluid() == null)) {
            tank.setEmpty();
        } else if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    private void sanitizeAndClampTank(BasicGasTank tank) {
        GasStack stored = tank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            tank.setEmpty();
        } else if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public List<String> getInfo(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? upgrade.getExpScaledInfo(this) : upgrade.getMultScaledInfo(this);
    }


    @Override
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank, gasTank};
    }

    @Override
    public int getRedstoneLevel() {
        if (mode == 0) {
            return MekanismUtils.redstoneLevelFromContents(gasTank.getStored(), gasTank.getMaxGas());
        }
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public EnumFacing getOrientation() {
        return facing;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        nbtTags.setInteger("mode", mode);
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        mode = nbtTags.getInteger("mode");
    }

    @Override
    public String getDataType() {
        return getName();
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelRotaryCondensentrator.class;
    }
}
