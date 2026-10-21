package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with a deployed weapon (a war lantern): bombs, barricades and banners. Normal: 1 a bomb (100%), 2 two bombs (60% x2), 3 a mine
 * for 2.7 s (130%, knocks back), 4 three land mines in a row (40% x3), 5 a war banner planted at the aim that bursts after a moment (150%). Heavy:
 * 0 a totem under the feet for 2.5 s (25% a pulse), 1 a stomp that sends a wave ahead (120%), 2 a barricade of five for 2.5 s (25% a pulse),
 * 3 four seeking bombs (35% x4), 4 a great bomb that pulls in and bursts (150%).
 */
public final class WarriorDeployCombo extends BaseCombo {

    static final String PLAN = "warrior_deploy";

    public WarriorDeployCombo() {
        super("warrior", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{1.0, 0.6, 1.3, 0.4, 1.5}, new int[]{1, 2, 1, 3, 1},
                new double[]{0.25, 1.2, 0.25, 0.35, 1.5}, new int[]{150, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        Vector right = kit.axes()[1];
        switch (step.stage()) {
            case 1 -> ComboParts.lobBurst(kit, target, 3.2, rate, Sound.ENTITY_IRON_GOLEM_ATTACK, null);
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    Location spot = target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0));
                    kit.later(index, () -> ComboParts.lobBurst(kit, spot, 2.6, rate, Sound.ENTITY_IRON_GOLEM_ATTACK, null));
                }
            }
            case 3 -> ComboParts.mine(kit, "warrior_mine", target, 2.0, 3.6, rate, 0.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < hits; index++) {
                    Location ground = kit.ground(kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index)));
                    kit.later(index * 2L, () -> {
                        ComboParts.orb(kit, ground.clone().add(0.0, 0.4, 0.0), 0.4);
                        kit.burst(ground.clone().add(0.0, 0.3, 0.0), 2.0);
                        kit.sound(ground, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.5f);
                        for (LivingEntity victim : kit.around(ground, 2.0)) {
                            if (kit.hit(victim, rate)) kit.push(victim, ground.toVector(), 0.4, 0.2);
                        }
                    });
                }
            }
            default -> {
                // a banner is planted at the aim; after a moment it goes off
                kit.sound(target, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.7f);
                for (int tick = 0; tick < 12; tick++) {
                    int which = tick;
                    kit.later(tick, () -> {
                        for (double y = 0.0; y <= 3.0; y += 0.4) {
                            kit.fx(target).dust(target.getX(), target.getY() + y, target.getZ(), kit.palette(), y / 3.0, 1.2);
                        }
                        kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), 4.2 - which * 0.15, 48, kit.palette(), 0.4, 0.3, 1.0);
                    });
                }
                kit.later(12, () -> {
                    kit.burst(target.clone().add(0.0, 0.4, 0.0), 4.2);
                    kit.shockwave(target, 1.0, 4.4, 5, 1.2);
                    kit.sound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
                    for (LivingEntity victim : kit.around(target, 4.4)) {
                        if (kit.hit(victim, rate)) kit.push(victim, target.toVector(), 0.7, 0.3);
                    }
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.8f);
                ComboParts.sigil(kit, "warrior_totem", at, 3.6, rate, 50, 10, here -> {
                    for (double y = 0.0; y <= 2.4; y += 0.4) kit.fx(here).dust(here.getX(), here.getY() + y, here.getZ(), kit.palette(), y / 2.4, 1.3);
                });
            }
            case 1 -> {
                kit.sound(feet, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
                kit.shockwave(feet, 0.8, 2.4, 3, 1.1);
                for (int along = 1; along <= 7; along++) {
                    double d = along;
                    kit.later(along / 2, () -> {
                        Location at = feet.clone().add(facing.clone().multiply(d * 1.2)).add(0.0, 0.3, 0.0);
                        kit.fx(at).flatRing(at, 1.0 + d * 0.1, 24, kit.palette(), 0.4 + 0.07 * d, 0.3, 1.0);
                    });
                }
                for (LivingEntity victim : kit.corridor(feet.clone().add(0.0, 0.5, 0.0), facing, 8.4, 1.7)) {
                    if (kit.hit(victim, rate)) kit.lift(victim, 0.55);
                }
            }
            case 2 -> {
                // a barricade of five across the way for 2.5 seconds
                Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
                Location middle = kit.ground(feet.clone().add(facing.clone().multiply(4.5)));
                kit.sound(feet, Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.0f);
                kit.zone("warrior_barricade", middle, 50, 8, zone -> {
                    for (int index = -2; index <= 2; index++) {
                        Location at = middle.clone().add(side.clone().multiply(index * 1.6));
                        for (double y = 0.0; y <= 1.8; y += 0.4) kit.fx(at).dust(at.getX(), at.getY() + y, at.getZ(), kit.palette(), 0.5 + y / 3.6, 1.2);
                        for (LivingEntity victim : kit.around(at.clone().add(0.0, 0.5, 0.0), 1.4)) {
                            if (kit.hit(victim, rate)) kit.push(victim, middle.toVector(), 0.3, 0.1);
                        }
                    }
                }, zone -> kit.shockwave(middle, 1.0, 2.5, 3, 0.8));
            }
            case 3 -> ComboParts.homingVolley(kit, 4, rate, 14.0, end -> kit.burst(end, 1.4));
            default -> ComboParts.pullBurst(kit, target.clone().add(0.0, 1.5, 0.0), 5.0, rate, 0.0);
        }
    }
}
