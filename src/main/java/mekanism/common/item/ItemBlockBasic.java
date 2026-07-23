package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.MekanismBlocks;
import mekanism.common.base.ITierItem;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.inventory.BinMekanismInventory;
import mekanism.common.inventory.slot.BinInventorySlot;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.BinTier;
import mekanism.common.tier.InductionCellTier;
import mekanism.common.tier.InductionProviderTier;
import mekanism.common.tile.TileEntityBin;
import mekanism.common.tile.multiblock.TileEntityInductionCell;
import mekanism.common.tile.multiblock.TileEntityInductionProvider;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Item class for handling multiple metal block IDs. 0:0: Osmium Block 0:1: Bronze Block 0:2: Refined Obsidian 0:3: Charcoal Block 0:4: Refined Glowstone 0:5: Steel Block
 * 0:6: Bin 0:7: Teleporter Frame 0:8: Steel Casing 0:9: Dynamic Tank 0:10: Structural Glass 0:11: Dynamic Valve 0:12: Copper Block 0:13: Tin Block 0:14: Thermal
 * Evaporation Controller 0:15: Thermal Evaporation Valve 1:0: Thermal Evaporation Block 1:1: Induction Casing 1:2: Induction Port 1:3: Induction Cell 1:4: Induction
 * Provider 1:5: Superheating Element 1:6: Pressure Disperser 1:7: Boiler Casing 1:8: Boiler Valve 1:9: Security Desk
 *
 * @author AidanBrady
 */
public class ItemBlockBasic extends ItemBlock implements ITierItem, IItemSustainedInventory, IItemBlockPlacementData {

    public Block metaBlock;

    public ItemBlockBasic(Block block) {
        super(block);
        metaBlock = block;
        setHasSubtypes(true);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        if (BasicBlockType.get(stack) == BasicBlockType.BIN) {
            return 1; // Temporary no stacking due to #
        }
        return super.getItemStackLimit(stack);
    }

    public ItemStack getUnchargedCell(InductionCellTier tier) {
        ItemStack stack = new ItemStack(MekanismBlocks.BasicBlock2, 1, 3);
        setBaseTier(stack, tier.getBaseTier());
        return stack;
    }

    public ItemStack getUnchargedProvider(InductionProviderTier tier) {
        ItemStack stack = new ItemStack(MekanismBlocks.BasicBlock2, 1, 4);
        setBaseTier(stack, tier.getBaseTier());
        return stack;
    }

    @Override
    public BaseTier getBaseTier(ItemStack itemstack) {
        if (itemstack.getTagCompound() == null) {
            return BaseTier.BASIC;
        }
        int tier = itemstack.getTagCompound().getInteger("tier");
        if (tier >= 0 && tier < BaseTier.values().length) {
            return BaseTier.values()[tier];
        }
        return BaseTier.BASIC;
    }

    @Override
    public void setBaseTier(ItemStack itemstack, BaseTier tier) {
        if (itemstack.getTagCompound() == null) {
            itemstack.setTagCompound(new NBTTagCompound());
        }
        itemstack.getTagCompound().setInteger("tier", tier.ordinal());
    }

