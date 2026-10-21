package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The assassin's combo with a deployed weapon (a lantern of shadow): traps and decoys. Normal: 1 a shadow lantern (100%), 2 two caltrops (60% x2),
 * 3 a tripwire mine for 2.7 s (130%, slows), 4 three smoke puffs in a row (40% x3), 5 shadows fall on the aim (30% x5). Heavy: 0 a smoke screen
 * under the feet for 2.5 s (slows, 20% a pulse), 1 a decoy lantern that bursts after a moment (120%), 2 three mines in a line (40% each),
 * 3 four seeking lanterns (35% x4), 4 a veil of night over the aim for 2.5 s (30% a pulse).
 */
public final class AssassinDeployCombo extends BaseCombo {

    static final String PLAN = "assassin_deploy";

    public AssassinDeployCombo() {
        super("assassin", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{1.0, 0.6, 1.3, 0.4, 0.3}, new int[]{1, 2, 1, 3, 5},
                new double[]{0.2, 1.2, 0.4, 0.35, 0.3}, new int[]{140, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.axes()[1];
        switch (step.stage()) {
            case 1 -> ComboParts.lobBurst(kit, target, 3.0, rate, Sound.BLOCK_GLASS_BREAK, null);
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    Location spot = target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0));
                    kit.later(index, () -> ComboParts.lobBurst(kit, spot, 2.5, rate, Sound.BLOCK_CHAIN_BREAK, null));
                }
            }
            case 3 -> ComboParts.mine(kit, "assassin_mine", target, 2.0, 3.4, rate, 45.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < hits; index++) {
                    Location ground = kit.ground(kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index)));
                    kit.later(index * 2L, () -> {
                        kit.fx(ground).particle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, ground.getX(), ground.getY() + 0.5, ground.getZ(), 8, 0.6, 0.3, 0.6, 0.01);
                        kit.burst(ground.clone().add(0.0, 0.3, 0.0), 1.8);
                        kit.sound(ground, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.4f, 1.6f);
                        kit.hitAll(kit.around(ground, 1.9), rate);
                    });
                }
            }
            default -> ComboParts.rain(kit, target, 3.4, hits, rate, 1.8, Sound.BLOCK_GLASS_BREAK);
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
            case 1 -> {
                // a decoy: it shows where it fell, and bursts a moment later
                kit.lob(ComboParts.hand(kit), target, 7, 1.6, land -> {
                    kit.sound(land, Sound.BLOCK_TRIPWIRE_ATTACH, 0.9f, 1.2f);
                    for (int tick = 0; tick < 12; tick++) {
                        kit.later(tick, () -> ComboParts.orb(kit, land.clone().add(0.0, 0.6, 0.0), 0.3 + 0.03 * kit.player.getTicksLived() % 3));
                    }
                    kit.later(12, () -> {
                        kit.burst(land.clone().add(0.0, 0.4, 0.0), 3.2);
                        kit.shockwave(land, 1.0, 3.2, 4, 1.0);
                        kit.sound(land, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
                        kit.hitAll(kit.around(land, 3.2), rate);
                    });
                });
            }
            case 2 -> {
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.facing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location spot = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.5 * index)));
                    kit.later(index * 2L, () -> ComboParts.mine(kit, "assassin_mines_" + spot.getBlockX() + "_" + spot.getBlockZ(), spot, 1.8, 2.8, rate, 0.0));
                }
            }
            case 3 -> ComboParts.homingVolley(kit, 4, rate, 14.0, end -> kit.burst(end, 1.2));
            default -> {
                // a veil of night: the aim goes dark and what stands in it is cut again and again
                kit.sound(target, Sfx.MAGIC_DARK, 1.0f, 0.7f);
                ComboParts.sigil(kit, "assassin_night", target, 4.6, rate, 50, 10, here ->
                        kit.fx(here).particle(org.bukkit.Particle.SQUID_INK, here.getX(), here.getY() + 1.0, here.getZ(), 10, 2.0, 0.8, 2.0, 0.01));
            }
        }
    }
}
