package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.content.assemblicator.RecipeFormula;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.inventory.slot.*;
import mekanism.common.item.ItemCraftingFormula;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TileEntityFormulaicAssemblicator extends TileEntityElectricBlock implements ISideConfiguration, IUpgradeTile, IRedstoneControl, IConfigCardAccess, ISecurityTile {

    private static final NonNullList<ItemStack> EMPTY_LIST = NonNullList.create();
    public static final int SLOT_FORMULA = 0;
    public static final int SLOT_INPUT_FIRST = 1;
    public static final int SLOT_INPUT_LAST = 18;
    public static final int SLOT_CRAFT_MATRIX_FIRST = 19;
    public static final int SLOT_CRAFT_MATRIX_LAST = 27;
    public static final int SLOT_OUTPUT_FIRST = 28;
    public static final int SLOT_OUTPUT_LAST = 33;
    public static final int SLOT_ENERGY = 34;

    public InventoryCrafting dummyInv = MekanismUtils.getDummyCraftingInv();

    public double BASE_ENERGY_PER_TICK = MachineType.FORMULAIC_ASSEMBLICATOR.getUsage();

    public double energyPerTick = BASE_ENERGY_PER_TICK;

    public int BASE_TICKS_REQUIRED = 40;

    public int ticksRequired = BASE_TICKS_REQUIRED;

    public int operatingTicks;

    public boolean autoMode = false;

    public boolean isRecipe = false;

    public boolean stockControl = false;
    private boolean usedEnergy = false;
    public boolean needsOrganize = true; //organize on load
    private final HashedItem[] stockControlMap = new HashedItem[18];

    public int pulseOperations;

    public RecipeFormula formula;
    private IRecipe cachedRecipe;
    private NonNullList<ItemStack> lastRemainingItems = EMPTY_LIST;

    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentUpgrade upgradeComponent;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public TileComponentSecurity securityComponent;
    private final List<IInventorySlot> craftingGridSlots = new ArrayList<>();
    private final List<IInventorySlot> inputSlots = new ArrayList<>();
    private final List<IInventorySlot> outputSlots = new ArrayList<>();
    private EnergyInventorySlot energySlot;
    private FormulaInventorySlot formulaSlot;

    public ItemStack lastFormulaStack = ItemStack.EMPTY;
    public boolean needsFormulaUpdate = false;
    public ItemStack lastOutputStack = ItemStack.EMPTY;

    public TileEntityFormulaicAssemblicator() {
        super("FormulaicAssemblicator", MachineType.FORMULAIC_ASSEMBLICATOR.getStorage());
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        initializeInventorySlots();
        ConfigInfo itemConfig = configComponent.setupItemIOConfig(inputSlots, outputSlots, energySlot, false);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.EXTRA, new InventorySlotInfo(true, true, formulaSlot));
        }
        configComponent.setConfig(TransmissionType.ITEM, DataType.NONE, DataType.NONE, DataType.NONE, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);
        configComponent.setInputConfig(TransmissionType.ENERGY);

        upgradeComponent = new TileComponentUpgrade(this);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);

        securityComponent = new TileComponentSecurity(this);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        craftingGridSlots.clear();
        inputSlots.clear();
        outputSlots.clear();
        IContentsListener inputSlotChanged = () -> {
            listener.onContentsChanged();
            needsOrganize = stockControl;
        };
        IContentsListener listenAndRecheckRecipe = () -> {
            listener.onContentsChanged();
            recalculateRecipe();
        };
        InventorySlotHelper builder = createInventorySlotHelper();
        formulaSlot = builder.addSlot(FormulaInventorySlot.at(listenAndRecheckRecipe, 6, 26));
        for (int slotY = 0; slotY < 2; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                int index = slotY * 9 + slotX;
                InputInventorySlot inputSlot = InputInventorySlot.at(stack -> isInputSlotItemValid(index, stack), inputSlotChanged, 8 + slotX * 18, 98 + slotY * 18);
                builder.addSlot(inputSlot);
                inputSlots.add(inputSlot);
            }
        }
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 3; slotX++) {
                IInventorySlot craftingSlot = FormulaicCraftingSlot.at(() -> autoMode, listenAndRecheckRecipe, 26 + slotX * 18, 17 + slotY * 18);
                builder.addSlot(craftingSlot);
                craftingGridSlots.add(craftingSlot);
            }
        }
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 2; slotX++) {
                OutputInventorySlot outputSlot = OutputInventorySlot.at(listener, 116 + slotX * 18, 17 + slotY * 18);
                builder.addSlot(outputSlot);
                outputSlots.add(outputSlot);
            }
        }
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 152, 76));
        return builder.build();
    }

    @Override
    protected double getMainEnergyPerTick() {
        return energyPerTick;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public FormulaInventorySlot getFormulaSlot() {
        return formulaSlot;
    }

    private boolean isInputSlotItemValid(int slotIndex, ItemStack itemstack) {
        if (formula == null) {
            return true;
        }
        List<Integer> indices = formula.getIngredientIndices(world, itemstack);
        if (!indices.isEmpty()) {
            if (stockControl) {
                HashedItem stockItem = stockControlMap[slotIndex];
                return stockItem == null || ItemHandlerHelper.canItemStacksStack(stockItem.getInternalStack(), itemstack);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            checkFormula();
            recalculateRecipe();
            if (formula != null && stockControl) {
                buildStockControlMap();
            }
        }
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (formula != null && stockControl && needsOrganize) {
            buildStockControlMap();
            organizeStock();
            needsOrganize = false;
        }
        energySlot.fillContainerOrConvert();
        if (controlType != RedstoneControl.PULSE) {
            pulseOperations = 0;
        } else if (MekanismUtils.canFunction(this)) {
            pulseOperations++;
        }
        checkFormula();
        if (autoMode && formula == null) {
            toggleAutoMode();
        }

        boolean usedEnergy = false;
        if (autoMode && formula != null && ((controlType == RedstoneControl.PULSE && pulseOperations > 0) || MekanismUtils.canFunction(this))) {
            boolean canOperate = true;
            if (!isRecipe) {
                canOperate = moveItemsToGrid();
            }
            if (canOperate) {
                isRecipe = true;
                if (operatingTicks >= ticksRequired) {
                    if (doSingleCraft()) {
                        operatingTicks = 0;
                        if (pulseOperations > 0) {
                            pulseOperations--;
                        }
                    }
                } else if (Double.compare(getMainEnergyContainer().extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL), energyPerTick) == 0) {
                    usedEnergy = getMainEnergyContainer().extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL) > 0;
                    operatingTicks++;
                }
            } else {
                operatingTicks = 0;
            }
        } else {
            operatingTicks = 0;
        }
        this.usedEnergy = usedEnergy;
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    private void checkFormula() {
        RecipeFormula prev = formula;
        ItemStack formulaStack = formulaSlot.getStack();
        if (!formulaStack.isEmpty() && formulaStack.getItem() instanceof ItemCraftingFormula) {
            if (formula == null || lastFormulaStack != formulaStack) {
                loadFormula();
            }
        } else {
            formula = null;
        }
        if (prev != formula) {
            needsFormulaUpdate = true;
        }

        lastFormulaStack = formulaStack;
    }

    public void loadFormula() {
        ItemStack formulaStack = formulaSlot.getStack();
        ItemCraftingFormula formulaItem = (ItemCraftingFormula) formulaStack.getItem();
        if (formulaItem.getInventory(formulaStack) != null && !formulaItem.isInvalid(formulaStack)) {
            RecipeFormula recipe = new RecipeFormula(world, formulaItem.getInventory(formulaStack));
            if (recipe.isValidFormula(world)) {
                if (formula != null && !formula.isFormulaEqual(world, recipe)) {
                    formula = recipe;
                    operatingTicks = 0;
                } else if (formula == null) {
                    formula = recipe;
                }
            } else {
                formula = null;
                formulaItem.setInvalid(formulaStack, true);
            }
        } else {
            formula = null;
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        recalculateRecipe();
    }

    private void recalculateRecipe() {
        if (world != null && !isRemote()) {
            if (formula == null) {
                for (int i = 0; i < craftingGridSlots.size(); i++) {
                    dummyInv.setInventorySlotContents(i, StackUtils.size(craftingGridSlots.get(i).getStack(), 1));
                }

                lastRemainingItems = EMPTY_LIST;

                if (cachedRecipe == null || !cachedRecipe.matches(dummyInv, world)) {
                    cachedRecipe = CraftingManager.findMatchingRecipe(dummyInv, world);
                }
                if (cachedRecipe != null) {
                    lastOutputStack = cachedRecipe.getCraftingResult(dummyInv);
                    lastRemainingItems = cachedRecipe.getRemainingItems(dummyInv);
                } else {
                    lastOutputStack = MekanismUtils.findRepairRecipe(dummyInv, world);
                }
                isRecipe = !lastOutputStack.isEmpty();
            } else {
                isRecipe = formula.matches(world, craftingGridSlots);
                if (isRecipe) {
                    lastOutputStack = formula.assemble();
                    lastRemainingItems = formula.getRemainingItems();
                } else {
                    lastOutputStack = ItemStack.EMPTY;
                }
            }
            needsOrganize = true;
        }
    }

    private boolean doSingleCraft() {
        for (int i = 0; i < craftingGridSlots.size(); i++) {
            dummyInv.setInventorySlotContents(i, StackUtils.size(craftingGridSlots.get(i).getStack(), 1));
        }
        recalculateRecipe();

        ItemStack output = lastOutputStack;
        if (!output.isEmpty() && tryMoveToOutput(output, Action.SIMULATE) && (lastRemainingItems.isEmpty() || lastRemainingItems.stream().allMatch(it -> it.isEmpty() || tryMoveToOutput(it, Action.SIMULATE)))) {
            tryMoveToOutput(output, Action.EXECUTE);
            lastRemainingItems.forEach(remainingItem -> {
                if (!remainingItem.isEmpty()) {
                    tryMoveToOutput(remainingItem, Action.EXECUTE);
                }
            });
            for (IInventorySlot craftingSlot : craftingGridSlots) {
                if (!craftingSlot.isEmpty()) {
                    craftingSlot.shrinkStack(1, Action.EXECUTE);
                }
            }
            if (formula != null) {
                moveItemsToGrid();
            }
            markNoUpdateSync();
            return true;
        }
        return false;
    }

    private boolean craftSingle() {
        if (formula != null) {
            boolean canOperate = true;
            if (!formula.matches(world, craftingGridSlots)) {
                canOperate = moveItemsToGrid();
            }
            if (canOperate) {
                return doSingleCraft();
            }
        } else {
            return doSingleCraft();
        }
        return false;
    }

    private boolean moveItemsToGrid() {
        boolean ret = true;
        for (int i = 0; i < craftingGridSlots.size(); i++) {
            IInventorySlot recipeSlot = craftingGridSlots.get(i);
            ItemStack recipeStack = recipeSlot.getStack();
            if (formula.isIngredientInPos(world, recipeStack, i)) {
                continue;
            }
            if (!recipeStack.isEmpty()) {
                //Update recipeStack as well so we can check if it is empty without having to get it again
                recipeSlot.setStack(recipeStack = tryMoveToInput(recipeStack));
                markNoUpdateSync();
                if (!recipeStack.isEmpty()) {
                    ret = false;
                }
            } else {
                boolean found = false;
                for (int j = inputSlots.size() - 1; j >= 0; j--) {
                    //The stack stored in the stock inventory
                    IInventorySlot stockSlot = inputSlots.get(j);
                    ItemStack stockStack = stockSlot.getStack();
                    if (!stockStack.isEmpty() && formula.isIngredientInPos(world, stockStack, i)) {
                        recipeSlot.setStack(StackUtils.size(stockStack, 1));
                        stockSlot.shrinkStack(1, Action.EXECUTE);
                        markNoUpdateSync();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ret = false;
                }
            }
        }
        return ret;
    }

    private void craftAll() {
        while (craftSingle()) {
        }
    }

    private void moveItemsToInput(boolean forcePush) {
        for (int i = 0; i < craftingGridSlots.size(); i++) {
            IInventorySlot recipeSlot = craftingGridSlots.get(i);
            ItemStack recipeStack = recipeSlot.getStack();
            if (!recipeStack.isEmpty() && (forcePush || (formula != null && !formula.isIngredientInPos(world, recipeStack, i)))) {
                recipeSlot.setStack(tryMoveToInput(recipeStack));
            }
        }
        markNoUpdateSync();
    }

    private void toggleAutoMode() {
        if (autoMode) {
            operatingTicks = 0;
            autoMode = false;
        } else if (formula != null) {
            moveItemsToInput(false);
            autoMode = true;
        }
        markNoUpdateSync();
    }

    private void toggleStockControl() {
        if (!isRemote() && formula != null) {
            stockControl = !stockControl;
            if (stockControl) {
                buildStockControlMap();
                organizeStock();
                needsOrganize = false;
            }
        }
    }

    private void organizeStock() {
        if (formula == null) {
            return;
        }
        Object2IntMap<HashedItem> storedMap = new Object2IntLinkedOpenHashMap<>();
        for (IInventorySlot inputSlot : inputSlots) {
            if (!inputSlot.isEmpty()) {
                ItemStack stack = inputSlot.getStack();
                HashedItem item = HashedItem.create(stack);
                storedMap.put(item, storedMap.getInt(item) + stack.getCount());
            }
        }
        IntSet unused = new IntArraySet(stockControlMap.length);
        for (int i = 0; i < inputSlots.size(); i++) {
            HashedItem hashedItem = stockControlMap[i];
            if (hashedItem == null) {
                unused.add(i);
            } else {
                IInventorySlot slot = inputSlots.get(i);
                int stored = storedMap.getInt(hashedItem);
                if (stored > 0) {
                    int count = Math.min(hashedItem.getMaxStackSize(), stored);
                    if (count == stored) {
                        storedMap.removeInt(hashedItem);
                    } else {
                        storedMap.put(hashedItem, stored - count);
                    }
                    setSlotIfChanged(slot, hashedItem, count);
                } else if (!slot.isEmpty()) {
                    slot.setEmpty();
                }
            }
        }
        boolean empty = storedMap.isEmpty();
        for (int i : unused) {
            IInventorySlot slot = inputSlots.get(i);
            if (empty) {
                if (!slot.isEmpty()) {
                    slot.setEmpty();
                }
            } else {
                empty = setSlotIfChanged(storedMap, slot);
            }
        }
        if (empty) {
            markNoUpdateSync();
            return;
        }
        for (IInventorySlot inputSlot : inputSlots) {
            if (inputSlot.isEmpty() && setSlotIfChanged(storedMap, inputSlot)) {
                markNoUpdateSync();
                return;
            }
        }
        if (!storedMap.isEmpty()) {
            Mekanism.logger.error("Critical error: Formulaic Assemblicator had items left over after organizing stock. Impossible!");
        }
        markNoUpdateSync();
    }

    private boolean setSlotIfChanged(Object2IntMap<HashedItem> storedMap, IInventorySlot inputSlot) {
        boolean empty = false;
        ObjectIterator<Entry<HashedItem>> iterator = storedMap.object2IntEntrySet().iterator();
        Object2IntMap.Entry<HashedItem> next = iterator.next();
        HashedItem item = next.getKey();
        int stored = next.getIntValue();
        int count = Math.min(item.getMaxStackSize(), stored);
        if (count == stored) {
            iterator.remove();
            empty = storedMap.isEmpty();
        } else {
            next.setValue(stored - count);
        }
        setSlotIfChanged(inputSlot, item, count);
        return empty;
    }

    private static void setSlotIfChanged(IInventorySlot slot, HashedItem item, int count) {
        ItemStack stack = item.createStack(count);
        if (!ItemHandlerHelper.canItemStacksStack(slot.getStack(), stack) || slot.getCount() != count) {
            slot.setStack(stack);
        }
    }

    private void buildStockControlMap() {
        if (formula == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            int j = i * 2;
            ItemStack stack = formula.input.get(i);
            if (stack.isEmpty()) {
                stockControlMap[j] = null;
                stockControlMap[j + 1] = null;
            } else {
                HashedItem hashedItem = HashedItem.create(stack);
                stockControlMap[j] = hashedItem;
                stockControlMap[j + 1] = hashedItem;
            }
        }
    }

    private ItemStack tryMoveToInput(ItemStack stack) {
        stack = stack.copy();
        for (IInventorySlot stockSlot : inputSlots) {
            stack = stockSlot.insertItem(stack, Action.EXECUTE, AutomationType.INTERNAL);
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
    }

    private boolean tryMoveToOutput(ItemStack stack, Action action) {
        stack = stack.copy();
        for (IInventorySlot outputSlot : outputSlots) {
            stack = outputSlot.insertItem(stack, action, AutomationType.INTERNAL);
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack.isEmpty();
    }

    private void encodeFormula() {
        ItemStack formulaStack = formulaSlot.getStack();
        if (!formulaStack.isEmpty() && formulaStack.getItem() instanceof ItemCraftingFormula item) {
            if (item.getInventory(formulaStack) == null) {
                RecipeFormula formula = new RecipeFormula(world, craftingGridSlots);
                if (formula.isValidFormula(world)) {
                    item.setInventory(formulaStack, formula.input);
                    markNoUpdateSync();
                }
            }
        }
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        autoMode = nbtTags.getBoolean("autoMode");
        operatingTicks = nbtTags.getInteger("operatingTicks");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        pulseOperations = nbtTags.getInteger("pulseOperations");
        stockControl = nbtTags.getBoolean("stockControl");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("autoMode", autoMode);
        nbtTags.setInteger("operatingTicks", operatingTicks);
        nbtTags.setInteger("controlType", controlType.ordinal());
        nbtTags.setInteger("pulseOperations", pulseOperations);
        nbtTags.setBoolean("stockControl", stockControl);

    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                toggleAutoMode();
            } else if (type == 1) {
                encodeFormula();
            } else if (type == 2) {
                craftSingle();
            } else if (type == 3) {
                craftAll();
            } else if (type == 4) {
                if (formula != null) {
                    moveItemsToGrid();
                }
            } else if (type == 6) {
                if (formula == null) {
                    moveItemsToInput(true);
                }
            } else if (type == 5) {
                toggleStockControl();
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            autoMode = dataStream.readBoolean();
            operatingTicks = dataStream.readInt();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            isRecipe = dataStream.readBoolean();
            stockControl = dataStream.readBoolean();
            usedEnergy = dataStream.readBoolean();
            if (dataStream.readBoolean()) {
                if (dataStream.readBoolean()) {
                    NonNullList<ItemStack> inv = NonNullList.withSize(9, ItemStack.EMPTY);
                    for (int i = 0; i < 9; i++) {
                        if (dataStream.readBoolean()) {
                            inv.set(i, PacketHandler.readStack(dataStream));
                        }
                    }
                    formula = new RecipeFormula(world, inv);
                } else {
                    formula = null;
                }
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(autoMode);
        data.add(operatingTicks);
        data.add(controlType.ordinal());
        data.add(isRecipe);
        data.add(stockControl);
        data.add(usedEnergy);
        if (needsFormulaUpdate) {
            data.add(true);
            if (formula != null) {
                data.add(true);
                for (int i = 0; i < 9; i++) {
                    if (!formula.input.get(i).isEmpty()) {
                        data.add(true);
                        data.add(formula.input.get(i));
                    } else {
                        data.add(false);
                    }
                }
            } else {
                data.add(false);
            }
        } else {
            data.add(false);
        }
        needsFormulaUpdate = false;
        return data;
    }

    public boolean usedEnergy() {
        return usedEnergy;
    }

    public int getOperatingTicks() {
        return operatingTicks;
    }

    public int getTicksRequired() {
        return ticksRequired;
    }

    public boolean getAutoMode() {
        return autoMode;
    }

    public boolean hasRecipe() {
        return isRecipe;
    }

    public boolean getStockControl() {
        return stockControl;
    }

    public boolean hasValidFormula() {
        return formula != null;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::getAutoMode, value -> autoMode = value));
        container.track(SyncableInt.create(this::getOperatingTicks, value -> operatingTicks = value));
        container.track(SyncableInt.create(this::getTicksRequired, value -> ticksRequired = value));
        container.track(SyncableBoolean.create(this::hasRecipe, value -> isRecipe = value));
        container.track(SyncableBoolean.create(this::getStockControl, value -> stockControl = value));
        container.track(SyncableBoolean.create(this::usedEnergy, value -> usedEnergy = value));
        for (int i = 0; i < 9; i++) {
            int index = i;
            container.track(SyncableItemStack.create(
                  () -> formula == null ? ItemStack.EMPTY : formula.input.get(index),
                  stack -> {
                      if (formula != null) {
                          formula.input.set(index, stack);
                      }
                  }));
        }
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
        return true;
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
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
            if (!isRecalculatingAllUpgradables()) {
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
            }
        } else if (upgrade == Upgrade.ENERGY) {
            if (!isRecalculatingAllUpgradables()) {
                recalculateEnergyAndCapacity();
            }
        }
    }

    @Override
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
        super.onAllUpgradablesRecalculated(upgrades);
        if (upgrades.contains(Upgrade.ENERGY)) {
            recalculateEnergyAndCapacity();
        } else if (upgrades.contains(Upgrade.SPEED)) {
            energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        }
    }

    private void recalculateEnergyAndCapacity() {
        energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
        setEnergy(Math.min(getMaxEnergy(), getEnergy()));
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
        }
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }
    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }
}
