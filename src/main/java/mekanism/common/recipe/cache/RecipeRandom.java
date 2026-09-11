package mekanism.common.recipe.cache;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

/**
 * Per-lane random source for recipe execution.
 *
 * <p>Before this type existed, every probability roll in the recipe layer drew from a single process-wide
 * {@link Random} instance held statically by {@code ChanceOutput}, {@code ChanceOutput2}, {@code ChanceGasOutput}
 * and {@code FarmOutput}. That had three consequences:</p>
 *
 * <ol>
 *     <li>results were not reproducible - the same machine and the same recipe could produce a different output
 *     sequence on every run;</li>
 *     <li>the sequence depended on execution order, so the output of one machine could change when an unrelated
 *     machine ran at a different time;</li>
 *     <li>{@link Random} synchronises internally, so parallel lanes contended on the same instance.</li>
 * </ol>
 *
 * <p>This type derives an independent {@link Random} per (machine seed, lane) pair so that a lane always replays the
 * same sequence for the same inputs, and so that lanes never influence each other. The machine seed is assigned by
 * the main thread at capture time and must not be re-drawn when a plan is retried or re-validated, otherwise the
 * output sequence would change between the calculation and the commit.</p>
 *
 * <p>Thread safety: derive a distinct instance per lane. A single instance must not be shared between lanes, but a
 * given lane may use its instance from whichever thread performs that lane's work.</p>
 */
public final class RecipeRandom {

    /**
     * Mixing constant from splitmix64; using a fixed odd constant keeps derivation stable across runs.
     */
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

    private RecipeRandom() {
    }

    /**
     * Derives the seed for one lane from a machine seed.
     *
     * @param machineSeed seed assigned on the main thread for this capture
     * @param lane        lane index within the machine; must not be negative
     * @return a deterministic seed for that lane
     */
    public static long deriveSeed(long machineSeed, int lane) {
        if (lane < 0) {
            throw new IllegalArgumentException("Lane index cannot be negative: " + lane);
        }
        long value = machineSeed + SEED_MIX * (lane + 1L);
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    /**
     * Creates an independent random source for one lane.
     *
     * @param machineSeed seed assigned on the main thread for this capture
     * @param lane        lane index within the machine; must not be negative
     * @return a new source that replays the same sequence for the same arguments
     */
    @Nonnull
    public static Random forLane(long machineSeed, int lane) {
        return new Random(deriveSeed(machineSeed, lane));
    }

    /**
     * Creates an independent random source for one lane, rejecting a null machine identity.
     *
     * @param machineIdentity stable identity of the machine, for example its position or a persistent id
     * @param lane            lane index within the machine; must not be negative
     */
    @Nonnull
    public static Random forLane(@Nonnull String machineIdentity, int lane) {
        Objects.requireNonNull(machineIdentity, "Machine identity cannot be null");
        return forLane(machineIdentity.hashCode(), lane);
    }
}
