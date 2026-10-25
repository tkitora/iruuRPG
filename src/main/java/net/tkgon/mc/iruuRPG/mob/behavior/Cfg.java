package net.tkgon.mc.iruuRPG.mob.behavior;

import java.util.Locale;
import java.util.Map;

/** Reading values out of the yaml maps of a behavior (a missing or wrong value gives the fallback). */
final class Cfg {

    private Cfg() {
    }

    static String text(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    static String lower(Map<?, ?> map, String key, String fallback) {
        return text(map, key, fallback).toLowerCase(Locale.ROOT);
    }

    static double number(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static boolean flag(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean flag) return flag;
        if (value instanceof String text) return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes");
        return fallback;
    }

    /** Seconds in the yaml as ticks. */
    static int ticks(Map<?, ?> map, String key, double fallbackSeconds) {
        return (int) Math.max(0, Math.round(number(map, key, fallbackSeconds) * 20.0));
    }
}
