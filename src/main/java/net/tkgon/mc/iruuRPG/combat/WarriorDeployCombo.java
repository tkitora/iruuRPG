package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with a deployed weapon (a war lantern): the ward that <b>isolates</b> (t168: it disrupts the enemies and cuts them off from
 * each other; no stuns, for crowd control would be too strong: pushes, a fence, a hook and a cage). An enemy with no other enemy within
 * {@code isolate-radius} (3.5) blocks of it is <b>isolated</b>, and the ward's attacks hit it {@code isolate-bonus} (40%) harder (a ring and a
 * ping on it). The attacks are made round the placement (see {@link DeployHold}), at the aim near it.
 *
 * <p>Normal: 1 an iron wedge struck into the aim (90% within 2.4 blocks; they are thrown apart, away from it), 2 a chain fence across the aim for
 * 3 s (the enemies that touch it are struck, 30%, and thrown back to their side), 3 a hook that tears the enemy nearest the aim away from the
 * rest toward the placement (80%, slowed a moment), 4 a cage round the enemy nearest the aim for 2.5 s (the others are thrown out and kept out;
 * it can move a little inside, and is struck three times, 35%), 5 the bell of judgment at the placement (60% within 7 blocks, slowed 25%; an
 * isolated enemy takes {@code bell-isolated} (150%) more on top).
 *
 * <p>The combo has no heavy attacks since t168 (a deployed weapon has no skills since the redesign of 2026-11-02).
 */
public final class WarriorDeployCombo extends BaseCombo {

