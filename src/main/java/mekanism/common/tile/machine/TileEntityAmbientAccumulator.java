package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.SlotOverlay;
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
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;

    public class TileEntityAmbientAccumulator extends TileEntityContainerBlock implements IRedstoneControl,
        IActiveState, ITankManager, ISecurityTile, IComparatorSupport, ISideConfiguration, ISustainedData, IRecipeLookupHandler<AmbientGasRecipe> {
    private final RecipeCacheLookupMonitor<AmbientGasRecipe> recipeCacheLookupMonitor = new RecipeCacheLookupMonitor<>(this);
    public BasicGasTank collectedGas;
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private int currentRedstoneLevel;

    private boolean isActive;

    public int cachedDimensionId;
    public AmbientGasRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;
    private GasInventorySlot gasSlot;

    public TileEntityAmbientAccumulator() {
        super("AmbientAccumulator");
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemOutputConfig(gasSlot);
        configComponent.fillConfig(TransmissionType.ITEM, DataType.OUTPUT);
        configComponent.setCanEject(TransmissionType.ITEM, false);
        configComponent.setupOutputConfig(TransmissionType.GAS, collectedGas);
        configComponent.fillConfig(TransmissionType.GAS, DataType.OUTPUT);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.GAS);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        gasSlot = builder.addSlot(GasInventorySlot.drain(collectedGas, listener, 127, 67));
        gasSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateCollectedGasTank());
        return builder.build();
    }

    private BasicGasTank getOrCreateCollectedGasTank() {
        if (collectedGas == null) {
            collectedGas = BasicGasTank.output(10000, this::onRecipeOutputContentsChanged);
        }
        return collectedGas;
    }

    private void onRecipeOutputContentsChanged() {
        onContentsChanged();
        recipeCacheLookupMonitor.onChange();
    }

    @Override
    public void onAsyncUpdateServer() {
        gasSlot.drainTank();
        AmbientGasRecipe recipe = getRecipe();

        if (recipe == null) {
            recipeCacheLookupMonitor.clear();
            setActive(false);
        } else {
            recipeCacheLookupMonitor.updateAndProcess();
        }

        if (world.getTotalWorldTime() % 20 == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }

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
        return recipe != null && recipe.canOperate(cachedDimensionId, collectedGas);
    }

    @Override
    public AmbientGasRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public CachedRecipe<AmbientGasRecipe> createNewCachedRecipe(AmbientGasRecipe recipe, int cacheIndex) {
        return new NoInputCachedRecipe<>(recipe, () -> false,
              () -> recipe.getInput().ingredient == cachedDimensionId,
              OutputHelper.getChanceGasOutputHandler(collectedGas, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getOutput().copy(),
              output -> output == null || output.getMaxOutput() == null)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(this::setActive)
              .setRequiredTicks(() -> 1)
              .setBaselineMaxOperations(() -> 1);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isActive);
        data.add(controlType.ordinal());
        TileUtils.addTankData(data, collectedGas);
        return data;
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        isActive = nbtTags.getBoolean("isActive");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("collectedGas")) {
            collectedGas.read(nbtTags.getCompoundTag("collectedGas"));
        }
        sanitizeAndClampTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            isActive = dataStream.readBoolean();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            TileUtils.readTankData(dataStream, collectedGas);
        }
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }


    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        boolean stateChange = isActive != active;
        if (stateChange) {
            isActive = active;
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
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
    public Object[] getManagedTanks() {
        return new Object[]{collectedGas};
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(collectedGas.getStored(), collectedGas.getMaxGas());
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
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "collectedGas", collectedGas.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            collectedGas.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "collectedGas"));
        }
        sanitizeAndClampTank();
    }

    private void sanitizeAndClampTank() {
        GasStack stored = collectedGas.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            collectedGas.setEmpty();
        } else if (stored != null) {
            collectedGas.setStackSize(stored.amount, Action.EXECUTE);
        }
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
