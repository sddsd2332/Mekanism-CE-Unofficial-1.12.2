package mekanism.common.item;

import buildcraft.api.tools.IToolWrench;
import cofh.api.item.IToolHammer;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.*;
import mekanism.api.annotations.FieldsAreNonnullByDefault;
import mekanism.api.math.MathUtils;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.lib.radial.IRadialEnumModeItem;
import mekanism.common.lib.radial.data.RadialDataHelper;
import mekanism.common.tier.BinTier;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.TileEntityBin;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.tile.TileEntityGasTank;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@InterfaceList({
        @Interface(iface = "buildcraft.api.tools.IToolWrench", modid = MekanismHooks.BUILDCRAFT_MOD_ID),
        @Interface(iface = "cofh.api.item.IToolHammer", modid = MekanismHooks.COFH_API_MOD_ID)
})
public class ItemConfigurator extends ItemEnergized implements IMekWrench, IToolWrench, IRadialEnumModeItem<ConfiguratorMode>, IToolHammer, IItemHUDProvider {

    public static final Lazy<RadialData<ConfiguratorMode>> LAZY_RADIAL_DATA = Lazy.of(() -> RadialDataHelper.INSTANCE.dataForEnum(Mekanism.rl("configurator_mode"), ConfiguratorMode.class));

    public final int ENERGY_PER_CONFIGURE = 400;
    public final int ENERGY_PER_ITEM_DUMP = 8;

    public ItemConfigurator() {
        super(60000);
        setRarity(EnumRarity.UNCOMMON);
        setMaxStackSize(1);
        this.addPropertyOverride(new ResourceLocation("mode"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                if (stack.getItem() instanceof ItemConfigurator) {
                    switch (getMode(stack)) {
                        case EMPTY -> {
                            return 1.0F;
                        }
                        case ROTATE -> {
                            return 2.0F;
                        }
                        case WRENCH -> {
                            return 3.0F;
                        }
                    }
                }
                return 0.0F;
            }
        });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        super.addInformation(itemstack, world, list, flag);
        list.add(EnumColor.PINK + LangUtils.localize("gui.state") + ": " + getColor(getMode(itemstack)) + getStateDisplay(getMode(itemstack)));
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            Block block = world.getBlockState(pos).getBlock();
            TileEntity tile = world.getTileEntity(pos);

