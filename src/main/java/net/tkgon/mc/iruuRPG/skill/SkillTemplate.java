package net.tkgon.mc.iruuRPG.skill;

import java.util.Locale;

/** The shapes a skill can be built from. */
public enum SkillTemplate {
    /** A ground slam: a circle around the caster is marked, then hit. */
    STRIKE,
    /** A wide cut in front of the caster. */
    SWEEP,
    /** The caster stops and fires a series of bolts. */
    BARRAGE,
    /** A lunge along a marked line. */
    DASH,
    /** A slash that flies through. */
    WAVE,
    /** A ring that expands from the caster in every direction. */
    NOVA,
    /** A straight beam. */
    BEAM,
    /** A volley of bullets leaving a pattern (ring / fan / line / scatter), curving and homing on enemies. */
    VOLLEY,
    /** The caster blinks: forward along where they face, or behind the enemy aimed at; optionally a burst on arrival. */
    TELEPORT,
    /** Several skills one after another (steps). */
    COMBO;

    public static SkillTemplate parse(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
