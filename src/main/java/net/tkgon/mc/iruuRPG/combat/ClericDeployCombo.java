package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * The cleric's combo with a deployed weapon (a lantern of sanctuary): lanterns that are set down and stay a few seconds, healing and hurting
 * what is round them. Everything is placed where the cleric aims and ends by itself within 2.5 s; the same kind placed again replaces the old one.
 * Normal: 1 a lantern of mercy (20% a pulse, 5 pulses, heals 0.6%), 2 two small lanterns (15% x4 pulses each), 3 a lantern of prayer (20% x5, a wider
 * reach, heals 1.5% a pulse), 4 three lanterns in a row (13% x3 each, heal 0.5%), 5 six lanterns in a ring round the aim (12% x2 each, heals 3%).
 * Heavy: 0 a sanctuary under the feet (15% a pulse, heals 1%), 1 four lanterns running ahead (20% x3 each), 2 a lantern that swells for 2 s, pulling
 * the enemies in, and bursts (100%, slows), 3 four lanterns round the aim that shoot light at the nearest enemy (7% a shot), 4 a great sanctuary (20% a pulse,
 * heals 1.2%).
 */
public final class ClericDeployCombo extends BaseCombo {

    static final String PLAN = "cleric_deploy";

    public ClericDeployCombo() {
        super("healer", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.15, 0.2, 0.13, 0.12}, new int[]{1, 2, 1, 3, 6},
                new double[]{0.15, 0.2, 1.0, 0.07, 0.2}, new int[]{160, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.4));
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.axes()[1];
        switch (step.stage()) {
            case 1 -> ComboParts.place(kit, "cleric_n1", target, 50, 10, 3.0, rate, 0.0, 0.6, null);
            case 2 -> {
                for (int index = 0; index < count; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "cleric_n2_" + index;
                    kit.later(index, () -> ComboParts.place(kit, key, spot, 40, 10, 2.4, rate, 0.0, 0.4, null));
                }
            }
            case 3 -> ComboParts.place(kit, "cleric_n3", target, 55, 10, 3.5, rate, 0.0, 1.5, null);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < count; index++) {
                    Location ground = kit.ground(kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index)));
                    String key = "cleric_n4_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, ground, 44, 14, 2.0, rate, 0.0, 0.5, null));
                }
            }
            default -> {
                // the finisher: a ring of lanterns that each light up twice, and the cleric is healed once
                for (int index = 0; index < count; index++) {
                    double angle = Math.PI * 2.0 * index / count;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 1.8, 0.0, Math.sin(angle) * 1.8));
                    String key = "cleric_n5_" + index;
                    kit.later(index, () -> ComboParts.placeAt(kit, key, spot, 42, 20, 1.8, rate, 0.0, 0.0, null));
                }
                kit.heal(3.0);
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.45));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
                ComboParts.sigil(kit, "cleric_deploy_sanctuary", at, 3.5, rate, 50, 10, ignored -> kit.heal(1.0));
            }
            case 1 -> {
                // four lanterns set down one after the other running ahead
                Vector facing = kit.facing();
                kit.sound(feet, Sfx.WIND, 0.9f, 0.8f);
                for (int index = 1; index <= 4; index++) {
                    Location at = kit.ground(feet.clone().add(facing.clone().multiply(2.0 * index)));
                    String key = "cleric_trail_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, at, 44, 14, 2.0, rate, 0.0, 0.3, null));
                }
            }
            case 2 -> ComboParts.pullBurst(kit, target.clone().add(0.0, 1.5, 0.0), 4.5, rate, 40.0, 40);
            case 3 -> {
                // four lanterns round the aim that shoot light at the nearest enemy
                for (int index = 0; index < 4; index++) {
                    double angle = Math.PI * 0.5 * index + Math.PI * 0.25;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0));
                    String key = "cleric_guard_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 10, 7.0, rate, null));
                }
            }
            default -> {
                kit.sound(target, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
                ComboParts.sigil(kit, "cleric_deploy_great", target, 6.0, rate, 50, 10, ignored -> {
                    if (kit.player.getLocation().distanceSquared(target) <= 36.0) kit.heal(1.2);
                });
            }
        }
    }
}
