package mekanism.generators.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.functions.ConstantPredicates;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismClient;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.base.ISustainedTank;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.*;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Item class for handling multiple generator block IDs. 0: Heat Generator 1: Solar Generator 3: Hydrogen Generator 4: Bio-Generator 5: Advanced Solar Generator 6: Wind
 * Generator 7: Turbine Rotor 8: Rotational Complex 9: Electromagnetic Coil 10: Turbine Casing 11: Turbine Valve 12: Turbine Vent 13: Saturating Condenser
 *
 * @author AidanBrady
 */
@InterfaceList({
        @Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID)
})
public class ItemBlockGenerator extends ItemBlock implements ILegacyEnergizedItem, ISpecialElectricItem, IItemSustainedInventory, ISustainedTank, IEnergyContainerItem,
      ISecurityItem, IItemBlockPlacementData {

    public Block metaBlock;

    public ItemBlockGenerator(Block block) {
        super(block);
        metaBlock = block;
        setHasSubtypes(true);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        GeneratorType type = GeneratorType.get(stack);
        if (type != null && type.maxEnergy == -1) {
            return 64;
        }
        return 1;
    }

    @Override
    public int getMetadata(int i) {
        return i;
    }

    @Nonnull
    @Override
    public String getTranslationKey(ItemStack itemstack) {
        GeneratorType generatorType = GeneratorType.get(itemstack);
        if (generatorType == null) {
            return "KillMe!";
        }
        return getTranslationKey() + "." + generatorType.getBlockName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        GeneratorType type = GeneratorType.get(itemstack);
        if (type == null) {
            return;
        }
        if (type.maxEnergy > -1) {
            if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
                list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                        EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
                list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.AQUA + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                        EnumColor.GREY + " " + LangUtils.localize("tooltip.and") + " " + EnumColor.AQUA +
                        GameSettings.getKeyDisplayString(MekanismKeyHandler.handModeSwitchKey.getKeyCode()) + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDesc") + ".");
            } else if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.handModeSwitchKey)) {
                if (hasSecurity(itemstack)) {
                    list.add(SecurityUtils.getOwnerDisplay(Minecraft.getMinecraft().player, MekanismClient.clientUUIDMap.get(getOwnerUUID(itemstack))));
                    list.add(EnumColor.GREY + LangUtils.localize("gui.security") + ": " + SecurityUtils.getSecurityDisplay(itemstack, Side.CLIENT));
                    if (SecurityUtils.isOverridden(itemstack, Side.CLIENT)) {
                        list.add(EnumColor.RED + "(" + LangUtils.localize("gui.overridden") + ")");
                    }
                }

                list.add(EnumColor.BRIGHT_GREEN + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY
                        + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack), getEnergyCapacity(itemstack)));

                if (hasTank(itemstack)) {
                    if (getFluidStack(itemstack) != null) {
                        list.add(EnumColor.PINK + FluidRegistry.getFluidName(getFluidStack(itemstack)) + ": " + EnumColor.GREY + getFluidStack(itemstack).amount + "mB");
                    }
                }

                list.add(EnumColor.AQUA + LangUtils.localize("tooltip.inventory") + ": " + EnumColor.GREY +
                        LangUtils.transYesNo(getInventory(itemstack) != null && getInventory(itemstack).tagCount() != 0));
            } else {
                list.addAll(MekanismUtils.splitTooltip(type.getDescription(), itemstack));
            }
        } else {
            if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
                list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                        EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
            } else {
                list.addAll(MekanismUtils.splitTooltip(type.getDescription(), itemstack));
            }
        }
    }

    @Override
    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY,
                                float hitZ, @Nonnull IBlockState state) {
        if (MekanismConfig.current().general.destroyDisabledBlocks.val()){
            GeneratorType type = GeneratorType.get(stack);
            if (type != null && !type.isEnabled()) {
                return false;
            }
        }
        GeneratorType type = GeneratorType.get(stack);
        if (type == null) {
            return false;
        }
        boolean place = true;
        Block block = world.getBlockState(pos).getBlock();
        if (type == GeneratorType.ADVANCED_SOLAR_GENERATOR) {
            if (!(block.isReplaceable(world, pos) && world.isAirBlock(pos.add(0, 1, 0)))) {
                return false;
            }
            outer:
            for (int xPos = -1; xPos <= 1; xPos++) {
                for (int zPos = -1; zPos <= 1; zPos++) {
                    if (!world.isAirBlock(pos.add(xPos, 2, zPos)) || pos.getY() + 2 > 255) {
                        place = false;
                        break outer;
                    }
                }
            }
        } else if (type == GeneratorType.WIND_GENERATOR) {
            if (!block.isReplaceable(world, pos)) {
                return false;
            }
            for (int yPos = 1; yPos <= 4; yPos++) {
                if (!world.isAirBlock(pos.add(0, yPos, 0)) || pos.getY() + yPos > 255) {
                    place = false;
                    break;
                }
            }
        }

        return place && super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state);
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull TileEntity tileEntity) {
        MekanismPlacementData.restoreCommon(stack, placer, tileEntity);
    }

    @Override
    public void setInventory(NBTTagList nbtTags, Object... data) {
        if (data[0] instanceof ItemStack stack) {
            ItemDataUtils.setList(stack, "Items", nbtTags);
        }
    }

    @Override
    public NBTTagList getInventory(Object... data) {
        if (data[0] instanceof ItemStack stack) {
            return ItemDataUtils.getList(stack, "Items");
        }
        return null;
    }

    @Override
    public void setFluidStack(FluidStack fluidStack, Object... data) {
        if (data[0] instanceof ItemStack itemStack) {
            ItemDataUtils.setStoredFluid(itemStack, "fluidTank", fluidStack, Integer.MAX_VALUE);
        }
    }

    @Override
    public FluidStack getFluidStack(Object... data) {
        if (data[0] instanceof ItemStack itemStack) {
            return ItemDataUtils.getStoredFluid(itemStack, "fluidTank");
        }
        return null;
    }

    @Override
    public boolean hasTank(Object... data) {
        if (!(data[0] instanceof ItemStack stack) || !(stack.getItem() instanceof ISustainedTank)) {
            return false;
        }
        return GeneratorType.get(stack) == GeneratorType.BIO_GENERATOR;
    }

    public void setStoredEnergy(ItemStack itemStack, double amount) {
        if (itemStack.getCount() > 1) {
            return;
        }
        StorageUtils.setStoredEnergy(itemStack, amount, getEnergyCapacity(itemStack));
    }

    public double getEnergyCapacity(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        GeneratorType generatorType = GeneratorType.get(itemStack);
        return hasEnergyStorage(generatorType) ? generatorType.maxEnergy : 0;
    }

    public double getEnergyTransfer(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        return getEnergyCapacity(itemStack) * 0.005;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        return false;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        return itemStack.getCount() <= 1 && hasEnergyStorage(GeneratorType.get(itemStack));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canReceiveEnergy(theItem)) {
            double amount = RFIntegration.fromRF(energy);
            double remainder = StorageUtils.insertEnergy(theItem, amount, Action.get(!simulate));
            return RFIntegration.toRF(amount - remainder);
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canSendEnergy(theItem)) {
            return RFIntegration.toRF(StorageUtils.extractEnergy(theItem, RFIntegration.fromRF(energy), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(ItemStack theItem) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(ItemStack theItem) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
    }

    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, "ownerUUID")) {
            return UUID.fromString(ItemDataUtils.getString(stack, "ownerUUID"));
        }
        return null;
    }

    @Override
    public void setOwnerUUID(ItemStack stack, UUID owner) {
        if (owner == null) {
            ItemDataUtils.removeData(stack, "ownerUUID");
            return;
        }
        ItemDataUtils.setString(stack, "ownerUUID", owner.toString());
    }

    @Override
    public SecurityMode getSecurity(ItemStack stack) {
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return SecurityMode.PUBLIC;
        }
        return MekanismUtils.getByIndex(SecurityMode.values(), ItemDataUtils.getInt(stack, "security"), SecurityMode.PUBLIC);
    }

    @Override
    public void setSecurity(ItemStack stack, SecurityMode mode) {
        if (getOwnerUUID(stack) == null){
            ItemDataUtils.removeData(stack,"security");
        }else {
            ItemDataUtils.setInt(stack, "security", mode.ordinal());
        }
    }

    @Override
    public boolean hasSecurity(ItemStack stack) {
        GeneratorType type = GeneratorType.get(stack);
        return type != null && type.hasModel;
    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return hasSecurity(stack);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        if (!hasEnergyStorage(GeneratorType.get(stack))) {
            return null;
        }
        return new ItemCapabilityWrapper(stack, new TeslaItemWrapper(), new ForgeEnergyItemWrapper(),
              RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack),
                    ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysFalse()));
    }

    private boolean hasEnergyStorage(GeneratorType generatorType) {
        return generatorType != null && generatorType.maxEnergy >= 0;
    }
}
