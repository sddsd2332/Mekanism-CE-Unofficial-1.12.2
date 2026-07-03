package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.capabilities.fluid.FluidTankFluidTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.FluidTankUpgradeData;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;

public class TileEntityFluidTank extends TileEntityContainerBlock implements IActiveState, IConfigurable, ISustainedTank, IFluidContainerManager,
        ITankManager, ISecurityTile, IUpgradeableTile, ITieredTile, IComparatorSupport {

    public boolean isActive;

    public boolean clientActive;

    public FluidTankFluidTank fluidTank;

    public ContainerEditMode editMode = ContainerEditMode.BOTH;

    public FluidTankTier tier = FluidTankTier.BASIC;

    public int updateDelay;

    public int prevAmount;

    public int valve;
    public FluidStack valveFluid;

    public float prevScale;

    public boolean needsPacket;

    public int currentRedstoneLevel;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private FluidInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;

    public TileEntityFluidTank() {
        super("FluidTank");
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = FluidTankHelper.forSide(() -> facing);
        fluidTank = builder.addTank(FluidTankFluidTank.create(this, listener));
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(FluidInventorySlot.input(fluidTank, listener, 146, 19));
        inputSlot.setSlotOverlay(SlotOverlay.INPUT);
        outputSlot = builder.addSlot(OutputInventorySlot.at(listener, 146, 51));
        outputSlot.setSlotOverlay(SlotOverlay.OUTPUT);
        return builder.build();
    }

    @Override
    public boolean canInstallUpgrade(BaseTier upgradeTier) {
        if (upgradeTier.ordinal() != tier.ordinal() + 1) {
            return false;
        }
        return upgradeTier.ordinal() < FluidTankTier.values().length;
    }

    @Nullable
    @Override
    public IUpgradeData getUpgradeData(BaseTier upgradeTier) {
        if (!canInstallUpgrade(upgradeTier)) {
            return null;
        }
        return new FluidTankUpgradeData(upgradeTier, facing, clientFacing, ticker, redstone, redstoneLastTick, doAutoSync, isActive,
              clientActive, editMode, valve, valveFluid, fluidTank.getFluid(), currentRedstoneLevel, writeUpgradeComponentData(),
              inputSlot.serializeNBT(), outputSlot.serializeNBT());
    }

    @Nonnull
    private NBTTagCompound writeUpgradeComponentData() {
        NBTTagCompound componentData = new NBTTagCompound();
        securityComponent.write(componentData);
        return componentData;
    }

    @Override
    public boolean parseUpgradeData(IUpgradeData upgradeData) {
        if (upgradeData instanceof FluidTankUpgradeData data && data.getUpgradeTier().ordinal() == tier.ordinal() + 1) {
            facing = data.facing;
            clientFacing = data.clientFacing;
            ticker = data.ticker;
            redstone = data.redstone;
            redstoneLastTick = data.redstoneLastTick;
            doAutoSync = data.doAutoSync;
            tier = FluidTankTier.values()[data.getUpgradeTier().ordinal()];
            isActive = data.active;
            clientActive = data.clientActive;
            editMode = data.editMode;
            valve = data.valve;
            valveFluid = data.valveFluid == null ? null : data.valveFluid.copy();
            currentRedstoneLevel = data.currentRedstoneLevel;
            securityComponent.read(data.componentData);
            inputSlot.deserializeNBT(data.inputSlot);
            outputSlot.deserializeNBT(data.outputSlot);
            fluidTank.setFluid(data.stored);
            sanitizeAndClampTank();
            prevAmount = fluidTank.getFluidAmount();
            prevScale = fluidTank.getCapacity() == 0 ? 0 : (float) fluidTank.getFluidAmount() / fluidTank.getCapacity();
            MekanismUtils.updateBlock(world, getPos());
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
            return true;
        }
        return false;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return false;
    }


    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
        float targetScale = (float) (fluidTank.getFluid() != null ? fluidTank.getFluid().amount : 0) / fluidTank.getCapacity();
        if (Math.abs(prevScale - targetScale) > 0.01) {
            prevScale = (9 * prevScale + targetScale) / 10;
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (fluidTank.getFluid() != null && fluidTank.getFluidAmount() == 0) {
            fluidTank.setEmpty();
        }
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                needsPacket = true;
            }
        }

        if (valve > 0) {
            valve--;
            if (valve == 0) {
                valveFluid = null;
                needsPacket = true;
            }
        }

        if (fluidTank.getFluidAmount() != prevAmount) {
            MekanismUtils.saveChunk(this);
            needsPacket = true;
        }

        prevAmount = fluidTank.getFluidAmount();
        if (!inputSlot.isEmpty()) {
            manageInventory();
        }
        if (isActive) {
            activeEmit();
        }

        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            markNoUpdateSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        needsPacket = false;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.FluidTank" + tier.getBaseTier().getSimpleName() + ".name");
    }

    private void activeEmit() {
        if (fluidTank.getFluid() != null) {
            FluidUtils.emit(Collections.singleton(EnumFacing.DOWN), fluidTank, this, Math.min(tier.getOutput(), fluidTank.getFluidAmount()));
        }
    }

    private void manageInventory() {
        inputSlot.handleTank(outputSlot, editMode);
        if (tier == FluidTankTier.CREATIVE && fluidTank.getFluid() != null) {
            fluidTank.fillToCapacity();
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("editMode", editMode.ordinal());
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        tier = MekanismUtils.getByIndex(FluidTankTier.values(), nbtTags.getInteger("tier"), tier);
        clientActive = isActive = nbtTags.getBoolean("isActive");
        editMode = ContainerEditMode.byIndexStatic(nbtTags.getInteger("editMode"));
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        sanitizeAndClampTank();
    }

    private void sanitizeAndClampTank() {
        FluidStack stored = fluidTank.getFluid();
        if (stored == null || stored.getFluid() == null || stored.amount <= 0) {
            fluidTank.setEmpty();
        } else {
            fluidTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            FluidTankTier prevTier = tier;
            tier = MekanismUtils.getByIndex(FluidTankTier.values(), dataStream.readInt(), tier);

            clientActive = dataStream.readBoolean();
            valve = dataStream.readInt();
            editMode = ContainerEditMode.byIndexStatic(dataStream.readInt());
            if (valve > 0) {
                valveFluid = TileUtils.readFluidStack(dataStream);
            } else {
                valveFluid = null;
            }

            TileUtils.readTankData(dataStream, fluidTank);
            if (prevTier != tier || (updateDelay == 0 && clientActive != isActive)) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }

    public int getCurrentNeeded() {
        int needed = fluidTank.getCapacity() - fluidTank.getFluidAmount();
        if (tier == FluidTankTier.CREATIVE) {
            return Integer.MAX_VALUE;
        }
        Coord4D top = Coord4D.get(this).offset(EnumFacing.UP);
        TileEntity topTile = top.getTileEntity(world);
        if (topTile instanceof TileEntityFluidTank topTank) {
            if (fluidTank.getFluid() != null && topTank.fluidTank.getFluid() != null) {
                if (fluidTank.getFluid().getFluid() != topTank.fluidTank.getFluid().getFluid()) {
                    return needed;
                }
            }
            needed += topTank.getCurrentNeeded();
        }
        return needed;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(tier.ordinal());
        data.add(isActive);
        data.add(valve);
        data.add(editMode.ordinal());
        if (valve > 0) {
            TileUtils.addFluidStack(data, valveFluid);
        }
        TileUtils.addTankData(data, fluidTank);
        return data;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active && updateDelay == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            updateDelay = 10;
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
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            setActive(!getActive());
            world.playSound(null, getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.3F, 1);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    @Nullable
    public FluidStack insertFluid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return insertExcess(stack, side, action, super.insertFluid(tank, stack, side, action));
    }

    @Override
    @Nullable
    public FluidStack insertFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return insertExcess(stack, side, action, super.insertFluid(stack, side, action));
    }

    @Nullable
    private FluidStack insertExcess(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action, @Nullable FluidStack remainder) {
        int amount = stack == null ? 0 : stack.amount;
        if (side == EnumFacing.UP && action.execute() && amount > (remainder == null ? 0 : remainder.amount)) {
            if (valve == 0) {
                needsPacket = true;
            }
            valve = 20;
            valveFluid = new FluidStack(stack, 1);
        }
        return remainder;
    }

    @Override
    public void setFluidStack(FluidStack fluidStack, Object... data) {
        fluidTank.setFluid(fluidStack);
        sanitizeAndClampTank();
    }

    @Override
    public FluidStack getFluidStack(Object... data) {
        return fluidTank.getFluid();
    }

    @Override
    public boolean hasTank(Object... data) {
        return true;
    }

    @Override
    public ContainerEditMode getContainerEditMode() {
        return editMode;
    }

    @Override
    public void setContainerEditMode(ContainerEditMode mode) {
        if (editMode != mode) {
            editMode = mode;
            MekanismUtils.saveChunk(this);
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank};
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public BaseTier getTier() {
        return tier.getBaseTier();
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }
}
