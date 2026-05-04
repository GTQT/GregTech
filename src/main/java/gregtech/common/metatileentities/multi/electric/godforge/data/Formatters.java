package gregtech.common.metatileentities.multi.electric.godforge.data;

import java.math.BigInteger;

import gregtech.api.util.TextFormattingUtil;

public enum Formatters {

    NONE,
    COMMA,
    EXPONENT;

    public static final Formatters[] VALUES = values();

    public Formatters cycle() {
        switch (this) {
            case NONE: return COMMA;
            case COMMA: return EXPONENT;
            case EXPONENT: return NONE;
            default: return NONE;
        }
    }

    public String format(Number number) {
        switch (this) {
            case NONE:
                return number.toString();
            case COMMA:
                return TextFormattingUtil.formatNumbers(number);
            case EXPONENT:
                return toExponentForm(number);
            default:
                return number.toString();
        }
    }

    private static String toExponentForm(Number number) {
        double value;
        if (number instanceof BigInteger) {
            value = ((BigInteger) number).doubleValue();
        } else {
            value = number.doubleValue();
        }

        if (Math.abs(value) < 1000.0) {
            if (number instanceof BigInteger) {
                return number.toString();
            }
            return Long.toString(number.longValue());
        }

        int exponent = (int) Math.floor(Math.log10(Math.abs(value)));
        double mantissa = value / Math.pow(10, exponent);

        if (mantissa >= 9.9995) {
            mantissa = 1.0;
            exponent++;
        }

        return String.format("%.3fE%d", mantissa, exponent);
    }
}
