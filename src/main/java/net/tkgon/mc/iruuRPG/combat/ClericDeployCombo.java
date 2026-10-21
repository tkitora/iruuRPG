package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The cleric's combo with a deployed weapon (a lantern of sanctuary): lanterns that heal where they fall. Normal: 1 a lantern (100%, heals 2%),
 * 2 two lanterns (60% x2), 3 a prayer lantern for 2.5 s (25% a pulse, heals 1.5% a pulse), 4 three lanterns in a row (40% x3, heal 1% each),
 * 5 six lanterns in a ring round the aim (25% x6, heals 4%). Heavy: 0 a sanctuary under the feet for 2.5 s (20% a pulse, heals 1%), 1 a rush of
 * light forward leaving lanterns (120%, heals 2%), 2 a lantern that pulls enemies in and bursts (130%, slows), 3 four seeking lanterns (35% x4),
 * 4 a great sanctuary for 2.5 s (30% a pulse, heals 6% in all).
 */
public final class ClericDeployCombo extends BaseCombo {

    static final String PLAN = "cleric_deploy";

    public ClericDeployCombo() {
        super("healer", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{1.0, 0.6, 0.25, 0.4, 0.25}, new int[]{1, 2, 5, 3, 6},
                new double[]{0.2, 1.2, 1.3, 0.35, 0.3}, new int[]{160, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.4));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.axes()[1];
        switch (step.stage()) {
            case 1 -> ComboParts.lobBurst(kit, target, 3.0, rate, Sound.BLOCK_AMETHYST_BLOCK_BREAK, land -> kit.heal(2.0));
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    Location spot = target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0));
                    kit.later(index, () -> ComboParts.lobBurst(kit, spot, 2.5, rate, Sound.BLOCK_AMETHYST_BLOCK_BREAK, null));
                }
            }
            case 3 -> {
                // a lantern of prayer where it falls: five pulses of light, each healing the cleric if it stands near
                kit.lob(ComboParts.hand(kit), target, 8, 1.8, land -> {
                    kit.sound(land, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
                    kit.zone("cleric_prayer", land, 50, 10, zone -> {
                        ComboParts.orb(kit, land.clone().add(0.0, 0.8, 0.0), 0.4);
                        kit.fx(land).flatRing(land.clone().add(0.0, 0.15, 0.0), 3.0, 48, kit.palette(), 0.6, 0.4, 1.0);
                        kit.hitAll(kit.around(land, 3.0), rate);
                        if (kit.player.getLocation().distanceSquared(land) <= 9.0) kit.heal(1.5);
                    }, zone -> kit.shockwave(land, 2.6, 3.0, 2, 0.9));
                });
            }
            case 4 -> {
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < hits; index++) {
                    Location ground = kit.ground(kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index)));
                    kit.later(index * 2L, () -> {
                        ComboParts.orb(kit, ground.clone().add(0.0, 0.5, 0.0), 0.4);
                        kit.burst(ground.clone().add(0.0, 0.3, 0.0), 1.8);
                        kit.sound(ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
                        kit.hitAll(kit.around(ground, 1.9), rate);
                        kit.heal(1.0);
                    });
                }
            }
            default -> {
                for (int index = 0; index < hits; index++) {
                    double angle = Math.PI * 2.0 * index / hits;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 1.6, 0.0, Math.sin(angle) * 1.6));
                    kit.later(index, () -> {
                        ComboParts.orb(kit, spot.clone().add(0.0, 0.6, 0.0), 0.4);
                        kit.burst(spot.clone().add(0.0, 0.3, 0.0), 2.2);
                        kit.sound(spot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
                        kit.hitAll(kit.around(spot, 2.2), rate);
                    });
                }
                kit.heal(4.0);
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
                // a rush of light forward, lanterns left behind it
                Vector facing = kit.facing();
                kit.sound(feet, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
                kit.dash(facing, 1.2, 5, end -> {
                    kit.shockwave(end, 1.0, 3.0, 3, 1.0);
                    kit.heal(2.0);
                });
                for (int index = 1; index <= 4; index++) {
                    Location at = kit.ground(feet.clone().add(facing.clone().multiply(1.8 * index)));
                    kit.later(index, () -> {
                        ComboParts.orb(kit, at.clone().add(0.0, 0.5, 0.0), 0.35);
                        kit.burst(at.clone().add(0.0, 0.3, 0.0), 1.6);
                        kit.hitAll(kit.around(at, 1.8), rate * 0.25);
                    });
                }
            }
            case 2 -> ComboParts.pullBurst(kit, target.clone().add(0.0, 1.5, 0.0), 4.5, rate, 40.0);
            case 3 -> ComboParts.homingVolley(kit, 4, rate, 14.0, end -> kit.burst(end, 1.2));
            default -> {
                kit.sound(target, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
                ComboParts.sigil(kit, "cleric_deploy_great", target, 6.0, rate, 50, 10, ignored -> {
                    if (kit.player.getLocation().distanceSquared(target) <= 36.0) kit.heal(1.2);
                });
            }
        }
    }
}
