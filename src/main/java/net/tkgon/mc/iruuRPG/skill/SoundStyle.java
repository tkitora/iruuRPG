package net.tkgon.mc.iruuRPG.skill;

import java.util.Locale;

/**
 * How a skill sounds: a combination of the existing sounds, for the moment it is cast and each hit (the warning before
 * it is the same for all but none). A skill's {@code look.sounds} can replace any of the three moments with sounds of
 * its own ({@link SkillSounds}).
 */
public enum SoundStyle {
    /** A heavy blow: an explosion and struck iron. */
    HEAVY,
    /** A cut: a sweep and wing beats. */
    SHARP,
    /** A spell: casting and a beacon. */
    MAGIC,
    /** Fire: a blaze's shot and a fire charge. */
    FIRE,
    /** Ice: glass and amethyst breaking, freezing. */
    ICE,
    /** Thunder: a lightning strike. */
    THUNDER,
    /** Wind: wing beats and a swoop. */
    WIND,
    /** Water: a splash and a bucket. */
    WATER,
    /** Earth: stone breaking, a heavy thud. */
    EARTH,
    /** Darkness: a wither's shot and an enderman. */
    DARK,
    /** Light: a beacon and chimes. */
    HOLY,
    /** A blunt blow: a knockback hit and an anvil. */
    BLUNT,
    /** A shot: a crossbow and a firework. */
    SHOT,
    /** An explosion. */
    EXPLOSION,
    /** Poison: slime and a witch's throw. */
    POISON,
    NONE;

    public static SoundStyle parse(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** The names a skill file may use, for warnings. */
    public static String names() {
        StringBuilder out = new StringBuilder();
        for (SoundStyle style : values()) {
            if (out.length() > 0) out.append(" / ");
            out.append(style.name().toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
