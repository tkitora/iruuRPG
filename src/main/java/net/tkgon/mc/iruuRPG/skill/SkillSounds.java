package net.tkgon.mc.iruuRPG.skill;

import java.util.List;

/**
 * The sounds a skill sets for itself ({@code look.sounds}), one list for each moment: the warning before it
 * ({@code windup}), the moment it is cast ({@code cast}) and each hit ({@code hit}). A null list keeps the sound
 * style's own for that moment; an empty one is silent.
 */
public record SkillSounds(List<Cue> windup, List<Cue> cast, List<Cue> hit) {

    /** At most this many sounds play together at one moment. */
    public static final int MAX_CUES = 4;
    public static final float MIN_VOLUME = 0.0f;
    public static final float MAX_VOLUME = 1.0f;
    public static final float MIN_PITCH = 0.5f;
    public static final float MAX_PITCH = 2.0f;

    /** One sound: its key ("minecraft:entity.blaze.shoot", or a resource pack's own), volume and pitch. */
    public record Cue(String key, float volume, float pitch) {
        public Cue {
            volume = Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
            pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
        }
    }

    public SkillSounds {
        windup = windup == null ? null : List.copyOf(windup);
        cast = cast == null ? null : List.copyOf(cast);
        hit = hit == null ? null : List.copyOf(hit);
    }

    /** These sounds with the moments {@code over} sets laid on top. */
    public SkillSounds with(SkillSounds over) {
        if (over == null) return this;
        return new SkillSounds(over.windup != null ? over.windup : windup, over.cast != null ? over.cast : cast,
                over.hit != null ? over.hit : hit);
    }
}
