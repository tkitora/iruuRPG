package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * The basic combo of a turret (a deployed weapon), for every class that may use one without it being a favorite of the class. Like the others it is made at the placement of the
 * weapon (see {@link DeployHold}) and everything set down stays as long as the placement does. Normal: 1 a turret that shoots the nearest enemy (20% a shot), 2 two small turrets
 * (10% a shot each), 3 a mine that waits for an enemy (100%), 4 three pulsing lanterns in a row (15% a pulse), 5 a mortar beacon that drops shells round it (25% each). Heavy:
 * 0 a ring under the feet (25% a pulse), 1 a bomb (100%), 2 a wall of three turrets (10% a shot each), 3 three mines in a line (40% each), 4 a great bomb (150%).
 */
public final class GenericDeployCombo extends BaseCombo {

    static final String PLAN = "generic_deploy";

    public GenericDeployCombo() {
        super("*", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.1, 1.0, 0.15, 0.25}, new int[]{1, 1, 1, 1, 1},
                new double[]{0.25, 1.0, 0.1, 0.4, 1.5, 0.45}, new int[]{140, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location target = ComboParts.target(kit);
        Vector right = kit.originRight();
        switch (step.stage()) {
            case 1 -> ComboParts.turret(kit, "generic_n1", target, 50, 8, 9.0, rate, null);
            case 2 -> {
                for (int index = 0; index < 2; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "generic_n2_" + index;
                    kit.later(index, () -> ComboParts.turret(kit, key, spot, 40, 8, 8.0, rate, null));
                }
            }
            case 3 -> ComboParts.mine(kit, "generic_mine", target, 2.0, 3.4, rate, 0.0);
            case 4 -> {
                Vector line = target.toVector().subtract(kit.origin().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.originFacing().multiply(6.0);
                for (int index = 0; index < 3; index++) {
                    Location ground = kit.ground(kit.origin().add(line.clone().multiply(0.3 + 0.35 * index)));
                    String key = "generic_n4_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, ground, 44, 14, 2.0, rate, 0.0, 0.0, null));
                }
            }
            default -> ComboParts.rainBeacon(kit, "generic_rain", target, 50, 8, 3.6, rate, Sfx.WIND);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> ComboParts.sigil(kit, "generic_sigil", kit.ground(feet), 3.4, rate, 50, 10, null);
            case 1 -> ComboParts.bomb(kit, "generic_bomb", target, 30, 3.4, rate, 0.0);
            case 2 -> {
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.originFacing() : toward.normalize();
                Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
                for (int index = 0; index < 3; index++) {
                    Location spot = kit.ground(feet.clone().add(facing.clone().multiply(4.0)).add(right.clone().multiply((index - 1) * 2.0)));
                    String key = "generic_wall_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 8, 8.0, rate, null));
                }
            }
            case 3 -> {
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.originFacing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location spot = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.5 * index)));
                    kit.later(index * 2L, () -> ComboParts.mine(kit, "generic_mines_" + spot.getBlockX() + "_" + spot.getBlockZ(), spot, 1.8, 2.8, rate, 0.0));
                }
            }
            case 4 -> ComboParts.bomb(kit, "generic_great", target, 40, 4.6, rate, 0.0);
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): three bombs in a row toward the aim, going off one after the other. */
    private void sixth(MoveKit kit, double rate) {
        Location origin = kit.origin();
        Location target = ComboParts.target(kit);
        Vector way = target.toVector().subtract(origin.toVector()).setY(0.0);
        if (way.lengthSquared() < 1.0E-6) way = kit.originFacing().clone();
        way.normalize();
        for (int index = 0; index < 3; index++) {
            Location spot = kit.ground(target.clone().add(way.clone().multiply((index - 1) * 2.4)));
            String key = "generic_row_" + index;
            int fuse = 20 + index * 8;
            kit.later(index, () -> ComboParts.bombAt(kit, key, spot, fuse, 2.4, rate, 0.3));
        }
    }
}
