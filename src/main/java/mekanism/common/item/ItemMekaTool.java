package mekanism.common.item;

import com.google.common.collect.Multimap;
import com.wdcftgg.farmersdelightlegacy.api.knife.IKnifeItem;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.Magnetic;
import mekanism.api.math.MathUtils;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.radial.mode.NestedRadialMode;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.MekanismModules;
import mekanism.common.OreDictCache;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.mekatool.*;
import mekanism.common.content.gear.shared.ModuleEnergyUnit;
import mekanism.common.entity.EntityMeka;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.lib.radial.data.NestingRadialData;
import mekanism.common.network.PacketPortalFX;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Interface(iface = "com.wdcftgg.farmersdelightlegacy.api.knife.IKnifeItem", modid = MekanismHooks.FARMERS_DELIGHT_LEGACY_MOD_ID)
public class ItemMekaTool extends ItemEnergized implements IModuleContainerItem, IGenericRadialModeItem, Magnetic, IBlastingItem, IKnifeItem {

    private static final ResourceLocation RADIAL_ID = Mekanism.rl("meka_tool");
    private static final double CUTTING_BOARD_RECIPE_ENERGY_USAGE = 50;


    public ItemMekaTool() {
        super(MekanismConfig.current().meka.mekaToolBaseEnergyCapacity.val());
        setRarity(EnumRarity.EPIC);
        setMaxStackSize(1);
        setNoRepair();
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack discharged = new ItemStack(this);
        list.add(discharged);
        ItemStack charged = new ItemStack(this);
        StorageUtils.setStoredEnergy(charged, getEnergyCapacity(charged));
        list.add(charged);
        ItemStack FullStack = new ItemStack(this);
        setAllModule(FullStack);
        StorageUtils.setStoredEnergy(FullStack, getEnergyCapacity(FullStack));
        list.add(FullStack);
    }


    @Override
    public boolean canHarvestBlock(IBlockState blockIn) {
        //Allow harvesting everything, things that are unbreakable are caught elsewhere
        return true;
    }

    @Override
    @Method(modid = MekanismHooks.FARMERS_DELIGHT_LEGACY_MOD_ID)
    public ItemStack getKnifeJeiInfoStack(ItemStack stack) {
        return MekanismModules.LASER_CUTTER_UNIT.getStack();
    }


    @Override
    @Method(modid = MekanismHooks.FARMERS_DELIGHT_LEGACY_MOD_ID)
    public boolean canProcessCuttingBoardRecipe(ItemStack stack, World world, BlockPos pos, IBlockState state, EntityPlayer player, ItemStack inputStack) {
        return StorageUtils.getStoredEnergy(stack) >= CUTTING_BOARD_RECIPE_ENERGY_USAGE && hasCuttingBoardRecipeModule(stack);
    }

    @Override
    @Method(modid = MekanismHooks.FARMERS_DELIGHT_LEGACY_MOD_ID)
    public void onCuttingBoardRecipeProcessed(ItemStack stack, World world, EntityPlayer player) {
        if (StorageUtils.getStoredEnergy(stack) >= CUTTING_BOARD_RECIPE_ENERGY_USAGE) {
            StorageUtils.extractFromContainer(stack, CUTTING_BOARD_RECIPE_ENERGY_USAGE, Action.EXECUTE);
        }
    }

    private boolean hasCuttingBoardRecipeModule(ItemStack stack) {
        return isModuleEnabled(stack, MekanismModules.LASER_CUTTER_UNIT);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        if (MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            addModuleDetails(stack, tooltip);
        } else {
            tooltip.add(EnumColor.AQUA + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(stack), getEnergyCapacity(stack)));
            tooltip.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return !stack.isEmpty() && super.hasEffect(stack) && IModuleContainerItem.hasOtherEnchants(stack);
    }


