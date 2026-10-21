package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The assassin's combo with a deployed weapon (a lantern of shadow): traps, smoke and shadows, set down where the assassin aims and gone within 2.5 s
 * (the same kind placed again replaces the old one). Normal: 1 a smoke lantern (20% a pulse, 5 pulses, slows 25%), 2 two caltrop lanterns (15% x4 each,
 * slows 40%), 3 a tripwire mine for 2.7 s (130%, slows), 4 three smoke bombs in a row (13% x3 each, slow 30%), 5 nightfall: a beacon that drops a shadow
 * on the ground round it every 6 ticks (12% each, 8). Heavy: 0 a smoke screen under the feet (20% a pulse, slows), 1 a decoy that bursts after 1.8 s
 * (120%), 2 three mines in a line (40% each), 3 three shadow lanterns that stab the nearest enemy (7% a stab), 4 a veil of night over the aim (20% a pulse).
 */
public final class AssassinDeployCombo extends BaseCombo {

    static final String PLAN = "assassin_deploy";

    public AssassinDeployCombo() {
        super("assassin", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.15, 1.3, 0.13, 0.12}, new int[]{1, 2, 1, 3, 8},
                new double[]{0.2, 1.2, 0.4, 0.07, 0.2}, new int[]{140, 0, 0, 0, 0}, 40));
    }

    private void smoke(MoveKit kit, Location at, double radius) {
        kit.fx(at).particle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, at.getX(), at.getY() + 0.5, at.getZ(), 5, radius * 0.4, 0.3, radius * 0.4, 0.01);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.axes()[1];
        switch (step.stage()) {
            case 1 -> ComboParts.place(kit, "assassin_n1", target, 50, 10, 3.0, rate, 25.0, 0.0, here -> smoke(kit, here, 3.0));
            case 2 -> {
                for (int index = 0; index < count; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "assassin_n2_" + index;
                    kit.later(index, () -> ComboParts.place(kit, key, spot, 40, 10, 2.0, rate, 40.0, 0.0, null));
                }
            }
            case 3 -> ComboParts.mine(kit, "assassin_mine", target, 2.0, 3.4, rate, 45.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < count; index++) {
                    Location ground = kit.ground(kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index)));
                    String key = "assassin_n4_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, ground, 44, 14, 2.0, rate, 30.0, 0.0, here -> smoke(kit, here, 2.0)));
                }
            }
            default -> ComboParts.rainBeacon(kit, "assassin_night_rain", target, 50, 6, 3.4, rate, Sfx.MAGIC_DARK);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
                ComboParts.sigil(kit, "assassin_screen", at, 3.4, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.4)) kit.slow(victim, 40.0, 1.0);
                    kit.fx(here).particle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, here.getX(), here.getY() + 0.6, here.getZ(), 6, 1.2, 0.3, 1.2, 0.01);
                });
            }
            case 1 -> ComboParts.bomb(kit, "assassin_decoy", target, 36, 3.2, rate, 0.0);
            case 2 -> {
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.facing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location spot = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.5 * index)));
                    kit.later(index * 2L, () -> ComboParts.mine(kit, "assassin_mines_" + spot.getBlockX() + "_" + spot.getBlockZ(), spot, 1.8, 2.8, rate, 0.0));
                }
            }
            case 3 -> {
                // three shadow lanterns round the aim that stab the nearest enemy
                for (int index = 0; index < 3; index++) {
                    double angle = Math.PI * 2.0 * index / 3.0 + Math.PI * 0.5;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2));
                    String key = "assassin_turret_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 6, 8.0, rate, null));
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
