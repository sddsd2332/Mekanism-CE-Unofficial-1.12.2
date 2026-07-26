package mekanism.common.content.miner;

import mekanism.common.TestBootstrap;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ThreadMinerSearchTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void stateVerdictsCacheBothMatchesAndMisses() {
        CountingFilter filter = new CountingFilter(false);
        ThreadMinerSearch.StateMatchCache cache = new ThreadMinerSearch.StateMatchCache(false, ItemStack.EMPTY, Collections.singletonList(filter));
        int stateId = Block.getStateId(Blocks.STONE.getDefaultState());

        ThreadMinerSearch.StateMatch first = cache.get(stateId, Blocks.STONE, 0);
        ThreadMinerSearch.StateMatch second = cache.get(stateId, Blocks.STONE, 0);

        assertSame(first, second);
        assertNull(first.filter);
        assertEquals(1, filter.calls);
    }

    @Test
    void replacementStatesBypassFilterChecksAndStayCached() {
        CountingFilter filter = new CountingFilter(true);
        filter.replaceStack = new ItemStack(Blocks.STONE);
        ThreadMinerSearch.StateMatchCache cache = new ThreadMinerSearch.StateMatchCache(false, ItemStack.EMPTY, Collections.singletonList(filter));
        int stateId = Block.getStateId(Blocks.STONE.getDefaultState());

        assertTrue(cache.get(stateId, Blocks.STONE, 0).replacement);
        assertTrue(cache.get(stateId, Blocks.STONE, 0).replacement);
        assertEquals(0, filter.calls);
    }

    @Test
    void oversizedSearchRegionsAreRejectedBeforeIntegerOverflow() {
        assertEquals(21 * 21 * 61, ThreadMinerSearch.checkedSearchSize(10, 0, 60));
        assertEquals(-1, ThreadMinerSearch.checkedSearchSize(Integer.MAX_VALUE, 0, 255));
        assertEquals(-1, ThreadMinerSearch.checkedSearchSize(10, 100, 99));
    }

    @Test
    void resetCancelsThePreviousSearchGeneration() {
        TileEntityDigitalMiner miner = new TileEntityDigitalMiner();
        ThreadMinerSearch oldSearch = miner.searcher;

        miner.reset();

        assertTrue(oldSearch.isCancelled());
        assertNotSame(oldSearch, miner.searcher);
        assertEquals(ThreadMinerSearch.State.IDLE, miner.searcher.state);
    }

    private static class CountingFilter extends MinerFilter {

        private final boolean result;
        private int calls;

        private CountingFilter(boolean result) {
            this.result = result;
        }

        @Override
        public boolean canFilter(ItemStack itemStack) {
            calls++;
            return result;
        }

        @Override
        public boolean hasBlacklistedElement() {
            return false;
        }

        @Override
        public CountingFilter clone() {
            CountingFilter copy = new CountingFilter(result);
            copyBaseData(copy);
            return copy;
        }
    }
}
