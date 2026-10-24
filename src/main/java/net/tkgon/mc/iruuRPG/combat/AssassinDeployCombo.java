package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The assassin's combo with a deployed weapon (a canister of smoke): smoke and the knives that fly out of it, made at the placement of the weapon (see {@link DeployHold}) and
 * staying as long as the placement does. A canister fills the place with smoke (the enemies in it are slowed and cut each pulse) and throws a knife at the nearest enemy
 * from the middle of it. Normal: 1 a canister (12% a pulse, a knife of 20% every 14 ticks), 2 two small canisters (a knife of 10% each), 3 a smoke bomb that waits and bursts
 * in a great cloud (100%, slows), 4 three canisters in a row (a knife of 10% each), 5 nightfall: a column of black smoke that drops knives round it (20% each). Heavy: 0 a smoke
 * screen under the feet (20% a pulse, slows; cooldown 7 s), 1 a decoy that bursts in smoke after 1.8 s (120%), 2 three smoke bombs in a line (40% each), 3 three knife
 * canisters round the aim (7% a knife), 4 a veil of night over the aim (20% a pulse).
 */
public final class AssassinDeployCombo extends BaseCombo {

    static final String PLAN = "assassin_deploy";

    public AssassinDeployCombo() {
        super("assassin", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.1, 1.0, 0.1, 0.2}, new int[]{1, 1, 1, 1, 1},
                new double[]{0.2, 1.2, 0.4, 0.07, 0.2}, new int[]{140, 0, 0, 0, 0}, 40));
    }

    private void smoke(MoveKit kit, Location at, double radius) {
        kit.fx(at).particle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, at.getX(), at.getY() + 0.5, at.getZ(), 2, radius * 0.4, 0.3, radius * 0.4, 0.01);
    }

    /**
     * A canister of smoke standing at {@code at}: a cloud of {@code radius} blocks that cuts and slows what is in it every {@code 10} ticks (a share of {@code rate}) and, every
     * {@code knifeEvery} ticks, throws a knife of {@code rate} out of the middle of it at the nearest enemy within 9 blocks. It stays for as long as the placement does.
     */
    private void canister(MoveKit kit, String key, Location at, double radius, double rate, int knifeEvery, double slowPercent, int life) {
        kit.sound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 0.7f);
        kit.zone(key, at, kit.linger(life), 2, zone -> {
            // (the smoke and the ring are drawn sparingly: a cloud that was too thick to see through)
            if (zone.age() % 6 == 0) smoke(kit, at, radius);
            if (zone.age() % 10 == 0) {
                if (zone.age() % 20 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), radius, (int) Math.max(14, radius * 5), kit.palette(), 0.3, 0.2, 0.9);
                for (LivingEntity victim : kit.around(at, radius)) {
                    if (kit.hit(victim, rate * 0.6) && slowPercent > 0.0) kit.slow(victim, slowPercent, 1.5);
                }
            }
            if (zone.age() % knifeEvery != 0 || zone.age() == 0) return;
            LivingEntity best = null;
            double bestDistance = 81.0;
            for (LivingEntity candidate : kit.around(at, 9.0)) {
                double d = candidate.getLocation().distanceSquared(at);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate;
                }
            }
            if (best == null) return;
            Location from = at.clone().add(0.0, 1.0, 0.0);
            Vector toward = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0).toVector().subtract(from.toVector());
            if (toward.lengthSquared() < 1.0E-6) return;
            Blades.fly(kit, from, toward, 1.6, 10.0, rate, null);
        }, zone -> smoke(kit, at, radius * 1.6));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location target = ComboParts.target(kit);
        Vector right = kit.originRight();
        switch (step.stage()) {
            case 1 -> {
                ComboParts.appear(kit, target);
                kit.later(2L, () -> canister(kit, "assassin_n1", target, 3.0, rate, 14, 25.0, 50));
            }
            case 2 -> {
                for (int index = 0; index < 2; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "assassin_n2_" + index;
                    kit.later(index, () -> canister(kit, key, spot, 2.2, rate, 14, 25.0, 40));
                }
            }
            case 3 -> ComboParts.mine(kit, "assassin_mine", target, 2.0, 3.6, rate, 45.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.origin().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.originFacing().multiply(6.0);
                for (int index = 0; index < 3; index++) {
                    Location ground = kit.ground(kit.origin().add(line.clone().multiply(0.3 + 0.35 * index)));
                    String key = "assassin_n4_" + index;
                    kit.later(index * 2L, () -> canister(kit, key, ground, 2.0, rate, 16, 20.0, 44));
                }
            }
            default -> ComboParts.rainBeacon(kit, "assassin_night_rain", target, 50, 6, 3.4, rate, Sfx.MAGIC_DARK);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
                ComboParts.sigil(kit, "assassin_screen", at, 3.4, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.4)) kit.slow(victim, 40.0, 1.0);
                    smoke(kit, here, 3.4);
                });
            }
            case 1 -> ComboParts.bomb(kit, "assassin_decoy", target, 36, 3.2, rate, 0.0);
            case 2 -> {
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.originFacing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location spot = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.5 * index)));
                    kit.later(index * 2L, () -> ComboParts.mine(kit, "assassin_mines_" + spot.getBlockX() + "_" + spot.getBlockZ(), spot, 1.8, 2.8, rate, 0.0));
                }
            }
            case 3 -> {
                // three canisters round the aim that throw knives at the nearest enemy
                for (int index = 0; index < 3; index++) {
                    double angle = Math.PI * 2.0 * index / 3.0 + Math.PI * 0.5;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2));
                    String key = "assassin_turret_" + index;
                    kit.later(index, () -> canister(kit, key, spot, 1.8, rate, 6, 0.0, 50));
                }
            }
            default -> {
                // a veil of night: the aim goes dark and what stands in it is cut again and again
                kit.sound(target, Sfx.MAGIC_DARK, 1.0f, 0.7f);
                ComboParts.sigil(kit, "assassin_night", target, 4.6, rate, 50, 10, here ->
                        kit.fx(here).particle(org.bukkit.Particle.SQUID_INK, here.getX(), here.getY() + 1.0, here.getZ(), 10, 2.0, 0.8, 2.0, 0.01));
            }
        }
    }
}
