package mekanism.common.util.text;

import mekanism.common.util.LangUtils;

import java.math.BigInteger;
import java.text.NumberFormat;

public final class TextUtils {

    private TextUtils() {
    }

    private static final String HEX_PREFIX = "0x";
    private static final char[] HEX_CODES = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private static final NumberFormat intFormatter = NumberFormat.getIntegerInstance();
    private static final NumberFormat doubleFormatter = NumberFormat.getNumberInstance();

    public static String getPercent(double ratio) {
        return (Math.round(ratio * 100)) + LangUtils.localize("generic.mekanism.percent") ;
    }

    public static String getHoursMinutes(int seconds) {
        int minutes = (int) Math.ceil(seconds / 60.0);
        int hours = minutes / 60;
        return hours > 0 ?LangUtils.localize("generic.mekanism.hours_minutes")+ hours +  minutes % 60 : LangUtils.localize("generic.mekanism.minutes") + minutes;
    }

    public static String format(long count) {
        return intFormatter.format(count);
    }

    public static String format(double count) {
        return doubleFormatter.format(count);
    }

    public static String format(BigInteger count) {
        return intFormatter.format(count);
    }

    public static String hex(boolean prefix, int bytes, long value) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < bytes * 2; i++) {
            ret.insert(0, HEX_CODES[(int) (value & 0xF)]);
            value >>= 4;
        }
        if (prefix) {
            ret.insert(0, HEX_PREFIX);
        }
        return ret.toString();
    }
}
