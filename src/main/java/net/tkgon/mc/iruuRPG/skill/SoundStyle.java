package net.tkgon.mc.iruuRPG.skill;

import java.util.Locale;

/** How a skill sounds: a combination of the existing sounds. */
public enum SoundStyle {
    HEAVY,
    SHARP,
    MAGIC,
    NONE;

    public static SoundStyle parse(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
