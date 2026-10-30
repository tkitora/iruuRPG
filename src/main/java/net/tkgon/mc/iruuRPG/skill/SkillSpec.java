package net.tkgon.mc.iruuRPG.skill;

import java.util.List;

/**
 * A skill, ready to run: a template plus its adjusted values. Built from the old flat form
 * ({@code {type: strike, radius: 3}}), from a skill file ({@code skills/*.yml}) or from a reference with overrides
 * ({@code {skill: fire_slam, radius: 5}}); see {@link SkillRegistry}.
 *
 * @param id             the skill id ("" for an unnamed one written inline)
 * @param template       the shape
 * @param look           colors, particle, size, width, sound
 * @param radius         strike / nova: radius; wave: hit width; beam: half width of the beam; damage: around the one
 *                       aimed at (0 = only it), or around the caster
 * @param range          sweep / wave / dash / beam / barrage / damage (aimed): how far it reaches
 * @param angle          sweep: the arc in degrees; wave: the spread between several waves
 * @param orientation    how the arc of a sweep is turned
 * @param windupTicks    the warning before it hits (damage: only a wait, with no warning)
 * @param cooldownTicks  time before the caster can use it again
 * @param count          barrage: number of bolts; wave: number of waves
 * @param intervalTicks  time between two bolts
 * @param speed          bolt / wave / nova speed in blocks per second
 * @param damageRate     times the caster's normal damage
 * @param statuses       status effects put on whoever is hit
 * @param healRate       share of max HP healed to allies hit (allies' skills; unused for enemies)
 * @param knockback      how the enemies hit are pushed (null = not at all)
 * @param target         who it affects
 * @param cost           MP a player pays to cast it (enemies do not pay)
 * @param description    lines for item / skill tree text
 * @param steps          combo: the steps in order (empty for any other template)
 * @param origin         where it appears (null = at the caster, as always)
 * @param teleportMode   teleport: forward along the facing, or behind the enemy aimed at (unused by the other templates)
 */
