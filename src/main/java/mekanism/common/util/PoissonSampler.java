package mekanism.common.util;

/**
 * Caches the cumulative table used by {@link StatUtils#inversePoisson(double)} for a stable mean.
 * Each sampler is owned by one ticking tile and consumes exactly one shared RNG draw per sample.
 */
public class PoissonSampler {

    static final int MAX_TABLE_BOUND = 4096;

    private double builtMean = Double.NaN;
    private double expMean;
    private int bound;
    private double[] cumulative;

    public int sample(double mean) {
        if (mean != builtMean) {
            prepare(mean);
        }
        if (cumulative == null) {
            return StatUtils.inversePoisson(mean);
        }
        return search(StatUtils.rand.nextDouble() * expMean);
    }

    void prepare(double mean) {
        builtMean = mean;
        expMean = Math.exp(mean);
        double tableBound = 3 * Math.ceil(mean);
        if (!(tableBound >= 0) || tableBound > MAX_TABLE_BOUND) {
            cumulative = null;
            bound = 0;
            return;
        }

        bound = (int) tableBound;
        double[] table = new double[bound + 1];
        table[0] = 1;
        double stirlingValue = mean * Math.E;
        for (int m = 1; m <= bound; m++) {
            table[m] = table[m - 1] + StatUtils.STIRLING_COEFF / Math.sqrt(m) * Math.pow(stirlingValue / m, m);
        }
        cumulative = table;
    }

    int search(double target) {
        int low = 0;
        int high = bound;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulative[middle] < target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    double getExpMean() {
        return expMean;
    }

    int getBound() {
        return bound;
    }

    double getCumulative(int index) {
        return cumulative[index];
    }

    boolean isFallback() {
        return cumulative == null;
    }
}
