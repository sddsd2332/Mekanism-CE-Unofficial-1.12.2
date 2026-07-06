package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.Upgrade;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler.ConstantUsageRecipeLookupHandler;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe.GasUsageMultiplier;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.DissolutionRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StatUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityChemicalDissolutionChamber extends TileEntityBasicMachine<ItemStackInput, GasOutput, DissolutionRecipe> implements ISustainedData, ITankManager,
      IComparatorSupport, ISpecialSelectionWireframeTile, ConstantUsageRecipeLookupHandler {

    public static final int MAX_GAS = 10000;
    public static final int BASE_INJECT_USAGE = 1;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public final double BASE_ENERGY_USAGE = MachineType.CHEMICAL_DISSOLUTION_CHAMBER.getUsage();
    public BasicGasTank injectTank;
    public BasicGasTank outputTank;
    public double injectUsage = BASE_INJECT_USAGE;
    public int injectUsageThisTick;
    private double gasPerTickMeanMultiplier = 1;
    private long baseTotalUsage;
    private long usedSoFar;
    private final GasUsageMultiplier gasUsageMultiplier;


    public DissolutionRecipe cachedRecipe;
    public float prevScale;
    public int updateDelay;
    public boolean needsPacket;
    private GasInventorySlot injectSlot;
    private InputInventorySlot inputSlot;
    private GasInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityChemicalDissolutionChamber() {
        super("dissolution", MachineType.CHEMICAL_DISSOLUTION_CHAMBER, 4, 100, TRACKED_ERROR_TYPES);
        setSupportedUpgrade(Upgrade.GAS);

        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.GAS, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemIOExtraConfig(inputSlot, outputSlot, injectSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.EXTRA, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);
        configComponent.setupIOConfig(TransmissionType.GAS, injectTank, outputTank, RelativeSide.RIGHT);
        configComponent.setConfig(TransmissionType.GAS, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS)
              .setCanTankEject(tank -> tank != injectTank);
        baseTotalUsage = BASE_TICKS_REQUIRED;
        gasUsageMultiplier = (usedSoFar, operatingTicks) -> {
            long baseRemaining = baseTotalUsage - usedSoFar;
            int remainingTicks = getTicksRequired() - operatingTicks;
            if (baseRemaining < remainingTicks) {
                return 0;
            } else if (baseRemaining == remainingTicks) {
                return 1;
            }
            return Math.max(MathUtils.clampToLong(baseRemaining / (double) remainingTicks), 0);
        };
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        injectSlot = builder.addSlot(GasInventorySlot.fill(injectTank, listener, 8, 65));
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> RecipeHandler.getDissolutionRecipe(new ItemStackInput(stack)) != null, getRecipeCacheListener(), 28, 36)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        outputSlot = builder.addSlot(GasInventorySlot.drain(outputTank, listener, 152, 55));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 152, 14));
        injectSlot.setSlotOverlay(SlotOverlay.MINUS);
        outputSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateInjectTank());
        builder.addTank(getOrCreateOutputTank(listener));
        return builder.build();
    }

    private BasicGasTank getOrCreateInjectTank() {
        if (injectTank == null) {
            injectTank = BasicGasTank.input(MAX_GAS, this::isValidGas, getRecipeCacheListener());
        }
        return injectTank;
    }

    private BasicGasTank getOrCreateOutputTank(IContentsListener listener) {
        if (outputTank == null) {
            outputTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return outputTank;
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                needsPacket = true;
            }
        }
        energySlot.fillContainerOrConvert();
        injectSlot.fillTank();
        outputSlot.drainTank();
        injectUsageThisTick = Math.max(BASE_INJECT_USAGE, StatUtils.inversePoisson(injectUsage));
        processRecipe();
        prevEnergy = getEnergy();
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        needsPacket = false;
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
        float targetScale = (float) (outputTank.getGas() != null ? outputTank.getGas().amount : 0) / outputTank.getMaxGas();
        if (Math.abs(prevScale - targetScale) > 0.01) {
            prevScale = (9 * prevScale + targetScale) / 10;
        }
    }

    public double getScaledProgress() {
        return Math.max(Math.min((double) operatingTicks / (double) ticksRequired, 1.0D),0.0D);
    }

    public DissolutionRecipe getRecipe() {
        refreshRecipeLookupCache();
        ItemStackInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getDissolutionRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public DissolutionRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public Map<ItemStackInput, DissolutionRecipe> getRecipes() {
        return RecipeHandler.Recipe.CHEMICAL_DISSOLUTION_CHAMBER.get();
    }


    public ItemStackInput getInput() {
        return new ItemStackInput(inputSlot.getStack());
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean hasWarningNoMatchingSecondaryInput() {
        return getRecipe() != null && (injectTank.getGas() == null || injectTank.getStored() < injectUsageThisTick);
    }

    public boolean hasWarningNoSpaceInOutput() {
        GasStack output = getCurrentOutput();
        return output != null && outputTank.canReceiveType(output.getGas()) && outputTank.getNeeded() < output.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        GasStack output = getCurrentOutput();
        return output != null && !outputTank.canReceiveType(output.getGas());
    }

    public boolean canOperate(DissolutionRecipe recipe) {
        return recipe != null && recipe.canOperate(inputSlot, outputTank);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        DissolutionRecipe recipe = RecipeHandler.getDissolutionRecipe(new ItemStackInput(getSimulatedStackWithInsert(0, stack)));
        return recipe != null && recipe.getOutput().applyOutputs(outputTank, false, 1);
    }

    @Override
    public CachedRecipe<DissolutionRecipe> createNewCachedRecipe(DissolutionRecipe recipe, int cacheIndex) {
        return new ItemStackConstantGasCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getConstantGasInputHandler(injectTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, false),
              OutputHelper.getGasOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              gasUsageMultiplier, used -> usedSoFar = used)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(() -> energyPerTick, getMainEnergyContainer())
              .setRequiredTicks(() -> ticksRequired)
              .setBaselineMaxOperations(() -> getBaselineMaxOperations(energyPerTick, true))
              .setOperatingTicksChanged(ticks -> operatingTicks = ticks)
              .setErrorsChanged(this::onRecipeErrorsChanged)
              .setOnFinish(this::onCachedRecipeFinish);
    }


    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            operatingTicks = dataStream.readInt();
            TileUtils.readTankData(dataStream, injectTank);
            TileUtils.readTankData(dataStream, outputTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(operatingTicks);
        TileUtils.addTankData(data, injectTank);
        TileUtils.addTankData(data, outputTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        operatingTicks = nbtTags.getInteger("operatingTicks");
        usedSoFar = nbtTags.getLong(NBTConstants.USED_SO_FAR);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("injectTank")) {
            injectTank.read(nbtTags.getCompoundTag("injectTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank")) {
            outputTank.read(nbtTags.getCompoundTag("gasTank"));
        }
        injectTank.clearIfInvalid(this::isValidGas);
        sanitizeAndClampTanks();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("operatingTicks", operatingTicks);
        nbtTags.setLong(NBTConstants.USED_SO_FAR, usedSoFar);
    }

    private boolean isValidGas(Gas gas) {
        //TODO: Replace with commented version once this becomes an AdvancedMachine
        return gas == MekanismFluids.SulfuricAcid;//Recipe.CHEMICAL_DISSOLUTION_CHAMBER.containsRecipe(gas);
    }

    private GasStack getCurrentOutput() {
        DissolutionRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output == null) {
            return null;
        }
        return recipe.getOutput().output;
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "injectTank", injectTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            injectTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "injectTank"));
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        injectTank.clearIfInvalid(this::isValidGas);
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(injectTank);
        sanitizeAndClampTank(outputTank);
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
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
            energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_USAGE);
            injectUsage = MekanismUtils.getSecondaryEnergyPerTickMean(this, BASE_INJECT_USAGE);
            gasPerTickMeanMultiplier = MekanismUtils.getGasPerTickMeanMultiplier(this);
            baseTotalUsage = MekanismUtils.getBaseUsage(this, BASE_INJECT_USAGE);
        } else if (upgrade == Upgrade.ENERGY) {
            energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK); // incorporate speed upgrades
        } else if (upgrade == Upgrade.GAS) {
            injectUsage = MekanismUtils.getSecondaryEnergyPerTickMean(this, BASE_INJECT_USAGE);
        }
        if (upgrade == Upgrade.GAS) {
            gasPerTickMeanMultiplier = MekanismUtils.getGasPerTickMeanMultiplier(this);
            baseTotalUsage = MekanismUtils.getBaseUsage(this, BASE_INJECT_USAGE);
        }
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{injectTank, outputTank};
    }


    public int getScaledFuelLevel(int i) {
        return outputTank.getStored() * i / outputTank.getMaxGas();
    }

    @Override
    public long getSavedUsedSoFar(int cacheIndex) {
        return usedSoFar;
    }

    @Override
    public String[] getMethods() {
        return new String[0];
    }

    @Override
    public Object[] invoke(int method, Object[] args) throws NoSuchMethodException {
        return new Object[0];
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        if (updateDelay == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            updateDelay = 10;
        }
    }
@Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelChemicalDissolutionChamber.class;
    }
}
