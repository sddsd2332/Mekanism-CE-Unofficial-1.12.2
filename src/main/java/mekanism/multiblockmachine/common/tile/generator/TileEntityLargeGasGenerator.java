package mekanism.multiblockmachine.common.tile.generator;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasStackFuelToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.*;
import mekanism.generators.common.tile.TileEntityGenerator;
import mekanism.multiblockmachine.client.render.block.generator.bloom.BloomRendererLargeGasGenerator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.MultiblockMachineUpgrades;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Collections;

public class TileEntityLargeGasGenerator extends TileEntityGenerator implements IAdvancedBoundingBlock, ISustainedData, IComparatorSupport, IUpgradeTile, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getGas", "getGasNeeded"};
    /**
     * The maximum amount of gas this block can store.
     */
    public int MAX_GAS = 8192000;
    /**
     * The tank this block is storing fuel in.
     */
    public BasicGasTank fuelTank;

    public int burnTicks = 0;
    public int maxBurnTicks;
    public double generationRate = 0;
    public double clientUsed;
    private int currentRedstoneLevel;
    public GasStackFuelToEnergyRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;
    public TileComponentUpgrade upgradeComponent;
    public int processes = MekanismConfig.current().multiblock.LargeGasGeneratorProcesses.val();
    public int numPowering;
    private GasInventorySlot fuelSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityLargeGasGenerator() {
        super("gas", "LargeGasGenerator", 0, 0);
        upgradeComponent = new TileComponentUpgrade(this, Upgrade.ENERGY);
        setSupportedUpgrade(MultiblockMachineUpgrades.THREAD);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        fuelSlot = builder.addSlot(GasInventorySlot.fill(fuelTank, listener, 17, 35),
              RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        fuelSlot.setSlotOverlay(SlotOverlay.MINUS);
        energySlot = builder.addSlot(EnergyInventorySlot.drain(this, listener, 143, 35), RelativeSide.RIGHT);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        fuelTank = new FuelTank(listener);
        return ProxiedGasTankHolder.create(
              this::isGasInputSide,
              side -> false,
              side -> side == null || isGasInputSide(side) ? Collections.singletonList(fuelTank) : Collections.emptyList()
        );
    }

    private boolean isGasInputSide(@Nullable EnumFacing side) {
        return side != null && side != facing && side != EnumFacing.UP && side != EnumFacing.DOWN;
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.drainContainer();
        fuelSlot.fillTank();

        GasStackFuelToEnergyRecipe recipe = getRecipe();
        boolean operate = recipe != null && canOperate();
        if (operate && getEnergyContainer().insert(generationRate, Action.SIMULATE, AutomationType.INTERNAL) == 0) {
            setActive(true);
            if (fuelTank.getStored() != 0) {
                maxBurnTicks = recipe.getInput().ingredient.amount;
                generationRate = recipe.getOutput().energyOutput;
            }
            int toUse = getToUse();

            int total = burnTicks + fuelTank.getStored() * maxBurnTicks;
            total -= toUse;
            getEnergyContainer().insert(generationRate * toUse, Action.EXECUTE, AutomationType.INTERNAL);

            if (fuelTank.getStored() > 0) {
                fuelTank.setStackSize(total / maxBurnTicks, Action.EXECUTE);
            }
            burnTicks = total % maxBurnTicks;
            clientUsed = toUse /(double)  maxBurnTicks;
        } else {
            if (!operate) {
                reset();
            }
            clientUsed = 0;
            setActive(false);
        }
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public void addTileSyncTask() {
        if (getEnergy() > 0) {
            CableUtils.emit(this, 3);
        }
    }

    @Override
    protected boolean supportsAsyncIdleSkipping() {
        return getClass() == TileEntityLargeGasGenerator.class;
    }

    @Override
    protected boolean isAsyncUpdateIdle() {
        return fuelTank.isEmpty() && fuelSlot.isEmpty() && energySlot.isEmpty() && burnTicks == 0 &&
              maxBurnTicks == 0 && generationRate == 0 && clientUsed == 0 && !getActive() &&
              cachedRecipeVersion == RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getRecipeVersion() &&
              currentRedstoneLevel == getRedstoneLevel();
    }

    public int getThread() {
        int thread = 1;
        if (isUpgradeInstalled(MultiblockMachineUpgrades.THREAD)) {
            thread += getInstalledUpgrades(MultiblockMachineUpgrades.THREAD);
        }
        return thread;
    }

    public void reset() {
        burnTicks = 0;
        maxBurnTicks = 0;
        generationRate = 0;
    }

    public int getToUse() {
        if (generationRate == 0 || fuelTank.getGas() == null) {
            return 0;
        }
        int max = (int) Math.ceil(((float) fuelTank.getStored() / (float) fuelTank.getMaxGas()) * 256F);
        max *= processes;
        max *= getThread();
        max = Math.min((fuelTank.getStored() * maxBurnTicks) + burnTicks, max);
        max = (int) Math.min((getMaxEnergy() - getEnergy()) / generationRate, max);
        return max;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return EnergyInventorySlot.drainExtractCheck(this, itemstack);
        } else if (slotID == 0) {
            return GasInventorySlot.fillExtractCheck(fuelTank, itemstack);
        }
        return false;
    }

    @Override
    public boolean canOperate() {
        return (fuelTank.getStored() > 0 || burnTicks > 0) && getRecipe() != null && MekanismUtils.canFunction(this);
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{getOutput()};
            case 2 -> new Object[]{getMaxEnergy()};
            case 3 -> new Object[]{getNeedEnergy()};
            case 4 -> new Object[]{fuelTank.getStored()};
            case 5 -> new Object[]{fuelTank.getNeeded()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fuelTank);
            generationRate = dataStream.readDouble();
            clientUsed = dataStream.readDouble();
            maxBurnTicks = dataStream.readInt();
            numPowering = dataStream.readInt();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fuelTank);
        data.add(generationRate);
        data.add(clientUsed);
        data.add(maxBurnTicks);
        data.add(numPowering);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("fuelTank")) {
            fuelTank.read(nbtTags.getCompoundTag("fuelTank"));
        }
        sanitizeFuelTank();
        updateOutputFromFuel(fuelTank.getGas());
        numPowering = nbtTags.getInteger("numPowering");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("numPowering", numPowering);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing back = MekanismUtils.getBack(facing);
        if (side != facing && side != left && side != right && side != back && side != EnumFacing.DOWN && (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side))) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }


    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGasTank(itemStack, "fuelTank", fuelTank);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        boolean loadedTank = readSustainedGasTanks(itemStack);
        loadedTank = loadedTank || ItemDataUtils.readLegacyGasTank(itemStack, "fuelTank", fuelTank);
        if (loadedTank) {
            sanitizeFuelTank();
            updateOutputFromFuel(fuelTank.getGas());
        }
    }

    private void sanitizeFuelTank() {
        GasStack stored = fuelTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            fuelTank.setEmpty();
        } else if (stored != null) {
            fuelTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fuelTank.getStored(), fuelTank.getMaxGas());
    }
