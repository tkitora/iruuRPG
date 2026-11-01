package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * The basic combo of a staff, for every class that may use a ranged weapon without it being a favorite of the class: bolts of the weapon's element. Normal: 1 a bolt (50%),
 * 2 two bolts (35% each), 3 a fan of three bolts (30% each), 4 a beam through everything ahead (100%), 5 a charged orb that bursts (180%: 40% where it lands, the rest
 * round). Heavy: 0 a nova round the player (50%, cooldown 3 s; it moves nobody), 1 three seeking bolts (30% each), 2 a long beam (100%), 3 a barrage of six bolts (20% each),
 * 4 a great nova (140%). The rates add up to 490% over the five bolts, which is what the plain bolts of the same time do; a longer chain is stronger per second.
 */
public final class GenericRangeCombo extends BaseCombo {

    static final String PLAN = "generic_range";

    public GenericRangeCombo() {
        super("*", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.58, 0.78, 0.98, 1.17, 1.37}, new int[]{1, 1, 1, 1, 1},
                new double[]{0.5, 0.9, 1.0, 1.2, 1.4, 0.3}, new int[]{60, 0, 0, 0, 0, 0}, 36));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.15));
        double rate = step.damageMultiplier();
        Location hand = ComboParts.hand(kit);
        Vector aim = kit.aim().toVector().subtract(hand.toVector());
        Vector direction = aim.lengthSquared() < 1.0E-6 ? kit.axes()[0] : aim.normalize();
        switch (step.stage()) {
            case 1 -> ComboParts.quickBolts(kit, 1, rate, 1.8, 28.0, 0.0);
            case 2 -> ComboParts.quickBolts(kit, 2, rate / 2.0, 1.8, 28.0, 0.04);
            case 3 -> ComboParts.fanBolts(kit, 3, 24.0, rate / 3.0, 1.8, 26.0);
            case 4 -> {
                kit.sound(hand, Sfx.MAGIC, 0.7f, 1.2f);
                ComboParts.pierce(kit, hand, direction, 14.0, 1.0, rate);
            }
            default -> {
                // a charged orb: it flies slowly and bursts where it lands
                kit.sound(hand, Sfx.MAGIC, 0.9f, 0.8f);
                kit.bolt(hand, direction, 1.3, 30.0, rate * 0.22, end -> {
                    kit.burst(end, 3.5);
                    kit.shockwave(end, 0.8, 3.5, 4, 1.1);
                    kit.hitAll(kit.around(end, 3.5), rate * 0.78);
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.15));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location hand = ComboParts.hand(kit);
        Vector direction = kit.axes()[0];
        switch (step.heavyIndex()) {
            case 0 -> {
                // a nova round the player (it moves nobody)
                kit.sound(feet, Sfx.MAGIC, 0.9f, 1.0f);
                kit.shockwave(feet, 0.8, 4.2, 4, 1.1);
                kit.hitAll(kit.around(feet, 4.2), rate);
            }
            case 1 -> ComboParts.homingVolley(kit, 3, rate / 3.0, 12.0, end -> kit.burst(end, 1.0));
            case 2 -> {
                kit.sound(hand, Sfx.MAGIC, 0.9f, 1.0f);
                ComboParts.pierce(kit, hand, direction, 20.0, 1.2, rate);
            }
            case 3 -> ComboParts.quickBolts(kit, 6, rate / 6.0, 1.8, 28.0, 0.08);
            case 4 -> {
                kit.sound(feet, Sfx.MAGIC, 1.0f, 0.7f);
                kit.shockwave(feet, 1.0, 5.8, 6, 1.2);
                kit.burst(feet.clone().add(0.0, 1.0, 0.0), 5.5);
                kit.hitAll(kit.around(feet, 5.8), rate);
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): five bolts fanned out over 40 degrees. */
    private void sixth(MoveKit kit, double rate) {
        kit.sound(kit.eye(), Sfx.MAGIC, 0.8f, 1.1f);
        ComboParts.fanBolts(kit, 5, 40.0, rate, 1.5, 16.0);
    }
}
