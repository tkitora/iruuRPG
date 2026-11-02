package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
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
 * The ranger's combo with a deployed weapon: the <b>spirit bow</b>, a turret (t168: nekorozv wanted a turret, and not a modern one). The first
 * attack of a placement sets it at the placement: a bow floating over a wooden post, turning to its target; it stays as long as the placement
 * does and shoots by itself every {@code turret-period} (20) ticks at an enemy within {@code turret-range} (12) blocks of it ({@code turret-rate}
 * 30%): a tied one first ({@link RangerSkills}: the knife's wire drags the enemies here), {@code tied-bonus} (30%) harder. Every attack is an order
 * to it. With the class's passive ({@code ranger_self_deploy}) it also shoots the enemies within 5 blocks of the ranger (at most 24 blocks from
 * it). The ranger's arrows that pass near it are stronger and a hit of the bow makes it shoot that enemy too (the loop, {@link MoveKit}).
 *
 * <p>Normal: 1 three quick arrows at its target (30% x3), 2 a spread at up to five enemies (25% x5), 3 a heavy arrow that pins (100%, slowed 50%
 * for 1.5 s), 4 a volley falling round its target (20% x6), 5 overdrive: an arrow at every enemy in reach (60%), and for {@code overdrive-seconds}
 * (4) it shoots every {@code overdrive-period} (7) ticks.
 *
 * <p>The heavy attacks below are what they were before t168 (a deployed weapon has no skills).
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

    /** The wood and the green light of the spirit bow. */
    static final Gradient SPIRIT = Gradient.of(Color.fromRGB(70, 45, 20), Color.fromRGB(120, 220, 120), Color.fromRGB(235, 255, 220));

    /** A ranger's spirit bow: where it stands, its bow and post, until when it shoots fast (ticks), its target and its last relayed shot. */
    private static final class Turret {
        final Location at;
        final ItemDisplay bow;
        final BlockDisplay post;
        boolean alive = true;
        /** The tick it was last kept by its zone (a zone taken away without its end, as when the placement is removed, leaves it to the watch). */
        int seen = Bukkit.getCurrentTick();
        int fastUntil;
        int lastRelay = -100;
        double facing;

        Turret(Location at, ItemDisplay bow, BlockDisplay post) {
            this.at = at;
            this.bow = bow;
            this.post = post;
        }

        Location top() {
            return at.clone().add(0.0, 1.55, 0.0);
        }

        void remove() {
            alive = false;
            if (bow.isValid()) bow.remove();
            if (post.isValid()) post.remove();
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
        kit.palette(SPIRIT);
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Turret turret = turret(kit);
        List<LivingEntity> targets = targets(kit, turret);
        LivingEntity first = targets.isEmpty() ? null : targets.get(0);
        switch (step.stage()) {
            case 1 -> {
                if (first == null) {
                    idle(kit, turret);
                    return;
                }
                for (int index = 0; index < count; index++) kit.later(index * 3L, () -> {
                    if (first.isValid() && !first.isDead()) shoot(kit, turret, first, rate);
                });
            }
            case 2 -> {
                if (first == null) {
                    idle(kit, turret);
                    return;
                }
                for (int index = 0; index < count; index++) {
                    LivingEntity victim = targets.get(index % targets.size());
                    kit.later(index, () -> {
                        if (victim.isValid() && !victim.isDead()) shoot(kit, turret, victim, rate);
                    });
                }
            }
            case 3 -> {
                if (first == null) {
                    idle(kit, turret);
                    return;
                }
                kit.sound(turret.top(), Sfx.BOW_DRAW, 0.8f, 0.8f);
                kit.later(4L, () -> {
                    if (!first.isValid() || first.isDead()) return;
                    if (shoot(kit, turret, first, rate)) kit.slow(first, kit.number(PLAN, "pin-slow", 50.0), 1.5);
                    kit.sound(first.getLocation(), Sfx.HIT_PIERCE, 1.0f, 0.7f);
                });
            }
            case 4 -> {
                if (first == null) {
                    idle(kit, turret);
                    return;
                }
                Location center = kit.ground(first.getLocation());
                kit.sound(turret.top(), Sfx.BOW_LOOSE, 0.8f, 1.3f);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int index = 0; index < count; index++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double r = 2.5 * Math.sqrt(random.nextDouble());
                    Location spot = kit.ground(center.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r));
                    kit.later(6L + index * 2L, () -> {
                        kit.fx(spot).line(spot.clone().add(0.6, 6.0, 0.3), spot.clone().add(0.0, 0.1, 0.0), SPIRIT, 0.3, 1.0, 1.5, 1.0, 0.3, null, 1);
                        kit.particle(Particle.CRIT, spot.clone().add(0.0, 0.2, 0.0), 3, 0.2, 0.05);
                        for (LivingEntity victim : kit.around(spot, 1.3)) hitFrom(kit, victim, rate);
                    });
                }
                kit.later(6L, () -> kit.sound(center, Sfx.HIT_PIERCE, 0.7f, 1.2f));
            }
            default -> {
                // overdrive: an arrow at everyone in reach now, and quick shots for a while
                turret.fastUntil = Bukkit.getCurrentTick() + (int) Math.round(kit.number(PLAN, "overdrive-seconds", 4.0) * 20.0);
                kit.sound(turret.top(), Sfx.SPIRIT_OVERDRIVE, 1.0f, 1.0f);
                kit.shockwave(turret.at, 0.6, 2.6, 4, 1.1);
                for (int index = 0; index < targets.size(); index++) {
                    LivingEntity victim = targets.get(index);
                    kit.later(index, () -> {
                        if (victim.isValid() && !victim.isDead()) shoot(kit, turret, victim, rate);
                    });
                }
            }
        }
    }

    /**
     * The spirit bow at the placement: the one standing there, or a new one (the old one goes). It shoots by itself while the placement lasts.
     */
    private static Turret turret(MoveKit kit) {
        UUID id = kit.player.getUniqueId();
        Location at = kit.ground(kit.origin());
        Turret old = TURRETS.get(id);
        if (old != null && old.alive && old.at.getWorld() == at.getWorld() && old.at.distanceSquared(at) < 0.25) return old;
        if (old != null) old.remove();
        BlockDisplay post = at.getWorld().spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(Material.OAK_FENCE.createBlockData());
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setTransformation(new Transformation(new Vector3f(-0.5f, 0.0f, -0.5f), new Quaternionf(), new Vector3f(1.0f, 1.1f, 1.0f), new Quaternionf()));
        });
        Location top = at.clone().add(0.0, 1.55, 0.0);
        ItemDisplay bow = at.getWorld().spawn(top, ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(Material.BOW));
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(bowShape(0.0));
        });
        Turret turret = new Turret(at, bow, post);
        TURRETS.put(id, turret);
        kit.sound(at, Sfx.SPIRIT_SET, 1.0f, 1.0f);
        ComboParts.appear(kit, at);
        int period = (int) Math.round(kit.number(PLAN, "turret-period", 20.0));
        int fast = (int) Math.round(kit.number(PLAN, "overdrive-period", 7.0));
        double rate = kit.number(PLAN, "turret-rate", 0.3);
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
            int tick = Bukkit.getCurrentTick();
            turret.seen = tick;
            boolean quick = tick < turret.fastUntil;
            boolean fire = age > 0 && age % (quick ? fast : period) == 0;
            if (age % 2 != 0 && !fire) return;
            if (age % 4 == 0) {
                Fx fx = kit.fx(at);
                fx.flatRing(at.clone().add(0.0, 0.12, 0.0), 0.9, 18, SPIRIT, quick ? 1.0 : 0.6, 0.2, 0.9);
                fx.dust(top.getX(), top.getY() + 0.05 * Math.sin(age * 0.2), top.getZ(), SPIRIT, 0.9, 1.0);
                if (quick) kit.particle(Particle.VILLAGER_HAPPY, top, 1, 0.3, 0.0);
            }
            List<LivingEntity> targets = targets(kit, turret);
            if (targets.isEmpty()) {
                // nothing to shoot: the bow turns slowly, keeping watch
                turret.facing += 0.05;
                if (age % 2 == 0) aimBow(turret, turret.facing, 2);
                return;
            }
            LivingEntity target = targets.get(0);
            Vector to = target.getLocation().toVector().subtract(at.toVector());
            turret.facing = Math.atan2(to.getZ(), -to.getX());
            if (age % 2 == 0) aimBow(turret, turret.facing, 2);
            if (fire) shoot(kit, turret, target, rate);
        }, zone -> {
            turret.remove();
            TURRETS.remove(id, turret);
        });
        return turret;
    }

    /** The bow's turn: upright (the sprite's diagonal stood up) and turned about the vertical by {@code angle}. */
    private static Transformation bowShape(double angle) {
        Quaternionf turn = new Quaternionf().rotateY((float) angle).mul(new Quaternionf().rotateZ((float) (-Math.PI / 4.0)));
        return new Transformation(new Vector3f(), turn, new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf());
    }

    private static void aimBow(Turret turret, double angle, int ticks) {
        if (!turret.bow.isValid()) return;
        turret.bow.setInterpolationDelay(0);
        turret.bow.setInterpolationDuration(ticks);
        turret.bow.setTransformation(bowShape(angle));
    }

    /**
     * The enemies the spirit bow can shoot, the first one first: the tied ones, then by distance; within its reach, and (with the class's passive)
     * those within 5 blocks of the ranger too, at most 24 blocks from it.
     */
    private static List<LivingEntity> targets(MoveKit kit, Turret turret) {
        double reach = kit.number(PLAN, "turret-range", 12.0);
        List<LivingEntity> found = new ArrayList<>(kit.around(turret.at, reach));
        Player player = kit.player;
        if (kit.hasSkill(SELF_ZONE) && player.getWorld() == turret.at.getWorld() && player.getLocation().distanceSquared(turret.at) <= 24.0 * 24.0) {
            for (LivingEntity near : kit.around(player.getLocation(), 5.0)) {
                if (!found.contains(near) && near.getLocation().distanceSquared(turret.at) <= 24.0 * 24.0) found.add(near);
            }
        }
        found.sort(java.util.Comparator.comparing((LivingEntity victim) -> !RangerSkills.tied(victim))
                .thenComparingDouble(victim -> victim.getLocation().distanceSquared(turret.at)));
        return found;
    }

    /** The ranger's passive that lets the spirit bow shoot the enemies round the ranger. */
    private static final String SELF_ZONE = "ranger_self_deploy";

    /** One arrow of the spirit bow at {@code victim}: drawn from the bow, {@code tied-bonus} more on a tied enemy. True when it hit. */
    private static boolean shoot(MoveKit kit, Turret turret, LivingEntity victim, double rate) {
        Location from = turret.top();
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        if (from.getWorld() != chest.getWorld()) return false;
        Vector way = chest.toVector().subtract(from.toVector());
        if (way.lengthSquared() > 1.0E-6) {
            turret.facing = Math.atan2(way.getZ(), -way.getX());
            aimBow(turret, turret.facing, 1);
            kit.fx(from).line(from, chest, SPIRIT, 0.2, 1.0, 1.5, 0.9, 0.3, null, 1);
        }
        kit.sound(from, Sfx.BOW_LOOSE, 0.45f, 1.4f);
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
    private static void idle(MoveKit kit, Turret turret) {
        kit.sound(turret.top(), Sfx.BOW_LOOSE, 0.3f, 1.8f);
        kit.fx(turret.top()).sphereShell(turret.top(), 0.4, 0.0, 8, SPIRIT, 0.7);
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                // a sigil of caltrops on the ground under the feet
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                ComboParts.sigil(kit, "ranger_sigil", at, 3.5, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.5)) kit.slow(victim, 30.0, 1.0);
                });
            }
            case 1 -> {
                // a cage: eight lanterns in a ring round the aim; what is inside is cut and slowed
                kit.sound(target, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
                kit.zone("ranger_cage", target, kit.linger(50), 4, zone -> {
                    boolean pulse = zone.age() % 12 == 0;
                    for (int index = 0; index < 8; index++) {
                        double angle = Math.PI * 2.0 * index / 8.0;
                        ComboParts.standLantern(kit, target.clone().add(Math.cos(angle) * 3.0, 0.0, Math.sin(angle) * 3.0), 1.2, pulse ? 1.0 : 0.0);
                    }
                    kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), 3.0, 48, kit.palette(), 0.45, 0.15, 0.9);
                    if (!pulse) return;
                    kit.shockwave(target, 1.0, 3.0, 3, 0.9);
                    for (LivingEntity victim : kit.around(target, 3.0)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 60.0, 1.5);
                    }
                }, zone -> kit.shockwave(target, 2.5, 3.2, 2, 0.8));
            }
            case 2 -> {
                // a wall of five lanterns across the aim
                Vector forward = kit.originFacing();
                Vector side = new Vector(-forward.getZ(), 0.0, forward.getX());
                Location middle = kit.ground(feet.clone().add(forward.clone().multiply(4.5)));
                kit.sound(feet, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
                kit.zone("ranger_wall", middle, kit.linger(50), 4, zone -> {
                    boolean pulse = zone.age() % 8 == 0;
                    for (int index = -2; index <= 2; index++) {
                        Location at = middle.clone().add(side.clone().multiply(index * 1.6));
                        ComboParts.standLantern(kit, at, 1.3, pulse ? 1.0 : 0.0);
                        if (pulse) kit.hitAll(kit.around(at, 1.5), rate);
                    }
                }, zone -> kit.shockwave(middle, 1.0, 2.5, 3, 0.8));
            }
            case 3 -> {
                // three arrow lanterns round the aim
                for (int index = 0; index < 3; index++) {
                    double angle = Math.PI * 2.0 * index / 3.0 + Math.PI * 0.5;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2));
                    String key = "ranger_turret_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 8, 9.0, rate, null));
                }
            }
            case 4 -> {
                // a great lantern: it swells for 2 seconds and pulls the enemies in, then bursts
                Location at = target.clone().add(0.0, 1.5, 0.0);
                ComboParts.pullBurst(kit, at, 5.0, rate, 0.0, 40);
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a watch post at the aim that shoots an arrow at the nearest enemy every 8 ticks for 2.5 s, each slowing it. */
    private void sixth(MoveKit kit, double rate) {
        Location target = ComboParts.target(kit);
        kit.sound(kit.origin(), org.bukkit.Sound.BLOCK_WOOD_PLACE, 1.0f, 0.9f);
        ComboParts.turret(kit, "ranger_watch", target, 50, 8, 12.0, rate, victim -> kit.slow(victim, 30.0, 1.0));
    }
}