            if (getMode(stack).isConfigurating()) { //Configurate
                TransmissionType transmissionType = Objects.requireNonNull(getMode(stack).getTransmission(), "Configurating state requires transmission type");
                if (tile instanceof ISideConfiguration configuration && configuration.getConfig().supports(transmissionType)) {
                    DataType initial = configuration.getConfig().getDataType(transmissionType, side, configuration.getOrientation());
                    if (initial != DataType.EMPTY) {
                        if (!player.isSneaking()) {
                            player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " " + getViewModeText(
                                    transmissionType) + ": " + initial.getColor() + initial.localize() + " (" + initial.getColor().getColoredName() + ")"));
                        } else {
                            if (StorageUtils.getStoredEnergy(stack) >= ENERGY_PER_CONFIGURE) {
                                if (SecurityUtils.canAccess(player, tile)) {
                                    StorageUtils.extractEnergy(stack, ENERGY_PER_CONFIGURE, Action.EXECUTE);
                                    MekanismUtils.incrementOutput(configuration, transmissionType, MekanismUtils.getBaseOrientation(side, configuration.getOrientation()));
                                    DataType data = configuration.getConfig().getDataType(transmissionType, side, configuration.getOrientation());
                                    player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " "
                                            + getToggleModeText(transmissionType) + ": " + data.getColor() + data.localize() + " (" +
                                            data.getColor().getColoredName() + ")"));
                                    if (configuration instanceof TileEntityBasicBlock basicBlock) {
                                        Mekanism.packetHandler.sendUpdatePacket(basicBlock);
                                    }
                                } else {
                                    SecurityUtils.displayNoAccess(player);
                                }
                            }
                        }
                    }
                    return EnumActionResult.SUCCESS;
                } else if (CapabilityUtils.hasCapability(tile, Capabilities.CONFIGURABLE_CAPABILITY, side)) {
                    IConfigurable config = CapabilityUtils.getCapability(tile, Capabilities.CONFIGURABLE_CAPABILITY, side);
                    if (SecurityUtils.canAccess(player, tile)) {
                        if (player.isSneaking()) {
                            return config.onSneakRightClick(player, side);
                        } else {
                            return config.onRightClick(player, side);
                        }
                    } else {
                        SecurityUtils.displayNoAccess(player);
                        return EnumActionResult.SUCCESS;
                    }
                }
            } else if (getMode(stack) == ConfiguratorMode.EMPTY) { //Empty
                if (tile instanceof TileEntityFluidTank tank) {
                    if (MekanismConfig.current().mekce.EmptytoCreateFluidTank.val()) {
                        if (SecurityUtils.canAccess(player, tile)) {
                            if (tank.tier == FluidTankTier.CREATIVE && tank.fluidTank.getFluid() != null && StorageUtils.getStoredEnergy(stack) >= ENERGY_PER_CONFIGURE) {
                                StorageUtils.extractEnergy(stack, ENERGY_PER_CONFIGURE, Action.EXECUTE);
                                tank.fluidTank.setEmpty();
                            }
                            return EnumActionResult.SUCCESS;
                        } else {
                            SecurityUtils.displayNoAccess(player);
                            return EnumActionResult.FAIL;
                        }
                    }
                } else if (tile instanceof TileEntityGasTank tank) {
                    if (MekanismConfig.current().mekce.EmptyToCreateGasTank.val()) {
                        if (SecurityUtils.canAccess(player, tile)) {
                            if (tank.tier == GasTankTier.CREATIVE && tank.gasTank.getGas() != null && StorageUtils.getStoredEnergy(stack) >= ENERGY_PER_CONFIGURE) {
                                StorageUtils.extractEnergy(stack, ENERGY_PER_CONFIGURE, Action.EXECUTE);
                                tank.gasTank.setEmpty();
                            }
                            return EnumActionResult.SUCCESS;
                        } else {
                            SecurityUtils.displayNoAccess(player);
                            return EnumActionResult.FAIL;
                        }
                    }
                } else if (tile instanceof TileEntityContainerBlock) {
                    if (SecurityUtils.canAccess(player, tile)) {
                        //TODO: Switch this to items being handled by TECB, energy handled here (via lambdas?)
                        IInventory inv = (IInventory) tile;
                        for (int i = 0; i < inv.getSizeInventory(); i++) {
                            ItemStack slotStack = inv.getStackInSlot(i);
                            if (!slotStack.isEmpty()) {
                                if (StorageUtils.getStoredEnergy(stack) < ENERGY_PER_ITEM_DUMP) {
                                    break;
                                }
                                Block.spawnAsEntity(world, pos, slotStack.copy());
                                inv.setInventorySlotContents(i, ItemStack.EMPTY);
                                StorageUtils.extractEnergy(stack, ENERGY_PER_ITEM_DUMP, Action.EXECUTE);
                            }
                        }
                        return EnumActionResult.SUCCESS;
                    } else {
                        SecurityUtils.displayNoAccess(player);
                        return EnumActionResult.FAIL;
                    }
                } else if (tile instanceof TileEntityBin bin) {
                    if (MekanismConfig.current().mekce.EmptyToCreateBin.val()) {
                        if (SecurityUtils.canAccess(player, tile)) {
                            if (bin.tier == BinTier.CREATIVE) {
                                IInventory inv = (IInventory) tile;
                                for (int i = 0; i < inv.getSizeInventory(); i++) {
                                    ItemStack slotStack = inv.getStackInSlot(i);
                                    if (!slotStack.isEmpty()) {
                                        if (StorageUtils.getStoredEnergy(stack) < ENERGY_PER_ITEM_DUMP) {
                                            break;
                                        }
                                        Block.spawnAsEntity(world, pos, bin.bottomStack.copy());
                                        inv.setInventorySlotContents(i, ItemStack.EMPTY);
                                        bin.setItemCount(0);
                                        StorageUtils.extractEnergy(stack, ENERGY_PER_ITEM_DUMP, Action.EXECUTE);
                                    }
                                }
                            }
                            return EnumActionResult.SUCCESS;
                        } else {
                            SecurityUtils.displayNoAccess(player);
                            return EnumActionResult.FAIL;
                        }
                    }
                }
            } else if (getMode(stack) == ConfiguratorMode.ROTATE) { //Rotate
                EnumFacing[] rotations = block.getValidRotations(world, pos);
                if (rotations != null && rotations.length > 0) {
                    List<EnumFacing> l = Arrays.asList(block.getValidRotations(world, pos));
                    if (!player.isSneaking() && l.contains(side)) {
                        block.rotateBlock(world, pos, side);
                    } else if (player.isSneaking() && l.contains(side.getOpposite())) {
                        block.rotateBlock(world, pos, side.getOpposite());
                    }
                }
                return EnumActionResult.SUCCESS;
            } else if (getMode(stack) == ConfiguratorMode.WRENCH && MekanismConfig.current().mekce.EnableConfiguratorWrench.val()) { //Wrench
                return EnumActionResult.PASS;
            }
        }
        return EnumActionResult.PASS;
    }

    public String getViewModeText(TransmissionType type) {
        String base = LangUtils.localize("tooltip.configurator.viewMode");
        return String.format(base, type.localize().toLowerCase(Locale.ROOT));
    }

    public String getToggleModeText(TransmissionType type) {
        String base = LangUtils.localize("tooltip.configurator.toggleMode");
        return String.format(base, type.localize());
    }

    public String getStateDisplay(ConfiguratorMode mode) {
        return mode.getName();
    }

    public EnumColor getColor(ConfiguratorMode mode) {
        return mode.color();
    }


    @Override
    public String getModeSaveKey() {
        return NBTConstants.STATE;
    }

    @Override
    public ConfiguratorMode getModeByIndex(int ordinal) {
        return ConfiguratorMode.byIndexStatic(ordinal);
    }

    @Override
    public @NotNull RadialData<ConfiguratorMode> getRadialData(ItemStack stack) {
        return LAZY_RADIAL_DATA.get();
    }

    public ConfiguratorMode getMode(ItemStack stack) {
        return ConfiguratorMode.byIndexStatic(ItemDataUtils.getInt(stack, NBTConstants.STATE));
    }

    @Override
    public void setMode(ItemStack stack, EntityPlayer player, ConfiguratorMode mode) {
        ItemDataUtils.setInt(stack, NBTConstants.STATE, mode.ordinal());
    }

    @Override
    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.BUILDCRAFT_MOD_ID)
    public boolean canWrench(EntityPlayer player, EnumHand hand, ItemStack wrench, RayTraceResult rayTrace) {
        return canUseWrench(wrench, player, rayTrace.getBlockPos());
    }

    @Override
    @Method(modid = MekanismHooks.BUILDCRAFT_MOD_ID)
    public void wrenchUsed(EntityPlayer player, EnumHand hand, ItemStack wrench, RayTraceResult rayTrace) {
    }

    @Override
    public boolean canUseWrench(ItemStack stack, EntityPlayer player, BlockPos pos) {
        return getMode(stack) == ConfiguratorMode.WRENCH && MekanismConfig.current().mekce.EnableConfiguratorWrench.val();
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return getMode(stack) == ConfiguratorMode.WRENCH && MekanismConfig.current().mekce.EnableConfiguratorWrench.val();
    }

    /*cofh IToolHammer */
    @Override
    public boolean isUsable(ItemStack stack, EntityLivingBase user, BlockPos pos) {
        return getMode(stack) == ConfiguratorMode.WRENCH && MekanismConfig.current().mekce.EnableConfiguratorWrench.val();
    }

    @Override
    public boolean isUsable(ItemStack stack, EntityLivingBase user, Entity entity) {
        return getMode(stack) == ConfiguratorMode.WRENCH && MekanismConfig.current().mekce.EnableConfiguratorWrench.val();
    }

    @Override
    public void toolUsed(ItemStack item, EntityLivingBase user, BlockPos pos) {
    }

    @Override
    public void toolUsed(ItemStack item, EntityLivingBase user, Entity entity) {
    }
    /*end cofh IToolHammer */


    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        list.add(EnumColor.PINK + LangUtils.localize("tooltip.mode") + ": " + getColor(getMode(stack)) + getStateDisplay(getMode(stack)));
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        ConfiguratorMode mode = getMode(stack);
        ConfiguratorMode newMode = mode.adjust(shift);
        if (mode != newMode) {
            setMode(stack, player, newMode);
            displayChange.sendMessage(player, () -> new TextComponentGroup().translation("mekanism.tooltip.configureState", LangUtils.withColor(newMode.getTextComponent(), newMode.color.textFormatting)));
        }
    }

    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        return getMode(stack).getTextComponent();
    }

    /*
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        return super.getItemStackDisplayName(itemstack) + " " + LangUtils.localize("tooltip.mode") + ": " + getColor(getMode(itemstack)) + getStateDisplay(getMode(itemstack));
    }

     */

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @FieldsAreNonnullByDefault
    public enum ConfiguratorMode implements IIncrementalEnum<ConfiguratorMode>, IHasTextComponent, IRadialMode {
        CONFIGURATE_ITEMS("configurate", TransmissionType.ITEM, EnumColor.BRIGHT_GREEN, true, null),
        CONFIGURATE_FLUIDS("configurate", TransmissionType.FLUID, EnumColor.BRIGHT_GREEN, true, null),
        CONFIGURATE_GASES("configurate", TransmissionType.GAS, EnumColor.BRIGHT_GREEN, true, null),
        CONFIGURATE_ENERGY("configurate", TransmissionType.ENERGY, EnumColor.BRIGHT_GREEN, true, null),
        CONFIGURATE_HEAT("configurate", TransmissionType.HEAT, EnumColor.BRIGHT_GREEN, true, null),
        EMPTY("empty", null, EnumColor.DARK_RED, false, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "empty.png")),
        ROTATE("rotate", null, EnumColor.YELLOW, false, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "rotate.png")),
        WRENCH("wrench", null, EnumColor.PINK, false, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "wrench.png"));

        private static final ConfiguratorMode[] MODES = values();
        @Nullable
        private final TransmissionType transmissionType;
        private final String name;
        private final EnumColor color;
        private final boolean configurating;
        private final ResourceLocation icon;

        ConfiguratorMode(String s, @Nullable TransmissionType s1, EnumColor c, boolean b, @Nullable ResourceLocation icon) {
            name = s;
            transmissionType = s1;
            color = c;
            configurating = b;
            if (transmissionType != null) {
                this.icon = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, transmissionType.getTransmission().toLowerCase(Locale.ROOT) + ".png");
            } else {
                this.icon = Objects.requireNonNull(icon, "Icon should only be null if there is a transmission type present.");
            }
        }

        public String getName() {
            String name = LangUtils.localize("tooltip.configurator." + this.name);
            if (this.transmissionType != null) {
                name += " (" + transmissionType.localize() + ")";
            }
            return name;
        }


        @Override
        public ITextComponent getTextComponent() {
            TextComponentGroup translation = new TextComponentGroup(color.textFormatting).translation("tooltip.configurator." + name);
            if (this.transmissionType != null) {
                translation.string(" (").translation(transmissionType.getTranslationKey()).string(")");
            }
            return translation;
        }

        @Override
        public @NotNull ITextComponent sliceName() {
            return getTextComponent();
        }

        @Override
        public ResourceLocation icon() {
            return icon;
        }

        @Override
        public EnumColor color() {
            return color;
        }

        public boolean isConfigurating() {
            return configurating;
        }

        @Nullable
        public TransmissionType getTransmission() {
            return switch (this) {
                case CONFIGURATE_ITEMS -> TransmissionType.ITEM;
                case CONFIGURATE_FLUIDS -> TransmissionType.FLUID;
                case CONFIGURATE_GASES -> TransmissionType.GAS;
                case CONFIGURATE_ENERGY -> TransmissionType.ENERGY;
                case CONFIGURATE_HEAT -> TransmissionType.HEAT;
                default -> null;
            };
        }

        @Override
        public ConfiguratorMode byIndex(int index) {
            return byIndexStatic(index);
        }

        public static ConfiguratorMode byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }

    }
}