    @Override
    public int getMetadata(int i) {
        return i;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        BasicBlockType type = BasicBlockType.get(itemstack);
        if (type != null && type.hasDescription) {
            if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
                if (type == BasicBlockType.BIN) {
                    BinMekanismInventory inventory = BinMekanismInventory.create(itemstack);
                    BinInventorySlot slot = inventory == null ? null : inventory.getBinSlot();
                    if (slot != null && !slot.isEmpty()) {
                        list.add(EnumColor.BRIGHT_GREEN + slot.getStack().getDisplayName());
                        String amountStr = slot.getCount() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : "" + slot.getCount();
                        list.add(EnumColor.PURPLE + LangUtils.localize("tooltip.itemAmount") + ": " + EnumColor.GREY + amountStr);
                    } else {
                        list.add(EnumColor.DARK_RED + LangUtils.localize("gui.empty"));
                    }
                    if (slot != null && slot.isLocked()) {
                        list.add(EnumColor.PINK + LangUtils.localize("tooltip.locked") + ": " + EnumColor.GREY + slot.getLockStack().getDisplayName());
                    }
                    int cap = BinTier.values()[getBaseTier(itemstack).ordinal()].getStorage();
                    list.add(EnumColor.INDIGO + LangUtils.localize("tooltip.capacity") + ": " + EnumColor.GREY +
                            (cap == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : cap) + " " + LangUtils.localize("transmission.Items"));
                } else if (type == BasicBlockType.INDUCTION_CELL) {
                    InductionCellTier tier = InductionCellTier.values()[getBaseTier(itemstack).ordinal()];
                    list.add(tier.getBaseTier().getColor() + LangUtils.localize("tooltip.capacity") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(tier.getMaxEnergy()));
                } else if (type == BasicBlockType.INDUCTION_PROVIDER) {
                    InductionProviderTier tier = InductionProviderTier.values()[getBaseTier(itemstack).ordinal()];
                    list.add(tier.getBaseTier().getColor() + LangUtils.localize("tooltip.outputRate") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(tier.getOutput()));
                }

                if (getEnergyCapacity(itemstack) > 0) {
                    list.add(EnumColor.BRIGHT_GREEN + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack)));
                }
                list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                        EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
            } else {
                list.addAll(MekanismUtils.splitTooltip(type.getDescription(), itemstack));
            }
        }
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return BasicBlockType.get(stack) == BasicBlockType.BIN && ItemDataUtils.hasData(stack, "newCount");
    }

    @Nonnull
    @Override
    public ItemStack getContainerItem(@Nonnull ItemStack stack) {
        if (BasicBlockType.get(stack) == BasicBlockType.BIN) {
            if (!ItemDataUtils.hasData(stack, "newCount")) {
                return ItemStack.EMPTY;
            }
            int newCount = ItemDataUtils.getInt(stack, "newCount");
            ItemDataUtils.removeData(stack, "newCount");
            ItemStack ret = stack.copy();
            BinMekanismInventory inventory = BinMekanismInventory.create(ret);
            if (inventory != null) {
                BinInventorySlot slot = inventory.getBinSlot();
                if (newCount <= 0 || slot.isEmpty()) {
                    slot.setEmpty();
                } else {
                    slot.setStackUnchecked(StackUtils.size(slot.getStack(), Math.min(newCount, slot.getLimit(slot.getStack()))));
                }
                inventory.onContentsChanged();
            }
            return ret;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY,
                                float hitZ, @Nonnull IBlockState state) {
        boolean place = true;

        BasicBlockType type = BasicBlockType.get(stack);
        if (type == BasicBlockType.SECURITY_DESK) {
            if (world.isOutsideBuildHeight(pos.up()) || !world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up())) {
                place = false;
            }
        }

        return place && super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state);
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull TileEntity tileEntity) {
        BasicBlockType type = BasicBlockType.get(stack);
        if (type == BasicBlockType.BIN && stack.hasTagCompound() && tileEntity instanceof TileEntityBin bin) {
            BinMekanismInventory inventory = BinMekanismInventory.create(stack);
            bin.tier = BinTier.values()[getBaseTier(stack).ordinal()];
            if (inventory != null) {
                BinInventorySlot slot = inventory.getBinSlot();
                if (!slot.isEmpty()) {
                    bin.setItemType(slot.getStack());
                }
                bin.setItemCount(slot.getCount());
                bin.getBinSlot().setLockStack(slot.getLockStack());
            }
        } else if (type == BasicBlockType.INDUCTION_CELL && tileEntity instanceof TileEntityInductionCell cell) {
            cell.tier = InductionCellTier.values()[getBaseTier(stack).ordinal()];
        } else if (type == BasicBlockType.INDUCTION_PROVIDER && tileEntity instanceof TileEntityInductionProvider provider) {
            provider.tier = InductionProviderTier.values()[getBaseTier(stack).ordinal()];
        }
        if (tileEntity instanceof IStrictEnergyStorage storage && !(tileEntity instanceof TileEntityMultiblock<?>)) {
            storage.setEnergy(StorageUtils.getStoredEnergyForDisplay(stack));
        }
    }

    @Nonnull
    @Override
    public String getTranslationKey(ItemStack itemstack) {
        BasicBlockType type = BasicBlockType.get(itemstack);
        if (type != null) {
            String name = getTranslationKey() + "." + type.name;
            if (type == BasicBlockType.BIN || type == BasicBlockType.INDUCTION_CELL || type == BasicBlockType.INDUCTION_PROVIDER) {
                name += getBaseTier(itemstack).getSimpleName();
            }
            return name;
        }
        return "Invalid Basic Block";
    }

    public void setStoredEnergy(ItemStack itemStack, double amount) {
        if (BasicBlockType.get(itemStack) == BasicBlockType.INDUCTION_CELL) {
            StorageUtils.setStoredEnergy(itemStack, amount, getEnergyCapacity(itemStack));
        }
    }

    public double getEnergyCapacity(ItemStack itemStack) {
        if (BasicBlockType.get(itemStack) == BasicBlockType.INDUCTION_CELL) {
            return InductionCellTier.values()[getBaseTier(itemStack).ordinal()].getMaxEnergy();
        }
        return 0;
    }

    @Override
    public int getItemBurnTime(ItemStack itemStack) {
        // If this is a block of charcoal, set burn time to 16000 ticks (per Minecraft standard)
        if (this.metaBlock == MekanismBlocks.BasicBlock && itemStack.getMetadata() == 3) {
            return 16000; // ticks
        }
        return super.getItemBurnTime(itemStack);
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        BasicBlockType type = BasicBlockType.get(itemstack);
        if (type == BasicBlockType.BIN) {
            BinTier tier = BinTier.values()[getBaseTier(itemstack).ordinal()];
            return tier.getBaseTier().getColor() + LangUtils.localize("tile.BasicBlock.Bin" + tier.getBaseTier().getSimpleName() + ".name");
        }
        return super.getItemStackDisplayName(itemstack);
    }

    @Override
    public void setInventory(NBTTagList nbtTags, Object... data) {
        if (data.length > 0 && data[0] instanceof ItemStack stack) {
            setSustainedInventory(nbtTags, stack);
        }
    }

    @Override
    public void setSustainedInventory(NBTTagList nbtTags, ItemStack stack) {
        if (BasicBlockType.get(stack) == BasicBlockType.BIN) {
            if (nbtTags == null || nbtTags.tagCount() == 0) {
                ItemDataUtils.removeData(stack, NBTConstants.ITEMS);
            } else {
                ItemDataUtils.setList(stack, NBTConstants.ITEMS, nbtTags);
            }
        }
    }

    @Override
    public NBTTagList getInventory(Object... data) {
        if (data.length > 0 && data[0] instanceof ItemStack stack) {
            return getSustainedInventory(stack);
        }
        return new NBTTagList();
    }

    @Override
    public NBTTagList getSustainedInventory(ItemStack stack) {
        if (BasicBlockType.get(stack) == BasicBlockType.BIN) {
            return ItemDataUtils.getList(stack, NBTConstants.ITEMS);
        }
        return new NBTTagList();
    }
}