public record SkillSpec(
        String id,
        String name,
        SkillTemplate template,
        SkillLook look,
        double radius,
        double range,
        double angle,
        Orientation orientation,
        int windupTicks,
        int cooldownTicks,
        int count,
        int intervalTicks,
        double speed,
        double damageRate,
        List<StatusApply> statuses,
        double healRate,
        Knockback knockback,
        Target target,
        double cost,
        List<String> description,
        List<ComboStep> steps,
        Origin origin,
        TeleportMode teleportMode,
        Extras extras
) {

    public enum Orientation { HORIZONTAL, VERTICAL, DIAGONAL }

    /** Where the bullets of a volley start. */
    public enum VolleyPattern { RING, FAN, LINE, SCATTER }

    /**
     * Settings that few skills need. Aim: {@code lockAim} keeps the direction the skill was started with (a combo
     * whose player turns half way still goes the first way), {@code flatAim} ignores looking up or down,
     * {@code aimYaw} / {@code aimPitch} turn the aim by that many degrees, {@code spread} fans the bolts of a barrage
     * over that many degrees. Volley: the start {@code pattern}, how hard the bullets first swing out
     * ({@code curve}), the {@code homing*} behavior, how long a bullet flies, its hit size and how far in front of
     * the caster the pattern is. Nova: {@code hitHeight} is how far above or below the ring's center the feet of the
     * one it hits may be (small: it can be jumped over). Sweep: {@code reverse} draws the cut from the caster's left
     * to the right (the other way round), to follow a model's swing. Damage: {@code damageMode} is whom it hits.
     */
    public record Extras(boolean lockAim, boolean flatAim, double aimYaw, double aimPitch, double spread,
                         VolleyPattern pattern, double curve, double homingRadius, int homingDelayTicks, double homingTurn,
                         int lifetimeTicks, double hitRadius, double spawnDistance, double hitHeight,
                         boolean reverse, DamageMode damageMode) {

        public static final double DEFAULT_HIT_HEIGHT = 2.5;

        public static final Extras NONE = new Extras(false, false, 0.0, 0.0, 0.0,
                VolleyPattern.RING, 0.9, 0.0, 7, 0.45, 70, 0.9, 2.0, DEFAULT_HIT_HEIGHT, false, DamageMode.AIMED);

        public Extras {
            pattern = pattern == null ? VolleyPattern.RING : pattern;
            damageMode = damageMode == null ? DamageMode.AIMED : damageMode;
        }


        public boolean aimChanged() {
            return lockAim || flatAim;
        }
    }

    /**
     * Whom a damage skill hits: {@code aimed} = the one enemy aimed at (in the range, not behind a wall) and the
     * enemies within the radius of it, {@code around} = the enemies within the radius of the caster (or its origin).
     */
    public enum DamageMode { AIMED, AROUND }

    /** Where a teleport goes: {@code forward} = as far as the range along the facing, {@code behind} = behind the enemy aimed at. */
    public enum TeleportMode { FORWARD, BEHIND }

    /** One step of a combo: a skill, how long to wait before it, and whether it starts together with the step before. */
    public record ComboStep(SkillSpec spec, int delayTicks, boolean withPrevious, boolean ifHit) {
    }

    public enum Anchor { CASTER, TARGET }

    public enum KnockbackType { BACK, UP, PULL, VECTOR }

    public enum KnockbackCenter { ORIGIN, CASTER }

    /**
     * A push on enemies hit: {@code back} = away from the center (with a little lift), {@code up} = straight up,
     * {@code pull} = toward the center, {@code vector} = forward / side / up components along where the caster faces.
     */
    public record Knockback(KnockbackType type, double power, KnockbackCenter center, double lift, double forward, double side, double up) {
    }

    /** A point to start from: from the caster (or the one aimed at), this far ahead / to the right / up. */
    public record Origin(Anchor anchor, double forward, double side, double up) {
    }

    public static final int MAX_COMBO_STEPS = 8;
    public static final int MAX_COMBO_TICKS = 240;

    public enum Target { ENEMIES, ALLIES, BOTH }

    /** A status effect put on a target: slow uses percent + seconds, the others a value. */
    public record StatusApply(String type, double percent, double seconds, double value) {
    }

    public SkillSpec {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        cost = Math.max(0.0, cost);
        description = description == null ? List.of() : List.copyOf(description);
        steps = steps == null ? List.of() : List.copyOf(steps);
        teleportMode = teleportMode == null ? TeleportMode.FORWARD : teleportMode;
    }

    /** The defaults of a template (the numbers the first enemy skills had). */
    public static SkillSpec defaults(SkillTemplate template) {
        SoundStyle sound = switch (template) {
            case STRIKE -> SoundStyle.HEAVY;
            case SWEEP, DASH, WAVE -> SoundStyle.SHARP;
            case BARRAGE, NOVA, BEAM, TELEPORT, VOLLEY -> SoundStyle.MAGIC;
            case COMBO, DAMAGE -> SoundStyle.NONE;
        };
        SkillLook look = new SkillLook(null, null, 1.0, 1.0, sound);
        return switch (template) {
            case STRIKE -> new SkillSpec("", "", template, look, 3.5, 4.5, 130, Orientation.HORIZONTAL, 24, 160, 1, 4, 12.0, 1.8, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case SWEEP -> new SkillSpec("", "", template, look, 4.5, 4.5, 130, Orientation.HORIZONTAL, 14, 120, 1, 4, 12.0, 1.3, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case BARRAGE -> new SkillSpec("", "", template, look, 1.0, 12.0, 130, Orientation.HORIZONTAL, 12, 200, 5, 5, 14.0, 0.5, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case DASH -> new SkillSpec("", "", template, look, 1.0, 10.0, 130, Orientation.HORIZONTAL, 16, 180, 1, 4, 12.0, 1.5, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case WAVE -> new SkillSpec("", "", template, look, 1.3, 16.0, 14, Orientation.HORIZONTAL, 16, 140, 1, 4, 14.0, 1.2, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case NOVA -> new SkillSpec("", "", template, look, 5.0, 5.0, 130, Orientation.HORIZONTAL, 16, 180, 1, 4, 10.0, 1.2, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case TELEPORT -> new SkillSpec("", "", template, look, 3.0, 12.0, 130, Orientation.HORIZONTAL, 12, 160, 1, 4, 12.0, 0.0, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case VOLLEY -> new SkillSpec("", "", template, look, 1.2, 24.0, 90, Orientation.HORIZONTAL, 12, 200, 6, 4, 22.0, 1.3, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, new Extras(false, false, 0.0, 0.0, 0.0, VolleyPattern.RING, 0.9, 8.0, 7, 0.45, 70, 0.9, 2.0, Extras.DEFAULT_HIT_HEIGHT, false, DamageMode.AIMED));
            case DAMAGE -> new SkillSpec("", "", template, look, 0.0, 12.0, 130, Orientation.HORIZONTAL, 0, 100, 1, 4, 12.0, 1.0, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case COMBO -> new SkillSpec("", "", template, look, 1.0, 1.0, 130, Orientation.HORIZONTAL, 4, 240, 1, 4, 12.0, 0.0, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
            case BEAM -> new SkillSpec("", "", template, look, 1.0, 14.0, 130, Orientation.HORIZONTAL, 18, 200, 1, 4, 12.0, 1.6, List.of(), 0, null, Target.ENEMIES, 0.0, List.of(), List.of(), null, TeleportMode.FORWARD, Extras.NONE);
        };
    }

    /** The skill whose conditions decide whether this one can be used: the first step of a combo, else itself. */
    public SkillSpec first() {
        return template == SkillTemplate.COMBO && !steps.isEmpty() ? steps.get(0).spec() : this;
    }

    /** How far this skill (or the farthest step of a combo) reaches, for searching targets. */
    public double reach() {
        double reach = Math.max(range, radius);
        for (ComboStep step : steps) reach = Math.max(reach, step.spec().reach());
        return reach;
    }

    /** About how many ticks a skill takes from start to end (the warning plus what follows it). */
    public int estimatedTicks() {
        return switch (template) {
            case STRIKE, SWEEP, WAVE -> windupTicks + 2;
            case BARRAGE, VOLLEY -> windupTicks + count * intervalTicks + extras.lifetimeTicks() / 2;
            case DASH -> windupTicks + 10;
            case NOVA -> windupTicks + (int) Math.ceil(radius / Math.max(0.1, speed / 20.0)) + 2;
            case BEAM -> windupTicks + 6;
            case TELEPORT -> windupTicks + 2;
            case DAMAGE -> windupTicks + (count - 1) * intervalTicks + 1;
            case COMBO -> comboTicks();
        };
    }

    private int comboTicks() {
        int end = 0;
        int previousStart = 0;
        for (int index = 0; index < steps.size(); index++) {
            ComboStep step = steps.get(index);
            int start = step.withPrevious() && index > 0 ? previousStart + step.delayTicks() : end + step.delayTicks();
            end = Math.max(end, start + step.spec().estimatedTicks());
            previousStart = start;
        }
        return end;
    }

    public SkillSpec withLook(SkillLook newLook) {
        return new SkillSpec(id, name, template, newLook, radius, range, angle, orientation, windupTicks, cooldownTicks, count, intervalTicks,
                speed, damageRate, statuses, healRate, knockback, target, cost, description, steps, origin, teleportMode, extras);
    }

    /** The same skill keeping the direction it was started with (it does not turn to follow the target while it winds up). */
    public SkillSpec withLockedAim() {
        if (extras.lockAim()) return this;
        Extras locked = new Extras(true, extras.flatAim(), extras.aimYaw(), extras.aimPitch(), extras.spread(), extras.pattern(), extras.curve(), extras.homingRadius(),
                extras.homingDelayTicks(), extras.homingTurn(), extras.lifetimeTicks(), extras.hitRadius(), extras.spawnDistance(), extras.hitHeight(), extras.reverse(), extras.damageMode());
        return new SkillSpec(id, name, template, look, radius, range, angle, orientation, windupTicks, cooldownTicks, count, intervalTicks,
                speed, damageRate, statuses, healRate, knockback, target, cost, description, steps, origin, teleportMode, locked);
    }

    public SkillSpec withDamageRate(double newRate) {
        return new SkillSpec(id, name, template, look, radius, range, angle, orientation, windupTicks, cooldownTicks, count, intervalTicks,
                speed, newRate, statuses, healRate, knockback, target, cost, description, steps, origin, teleportMode, extras);
    }
}
