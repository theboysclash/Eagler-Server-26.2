package dev.theboysclash.hubeconomy;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormat {

    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private MoneyFormat() {}

    public static String format(long amount) {
        return FORMAT.format(amount);
    }

    public static String formatCoins(long amount) {
        long abs = Math.abs(amount);
        String num = FORMAT.format(abs);
        if (amount == 1 || amount == -1) {
            return num + " coin";
        }
        return num + " coins";
    }
}
