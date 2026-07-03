package mekanism.common.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMaps;
import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.math.MathUtils;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.text.IHasTextComponent;
import mekanism.common.Mekanism;
import mekanism.common.OreDictCache;
import mekanism.common.block.BlockBounding;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.mekatool.ModuleExcavationEscalationUnit.ExcavationMode;
import mekanism.common.content.gear.mekatool.ModuleVeinMiningUnit;
import mekanism.common.item.ItemAtomicDisassembler.DisassemblerMode;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.lib.radial.IRadialEnumModeItem;
import mekanism.common.lib.radial.data.RadialDataHelper;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class ItemAtomicDisassembler extends ItemEnergized implements IItemHUDProvider, IRadialEnumModeItem<DisassemblerMode> {


    private static final Lazy<RadialData<DisassemblerMode>> LAZY_RADIAL_DATA = Lazy.of(() -> RadialDataHelper.INSTANCE.dataForEnum(Mekanism.rl("disassembler_mode"), DisassemblerMode.NORMAL));

    public ItemAtomicDisassembler() {
        super(MekanismConfig.current().general.disassemblerBatteryCapacity.val());
        setMaxStackSize(1);
        setRarity(EnumRarity.RARE);
        setNoRepair();
    }

    @Override
    public boolean canHarvestBlock(IBlockState blockIn) {
        //Allow harvesting everything, things that are unbreakable are caught elsewhere
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        DisassemblerMode mode = getMode(stack);
        tooltip.add(LangUtils.localize("tooltip.mode") + ": " + EnumColor.INDIGO + mode.getModeName());
        tooltip.add(LangUtils.localize("tooltip.efficiency") + ": " + EnumColor.INDIGO + mode.getEfficiency());
    }


    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
        if (energyContainer != null && !energyContainer.isEmpty()) {
            //Try to extract full energy, even if we have a lower damage amount this is fine as that just means
            // we don't have enough energy, but we will remove as much as we can, which is how much corresponds
            // to the amount of damage we will actually do
            energyContainer.extract(MekanismConfig.current().general.disassemblerEnergyUsageWeapon.val(), Action.EXECUTE, AutomationType.MANUAL);
        }
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
        if (energyContainer == null) {
            return 0;
        }
        double energyRequired = getDestroyEnergy(stack, state.getBlock().blockHardness);
        double energyAvailable = energyContainer.extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL);
        if (energyAvailable < energyRequired) {
            return DisassemblerMode.NORMAL.getEfficiency() * (float) (energyAvailable / energyRequired);
        }
        return getMode(stack).getEfficiency();
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, IBlockState state, BlockPos pos, EntityLivingBase entity) {
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
        if (energyContainer != null) {
            energyContainer.extract(getDestroyEnergy(stack, state.getBlockHardness(world, pos)), Action.EXECUTE, AutomationType.MANUAL);
        }
        return true;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
        if (player.world.isRemote || player.isCreative()) {
            return super.onBlockStartBreak(itemstack, pos, player);
        }
        IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(itemstack, 0);
        if (energyContainer != null && getMode(itemstack) == DisassemblerMode.VEIN) {
            World world = player.world;
            IBlockState state = world.getBlockState(pos);
            double baseDestroyEnergy = getDestroyEnergy(itemstack);
            double energyRequired = getDestroyEnergy(baseDestroyEnergy, state.getBlockHardness(world, pos));
            if (energyContainer.extract(energyRequired, Action.SIMULATE, AutomationType.MANUAL) >= energyRequired) {
                //Even though we now handle breaking bounding blocks properly, don't allow vein mining them
                // only allow mining things that are considered an ore

                Block block = state.getBlock();
                if (block == Blocks.LIT_REDSTONE_ORE) {
                    block = Blocks.REDSTONE_ORE;
                }
                RayTraceResult raytrace = doRayTrace(state, pos, player);
                ItemStack stack = block.getPickBlock(state, raytrace, player.world, pos, player);
                List<String> names = OreDictCache.getOreDictName(stack);
                boolean is = names.stream().anyMatch(s -> s.startsWith("ore") || s.equals("logWood"));
                if (!(state.getBlock() instanceof BlockBounding) && is) {
                    Map<BlockPos, IBlockState> map = new HashMap<>();
                    map.put(pos, state);
                    Object2IntMap<BlockPos> found = ModuleVeinMiningUnit.findPositions(world, map, 0, Reference2BooleanMaps.singleton(state.getBlock(), true));
                    MekanismUtils.veinMineArea(energyContainer, energyRequired, world, pos, (EntityPlayerMP) player, itemstack, this, found, hardness -> 0, (hardness, distance, bs) -> getDestroyEnergy(baseDestroyEnergy, hardness) * (0.5 * Math.pow(distance, 1.5)));
                }
            }
        }
        return super.onBlockStartBreak(itemstack, pos, player);
    }

    private RayTraceResult doRayTrace(IBlockState state, BlockPos pos, EntityPlayer player) {
        Vec3d positionEyes = player.getPositionEyes(1.0F);
        Vec3d playerLook = player.getLook(1.0F);
        double blockReachDistance = player.getAttributeMap().getAttributeInstance(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d maxReach = positionEyes.add(playerLook.x * blockReachDistance, playerLook.y * blockReachDistance, playerLook.z * blockReachDistance);
        RayTraceResult res = state.collisionRayTrace(player.world, pos, playerLook, maxReach);
        //noinspection ConstantConditions - idea thinks it's nonnull due to package level annotations, but it's not
        return res != null ? res : new RayTraceResult(RayTraceResult.Type.MISS, Vec3d.ZERO, EnumFacing.UP, pos);
    }

    private double getDestroyEnergy(ItemStack itemStack, float hardness) {
        return getDestroyEnergy(getDestroyEnergy(itemStack), hardness);
    }

    private double getDestroyEnergy(double baseDestroyEnergy, float hardness) {
        return hardness == 0 ? baseDestroyEnergy / (2) : baseDestroyEnergy;
    }

    private double getDestroyEnergy(ItemStack itemStack) {
        return MekanismConfig.current().general.disassemblerEnergyUsage.val() * (getMode(itemStack).getEfficiency());
    }

    @Override
    public @NotNull RadialData<DisassemblerMode> getRadialData(ItemStack stack) {
        return LAZY_RADIAL_DATA.get();
    }

    @Override
    public DisassemblerMode getMode(ItemStack itemStack) {
        return DisassemblerMode.byIndexStatic(ItemDataUtils.getInt(itemStack, NBTConstants.MODE));
    }

    @Override
    public String getModeSaveKey() {
        return "";
    }

    @Override
    public DisassemblerMode getModeByIndex(int ordinal) {
        return DisassemblerMode.byIndexStatic(ordinal);
    }

    @Override
    public void setMode(ItemStack stack, EntityPlayer player, DisassemblerMode mode) {
        ItemDataUtils.setInt(stack, NBTConstants.MODE, mode.ordinal());
    }


    @Nonnull
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        if (slot == EntityEquipmentSlot.MAINHAND) {
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
            double energy = energyContainer == null ? 0 : energyContainer.getEnergy();
            double energyCost = MekanismConfig.current().general.disassemblerEnergyUsageWeapon.val();
            if (energy >= energyCost) {
                Multimap<String, AttributeModifier> builder = HashMultimap.create();
                builder.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", MekanismConfig.current().general.disassemblerDamageMax.val(), 0));
                builder.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", MekanismConfig.current().general.disassemblerAttackSpeed.val(), 0));
                return builder;
            }
            //If we don't have enough power use it at a reduced power level
            int minDamage = MekanismConfig.current().general.disassemblerDamageMin.val();
            int damageDifference = MekanismConfig.current().general.disassemblerDamageMax.val() - minDamage;
            double damage = minDamage + damageDifference * energy / (energyCost);
            Multimap<String, AttributeModifier> builder = HashMultimap.create();
            builder.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", damage, 0));
            builder.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", MekanismConfig.current().general.disassemblerAttackSpeed.val(), 0));
            return builder;
        }
        return super.getAttributeModifiers(slot, stack);
    }

    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        DisassemblerMode mode = getMode(stack);
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.mode") + ": " + EnumColor.INDIGO + mode.getModeName());
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.efficiency") + ": " + EnumColor.INDIGO + mode.getEfficiency());
    }


    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        DisassemblerMode mode = getMode(stack);
        DisassemblerMode newMode = mode.adjust(shift);
        if (mode != newMode) {
            setMode(stack, player, newMode);
            displayChange.sendMessage(player, () -> new TextComponentString(EnumColor.GREY + LangUtils.localize("tooltip.modeToggle") + " " + EnumColor.INDIGO + newMode.getModeName() + EnumColor.AQUA + " (" + newMode.getEfficiency() + ")"));
        }
    }

    @Override
    public double getEnergyTransfer(ItemStack itemStack) {
        return MekanismConfig.current().general.disassemblerChargeRate.val();
    }

    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        return getMode(stack).getTextComponent();
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

    public enum DisassemblerMode implements IDisableableEnum<DisassemblerMode>, IHasTextComponent, IRadialMode {
        NORMAL("normal", 20, () -> true, EnumColor.BRIGHT_GREEN, ExcavationMode.NORMAL.icon()),
        SLOW("slow", 8, () -> MekanismConfig.current().general.disassemblerSlowMode.val(), EnumColor.BRIGHT_GREEN, ExcavationMode.SLOW.icon()),
        FAST("fast", 128, () -> MekanismConfig.current().general.disassemblerFastMode.val(), EnumColor.BRIGHT_GREEN, ExcavationMode.EXTREME.icon()),
        VEIN("vein", 20, () -> MekanismConfig.current().general.disassemblerVeinMining.val(), EnumColor.BRIGHT_GREEN, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_RADIAL, "vein_normal.png")),
        OFF("off", 0, () -> true, EnumColor.BRIGHT_GREEN, ExcavationMode.OFF.icon());

        private static final DisassemblerMode[] MODES = values();

        private final BooleanSupplier checkEnabled;
        private final String mode;
        private final int efficiency;
        private final EnumColor color;
        private final ResourceLocation icon;

        DisassemblerMode(String mode, int efficiency, BooleanSupplier checkEnabled, EnumColor color, ResourceLocation icon) {
            this.mode = mode;
            this.efficiency = efficiency;
            this.checkEnabled = checkEnabled;
            this.color = color;
            this.icon = icon;
        }

        /**
         * Gets a Mode from its ordinal. NOTE: if this mode is not enabled then it will reset to NORMAL
         */
        public static DisassemblerMode byIndexStatic(int index) {
            DisassemblerMode mode = MathUtils.getByIndexMod(MODES, index);
            return mode.isEnabled() ? mode : NORMAL;
        }

        @Nonnull
        @Override
        public DisassemblerMode byIndex(int index) {
            //Note: We can't just use byIndexStatic, as we want to be able to return disabled modes
            return MathUtils.getByIndexMod(MODES, index);
        }

        public String getModeName() {
            return LangUtils.localize("mekanism.tooltip.disassembler." + mode);
        }

        @Override
        public ITextComponent sliceName() {
            return getTextComponent();
        }

        public int getEfficiency() {
            return efficiency;
        }

        @Override
        public boolean isEnabled() {
            return checkEnabled.getAsBoolean();
        }

        @NotNull
        @Override
        public ResourceLocation icon() {
            return icon;
        }

        @Override
        public EnumColor color() {
            return color;
        }

        @Override
        public ITextComponent getTextComponent() {
            return new TextComponentGroup(color.textFormatting).translation("mekanism.tooltip.disassembler." + mode);
        }
    }


}
