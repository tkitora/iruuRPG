package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.vfx.Curves;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The parts the {@link ClassCombo}s are made of: where the player faces, who is in reach (a cone, a ring, a corridor),
 * one hit of the weapon on someone (by the kind of the weapon), a push or a pull, a dash or a leap, a flying bolt, a zone
 * that stands for a time, and the slashes, rings and beams drawn with the gradients of {@link Palettes}. One kit is made for
 * each attack.
 *
 * <p>Rules for what a combo puts in the world (zones, traps, orbiting things): every one has a life of a few seconds
 * at the most (less than a round of the combo takes, so the second round never meets the first one's), and a new one of the
 * same kind of the same player replaces the old one.
 */
public final class MoveKit {

    private static final Vector X = new Vector(1.0, 0.0, 0.0);
    private static final Vector Y = new Vector(0.0, 1.0, 0.0);
    private static final Vector Z = new Vector(0.0, 0.0, 1.0);

    private final JavaPlugin plugin;
    private final AttackService attack;
    private final AttackEffects effects;
    private final Zones zones;
    public final Player player;
    public final RpgItemDefinition weapon;
    private Gradient palette;
    /** The key of the placed thing whose zone is running (null outside one): see {@link #hit}. */
    private String source;
    private Location origin;
    private Vector originFacing;
    /** Placed things (lanterns, mines, bombs) hit this much harder than the numbers of the combo say. */
    private static final double DEPLOY_BONUS = 1.1;

    MoveKit(JavaPlugin plugin, AttackService attack, AttackEffects effects, Zones zones, Player player, RpgItemDefinition weapon) {
        this.plugin = plugin;
        this.attack = attack;
        this.effects = effects;
        this.zones = zones;
        this.player = player;
        this.weapon = weapon;
        this.palette = Palettes.of(weapon.element());
    }

    // ---- where the combo began ---------------------------------------------------------------------------------------------------

    /** Sets where the chain of this combo began and the way the player faced then: placed things are put round that place, not round where the player stands now. */
    void anchor(Location origin, Vector facing) {
        this.origin = origin == null ? null : origin.clone();
        this.originFacing = facing == null ? null : facing.clone();
    }

    /** Where the combo began (the player's own place when it has not been set). */
    public Location origin() {
        return (origin != null ? origin : player.getLocation()).clone();
    }

    public Vector originFacing() {
        return originFacing != null ? originFacing.clone() : facing();
    }

    /** To the right of {@link #originFacing()}. */
    public Vector originRight() {
        Vector f = originFacing();
        return new Vector(-f.getZ(), 0.0, f.getX());
    }

    // ---- numbers and timing ------------------------------------------------------------------------------------------------

    public double number(String plan, String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo." + plan + "." + key, fallback);
    }

    public void later(long ticks, Runnable action) {
        if (ticks <= 0L) action.run();
        else plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) action.run();
        }, ticks);
    }

    /** Runs {@code action} once a tick for {@code ticks} ticks with the age (0 - ); it ends early when it returns true, or when the player leaves or dies. */
    public void every(int ticks, java.util.function.IntPredicate action) {
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= ticks) {
                    cancel();
                    return;
                }
                if (action.test(age++)) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- where and who ------------------------------------------------------------------------------------------------------

    public World world() {
        return player.getWorld();
    }

    public Location eye() {
        return player.getEyeLocation();
    }

    /** The way the player faces, flat. */
    public Vector facing() {
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) direction = new Vector(0.0, 0.0, 1.0);
        return direction.normalize();
    }

    /** The view: {forward, right, up}. */
    public Vector[] axes() {
        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector right = forward.clone().crossProduct(Y);
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        return new Vector[]{forward, right, right.clone().crossProduct(forward).normalize()};
    }

    /** Where the player aims: the first enemy or block in the way, or a point of the range ahead. */
    public Location aim() {
        return attack.traceRange(player).hitLocation();
    }

    /** The enemy the player aims at, or null. */
    public LivingEntity aimed() {
        return attack.traceRange(player).victim();
    }

    public List<LivingEntity> around(Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, Math.max(2.0, radius * 0.6), radius)) {
            if (!attack.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.getLocation().getX() - center.getX();
            double dz = living.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            result.add(living);
        }
        return result;
    }

    /** The enemies within {@code radius} of the player and {@code halfAngle} degrees of where the player faces. */
    public List<LivingEntity> cone(double radius, double halfAngle) {
        Vector facing = facing();
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : around(player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            result.add(target);
        }
        return result;
    }

    /** The enemies within {@code width} of the line from {@code from} along {@code direction} for {@code length}. */
    public List<LivingEntity> corridor(Location from, Vector direction, double length, double width) {
        List<LivingEntity> result = new ArrayList<>();
        Vector end = from.toVector().add(direction.clone().multiply(length));
        Vector low = Vector.getMinimum(from.toVector(), end).subtract(new Vector(width, width, width));
        Vector high = Vector.getMaximum(from.toVector(), end).add(new Vector(width, width + 1.0, width));
        for (Entity entity : from.getWorld().getNearbyEntities(org.bukkit.util.BoundingBox.of(low, high))) {
            if (!attack.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            Vector center = living.getLocation().toVector().add(new Vector(0.0, living.getHeight() * 0.5, 0.0));
            double along = center.clone().subtract(from.toVector()).dot(direction);
            if (along < -0.5 || along > length + 0.5) continue;
            Vector nearest = from.toVector().add(direction.clone().multiply(Math.max(0.0, Math.min(length, along))));
            if (nearest.distanceSquared(center) <= width * width) result.add(living);
        }
        return result;
    }

    // ---- hits and pushes ------------------------------------------------------------------------------------------------------

    /** One hit of {@code rate} times the weapon's damage (a ranged weapon hits as a ranged attack, any other as its own kind). */
    public boolean hit(LivingEntity target, double rate) {
        if (weapon.attackType() == AttackType.RANGE) return attack.strikeRanged(player, target, weapon, rate);
        if (weapon.attackType() == AttackType.DEPLOY) {
            // placed things hit 10% harder, but however many overlap, an enemy takes the hits of one of them at a time
            if (source != null && !zones.allowDeployHit(player, target, source)) return false;
            return attack.strike(player, target, weapon, rate * DEPLOY_BONUS);
        }
        return attack.strike(player, target, weapon, rate);
    }

    /** One hit on each; how many were hit. */
    public int hitAll(List<LivingEntity> targets, double rate) {
        int count = 0;
        for (LivingEntity target : targets) if (hit(target, rate)) count++;
        return count;
    }

    /** Who is in the air from a knockback, and how hard they were thrown: {strength, tick}. */
    private static final Map<UUID, double[]> THROWN = new HashMap<>();

    /**
     * A knockback of {@code strength} on {@code target}, if they may take it: while a target is still in the air from a knockback or a knock-up, a new one of no more
     * strength than the one they are in is not taken (so chained hits cannot hold an enemy helpless until the landing); a stronger one is, and becomes the one they are in.
     */
    private static boolean allowKnock(LivingEntity target, double strength) {
        int now = org.bukkit.Bukkit.getCurrentTick();
        UUID id = target.getUniqueId();
        double[] state = THROWN.get(id);
        if (state != null) {
            int since = now - (int) state[1];
            if ((since > 3 && target.isOnGround()) || since > 80 || !target.isValid()) {
                THROWN.remove(id);
                state = null;
            }
        }
        if (state != null && strength <= state[0] + 1.0E-9) return false;
        THROWN.put(id, new double[]{strength, now});
        if (THROWN.size() > 400) THROWN.values().removeIf(entry -> now - (int) entry[1] > 80);
        return true;
    }

    public void push(LivingEntity target, Vector from, double power, double lift) {
        if (!allowKnock(target, Math.sqrt(power * power + lift * lift))) return;
        Vector away = target.getLocation().toVector().subtract(from).setY(0.0);
        if (away.lengthSquared() < 1.0E-6) away = facing();
        target.setVelocity(away.normalize().multiply(power).setY(lift));
    }

    public void pull(LivingEntity target, Location to, double power) {
        Vector toward = to.toVector().subtract(target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector());
        if (toward.lengthSquared() < 0.04) return;
        if (!allowKnock(target, power)) return;
        target.setVelocity(toward.normalize().multiply(power).setY(0.1));
    }

    public void lift(LivingEntity target, double power) {
        if (!allowKnock(target, power)) return;
        target.setVelocity(new Vector(target.getVelocity().getX() * 0.3, power, target.getVelocity().getZ() * 0.3));
    }

    public void slow(LivingEntity target, double percent, double seconds) {
        attack.slow(target, percent, Math.round(seconds * 1000.0), player.getUniqueId());
    }

    // ---- hands, guard and blinks ---------------------------------------------------------------------------------------------------

    /** The hand the next cut is struck with: an assassin with a weapon in each hand (固有0) strikes with them in turn; anybody else with the main hand. */
    private boolean offHandVisuals;

    /** Turns on the look of a cut with the off hand (see {@link #nextHand}); off unless a combo asks for it. */
    void offHandVisuals(boolean on) {
        this.offHandVisuals = on;
    }

    public AttackService.MeleeHand nextHand() {
        AttackService.MeleeHand hand = attack.nextSlashHand(player, weapon);
        if (hand.offHand() && offHandVisuals) offHandFlourish();
        return hand;
    }

    /**
     * The look of a cut made with the weapon in the off hand (the assassin holding a weapon in each hand): the off arm swings, and two cuts at different angles cross in
     * front of the assassin, a moment apart, one rising and one falling, in the colours of the combo.
     */
    private void offHandFlourish() {
        attack.swingOffHand(player);
        // (about a right angle apart, but not exactly a cross)
        slash("arc", 2.4, 110.0, 43.0, 0.0, 1.7, 0.4, 0.0, 1.0, 0.07, 0.0);
        slash("arc", 2.4, 110.0, -43.0, 180.0, 1.7, 0.5, 0.0, 1.0, 0.07, 0.05);
        sound(player.getLocation(), Sfx.WIND, 0.4f, 1.9f);
    }

    /** One hit of {@code rate} times the damage of {@code hand}'s weapon (and of the other hand's when the double strike fires). */
    public boolean hitHand(AttackService.MeleeHand hand, LivingEntity target, double rate) {
        if (!attack.strike(player, target, hand, rate)) return false;
        if (hand.doubleStrike()) attack.doubleStrikeHit(player, target, hand);
        return true;
    }

    /** The damage the player takes is cut by {@code share} (1.0 = none at all) for {@code ticks} ticks. */
    public void guard(double share, int ticks) {
        if (attack.guard() != null) attack.guard().grant(player, share, ticks);
    }

    /** No damage at all for {@code ticks} ticks (a dash through enemies). */
    public void invulnerable(int ticks) {
        guard(1.0, ticks);
    }

    /** 0 normally; during the assassin's frenzy (SATUJIN) its level: 1, or 2 when upgraded. */
    public int frenzy() {
        return attack.assassinFrenzy(player);
    }

    /** One more shield turning round the player (the warrior's), up to the number a bulwark gives. */
    public void addShield() {
        if (attack.guard() != null) attack.guard().addShield(player);
    }

    /** All the shields of a bulwark. */
    public void bulwark() {
        if (attack.guard() != null) attack.guard().gainShields(player);
    }

    public int shields() {
        return attack.guard() == null ? 0 : attack.guard().shieldCount(player);
    }

    public void confuse(LivingEntity target, double amount) {
        attack.confuse(target, amount, player.getUniqueId());
    }

    /** The enemy the player aims at within {@code range}, else the nearest one within {@code range} and {@code halfAngle} degrees of where the player faces; null when none. */
    public LivingEntity nearest(double range, double halfAngle) {
        LivingEntity aimed = aimed();
        if (aimed != null && aimed.getLocation().distanceSquared(player.getLocation()) <= range * range) return aimed;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        Vector facing = facing();
        for (LivingEntity target : around(player.getLocation(), range)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            double distance = target.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }

    // ---- clones (they take the place of teleports: a teleport throws the camera) ---------------------------------------------------

    /** The angle (radians in the horizontal plane) of the place behind {@code target}, the side opposite to where it faces. */
    public double behindAngle(LivingEntity target) {
        Vector facing = target.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = facing();
        return Math.atan2(-facing.getZ(), -facing.getX());
    }

    /** The place the item swap skill was used, while it lasts (null otherwise). */
    Location frenzyCenter() {
        return attack.frenzyCenter(player);
    }

    /** Ticks left of the placement of a deployed weapon this kit works for (0 when it does not work for one). */
    private int holdTicks;

    void holdTicks(int ticks) {
        this.holdTicks = Math.max(0, ticks);
    }

    /**
     * The life of something that is set down and stays (a trap, a lantern, a mine, a sigil): the life the combo gives it, or, when the attack is made at the placement of a
     * deployed weapon, as long as the placement still lasts (the zones keep their own rhythm; only the time they are there grows).
     */
    int linger(int lifeTicks) {
        return holdTicks > 0 ? Math.max(lifeTicks, holdTicks) : lifeTicks;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    /**
     * A clone of the player steps in to {@code target} from {@code angle} (radians round it), turns to it, swings {@code hits} times {@code gap} ticks apart and goes: for each blow
     * {@code onHit} gets its number (the caller does the damage). The player does not move. The assassin can have two clones at a time (the standing ones count): when there is
     * no room the blows land at once with no figure. The clone is small and has no glow (the standing clones and those of the item swap skill are full-size); red and black while the item swap skill lasts, grey otherwise.
     */
    public void clone(LivingEntity target, double angle, int hits, int gap, boolean quality, java.util.function.IntConsumer onHit) {
        if (!AssassinClones.beginTransient(player)) {
            for (int index = 0; index < hits; index++) {
                int which = index;
                later(3L + (long) index * gap, () -> onHit.accept(which));
            }
            return;
        }
        Clones.Look look = AssassinClones.lookFor(this, attack.assassinFrenzy(player) > 0);
        Clones.strike(plugin, player, look, () -> target.isValid() && !target.isDead() ? target.getLocation() : null, angle, hits, gap, onHit, () -> AssassinClones.endTransient(player));
    }

    /** A clone that runs from {@code from} to {@code to} in {@code ticks} ticks; {@code onStep} gets each tick (no figure when two clones exist already: the steps still come). */
    public void cloneRun(Location from, Location to, int ticks, java.util.function.IntConsumer onStep) {
        if (!AssassinClones.beginTransient(player)) {
            for (int tick = 0; tick < ticks; tick++) {
                int which = tick;
                later(which, () -> onStep.accept(which));
            }
            return;
        }
        Clones.run(plugin, player, AssassinClones.lookFor(this, attack.assassinFrenzy(player) > 0), from, to, ticks, onStep, () -> AssassinClones.endTransient(player));
    }

    /** The colours the slashes of this kit are drawn in (null: the weapon's own). Set by the combos that change their look. */
    private java.util.List<org.bukkit.Color> slashColors;

    void slashColors(java.util.List<org.bukkit.Color> colors) {
        this.slashColors = colors;
    }

    /**
     * A dash along {@code heading} that stops at the first enemy within {@code reach} blocks of the player (a contact) or after
     * {@code ticks} ticks; {@code end} gets the place and the enemy touched (null when there was none).
     */
    public void dashToContact(Vector heading, double power, int ticks, double reach, java.util.function.BiConsumer<Location, LivingEntity> end) {
        Vector flat = heading.clone().setY(0.0).normalize();
        every(ticks + 1, age -> {
            player.setFallDistance(0.0f);
            LivingEntity touched = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity target : around(player.getLocation(), reach)) {
                double distance = target.getLocation().distanceSquared(player.getLocation());
                if (distance < best) {
                    best = distance;
                    touched = target;
                }
            }
            if (touched != null || age >= ticks) {
                player.setVelocity(new Vector(0.0, Math.min(0.0, player.getVelocity().getY()), 0.0));
                if (end != null) end.accept(player.getLocation(), touched);
                return true;
            }
            player.setVelocity(flat.clone().multiply(power).setY(Math.min(0.0, player.getVelocity().getY())));
            return false;
        });
    }

    // ---- movement -------------------------------------------------------------------------------------------------------------

    /** A dash along {@code direction}: {@code power} a tick for {@code ticks} ticks, then {@code end} (at the spot where it ended). */
    public void dash(Vector direction, double power, int ticks, Consumer<Location> end) {
        Vector heading = direction.clone().setY(0.0).normalize();
        every(ticks + 1, age -> {
            player.setFallDistance(0.0f);
            if (age >= ticks) {
                if (end != null) end.accept(player.getLocation());
                return true;
            }
            player.setVelocity(heading.clone().multiply(power).setY(Math.min(0.0, player.getVelocity().getY())));
            return false;
        });
    }

    /** A leap: {@code horizontal} (a direction) times {@code power} and {@code up}; {@code onLand} when the feet touch the ground again (or after 30 ticks). */
    public void leap(Vector horizontal, double power, double up, Consumer<Location> onLand) {
        player.setFallDistance(0.0f);
        player.setVelocity(horizontal.clone().setY(0.0).normalize().multiply(power).setY(up));
        if (onLand == null) return;
        every(30, age -> {
            player.setFallDistance(0.0f);
            if (age > 5 && player.isOnGround()) {
                onLand.accept(player.getLocation());
                return true;
            }
            return false;
        });
    }

    public void hopBack(double power, double up) {
        player.setFallDistance(0.0f);
        player.setVelocity(facing().multiply(-power).setY(up));
    }

    // ---- bolts, zones ---------------------------------------------------------------------------------------------------------

    /**
     * A bolt from {@code start} along {@code direction}: {@code speed} blocks a tick, at most {@code range} blocks; it hits the
     * first enemy (one hit of {@code rate}) or block. Drawn with the palette (a tail and a head). {@code onEnd}: where it ended.
     */
    public void bolt(Location start, Vector direction, double speed, double range, double rate, Consumer<Location> onEnd) {
        Vector heading = direction.clone().normalize();
        World world = start.getWorld();
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6) * 0.7;
        new SafeTask(plugin) {
            private Location position = start.clone();
            private double travelled;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                double step = Math.min(speed, range - travelled);
                if (step <= 1.0E-6) {
                    cancel();
                    if (onEnd != null) onEnd.accept(position);
                    return;
                }
                RayTraceResult result = world.rayTrace(position, heading, step, FluidCollisionMode.NEVER, true, raySize,
                        entity -> attack.isAttackTarget(entity, player));
                Location next = result != null && result.getHitPosition() != null ? result.getHitPosition().toLocation(world)
                        : position.clone().add(heading.clone().multiply(step));
                line(position, next, 0.2, 1.0, 1.1);
                if (result != null) {
                    cancel();
                    if (result.getHitEntity() instanceof LivingEntity victim) hit(victim, rate);
                    if (onEnd != null) onEnd.accept(next);
                    return;
                }
                position = next;
                travelled += step;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Something that stands in the world for a time ({@code lifeTicks}, at most {@code Zones.MAX_TICKS}): {@code tick} runs every
     * {@code period} ticks with the zone, {@code end} once when it ends. One of the same {@code key} of this player is replaced.
     */
    public void zone(String key, Location center, int lifeTicks, int period, Consumer<Zones.Zone> tick, Consumer<Zones.Zone> end) {
        // what the zone hits is marked with its key, so several of the same kind that overlap do not add up (see hit)
        Consumer<Zones.Zone> markedTick = tick == null ? null : zone -> {
            String before = source;
            source = key;
            try {
                tick.accept(zone);
            } finally {
                source = before;
            }
        };
        Consumer<Zones.Zone> markedEnd = end == null ? null : zone -> {
            String before = source;
            source = key;
            try {
                end.accept(zone);
            } finally {
                source = before;
            }
        };
        zones.add(player, key, center, lifeTicks, period, markedTick, markedEnd);
    }

    /** A lobbed shot: from {@code from} to {@code to} in {@code ticks} ticks along an arc {@code height} blocks high, drawn as a trail; {@code land} where it comes down. */
    public void lob(Location from, Location to, int ticks, double height, Consumer<Location> land) {
        Location start = from.clone();
        Location end = to.clone();
        every(ticks + 1, age -> {
            ParticleFx.mark(player);
            double t = Math.min(1.0, age / (double) ticks);
            double previous = Math.max(0.0, (age - 1) / (double) ticks);
            Location now = arcPoint(start, end, t, height);
            if (age > 0) line(arcPoint(start, end, previous, height), now, 0.3, 1.0, 1.2);
            if (age >= ticks) {
                if (land != null) land.accept(end);
                return true;
            }
            return false;
        });
    }

    private static Location arcPoint(Location from, Location to, double t, double height) {
        return new Location(from.getWorld(), Curves.lerp(from.getX(), to.getX(), t), Curves.lerp(from.getY(), to.getY(), t) + 4.0 * height * t * (1.0 - t),
                Curves.lerp(from.getZ(), to.getZ(), t));
    }

    /** A bolt that bends toward the nearest enemy within {@code seek} blocks after a moment: {@code rate} on the first it touches; {@code onEnd}: where it ended. */
    public void homing(Location start, Vector heading, double speed, int life, double rate, double seek, Consumer<Location> onEnd) {
        World world = start.getWorld();
        Vector velocity = heading.clone().normalize().multiply(speed * 0.6);
        Location[] position = {start.clone()};
        LivingEntity[] target = {null};
        every(life, age -> {
            ParticleFx.mark(player);
            if (age >= 4) {
                if (target[0] == null || target[0].isDead() || !target[0].isValid() || age % 6 == 0) {
                    target[0] = null;
                    double best = seek * seek;
                    for (LivingEntity candidate : around(position[0], seek)) {
                        double distance = candidate.getLocation().add(0.0, candidate.getHeight() * 0.5, 0.0).distanceSquared(position[0]);
                        if (distance < best) {
                            best = distance;
                            target[0] = candidate;
                        }
                    }
                }
                if (target[0] != null) {
                    Vector toward = target[0].getLocation().add(0.0, target[0].getHeight() * 0.5, 0.0).toVector().subtract(position[0].toVector());
                    if (toward.lengthSquared() > 1.0E-6) velocity.add(toward.normalize().multiply(speed).subtract(velocity).multiply(0.4));
                }
            }
            double length = velocity.length();
            if (length > speed) velocity.multiply(speed / length);
            if (length < 1.0E-6) return false;
            Vector direction = velocity.clone().normalize();
            RayTraceResult result = world.rayTrace(position[0], direction, velocity.length(), FluidCollisionMode.NEVER, true, 0.45,
                    entity -> attack.isAttackTarget(entity, player));
            Location next = result != null && result.getHitPosition() != null ? result.getHitPosition().toLocation(world) : position[0].clone().add(velocity);
            line(position[0], next, 0.2, 1.0, 1.0);
            if (result != null) {
                if (result.getHitEntity() instanceof LivingEntity victim) hit(victim, rate);
                if (onEnd != null) onEnd.accept(next);
                return true;
            }
            position[0] = next;
            return false;
        });
    }

    /** An arrow of the player's ranged weapon from {@code start}: the speed of the weapon's shots times {@code speedScale}, the range of its shots. */
    public void arrow(Location start, Vector direction, double rate, double speedScale, Consumer<Location> onEnd) {
        effects.playArrowLaunch(player, start);
        bolt(start, direction, attack.projectileSpeed(player) * speedScale / 20.0, attack.rangeDistance(player), rate, onEnd);
    }

    /**
     * A shot that bounces off up to {@code bounces} blocks: {@code rate} on the first enemy it touches (it ends there), {@code onBounce}
     * at each block (where it turned). Drawn as the bolts are.
     */
    public void ricochet(Location start, Vector direction, int bounces, double speed, int maxTicks, double rate, Consumer<Location> onBounce) {
        World world = start.getWorld();
        Location[] position = {start.clone()};
        Vector[] heading = {direction.clone().normalize()};
        int[] left = {bounces};
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6) * 0.6;
        every(maxTicks, age -> {
            ParticleFx.mark(player);
            RayTraceResult result = world.rayTrace(position[0], heading[0], speed, FluidCollisionMode.NEVER, true, raySize,
                    entity -> attack.isAttackTarget(entity, player));
            Location next = result != null && result.getHitPosition() != null ? result.getHitPosition().toLocation(world)
                    : position[0].clone().add(heading[0].clone().multiply(speed));
            line(position[0], next, 0.2, 1.0, 1.1);
            if (result == null) {
                position[0] = next;
                return false;
            }
            if (result.getHitEntity() instanceof LivingEntity victim) {
                hit(victim, rate);
                if (onBounce != null) onBounce.accept(next);
                return true;
            }
            if (onBounce != null) onBounce.accept(next);
            if (left[0] <= 0 || result.getHitBlockFace() == null) return true;
            left[0]--;
            Vector normal = result.getHitBlockFace().getDirection();
            heading[0] = heading[0].clone().subtract(normal.clone().multiply(2.0 * heading[0].dot(normal))).normalize();
            position[0] = next.clone().add(normal.clone().multiply(0.15));
            return false;
        });
    }

    private static final Gradient ARROW_SHAFT = Gradient.of(org.bukkit.Color.fromRGB(90, 59, 30), org.bukkit.Color.fromRGB(194, 160, 106));
    private static final Gradient ARROW_HEAD = Gradient.of(org.bukkit.Color.fromRGB(150, 156, 164), org.bukkit.Color.fromRGB(240, 244, 248));
    private static final Gradient ARROW_FEATHER = Gradient.of(org.bukkit.Color.fromRGB(170, 170, 170), org.bukkit.Color.fromRGB(240, 240, 240));

    /**
     * An arrow of the player's bow from {@code start}: drawn as an arrow (a shaft, a steel head, feathers at the tail, a faint trail), flying in an arc (gravity pulls it
     * down, and the aim is raised to meet it where the player looks), with a ring of light spreading from the bow as it is loosed. It hits the first enemy it touches for
     * {@code rate}. For a ranger, an arrow that passes near one of the ranger's placed lanterns hits 25% harder, and every lantern near an enemy it hits sends a shot of
     * its own (30%) at that enemy: the lanterns and the bow work together.
     */
    public void arrowShot(Location start, Vector direction, double rate, double speedScale, Consumer<Location> onEnd) {
        effects.playArrowLaunch(player, start);
        Vector heading = direction.clone().normalize();
        // the ring of light at the muzzle: four rings opening one after the other. Everyone else sees all four; the shooter sees only
        // the outermost one, so the rings do not stand in front of their own aim.
        Vector u = heading.clone().crossProduct(Y);
        if (u.lengthSquared() < 1.0E-6) u = X.clone();
        u.normalize();
        Vector v = heading.clone().crossProduct(u).normalize();
        Vector ringU = u;
        Location ringAt = start.clone().add(heading.clone().multiply(0.5));
        for (int step = 0; step < 4; step++) {
            int which = step;
            later(step, () -> {
                Fx ring = fx(ringAt);
                if (which < 3) ring = ring.exceptOwner();
                ring.ring(ringAt.clone().add(heading.clone().multiply(0.15 * which)), ringU, v, 0.3 + 0.32 * which, 20, palette, 0.9 - 0.18 * which, 0.1, 1.0);
            });
        }
        double speed = Math.max(0.5, attack.projectileSpeed(player) * speedScale / 20.0);
        double range = attack.rangeDistance(player);
        double gravity = plugin.getConfig().getDouble("combat.combo.arrow-gravity", 0.02);
        // aim high enough to come down on the point looked at
        double distance = Math.max(1.0, Math.min(range, start.distance(aim())));
        double flight = distance / speed;
        Vector velocity = heading.clone().multiply(distance).add(new Vector(0.0, 0.5 * gravity * flight * flight, 0.0)).normalize().multiply(speed);
        World world = start.getWorld();
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6) * 0.7;
        boolean ranger = "gifted".equals(attack.classIdOf(player));
        List<Zones.Zone> lanterns = ranger ? zones.near(player, "ranger_", start, range + 6.0) : List.of();
        boolean[] boosted = {false};
        double[] travelled = {0.0};
        Location[] position = {start.clone()};
        every((int) Math.ceil(range / speed) + 2, age -> {
            ParticleFx.mark(player);
            double effectiveRate = rate * (boosted[0] ? 1.25 : 1.0);
            Vector flight2 = velocity.clone();
            double length = flight2.length();
            Vector direction2 = flight2.clone().normalize();
            RayTraceResult result = world.rayTrace(position[0], direction2, length, FluidCollisionMode.NEVER, true, raySize, entity -> attack.isAttackTarget(entity, player));
            Location next = result != null && result.getHitPosition() != null ? result.getHitPosition().toLocation(world) : position[0].clone().add(flight2);
            drawArrow(position[0], next, direction2);
            if (!boosted[0]) {
                for (Zones.Zone lantern : lanterns) {
                    if (lantern.center.clone().add(0.0, 1.0, 0.0).distanceSquared(next) <= 6.25) {
                        boosted[0] = true;
                        burst(lantern.center.clone().add(0.0, 1.3, 0.0), 0.8);
                        sound(lantern.center, Sfx.MAGIC, 0.5f, 1.8f);
                        break;
                    }
                }
            }
            if (result != null) {
                if (result.getHitEntity() instanceof LivingEntity victim && hit(victim, effectiveRate)) relay(victim, lanterns, rate);
                if (onEnd != null) onEnd.accept(next);
                return true;
            }
            position[0] = next;
            velocity.setY(velocity.getY() - gravity);
            travelled[0] += length;
            return travelled[0] >= range;
        });
    }

    /** Every lantern of the ranger within 9 blocks of {@code victim} sends a shot of 30% at it (each once in 8 ticks). */
    private void relay(LivingEntity victim, List<Zones.Zone> lanterns, double rate) {
        int sent = 0;
        for (Zones.Zone lantern : lanterns) {
            if (sent >= 3 || lantern.center.getWorld() != victim.getWorld()) continue;
            if (lantern.center.distanceSquared(victim.getLocation()) > 81.0 || !zones.relayReady(player, lantern.key)) continue;
            sent++;
            Location top = lantern.center.clone().add(0.0, 1.35, 0.0);
            Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
            line(top, chest, 0.2, 1.0, 1.1);
            burst(top, 0.6);
            sound(top, Sfx.MAGIC, 0.35f, 1.9f);
            hit(victim, rate * 0.3);
        }
    }

    private void drawArrow(Location from, Location to, Vector heading) {
        Fx fx = fx(to);
        if (!fx.active()) return;
        // the arrow: head at `to`, a shaft behind it, feathers at the tail, and a faint trail behind that
        Location tail = to.clone().subtract(heading.clone().multiply(1.1));
        Location neck = to.clone().subtract(heading.clone().multiply(0.1));
        fx.line(tail, neck, ARROW_SHAFT, 0.0, 1.0, 0.0, 1.0, 0.1, null, 1);
        fx.line(neck, to.clone().add(heading.clone().multiply(0.15)), ARROW_HEAD, 0.4, 1.0, 0.0, 1.2, 0.07, null, 1);
        Vector side = heading.clone().crossProduct(Y);
        if (side.lengthSquared() < 1.0E-6) side = X.clone();
        side.normalize().multiply(0.16);
        Vector up = side.clone().crossProduct(heading).normalize().multiply(0.16);
        Location tailFront = tail.clone().add(heading.clone().multiply(0.35));
        fx.line(tail, tailFront.clone().add(side), ARROW_FEATHER, 0.2, 1.0, 0.0, 0.9, 0.1, null, 1);
        fx.line(tail, tailFront.clone().subtract(side), ARROW_FEATHER, 0.2, 1.0, 0.0, 0.9, 0.1, null, 1);
        fx.line(tail, tailFront.clone().add(up), ARROW_FEATHER, 0.2, 1.0, 0.0, 0.9, 0.1, null, 1);
        fx.line(tail.clone().subtract(heading.clone().multiply(2.2)), tail, palette, 0.05, 0.45, 1.5, 0.7, 0.3, null, 1);
    }

    /** A column of light standing at {@code base}: rings along it, widening toward the bottom, the color bright at the top. */
    public void pillar(Location base, double radius, double height) {
        Fx fx = fx(base);
        for (double y = 0.0; y <= height; y += 0.5) {
            double r = radius * (1.0 - 0.5 * y / height);
            fx.flatRing(base.clone().add(0.0, y, 0.0), r, 14, palette, 0.3 + 0.7 * y / height, 0.3, 1.1);
        }
    }

    /** Heals the player by {@code percent} of their maximum HP (a heal counts for the cleric's pulse like any other). */
    private static final java.util.Map<java.util.UUID, double[]> HEALED = new java.util.HashMap<>();

    /**
     * A heal from something set down: the sum of such heals is held to {@code combat.combo.deploy-heal-cap} (1.5) percent of the maximum health in any second, so lanterns
     * that stand together do not heal more than one does by much. The heal that would pass the cap is cut to what is left.
     */
    public void healCapped(double percent) {
        double cap = plugin.getConfig().getDouble("combat.combo.deploy-heal-cap", 1.5);
        int now = org.bukkit.Bukkit.getCurrentTick();
        double[] window = HEALED.computeIfAbsent(player.getUniqueId(), id -> new double[]{now, 0.0});
        if (now - window[0] >= 20) {
            window[0] = now;
            window[1] = 0.0;
        }
        double allowed = Math.max(0.0, Math.min(percent, cap - window[1]));
        if (allowed <= 0.0) return;
        window[1] += allowed;
        heal(allowed);
    }

    private static final java.util.Map<UUID, double[]> ALLY_HEALED = new java.util.HashMap<>();

    /**
     * Heals the players (the cleric among them) within {@code radius} of {@code center} by {@code percent} of their own maximum life, with the cleric's heal bonuses; one player
     * is healed by at most {@code combat.combo.ally-heal-cap} percent in a second by this, so that the lights of a staff do not make anyone unkillable.
     */
    public void healAllies(Location center, double radius, double percent) {
        double cap = plugin.getConfig().getDouble("combat.combo.ally-heal-cap", 4.0);
        int now = org.bukkit.Bukkit.getCurrentTick();
        for (Player ally : center.getWorld().getPlayers()) {
            if (ally.isDead() || !ally.isValid() || ally.getLocation().distanceSquared(center) > radius * radius) continue;
            double[] window = ALLY_HEALED.computeIfAbsent(ally.getUniqueId(), id -> new double[]{now, 0.0});
            if (now - window[0] >= 20) {
                window[0] = now;
                window[1] = 0.0;
            }
            double allowed = Math.max(0.0, Math.min(percent, cap - window[1]));
            if (allowed <= 0.0) continue;
            window[1] += allowed;
            attack.healPlayerBy(player, ally, attack.profileForPlayer(ally).maxHp() * allowed / 100.0);
        }
    }

    public void heal(double percent) {
        PlayerProfile profile = attack.profileForPlayer(player);
        attack.healPlayer(player, profile.maxHp() * percent / 100.0);
    }

    /** The boost of the ranger's loop for the next ranged attack: {damage multiplier, speed multiplier}; takes it. */
    public double[] rangeBoost() {
        return attack.takeRangeBoost(player);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------------------

    /** The weapon's element gradient (or what {@link #palette(Gradient)} set). */
    public Gradient palette() {
        return palette;
    }

    public MoveKit palette(Gradient gradient) {
        this.palette = gradient;
        return this;
    }

    public Fx fx(Location at) {
        return Fx.of(player, at.getWorld(), at);
    }

    /** A line in the palette: a deep tail going to a bright head. */
    public void line(Location from, Location to, double t0, double t1, double size) {
        Fx.of(player, from.getWorld(), from).line(from, to, palette, t0, t1, 2.5, size, 0.3, null, 1);
    }

    /** A ring on the ground widening from {@code r0} to {@code r1} over {@code ticks} ticks (an exponential curve): a shock wave. */
    public void shockwave(Location center, double r0, double r1, int ticks, double size) {
        for (int step = 0; step < ticks; step++) {
            double r = Curves.lerp(r0, r1, Curves.expOut((step + 1.0) / ticks, 3.0));
            double t = 1.0 - 0.6 * step / Math.max(1, ticks - 1);
            int count = (int) Math.max(18, r * 8);
            later(step, () -> fx(center).flatRing(center.clone().add(0.0, 0.15, 0.0), r, count, palette, t, 0.25, size));
        }
    }

    /**
     * A slash across the view: {@code kind} arc / drop / line, {@code radius}, {@code angle} (degrees), {@code tilt} (0 = stands up
     * across the view, 90 = flat), {@code roll} (0 = right to left, 180 = left to right), put {@code forward} blocks ahead,
     * {@code drop} below the eyes and {@code side} to the right; {@code scale} the size, {@code duration} seconds, {@code delay}
     * seconds before. Several together are drawn with the usual follow-up turning.
     */
    public void slash(String kind, double radius, double angle, double tilt, double roll, double forward, double drop, double side,
                      double scale, double duration, double delay) {
        ComboStyle.Cut cut = new ComboStyle.Cut(radius, angle, tilt, roll, 0.0, forward, drop, duration, scale, delay, side, kind);
        net.tkgon.mc.iruuRPG.skill.SkillLook look = slashColors == null ? null : new net.tkgon.mc.iruuRPG.skill.SkillLook(slashColors, null, 1.0, 1.0, null);
        effects.playStyledSlash(player, weapon, new ComboStyle.Stage(List.of(cut), "swing", 0), look, 0);
    }

    public void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    /** A sound role ({@link Sfx}): several vanilla sounds layered; {@code pitch} 1.0 is the role's own pitch. */
    public void sound(Location at, Sfx role, float volume, float pitch) {
        remap(role).play(at, volume, pitch);
    }

    /**
     * The sounds of a class are built on one family: the assassin's on the wind (the breeze), the warrior's on struck metal (the mace), the mage's on the allay. A role
     * a combo asks for is turned into the class's own family (a crack or a clang becomes a gust for the assassin, a gust a clang for the warrior).
     */
    private Sfx remap(Sfx role) {
        return switch (attack.classIdOf(player)) {
            case "assassin" -> switch (role) {
                case METAL_HEAVY -> Sfx.WIND_BURST;
                case MAGIC, MAGIC_DARK, METAL, CRACK -> Sfx.WIND;
                default -> role;
            };
            case "warrior" -> switch (role) {
                case WIND_BURST -> Sfx.METAL_HEAVY;
                case WIND, MAGIC, MAGIC_DARK, CRACK -> Sfx.METAL;
                default -> role;
            };
            case "mage" -> switch (role) {
                case WIND, WIND_BURST, METAL, METAL_HEAVY, CRACK -> Sfx.MAGIC;
                default -> role;
            };
            default -> role;
        };
    }

    public void particle(Particle particle, Location at, int count, double spread, double speed) {
        Fx.of(player, at.getWorld(), at).particle(particle, at.getX(), at.getY(), at.getZ(), count, spread, spread, spread, speed);
    }

    /** A bright burst of dust where something struck: a small sphere shell and a ring. */
    public void burst(Location at, double radius) {
        Fx fx = fx(at);
        fx.sphereShell(at, radius * 0.6, 0.0, 22, palette, 1.0);
        fx.flatRing(at.clone().add(0.0, 0.1, 0.0), radius, 24, palette, 0.6, 0.3, 1.0);
    }

    // ---- ground ---------------------------------------------------------------------------------------------------------------

    /** The ground under a point (the height of the first block below within 12 blocks, else the point's own). */
    public Location ground(Location at) {
        RayTraceResult result = at.getWorld().rayTraceBlocks(at.clone().add(0.0, 0.5, 0.0), new Vector(0.0, -1.0, 0.0), 12.0, FluidCollisionMode.NEVER, true);
        return result == null ? at.clone() : new Location(at.getWorld(), at.getX(), result.getHitPosition().getY(), at.getZ());
    }

    // ---- what the zones are ------------------------------------------------------------------------------------------------------

    /** Zones that stand for a time, kept by one task. */
    public static final class Zones {

        /** The longest life of anything a combo puts in the world: 35 s, which a placement of a deployed weapon with a long effect time can ask for (the rest last 3 s or less). */
        public static final int MAX_TICKS = 700;

        public static final class Zone {
            public final Player owner;
            public final String key;
            public final Location center;
            public final int life;
            private final int period;
            private final Consumer<Zone> tick;
            private final Consumer<Zone> end;
            private int age;

            private Zone(Player owner, String key, Location center, int life, int period, Consumer<Zone> tick, Consumer<Zone> end) {
                this.owner = owner;
                this.key = key;
                this.center = center;
                this.life = life;
                this.period = period;
                this.tick = tick;
                this.end = end;
            }

            public int age() {
                return age;
            }

            /** Ends it at the next tick (it is over: the {@code end} callback runs). */
            public void stop() {
                age = life;
            }

            /** How far through its life it is (0 - 1). */
            public double progress() {
                return Math.min(1.0, age / (double) life);
            }
        }

        private final JavaPlugin plugin;
        private final Map<String, Zone> active = new HashMap<>();
        private SafeTask task;

        public Zones(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        private final Map<String, Object[]> deployHits = new HashMap<>();
        private final Map<String, Integer> relays = new HashMap<>();

        /** The zones of {@code owner} whose key starts with {@code prefix} and whose centre is within {@code radius} of {@code at}. */
        List<Zone> near(Player owner, String prefix, Location at, double radius) {
            List<Zone> result = new ArrayList<>();
            for (Zone zone : active.values()) {
                if (!zone.owner.equals(owner) || !zone.key.startsWith(prefix) || zone.center.getWorld() != at.getWorld()) continue;
                if (zone.center.distanceSquared(at) <= radius * radius) result.add(zone);
            }
            return result;
        }

        /** Whether the zone {@code key} of the owner may relay a shot now (each can once in 8 ticks). */
        boolean relayReady(Player owner, String key) {
            int now = org.bukkit.Bukkit.getCurrentTick();
            String id = owner.getUniqueId() + "|" + key;
            Integer last = relays.get(id);
            if (last != null && now - last < 8) return false;
            relays.put(id, now);
            if (relays.size() > 200) relays.values().removeIf(tick -> now - tick > 60);
            return true;
        }

        /**
         * Whether an enemy may take a hit from the placed thing {@code source} now: not when a different placed thing of the same player hit it within the last 8 ticks
         * (so lanterns that overlap hit as one), while one placed thing's own hits are never held back.
         */
        boolean allowDeployHit(Player owner, LivingEntity target, String source) {
            int now = org.bukkit.Bukkit.getCurrentTick();
            String id = owner.getUniqueId() + "|" + target.getUniqueId();
            Object[] last = deployHits.get(id);
            if (last != null && !source.equals(last[0]) && now - (Integer) last[1] < 8) return false;
            deployHits.put(id, new Object[]{source, now});
            if (deployHits.size() > 600) deployHits.values().removeIf(entry -> now - (Integer) entry[1] > 40);
            return true;
        }

        void add(Player owner, String key, Location center, int lifeTicks, int period, Consumer<Zone> tick, Consumer<Zone> end) {
            String id = owner.getUniqueId() + "|" + key;
            Zone old = active.remove(id);
            if (old != null && old.end != null) old.end.accept(old);
            active.put(id, new Zone(owner, key, center.clone(), Math.max(2, Math.min(MAX_TICKS, lifeTicks)), Math.max(1, period), tick, end));
            if (task == null) {
                task = new SafeTask(plugin) {
                    @Override
                    protected void tick() {
                        step();
                    }
                };
                task.runTaskTimer(plugin, 1L, 1L);
            }
        }

        private void step() {
            for (Iterator<Map.Entry<String, Zone>> it = active.entrySet().iterator(); it.hasNext(); ) {
                Zone zone = it.next().getValue();
                if (!zone.owner.isOnline() || zone.owner.isDead() || zone.age >= zone.life) {
                    it.remove();
                    if (zone.end != null) zone.end.accept(zone);
                    continue;
                }
                ParticleFx.mark(zone.owner);
                if (zone.age % zone.period == 0 && zone.tick != null) zone.tick.accept(zone);
                zone.age++;
            }
            if (active.isEmpty() && task != null) {
                task.cancel();
                task = null;
            }
        }

        /** Ends everything of a player (they left, or died). */
        public void clear(UUID id) {
            active.entrySet().removeIf(entry -> entry.getKey().startsWith(id + "|"));
        }

        public void shutdown() {
            if (task != null) task.cancel();
            task = null;
            active.clear();
        }
    }
}
