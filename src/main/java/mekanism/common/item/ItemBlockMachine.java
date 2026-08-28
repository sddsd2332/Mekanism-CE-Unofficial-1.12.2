package mekanism.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.functions.ConstantPredicates;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismClient;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.capabilities.fluid.item.RateLimitFluidHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Item class for handling multiple machine block IDs.
 * 0:0: Enrichment Chamber
 * 0:1: Osmium Compressor
 * 0:2: Combiner
 * 0:3: Crusher
 * 0:4: Digital Miner
 * 0:5: Basic Factory
 * 0:6: Advanced Factory
 * 0:7: Elite Factory
 * 0:8: Metallurgic Infuser
 * 0:9: Purification Chamber
 * 0:10: Energized Smelter
 * 0:11: Teleporter
 * 0:12: Electric Pump
 * 0:13: Personal Chest
 * 0:14: Chargepad
 * 0:15: Logistical Sorter
 * <p>
 * 1:0: Rotary Condensentrator
 * 1:1: Chemical Oxidizer
 * 1:2: Chemical Infuser
 * 1:3: Chemical Injection Chamber
 * 1:4: Electrolytic Separator
 * 1:5: Precision Sawmill
 * 1:6: Chemical Dissolution Chamber
 * 1:7: Chemical Washer
 * 1:8: Chemical Crystallizer
 * 1:9: Seismic Vibrator
 * 1:10: Pressurized Reaction Chamber
 * 1:11: Portable Tank
 * 1:12: Fluidic Plenisher
 * 1:13: Laser
 * 1:14: Laser Amplifier
 * 1:15: Laser Tractor Beam
 * <p>
 * 2:0: Entangled Block
 * 2:1: Solar Neutron Activator
 * 2:2: Ambient Accumulator
 * 2:3: Oredictionificator
 * 2:4: Resistive Heater
 * 2:5: Formulaic Assemblicator
 * 2:6: Fuelwood Heater
 *
 * @author AidanBrady
 */
