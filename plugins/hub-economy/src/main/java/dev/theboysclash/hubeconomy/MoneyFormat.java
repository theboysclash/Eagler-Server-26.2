package dev.theboysclash.hubeconomy;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormat {

    private static final NumberFormat FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private MoneyFormat() {}

    public static String format(double amount) {
        return FORMAT.format(amount);
    }
}
