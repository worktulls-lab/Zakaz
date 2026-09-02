package ru.tarkmull.kitmod;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Msg {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Msg() {
    }

    /** &-коды -> Component. */
    public static Component c(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy);
    }

    /** То же, но без курсива (для названий и лора предметов). */
    public static Component item(String legacy) {
        return c(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static String strip(Component component) {
        return LEGACY.serialize(component);
    }
}
