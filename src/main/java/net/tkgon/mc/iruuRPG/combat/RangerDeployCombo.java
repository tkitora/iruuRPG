package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The ranger's combo with a deployed weapon: the <b>spirit bow</b>, a turret (t168: nekorozv wanted a turret, and not a modern one; t198: a bow
 * floating in the air, no post). The first attack of a placement sets it at the placement: a bow floating and bobbing over a ring of light,
 * turning to its target; it stays as long as the placement does and shoots by itself every {@code turret-period} (20) ticks at an enemy within
 * {@code turret-range} (12) blocks of it ({@code turret-rate} 30%): a tied one first ({@link RangerSkills}: the knife's wire drags the enemies
 * here), {@code tied-bonus} (30%) harder. Every attack is an order to it. The ranger's arrows that pass near it are stronger and a hit of the bow
 * makes it shoot that enemy too (the loop, {@link MoveKit}). Drawn in the weapon's colors.
 *
 * <p>With the class's passive ({@code ranger_self_deploy}, t198: the lantern's effect round the ranger too, now seen) a smaller spirit bow floats
 * over the ranger's shoulder while the turret stands within {@code self-reach} (24) blocks: every attack of the combo is made from it too, on the
 * enemies within {@code self-radius} (5) blocks of the ranger ({@code self-share} 60% of it; a ring of that radius lights round the ranger), and it
 * shoots them by itself with the turret.
 *
 * <p>Normal: 1 three quick arrows at its target (30% x3), 2 a spread at up to five enemies (25% x5), 3 a heavy arrow that pins (100%, slowed 50%
 * for 1.5 s), 4 a volley falling round its target (20% x6), 5 overdrive: an arrow at every enemy in reach (60%), and for {@code overdrive-seconds}
 * (4) it shoots every {@code overdrive-period} (7) ticks.
 *
 * <p>The combo has no heavy attacks since t168 (a deployed weapon has no skills).
 */
public final class RangerDeployCombo implements ClassCombo {

