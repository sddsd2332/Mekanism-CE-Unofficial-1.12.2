package mekanism.common.block.states;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.block.BlockBasic;
import mekanism.api.tier.BaseTier;
import mekanism.common.tile.*;
import mekanism.common.util.LangUtils;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Plane;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public class BlockStateBasic extends ExtendedBlockState {

    public static final PropertyBool activeProperty = PropertyBool.create("active");
    public static final PropertyEnum<BaseTier> tierProperty = PropertyEnum.create("tier", BaseTier.class);

    public BlockStateBasic(BlockBasic block, PropertyEnum<BasicBlockType> typeProperty) {
        super(block, new IProperty[]{BlockStateFacing.facingProperty, typeProperty, activeProperty, tierProperty}, new IUnlistedProperty[]{});
    }

    public enum BasicBlock {
        BASIC_BLOCK_1,
        BASIC_BLOCK_2,
        BASIC_BLOCK_3;

        private PropertyEnum<BasicBlockType> predicatedProperty;

        public PropertyEnum<BasicBlockType> getProperty() {
            if (predicatedProperty == null) {
                predicatedProperty = PropertyEnum.create("type", BasicBlockType.class, it -> Objects.requireNonNull(it).blockType == this);
            }
            return predicatedProperty;
        }

        public Block getBlock() {
            switch (this) {
                case BASIC_BLOCK_1:
                    return MekanismBlocks.BasicBlock;
                case BASIC_BLOCK_2:
                    return MekanismBlocks.BasicBlock2;
                case BASIC_BLOCK_3:
                    return MekanismBlocks.BasicBlock3;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public enum BasicBlockType implements IStringSerializable {
        OSMIUM_BLOCK(BasicBlock.BASIC_BLOCK_1, 0, "OsmiumBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        BRONZE_BLOCK(BasicBlock.BASIC_BLOCK_1, 1, "BronzeBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        REFINED_OBSIDIAN(BasicBlock.BASIC_BLOCK_1, 2, "RefinedObsidian", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        COAL_BLOCK(BasicBlock.BASIC_BLOCK_1, 3, "CharcoalBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, false),
        REFINED_GLOWSTONE(BasicBlock.BASIC_BLOCK_1, 4, "RefinedGlowstone", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        STEEL_BLOCK(BasicBlock.BASIC_BLOCK_1, 5, "SteelBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        BIN(BasicBlock.BASIC_BLOCK_1, 6, "Bin", TileEntityBin::new, true, Plane.HORIZONTAL, true, true, false, true),
        TELEPORTER_FRAME(BasicBlock.BASIC_BLOCK_1, 7, "TeleporterFrame", null, true, BlockStateUtils.NO_ROTATION, false, false, false),
        STEEL_CASING(BasicBlock.BASIC_BLOCK_1, 8, "SteelCasing", null, true, BlockStateUtils.NO_ROTATION, false, false, false),
        DYNAMIC_TANK(BasicBlock.BASIC_BLOCK_1, 9, "DynamicTank", TileEntityDynamicTank::new, true, BlockStateUtils.NO_ROTATION, false, false, false),
        STRUCTURAL_GLASS(BasicBlock.BASIC_BLOCK_1, 10, "StructuralGlass", TileEntityStructuralGlass::new, true, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        DYNAMIC_VALVE(BasicBlock.BASIC_BLOCK_1, 11, "DynamicValve", TileEntityDynamicValve::new, true, BlockStateUtils.NO_ROTATION, false, false, false, true),
        COPPER_BLOCK(BasicBlock.BASIC_BLOCK_1, 12, "CopperBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        TIN_BLOCK(BasicBlock.BASIC_BLOCK_1, 13, "TinBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        THERMAL_EVAPORATION_CONTROLLER(BasicBlock.BASIC_BLOCK_1, 14, "ThermalEvaporationController", TileEntityThermalEvaporationController::new, true, Plane.HORIZONTAL, true, false, false),
        THERMAL_EVAPORATION_VALVE(BasicBlock.BASIC_BLOCK_1, 15, "ThermalEvaporationValve", TileEntityThermalEvaporationValve::new, true, BlockStateUtils.NO_ROTATION, false, false, false, true),

        THERMAL_EVAPORATION_BLOCK(BasicBlock.BASIC_BLOCK_2, 0, "ThermalEvaporationBlock", TileEntityThermalEvaporationBlock::new, true, BlockStateUtils.NO_ROTATION, false, false, false),
        INDUCTION_CASING(BasicBlock.BASIC_BLOCK_2, 1, "InductionCasing", TileEntityInductionCasing::new, true, BlockStateUtils.NO_ROTATION, false, false, false),
        INDUCTION_PORT(BasicBlock.BASIC_BLOCK_2, 2, "InductionPort", TileEntityInductionPort::new, true, BlockStateUtils.NO_ROTATION, true, false, false, true),
        INDUCTION_CELL(BasicBlock.BASIC_BLOCK_2, 3, "InductionCell", TileEntityInductionCell::new, true, BlockStateUtils.NO_ROTATION, false, true, false),
        INDUCTION_PROVIDER(BasicBlock.BASIC_BLOCK_2, 4, "InductionProvider", TileEntityInductionProvider::new, true, BlockStateUtils.NO_ROTATION, false, true, false),
        SUPERHEATING_ELEMENT(BasicBlock.BASIC_BLOCK_2, 5, "SuperheatingElement", TileEntitySuperheatingElement::new, true, BlockStateUtils.NO_ROTATION, false, false, false),
        PRESSURE_DISPERSER(BasicBlock.BASIC_BLOCK_2, 6, "PressureDisperser", TileEntityPressureDisperser::new, true, BlockStateUtils.NO_ROTATION, false, false, false),
        BOILER_CASING(BasicBlock.BASIC_BLOCK_2, 7, "BoilerCasing", TileEntityBoilerCasing::new, true, BlockStateUtils.NO_ROTATION, false, false, false),
        BOILER_VALVE(BasicBlock.BASIC_BLOCK_2, 8, "BoilerValve", TileEntityBoilerValve::new, true, BlockStateUtils.NO_ROTATION, false, false, false, true),
        SECURITY_DESK(BasicBlock.BASIC_BLOCK_2, 9, "SecurityDesk", TileEntitySecurityDesk::new, true, Plane.HORIZONTAL, false, false, false, false, false, false),
        URANIUM_BLOCK(BasicBlock.BASIC_BLOCK_2, 10, "UraniumBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),
        lEAD_BLOCK(BasicBlock.BASIC_BLOCK_2, 11, "LeadBlock", null, false, BlockStateUtils.NO_ROTATION, false, false, true),

        SPS_CASING(BasicBlock.BASIC_BLOCK_3, 0, "SpsCasing", null, false, BlockStateUtils.NO_ROTATION, false, false, false),
        FISSION_REACHER_CASING(BasicBlock.BASIC_BLOCK_3, 1, "FissionReacherCasing", null, false, BlockStateUtils.NO_ROTATION, false, false, false),
        CONTROL_ROD_ASSEMBLY(BasicBlock.BASIC_BLOCK_3, 2, "ControlRodAssembly", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        FISSION_FUEL_ASSEMBLY(BasicBlock.BASIC_BLOCK_3, 3, "FissionFuelAssembly", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        BASIC_ENERGY_CUBE_CRAFT(BasicBlock.BASIC_BLOCK_3, 4, "BasicEnergyCubeCraft", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        ADVANCED_ENERGY_CUBE_CRAFT(BasicBlock.BASIC_BLOCK_3, 5, "AdvancedEnergyCubeCraft", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        ELITE_ENERGY_CUBE_CRAFT(BasicBlock.BASIC_BLOCK_3, 6, "EliteEnergyCubeCraft", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        ULTIMATE_ENERGY_CUBE_CRAFT(BasicBlock.BASIC_BLOCK_3, 7, "UltimateEnergyCubeCraft", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        CREATIVE_ENERGY_CUBE_CRAFT(BasicBlock.BASIC_BLOCK_3, 8, "CreativeEnergyCubeCraft", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        BASIC_ENERGY_CUBE_FRAME(BasicBlock.BASIC_BLOCK_3, 9, "BasicEnergyCubeFrame", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        ADVANCED_ENERGY_CUBE_FRAME(BasicBlock.BASIC_BLOCK_3, 10, "AdvancedEnergyCubeFrame", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        ELITE_ENERGY_CUBE_FRAME(BasicBlock.BASIC_BLOCK_3, 11, "EliteEnergyCubeFrame", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        ULTIMATE_ENERGY_CUBE_FRAME(BasicBlock.BASIC_BLOCK_3, 12, "UltimateEnergyCubeFrame", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false),
        CREATIVE_ENERGY_CUBE_FRAME(BasicBlock.BASIC_BLOCK_3, 13, "CreativeEnergyCubeFrame", null, false, BlockStateUtils.NO_ROTATION, false, false, false, false, false, false);

        @Nonnull
        public BasicBlock blockType;
        public int meta;
        public String name;
        public Supplier<TileEntity> tileEntitySupplier;
        public boolean hasDescription;
        public Predicate<EnumFacing> facingPredicate;
        public boolean activable;
        public boolean tiers;
        public boolean isBeaconBase;
        public boolean hasRedstoneOutput;
        public boolean isFullBlock;
        public boolean isOpaqueCube;

        BasicBlockType(@Nonnull BasicBlock block, int metadata, String nameIn, Supplier<TileEntity> tileClass, boolean hasDesc, Predicate<EnumFacing> facingAllowed,
                       boolean activeState, boolean hasTiers, boolean beaconBase) {
            this(block, metadata, nameIn, tileClass, hasDesc, facingAllowed, activeState, hasTiers, beaconBase, false);
        }

        BasicBlockType(@Nonnull BasicBlock block, int metadata, String nameIn, Supplier<TileEntity> tileClass, boolean hasDesc, Predicate<EnumFacing> facingAllowed,
                       boolean activeState, boolean hasTiers, boolean beaconBase, boolean hasRedstoneOutput) {
            this(block, metadata, nameIn, tileClass, hasDesc, facingAllowed, activeState, hasTiers, beaconBase, hasRedstoneOutput, true);
        }

        BasicBlockType(@Nonnull BasicBlock block, int metadata, String nameIn, Supplier<TileEntity> tileClass, boolean hasDesc, Predicate<EnumFacing> facingAllowed,
                       boolean activeState, boolean hasTiers, boolean beaconBase, boolean hasRedstoneOutput, boolean fullBlock) {
            this(block, metadata, nameIn, tileClass, hasDesc, facingAllowed, activeState, hasTiers, beaconBase, hasRedstoneOutput, fullBlock, true);
        }

        BasicBlockType(@Nonnull BasicBlock block, int metadata, String nameIn, Supplier<TileEntity> tileClass, boolean hasDesc, Predicate<EnumFacing> facingAllowed,
                       boolean activeState, boolean hasTiers, boolean beaconBase, boolean hasRedstoneOutput, boolean fullBlock, boolean opaque) {
            blockType = block;
            meta = metadata;
            name = nameIn;
            tileEntitySupplier = tileClass;
            hasDescription = hasDesc;
            facingPredicate = facingAllowed;
            activable = activeState;
            tiers = hasTiers;
            isBeaconBase = beaconBase;
            this.hasRedstoneOutput = hasRedstoneOutput;
            isFullBlock = fullBlock;
            isOpaqueCube = opaque;
        }

        @Nullable public static BasicBlockType get(IBlockState state) {
            if (state.getBlock() instanceof BlockBasic) {
                return state.getValue(((BlockBasic) state.getBlock()).getTypeProperty());
            }
            return null;
        }

        @Nullable public static BasicBlockType get(ItemStack stack) {
            return get(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
        }

        @Nullable public static BasicBlockType get(Block block, int meta) {
            if (block instanceof BlockBasic) {
                return get(((BlockBasic) block).getBasicBlock(), meta);
            }
            return null;
        }

        @Nullable public static BasicBlockType get(BasicBlock blockType, int metadata) {
            int index = blockType.ordinal() << 4 | metadata;
            if (index < values().length) {
                BasicBlockType firstTry = values()[index];
                if (firstTry.blockType == blockType && firstTry.meta == metadata) {
                    return firstTry;
                }
            }
            for (BasicBlockType type : values()) {
                if (type.blockType == blockType && type.meta == metadata) {
                    return type;
                }
            }
            Mekanism.logger.error("Invalid BasicBlock. type: {}, meta: {}", blockType.ordinal(), metadata);
            return null;
        }

        public TileEntity create() {
            return this.tileEntitySupplier != null ? this.tileEntitySupplier.get() : null;
        }

        @Override
        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String getDescription() {
            return LangUtils.localize("tooltip." + name);
        }

        public ItemStack getStack(int amount) {
            return new ItemStack(blockType.getBlock(), amount, meta);
        }

        public boolean canRotateTo(EnumFacing side) {
            return facingPredicate.test(side);
        }

        public boolean hasRotations() {
            return !facingPredicate.equals(BlockStateUtils.NO_ROTATION);
        }

        public boolean hasActiveTexture() {
            return activable;
        }
    }

    public static class BasicBlockStateMapper extends StateMapperBase {

        @Nonnull
        @Override
        protected ModelResourceLocation getModelResourceLocation(@Nonnull IBlockState state) {
            BlockBasic block = (BlockBasic) state.getBlock();
            BasicBlockType type = state.getValue(block.getTypeProperty());
            StringBuilder builder = new StringBuilder();
            String nameOverride = null;

            if (type.hasActiveTexture()) {
                builder.append(activeProperty.getName());
                builder.append("=");
                builder.append(state.getValue(activeProperty));
            }

            if (type.hasRotations()) {
                EnumFacing facing = state.getValue(BlockStateFacing.facingProperty);

                if (!type.canRotateTo(facing)) {
                    facing = EnumFacing.NORTH;
                }

                if (builder.length() > 0) {
                    builder.append(",");
                }

                builder.append(BlockStateFacing.facingProperty.getName());
                builder.append("=");
                builder.append(facing.getName());
            }

            if (type.tiers) {
                BaseTier tier = state.getValue(tierProperty);
                if (tier == BaseTier.CREATIVE && type != BasicBlockType.BIN) {
                    tier = BaseTier.ULTIMATE;
                }
                nameOverride = type.getName() + "_" + tier.getName();
            }

            if (builder.length() == 0) {
                builder.append("normal");
            }
            ResourceLocation baseLocation = new ResourceLocation(Mekanism.MODID, nameOverride != null ? nameOverride : type.getName());
            return new ModelResourceLocation(baseLocation, builder.toString());
        }
    }
}