@InterfaceList({
        @Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID)
})
public class ItemBlockMachine extends ItemBlock implements ILegacyEnergizedItem, ISpecialElectricItem, IFactory, IItemSustainedInventory, ISustainedTank, IEnergyContainerItem,
        ITierItem, ISecurityItem, IModeItem, IItemBlockPlacementData {

    public Block metaBlock;

    public ItemBlockMachine(Block block) {
        super(block);
        metaBlock = block;
        setHasSubtypes(true);
        setNoRepair();
        //setMaxStackSize(1);
    }

    @Override
    public int getMetadata(int i) {
        return i;
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        if (MachineType.get(stack) == MachineType.PERSONAL_CHEST && getOwnerUUID(stack) != null) {
            return 1;
        }
        return super.getItemStackLimit(stack);
    }

    @Nonnull
    @Override
    public String getTranslationKey(ItemStack itemstack) {
        if (MachineType.get(itemstack) != null) {
            MachineType type = MachineType.get(itemstack);
            if (type.isFactory()) {
                BaseTier tier = type.factoryTier.getBaseTier();
                RecipeType recipeType = getRecipeTypeOrNull(itemstack);
                if (recipeType != null) {
                    return "tile." + recipeType.getTranslationKey() + "." + tier.getSimpleName() + "." + MachineType.get(itemstack).blockName;
                }
            }
            return getTranslationKey() + "." + MachineType.get(itemstack).blockName;
        }
        return "null";
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        MachineType type = MachineType.get(itemstack);
        if (type == null) {
            return super.getItemStackDisplayName(itemstack);
        }
        if (type.isFactory()) {
            BaseTier tier = type.factoryTier.getBaseTier();
            RecipeType recipeType = getRecipeTypeOrNull(itemstack);
            if (recipeType != null) {
                String langKey = "tile." + tier.getSimpleName() + recipeType.getTranslationKey() + "Factory";
                if (LangUtils.canLocalize(langKey)) {
                    return LangUtils.localize(langKey);
                }
                return tier.getColor() + tier.getLocalizedName() + recipeType.getLocalizedName() + LangUtils.localize("tile.MachineBlock.Factory.name");
            }
        } else if (type == MachineType.FLUID_TANK) {
            return getBaseTier(itemstack).getColor() + LangUtils.localize("tile.FluidTank" + getBaseTier(itemstack).getSimpleName() + ".name");
        }
        return super.getItemStackDisplayName(itemstack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        MachineType type = MachineType.get(itemstack);
        if (type != null) { // If the block is null, no information is added
            if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
                if (type == MachineType.FLUID_TANK && itemstack.getCount() <= 1) {
                    FluidStack fluidStack = getFluidStack(itemstack);
                    if (fluidStack != null) {
                        int amount = getFluidStack(itemstack).amount;
                        String amountStr = amount == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : amount + "mB";
                        list.add(EnumColor.AQUA + LangUtils.localizeFluidStack(fluidStack) + ": " + EnumColor.GREY + amountStr);
                    } else {
                        list.add(EnumColor.DARK_RED + LangUtils.localize("gui.empty") + ".");
                    }
                    int cap = FluidTankTier.values()[getBaseTier(itemstack).ordinal()].getStorage();
                    list.add(EnumColor.INDIGO + LangUtils.localize("tooltip.capacity") + ": " + EnumColor.GREY +
                            (cap == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : cap + " mB"));
                }

                if (type == MachineType.QUANTUM_ENTANGLOPORTER) {
                    FrequencyIdentity freq = getStoredInventoryFrequency(itemstack);
                    if (freq != null) {
                        list.add(EnumColor.INDIGO + LangUtils.localize("gui.frequency") + ": " + EnumColor.GREY + freq.key());

                        list.add(EnumColor.INDIGO + LangUtils.localize("gui.mode") + ": " + EnumColor.GREY +
                              LangUtils.localize("gui." + (freq.securityMode() == SecurityMode.PRIVATE ? "private" : "public")));
                    }
                }

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
                if (type.isFactory()) {
                    RecipeType recipeType = getRecipeTypeOrNull(itemstack);
                    if (recipeType != null) {
                        list.add(EnumColor.INDIGO + LangUtils.localize("tooltip.recipeType") + ": " + EnumColor.GREY + recipeType.getLocalizedName());
                    }
                }
                if (type == MachineType.FLUID_TANK) {
                    list.add(EnumColor.INDIGO + LangUtils.localizeWithFormat("mekanism.tooltip.portableTank.bucketMode", LangUtils.transYesNo(getBucketMode(itemstack))));
                }

                if (type.isElectric && itemstack.getCount() <= 1) {
                    list.add(EnumColor.BRIGHT_GREEN + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack), getEnergyCapacity(itemstack)));
                }

                if (hasTank(itemstack) && type != MachineType.FLUID_TANK) {
                    FluidStack fluidStack = getFluidStack(itemstack);

                    if (fluidStack != null && itemstack.getCount() <= 1) {
                        list.add(EnumColor.PINK + LangUtils.localizeFluidStack(fluidStack) + ": " + EnumColor.GREY + getFluidStack(itemstack).amount + "mB");
                    }
                }

                if (type != MachineType.CHARGEPAD && type != MachineType.LOGISTICAL_SORTER) {
                    list.add(EnumColor.AQUA + LangUtils.localize("tooltip.inventory") + ": " + EnumColor.GREY +
                            LangUtils.transYesNo(getInventory(itemstack) != null && getInventory(itemstack).tagCount() != 0));
                }
                if (type.supportsUpgrades && Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(itemstack))) {
                    Upgrade.buildComponentMap(ItemDataUtils.getDataMapIfPresent(itemstack)).forEach((key, value) -> list.add(key.getColor() + "- " + key.getName() + (key.canMultiply() ? ": " + EnumColor.GREY + "x" + value : "")));
                }
            } else {
                list.addAll(MekanismUtils.splitTooltip(type.getDescription(), itemstack));
            }
        }
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, @Nonnull BlockPos pos, @Nonnull EnumFacing side,
          float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (MachineType.get(stack) == MachineType.PERSONAL_CHEST && tryClaimPersonalChest(world, player, stack)) {
            //Claiming the chest consumes this click. The next click can place it or open its GUI.
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        MachineType type = MachineType.get(stack);
        if (type == MachineType.PERSONAL_CHEST) {
            if (tryClaimPersonalChest(world, player, stack)) {
                return EnumActionResult.SUCCESS;
            }
            //A claimed personal chest must never be used as a shared stack, as its inventory is stack-backed.
            if (getOwnerUUID(stack) == null || stack.getCount() > 1) {
                return EnumActionResult.PASS;
            }
        }
        if (type == MachineType.FLUID_TANK) {
            if (getBucketMode(stack)) {
                return EnumActionResult.PASS;
            }
        }
        return super.onItemUse(player, world, pos, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull IBlockState state) {
        boolean place = true;
        MachineType type = MachineType.get(stack);
        if (type == MachineType.PERSONAL_CHEST && (getOwnerUUID(stack) == null || stack.getCount() > 1)) {
            return false;
        }
        if (MekanismConfig.current().general.destroyDisabledBlocks.val()){
            if (type != null && !type.isEnabled()) {
                return false;
            }
        }
        if (type == MachineType.DIGITAL_MINER) {
            BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();
            for (int xPos = -1; xPos <= +1; xPos++) {
                for (int yPos = 0; yPos <= +1; yPos++) {
                    for (int zPos = -1; zPos <= +1; zPos++) {
                        testPos.setPos(pos.getX() + xPos, pos.getY() + yPos, pos.getZ() + zPos);
                        if (!MekanismUtils.isValidBoundingBlockPosition(world, testPos, pos)) {
                            place = false;
                        }
                    }
                }
            }
        } else if (type == MachineType.MODIFICATION_STATION) {
            int facingIndex = MathHelper.floor((player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            EnumFacing facing = switch (facingIndex) {
                case 0 -> EnumFacing.NORTH;
                case 1 -> EnumFacing.EAST;
                case 2 -> EnumFacing.SOUTH;
                case 3 -> EnumFacing.WEST;
                default -> EnumFacing.NORTH;
            };
            EnumFacing right = MekanismUtils.getRight(facing);
            place = MekanismUtils.isValidBoundingBlockPosition(world, pos.up(), pos)
                  && MekanismUtils.isValidBoundingBlockPosition(world, pos.offset(right), pos)
                  && MekanismUtils.isValidBoundingBlockPosition(world, pos.offset(right).up(), pos);
        } else if (type == MachineType.SOLAR_NEUTRON_ACTIVATOR || type == MachineType.SEISMIC_VIBRATOR || type == MachineType.ISOTOPIC_CENTRIFUGE) {
            BlockPos abovePos = pos.up();
            if (!MekanismUtils.isValidBoundingBlockPosition(world, abovePos, pos)) {
                place = false;
            }
        }

        return place && super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state);
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull TileEntity tileEntity) {
        if (tileEntity instanceof TileEntityFluidTank tile) {
            tile.tier = FluidTankTier.values()[getBaseTier(stack).ordinal()];
        }
        if (tileEntity instanceof TileEntityFactory factory) {
            RecipeType recipeType = getRecipeTypeOrNull(stack);
            if (recipeType != null) {
                factory.setRecipeType(recipeType);
            }
        }
        MekanismPlacementData.restoreCommon(stack, placer, tileEntity);
        if (world != null && tileEntity instanceof TileEntityFactory) {
            world.notifyNeighborsOfStateChange(pos, tileEntity.getBlockType(), true);
        }
        if (world != null && !world.isRemote && tileEntity instanceof TileEntityQuantumEntangloporter quantum) {
            FrequencyIdentity frequency = getStoredInventoryFrequency(stack);
            if (frequency != null) {
                quantum.setFrequency(frequency);
            }
        }
    }

    @Nullable
    private FrequencyIdentity getStoredInventoryFrequency(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, NBTConstants.COMPONENT_FREQUENCY)) {
            NBTTagCompound frequencyData = ItemDataUtils.getCompound(stack, NBTConstants.COMPONENT_FREQUENCY);
            if (frequencyData.hasKey(FrequencyType.INVENTORY.getName())) {
                return FrequencyType.INVENTORY.getIdentitySerializer().read(frequencyData.getCompoundTag(FrequencyType.INVENTORY.getName()));
            }
        }
        return null;
    }

    public boolean tryPlaceContainedLiquid(World world, ItemStack itemstack, BlockPos pos) {
        if (getFluidStack(itemstack) == null || !getFluidStack(itemstack).getFluid().canBePlacedInWorld()) {
            return false;
        }
        Material material = world.getBlockState(pos).getMaterial();
        boolean flag = !material.isSolid();
        if (!world.isAirBlock(pos) && !flag) {
            return false;
        }
        if (world.provider.doesWaterVaporize() && getFluidStack(itemstack).getFluid() == FluidRegistry.WATER) {
            world.playSound(null, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F,
                    2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
            for (int l = 0; l < 8; l++) {
                world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + Math.random(),
                        pos.getY() + Math.random(), pos.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
            }
        } else {
            if (!world.isRemote && flag && !material.isLiquid()) {
                world.destroyBlock(pos, true);
            }
            world.setBlockState(pos, MekanismUtils.getFlowingBlock(getFluidStack(itemstack).getFluid()).getDefaultState(), 3);
        }
        return true;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer entityplayer, @Nonnull EnumHand hand) {
        ItemStack itemstack = entityplayer.getHeldItem(hand);
        MachineType type = MachineType.get(itemstack);
        if (type == MachineType.PERSONAL_CHEST && tryClaimPersonalChest(world, entityplayer, itemstack)) {
            //Claiming the chest consumes this click. The next click can open its GUI.
            return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
        }
        if (itemstack.getCount() > 1) {
            return new ActionResult<>(EnumActionResult.PASS, itemstack);
        }
        if (type == MachineType.PERSONAL_CHEST) {
            if (!world.isRemote) {
                if (SecurityUtils.canAccess(entityplayer, itemstack)) {
                    MekanismUtils.openItemGui(entityplayer, hand, 19);
                } else {
                    SecurityUtils.displayNoAccess(entityplayer);
                }
            }
        } else if (type == MachineType.FLUID_TANK && getBucketMode(itemstack)) {
            if (SecurityUtils.canAccess(entityplayer, itemstack)) {
                RayTraceResult pos = rayTrace(world, entityplayer, !entityplayer.isSneaking());
                //It can be null if there is nothing in range
                if (pos != null && pos.typeOfHit == RayTraceResult.Type.BLOCK) {
                    Coord4D coord = new Coord4D(pos.getBlockPos(), world);
                    if (!world.provider.canMineBlock(entityplayer, coord.getPos())) {
                        return new ActionResult<>(EnumActionResult.FAIL, itemstack);
                    }
                    if (!entityplayer.isSneaking()) {
                        if (!entityplayer.canPlayerEdit(coord.getPos(), pos.sideHit, itemstack)) {
                            return new ActionResult<>(EnumActionResult.FAIL, itemstack);
                        }
                        IExtendedFluidTank fluidTank = getExtendedFluidTank(itemstack);
                        FluidStack stored = fluidTank == null ? getFluidStack(itemstack) : fluidTank.getFluid();
                        FluidStack fluid = MekanismUtils.getFluid(world, coord, false);
                        if (fluid != null && (stored == null || stored.isFluidEqual(fluid))) {
                            int storedAmount = stored == null ? 0 : stored.amount;
                            int needed = getCapacity(itemstack) - storedAmount;
                            if (fluid.amount > needed) {
                                return new ActionResult<>(EnumActionResult.FAIL, itemstack);
                            }
                            setFluidStackSize(itemstack, fluidTank, fluid, storedAmount + fluid.amount);
                            world.setBlockToAir(coord.getPos());
                        }
                    } else {
                        IExtendedFluidTank fluidTank = getExtendedFluidTank(itemstack);
                        FluidStack stored = fluidTank == null ? getFluidStack(itemstack) : fluidTank.getFluid();
                        if (stored == null || stored.amount < Fluid.BUCKET_VOLUME) {
                            return new ActionResult<>(EnumActionResult.FAIL, itemstack);
                        }
                        Coord4D trans = coord.offset(pos.sideHit);
                        if (!entityplayer.canPlayerEdit(trans.getPos(), pos.sideHit, itemstack)) {
                            return new ActionResult<>(EnumActionResult.FAIL, itemstack);
                        }
                        if (tryPlaceContainedLiquid(world, itemstack, trans.getPos())
                                && !entityplayer.capabilities.isCreativeMode) {
                            setFluidStackSize(itemstack, fluidTank, stored, stored.amount - Fluid.BUCKET_VOLUME);
                        }
                    }
                }
                return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
            } else {
                SecurityUtils.displayNoAccess(entityplayer);
            }
        }
        return new ActionResult<>(EnumActionResult.PASS, itemstack);
    }

    private boolean tryClaimPersonalChest(World world, EntityPlayer player, ItemStack stack) {
        if (getOwnerUUID(stack) == null) {
            if (!world.isRemote) {
                setOwnerUUID(stack, player.getUniqueID());
            }
            return true;
        }
        return false;
    }

    @Override
    public int getRecipeType(ItemStack itemStack) {
        RecipeType recipeType = getRecipeTypeOrNull(itemStack);
        return recipeType == null ? -1 : recipeType.ordinal();
    }

    @Nullable
    @Override
    public RecipeType getRecipeTypeOrNull(ItemStack itemStack) {
        NBTTagCompound nbt = itemStack.getTagCompound();
        if (!FactoryRecipeTypeCodec.hasRecipeType(nbt)) {
            return RecipeType.SMELTING;
        }
        return FactoryRecipeTypeCodec.read(nbt);
    }

    @Override
    public void setRecipeType(int type, ItemStack itemStack) {
        if (itemStack.getTagCompound() == null) {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        RecipeType[] values = RecipeType.values();
        if (type >= 0 && type < values.length) {
            FactoryRecipeTypeCodec.write(itemStack.getTagCompound(), values[type]);
        } else {
            FactoryRecipeTypeCodec.writeLegacyOrdinal(itemStack.getTagCompound(), type);
        }
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
            if (MachineType.get(itemStack) == MachineType.FLUID_TANK) {
                IExtendedFluidTank fluidTank = getExtendedFluidTank(itemStack);
                if (fluidTank != null) {
                    fluidTank.setStack(fluidStack == null || fluidStack.amount <= 0 ? null : fluidStack.copy());
                } else if (itemStack.getCount() == 1) {
                    ItemDataUtils.setStoredFluid(itemStack, "fluidTank", fluidStack, Integer.MAX_VALUE);
                }
                return;
            }
            ItemDataUtils.setStoredFluid(itemStack, "fluidTank", fluidStack, Integer.MAX_VALUE);
        }
    }

    @Override
    public FluidStack getFluidStack(Object... data) {
        if (data[0] instanceof ItemStack itemStack) {
            if (MachineType.get(itemStack) == MachineType.FLUID_TANK) {
                IExtendedFluidTank fluidTank = getExtendedFluidTank(itemStack);
                if (fluidTank != null && fluidTank.getFluid() != null) {
                    return fluidTank.getFluid().copy();
                }
                return ItemDataUtils.getStoredFluid(itemStack, "fluidTank");
            }
            return ItemDataUtils.getStoredFluid(itemStack, "fluidTank");
        }
        return null;
    }

    @Nullable
    private IExtendedFluidTank getExtendedFluidTank(@Nonnull ItemStack stack) {
        if (stack.getCount() != 1 || MachineType.get(stack) != MachineType.FLUID_TANK) {
            return null;
        }
        IFluidHandlerItem fluidHandlerItem = FluidContainerUtils.getFluidHandlerCapability(stack);
        if (fluidHandlerItem instanceof IMekanismFluidHandler fluidHandler) {
            return fluidHandler.getFluidTank(0, null);
        }
        return null;
    }

    @Override
    public boolean hasTank(Object... data) {
        if (!(data[0] instanceof ItemStack stack) || !(stack.getItem() instanceof ISustainedTank)) {
            return false;
        }
        MachineType type = MachineType.get(stack);
        return type == MachineType.ELECTRIC_PUMP || type == MachineType.FLUID_TANK || type == MachineType.FLUIDIC_PLENISHER;
    }

    public void setBucketMode(ItemStack itemStack, boolean bucketMode) {
        ItemDataUtils.setBoolean(itemStack, "bucketMode", bucketMode);
    }

    public boolean getBucketMode(ItemStack itemStack) {
        return ItemDataUtils.getBoolean(itemStack, "bucketMode");
    }

    public void setStoredEnergy(ItemStack itemStack, double amount) {
        if (itemStack.getCount() > 1) {
            return;
        }
        MachineType machineType = MachineType.get(itemStack);
        if (machineType == null || !machineType.isElectric) {
            return;
        }
        StorageUtils.setStoredEnergy(itemStack, amount, getEnergyCapacity(itemStack));
    }

    public double getEnergyCapacity(ItemStack itemStack) {
        MachineType machineType = MachineType.get(itemStack);
        if (machineType == null) {
            return 0;
        }
        if (machineType.isFactory()) {
            RecipeType recipeType = getRecipeTypeOrNull(itemStack);
            int tierProcess = machineType.factoryTier.processes;
            double baseMaxEnergy = tierProcess * (recipeType == null ? 1 : Math.max(0.5D * recipeType.getEnergyStorage(), recipeType.getEnergyUsage()));
            return Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(itemStack)) ? MekanismUtils.getMaxEnergy(itemStack, baseMaxEnergy) : baseMaxEnergy;
        }
        return Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(itemStack)) ? MekanismUtils.getMaxEnergy(itemStack, machineType.getStorage()) : machineType.getStorage();
    }

    public double getEnergyTransfer(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        return getEnergyCapacity(itemStack) * 0.005;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        MachineType machineType = MachineType.get(itemStack);
        return machineType != null && machineType.isElectric && itemStack.getCount() == 1;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
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
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
    }

    public FluidStack getFluid(ItemStack container) {
        return getFluidStack(container);
    }

    public int getCapacity(ItemStack container) {
        return FluidTankTier.values()[getBaseTier(container).ordinal()].getStorage();
    }

    public int fill(ItemStack container, FluidStack resource, boolean doFill) {
        if (container.getCount() > 1) {
            return 0;
        }
        IExtendedFluidTank fluidTank = getExtendedFluidTank(container);
        if (fluidTank != null) {
            return fluidTank.fill(resource, doFill);
        }
        if (MachineType.get(container) == MachineType.FLUID_TANK && resource != null) {
            if (getBaseTier(container) == BaseTier.CREATIVE) {
                setFluidStack(PipeUtils.copy(resource, Integer.MAX_VALUE), container);
                return resource.amount;
            }
            FluidStack stored = getFluidStack(container);
            int toFill;
            if (stored != null && stored.getFluid() != resource.getFluid()) {
                return 0;
            }
            if (stored == null) {
                toFill = Math.min(resource.amount, getCapacity(container));
            } else {
                toFill = Math.min(resource.amount, getCapacity(container) - stored.amount);
            }
            if (doFill) {
                int fillAmount = toFill + (stored == null ? 0 : stored.amount);
                setFluidStack(PipeUtils.copy(resource, fillAmount), container);
            }
            return toFill;
        }
        return 0;
    }

    private void setFluidStackSize(ItemStack itemStack, @Nullable IExtendedFluidTank fluidTank, @Nullable FluidStack type, int amount) {
        if (amount <= 0 || type == null) {
            if (fluidTank != null) {
                fluidTank.setEmpty();
            } else {
                setFluidStack(null, itemStack);
            }
        } else if (fluidTank != null) {
            if (fluidTank.isEmpty()) {
                fluidTank.setStack(PipeUtils.copy(type, amount));
            } else {
                fluidTank.setStackSize(amount, Action.EXECUTE);
            }
        } else {
            setFluidStack(PipeUtils.copy(type, amount), itemStack);
        }
    }

    public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {
        if (container.getCount() > 1) {
            return null;
        }
        IExtendedFluidTank fluidTank = getExtendedFluidTank(container);
        if (fluidTank != null) {
            return fluidTank.drain(maxDrain, doDrain);
        }
        if (MachineType.get(container) == MachineType.FLUID_TANK) {
            FluidStack stored = getFluidStack(container);
            if (stored != null) {
                FluidStack toDrain = PipeUtils.copy(stored, Math.min(stored.amount, maxDrain));
                if (doDrain && getBaseTier(container) != BaseTier.CREATIVE) {
                    int remaining = stored.amount - toDrain.amount;
                    setFluidStack(remaining > 0 ? PipeUtils.copy(stored, remaining) : null, container);
                }
                return toDrain;
            }
        }
        return null;
    }

    @Override
    public BaseTier getBaseTier(ItemStack itemstack) {
        if (!itemstack.hasTagCompound()) {
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
        if (!itemstack.hasTagCompound()) {
            itemstack.setTagCompound(new NBTTagCompound());
        }
        itemstack.getTagCompound().setInteger("tier", tier.ordinal());
    }

    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, "ownerUUID")) {
            try {
                return UUID.fromString(ItemDataUtils.getString(stack, "ownerUUID"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
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
        int security = ItemDataUtils.getInt(stack, "security");
        if (security >= 0 && security < SecurityMode.values().length) {
            return SecurityMode.values()[security];
        }
        return SecurityMode.PUBLIC;
    }

    @Override
    public void setSecurity(ItemStack stack, SecurityMode mode) {
        if (getOwnerUUID(stack) == null) {
            ItemDataUtils.removeData(stack, "security");
        } else {
            ItemDataUtils.setInt(stack, "security", mode.ordinal());
        }
    }

    @Override
    public boolean hasSecurity(ItemStack stack) {
        MachineType type = MachineType.get(stack);
        if (type == null) {
            return false;
        }
        return type != MachineType.LASER && type != MachineType.CHARGEPAD && type != MachineType.TELEPORTER && type != MachineType.QUANTUM_ENTANGLOPORTER && type != MachineType.INDUSTRIAL_ALARM;

    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return hasSecurity(stack);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, new TeslaItemWrapper(), new ForgeEnergyItemWrapper(),
              RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack), BasicEnergyContainer.manualOnly, ConstantPredicates.alwaysTrue()),
              RateLimitFluidHandler.create(() -> FluidTankTier.values()[getBaseTier(stack).ordinal()], "fluidTank")) {
            @Override
            public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
                if (capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY) {
                    return MachineType.get(itemStack) == MachineType.FLUID_TANK && itemStack.getCount() == 1;
                }
                return super.hasCapability(capability, facing);
            }

            @Override
            public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
                if (capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY &&
                      (MachineType.get(itemStack) != MachineType.FLUID_TANK || itemStack.getCount() != 1)) {
                    return null;
                }
                return super.getCapability(capability, facing);
            }
        };
    }


    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return stack.getCount() == 1 && StorageUtils.getStoredEnergy(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        double capacity = getEnergyCapacity(stack);
        return capacity <= 0 ? 1D : 1D - (StorageUtils.getStoredEnergy(stack) / capacity);
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        if (MachineType.get(stack) == MachineType.FLUID_TANK) {
            if (Math.abs(shift) % 2 == 1) {
                boolean newState = !getBucketMode(stack);
                setBucketMode(stack, newState);
                displayChange.sendMessage(player, () -> new TextComponentGroup().translation("mekanism.tooltip.portableTank.bucketMode", LangUtils.onOffColoured(newState)));
            }
        }
    }

    @Override
    public boolean supportsSlotType(ItemStack stack, @Nonnull EntityEquipmentSlot slotType) {
        return IModeItem.super.supportsSlotType(stack, slotType) && MachineType.get(stack) == MachineType.FLUID_TANK;
    }

    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        if (MachineType.get(stack) == MachineType.FLUID_TANK) {
            return new TextComponentGroup(TextFormatting.GRAY).translation("mekanism.tooltip.portableTank.bucketMode", LangUtils.onOffColoured(getBucketMode(stack)));
        }
        return new TextComponentString("");
    }

}
