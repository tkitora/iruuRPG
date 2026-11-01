package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with a deployed weapon (a war lantern): bombs, mines and banners, set down where the warrior aims and gone within 2.5 s (the same
 * kind placed again replaces the old one). Normal: 1 a bomb that goes off after 1.5 s (100%, knocks back), 2 two bombs (60% each), 3 a mine that waits for
 * 2.7 s (130%), 4 three mines in a row (40% each, they go off when an enemy comes close), 5 a war banner planted at the aim for 2.7 s (25% a pulse, 5 pulses,
 * pushes out and slows 40%; the warrior by it takes 20% less damage). Heavy: 0 a totem under the feet (25% a pulse; the warrior on it takes 30% less damage, the enemies are slowed 50%), 1 a fault line ahead that stays 2 s (25% a pulse, lifts), 2 a barricade of five (10% a pulse,
 * pushes), 3 four bombs in a cross round the aim (35% each), 4 a great bomb that pulls the enemies in for 2 s and bursts (150%).
 */
public final class WarriorDeployCombo extends BaseCombo {

    static final String PLAN = "warrior_deploy";

    public WarriorDeployCombo() {
        super("warrior", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{1.0, 0.6, 1.3, 0.4, 0.25}, new int[]{1, 2, 1, 3, 1},
                new double[]{0.25, 0.25, 0.1, 0.35, 1.5, 0.25}, new int[]{150, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.originRight();
        switch (step.stage()) {
            case 1 -> ComboParts.bomb(kit, "warrior_n1", target, 30, 3.2, rate, 0.4);
            case 2 -> {
                for (int index = 0; index < count; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "warrior_n2_" + index;
                    kit.later(index, () -> ComboParts.bomb(kit, key, spot, 28, 2.6, rate, 0.3));
                }
            }
            case 3 -> ComboParts.mine(kit, "warrior_mine", target, 2.0, 3.6, rate, 0.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.origin().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.originFacing().multiply(6.0);
                for (int index = 0; index < count; index++) {
                    Location ground = kit.ground(kit.origin().add(line.clone().multiply(0.3 + 0.35 * index)));
                    kit.later(index * 2L, () -> ComboParts.mine(kit, "warrior_mines_" + ground.getBlockX() + "_" + ground.getBlockZ(), ground, 1.8, 2.4, rate, 0.0));
                }
            }
            default -> {
                // a banner is planted at the aim: it stands for 2.7 s and every 11 ticks a wave pushes what is near it out
                kit.sound(target, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.7f);
                kit.zone("warrior_banner", target, kit.linger(55), 2, zone -> {
                    boolean pulse = zone.age() % 11 == 0;
                    for (double y = 0.0; y <= 3.0; y += 0.4) kit.fx(target).dust(target.getX(), target.getY() + y, target.getZ(), kit.palette(), 0.3 + 0.7 * y / 3.0, 1.2);
                    kit.fx(target).sphereShell(target.clone().add(0.0, 3.2, 0.0), 0.3 + (pulse ? 0.15 : 0.0), 0.0, 14, kit.palette(), 1.0);
                    if (zone.age() % 6 == 0) kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), 4.2, 48, kit.palette(), 0.4, 0.15, 0.9);
                    if (!pulse) return;
                    kit.shockwave(target, 1.0, 4.2, 3, 1.0);
                    kit.sound(target, Sfx.METAL, 0.6f, 0.8f);
                    // the banner steadies the warrior who stands by it, and holds the enemies back
                    if (kit.player.getLocation().distanceSquared(target) <= 4.5 * 4.5) kit.guard(0.2, 14);
                    for (LivingEntity victim : kit.around(target, 4.2)) {
                        if (kit.hit(victim, rate)) {
                            kit.push(victim, target.toVector(), 0.3, 0.1);
                            kit.slow(victim, 40.0, 1.5);
                        }
                    }
                }, zone -> kit.shockwave(target, 3.0, 4.4, 3, 1.0));
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        Vector facing = kit.originFacing();
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sfx.METAL_HEAVY, 0.8f, 0.8f);
                ComboParts.sigil(kit, "warrior_totem", at, 3.6, rate, 50, 10, here -> {
                    // the totem steadies the warrior on it (30% less damage) and slows the enemies round it
                    if (kit.player.getLocation().distanceSquared(here) <= 3.8 * 3.8) kit.guard(0.3, 14);
                    for (LivingEntity victim : kit.around(here, 3.6)) kit.slow(victim, 50.0, 1.2);
                    for (double y = 0.0; y <= 2.4; y += 0.4) kit.fx(here).dust(here.getX(), here.getY() + y, here.getZ(), kit.palette(), y / 2.4, 1.3);
                });
            }
            case 1 -> {
                // a fault line ahead: the ground stays cracked for 2 s and what is on it is struck again and again
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.6f);
                Location from = feet.clone().add(0.0, 0.5, 0.0);
                kit.zone("warrior_fault", feet, kit.linger(40), 4, zone -> {
                    boolean pulse = zone.age() % 8 == 0;
                    for (int along = 1; along <= 8; along++) {
                        Location at = feet.clone().add(facing.clone().multiply(along * 1.1)).add(0.0, 0.2, 0.0);
                        kit.fx(at).flatRing(at, 0.7 + 0.05 * along, 14, kit.palette(), 0.4 + 0.05 * along, 0.2, 1.0);
                    }
                    if (!pulse) return;
                    kit.sound(feet, Sfx.METAL, 0.5f, 0.7f);
                    for (LivingEntity victim : kit.corridor(from, facing, 8.8, 1.5)) {
                        if (kit.hit(victim, rate)) kit.lift(victim, 0.3);
                    }
                }, null);
            }
            case 2 -> {
                // a barricade of five across the way
                Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
                Location middle = kit.ground(feet.clone().add(facing.clone().multiply(4.5)));
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 1.0f);
                kit.zone("warrior_barricade", middle, kit.linger(50), 4, zone -> {
                    boolean pulse = zone.age() % 8 == 0;
                    for (int index = -2; index <= 2; index++) {
                        Location at = middle.clone().add(side.clone().multiply(index * 1.6));
                        for (double y = 0.0; y <= 1.8; y += 0.4) kit.fx(at).dust(at.getX(), at.getY() + y, at.getZ(), kit.palette(), 0.5 + y / 3.6, 1.2);
                        if (!pulse) continue;
                        for (LivingEntity victim : kit.around(at.clone().add(0.0, 0.5, 0.0), 1.4)) {
                            if (kit.hit(victim, rate)) kit.push(victim, middle.toVector(), 0.3, 0.1);
                        }
                    }
                }, zone -> kit.shockwave(middle, 1.0, 2.5, 3, 0.8));
            }
            case 3 -> {
                // four bombs in a cross round the aim
                for (int index = 0; index < 4; index++) {
                    double angle = Math.PI * 0.5 * index;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2));
                    String key = "warrior_cross_" + index;
                    kit.later(index, () -> ComboParts.bombAt(kit, key, spot, 30, 2.4, rate, 0.35));
                }
            }
            case 4 -> ComboParts.pullBurst(kit, target.clone().add(0.0, 1.5, 0.0), 5.0, rate, 0.0, 40);
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a ballista at the aim that shoots a heavy bolt at the nearest enemy every 10 ticks for 2.5 s, each pushing it back. */
    private void sixth(MoveKit kit, double rate) {
        Location target = ComboParts.target(kit);
        kit.sound(kit.origin(), Sfx.METAL_HEAVY, 0.9f, 1.1f);
        ComboParts.turret(kit, "warrior_ballista", target, 50, 10, 10.0, rate, victim -> kit.push(victim, target.toVector(), 0.4, 0.1));
    }
}
