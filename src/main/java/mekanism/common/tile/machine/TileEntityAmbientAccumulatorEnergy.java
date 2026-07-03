package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.recipe.cache.NoInputCachedRecipe;
import mekanism.common.recipe.cache.RecipeCacheLookupMonitor;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.IntegerInput;
import mekanism.common.recipe.machines.AmbientGasRecipe;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.List;

public class TileEntityAmbientAccumulatorEnergy extends TileEntityMachine implements ISustainedData,
        IUpgradeInfoHandler, ITankManager, IComparatorSupport, ISideConfiguration, IConfigCardAccess, IRecipeLookupHandler<AmbientGasRecipe> {

    public static final int MAX_GAS = GasTankTier.BASIC.getBaseStorage();
    private final RecipeCacheLookupMonitor<AmbientGasRecipe> recipeCacheLookupMonitor = new RecipeCacheLookupMonitor<>(this);
    public BasicGasTank outputTank;
    public AmbientGasRecipe cachedRecipe;
    public double clientEnergyUsed;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    private int currentRedstoneLevel;
    public int cachedDimensionId;
    private int cachedRecipeVersion = -1;
    private GasInventorySlot gasSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityAmbientAccumulatorEnergy() {
        super("machine.washer", BlockStateMachine.MachineType.AMBIENT_ACCUMULATOR_ENERGY, 2);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemOutputEnergyConfig(gasSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.NONE, DataType.OUTPUT, DataType.ENERGY, DataType.OUTPUT, DataType.NONE, DataType.NONE);
        configComponent.setCanEject(TransmissionType.ITEM, false);
        configComponent.setupOutputConfig(TransmissionType.GAS, outputTank);
        configComponent.fillConfig(TransmissionType.GAS, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.GAS);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        gasSlot = builder.addSlot(GasInventorySlot.drain(outputTank, listener, 103, 67));
        gasSlot.setSlotOverlay(SlotOverlay.PLUS);
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 136, 67));
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateOutputTank());
        return builder.build();
    }

    private BasicGasTank getOrCreateOutputTank() {
        if (outputTank == null) {
            outputTank = BasicGasTank.output(MAX_GAS, this::onRecipeOutputContentsChanged);
        }
        return outputTank;
    }

    private void onRecipeOutputContentsChanged() {
        onContentsChanged();
        recipeCacheLookupMonitor.onChange();
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        gasSlot.drainTank();
        AmbientGasRecipe recipe = getRecipe();
        if (recipe == null) {
            recipeCacheLookupMonitor.clear();
            if (prevEnergy >= getEnergy()) {
                setActive(false);
            }
        } else {
            clientEnergyUsed = recipeCacheLookupMonitor.updateAndProcess(getMainEnergyContainer());
        }
        prevEnergy = getEnergy();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    public AmbientGasRecipe getRecipe() {
        refreshRecipeLookupCache();
        IntegerInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getDimensionGas(getInput());
        }
        return cachedRecipe;
    }

    public IntegerInput getInput() {
        refreshRecipeLookupCache();
        if (cachedRecipe == null || world.provider.getDimension() != cachedDimensionId) {
            cachedDimensionId = world.provider.getDimension();
            cachedRecipe = RecipeHandler.getDimensionGas(new IntegerInput(cachedDimensionId));
        }
        return new IntegerInput(cachedDimensionId);
    }

    private void refreshRecipeLookupCache() {
        int recipeVersion = RecipeHandler.getGlobalRecipeVersion();
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
    }

    @Override
    public void onRecipeCacheInvalidated(int cacheIndex) {
        cachedRecipe = null;
        cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
    }

    public boolean canOperate(AmbientGasRecipe recipe) {
        return recipe != null && recipe.canOperate(cachedDimensionId, outputTank);
    }

    @Override
    public AmbientGasRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public CachedRecipe<AmbientGasRecipe> createNewCachedRecipe(AmbientGasRecipe recipe, int cacheIndex) {
        return new NoInputCachedRecipe<>(recipe, () -> false,
              () -> recipe.getInput().ingredient == cachedDimensionId,
              OutputHelper.getChanceGasOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getOutput().copy(),
              output -> output == null || output.getMaxOutput() == null)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(() -> energyPerTick, getMainEnergyContainer())
              .setRequiredTicks(() -> 1)
              .setBaselineMaxOperations(this::getUpgradedUsage);
    }

    public int getUpgradedUsage() {
        int possibleProcess = (int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED));
        possibleProcess = Math.min(outputTank.getNeeded(), possibleProcess);
        possibleProcess = Math.min((int) (getEnergy() / energyPerTick), possibleProcess);
        possibleProcess = Math.max(possibleProcess, 1);
        return possibleProcess;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientEnergyUsed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, outputTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, outputTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("outputTank")) {
            outputTank.read(nbtTags.getCompoundTag("outputTank"));
        }
        sanitizeAndClampTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }


    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        sanitizeAndClampTank();
    }

    private void sanitizeAndClampTank() {
        GasStack stored = outputTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            outputTank.setEmpty();
        } else if (stored != null) {
            outputTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public List<String> getInfo(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? upgrade.getExpScaledInfo(this) : upgrade.getMultScaledInfo(this);
    }


    @Override
    public Object[] getManagedTanks() {
        return new Object[]{outputTank};
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(outputTank.getStored(), outputTank.getMaxGas());
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
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
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
