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
 * @param radius         strike / nova: radius; wave: hit width; beam: half width of the beam
 * @param range          sweep / wave / dash / beam / barrage: how far it reaches
 * @param angle          sweep: the arc in degrees; wave: the spread between several waves
 * @param orientation    how the arc of a sweep is turned
 * @param windupTicks    the warning before it hits
 * @param cooldownTicks  time before the caster can use it again
 * @param count          barrage: number of bolts; wave: number of waves
 * @param intervalTicks  time between two bolts
 * @param speed          bolt / wave / nova speed in blocks per second
 * @param damageRate     times the caster's normal damage
 * @param statuses       status effects put on whoever is hit
 * @param healRate       share of max HP healed to allies hit (allies' skills; unused for enemies)
 * @param knockback      push away from the caster (0 = none)
 * @param target         who it affects
 * @param cost           MP a player pays to cast it (enemies do not pay)
 * @param description    lines for item / skill tree text
 * @param steps          combo: the steps in order (empty for any other template)
 * @param origin         where it appears (null = at the caster, as always)
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
        double knockback,
        Target target,
        double cost,
        List<String> description,
        List<ComboStep> steps,
        Origin origin
) {

    public enum Orientation { HORIZONTAL, VERTICAL, DIAGONAL }

    /** One step of a combo: a skill, how long to wait before it, and whether it starts together with the step before. */
    public record ComboStep(SkillSpec spec, int delayTicks, boolean withPrevious) {
    }

    public enum Anchor { CASTER, TARGET }

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
    }

    /** The defaults of a template (the numbers the first enemy skills had). */
    public static SkillSpec defaults(SkillTemplate template) {
        SoundStyle sound = switch (template) {
            case STRIKE -> SoundStyle.HEAVY;
            case SWEEP, DASH, WAVE -> SoundStyle.SHARP;
            case BARRAGE, NOVA, BEAM -> SoundStyle.MAGIC;
            case COMBO -> SoundStyle.NONE;
        };
        SkillLook look = new SkillLook(null, null, 1.0, 1.0, sound);
        return switch (template) {
            case STRIKE -> new SkillSpec("", "", template, look, 3.5, 4.5, 130, Orientation.HORIZONTAL, 24, 160, 1, 4, 12.0, 1.8, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case SWEEP -> new SkillSpec("", "", template, look, 4.5, 4.5, 130, Orientation.HORIZONTAL, 14, 120, 1, 4, 12.0, 1.3, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case BARRAGE -> new SkillSpec("", "", template, look, 1.0, 12.0, 130, Orientation.HORIZONTAL, 12, 200, 5, 5, 14.0, 0.5, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case DASH -> new SkillSpec("", "", template, look, 1.0, 10.0, 130, Orientation.HORIZONTAL, 16, 180, 1, 4, 12.0, 1.5, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case WAVE -> new SkillSpec("", "", template, look, 1.3, 16.0, 14, Orientation.HORIZONTAL, 16, 140, 1, 4, 14.0, 1.2, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case NOVA -> new SkillSpec("", "", template, look, 5.0, 5.0, 130, Orientation.HORIZONTAL, 16, 180, 1, 4, 10.0, 1.2, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case COMBO -> new SkillSpec("", "", template, look, 1.0, 1.0, 130, Orientation.HORIZONTAL, 4, 240, 1, 4, 12.0, 0.0, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
            case BEAM -> new SkillSpec("", "", template, look, 1.0, 14.0, 130, Orientation.HORIZONTAL, 18, 200, 1, 4, 12.0, 1.6, List.of(), 0, 0, Target.ENEMIES, 0.0, List.of(), List.of(), null);
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
            case BARRAGE -> windupTicks + count * intervalTicks + 2;
            case DASH -> windupTicks + 10;
            case NOVA -> windupTicks + (int) Math.ceil(radius / Math.max(0.1, speed / 20.0)) + 2;
            case BEAM -> windupTicks + 6;
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
}
