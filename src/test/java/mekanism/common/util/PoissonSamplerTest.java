package mekanism.common.util;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.*;

class PoissonSamplerTest {

    private static final double[] TABLE_MEANS = {
          0, 1.0E-9, 0.25, 0.5, 1, 1.5, 2, 3, 5, 7.5, 10, 25, 50, 99.9, 100, 100.0001, 250, 500, 1_000, 1_365
    };

    @Test
    void cachedTableIsIdenticalToOriginalForTheSameDraw() {
        PoissonSampler sampler = new PoissonSampler();
        for (double mean : TABLE_MEANS) {
            sampler.prepare(mean);
            assertFalse(sampler.isFallback(), "expected cached table for mean=" + mean);
            for (double uniform : uniformDraws(sampler)) {
                assertEquals(StatUtils.inversePoisson(mean, uniform), sampler.search(uniform * sampler.getExpMean()),
                      "mean=" + mean + ", uniform=" + uniform);
            }
        }
    }

    @Test
    void samplingConsumesExactlyOneSharedRandomDraw() {
        long seed = 0x5EEDBEEFL;
        double mean = 42.5;
        Random expectedRandom = new Random(seed);
        int expectedSample = StatUtils.inversePoisson(mean, expectedRandom.nextDouble());
        double expectedNextDraw = expectedRandom.nextDouble();

        Random previousRandom = StatUtils.rand;
        try {
            StatUtils.rand = new Random(seed);
            PoissonSampler sampler = new PoissonSampler();
            assertEquals(expectedSample, sampler.sample(mean));
            assertEquals(expectedNextDraw, StatUtils.rand.nextDouble());
        } finally {
            StatUtils.rand = previousRandom;
        }
    }

    @Test
    void outOfRangeMeansUseOriginalImplementationWithoutAllocatingATable() {
        PoissonSampler sampler = new PoissonSampler();
        sampler.prepare(PoissonSampler.MAX_TABLE_BOUND / 3.0 + 1);
        assertTrue(sampler.isFallback());

        sampler.prepare(Double.NaN);
        assertTrue(sampler.isFallback());

        sampler.prepare(50);
        assertFalse(sampler.isFallback());
    }

    private static double[] uniformDraws(PoissonSampler sampler) {
        DoubleStream.Builder draws = DoubleStream.builder();
        for (int i = 0; i <= 256; i++) {
            draws.add(Math.min(i / 256.0, Math.nextDown(1.0)));
        }
        for (int m = 0; m <= sampler.getBound(); m++) {
            double boundary = sampler.getCumulative(m) / sampler.getExpMean();
            for (double candidate : new double[]{boundary, Math.nextUp(boundary), Math.nextDown(boundary)}) {
                if (candidate >= 0 && candidate < 1) {
                    draws.add(candidate);
                }
            }
        }
        return draws.build().toArray();
    }
}
