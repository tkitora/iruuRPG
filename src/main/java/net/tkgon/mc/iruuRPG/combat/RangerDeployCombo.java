package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The ranger's combo with a deployed weapon (the lantern): snares and arrow lanterns, set down where the ranger aims and gone within 2.5 s
 * (the same kind placed again replaces the old one). Normal: 1 a snare lantern (25% a pulse, 5 pulses, slows 30%), 2 two arrow lanterns that shoot
 * the nearest enemy (12% a shot), 3 a mine that waits for 2.7 s and goes off when an enemy comes close (130%, slows), 4 three net lanterns in a row
 * (20% x3 each, slows 40%), 5 a hail beacon that drops an arrow every 6 ticks round it (12% each, 8 arrows). Heavy: 0 a caltrop sigil under the feet
 * (25% a pulse), 1 a cage of eight lanterns round the aim (15% a pulse, slows 60%), 2 a wall of five lanterns (10% a pulse), 3 three arrow lanterns
 * (8% a shot), 4 a great lantern that pulls the enemies in for 2 s and bursts (150%). A deployed weapon's damage powers up the ranger's
 * melee attacks (the loop).
 */
public final class RangerDeployCombo implements ClassCombo {

    static final String PLAN = "ranger_deploy";

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{0.25, 0.12, 1.3, 0.2, 0.12}, new int[]{1, 2, 1, 3, 8},
            new double[]{0.25, 0.15, 0.1, 0.08, 1.5}, new int[]{120, 0, 0, 0, 0}, 40);

    @Override
    public String classId() {
        return "gifted";
    }

    @Override
    public AttackType type() {
        return AttackType.DEPLOY;
    }

    @Override
    public ComboSpec spec() {
        return SPEC;
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.3));
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.originRight();
        switch (step.stage()) {
            case 1 -> ComboParts.place(kit, "ranger_n1", target, 50, 10, 3.0, rate, 30.0, 0.0, null);
            case 2 -> {
                for (int index = 0; index < count; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "ranger_n2_" + index;
                    kit.later(index, () -> ComboParts.turret(kit, key, spot, 40, 8, 9.0, rate, null));
                }
            }
            case 3 -> ComboParts.mine(kit, "ranger_mine", target, 2.0, 3.5, rate, 40.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.origin().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.originFacing().multiply(6.0);
                for (int index = 0; index < count; index++) {
                    Location ground = kit.ground(kit.origin().add(line.clone().multiply(0.3 + 0.35 * index)));
                    String key = "ranger_n4_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, ground, 44, 14, 2.0, rate, 40.0, 0.0, null));
                }
            }
            default -> ComboParts.rainBeacon(kit, "ranger_hail", target, 50, 6, 3.6, rate, Sfx.WIND);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                // a sigil of caltrops on the ground under the feet
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                ComboParts.sigil(kit, "ranger_sigil", at, 3.5, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.5)) kit.slow(victim, 30.0, 1.0);
                });
            }
            case 1 -> {
                // a cage: eight lanterns in a ring round the aim; what is inside is cut and slowed
                kit.sound(target, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
                kit.zone("ranger_cage", target, kit.linger(50), 4, zone -> {
                    boolean pulse = zone.age() % 12 == 0;
                    for (int index = 0; index < 8; index++) {
                        double angle = Math.PI * 2.0 * index / 8.0;
                        ComboParts.standLantern(kit, target.clone().add(Math.cos(angle) * 3.0, 0.0, Math.sin(angle) * 3.0), 1.2, pulse ? 1.0 : 0.0);
                    }
                    kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), 3.0, 48, kit.palette(), 0.45, 0.15, 0.9);
                    if (!pulse) return;
                    kit.shockwave(target, 1.0, 3.0, 3, 0.9);
                    for (LivingEntity victim : kit.around(target, 3.0)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 60.0, 1.5);
                    }
                }, zone -> kit.shockwave(target, 2.5, 3.2, 2, 0.8));
            }
            case 2 -> {
                // a wall of five lanterns across the aim
                Vector forward = kit.originFacing();
                Vector side = new Vector(-forward.getZ(), 0.0, forward.getX());
                Location middle = kit.ground(feet.clone().add(forward.clone().multiply(4.5)));
                kit.sound(feet, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
                kit.zone("ranger_wall", middle, kit.linger(50), 4, zone -> {
                    boolean pulse = zone.age() % 8 == 0;
                    for (int index = -2; index <= 2; index++) {
                        Location at = middle.clone().add(side.clone().multiply(index * 1.6));
                        ComboParts.standLantern(kit, at, 1.3, pulse ? 1.0 : 0.0);
                        if (pulse) kit.hitAll(kit.around(at, 1.5), rate);
                    }
                }, zone -> kit.shockwave(middle, 1.0, 2.5, 3, 0.8));
            }
            case 3 -> {
                // three arrow lanterns round the aim
                for (int index = 0; index < 3; index++) {
                    double angle = Math.PI * 2.0 * index / 3.0 + Math.PI * 0.5;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2));
                    String key = "ranger_turret_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 8, 9.0, rate, null));
                }
            }
            default -> {
                // a great lantern: it swells for 2 seconds and pulls the enemies in, then bursts
                Location at = target.clone().add(0.0, 1.5, 0.0);
                ComboParts.pullBurst(kit, at, 5.0, rate, 0.0, 40);
            }
        }
    }
}
