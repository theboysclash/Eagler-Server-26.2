package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Messages {

    private Messages() {}

    public static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    public static Component info(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    public static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
