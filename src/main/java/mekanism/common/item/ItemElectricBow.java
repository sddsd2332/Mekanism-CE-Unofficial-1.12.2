package mekanism.common.item;

import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.StorageUtils;
import mekanism.common.util.TextComponentGroup;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.stats.StatList;
import net.minecraft.util.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemElectricBow extends ItemEnergized implements IModeItem, IItemHUDProvider {

    public ItemElectricBow() {
        super(120000);
        setMaxStackSize(1);
        setRarity(EnumRarity.RARE);
        setFull3D();
        this.addPropertyOverride(new ResourceLocation("pull"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                if (entityIn == null) {
                    return 0.0F;
                }else {
                    return !(entityIn.getActiveItemStack().getItem() instanceof ItemElectricBow) ? 0.0F : (float) (stack.getMaxItemUseDuration() - entityIn.getItemInUseCount()) / 20.0F;
                }
            }
        });
        this.addPropertyOverride(new ResourceLocation("pulling"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                return entityIn != null && entityIn.isHandActive() && entityIn.getActiveItemStack() == stack ? 1.0F : 0.0F;
            }
        });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        super.addInformation(itemstack, world, list, flag);
        list.add(EnumColor.PINK + LangUtils.localizeWithFormat("mekanism.tooltip.fireMode", LangUtils.transOnOff(getFireState(itemstack))));
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack itemstack, World world, EntityLivingBase entityLiving, int itemUseCount) {
        if (entityLiving instanceof EntityPlayer player && StorageUtils.getStoredEnergy(itemstack) > 0) {
            boolean flag = player.capabilities.isCreativeMode || EnchantmentHelper.getEnchantmentLevel(Enchantments.INFINITY, itemstack) > 0;
            ItemStack ammo = findAmmo(player);

            int maxItemUse = getMaxItemUseDuration(itemstack) - itemUseCount;
            maxItemUse = ForgeEventFactory.onArrowLoose(itemstack, world, player, maxItemUse, !itemstack.isEmpty() || flag);
            if (maxItemUse < 0) {
                return;
            }

            if (flag || !ammo.isEmpty()) {
                if (ammo.isEmpty()) {
                    ammo = new ItemStack(Items.ARROW);
                }
                float f = maxItemUse / 20F;
                f = (f * f + f * 2.0F) / 3F;
                if (f < 0.1D) {
                    return;
                }
                if (f > 1.0F) {
                    f = 1.0F;
                }
                boolean noConsume = flag && itemstack.getItem() instanceof ItemArrow;
                if (!world.isRemote) {
                    ItemArrow itemarrow = (ItemArrow) (ammo.getItem() instanceof ItemArrow ? ammo.getItem() : Items.ARROW);
                    EntityArrow entityarrow = itemarrow.createArrow(world, itemstack, player);
                    entityarrow.shoot(player, player.rotationPitch, player.rotationYaw, 0.0F, f * 3.0F, 1.0F);
                    if (f == 1.0F) {
                    entityarrow.setIsCritical(true);
                    }
                    if (!player.capabilities.isCreativeMode) {
                        StorageUtils.extractFromContainer(itemstack, getFireState(itemstack) ? 1200 : 120, Action.EXECUTE);
                    }
                    if (noConsume) {
                        entityarrow.pickupStatus = EntityArrow.PickupStatus.CREATIVE_ONLY;
                    }
                    entityarrow.setFire(getFireState(itemstack) ? 100 : 0);
                    world.spawnEntity(entityarrow);
                }

                world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.NEUTRAL,
                        1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);

                if (!noConsume) {
                    ammo.shrink(1);
                    if (ammo.getCount() == 0) {
                        player.inventory.deleteStack(ammo);
                    }
                }
                player.addStat(StatList.getObjectUseStats(this));
            }
        }
    }

    @Override
    public int getMaxItemUseDuration(ItemStack itemstack) {
        return 72000;
    }

    @Nonnull
    @Override
    public EnumAction getItemUseAction(ItemStack itemstack) {
        return EnumAction.BOW;
    }

    private ItemStack findAmmo(EntityPlayer player) {
        if (isArrow(player.getHeldItem(EnumHand.OFF_HAND))) {
            return player.getHeldItem(EnumHand.OFF_HAND);
        } else if (isArrow(player.getHeldItem(EnumHand.MAIN_HAND))) {
            return player.getHeldItem(EnumHand.MAIN_HAND);
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
            ItemStack itemstack = player.inventory.getStackInSlot(i);
            if (isArrow(itemstack)) {
                return itemstack;
            }
        }
        return ItemStack.EMPTY;
    }

    protected boolean isArrow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemArrow;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, @Nonnull EnumHand hand) {
        ItemStack itemStackIn = playerIn.getHeldItem(hand);
        boolean flag = !findAmmo(playerIn).isEmpty();
        ActionResult<ItemStack> ret = ForgeEventFactory.onArrowNock(itemStackIn, worldIn, playerIn, hand, flag);
        if (ret != null) {
            return ret;
        }
        if (!playerIn.capabilities.isCreativeMode && !flag) {
            return new ActionResult<>(EnumActionResult.FAIL, itemStackIn);
        }
        playerIn.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, itemStackIn);
    }

    public void setFireState(ItemStack stack, boolean state) {
        ItemDataUtils.setBoolean(stack, NBTConstants.MODE, state);
    }

    public boolean getFireState(ItemStack stack) {
        return ItemDataUtils.getBoolean(stack, NBTConstants.MODE);
    }

    @Override
    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }


    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        list.add(EnumColor.PINK + LangUtils.localizeWithFormat("mekanism.tooltip.fireMode", LangUtils.transOnOff(getFireState(stack))));
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        if (Math.abs(shift) % 2 == 1) {
            boolean newState = !getFireState(stack);
            setFireState(stack, newState);
            displayChange.sendMessage(player,()->new TextComponentGroup().translation("mekanism.tooltip.fireMode", LangUtils.onOffColoured(newState)));
        }
    }


    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        return new TextComponentGroup(TextFormatting.GRAY).translation("mekanism.tooltip.fireMode", LangUtils.onOffColoured(getFireState(stack)));
    }
}
