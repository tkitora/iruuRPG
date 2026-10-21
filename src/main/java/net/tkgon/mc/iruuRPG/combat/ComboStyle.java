package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.skill.SkillLook;

import java.util.List;

/**
 * How a melee combo looks (the `combo-style:` section of a class or an item). The lights, the finisher and the heavy
 * attack each have their own cuts; a part left out falls back to the next level (item, then class, then the built-in
 * look).
 *
 * @param look   colors / spark / size / width of the cuts (null: the weapon's element)
 * @param light  the cuts of light attack 1, 2, 3, ... (the list is gone through again if the chain is longer)
 */
public record ComboStyle(SkillLook look, List<Stage> light, Stage finisher, Stage heavy) {

    /**
     * One cut of an attack: a sweep across the way the player faces (looking up or down changes nothing).
     * {@code tilt} turns the sweep about the horizontal axis to the player's right: 0 stands it up across the facing
     * direction, 90 lays it flat. {@code roll} 0 sweeps from the right to the left, 180 from the left to the right.
     * {@code forward} / {@code drop} / {@code side} place the middle of the arc from the eyes (ahead, below, to the right).
     */
    public record Cut(double radius, double angle, double tilt, double roll, double rollJitter, double forward,
                      double drop, double duration, double scale, double delay, double side, String shape) {

        public Cut {
            shape = shape == null ? "arc" : shape;
        }

        /** A cut drawn as an arc. */
        public Cut(double radius, double angle, double tilt, double roll, double rollJitter, double forward,
                   double drop, double duration, double scale, double delay, double side) {
            this(radius, angle, tilt, roll, rollJitter, forward, drop, duration, scale, delay, side, "arc");
        }
    }

    /** The cuts of one attack, the sound ({@code swing} / {@code sweep} / {@code cut}) and a burst of sparks. */
    public record Stage(List<Cut> cuts, String sound, int burst) {
        public Stage {
            cuts = cuts == null ? List.of() : List.copyOf(cuts);
            sound = sound == null ? "swing" : sound;
        }
    }

    public ComboStyle {
        light = light == null ? List.of() : List.copyOf(light);
    }

    /** The stage drawn for {@code step}, or null when this style leaves it to the next level. */
    public Stage stageFor(MeleeCombo.Step step) {
        if (step.heavy()) return heavy;
        if (step.finisher()) return finisher;
        if (light.isEmpty()) return null;
        Stage stage = light.get((Math.max(1, step.stage()) - 1) % light.size());
        return stage.cuts().isEmpty() ? null : stage;
    }
}
