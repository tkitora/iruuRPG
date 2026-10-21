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

    MoveKit(JavaPlugin plugin, AttackService attack, AttackEffects effects, Zones zones, Player player, RpgItemDefinition weapon) {
        this.plugin = plugin;
        this.attack = attack;
        this.effects = effects;
        this.zones = zones;
        this.player = player;
        this.weapon = weapon;
        this.palette = Palettes.of(weapon.element());
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
        return attack.strike(player, target, weapon, rate);
    }

    /** One hit on each; how many were hit. */
    public int hitAll(List<LivingEntity> targets, double rate) {
        int count = 0;
        for (LivingEntity target : targets) if (hit(target, rate)) count++;
        return count;
    }

    public void push(LivingEntity target, Vector from, double power, double lift) {
        Vector away = target.getLocation().toVector().subtract(from).setY(0.0);
        if (away.lengthSquared() < 1.0E-6) away = facing();
        target.setVelocity(away.normalize().multiply(power).setY(lift));
    }

    public void pull(LivingEntity target, Location to, double power) {
        Vector toward = to.toVector().subtract(target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector());
        if (toward.lengthSquared() < 0.04) return;
        target.setVelocity(toward.normalize().multiply(power).setY(0.1));
    }

    public void lift(LivingEntity target, double power) {
        target.setVelocity(new Vector(target.getVelocity().getX() * 0.3, power, target.getVelocity().getZ() * 0.3));
    }

    public void slow(LivingEntity target, double percent, double seconds) {
        attack.slow(target, percent, Math.round(seconds * 1000.0), player.getUniqueId());
    }

    // ---- hands, guard and blinks ---------------------------------------------------------------------------------------------------

    /** The hand the next cut is struck with: an assassin with a weapon in each hand (固有0) strikes with them in turn; anybody else with the main hand. */
    public AttackService.MeleeHand nextHand() {
        return attack.nextSlashHand(player, weapon);
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

    private static boolean standable(Location at) {
        return at.getBlock().isPassable() && at.clone().add(0.0, 1.0, 0.0).getBlock().isPassable();
    }

    /** A place {@code distance} blocks behind {@code target} (the other side from where it faces) where the player can stand; the target's own spot when there is none. */
    public Location behind(LivingEntity target, double distance) {
        Vector away = target.getLocation().getDirection().setY(0.0);
        if (away.lengthSquared() < 1.0E-6) away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
        if (away.lengthSquared() < 1.0E-6) away = facing();
        away.normalize();
        for (double d = distance; d >= 0.8; d -= 0.5) {
            Location spot = target.getLocation().clone().subtract(away.clone().multiply(d));
            if (standable(spot)) return spot;
        }
        return target.getLocation().clone();
    }

    /** Moves the player to {@code to} at once, facing {@code face} (when not null), with a streak of the palette between the two places and a puff at both. */
    public void blink(Location to, LivingEntity face) {
        Location from = player.getLocation();
        Location dest = to.clone();
        if (face != null) {
            Vector look = face.getLocation().add(0.0, face.getHeight() * 0.5, 0.0).toVector().subtract(dest.clone().add(0.0, 1.6, 0.0).toVector());
            if (look.lengthSquared() > 1.0E-6) dest.setDirection(look);
        } else {
            dest.setYaw(from.getYaw());
            dest.setPitch(from.getPitch());
        }
        line(from.clone().add(0.0, 1.0, 0.0), dest.clone().add(0.0, 1.0, 0.0), 0.2, 1.0, 1.3);
        burst(from.clone().add(0.0, 1.0, 0.0), 0.9);
        player.setFallDistance(0.0f);
        player.teleport(dest);
        burst(dest.clone().add(0.0, 1.0, 0.0), 0.9);
        Sfx.MAGIC_DARK.play(dest, 0.5f, 1.8f);
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
        zones.add(player, key, center, lifeTicks, period, tick, end);
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

    /** A column of light standing at {@code base}: rings along it, widening toward the bottom, the color bright at the top. */
    public void pillar(Location base, double radius, double height) {
        Fx fx = fx(base);
        for (double y = 0.0; y <= height; y += 0.5) {
            double r = radius * (1.0 - 0.5 * y / height);
            fx.flatRing(base.clone().add(0.0, y, 0.0), r, 14, palette, 0.3 + 0.7 * y / height, 0.3, 1.1);
        }
    }

    /** Heals the player by {@code percent} of their maximum HP (a heal counts for the cleric's pulse like any other). */
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
        effects.playStyledSlash(player, weapon, new ComboStyle.Stage(List.of(cut), "swing", 0), null, 0);
    }

    public void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    /** A sound role ({@link Sfx}): several vanilla sounds layered; {@code pitch} 1.0 is the role's own pitch. */
    public void sound(Location at, Sfx role, float volume, float pitch) {
        role.play(at, volume, pitch);
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

        /** The longest life of anything a combo puts in the world (3 seconds: less than a round of a combo). */
        public static final int MAX_TICKS = 60;

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