    @Nonnull
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        for (Module<?> module : getModules(player.getHeldItem(hand))) {
            if (module != null && module.isEnabled()) {
                EnumActionResult result = onModuleUse(module, player, world, pos, hand, side, hitX, hitY, hitZ);
                if (result != EnumActionResult.PASS) {
                    return result;
                }
            }
        }
        return super.onItemUse(player, world, pos, hand, side, hitX, hitY, hitZ);
    }


    private <MODULE extends ICustomModule<MODULE>> EnumActionResult onModuleUse(IModule<MODULE> module, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        return module.getCustomInstance().onItemUse(module, player, world, pos, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase entity, EnumHand hand) {
        for (Module<?> module : getModules(stack)) {
            if (module != null && module.isEnabled()) {
                EnumActionResult result = onModuleInteract(module, player, entity, hand);
                if (result != EnumActionResult.PASS && result != EnumActionResult.FAIL) {
                    return true;
                }
            }
        }
        return super.itemInteractionForEntity(stack, player, entity, hand);
    }


    private <MODULE extends ICustomModule<MODULE>> EnumActionResult onModuleInteract(IModule<MODULE> module, @Nonnull EntityPlayer player, @Nonnull EntityLivingBase entity, @Nonnull EnumHand hand) {
        return module.getCustomInstance().onInteract(module, player, entity, hand);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
        if (energyContainer == null) {
            return 0;
        }

        //Use raw hardness to get the best guess of if it is zero or not
        double energyRequired = getDestroyEnergy(stack, state.getBlock().blockHardness, isModuleEnabled(stack, MekanismModules.SILK_TOUCH_UNIT));
        double energyAvailable = energyContainer.extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL);
        if (energyAvailable < (energyRequired)) {
            //If we can't extract all the energy we need to break it go at base speed reduced by how much we actually have available
            return (float) (MekanismConfig.current().meka.mekaToolBaseEfficiency.val() * (energyAvailable / (energyRequired)));
        }
        IModule<ModuleExcavationEscalationUnit> module = getModule(stack, MekanismModules.EXCAVATION_ESCALATION_UNIT);
        return module == null || !module.isEnabled() ? MekanismConfig.current().meka.mekaToolBaseEfficiency.val() : module.getCustomInstance().getEfficiency();
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, IBlockState state, BlockPos pos, EntityLivingBase entity) {
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
        if (energyContainer != null) {
            double energyRequired = getDestroyEnergy(stack, state.getBlockHardness(world, pos), isModuleEnabled(stack, MekanismModules.SILK_TOUCH_UNIT));
            double extractedEnergy = energyContainer.extract(energyRequired, Action.EXECUTE, AutomationType.MANUAL);
            if (extractedEnergy == (energyRequired) || entity instanceof EntityPlayer player && player.isCreative()) {
                //Only disarm tripwires if we had all the energy we tried to use (or are creative). Otherwise, treat it as if we may have failed to disarm it
                if (state.getBlock() == Blocks.TRIPWIRE && !state.getValue(BlockTripWire.DISARMED) && isModuleEnabled(stack, MekanismModules.SHEARING_UNIT)) {
                    world.setBlockState(pos, state.withProperty(BlockTripWire.DISARMED, Boolean.valueOf(true)), Constants.BlockFlags.NO_RERENDER);
                }
            }

        }
        return true;
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        getModules(stack).forEach(module -> module.hitEntity(stack, target, attacker));
        return false;
    }

    @Override
    public Map<BlockPos, IBlockState> getBlastedBlocks(World world, EntityPlayer player, ItemStack stack, BlockPos pos, IBlockState state) {
        //Setup initial set for blasting
        if (!player.isSneaking()) {
            IModule<ModuleBlastingUnit> blastingUnit = getModule(stack, MekanismModules.BLASTING_UNIT);
            if (blastingUnit != null && blastingUnit.isEnabled()) {
                int radius = blastingUnit.getCustomInstance().getBlastRadius();
                if (radius > 0 && IBlastingItem.canBlastBlock(world, pos, state)) {
                    return IBlastingItem.findPositions(world, pos, player, radius);
                }
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public Map<BlockPos, IBlockState> getBlastedBlocksForRendering(World world, EntityPlayer player, ItemStack stack, BlockPos pos, IBlockState state) {
        VeinMiningTargets targets = getVeinMiningTargets(world, player, stack, pos, state);
        if (targets == null || targets.getVeinedBlocks().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<BlockPos, IBlockState> blocks = new HashMap<>(targets.getVeinedBlocks().size());
        for (BlockPos targetPos : targets.getVeinedBlocks().keySet()) {
            IBlockState targetState = world.getBlockState(targetPos);
            if (!targetState.getBlock().isAir(targetState, world, targetPos)) {
                blocks.put(targetPos, targetState);
            }
        }
        return blocks;
    }

    public VeinMiningTargets getVeinMiningTargets(World world, EntityPlayer player, ItemStack stack, BlockPos pos, IBlockState state) {
        Map<BlockPos, IBlockState> blocks = getBlastedBlocks(world, player, stack, pos, state);
        if (blocks.isEmpty() && ModuleVeinMiningUnit.canVeinBlock(state)) {
            Map<BlockPos, IBlockState> fallback = new HashMap<>();
            fallback.put(pos, state);
            blocks = fallback;
        }
        if (blocks.isEmpty()) {
            return null;
        }
        Block block = state.getBlock();
        if (block == Blocks.LIT_REDSTONE_ORE) {
            block = Blocks.REDSTONE_ORE;
        }
        RayTraceResult raytrace = doRayTrace(state, pos, player);
        ItemStack pickedStack = block.getPickBlock(state, raytrace, player.world, pos, player);
        List<String> names = OreDictCache.getOreDictName(pickedStack);
        boolean isOre = names.stream().anyMatch(s -> s.startsWith("ore") || s.equals("logWood"));
        Reference2BooleanMap<Block> oreTracker = blocks.values().stream().collect(Collectors.toMap(IBlockState::getBlock, bs -> isOre, (l, r) -> l, Reference2BooleanArrayMap::new));
        Object2IntMap<BlockPos> veinedBlocks = getVeinedBlocks(world, stack, blocks, oreTracker);
        return new VeinMiningTargets(oreTracker, veinedBlocks, isOre);
    }

    public static class VeinMiningTargets {
        private final Reference2BooleanMap<Block> oreTracker;
        private final Object2IntMap<BlockPos> veinedBlocks;
        private final boolean isOre;

        private VeinMiningTargets(Reference2BooleanMap<Block> oreTracker, Object2IntMap<BlockPos> veinedBlocks, boolean isOre) {
            this.oreTracker = oreTracker;
            this.veinedBlocks = veinedBlocks;
            this.isOre = isOre;
        }

        public Reference2BooleanMap<Block> getOreTracker() {
            return oreTracker;
        }

        public Object2IntMap<BlockPos> getVeinedBlocks() {
            return veinedBlocks;
        }

        public boolean isOre() {
            return isOre;
        }
    }


    public Object2IntMap<BlockPos> getVeinedBlocks(World world, ItemStack stack, Map<BlockPos, IBlockState> blocks, Reference2BooleanMap<Block> oreTracker) {
        IModule<ModuleVeinMiningUnit> veinMiningUnit = getModule(stack, MekanismModules.VEIN_MINING_UNIT);
        if (veinMiningUnit != null && veinMiningUnit.isEnabled()) {
            ModuleVeinMiningUnit customInstance = veinMiningUnit.getCustomInstance();
            return ModuleVeinMiningUnit.findPositions(world, blocks, customInstance.isExtended() ? customInstance.getExcavationRange() : 0, oreTracker);
        }
        return blocks.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, be -> 0, (l, r) -> l, Object2IntArrayMap::new));
    }


    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
        if (player.world.isRemote || player.isCreative()) {
            return super.onBlockStartBreak(itemstack, pos, player);
        }
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(itemstack, 0);
        if (energyContainer != null) {
            World world = player.world;
            IBlockState state = world.getBlockState(pos);
            boolean silk = isModuleEnabled(itemstack, MekanismModules.SILK_TOUCH_UNIT);
            double modDestroyEnergy = getDestroyEnergy(itemstack, silk);
            double energyRequired = getDestroyEnergy(modDestroyEnergy, state.getBlockHardness(world, pos));
            if (energyContainer.extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL) >= energyRequired) {
                VeinMiningTargets targets = getVeinMiningTargets(world, player, itemstack, pos, state);
                if (targets != null && !targets.getVeinedBlocks().isEmpty()) {
                    double baseDestroyEnergy = getDestroyEnergy(silk);
                    MekanismUtils.veinMineArea(energyContainer, energyRequired, world, pos, (EntityPlayerMP) player, itemstack, this, targets.getVeinedBlocks(),
                            hardness -> getDestroyEnergy(modDestroyEnergy, hardness),
                            (hardness, distance, bs) -> getDestroyEnergy(baseDestroyEnergy, hardness) * (0.5 * Math.pow(distance, targets.getOreTracker().getBoolean(targets.isOre()) ? 1.5 : 2)));

                }
            }
        }
        return super.onBlockStartBreak(itemstack, pos, player);
    }


    public RayTraceResult doRayTrace(IBlockState state, BlockPos pos, EntityPlayer player) {
        Vec3d positionEyes = player.getPositionEyes(1.0F);
        Vec3d playerLook = player.getLook(1.0F);
        double blockReachDistance = player.getAttributeMap().getAttributeInstance(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d maxReach = positionEyes.add(playerLook.x * blockReachDistance, playerLook.y * blockReachDistance, playerLook.z * blockReachDistance);
        RayTraceResult res = state.collisionRayTrace(player.world, pos, playerLook, maxReach);
        //noinspection ConstantConditions - idea thinks it's nonnull due to package level annotations, but it's not
        return res != null ? res : new RayTraceResult(RayTraceResult.Type.MISS, Vec3d.ZERO, EnumFacing.UP, pos);
    }

    public double getDestroyEnergy(boolean silk) {
        return silk ? MekanismConfig.current().meka.mekaToolEnergyUsageSilk.val() : MekanismConfig.current().meka.mekaToolEnergyUsage.val();
    }

    public double getDestroyEnergy(ItemStack itemStack, float hardness, boolean silk) {
        return getDestroyEnergy(getDestroyEnergy(itemStack, silk), hardness);
    }

    public double getDestroyEnergy(double baseDestroyEnergy, float hardness) {
        return hardness == 0 ? baseDestroyEnergy / (2) : baseDestroyEnergy;
    }


    public double getDestroyEnergy(ItemStack itemStack, boolean silk) {
        double destroyEnergy = getDestroyEnergy(silk);
        IModule<ModuleExcavationEscalationUnit> module = getModule(itemStack, MekanismModules.EXCAVATION_ESCALATION_UNIT);
        float efficiency = module == null || !module.isEnabled() ? MekanismConfig.current().meka.mekaToolBaseEfficiency.val() : module.getCustomInstance().getEfficiency();
        return destroyEnergy * (efficiency);
    }


    @Nonnull
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);
        double damage = MekanismConfig.current().meka.mekaToolBaseDamage.val();
        double attackSpeed = MekanismConfig.current().meka.mekaToolAttackSpeed.val();
        IModule<ModuleAttackAmplificationUnit> attackAmplificationUnit = getModule(stack, MekanismModules.ATTACK_AMPLIFICATION_UNIT);
        if (attackAmplificationUnit != null && attackAmplificationUnit.isEnabled()) {
            int unitDamage = attackAmplificationUnit.getCustomInstance().getDamage();
            if (unitDamage > 0) {
                double energyCost = MekanismConfig.current().meka.mekaToolEnergyUsageWeapon.val() * (unitDamage / 4D);
                IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                double energy = energyContainer == null ? 0L : energyContainer.getEnergy();
                if (energy < energyCost) {
                    //If we don't have enough power use it at a reduced power level (this will be false the majority of the time)
                    damage += unitDamage * MathUtils.divideToLevel(energy, energyCost);
                } else {
                    damage += unitDamage;
                }
            }
        }
        if (slot == EntityEquipmentSlot.MAINHAND) {
            multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", damage, 0));
            multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", attackSpeed, 0));
        }
        getModules(stack).forEach(module -> module.multimapModule(multimap, slot, stack));
        return multimap;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            IModule<ModuleTeleportationUnit> module = getModule(stack, MekanismModules.TELEPORTATION_UNIT);
            if (module != null && module.isEnabled()) {
                RayTraceResult result = MekanismUtils.rayTrace(world, player, MekanismConfig.current().meka.mekaToolMaxTeleportReach.val());
                //If we don't require a block target or are not a miss, allow teleporting
                if (!module.getCustomInstance().requiresBlockTarget() || result.typeOfHit != RayTraceResult.Type.MISS) {
                    BlockPos pos = result.getBlockPos();
                    // make sure we fit
                    if (isValidDestinationBlock(world, pos.up()) && isValidDestinationBlock(world, pos.up(2))) {
                        double distance = player.getDistanceSq(pos.getX(), pos.getY(), pos.getZ());
                        if (distance < 5) {
                            return new ActionResult<>(EnumActionResult.PASS, stack);
                        }
                        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                        double energyNeeded = MekanismConfig.current().meka.mekaToolEnergyUsageTeleport.val() * (distance / 10D);
                        if (energyContainer == null || energyContainer.getEnergy() < energyNeeded) {
                            return new ActionResult<>(EnumActionResult.FAIL, stack);
                        }
                        energyContainer.extract(energyNeeded, Action.EXECUTE, AutomationType.MANUAL);
                        if (player.isRiding()) {
                            player.dismountRidingEntity();
                        }
                        player.setPositionAndUpdate(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
                        player.fallDistance = 0.0F;
                        Mekanism.packetHandler.sendToAllTracking(new PacketPortalFX.PortalFXMessage(new Coord4D(pos, world)), new Coord4D(pos, world));
                        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
                    }
                }
            }
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    private boolean isValidDestinationBlock(World world, BlockPos pos) {
        IBlockState blockState = world.getBlockState(pos);
        //Allow teleporting into air or fluids
        return blockState.getBlock().isAir(blockState, world, pos) || blockState.getBlock() instanceof BlockLiquid || blockState.getBlock() instanceof IFluidBlock;
    }

    @Override
    public boolean isEnchantable(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean supportsSlotType(ItemStack stack, @Nonnull EntityEquipmentSlot slotType) {
        return IGenericRadialModeItem.super.supportsSlotType(stack, slotType) && getModules(stack).stream().anyMatch(Module::handlesModeChange);
    }

    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        return getModules(stack).stream().filter(Module::handlesModeChange).findFirst().map(module -> module.getModeScrollComponent(stack)).orElse(null);
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesModeChange()) {
                module.changeMode(player, stack, shift, displayChange);
                return;
            }
        }
    }

    @Override
    public double getEnergyCapacity(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        return module == null ? MekanismConfig.current().meka.mekaToolBaseEnergyCapacity.val() : module.getCustomInstance().getEnergyCapacity(module);
    }


    @Override
    public double getEnergyTransfer(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        return module == null ? MekanismConfig.current().meka.mekaToolBaseChargeRate.val() : module.getCustomInstance().getChargeRate(module);
    }


    @Override
    public boolean isMagnetic(ItemStack stack) {
        return isModuleEnabled(stack, MekanismModules.MAGNETIC_UNIT);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack itemstack) {
        return new EntityMeka(world, location, itemstack);
    }

    @Override
    public @Nullable RadialData<?> getRadialData(ItemStack stack) {
        List<NestedRadialMode> nestedModes = new ArrayList<>();
        Consumer<NestedRadialMode> adder = nestedModes::add;
        for (Module<?> module : getModules(stack)) {
            if (module.handlesRadialModeChange()) {
                module.addRadialModes(stack, adder);
            }
        }
        if (nestedModes.isEmpty()) {
            //No modes available, return that we don't actually currently support radials
            return null;
        } else if (nestedModes.size() == 1) {
            //If we only have one mode available, just return it rather than having to select the singular mode
            return nestedModes.get(0).nestedData();
        }
        return new NestingRadialData(RADIAL_ID, nestedModes);
    }

    @Override
    public <M extends IRadialMode> @Nullable M getMode(ItemStack stack, RadialData<M> radialData) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesRadialModeChange()) {
                M mode = module.getMode(stack, radialData);
                if (mode != null) {
                    return mode;
                }
            }
        }
        return null;
    }

    @Override
    public <M extends IRadialMode> void setMode(ItemStack stack, EntityPlayer player, RadialData<M> radialData, M mode) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesRadialModeChange() && module.setMode(player, stack, radialData, mode)) {
                return;
            }
        }
    }


    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        getModules(stack).forEach(module -> module.onUpdateModule(stack, world, entity, itemSlot, isSelected));
    }

}
