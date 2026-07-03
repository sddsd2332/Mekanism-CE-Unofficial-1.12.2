package mekanism.api.heat;

public final class HeatAPI {

    private HeatAPI() {
    }

    public static final double AMBIENT_TEMP = 300;
    public static final double AIR_INVERSE_COEFFICIENT = 10_000;
    public static final double DEFAULT_HEAT_CAPACITY = 1;
    public static final double DEFAULT_INVERSE_CONDUCTION = 1;
    public static final double DEFAULT_INVERSE_INSULATION = 0;
    public static final double EPSILON = 1.0E-5;

    public static double getAmbientTemp(double biomeTemp) {
        biomeTemp = Math.max(-5, Math.min(5, biomeTemp));
        return AMBIENT_TEMP + 25 * (biomeTemp - 0.8);
    }

    public static class HeatTransfer {

        private final double adjacentTransfer;
        private final double environmentTransfer;

        public HeatTransfer(double adjacentTransfer, double environmentTransfer) {
            this.adjacentTransfer = adjacentTransfer;
            this.environmentTransfer = environmentTransfer;
        }

        public double adjacentTransfer() {
            return adjacentTransfer;
        }

        public double environmentTransfer() {
            return environmentTransfer;
        }
    }
}