public GasStackFuelToEnergyRecipe getRecipe() {
        int recipeVersion = RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getRecipeVersion();
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
        GasInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getGasStackFuelToEnergyRecipe(getInput());
        }
        return cachedRecipe;
    }

    public GasInput getInput() {
        return new GasInput(fuelTank.getGas());
    }

    @Override
    public boolean canBoundReceiveEnergy(BlockPos location, EnumFacing side) {
        return false;
    }

    @Override
    public boolean canBoundOutPutEnergy(BlockPos coord, EnumFacing side) {
        if (coord.equals(getPos().offset(EnumFacing.UP, 2))) {
            return side == EnumFacing.UP;
        }
        return false;
    }

    @Override
    public void onPower() {
        numPowering++;
    }

    @Override
    public void onNoPower() {
        numPowering--;
    }

    @Override
    public boolean isPowered() {
        return redstone || numPowering > 0;
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {

    }

    @Override
    public String getDataType() {
        return getName();
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.LargeGasGenerator.name");
    }

    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    boolean advanced;
                    if (y == 2) {
                        advanced = x == 0 && z == 0;
                    } else {
                        boolean normal = x == z || x == -z ||
                              facing == EnumFacing.NORTH && x == 0 && z == -1 ||
                              facing == EnumFacing.WEST && z == 0 && x == -1 ||
                              facing == EnumFacing.SOUTH && x == 0 && z == 1 ||
                              facing == EnumFacing.EAST && z == 0 && x == 1;
                        advanced = !normal;
                    }
                    consumer.accept(getPos().add(x, y, z), advanced);
                }
            }
        }
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
    }

    @Override
    public boolean hasOffsetCapability(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing back = MekanismUtils.getBack(facing);
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return false;
        }
        if (side != facing && side != left && side != right && side != back && side != EnumFacing.DOWN) {
            if (isManagedStrictEnergy(capability)) {
                return getEnergyHandler(capability, side) != null;
            } else if (capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
                return true;
            }
        }
        if (side != facing && side != EnumFacing.UP && side != EnumFacing.DOWN && capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return getGasHandler(side) != null;
        }
        return hasCapability(capability, side);
    }

    @Override
    public @Nullable <T> T getOffsetCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing back = MekanismUtils.getBack(facing);
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return null;
        } else if (side != facing && side != left && side != right && side != back && side != EnumFacing.DOWN) {
            if (isManagedStrictEnergy(capability)) {
                return getEnergyHandler(capability, side);
            } else if (isTesla(capability, side)) {
                return (T) getTeslaEnergyWrapper(side);
            } else if (capability == CapabilityEnergy.ENERGY) {
                return CapabilityEnergy.ENERGY.cast(getForgeEnergyWrapper(side));
            }
        } else if (side != facing && side != EnumFacing.UP && side != EnumFacing.DOWN && capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(getGasHandler(side));
        }
        return getCapability(capability, side);
    }

    @Override
    public boolean isOffsetCapabilityDisabled(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing back = MekanismUtils.getBack(facing);
        if (side != facing && side != left && side != right && side != back && side != EnumFacing.DOWN && (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side))) {
            if (offset.equals(new Vec3i(0, 2, 0))) {
                return side != EnumFacing.UP;
            }
            return true;
        } else if (side != facing && side != EnumFacing.UP && side != EnumFacing.DOWN && capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            for (int y = 0; y < 1; y++) {
                if (offset.equals(new Vec3i(back.getXOffset(), y, back.getZOffset()))) {
                    return side != back;
                } else if (offset.equals(new Vec3i(left.getXOffset(), y, left.getZOffset()))) {
                    return side != left;
                } else if (offset.equals(new Vec3i(right.getXOffset(), y, right.getZOffset()))) {
                    return side != right;
                }
            }
            return true;
        }
        return false;
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
    public boolean sideIsOutput(EnumFacing side) {
        return side == EnumFacing.UP;
    }

    private boolean isManagedStrictEnergy(@Nonnull Capability<?> capability) {
        return capability == Capabilities.STRICT_ENERGY_CAPABILITY || isStrictEnergy(capability);
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRendererLargeGasGenerator(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRendererLargeGasGenerator", e);
                }
            }
        }
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return 4;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismMultiblockMachine.proxy;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }


    @Override
    public double getMaxOutput() {
        return (isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy()) * 2;
    }

    @Override
    public double getMaxEnergy() {
        return isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy();
    }

    public double getTierEnergy() {
        return MekanismConfig.current().general.FROM_H2.val() * 1000 * processes * getThread();
    }

    public double getUsed() {
        return Math.round(clientUsed * 100) / 100D;
    }

    public int getMaxBurnTicks() {
        return maxBurnTicks;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.multiblockmachine.client.model.generator.ModelLargeGasGenerator.class;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    private void updateOutputFromFuel(@Nullable GasStack stack) {
        if (stack == null || stack.amount <= 0) {
            return;
        }
        GasStackFuelToEnergyRecipe recipe = RecipeHandler.getGasStackFuelToEnergyRecipe(stack);
        if (recipe != null) {
            output = recipe.getOutput().energyOutput * 2;
        }
    }

    private class FuelTank extends BasicGasTank {

        private FuelTank(IContentsListener listener) {
            super(MAX_GAS, BasicGasTank.notExternal, BasicGasTank.alwaysTrueBi,
                  gas -> RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.containsRecipe(gas), listener);
        }

        @Override
        public void setStack(GasStack stack) {
            boolean wasEmpty = isEmpty();
            super.setStack(stack);
            if (wasEmpty) {
                updateOutputFromFuel(stack);
            }
        }

        @Override
        public void setStackUnchecked(GasStack stack) {
            boolean wasEmpty = isEmpty();
            super.setStackUnchecked(stack);
            if (wasEmpty) {
                updateOutputFromFuel(stack);
            }
        }
    }
}
