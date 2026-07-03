package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.base.IUpgradeableTile;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BinInventorySlot;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.BinTier;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.BinUpgradeData;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileEntityBin extends TileEntityContainerBlock implements IActiveState, IConfigurable, IUpgradeableTile, IComparatorSupport {

    public final int MAX_DELAY = 10;
    public boolean isActive;
    public boolean clientActive;
    public int addTicks = 0;

    public int delayTicks;

    public int cacheCount;

    public BinTier tier = BinTier.BASIC;

    public ItemStack itemType = ItemStack.EMPTY;

    public ItemStack topStack = ItemStack.EMPTY;
    public ItemStack bottomStack = ItemStack.EMPTY;

    public int prevCount;

    public int clientAmount;
    public ItemStack clientLockStack = ItemStack.EMPTY;

    private BinInventorySlot binSlot;

    public TileEntityBin() {
        super("Bin");
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        binSlot = builder.addSlot(BinInventorySlot.create(listener, () -> tier));
        return builder.build();
    }

    @Override
    public boolean canInstallUpgrade(BaseTier upgradeTier) {
        if (upgradeTier.ordinal() != tier.ordinal() + 1) {
            return false;
        }
        return upgradeTier.ordinal() < BinTier.values().length;
    }

    @Nullable
    @Override
    public IUpgradeData getUpgradeData(BaseTier upgradeTier) {
        if (!canInstallUpgrade(upgradeTier)) {
            return null;
        }
        return new BinUpgradeData(upgradeTier, facing, clientFacing, ticker, redstone, redstoneLastTick, doAutoSync, isActive, binSlot.serializeNBT());
    }

    @Override
    public boolean parseUpgradeData(IUpgradeData upgradeData) {
        if (upgradeData instanceof BinUpgradeData data && data.getUpgradeTier().ordinal() == tier.ordinal() + 1) {
            facing = data.facing;
            clientFacing = data.clientFacing;
            ticker = data.ticker;
            redstone = data.redstone;
            redstoneLastTick = data.redstoneLastTick;
            doAutoSync = data.doAutoSync;
            tier = BinTier.values()[data.getUpgradeTier().ordinal()];
            clientActive = isActive = data.active;
            binSlot.deserializeNBT(data.binSlot);
            sortStacks();
            clientLockStack = binSlot.getLockStack();
            clientAmount = getItemCount();
            prevCount = getItemCount();
            MekanismUtils.updateBlock(world, getPos());
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
            return true;
        }
        return false;
    }

    public void sortStacks() {
        if (binSlot == null) {
            return;
        }
        ItemStack stored = binSlot.getStack();
        if (stored.isEmpty() || binSlot.getCount() <= 0) {
            itemType = ItemStack.EMPTY;
            topStack = ItemStack.EMPTY;
            bottomStack = ItemStack.EMPTY;
            cacheCount = 0;
            return;
        }
        itemType = StackUtils.size(stored, 1);
        int count = binSlot.getCount();
        int remain = tier.getStorage() - count;
        if (remain >= itemType.getMaxStackSize()) {
            topStack = ItemStack.EMPTY;
        } else {
            topStack = StackUtils.size(itemType, itemType.getMaxStackSize() - remain);
        }
        count -= topStack.getCount();
        bottomStack = StackUtils.size(itemType, Math.min(itemType.getMaxStackSize(), count));
        count -= bottomStack.getCount();
        cacheCount = count;
    }

    public boolean isValid(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0 && binSlot.isItemValid(stack) &&
              binSlot.insertItem(stack, Action.SIMULATE, AutomationType.MANUAL).getCount() < stack.getCount();
    }

    public ItemStack add(ItemStack stack, boolean simulate) {
        ItemStack remainder = binSlot.insertItem(stack, Action.get(!simulate), AutomationType.MANUAL);
        if (!simulate && remainder.getCount() != stack.getCount()) {
            sortStacks();
            markDirty();
        }
        return remainder;
    }

    public ItemStack add(ItemStack stack) {
        return add(stack, false);
    }

    public ItemStack removeStack() {
        return remove(bottomStack.getCount());
    }

    public ItemStack remove(int amount, boolean simulate) {
        ItemStack ret = binSlot.extractItem(amount, Action.get(!simulate), AutomationType.MANUAL);
        if (!simulate && !ret.isEmpty()) {
            sortStacks();
            markDirty();
        }
        return ret;
    }

    public ItemStack remove(int amount) {
        return remove(amount, false);
    }

    public int getItemCount() {
        return binSlot.getCount();
    }

    public void setItemCount(int count) {
        if (count <= 0) {
            binSlot.setEmpty();
        } else if (!itemType.isEmpty()) {
            binSlot.setStackUnchecked(StackUtils.size(itemType, Math.min(count, tier.getStorage())));
        }
        sortStacks();
        markNoUpdateSync();
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        addTicks = Math.max(0, addTicks - 1);
        delayTicks = Math.max(0, delayTicks - 1);
        sortStacks();
        if (getItemCount() != prevCount) {
            markNoUpdateSync();
            MekanismUtils.saveChunk(this);
        }

        if (delayTicks == 0) {
            if (!bottomStack.isEmpty() && isActive) {
                TileEntity tile = Coord4D.get(this).offset(EnumFacing.DOWN).getTileEntity(world);
                ILogisticalTransporter transporter = CapabilityUtils.getCapability(tile, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, EnumFacing.UP);
                TransitResponse response;
                if (transporter == null) {
                    response = InventoryUtils.putStackInInventory(tile, TransitRequest.getFromStack(bottomStack), EnumFacing.DOWN, false);
                } else {
                    response = TransporterUtils.insert(this, transporter, TransitRequest.getFromStack(bottomStack), null, true, 0);
                }
                if (!response.isEmpty() && tier != BinTier.CREATIVE) {
                    binSlot.shrinkStack(response.getSendingAmount(), Action.EXECUTE);
                    sortStacks();
                }
                delayTicks = 10;
            }
        } else {
            delayTicks--;
        }
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("tier", tier.ordinal());
    }

    @Override
    protected void readCustomNBTBeforeInventory(NBTTagCompound nbtTags) {
        clientActive = isActive = nbtTags.getBoolean("isActive");
        tier = MekanismUtils.getByIndex(BinTier.values(), nbtTags.getInteger("tier"), tier);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        sortStacks();
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isActive);
        data.add(getItemCount());
        data.add(tier.ordinal());
        if (getItemCount() > 0) {
            data.add(itemType);
        }
        data.add(binSlot.getLockStack());
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientActive = isActive = dataStream.readBoolean();
            clientAmount = dataStream.readInt();
            tier = MekanismUtils.getByIndex(BinTier.values(), dataStream.readInt(), tier);
            if (clientAmount > 0) {
                itemType = PacketHandler.readStack(dataStream);
                binSlot.setStackUnchecked(StackUtils.size(itemType, Math.min(clientAmount, tier.getStorage())));
            } else {
                itemType = ItemStack.EMPTY;
                binSlot.setEmpty();
            }
            clientLockStack = PacketHandler.readStack(dataStream);
            binSlot.setLockStack(clientLockStack);
            sortStacks();
            MekanismUtils.updateBlock(world, getPos());
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (!isRemote()) {
            MekanismUtils.saveChunk(this);
            Mekanism.packetHandler.sendUpdatePacket(this);
            prevCount = getItemCount();
            sortStacks();
        }
    }

    public void setItemType(ItemStack stack) {
        if (stack.isEmpty()) {
            itemType = ItemStack.EMPTY;
            binSlot.setEmpty();
            sortStacks();
            return;
        }
        itemType = StackUtils.size(stack, 1);
        if (!binSlot.isEmpty()) {
            binSlot.setStackUnchecked(StackUtils.size(itemType, getItemCount()));
            sortStacks();
        }
    }

    public BinInventorySlot getBinSlot() {
        return binSlot;
    }

    public boolean toggleLock() {
        return setLocked(!binSlot.isLocked());
    }

    public boolean setLocked(boolean isLocked) {
        if (binSlot.setLocked(isLocked)) {
            clientLockStack = binSlot.getLockStack();
            if (!isRemote()) {
                Mekanism.packetHandler.sendUpdatePacket(this);
                markNoUpdateSync();
                world.playSound(null, getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.3F, 1);
            }
            return true;
        }
        return false;
    }

    public ItemStack getRenderStack() {
        return binSlot.getRenderStack();
    }

    public ItemStack getLockStack() {
        return binSlot.getLockStack();
    }

    public void onContentsChanged() {
        sortStacks();
        clientLockStack = binSlot.getLockStack();
        if (!isRemote()) {
            markDirty();
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize(getBlockType().getTranslationKey() + ".Bin" + tier.getBaseTier().getSimpleName() + ".name");
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Nonnull
    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }

    @Override
    public boolean isUsableByPlayer(@Nonnull EntityPlayer entityplayer) {
        return true;
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public void closeInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            clientActive = active;
        }
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(getItemCount(), getMaxStoredCount());
    }

    public int getMaxStoredCount() {
        return tier.getStorage();
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        setActive(!getActive());
        world.playSound(null, getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.3F, 1);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }
}
