package mekanism.common.content.gear.mekatool;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import mekanism.api.EnumColor;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.radial.IRadialDataHelper;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.BasicRadialMode;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.radial.mode.NestedRadialMode;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.block.BlockBounding;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.ItemAtomicDisassembler.DisassemblerMode;
import mekanism.common.lib.radial.data.RadialDataHelper;
import mekanism.common.network.PacketLightningRender.LightningPreset;
import mekanism.common.network.PacketLightningRender.LightningRenderMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;

@ParametersAreNonnullByDefault
public class ModuleVeinMiningUnit implements ICustomModule<ModuleVeinMiningUnit> {


    private static final IRadialDataHelper.BooleanRadialModes RADIAL_MODES = new IRadialDataHelper.BooleanRadialModes(
            new BasicRadialMode(MekanismLang.RADIAL_VEIN_NORMAL, DisassemblerMode.VEIN.icon(), EnumColor.AQUA),
            new BasicRadialMode(MekanismLang.RADIAL_VEIN_EXTENDED, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_RADIAL, "vein_extended.png"), EnumColor.PINK)
    );
    private static final RadialData<IRadialMode> RADIAL_DATA = RadialDataHelper.INSTANCE.booleanBasedData(Mekanism.rl("vein_mining_mode"), RADIAL_MODES);
    private static final NestedRadialMode NESTED_RADIAL_MODE = new NestedRadialMode(RADIAL_DATA, MekanismLang.RADIAL_VEIN, DisassemblerMode.VEIN.icon(), EnumColor.AQUA);

    private IModuleConfigItem<Boolean> extendedMode;
    private IModuleConfigItem<ExcavationRange> excavationRange;

    @Override
    public void init(IModule<ModuleVeinMiningUnit> module, ModuleConfigItemCreator configItemCreator) {
        extendedMode = configItemCreator.createDisableableConfigItem("extended_mode", MekanismLang.MODULE_EXTENDED_MODE, false, MekanismConfig.current().meka.mekaToolExtendedMining);
        excavationRange = configItemCreator.createConfigItem("excavation_range", MekanismLang.MODULE_EXCAVATION_RANGE, new ModuleEnumData<>(ExcavationRange.LOW, module.getInstalledCount() + 1));
    }

    @Override
    public void addRadialModes(IModule<ModuleVeinMiningUnit> module, @NotNull ItemStack stack, Consumer<NestedRadialMode> adder) {
        if (MekanismConfig.current().meka.mekaToolExtendedMining.val()) {
            adder.accept(NESTED_RADIAL_MODE);
        }
    }

    @Nullable
    @Override
    public <MODE extends IRadialMode> MODE getMode(IModule<ModuleVeinMiningUnit> module, ItemStack stack, RadialData<MODE> radialData) {
        if (radialData == RADIAL_DATA && MekanismConfig.current().meka.mekaToolExtendedMining.val()) {
            return (MODE) RADIAL_MODES.get(isExtended());
        }
        return null;
    }

    @Override
    public <MODE extends IRadialMode> boolean setMode(IModule<ModuleVeinMiningUnit> module, EntityPlayer player, ItemStack stack, RadialData<MODE> radialData, MODE mode) {
        if (radialData == RADIAL_DATA && MekanismConfig.current().meka.mekaToolExtendedMining.val()) {
            boolean extended = mode == RADIAL_MODES.trueMode();
            if (isExtended() != extended) {
                extendedMode.set(extended);
            }
        }
        return false;
    }

    @Nullable
    @Override
    public ITextComponent getModeScrollComponent(IModule<ModuleVeinMiningUnit> module, ItemStack stack) {
        if (isExtended()) {
            return MekanismLang.RADIAL_VEIN_EXTENDED.translateColored(EnumColor.PINK);
        }
        return MekanismLang.RADIAL_VEIN_NORMAL.translateColored(EnumColor.AQUA);
    }

    @Override
    public void changeMode(IModule<ModuleVeinMiningUnit> module, EntityPlayer player, ItemStack stack, int shift, boolean displayChangeMessage) {
        if (Math.abs(shift) % 2 == 1) {
            //We are changing by an odd amount, so toggle the mode
            boolean newState = !isExtended();
            extendedMode.set(newState);
            if (displayChangeMessage) {
                player.sendMessage(new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG, TextFormatting.DARK_BLUE).string(" ").translation(MekanismLang.MODULE_MODE_CHANGE.getTranslationKey()).translation(MekanismLang.MODULE_EXTENDED_MODE.getTranslationKey(), LangUtils.onOffColoured(newState)));
            }
        }
    }

    public boolean isExtended() {
        return extendedMode.get();
    }

    public int getExcavationRange() {
        return excavationRange.get().getRange();
    }

    public static boolean canVeinBlock(IBlockState state) {
        //Even though we now handle breaking bounding blocks properly, don't allow vein mining them
        return !(state.getBlock() instanceof BlockBounding);
    }

    public static Object2IntMap<BlockPos> findPositions(World world, Map<BlockPos, IBlockState> initial, int extendedRange, Reference2BooleanMap<Block> oreTracker) {
        Object2IntMap<BlockPos> found = new Object2IntLinkedOpenHashMap<>();
        int maxVein = MekanismConfig.current().general.disassemblerMiningCount.val();
        int maxCount = initial.size() + maxVein * oreTracker.size();
        Map<BlockPos, IBlockState> frontier = new LinkedHashMap<>(initial);
        TraversalDistance dist = new TraversalDistance(frontier.size());
        while (!frontier.isEmpty()) {
            Iterator<Map.Entry<BlockPos, IBlockState>> iterator = frontier.entrySet().iterator();
            Map.Entry<BlockPos, IBlockState> blockEntry = iterator.next();
            iterator.remove();
            BlockPos blockPos = blockEntry.getKey();
            found.put(blockPos, dist.getDistance());
            if (found.size() >= maxCount) {
                break;
            }
            Block block = blockEntry.getValue().getBlock();
            boolean isOre = oreTracker.getBoolean(block);
            //If it is extended or should be treated as an ore
            if (isOre || extendedRange > dist.getDistance()) {
                for (BlockPos nextPos : BlockPos.getAllInBoxMutable(blockPos.add(-1, -1, -1), blockPos.add(1, 1, 1))) {
                    //We can check contains as mutable
                    if (!found.containsKey(nextPos) && !frontier.containsKey(nextPos)) {
                        Optional<IBlockState> nextState = WorldUtils.getBlockState(world, nextPos);
                        if (nextState.isPresent() && nextState.get().getBlock() == block) {
                            //Make sure to add it as immutable
                            BlockPos immutablePos = nextPos.toImmutable();
                            frontier.put(immutablePos, nextState.get());
                            if (!world.isRemote) {
                                Vec3d start = new Vec3d(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
                                Vec3d end = new Vec3d(immutablePos.getX() + 0.5D, immutablePos.getY() + 0.5D, immutablePos.getZ() + 0.5D);
                                Mekanism.packetHandler.sendToAllTracking(new LightningRenderMessage(LightningPreset.TOOL_AOE, Objects.hash(blockPos, immutablePos), start, end, 10),
                                        world.provider.getDimension(), blockPos.getX(), blockPos.getY(), blockPos.getZ());
                            }
                        }
                    }
                }
            }
            dist.updateDistance(found.size(), frontier.size());
        }
        return found;
    }


    @Override
    public void addHUDStrings(IModule<ModuleVeinMiningUnit> module, EntityPlayer player, Consumer<String> hudStringAdder) {
        //Only add hud string for extended vein mining if enabled in config
        if (module.isEnabled() && MekanismConfig.current().meka.mekaToolExtendedMining.getAsBoolean()) {
            hudStringAdder.accept(MekanismLang.MODULE_EXTENDED_ENABLED.translateColored(EnumColor.DARK_GREY).getFormattedText() + (isExtended() ? EnumColor.BRIGHT_GREEN : EnumColor.DARK_RED) + (isExtended() ? MekanismLang.MODULE_ENABLED_LOWER.getTranslationKey() : MekanismLang.MODULE_DISABLED_LOWER.getTranslationKey()));
        }
    }

    public enum ExcavationRange implements IHasTextComponent {
        OFF(0),
        LOW(2),
        MED(4),
        HIGH(6),
        EXTREME(8);

        private final int range;
        private final ITextComponent label;

        ExcavationRange(int range) {
            this.range = range;
            this.label = new TextComponentGroup().getString(Integer.toString(range));
        }

        @Override
        public ITextComponent getTextComponent() {
            return label;
        }

        public int getRange() {
            return range;
        }
    }

    // Helper class to help calculate the breadth first traversal path distance
    private static class TraversalDistance {

        // Start at distance 0
        private int distance = 0;
        private int next;

        // Initialize with the number of elements at distance 0
        public TraversalDistance(int next) {
            this.next = next;
        }

        // When all elements at distance 0 are found, determine how many elements there are with distance 1 and increment distance
        public void updateDistance(int found, int frontierSize) {
            if (found == next) {
                distance++;
                next += frontierSize;
            }
        }

        public int getDistance() {
            return distance;
        }
    }
}

