package mekanism.common.tile.prefab;

import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.gas.*;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Upgrade;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.base.ITankManager;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityCache;
import mekanism.common.capabilities.IToggleableCapability;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.capabilities.resolver.BasicCapabilityResolver;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import mekanism.common.capabilities.resolver.manager.*;
import mekanism.common.frequency.TileComponentFrequency;
import mekanism.common.inventory.ISlotBackedInventory;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.HeatCapabilityUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * 带有可已存储类型的方块
 */

public abstract class TileEntityContainerBlock extends TileEntityBasicBlock implements ISustainedInventory, IToggleableCapability,
        ISlotBackedInventory, IMekanismFluidHandler, IMekanismGasHandler, IMekanismStrictEnergyHandler, ITileHeatHandler {

    private static volatile Consumer<TileEntityContainerBlock> contentsChangedListener = tile -> {
    };

    /**
     * Optional module hook used to deny extraction from one physical container while a
     * higher-level transfer owns it.  The base Mekanism module deliberately knows nothing
     * about QIO (or any other optional module); an unset hook is therefore completely inert.
     */
    private static volatile BiPredicate<TileEntityContainerBlock, Object> containerExtractionGuard =
          (tile, container) -> false;

    private IInventorySlotHolder inventorySlotHolder;
    private IFluidTankHolder fluidTankHolder;
    private IGasTankHolder gasTankHolder;
    private IEnergyContainerHolder energyContainerHolder;
    private IHeatCapacitorHolder heatCapacitorHolder;
    private final CapabilityCache capabilityCache = new CapabilityCache();
    protected final TileComponentFrequency frequencyComponent = new TileComponentFrequency(this);
    private ItemHandlerManager itemHandlerManager;
    private FluidHandlerManager fluidHandlerManager;

    private GasHandlerManager gasHandlerManager;
    private EnergyHandlerManager energyHandlerManager;
    private HeatHandlerManager heatHandlerManager;
    private final CachedAmbientTemperature ambientTemperature = new CachedAmbientTemperature(this::getWorld, this::getPos);
    private final List<IInventorySlot> noSlots = Collections.emptyList();
    private final List<IExtendedFluidTank> noFluidTanks = Collections.emptyList();
    private final List<IExtendedGasTank> noGasTanks = Collections.emptyList();
    private final List<IEnergyContainer> noEnergyContainers = Collections.emptyList();
    private final List<IHeatCapacitor> noHeatCapacitors = Collections.emptyList();
    private boolean recalculatingAllUpgradables;

    public static void setContentsChangedListener(Consumer<TileEntityContainerBlock> listener) {
        contentsChangedListener = Objects.requireNonNull(listener, "Contents changed listener cannot be null");
    }

    /** Installs an optional predicate which returns true when extraction must be denied. */
    public static void setContainerExtractionGuard(
          BiPredicate<TileEntityContainerBlock, Object> guard) {
        containerExtractionGuard = Objects.requireNonNull(guard,
              "Container extraction guard cannot be null");
    }

    /** Returns whether the supplied physical slot/tank is currently protected. */
    public final boolean isContainerExtractionGuarded(@Nullable Object container) {
        return container != null && containerExtractionGuard.test(this, container);
    }

    /**
     * The full name of this machine.
     */
    public String fullName;

    /**
     * A simple tile entity with a container and facing state.
     *
     * @param name - full name of this tile entity
     */
    public TileEntityContainerBlock(String name) {
        fullName = name;
    }

    public TileComponentFrequency getFrequencyComponent() {
        return frequencyComponent;
    }

    protected void initializeInventorySlots() {
        initializeContainerHolders(true);
    }

    @Nullable
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        return null;
    }

    protected void setInventorySlotHolder(IInventorySlotHolder slotHolder) {
        inventorySlotHolder = slotHolder;
        clearCapabilityCache();
        initializeCapabilityManagers();
    }

    protected InventorySlotHelper createInventorySlotHelper() {
        if (this instanceof ISideConfiguration) {
            return InventorySlotHelper.forSideWithConfig((ISideConfiguration) this);
        }
        return InventorySlotHelper.forSide(() -> facing);
    }

    protected FluidTankHelper createFluidTankHelper() {
        if (this instanceof ISideConfiguration) {
            ISideConfiguration sideConfiguration = (ISideConfiguration) this;
            return FluidTankHelper.forSideWithConfig(sideConfiguration::getOrientation, sideConfiguration::getConfig);
        }
        return FluidTankHelper.forSide(() -> facing);
    }

    protected GasTankHelper createGasTankHelper() {
        if (this instanceof ISideConfiguration) {
            return GasTankHelper.forSideWithConfig((ISideConfiguration) this);
        }
        return GasTankHelper.forSide(() -> facing);
    }

    protected EnergyContainerHelper createEnergyContainerHelper() {
        if (this instanceof ISideConfiguration) {
            return EnergyContainerHelper.forSideWithConfig((ISideConfiguration) this);
        }
        return EnergyContainerHelper.forSide(() -> facing);
    }

    protected HeatCapacitorHelper createHeatCapacitorHelper() {
        if (this instanceof ISideConfiguration) {
            return HeatCapacitorHelper.forSideWithConfig((ISideConfiguration) this);
        }
        return HeatCapacitorHelper.forSide(() -> facing);
    }

    @Nullable
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        Object[] tanks = getManagedTanks();
        if (tanks != null) {
            for (Object tank : tanks) {
                if (tank instanceof IExtendedFluidTank fluidTank) {
                    builder.addTank(fluidTank);
                }
            }
        }
        return builder.build();
    }

    @Nullable
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        Object[] tanks = getManagedTanks();
        if (tanks != null) {
            for (Object tank : tanks) {
                if (tank instanceof IExtendedGasTank gasTank) {
                    builder.addTank(gasTank);
                }
            }
        }
        return builder.build();
    }

    @Nullable
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        if (this instanceof IEnergyWrapper energyWrapper) {
            if (!(this instanceof ISideConfiguration configurable) || configurable.getConfig() == null) {
                return ProxiedEnergyContainerHolder.create(
                      side -> side != null && energyWrapper.sideIsConsumer(side),
                      side -> side != null && energyWrapper.sideIsOutput(side),
                      side -> side == null || energyWrapper.sideIsConsumer(side) || energyWrapper.sideIsOutput(side) ? Collections.singletonList(energyWrapper) : Collections.emptyList());
            }
            EnergyContainerHelper builder = createEnergyContainerHelper();
            builder.addContainer(energyWrapper);
            return builder.build();
        }
        return null;
    }

    @Nullable
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        HeatCapacitorHelper builder = createHeatCapacitorHelper();
        if (this instanceof IHeatCapacitor heatCapacitor) {
            builder.addCapacitor(heatCapacitor);
        }
        return builder.build();
    }

    @Nullable
    private Object[] getManagedTanks() {
        return this instanceof ITankManager tankManager ? tankManager.getManagedTanks() : null;
    }

    @Nullable
    public IInventorySlot getInventorySlot(int slot) {
        List<IInventorySlot> slots = getInventorySlots(null);
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
    }

    @Nullable
    protected <SLOT extends IInventorySlot> SLOT getInventorySlotAs(int slot, Class<SLOT> slotType) {
        IInventorySlot inventorySlot = getInventorySlot(slot);
        return slotType.isInstance(inventorySlot) ? slotType.cast(inventorySlot) : null;
    }

    protected void clearCapabilityCache() {
        capabilityCache.invalidateAll();
    }

    public void invalidateCapability(@Nullable Capability<?> capability, @Nullable EnumFacing side) {
        capabilityCache.invalidate(capability, side);
    }

    protected void clearContainerHolderCache() {
        initializeContainerHolders(false);
    }

    protected void initializeContainerHolders() {
        initializeContainerHolders(false);
    }

    protected void initializeContainerHolders(boolean initializeInventory) {
        fluidTankHolder = getInitialFluidTanks(this);
        gasTankHolder = getInitialGasTanks(this);
        energyContainerHolder = getInitialEnergyContainers(this);
        heatCapacitorHolder = getInitialHeatCapacitors(this);
        if (initializeInventory) {
            inventorySlotHolder = getInitialInventory(this);
        }
        initializeCapabilityManagers();
    }

    protected void initializeCapabilityManagers() {
        itemHandlerManager = new ItemHandlerManager(inventorySlotHolder, this);
        fluidHandlerManager = new FluidHandlerManager(fluidTankHolder, this);
        gasHandlerManager = new GasHandlerManager(gasTankHolder, this);
        energyHandlerManager = new EnergyHandlerManager(energyContainerHolder, this);
        heatHandlerManager = new HeatHandlerManager(this, heatCapacitorHolder);
        List<ICapabilityResolver> resolvers = new ArrayList<>(Arrays.asList(itemHandlerManager, fluidHandlerManager, gasHandlerManager, energyHandlerManager, heatHandlerManager));
        if (this instanceof IConfigurable configurable) {
            resolvers.add(BasicCapabilityResolver.constant(Capabilities.CONFIGURABLE_CAPABILITY, configurable));
        }
        capabilityCache.setCapabilityResolvers(resolvers);
    }

    @Override
    public boolean isEmpty() {
        return tryCallContainerTransaction(ISlotBackedInventory.super::isEmpty, () -> true);
    }

    @Nonnull
    @Override
    public ItemStack decrStackSize(int index, int count) {
        return tryCallContainerTransaction(() -> ISlotBackedInventory.super.decrStackSize(index, count), () -> ItemStack.EMPTY);
    }

    @Nonnull
    @Override
    public ItemStack removeStackFromSlot(int index) {
        return tryCallContainerTransaction(() -> ISlotBackedInventory.super.removeStackFromSlot(index), () -> ItemStack.EMPTY);
    }

    @Override
    public void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
        runContainerTransaction(() -> ISlotBackedInventory.super.setInventorySlotContents(index, stack));
    }

    @Override
    public void clear() {
        runContainerTransaction(ISlotBackedInventory.super::clear);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        readCustomNBTBeforeInventory(nbtTags);
        if (persistInventory() && hasInventory()) {
            DataHandlerUtils.readContainers(getInventorySlots(null), nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
        }
        if (persistFluidTanks() && hasFluidTanks() && hasStoredFluidTanks(nbtTags)) {
            DataHandlerUtils.readContainers(getFluidTanks(null), nbtTags.getTagList(NBTConstants.FLUID_TANKS, NBT.TAG_COMPOUND));
        }
        if (persistGasTanks() && hasGasTanks() && hasStoredGasTanks(nbtTags)) {
            DataHandlerUtils.readContainers(getGasTanks(null), nbtTags.getTagList(NBTConstants.GAS_TANKS, NBT.TAG_COMPOUND));
        }
        if (persistHeatCapacitors() && canHandleHeat() && nbtTags.hasKey(NBTConstants.HEAT_CAPACITORS, NBT.TAG_LIST)) {
            DataHandlerUtils.readContainers(getHeatCapacitors(null), nbtTags.getTagList(NBTConstants.HEAT_CAPACITORS, NBT.TAG_COMPOUND));
        }
    }

    protected void readCustomNBTBeforeInventory(NBTTagCompound nbtTags) {
    }

    protected boolean hasStoredFluidTanks(NBTTagCompound nbtTags) {
        return nbtTags.hasKey(NBTConstants.FLUID_TANKS, NBT.TAG_LIST);
    }

    protected boolean hasStoredGasTanks(NBTTagCompound nbtTags) {
        return nbtTags.hasKey(NBTConstants.GAS_TANKS, NBT.TAG_LIST);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        if (persistInventory() && hasInventory()) {
            nbtTags.setTag(NBTConstants.ITEMS, DataHandlerUtils.writeContainers(getInventorySlots(null)));
        }
        if (persistFluidTanks() && hasFluidTanks()) {
            nbtTags.setTag(NBTConstants.FLUID_TANKS, DataHandlerUtils.writeContainers(getFluidTanks(null)));
        }
        if (persistGasTanks() && hasGasTanks()) {
            nbtTags.setTag(NBTConstants.GAS_TANKS, DataHandlerUtils.writeContainers(getGasTanks(null)));
        }
        if (persistHeatCapacitors() && canHandleHeat()) {
            nbtTags.setTag(NBTConstants.HEAT_CAPACITORS, DataHandlerUtils.writeContainers(getHeatCapacitors(null)));
        }
    }

    @Override
    public boolean isUsableByPlayer(@Nonnull EntityPlayer entityplayer) {
        return !isInvalid() && this.world.isBlockLoaded(this.pos);//prevent Containers from remaining valid after the chunk has unloaded;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize(getBlockType().getTranslationKey() + "." + fullName + ".name");
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public void closeInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    public boolean canInsertItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        return tryCallContainerTransaction(() -> {
            IInventorySlot slot = getInventorySlot(slotID);
            return slot != null && canInsertItem(slot, itemstack, side) &&
                  slot.insertItem(itemstack, Action.SIMULATE, AutomationType.EXTERNAL).getCount() < itemstack.getCount();
        }, () -> false);
    }

    @Nonnull
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return getInventorySlotIdsForSide(side);
    }

    @Nonnull
    protected int[] getInventorySlotIdsForSide(@Nullable EnumFacing side) {
        List<IInventorySlot> slots = getInventorySlots(side);
        List<IInventorySlot> internalSlots = getInventorySlots(null);
        int[] slotIds = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            slotIds[i] = internalSlots.indexOf(slots.get(i));
        }
        return slotIds;
    }

    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        return tryCallContainerTransaction(() -> {
            IInventorySlot slot = getInventorySlot(slotID);
            return slot != null && canExtractItem(slot, itemstack, side) &&
                  !slot.extractItem(itemstack.getCount(), Action.SIMULATE, AutomationType.EXTERNAL).isEmpty();
        }, () -> false);
    }

    @Override
    public void setInventory(NBTTagList nbtTags, Object... data) {
        runContainerTransaction(() -> {
            if (nbtTags != null && nbtTags.tagCount() > 0 && hasInventory() && persistInventory()) {
                DataHandlerUtils.readContainers(getInventorySlots(null), nbtTags);
            }
        });
    }

    @Override
    public NBTTagList getInventory(Object... data) {
        return callContainerTransaction(() -> hasInventory() && persistInventory() ?
              DataHandlerUtils.writeContainers(getInventorySlots(null)) : new NBTTagList());
    }

    public boolean persistInventory() {
        return hasInventory();
    }

    protected boolean persistFluidTanks() {
        return hasFluidTanks();
    }

    protected boolean persistGasTanks() {
        return hasGasTanks();
    }

    protected boolean persistHeatCapacitors() {
        return canHandleHeat();
    }

    protected void writeSustainedFluidTanks(ItemStack itemStack) {
        runContainerTransaction(() -> ItemDataUtils.writeContainers(itemStack, NBTConstants.FLUID_TANKS, getFluidTanks(null)));
    }

    protected boolean readSustainedFluidTanks(ItemStack itemStack) {
        return callContainerTransaction(() -> {
            if (ItemDataUtils.hasData(itemStack, NBTConstants.FLUID_TANKS, NBT.TAG_LIST)) {
                ItemDataUtils.readContainers(itemStack, NBTConstants.FLUID_TANKS, getFluidTanks(null));
                return true;
            }
            return false;
        });
    }

    protected void writeSustainedGasTanks(ItemStack itemStack) {
        runContainerTransaction(() -> ItemDataUtils.writeContainers(itemStack, NBTConstants.GAS_TANKS, getGasTanks(null)));
    }

    protected boolean readSustainedGasTanks(ItemStack itemStack) {
        return callContainerTransaction(() -> {
            if (ItemDataUtils.hasData(itemStack, NBTConstants.GAS_TANKS, NBT.TAG_LIST)) {
                ItemDataUtils.readContainers(itemStack, NBTConstants.GAS_TANKS, getGasTanks(null));
                return true;
            }
            return false;
        });
    }

    public void recalculateUpgradables(Upgrade upgradeType) {
    }

    /**
     * Recalculates an ordered snapshot of upgrade types while preserving per-type virtual dispatch.
     */
    public final void recalculateAllUpgradables(Collection<Upgrade> upgrades) {
        if (recalculatingAllUpgradables) {
            throw new IllegalStateException("Upgrade recalculation batch is already active");
        }
        Set<Upgrade> snapshot = new LinkedHashSet<>();
        if (upgrades != null) {
            for (Upgrade upgrade : upgrades) {
                if (upgrade != null) {
                    snapshot.add(upgrade);
                }
            }
        }
        recalculatingAllUpgradables = true;
        try {
            for (Upgrade upgrade : snapshot) {
                recalculateUpgradables(upgrade);
            }
        } finally {
            recalculatingAllUpgradables = false;
        }
        onAllUpgradablesRecalculated(Collections.unmodifiableSet(snapshot));
    }

    protected final boolean isRecalculatingAllUpgradables() {
        return recalculatingAllUpgradables;
    }

    /**
     * Called once after every per-type callback completes successfully. Overrides must call {@code super}.
     */
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capabilityCache.hasCapability(capability, side) || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        T resolved = capabilityCache.getCapability(capability, side);
        if (resolved != null) {
            return resolved;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        if (side != null && this instanceof ISideConfiguration) {
            ISideConfiguration configurable = (ISideConfiguration) this;
            if (configurable.getConfig() != null && configurable.getConfig().isCapabilityDisabled(capability, side, configurable.getOrientation())) {
                return true;
            }
        }
        return IToggleableCapability.super.isCapabilityDisabled(capability, side);
    }

    protected IItemHandler getItemHandler(EnumFacing side) {
        return itemHandlerManager == null ? null : itemHandlerManager.resolve(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
    }

    protected IFluidHandler getFluidHandler(EnumFacing side) {
        return fluidHandlerManager == null ? null : fluidHandlerManager.resolve(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    protected IGasHandler getGasHandler(EnumFacing side) {
        return gasHandlerManager == null ? null : gasHandlerManager.resolve(Capabilities.GAS_HANDLER_CAPABILITY, side);
    }

    protected <T> T getEnergyHandler(Capability<T> capability, EnumFacing side) {
        return energyHandlerManager == null ? null : energyHandlerManager.resolve(capability, side);
    }

    private boolean hasItemHandler(@Nullable EnumFacing side) {
        return hasInventory() && !getInventorySlots(side).isEmpty();
    }

    protected boolean canInsertItems(@Nullable EnumFacing side) {
        return side == null || inventorySlotHolder == null || inventorySlotHolder.canInsert(side);
    }

    protected boolean canExtractItems(@Nullable EnumFacing side) {
        return side == null || inventorySlotHolder == null || inventorySlotHolder.canExtract(side);
    }

    protected boolean canInsertItem(@Nonnull IInventorySlot slot, @Nonnull ItemStack stack, @Nullable EnumFacing side) {
        return side == null || inventorySlotHolder == null || inventorySlotHolder.canInsert(side, slot);
    }

    protected boolean canExtractItem(@Nonnull IInventorySlot slot, @Nonnull ItemStack stack, @Nullable EnumFacing side) {
        return !isContainerExtractionGuarded(slot) &&
              (side == null || inventorySlotHolder == null || inventorySlotHolder.canExtract(side, slot));
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        if (!hasInventory()) {
            return noSlots;
        }
        return inventorySlotHolder == null ? noSlots : inventorySlotHolder.getInventorySlots(side);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack, @Nullable EnumFacing side) {
        runContainerTransaction(() -> {
            IInventorySlot inventorySlot = getInventorySlot(slot, side);
            if (inventorySlot != null) {
                inventorySlot.setStack(stack);
            }
        });
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, @Nullable EnumFacing side, @Nonnull Action action) {
        return tryCallContainerTransaction(() -> {
            IInventorySlot inventorySlot = getInventorySlot(slot, side);
            if (inventorySlot == null || !canInsertItem(inventorySlot, stack, side)) {
                return stack;
            }
            return inventorySlot.insertItem(stack, action, AutomationType.handler(side));
        }, () -> stack);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, @Nullable EnumFacing side, @Nonnull Action action) {
        return tryCallContainerTransaction(() -> {
            IInventorySlot inventorySlot = getInventorySlot(slot, side);
            if (inventorySlot == null || !canExtractItem(inventorySlot, ItemStack.EMPTY, side)) {
                return ItemStack.EMPTY;
            }
            return inventorySlot.extractItem(amount, action, AutomationType.handler(side));
        }, () -> ItemStack.EMPTY);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack, @Nullable EnumFacing side) {
        IInventorySlot inventorySlot = getInventorySlot(slot, side);
        if (inventorySlot == null || !canInsertItem(inventorySlot, stack, side)) {
            return false;
        }
        if (inventorySlot instanceof BasicInventorySlot basicSlot) {
            return basicSlot.isItemValidForInsertion(stack, AutomationType.handler(side));
        }
        return inventorySlot.isItemValid(stack);
    }

    public boolean hasFluidTanks() {
        return !getFluidTanks(null).isEmpty();
    }

    @Override
    public boolean canHandleFluid() {
        return fluidTankHolder != null && fluidHandlerManager != null && fluidHandlerManager.canHandle();
    }

    @Nonnull
    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        return canHandleFluid() ? fluidTankHolder.getTanks(side) : noFluidTanks;
    }

    @Override
    public void setFluidInTank(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side) {
        runContainerTransaction(() -> {
            IExtendedFluidTank fluidTank = getFluidTank(tank, side);
            if (fluidTank != null) {
                fluidTank.setStack(stack);
            }
        });
    }

    public boolean canInsertFluid(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return fluidTankHolder != null && fluidTankHolder.canInsert(side);
    }

    public boolean canExtractFluid(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return fluidTankHolder != null && fluidTankHolder.canExtract(side);
    }

    protected boolean canInsertFluid(@Nonnull IExtendedFluidTank tank, @Nullable EnumFacing side) {
        return side == null || fluidTankHolder == null || fluidTankHolder.canInsert(side, tank);
    }

    protected boolean canExtractFluid(@Nonnull IExtendedFluidTank tank, @Nullable EnumFacing side) {
        return !isContainerExtractionGuarded(tank) &&
              (side == null || fluidTankHolder == null || fluidTankHolder.canExtract(side, tank));
    }

    @Override
    @Nullable
    public FluidStack insertFluid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            IExtendedFluidTank fluidTank = getFluidTank(tank, side);
            if (fluidTank == null || !canInsertFluid(fluidTank, side)) {
                return stack;
            }
            return fluidTank.insert(stack, action, AutomationType.handler(side));
        }, () -> stack);
    }

    @Override
    @Nullable
    public FluidStack insertFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canInsertFluid(side)) {
                return stack;
            }
            if (side == null || fluidTankHolder == null) {
                return IMekanismFluidHandler.super.insertFluid(stack, side, action);
            }
            List<IExtendedFluidTank> fluidTanks = fluidTankHolder.getTanksForInsert(side);
            return ExtendedFluidHandlerUtils.insert(stack, action, AutomationType.handler(side), fluidTanks.size(), fluidTanks);
        }, () -> stack);
    }

    @Override
    @Nullable
    public FluidStack extractFluid(int tank, int amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            IExtendedFluidTank fluidTank = getFluidTank(tank, side);
            if (fluidTank == null || !canExtractFluid(fluidTank, side)) {
                return null;
            }
            return fluidTank.extract(amount, action, AutomationType.handler(side));
        }, () -> null);
    }

    @Override
    @Nullable
    public FluidStack extractFluid(int amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canExtractFluid(side)) {
                return null;
            }
            if (side == null || fluidTankHolder == null) {
                return IMekanismFluidHandler.super.extractFluid(amount, side, action);
            }
            List<IExtendedFluidTank> fluidTanks = fluidTankHolder.getTanksForExtract(side);
            return ExtendedFluidHandlerUtils.extract(amount, action, AutomationType.handler(side), fluidTanks.size(), fluidTanks);
        }, () -> null);
    }

    @Override
    @Nullable
    public FluidStack extractFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canExtractFluid(side)) {
                return null;
            }
            if (side == null || fluidTankHolder == null) {
                return IMekanismFluidHandler.super.extractFluid(stack, side, action);
            }
            List<IExtendedFluidTank> fluidTanks = fluidTankHolder.getTanksForExtract(side);
            return ExtendedFluidHandlerUtils.extract(stack, action, AutomationType.handler(side), fluidTanks.size(), fluidTanks);
        }, () -> null);
    }

    public boolean hasGasTanks() {
        return !getGasTanks(null).isEmpty();
    }

    @Override
    public boolean canHandleGas() {
        return gasTankHolder != null && gasHandlerManager != null && gasHandlerManager.canHandle();
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        return canHandleGas() ? gasTankHolder.getTanks(side) : noGasTanks;
    }

    @Override
    public void setGasInTank(int tank, @Nullable GasStack stack, @Nullable EnumFacing side) {
        runContainerTransaction(() -> {
            IExtendedGasTank gasTank = getGasTank(tank, side);
            if (gasTank != null) {
                gasTank.setStack(stack);
            }
        });
    }

    public boolean canInsertGas(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return gasTankHolder != null && gasTankHolder.canInsert(side);
    }

    public boolean canExtractGas(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return gasTankHolder != null && gasTankHolder.canExtract(side);
    }

    protected boolean canInsertGas(@Nonnull IExtendedGasTank tank, @Nullable EnumFacing side) {
        return side == null || gasTankHolder == null || gasTankHolder.canInsert(side, tank);
    }

    protected boolean canExtractGas(@Nonnull IExtendedGasTank tank, @Nullable EnumFacing side) {
        return !isContainerExtractionGuarded(tank) &&
              (side == null || gasTankHolder == null || gasTankHolder.canExtract(side, tank));
    }

    @Override
    @Nullable
    public GasStack insertGas(int tank, @Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            IExtendedGasTank gasTank = getGasTank(tank, side);
            if (gasTank == null || !canInsertGas(gasTank, side)) {
                return stack;
            }
            return gasTank.insert(stack, action, AutomationType.handler(side));
        }, () -> stack);
    }

    @Override
    @Nullable
    public GasStack insertGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canInsertGas(side)) {
                return stack;
            }
            if (side == null || gasTankHolder == null) {
                return IMekanismGasHandler.super.insertGas(stack, side, action);
            }
            List<IExtendedGasTank> gasTanks = gasTankHolder.getTanksForInsert(side);
            return ExtendedGasHandlerUtils.insert(stack, action, AutomationType.handler(side), gasTanks.size(), gasTanks);
        }, () -> stack);
    }

    @Override
    @Nullable
    public GasStack extractGas(int tank, int amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            IExtendedGasTank gasTank = getGasTank(tank, side);
            if (gasTank == null || !canExtractGas(gasTank, side)) {
                return null;
            }
            return gasTank.extract(amount, action, AutomationType.handler(side));
        }, () -> null);
    }

    @Override
    @Nullable
    public GasStack extractGas(int amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canExtractGas(side)) {
                return null;
            }
            if (side == null || gasTankHolder == null) {
                return IMekanismGasHandler.super.extractGas(amount, side, action);
            }
            List<IExtendedGasTank> gasTanks = gasTankHolder.getTanksForExtract(side);
            return ExtendedGasHandlerUtils.extract(amount, action, AutomationType.handler(side), gasTanks.size(), gasTanks);
        }, () -> null);
    }

    @Override
    @Nullable
    public GasStack extractGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canExtractGas(side)) {
                return null;
            }
            if (side == null || gasTankHolder == null) {
                return IMekanismGasHandler.super.extractGas(stack, side, action);
            }
            List<IExtendedGasTank> gasTanks = gasTankHolder.getTanksForExtract(side);
            return ExtendedGasHandlerUtils.extract(stack, action, AutomationType.handler(side), gasTanks.size(), gasTanks);
        }, () -> null);
    }

    public boolean hasEnergyContainers() {
        return !getEnergyContainers(null).isEmpty();
    }

    @Override
    public boolean canHandleEnergy() {
        return energyContainerHolder != null && energyHandlerManager != null && energyHandlerManager.canHandle();
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return canHandleEnergy() ? energyContainerHolder.getEnergyContainers(side) : noEnergyContainers;
    }

    @Override
    public void setEnergy(int container, double energy, @Nullable EnumFacing side) {
        runContainerTransaction(() -> {
            IEnergyContainer energyContainer = getEnergyContainer(container, side);
            if (energyContainer != null) {
                energyContainer.setEnergy(Math.max(0, energy));
            }
        });
    }

    public boolean canInsertEnergy(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return energyContainerHolder != null && energyContainerHolder.canInsert(side);
    }

    public boolean canExtractEnergy(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return energyContainerHolder != null && energyContainerHolder.canExtract(side);
    }

    @Override
    public double insertEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> side != null && !canInsertEnergy(side) ? amount :
              IMekanismStrictEnergyHandler.super.insertEnergy(container, amount, side, action), () -> amount);
    }

    @Override
    public double insertEnergy(double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> side != null && !canInsertEnergy(side) ? amount :
              IMekanismStrictEnergyHandler.super.insertEnergy(amount, side, action), () -> amount);
    }

    @Override
    public double extractEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> side != null && !canExtractEnergy(side) ? 0 :
              IMekanismStrictEnergyHandler.super.extractEnergy(container, amount, side, action), () -> 0D);
    }

    @Override
    public double extractEnergy(double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> side != null && !canExtractEnergy(side) ? 0 :
              IMekanismStrictEnergyHandler.super.extractEnergy(amount, side, action), () -> 0D);
    }

    @Override
    public boolean canHandleHeat() {
        return heatCapacitorHolder != null && heatHandlerManager != null && heatHandlerManager.canHandle();
    }

    @Override
    public int getHeatCapacitorCount() {
        return getHeatCapacitorCount(getHeatSideFor());
    }

    @Override
    public Object getHeatIdentity() {
        return getHeatIdentity(getHeatSideFor());
    }

    @Override
    public double getTemperature(int capacitor) {
        return getTemperature(capacitor, getHeatSideFor());
    }

    @Override
    public double getInverseConduction(int capacitor) {
        return getInverseConduction(capacitor, getHeatSideFor());
    }

    @Override
    public double getHeatCapacity(int capacitor) {
        return getHeatCapacity(capacitor, getHeatSideFor());
    }

    @Override
    public void handleHeat(int capacitor, double transfer) {
        handleHeat(capacitor, transfer, getHeatSideFor());
    }

    @Nonnull
    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        return canHandleHeat() ? heatCapacitorHolder.getHeatCapacitors(side) : noHeatCapacitors;
    }

    @Override
    public void handleHeat(double transfer) {
        handleHeat(transfer, getHeatSideFor());
    }

    public boolean canInsertHeat(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return heatCapacitorHolder != null && heatCapacitorHolder.canInsert(side);
    }

    public boolean canExtractHeat(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }
        return heatCapacitorHolder != null && heatCapacitorHolder.canExtract(side);
    }

    @Nullable
    @Override
    public IHeatHandler getAdjacent(EnumFacing side) {
        if (world == null) {
            return null;
        }
        TileEntity adjacent = MekanismUtils.getTileEntity(world, pos.offset(side));
        return HeatCapabilityUtils.getHandler(adjacent, side.getOpposite());
    }

    @Override
    public double getAmbientTemperature(EnumFacing side) {
        return ambientTemperature.getTemperature(side);
    }

    @Nonnull
    public List<Slot> getContainerSlots() {
        List<Slot> slots = new ArrayList<>();
        for (IInventorySlot inventorySlot : getInventorySlots(null)) {
            Slot slot = inventorySlot.createContainerSlot();
            if (slot != null) {
                if (slot instanceof InventoryContainerSlot inventoryContainerSlot) {
                    inventoryContainerSlot.setExtractionGuard(() ->
                          isContainerExtractionGuarded(inventorySlot));
                }
                slots.add(slot);
            }
        }
        return slots;
    }

    @Override
    public boolean hasInventory() {
        return inventorySlotHolder != null;
    }

    @Override
    public void onContentsChanged() {
        // Every real container mutation invalidates an in-flight recipe plan. The
        // listener is invoked by inventory, fluid, gas, energy and heat handlers.
        markProcessingStateChanged();
        markNoUpdateSync();
        if (world != null && !world.isRemote) {
            contentsChangedListener.accept(this);
        }
    }

}
