package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The mage's combo with a melee weapon (a spell blade): magic poured into the blade. Normal: 1 two quick magic cuts (60% x2), 2 a crossing pair of
 * cuts (50% x2), 3 a rune strike (120%, a rune bursts), 4 three bolts shot from the blade (35% x3), 5 a nova of the blade (150%).
 * Heavy: 0 a blink forward through the enemies (100% on all in the way), 1 a rune circle on the ground for 2.5 s (25% a pulse), 2 a wave of
 * magic along the ground (130%), 3 a drain (110%, heals 3%), 4 a storm of blades for 1 s (18% x8).
 */
public final class MageMeleeCombo extends BaseCombo {

    static final String PLAN = "mage_melee";

    public MageMeleeCombo() {
        super("mage", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.6, 0.5, 1.2, 0.35, 1.5}, new int[]{2, 2, 1, 3, 1},
                new double[]{1.0, 0.25, 1.3, 1.1, 0.18}, new int[]{120, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.6));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        switch (step.stage()) {
            case 1 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> {
                        kit.slash("arc", 2.3, 130.0, 40.0, which % 2 == 0 ? 0.0 : 180.0, 2.0, 0.3, 0.0, 1.0, 0.1, 0.0);
                        kit.sound(feet, Sfx.MAGIC, 0.5f, 1.6f + which * 0.1f);
                        kit.hitAll(kit.cone(3.2, 65.0), rate);
                    });
                }
            }
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index, () -> {
                        kit.slash("line", 2.6, 120.0, 0.0, which == 0 ? 50.0 : -50.0, 2.0, 0.3, 0.0, 1.1, 0.1, 0.0);
                        kit.sound(feet, Sfx.WIND, 0.7f, 1.4f);
                        kit.hitAll(kit.cone(3.3, 70.0), rate);
                    });
                }
            }
            case 3 -> {
                kit.slash("drop", 2.6, 130.0, 15.0, 0.0, 2.0, 0.2, 0.0, 1.3, 0.14, 0.0);
                kit.sound(feet, Sfx.METAL, 1.0f, 1.0f);
                Location rune = feet.clone().add(kit.facing().multiply(2.6));
                kit.hitAll(kit.cone(3.4, 55.0), rate);
                // a rune where the blade struck: it shows, then bursts
                Location at = kit.ground(rune);
                for (int tick = 0; tick < 6; tick++) {
                    double r = 1.6 - 0.8 * tick / 5.0;
                    kit.later(tick, () -> kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), r, 32, kit.palette(), 0.5, 0.3, 1.0));
                }
                kit.later(6, () -> {
                    kit.burst(at.clone().add(0.0, 0.3, 0.0), 2.0);
                    kit.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.2f);
                });
            }
            case 4 -> {
                Vector facing = kit.facing();
                for (int index = 0; index < hits; index++) {
                    double side = (index - 1) * 0.5;
                    Location start = kit.eye().add(facing.clone().multiply(0.9)).add(new Vector(-facing.getZ() * side, -0.4, facing.getX() * side));
                    kit.later(index, () -> {
                        kit.sound(start, Sfx.MAGIC, 0.6f, 1.8f);
                        kit.bolt(start, facing, 1.6, 7.0, rate, end -> kit.burst(end, 0.9));
                    });
                }
            }
            default -> {
                // the finisher: the blade lets everything go in a nova
                kit.slash("arc", 3.0, 200.0, 70.0, 0.0, 1.6, 0.4, 0.0, 1.4, 0.16, 0.0);
                kit.shockwave(feet, 1.0, 4.2, 5, 1.2);
                kit.sound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
                kit.sound(feet, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.9f);
                kit.fx(feet).sphereShell(feet.clone().add(0.0, 1.0, 0.0), 1.8, 0.0, 48, kit.palette(), 1.0);
                for (LivingEntity target : kit.around(feet, 4.2)) {
                    if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.8, 0.3);
                }
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.7));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a blink forward 6 blocks: everything on the way is cut as the mage passes
                Location from = feet.clone();
                Location to = from.clone().add(facing.clone().multiply(6.0));
                Location safe = kit.ground(to);
                kit.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.5f);
                kit.fx(from).sphereShell(from.clone().add(0.0, 1.0, 0.0), 0.8, 0.0, 32, kit.palette(), 1.0);
                kit.hitAll(kit.corridor(from.clone().add(0.0, 1.0, 0.0), facing, 6.0, 1.4), rate);
                kit.line(from.clone().add(0.0, 1.0, 0.0), to.clone().add(0.0, 1.0, 0.0), 0.2, 1.0, 1.3);
                if (safe.getBlock().isPassable() && safe.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                    safe.setYaw(from.getYaw());
                    safe.setPitch(from.getPitch());
                    kit.player.teleport(safe);
                    kit.player.setFallDistance(0.0f);
                }
                kit.fx(kit.player.getLocation()).sphereShell(kit.player.getLocation().add(0.0, 1.0, 0.0), 0.8, 0.0, 32, kit.palette(), 1.0);
                kit.sound(kit.player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.2f);
            }
            case 1 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.6f);
                ComboParts.sigil(kit, "mage_runes", at, 3.5, rate, 50, 10, ignored -> {
                    for (int arm = 0; arm < 3; arm++) {
                        double angle = Math.PI * 2.0 * arm / 3.0 + kit.player.getTicksLived() * 0.1;
                        kit.fx(at).dust(at.getX() + Trig.cos(angle) * 2.2, at.getY() + 0.2, at.getZ() + Trig.sin(angle) * 2.2, kit.palette(), 1.0, 1.4);
                    }
                });
            }
            case 2 -> {
                // a wave of magic running along the ground, widening
                kit.slash("line", 3.4, 100.0, 0.0, 0.0, 1.8, 0.2, 0.0, 1.4, 0.14, 0.0);
                kit.sound(feet, Sfx.MAGIC, 1.0f, 1.2f);
                for (int index = 1; index <= 8; index++) {
                    double along = index;
                    kit.later(index - 1, () -> {
                        Location at = feet.clone().add(facing.clone().multiply(along)).add(0.0, 0.3, 0.0);
                        kit.fx(at).flatRing(at, 0.9 + along * 0.18, 24, kit.palette(), 0.3 + 0.08 * along, 0.3, 1.0);
                    });
                }
                for (LivingEntity target : kit.corridor(feet.clone().add(0.0, 0.5, 0.0), facing, 8.0, 1.8)) {
                    if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.6, 0.25);
                }
            }
            case 3 -> {
                // a drain: the cone is cut and its life flows to the mage
                kit.slash("drop", 2.8, 140.0, 30.0, 0.0, 2.0, 0.3, 0.0, 1.3, 0.16, 0.0);
                kit.sound(feet, Sfx.MAGIC_DARK, 0.9f, 1.4f);
                int hit = 0;
                for (LivingEntity target : kit.cone(3.8, 60.0)) {
                    if (!kit.hit(target, rate)) continue;
                    hit++;
                    Location to = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
                    kit.line(to, kit.player.getEyeLocation().subtract(0.0, 0.4, 0.0), 0.9, 0.2, 1.0);
                }
                if (hit > 0) kit.heal(3.0);
            }
            default -> {
                // a storm of blades: they turn round the mage for a second
                kit.sound(feet, Sfx.WIND, 1.0f, 0.7f);
                kit.every(21, age -> {
                    Location center = kit.player.getLocation().add(0.0, 1.0, 0.0);
                    for (int arm = 0; arm < 4; arm++) {
                        double base = age * 0.8 + arm * Math.PI / 2.0;
                        kit.fx(center).spiral(center, new Vector(1, 0, 0), new Vector(0, 0, 1), new Vector(0, 1, 0), 2.2, 3.4, -0.17, base - 0.9, 0.3, 9, kit.palette(), 1.3);
                    }
                    if (age % 3 == 0 && age > 0) kit.hitAll(kit.around(kit.player.getLocation(), 3.5), rate);
                    return false;
                });
            }
        }
    }
}
