package mekanism.common.content.miner;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mekanism.api.Chunk3D;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.IFluidBlock;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadMinerSearch extends Thread {

    private final TileEntityDigitalMiner tileEntity;
    private final Map<Chunk3D, BitSet> oresToMine = new HashMap<>();
    private final Int2ObjectOpenHashMap<MinerFilter> replaceMap = new Int2ObjectOpenHashMap<>();

    private volatile ChunkCache chunkCache;
    private volatile SearchConfig searchConfig;
    private volatile boolean cancelled;

    public volatile State state = State.IDLE;
    public volatile int found;

    public ThreadMinerSearch(TileEntityDigitalMiner tile) {
        super("Mekanism Digital Miner Search " + tile.getPos());
        tileEntity = tile;
        setDaemon(true);
    }

    /**
     * Captures all mutable miner settings and builds the read-only region cache on the server thread.
     */
    public boolean prepare() {
        if (state != State.IDLE || !(tileEntity.getWorld() instanceof WorldServer world)) {
            return false;
        }
        SearchConfig config = SearchConfig.capture(tileEntity, world);
        searchConfig = config;
        if (config.valid && config.requiresScan()) {
            BlockPos start = new BlockPos(config.startX, config.startY, config.startZ);
            chunkCache = new MinerChunkCache(world, start, start.add(config.diameter, config.height, config.diameter), 0);
        }
        state = State.SEARCHING;
        return true;
    }

    /**
     * Legacy setup entry point retained for addons that construct the region cache themselves.
     */
    @Deprecated
    public void setChunkCache(ChunkCache chunkCache) {
        if (state == State.IDLE && tileEntity.getWorld() instanceof WorldServer world) {
            searchConfig = SearchConfig.capture(tileEntity, world);
            this.chunkCache = chunkCache;
            state = State.SEARCHING;
        }
    }

    @Override
    public void run() {
        SearchConfig config = searchConfig;
        ChunkCache cache = chunkCache;
        if (config == null || isCancelled()) {
            return;
        }
        if (!config.valid) {
            Mekanism.logger.error("Digital Miner search region at {} is too large or invalid; aborting search.", config.minerPos);
            publishResults(config);
            return;
        }
        if (!config.requiresScan() || cache == null || cache instanceof MinerChunkCache minerCache && minerCache.isRegionEmpty()) {
            publishResults(config);
            return;
        }

        StateMatchCache stateMatches = new StateMatchCache(config.inverse, config.inverseReplaceTarget, config.filters);
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();
        int foundCount = 0;
        for (int index = 0; index < config.size; index++) {
            if ((index & 0xFF) == 0) {
                found = foundCount;
            }
            if (isCancelled() || tileEntity.isInvalid()) {
                return;
            }

            int x = config.startX + index % config.diameter;
            int z = config.startZ + (index / config.diameter) % config.diameter;
            int y = config.startY + index / config.diameter / config.diameter;
            if (config.minerPos.getX() == x && config.minerPos.getY() == y && config.minerPos.getZ() == z) {
                continue;
            }

            testPos.setPos(x, y, z);
            IBlockState blockState = cache.getBlockState(testPos);
            Block block = blockState.getBlock();
            if (block == MekanismBlocks.BoundingBlock || block instanceof BlockLiquid || block instanceof IFluidBlock ||
                  block.isAir(blockState, cache, testPos)) {
                continue;
            }

            StateMatch match = stateMatches.get(Block.getStateId(blockState), block, block.getMetaFromState(blockState));
            if (match.replacement) {
                continue;
            }
            if (config.inverse == (match.filter == null)) {
                set(index, x, z, config.dimension);
                if (match.filter != null) {
                    replaceMap.put(index, match.filter);
                }
                foundCount++;
            }
        }

        found = foundCount;
        publishResults(config);
    }

    private void publishResults(SearchConfig config) {
        chunkCache = null;
        searchConfig = null;
        if (isCancelled()) {
            return;
        }
        config.world.addScheduledTask(() -> {
            if (!isCancelled() && !tileEntity.isInvalid() && tileEntity.searcher == this) {
                if (!config.matches(tileEntity)) {
                    tileEntity.reset();
                    return;
                }
                tileEntity.oresToMine = oresToMine;
                tileEntity.replaceMap = replaceMap;
                state = State.FINISHED;
                MekanismUtils.saveChunk(tileEntity);
            }
        });
    }

    private void set(int index, int blockX, int blockZ, int dimension) {
        Chunk3D chunk = new Chunk3D(blockX >> 4, blockZ >> 4, dimension);
        oresToMine.computeIfAbsent(chunk, ignored -> new BitSet()).set(index);
    }

    @Deprecated
    public void set(int index, Coord4D location) {
        set(index, location.x, location.z, location.dimensionId);
    }

    public void cancel() {
        cancelled = true;
        interrupt();
        chunkCache = null;
        searchConfig = null;
    }

    @Deprecated
    public void reset() {
        cancel();
    }

    boolean isCancelled() {
        return cancelled || isInterrupted();
    }

    static int checkedSearchSize(int radius, int minY, int maxY) {
        long diameter = 2L * radius + 1;
        long height = (long) maxY - minY + 1;
        if (diameter <= 0 || diameter > Integer.MAX_VALUE || height <= 0 || height > Integer.MAX_VALUE) {
            return -1;
        }
        try {
            long size = Math.multiplyExact(Math.multiplyExact(diameter, diameter), height);
            return size <= Integer.MAX_VALUE ? (int) size : -1;
        } catch (ArithmeticException ignored) {
            return -1;
        }
    }

    static final class StateMatchCache {

        private final boolean inverse;
        private final ItemStack inverseReplaceTarget;
        private final List<MinerFilter> filters;
        private final Int2ObjectOpenHashMap<StateMatch> matches = new Int2ObjectOpenHashMap<>();

        StateMatchCache(boolean inverse, ItemStack inverseReplaceTarget, List<MinerFilter> filters) {
            this.inverse = inverse;
            this.inverseReplaceTarget = inverseReplaceTarget == null ? ItemStack.EMPTY : inverseReplaceTarget;
            this.filters = filters;
        }

        StateMatch get(int stateId, Block block, int metadata) {
            StateMatch cached = matches.get(stateId);
            if (cached != null) {
                return cached;
            }

            ItemStack stack = new ItemStack(block, 1, metadata);
            StateMatch computed;
            if (isReplacement(stack)) {
                computed = StateMatch.REPLACEMENT;
            } else {
                MinerFilter matchingFilter = null;
                for (MinerFilter filter : filters) {
                    if (filter.canFilter(stack)) {
                        matchingFilter = filter;
                        break;
                    }
                }
                computed = matchingFilter == null ? StateMatch.NO_FILTER : new StateMatch(matchingFilter, false);
            }
            matches.put(stateId, computed);
            return computed;
        }

        private boolean isReplacement(ItemStack stack) {
            if (inverse && !inverseReplaceTarget.isEmpty() && StackUtils.equalsWildcardWithNBT(inverseReplaceTarget, stack)) {
                return true;
            }
            for (MinerFilter filter : filters) {
                if (!filter.replaceStack.isEmpty() && filter.replaceStack.isItemEqual(stack)) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class StateMatch {

        private static final StateMatch REPLACEMENT = new StateMatch(null, true);
        private static final StateMatch NO_FILTER = new StateMatch(null, false);

        final MinerFilter filter;
        final boolean replacement;

        private StateMatch(MinerFilter filter, boolean replacement) {
            this.filter = filter;
            this.replacement = replacement;
        }
    }

    private static final class SearchConfig {

        private final WorldServer world;
        private final BlockPos minerPos;
        private final int dimension;
        private final int startX;
        private final int startY;
        private final int startZ;
        private final int diameter;
        private final int height;
        private final int size;
        private final int radius;
        private final int minY;
        private final int maxY;
        private final boolean inverse;
        private final ItemStack inverseReplaceTarget;
        private final List<MinerFilter> filters;
        private final boolean valid;

        private SearchConfig(WorldServer world, BlockPos minerPos, int dimension, int startX, int startY, int startZ, int diameter, int height,
              int size, int radius, int minY, int maxY, boolean inverse, ItemStack inverseReplaceTarget, List<MinerFilter> filters, boolean valid) {
            this.world = world;
            this.minerPos = minerPos;
            this.dimension = dimension;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.diameter = diameter;
            this.height = height;
            this.size = size;
            this.radius = radius;
            this.minY = minY;
            this.maxY = maxY;
            this.inverse = inverse;
            this.inverseReplaceTarget = inverseReplaceTarget;
            this.filters = filters;
            this.valid = valid;
        }

        private static SearchConfig capture(TileEntityDigitalMiner tile, WorldServer world) {
            int radius = tile.getRadius();
            int size = checkedSearchSize(radius, tile.minY, tile.maxY);
            long diameterLong = 2L * radius + 1;
            long heightLong = (long) tile.maxY - tile.minY + 1;
            long startXLong = (long) tile.getPos().getX() - radius;
            long startZLong = (long) tile.getPos().getZ() - radius;
            boolean coordinatesValid = diameterLong > 0 && diameterLong <= Integer.MAX_VALUE && heightLong > 0 && heightLong <= Integer.MAX_VALUE &&
                  startXLong >= Integer.MIN_VALUE && startXLong <= Integer.MAX_VALUE && startZLong >= Integer.MIN_VALUE && startZLong <= Integer.MAX_VALUE &&
                  startXLong + diameterLong <= Integer.MAX_VALUE && startZLong + diameterLong <= Integer.MAX_VALUE &&
                  (long) tile.minY + heightLong <= Integer.MAX_VALUE;

            List<MinerFilter> filters = new ArrayList<>();
            for (MinerFilter filter : tile.getFilterManager().getEnabledFilters()) {
                filters.add(filter.clone());
            }
            return new SearchConfig(world, tile.getPos().toImmutable(), world.provider.getDimension(), (int) startXLong, tile.minY, (int) startZLong,
                  coordinatesValid ? (int) diameterLong : 0, coordinatesValid ? (int) heightLong : 0, size, radius, tile.minY, tile.maxY, tile.inverse,
                  tile.getInverseReplaceTarget().copy(), Collections.unmodifiableList(filters), size >= 0 && coordinatesValid);
        }

        private boolean requiresScan() {
            return inverse || !filters.isEmpty();
        }

        private boolean matches(TileEntityDigitalMiner tile) {
            if (radius != tile.getRadius() || minY != tile.minY || maxY != tile.maxY || inverse != tile.inverse ||
                  !ItemStack.areItemStacksEqual(inverseReplaceTarget, tile.getInverseReplaceTarget())) {
                return false;
            }
            List<MinerFilter> currentFilters = tile.getFilterManager().getEnabledFilters();
            if (filters.size() != currentFilters.size()) {
                return false;
            }
            for (int i = 0; i < filters.size(); i++) {
                if (!filters.get(i).equals(currentFilters.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class MinerChunkCache extends ChunkCache {

        public MinerChunkCache(World world, BlockPos from, BlockPos to, int padding) {
            super(world, from, to, padding);
        }

        boolean isRegionEmpty() {
            return empty;
        }
    }

    public enum State {
        IDLE("gui.teleporter.notReady"),
        SEARCHING("gui.teleporter.Searching"),
        PAUSED("gui.teleporter.Paused"),
        FINISHED("gui.teleporter.ready");

        public String desc;

        State(String s) {
            desc = s;
        }

        public String localize() {
            return LangUtils.localize(getTranslationKey());
        }

        public String getTranslationKey() {
            return desc;
        }
    }
}
