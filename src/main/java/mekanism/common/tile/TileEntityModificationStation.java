package mekanism.common.tile;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.IContentsListener;
import mekanism.api.gear.IModule;
import mekanism.api.gear.ModuleData;
import mekanism.common.Upgrade;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.IModuleItem;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.tile.prefab.TileEntityOperationalMachine;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

public class TileEntityModificationStation extends TileEntityOperationalMachine implements IBoundingBlock {

    private EnergyInventorySlot energySlot;
    private InputInventorySlot moduleSlot;
    private InputInventorySlot containerSlot;

    public TileEntityModificationStation() {
        super("null", MachineType.MODIFICATION_STATION, 0, 40);
        upgradeComponent.removeSupported(Upgrade.MUFFLING);
        upgradeComponent.removeSupported(Upgrade.SPEED);
        upgradeComponent.removeSupported(Upgrade.ENERGY);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        moduleSlot = builder.addSlot(InputInventorySlot.at(stack -> stack.getItem() instanceof IModuleItem, listener, 35, 118));
        containerSlot = builder.addSlot(InputInventorySlot.at(stack -> stack.getItem() instanceof IModuleContainerItem, listener, 125, 118));
        moduleSlot.setSlotType(ContainerSlotType.NORMAL);
        moduleSlot.setSlotOverlay(SlotOverlay.MODULE);
        containerSlot.setSlotType(ContainerSlotType.NORMAL);
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 151, 21));
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        ItemStack moduleStack = moduleSlot.getStack();
        ItemStack containerStack = containerSlot.getStack();
        if (MekanismUtils.canFunction(this)) {
            boolean operated = false;
            if (getEnergy() >= energyPerTick && !moduleStack.isEmpty() && !containerStack.isEmpty()) {
                ModuleData<?> data = ((IModuleItem) moduleStack.getItem()).getModuleData();
                // make sure the container supports this module
                if (ModuleHelper.get().getSupported(containerStack).contains(data)) {
                    // make sure we can still install more of this module
                    IModule<?> module = ModuleHelper.get().load(containerStack, data);
                    if (module == null || module.getInstalledCount() < data.getMaxStackSize()) {
                        operated = true;
                        operatingTicks++;
                        getMainEnergyContainer().extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                        if (operatingTicks == ticksRequired) {
                            operatingTicks = 0;
                            ((IModuleContainerItem) containerStack.getItem()).addModule(containerStack, data);
                            containerSlot.onContentsChanged();
                            moduleSlot.shrinkStack(1, Action.EXECUTE);
                        }
                    }
                }

            }
            if (!operated) {
                operatingTicks = 0;
            }
        }
        prevEnergy = getEnergy();
    }

    public void removeModule(EntityPlayer player, ModuleData<?> type, boolean removeAll) {
        ItemStack stack = containerSlot.getStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof IModuleContainerItem container)) {
            return;
        }
        IModule<?> module = ModuleHelper.get().load(stack, type);
        int installed = module == null ? 0 : module.getInstalledCount();
        if (installed > 0) {
            int toRemove = removeAll ? installed : 1;
            ItemStack moduleStack = type.getStack();
            moduleStack.setCount(toRemove);
            if (addItemStackToInventory(player, moduleStack)) {
                for (int i = 0; i < toRemove; i++) {
                    container.removeModule(stack, type);
                }
                containerSlot.setStack(stack);
            }
        }
    }

    private boolean addItemStackToInventory(EntityPlayer player, ItemStack stack) {
        IItemHandler inventoryHandler = new InvWrapper(player.inventory);
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < inventoryHandler.getSlots(); slot++) {
            remaining = inventoryHandler.insertItem(slot, remaining, true);
            if (remaining.isEmpty()) {
                ItemStack toInsert = stack;
                for (int insertSlot = 0; insertSlot < inventoryHandler.getSlots() && !toInsert.isEmpty(); insertSlot++) {
                    toInsert = inventoryHandler.insertItem(insertSlot, toInsert, false);
                }
                player.inventory.markDirty();
                return toInsert.isEmpty();
            }
        }
        return false;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean usedEnergy() {
        return prevEnergy > getEnergy();
    }

    public ItemStack getContainerStack() {
        return containerSlot.getStack();
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return side == facing.getOpposite();
    }


    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @NotNull
    @Override
    public int[] getSlotsForFace(@NotNull EnumFacing side) {
        return new int[0];
    }

    @Override
    public void onPlace() {
        EnumFacing right = MekanismUtils.getRight(facing);
        MekanismUtils.makeBoundingBlock(world, getPos().up(), Coord4D.get(this));
        MekanismUtils.makeBoundingBlock(world, getPos().offset(right), Coord4D.get(this));
        MekanismUtils.makeBoundingBlock(world, getPos().offset(right).up(), Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        EnumFacing right = MekanismUtils.getRight(facing);
        world.setBlockToAir(getPos().offset(right).up());
        world.setBlockToAir(getPos().offset(right));
        world.setBlockToAir(getPos().up());
        world.setBlockToAir(getPos());
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }


}