    static final String PLAN = "ranger_deploy";

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{0.3, 0.25, 1.0, 0.2, 0.6}, new int[]{3, 5, 1, 6, 1},
            new double[]{0.25, 0.15, 0.1, 0.08, 1.5, 0.2}, new int[]{120, 0, 0, 0, 0, 0}, 40);

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

    /** A spirit bow: the turret at the placement, or the small one over the ranger's shoulder (the passive); where it shoots from. */
    private abstract static class Bow {
        final ItemDisplay bow;
        double facing;

        Bow(ItemDisplay bow) {
            this.bow = bow;
        }

        abstract Location top();
    }

    /** A ranger's spirit bow: where it stands, its bow (and the one over the ranger), until when it shoots fast (ticks), its last relayed shot. */
    private static final class Turret extends Bow {
        final Location at;
        /** The small bow over the ranger's shoulder (the passive), or null. */
        Companion companion;
        boolean alive = true;
        /** The tick it was last kept by its zone (a zone taken away without its end, as when the placement is removed, leaves it to the watch). */
        int seen = Bukkit.getCurrentTick();
        int fastUntil;
        int lastRelay = -100;
        int age;

        Turret(Location at, ItemDisplay bow) {
            super(bow);
            this.at = at;
        }

        @Override
        Location top() {
            return at.clone().add(0.0, 1.6 + 0.15 * Math.sin(age * 0.12), 0.0);
        }

        void remove() {
            alive = false;
            if (bow.isValid()) bow.remove();
            if (companion != null) companion.remove();
        }
    }

    /** The small spirit bow over the ranger's shoulder (the passive, t198). */
    private static final class Companion extends Bow {
        final Player owner;

        Companion(Player owner, ItemDisplay bow) {
            super(bow);
            this.owner = owner;
        }

        /** Over the ranger's right shoulder, a little behind. */
        @Override
        Location top() {
            Location at = owner.getLocation();
            Vector facing = at.getDirection().setY(0.0);
            if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
            facing.normalize();
            Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
            return at.add(0.0, 2.3, 0.0).add(right.multiply(0.7)).subtract(facing.multiply(0.35));
        }

        void remove() {
            if (bow.isValid()) bow.remove();
        }
    }

    private static final Map<UUID, Turret> TURRETS = new ConcurrentHashMap<>();

    /** Where the spirit bow of {@code player} stands when it is within {@code range} of them, else null. */
    static Location turretNear(Player player, double range) {
        Turret turret = TURRETS.get(player.getUniqueId());
        if (turret == null || !turret.alive || turret.at.getWorld() != player.getWorld()) return null;
        return turret.at.distanceSquared(player.getLocation()) <= range * range ? turret.at.clone() : null;
    }

    /** A hit of the ranger's power shot: their spirit bow, when it is near, shoots that enemy too (once in 8 ticks). */
    static void relay(MoveKit kit, LivingEntity victim) {
        Turret turret = TURRETS.get(kit.player.getUniqueId());
        if (turret == null || !turret.alive || turret.at.getWorld() != victim.getWorld()) return;
        double reach = kit.number(PLAN, "turret-range", 12.0);
        if (turret.at.distanceSquared(victim.getLocation()) > reach * reach) return;
        int tick = Bukkit.getCurrentTick();
        if (tick - turret.lastRelay < 8) return;
        turret.lastRelay = tick;
        shoot(kit, turret, victim, kit.number(PLAN, "turret-rate", 0.3));
    }

    /** The ranger's ULT (t168): their spirit bow, when it stands, shoots quickly for {@code seconds}. True when there was one. */
    static boolean overdrive(Player player, double seconds) {
        Turret turret = TURRETS.get(player.getUniqueId());
        if (turret == null || !turret.alive) return false;
        turret.fastUntil = Math.max(turret.fastUntil, Bukkit.getCurrentTick() + (int) Math.round(seconds * 20.0));
        Sfx.SPIRIT_OVERDRIVE.play(turret.top(), 1.0f, 1.0f);
        return true;
    }

    static void forget(UUID player) {
        Turret turret = TURRETS.remove(player);
        if (turret != null) turret.remove();
    }

    /** The plugin stops: every spirit bow goes. */
    static void shutdown() {
        for (Turret turret : TURRETS.values()) turret.remove();
        TURRETS.clear();
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Turret turret = turret(kit);
        attack(kit, step.stage(), turret, targets(kit, turret), rate, count);
        // (the passive: the same attack from the bow over the ranger's shoulder, on the enemies round the ranger)
        Companion companion = companion(kit, turret);
        if (companion == null) return;
        double radius = kit.number(PLAN, "self-radius", 5.0);
        List<LivingEntity> near = selfTargets(kit, radius);
        Location feet = kit.player.getLocation();
        kit.fx(feet).flatRing(feet.clone().add(0.0, 0.12, 0.0), radius, 48, kit.palette(), 0.6, 0.15, 0.9);
        if (!near.isEmpty()) attack(kit, step.stage(), companion, near, rate * kit.number(PLAN, "self-share", 0.6), count);
    }

    /** Normal attack {@code stage} made by {@code from} on {@code targets} (the first first). */
    private static void attack(MoveKit kit, int stage, Bow from, List<LivingEntity> targets, double rate, int count) {
        LivingEntity first = targets.isEmpty() ? null : targets.get(0);
        switch (stage) {
            case 1 -> {
                if (first == null) {
                    idle(kit, from);
                    return;
                }
                for (int index = 0; index < count; index++) kit.later(index * 3L, () -> {
                    if (first.isValid() && !first.isDead()) shoot(kit, from, first, rate);
                });
            }
            case 2 -> {
                if (first == null) {
                    idle(kit, from);
                    return;
                }
                for (int index = 0; index < count; index++) {
                    LivingEntity victim = targets.get(index % targets.size());
                    kit.later(index, () -> {
                        if (victim.isValid() && !victim.isDead()) shoot(kit, from, victim, rate);
                    });
                }
            }
            case 3 -> {
                if (first == null) {
                    idle(kit, from);
                    return;
                }
                kit.sound(from.top(), Sfx.BOW_DRAW, 0.8f, 0.8f);
                kit.later(4L, () -> {
                    if (!first.isValid() || first.isDead()) return;
                    if (shoot(kit, from, first, rate)) kit.slow(first, kit.number(PLAN, "pin-slow", 50.0), 1.5);
                    kit.sound(first.getLocation(), Sfx.HIT_PIERCE, 1.0f, 0.7f);
                });
            }
            case 4 -> {
                if (first == null) {
                    idle(kit, from);
                    return;
                }
                Location center = kit.ground(first.getLocation());
                kit.sound(from.top(), Sfx.BOW_LOOSE, 0.8f, 1.3f);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int index = 0; index < count; index++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double r = 2.5 * Math.sqrt(random.nextDouble());
                    Location spot = kit.ground(center.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r));
                    kit.later(6L + index * 2L, () -> {
                        kit.fx(spot).line(spot.clone().add(0.6, 6.0, 0.3), spot.clone().add(0.0, 0.1, 0.0), kit.palette(), 0.3, 1.0, 1.5, 1.0, 0.3, null, 1);
                        kit.particle(Particle.CRIT, spot.clone().add(0.0, 0.2, 0.0), 3, 0.2, 0.05);
                        for (LivingEntity victim : kit.around(spot, 1.3)) hitFrom(kit, victim, rate);
                    });
                }
                kit.later(6L, () -> kit.sound(center, Sfx.HIT_PIERCE, 0.7f, 1.2f));
            }
            default -> {
                // overdrive: an arrow at everyone in reach now, and (the turret) quick shots for a while
                if (from instanceof Turret turret) {
                    turret.fastUntil = Bukkit.getCurrentTick() + (int) Math.round(kit.number(PLAN, "overdrive-seconds", 4.0) * 20.0);
                    kit.shockwave(turret.at, 0.6, 2.6, 4, 1.1);
                }
                kit.sound(from.top(), Sfx.SPIRIT_OVERDRIVE, 1.0f, 1.0f);
                for (int index = 0; index < targets.size(); index++) {
                    LivingEntity victim = targets.get(index);
                    kit.later(index, () -> {
                        if (victim.isValid() && !victim.isDead()) shoot(kit, from, victim, rate);
                    });
                }
            }
        }
    }

    /** A floating bow (an item display of a bow, lit, never saved) at {@code at}, {@code size} large. */
    private static ItemDisplay spawnBow(Location at, float size) {
        return at.getWorld().spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(Material.BOW));
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(2);
            spawned.setTransformation(bowShape(0.0, size));
        });
    }

    /**
     * The spirit bow at the placement: the one standing there, or a new one (the old one goes). It floats over a ring of light and shoots by itself
     * while the placement lasts (and the bow over the ranger's shoulder with it, with the passive).
     */
    private static Turret turret(MoveKit kit) {
        UUID id = kit.player.getUniqueId();
        Location at = kit.ground(kit.origin());
        Turret old = TURRETS.get(id);
        if (old != null && old.alive && old.at.getWorld() == at.getWorld() && old.at.distanceSquared(at) < 0.25) return old;
        if (old != null) old.remove();
        Turret turret = new Turret(at, spawnBow(at.clone().add(0.0, 1.6, 0.0), 1.5f));
        TURRETS.put(id, turret);
        kit.sound(at, Sfx.SPIRIT_SET, 1.0f, 1.0f);
        ComboParts.appear(kit, at);
        int period = (int) Math.round(kit.number(PLAN, "turret-period", 20.0));
        int fast = (int) Math.round(kit.number(PLAN, "overdrive-period", 7.0));
        double rate = kit.number(PLAN, "turret-rate", 0.3);
        double selfRadius = kit.number(PLAN, "self-radius", 5.0);
        double selfShare = kit.number(PLAN, "self-share", 0.6);
        // (the watch: a turret whose zone is gone without its end goes too)
        new SafeTask(kit.plugin()) {
            @Override
            protected void tick() {
                if (!turret.alive) {
                    cancel();
                    return;
                }
                if (Bukkit.getCurrentTick() - turret.seen > 10) {
                    turret.remove();
                    TURRETS.remove(id, turret);
                    cancel();
                }
            }
        }.runTaskTimer(kit.plugin(), 10L, 10L);
        kit.zone("ranger_spiritbow", at, kit.linger(200), 1, zone -> {
            if (!turret.alive) {
                zone.stop();
                return;
            }
            int age = zone.age();
            turret.age = age;
            int tick = Bukkit.getCurrentTick();
            turret.seen = tick;
            boolean quick = tick < turret.fastUntil;
            boolean fire = age > 0 && age % (quick ? fast : period) == 0;
            if (age % 2 != 0 && !fire) return;
            // the bow bobs over its ring of light; the one over the ranger's shoulder follows them
            if (turret.bow.isValid()) turret.bow.teleport(turret.top());
            Companion companion = companion(kit, turret);
            if (companion != null && companion.bow.isValid()) companion.bow.teleport(companion.top());
            if (age % 4 == 0) {
                Fx fx = kit.fx(at);
                Location top = turret.top();
                fx.flatRing(at.clone().add(0.0, 0.12, 0.0), 1.2, 24, kit.palette(), quick ? 1.0 : 0.6, 0.2, 1.0);
                fx.flatRing(top.clone().subtract(0.0, 0.9, 0.0), 0.5 + 0.1 * Math.sin(age * 0.2), 12, kit.palette(), 0.8, 0.1, 0.9);
                fx.dust(top.getX(), top.getY() - 0.4 - 0.3 * ((age / 4) % 3), top.getZ(), kit.palette(), 0.9, 0.9);
                if (quick) kit.particle(Particle.VILLAGER_HAPPY, top, 1, 0.3, 0.0);
                if (companion != null) {
                    Location shoulder = companion.top();
                    kit.fx(shoulder).flatRing(shoulder.clone().subtract(0.0, 0.5, 0.0), 0.3, 8, kit.palette(), 0.8, 0.0, 0.8);
                    if (age % 20 == 0) {
                        Location feet = kit.player.getLocation();
                        kit.fx(feet).flatRing(feet.clone().add(0.0, 0.12, 0.0), selfRadius, 40, kit.palette(), 0.3, 0.1, 0.7);
                    }
                }
            }
            List<LivingEntity> targets = targets(kit, turret);
            if (targets.isEmpty()) {
                // nothing to shoot: the bow turns slowly, keeping watch
                turret.facing += 0.05;
                if (age % 2 == 0) aimBow(turret, turret.facing, 2, 1.5f);
            } else {
                LivingEntity target = targets.get(0);
                Vector to = target.getLocation().toVector().subtract(at.toVector());
                turret.facing = Math.atan2(to.getZ(), -to.getX());
                if (age % 2 == 0) aimBow(turret, turret.facing, 2, 1.5f);
                if (fire) shoot(kit, turret, target, rate);
            }
            if (fire && companion != null) {
                List<LivingEntity> near = selfTargets(kit, selfRadius);
                if (!near.isEmpty()) shoot(kit, companion, near.get(0), rate * selfShare);
            }
        }, zone -> {
            turret.remove();
            TURRETS.remove(id, turret);
        });
        return turret;
    }

    /**
     * The bow over the ranger's shoulder (the passive, t198): made when the ranger has the passive and stands within {@code self-reach} of the
     * turret, taken away when they do not; null then.
     */
    private static Companion companion(MoveKit kit, Turret turret) {
        Player player = kit.player;
        double reach = kit.number(PLAN, "self-reach", 24.0);
        boolean on = turret.alive && kit.hasSkill(SELF_ZONE) && player.isOnline() && !player.isDead() && player.getWorld() == turret.at.getWorld()
                && player.getLocation().distanceSquared(turret.at) <= reach * reach;
        if (!on) {
            if (turret.companion != null) {
                turret.companion.remove();
                turret.companion = null;
            }
            return null;
        }
        if (turret.companion == null || !turret.companion.bow.isValid()) {
            Companion made = new Companion(player, spawnBow(player.getLocation().add(0.0, 2.3, 0.0), 0.8f));
            turret.companion = made;
            kit.fx(made.top()).sphereShell(made.top(), 0.5, 0.0, 12, kit.palette(), 0.9);
        }
        return turret.companion;
    }

    /** The bow's turn: upright (the sprite's diagonal stood up), {@code size} large, turned about the vertical by {@code angle}. */
    private static Transformation bowShape(double angle, float size) {
        Quaternionf turn = new Quaternionf().rotateY((float) angle).mul(new Quaternionf().rotateZ((float) (-Math.PI / 4.0)));
        return new Transformation(new Vector3f(), turn, new Vector3f(size, size, size), new Quaternionf());
    }

    private static void aimBow(Bow bow, double angle, int ticks, float size) {
        if (!bow.bow.isValid()) return;
        bow.bow.setInterpolationDelay(0);
        bow.bow.setInterpolationDuration(ticks);
        bow.bow.setTransformation(bowShape(angle, size));
    }

    /** The enemies the spirit bow can shoot, the first one first: the tied ones, then by distance; within its reach. */
    private static List<LivingEntity> targets(MoveKit kit, Turret turret) {
        List<LivingEntity> found = new ArrayList<>(kit.around(turret.at, kit.number(PLAN, "turret-range", 12.0)));
        found.sort(java.util.Comparator.comparing((LivingEntity victim) -> !RangerSkills.tied(victim))
                .thenComparingDouble(victim -> victim.getLocation().distanceSquared(turret.at)));
        return found;
    }

    /** The enemies within {@code radius} of the ranger (the bow over their shoulder), the tied ones first, then the nearest. */
    private static List<LivingEntity> selfTargets(MoveKit kit, double radius) {
        Location at = kit.player.getLocation();
        List<LivingEntity> found = new ArrayList<>(kit.around(at, radius));
        found.sort(java.util.Comparator.comparing((LivingEntity victim) -> !RangerSkills.tied(victim))
                .thenComparingDouble(victim -> victim.getLocation().distanceSquared(at)));
        return found;
    }

    /** The ranger's passive that brings the spirit bow's attacks round the ranger too. */
    private static final String SELF_ZONE = "ranger_self_deploy";

    /** One arrow of a spirit bow at {@code victim}: drawn from the bow, {@code tied-bonus} more on a tied enemy. True when it hit. */
    private static boolean shoot(MoveKit kit, Bow from, LivingEntity victim, double rate) {
        Location top = from.top();
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        if (top.getWorld() != chest.getWorld()) return false;
        Vector way = chest.toVector().subtract(top.toVector());
        if (way.lengthSquared() > 1.0E-6) {
            from.facing = Math.atan2(way.getZ(), -way.getX());
            aimBow(from, from.facing, 1, from instanceof Turret ? 1.5f : 0.8f);
            kit.fx(top).line(top, chest, kit.palette(), 0.2, 1.0, 1.5, 0.9, 0.3, null, 1);
        }
        kit.sound(top, Sfx.BOW_LOOSE, 0.45f, from instanceof Turret ? 1.4f : 1.7f);
        kit.particle(Particle.CRIT, chest, 3, 0.15, 0.05);
        return hitFrom(kit, victim, rate);
    }

    /** A hit of the spirit bow: {@code tied-bonus} more on an enemy tied by the ranger's wire. */
    private static boolean hitFrom(MoveKit kit, LivingEntity victim, double rate) {
        boolean tied = RangerSkills.tied(victim);
        boolean landed = kit.hit(victim, tied ? rate * (1.0 + kit.number(RangerSkills.MELEE, "tied-bonus", 0.3)) : rate);
        if (landed && tied) kit.fx(victim.getLocation()).flatRing(victim.getLocation().add(0.0, 0.1, 0.0), 0.7, 12, RangerSkills.WIRE, 0.9, 0.0, 0.9);
        return landed;
    }

    /** The bow has nobody to shoot: a dry twang. */
    private static void idle(MoveKit kit, Bow bow) {
        kit.sound(bow.top(), Sfx.BOW_LOOSE, 0.3f, 1.8f);
        kit.fx(bow.top()).sphereShell(bow.top(), 0.4, 0.0, 8, kit.palette(), 0.7);
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: a deployed weapon has no skills.
    }

}