    static final String PLAN = "warrior_deploy";
    public WarriorDeployCombo() {
        super("warrior", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.9, 0.3, 0.8, 0.35, 0.6}, new int[]{1, 1, 1, 3, 1},
                new double[]{0.25, 0.25, 0.1, 0.35, 1.5, 0.25}, new int[]{150, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        switch (step.stage()) {
            case 1 -> wedge(kit, target, rate);
            case 2 -> fence(kit, target, rate);
            case 3 -> hook(kit, target, rate);
            case 4 -> cage(kit, target, rate, hits);
            default -> bell(kit, rate);
        }
    }

    // ---- isolation ------------------------------------------------------------------------------------------------------------

    /** Whether {@code victim} is isolated: no other enemy within {@code radius} of it. */
    static boolean isolated(java.util.List<LivingEntity> near, LivingEntity victim) {
        for (LivingEntity other : near) if (!other.equals(victim)) return false;
        return true;
    }

    /** One hit of the ward: {@code isolate-bonus} more (and {@code extra} times that) on an isolated enemy, which is marked by a ring and a ping. */
    private static boolean wardHit(MoveKit kit, LivingEntity victim, double rate, double extra) {
        boolean alone = isolated(kit.around(victim.getLocation(), kit.number(PLAN, "isolate-radius", 3.5)), victim);
        double bonus = alone ? (1.0 + kit.number(PLAN, "isolate-bonus", 0.4)) * extra : 1.0;
        if (!kit.hit(victim, rate * bonus)) return false;
        if (alone) {
            Location feet = victim.getLocation().add(0.0, 0.1, 0.0);
            Fx fx = kit.fx(feet);
            fx.flatRing(feet, Math.max(0.7, victim.getWidth() + 0.3), 20, kit.palette(), 0.85, 0.15, 1.1);
            fx.flatRing(feet.clone().add(0.0, victim.getHeight() + 0.3, 0.0), 0.45, 10, kit.palette(), 1.0, 0.0, 0.9);
            kit.sound(feet, Sfx.WARD_ISOLATED, 0.6f, 1.0f);
        }
        return true;
    }

    private static Vector flat(Vector vector) {
        Vector flat = vector.clone().setY(0.0);
        return flat.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : flat.normalize();
    }

    /** The enemy nearest {@code at} within {@code radius}, or null. */
    private static LivingEntity nearestTo(MoveKit kit, Location at, double radius) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity victim : kit.around(at, radius)) {
            double distance = victim.getLocation().distanceSquared(at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = victim;
            }
        }
        return best;
    }

    // ---- 1 - 5 ----------------------------------------------------------------------------------------------------------------

    /** An iron wedge struck into the aim: everyone near is struck and thrown apart, away from it. */
    private static void wedge(MoveKit kit, Location target, double rate) {
        kit.sound(target, Sfx.WARD_WEDGE, 1.0f, 1.0f);
        Fx fx = kit.fx(target);
        fx.line(target.clone().add(0.0, 4.0, 0.0), target.clone().add(0.0, 0.2, 0.0), kit.palette(), 0.2, 1.0, 2.0, 1.3, 0.2, null, 1);
        kit.shockwave(target, 0.5, 2.6, 4, 1.1);
        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.5 * index + Math.PI * 0.25;
            Location to = target.clone().add(Math.cos(angle) * 2.0, 0.15, Math.sin(angle) * 2.0);
            kit.later(1L, () -> kit.fx(target).line(target.clone().add(0.0, 0.15, 0.0), to, kit.palette(), 0.9, 0.3, 0.0, 0.9, 0.2, null, 1));
        }
        for (LivingEntity victim : kit.around(target, 2.4)) {
            if (wardHit(kit, victim, rate, 1.0)) kit.push(victim, target.toVector(), 0.55, 0.15);
        }
    }

    /** A fence of chains across the aim for 3 s: the enemies that touch it are struck and thrown back to their own side. */
    private static void fence(MoveKit kit, Location target, double rate) {
        Vector along = flat(target.toVector().subtract(kit.origin().toVector()));
        Vector across = new Vector(-along.getZ(), 0.0, along.getX());
        double half = kit.number(PLAN, "fence-half-width", 3.0);
        java.util.List<Location> posts = new java.util.ArrayList<>();
        for (int index = -2; index <= 2; index++) posts.add(kit.ground(target.clone().add(across.clone().multiply(half * index / 2.0))));
        java.util.Map<java.util.UUID, Integer> touched = new java.util.HashMap<>();
        kit.sound(target, Sfx.WARD_FENCE, 1.0f, 1.0f);
        kit.zone("warrior_fence", target, kit.linger(60), 2, zone -> {
            int age = zone.age();
            Fx fx = kit.fx(target);
            if (age % 4 == 0) {
                for (int index = 0; index < posts.size(); index++) {
                    Location post = posts.get(index);
                    fx.line(post, post.clone().add(0.0, 1.6, 0.0), kit.palette(), 0.2, 0.8, 0.0, 1.0, 0.2, null, 1);
                    if (index + 1 < posts.size()) {
                        WarriorSwords.chain(kit, post.clone().add(0.0, 1.3, 0.0), posts.get(index + 1).clone().add(0.0, 1.3, 0.0), 0.25);
                        WarriorSwords.chain(kit, post.clone().add(0.0, 0.6, 0.0), posts.get(index + 1).clone().add(0.0, 0.6, 0.0), 0.15);
                    }
                }
            }
            for (LivingEntity victim : kit.corridor(target.clone().subtract(across.clone().multiply(half)).add(0.0, 0.5, 0.0), across, half * 2.0, 1.1)) {
                Integer last = touched.get(victim.getUniqueId());
                if (last != null && age - last < 10) continue;
                touched.put(victim.getUniqueId(), age);
                double side = victim.getLocation().toVector().subtract(target.toVector()).dot(along) >= 0.0 ? 1.0 : -1.0;
                Location from = victim.getLocation().clone().subtract(along.clone().multiply(side));
                if (wardHit(kit, victim, rate, 1.0)) kit.push(victim, from.toVector(), 0.45, 0.1);
                kit.sound(victim.getLocation(), Sfx.CHAIN_BREAK, 0.4f, 1.4f);
            }
        }, zone -> {
            for (Location post : posts) kit.particle(org.bukkit.Particle.CRIT, post.clone().add(0.0, 1.0, 0.0), 4, 0.2, 0.05);
        });
    }

    /** A hook that tears the enemy nearest the aim away from the rest, toward the placement. */
    private static void hook(MoveKit kit, Location target, double rate) {
        LivingEntity victim = nearestTo(kit, target, kit.number(PLAN, "hook-radius", 4.0));
        Location from = kit.origin().clone().add(0.0, 1.4, 0.0);
        kit.sound(from, Sfx.CHAIN_THROW, 1.0f, 0.9f);
        if (victim == null) {
            WarriorSwords.chain(kit, from, target.clone().add(0.0, 0.3, 0.0), 0.2);
            kit.burst(target.clone().add(0.0, 0.3, 0.0), 1.0);
            for (LivingEntity near : kit.around(target, 2.0)) wardHit(kit, near, rate * 0.5, 1.0);
            return;
        }
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.55, 0.0);
        WarriorSwords.chain(kit, from, chest, 0.1);
        kit.pull(victim, kit.origin().clone().add(flat(chest.toVector().subtract(kit.origin().toVector())).multiply(1.5)), 1.0);
        kit.later(4L, () -> {
            if (!victim.isValid() || victim.isDead()) return;
            kit.sound(victim.getLocation(), Sfx.CHAIN_BIND, 0.7f, 1.2f);
            wardHit(kit, victim, rate, 1.0);
            kit.slow(victim, 30.0, 1.0);
        });
    }

    /** A cage round the enemy nearest the aim: the others are thrown out and kept out; it is struck {@code hits} times. */
    private static void cage(MoveKit kit, Location target, double rate, int hits) {
        LivingEntity victim = nearestTo(kit, target, kit.number(PLAN, "cage-radius", 4.5));
        Location center = victim == null ? target : kit.ground(victim.getLocation());
        double radius = kit.number(PLAN, "cage-size", 2.6);
        int life = kit.linger(50);
        kit.sound(center, Sfx.WARD_CAGE, 1.0f, 1.0f);
        for (LivingEntity other : kit.around(center, radius + 1.5)) {
            if (!other.equals(victim)) kit.push(other, center.toVector(), 0.6, 0.15);
        }
        if (victim != null) WarriorSwords.bind(kit, victim, center.clone().add(0.0, 0.5, 0.0), Math.max(0.5, radius - 0.6), life, null);
        int gap = Math.max(4, 40 / Math.max(1, hits));
        int[] struck = {0};
        kit.zone("warrior_cage", center, life, 2, zone -> {
            int age = zone.age();
            Fx fx = kit.fx(center);
            if (age % 4 == 0) {
                for (int index = 0; index < 10; index++) {
                    double angle = index * Math.PI / 5.0;
                    Location bar = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                    fx.line(bar, bar.clone().add(0.0, 2.4, 0.0), kit.palette(), 0.3, 0.9, 0.0, 0.9, 0.25, null, 1);
                }
                fx.flatRing(center.clone().add(0.0, 2.5, 0.0), radius, 30, kit.palette(), 0.8, 0.2, 1.0);
                fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius, 30, kit.palette(), 0.5, 0.2, 1.0);
            }
            // nobody else comes in
            for (LivingEntity other : kit.around(center, radius + 0.4)) {
                if (!other.equals(victim)) kit.push(other, center.toVector(), 0.35, 0.05);
            }
            if (victim != null && victim.isValid() && !victim.isDead() && age > 0 && age % gap == 0 && struck[0] < hits) {
                struck[0]++;
                kit.sound(center, Sfx.WARD_WEDGE, 0.5f, 1.4f);
                Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                fx.line(center.clone().add(0.0, 2.5, 0.0), chest, kit.palette(), 0.3, 1.0, 1.5, 1.1, 0.2, null, 1);
                wardHit(kit, victim, rate, 1.0);
            }
        }, zone -> kit.shockwave(center, radius, radius + 1.0, 3, 0.9));
    }

    /** The bell of judgment at the placement: everyone within 7 blocks is struck and slowed, the isolated ones much harder. */
    private static void bell(MoveKit kit, double rate) {
        Location at = kit.ground(kit.origin());
        double radius = kit.number(PLAN, "bell-radius", 7.0);
        double extra = 1.0 + kit.number(PLAN, "bell-isolated", 1.5);
        kit.sound(at, Sfx.WARD_BELL, 1.0f, 1.0f);
        Fx fx = kit.fx(at);
        // the bell: rings narrowing to its top over the placement
        for (int index = 0; index <= 6; index++) {
            double y = 2.4 + index * 0.25;
            double r = 1.2 - 0.14 * index;
            fx.flatRing(at.clone().add(0.0, y, 0.0), r, 18, kit.palette(), 0.4 + 0.08 * index, 0.2, 1.0);
        }
        for (int wave = 0; wave < 3; wave++) {
            int which = wave;
            kit.later(wave * 4L, () -> kit.shockwave(at, 1.0, radius, 6, 1.2 - 0.2 * which));
        }
        for (LivingEntity victim : kit.around(at, radius)) {
            if (wardHit(kit, victim, rate, extra)) kit.slow(victim, 25.0, 1.5);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: a deployed weapon has no skills.
    }

}
