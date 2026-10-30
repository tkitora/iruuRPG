package net.tkgon.mc.iruuRPG.skill;

import net.tkgon.mc.iruuRPG.combat.AttackEffects;
import net.tkgon.mc.iruuRPG.combat.StatusEffectService;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Plays a skill: the warning, the hit and what it does to whoever is caught, for every template. The caster comes in
 * through {@link SkillCaster}; damage goes back out through it ({@code caster.damage}), so the usual damage rules
 * apply and nothing here invents a new way to hurt.
 */
public final class SkillRunner {

    private final JavaPlugin plugin;
    private final AttackEffects effects;
    private final StatusEffectService statusEffects;

    public SkillRunner(JavaPlugin plugin, AttackEffects effects, StatusEffectService statusEffects) {
        this.plugin = plugin;
        this.effects = effects;
        this.statusEffects = statusEffects;
    }

    /**
     * Starts a skill. {@code aim} gives where the caster is aiming at the moment it is asked (a player's chest, or a
     * fixed point). {@code onDone} runs when the caster is free again.
     */
    public void start(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        startShaped(caster, spec, aim, onDone, null);
    }

    /**
     * Starts a skill; {@code groupHits} (a combo's counter, or null) counts the hits it lands. With
     * {@code lock-aim} the skill keeps the direction it was started with, with {@code flat-aim} it ignores looking
     * up or down (both by playing it through a {@link ShapedCaster}).
     */
    private void startShaped(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone,
                             java.util.concurrent.atomic.AtomicInteger groupHits) {
        if (!caster.valid()) {
            onDone.run();
            return;
        }
        if (spec.extras().aimChanged() || groupHits != null) {
            ShapedCaster shaped = new ShapedCaster(caster, spec.extras().lockAim(), spec.extras().flatAim(), groupHits, aim);
            caster = shaped;
            aim = shaped.aim();
        }
        if (spec.template() == SkillTemplate.COMBO) {
            combo(caster, spec, aim, onDone);
            return;
        }
        caster.freeze();
        Location aimAt = aim.get();
        if (aimAt != null) caster.face(aimAt);
        effects.playSkillWindupSound(caster.owner(), caster.location(), spec.look(), caster.element());
        switch (spec.template()) {
            case STRIKE -> strike(caster, spec, aim, onDone);
            case SWEEP -> sweep(caster, spec, aim, onDone);
            case BARRAGE -> barrage(caster, spec, aim, onDone);
            case DASH -> dash(caster, spec, aim, onDone);
            case WAVE -> wave(caster, spec, aim, onDone);
            case NOVA -> nova(caster, spec, aim, onDone);
            case BEAM -> beam(caster, spec, aim, onDone);
            case TELEPORT -> teleport(caster, spec, aim, onDone);
            case VOLLEY -> volley(caster, spec, aim, onDone);
            case DAMAGE -> damage(caster, spec, aim, onDone);
            case COMBO -> onDone.run();
        }
    }

    /**
     * A repeating task that stops itself when it throws (a bad value from a skill file, a bug): it logs once, cancels,
     * and runs {@code cleanup} (give the caster its movement back, tell the owner the skill is over), so an enemy's AI
     * can never stay switched off.
     */
    private abstract class Guarded extends BukkitRunnable {
        private final Runnable cleanup;

        private Guarded(Runnable cleanup) {
            this.cleanup = cleanup;
        }

        protected abstract void tick();

        @Override
        public final void run() {
            try {
                tick();
            } catch (RuntimeException exception) {
                cancel();
                plugin.getLogger().warning("skill: stopped after an error: " + exception);
                if (cleanup != null) {
                    try {
                        cleanup.run();
                    } catch (RuntimeException ignored) {
                        // the caster is gone or broken: nothing more to give back
                    }
                }
            }
        }
    }

    // ---- combo --------------------------------------------------------------------------------------------------------

    /**
     * Runs the steps one after another. A normal step starts when every step started before it is over (plus its
     * delay); a step with {@code with-previous} starts its delay after the step before it started. When the caster
     * is gone, no further step is started. {@code onDone} runs once, when nothing is running or waiting any more.
     */
    private void combo(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        if (spec.steps().isEmpty()) {
            onDone.run();
            return;
        }
        new Combo(caster, spec, aim, onDone).begin();
    }

    private final class Combo {
        private final SkillCaster caster;
        private final SkillSpec spec;
        private final Supplier<Location> aim;
        private final Runnable onDone;
        /** Steps running or waiting for their start. */
        private int pending;
        /** Index of the next step that has not been scheduled yet. */
        private int next;
        /** Hits landed by the steps of the group that is running / just ended (for the steps that need a hit). */
        private final java.util.concurrent.atomic.AtomicInteger groupHits = new java.util.concurrent.atomic.AtomicInteger();
        private int lastGroupHits;
        private boolean finished;
        private final int startedAt = org.bukkit.Bukkit.getCurrentTick();

        private Combo(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
            this.caster = caster;
            this.spec = spec;
            this.aim = aim;
            this.onDone = onDone;
        }

        private void begin() {
            schedule(0);
        }

        /** Schedules step {@code index}, then the steps after it that start together with it. */
        private void schedule(int index) {
            SkillSpec.ComboStep step = spec.steps().get(index);
            next = index + 1;
            pending++;
            long delay = Math.max(0, step.delayTicks());
            if (delay == 0L) {
                startStep(index);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> startStep(index), delay);
            }
        }

        private void startStep(int index) {
            if (finished) return;
            if (!caster.valid() || org.bukkit.Bukkit.getCurrentTick() - startedAt > SkillSpec.MAX_COMBO_TICKS + 60) {
                // the caster is gone (or the combo ran far too long): nothing more is started
                stepOver(true);
                return;
            }
            SkillSpec.ComboStep comboStep = spec.steps().get(index);
            if (comboStep.ifHit() && lastGroupHits <= 0) {
                // a step that needs a hit: the steps before it hit nothing, so it is skipped
                stepOver(false);
                return;
            }
            // a guard: the combo cannot be judged finished while this step is being started and its partners scheduled
            pending++;
            try {
                startShaped(caster, comboStep.spec(), aim, () -> stepOver(false), groupHits);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("skill: a combo step failed: " + exception);
                stepOver(false);
            }
            // the steps that start together with this one are scheduled from its start
            if (next < spec.steps().size() && spec.steps().get(next).withPrevious()) schedule(next);
            pending--;
            settle();
        }

        private void stepOver(boolean abort) {
            if (finished) return;
            pending--;
            if (abort) {
                // stop: later steps are not started, the ones already running finish on their own
                next = spec.steps().size();
            }
            settle();
        }

        /** Nothing running or waiting: start the next normal step, or end the combo. */
        private void settle() {
            if (finished || pending > 0) return;
            lastGroupHits = groupHits.getAndSet(0);
            if (next < spec.steps().size() && caster.valid()) {
                schedule(next);
            } else {
                finished = true;
                caster.release();
                onDone.run();
            }
        }
    }

    // ---- strike -------------------------------------------------------------------------------------------------------

    private void strike(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location fixed = originPoint(caster, spec, aim);
        Supplier<Location> where = fixed == null ? caster::location : () -> fixed.clone();
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                if (age >= spec.windupTicks()) {
                    Location center = where.get();
                    effects.playSkillStrike(center, spec.radius(), spec.look(), element);
                    effects.playSkillCastSound(caster.owner(), center, spec.look(), element);
                    for (LivingEntity player : within(center, caster, spec, spec.radius(), 2.5)) hit(caster, spec, player, center);
                    caster.release();
                    cancel();
                    onDone.run();
                    return;
                }
                if (age % 4 == 0) effects.playSkillTelegraphRing(where.get(), spec.radius(), spec.look(), element);
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- sweep --------------------------------------------------------------------------------------------------------

    private void sweep(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location fixed = originPoint(caster, spec, aim);
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                if (age >= spec.windupTicks()) {
                    Location origin = fixed == null ? caster.location() : fixed.clone();
                    origin.setYaw(caster.location().getYaw());
                    effects.playSkillCrescent(origin, origin.getYaw(), spec.range(), spec.angle(), spec.orientation(), spec.extras().reverse(), spec.look(), element);
                    effects.playSkillCastSound(caster.owner(), origin, spec.look(), element);
                    double tall = spec.orientation() == SkillSpec.Orientation.HORIZONTAL ? 2.5 : 3.5;
                    for (LivingEntity player : within(origin, caster, spec, spec.range(), tall)) {
                        if (inFan(origin, player, spec.angle() / 2.0)) hit(caster, spec, player, origin);
                    }
                    caster.release();
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (at != null) caster.face(at);
                if (age % 4 == 0) {
                    Location mark = fixed == null ? caster.location() : fixed.clone();
                    effects.playSkillTelegraphArc(mark, caster.location().getYaw(), spec.range(), spec.angle(), spec.look(), element);
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- barrage ------------------------------------------------------------------------------------------------------

    private void barrage(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Location fixed = originPoint(caster, spec, aim);
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;
            private int fired;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (at != null) caster.face(at);
                if (fired >= spec.count() || at == null) {
                    caster.release();
                    cancel();
                    onDone.run();
                    return;
                }
                if (age >= spec.windupTicks() && (age - spec.windupTicks()) % spec.intervalTicks() == 0) {
                    // the bolts fan out over `spread` degrees across the whole series
                    double fan = spec.count() > 1 ? spec.extras().spread() * ((fired + 0.5) / spec.count() - 0.5) : 0.0;
                    fireBolt(caster, spec, at, fixed, fan);
                    fired++;
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireBolt(SkillCaster caster, SkillSpec spec, Location at, Location fixed, double extraYaw) {
        Element element = caster.element();
        Location from = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0)
                : caster.eye().add(caster.location().getDirection().multiply(0.4)).add(0.0, -0.2, 0.0);
        Vector direction = at.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        direction = offsetAim(direction.normalize(), spec, extraYaw);
        final Vector flight = direction;
        caster.swing();
        effects.playSkillCastSound(caster.owner(), from, spec.look(), element);
        World world = caster.world();
        double perTick = spec.speed() / 20.0;
        double maxDistance = spec.range() * 1.3;
        new Guarded(null) {
            private Location position = from.clone();
            private double travelled;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    return;
                }
                Location next = position.clone().add(flight.clone().multiply(perTick));
                RayTraceResult wall = world.rayTraceBlocks(position, flight, perTick, FluidCollisionMode.NEVER, true);
                double wallDistance = wall == null ? Double.MAX_VALUE : wall.getHitPosition().distance(position.toVector());
                LivingEntity hitPlayer = null;
                double hitDistance = Double.MAX_VALUE;
                for (LivingEntity player : targets(caster, spec)) {
                    RayTraceResult result = player.getBoundingBox().expand(0.25).rayTrace(position.toVector(), flight, perTick);
                    if (result == null) continue;
                    double distance = result.getHitPosition().distance(position.toVector());
                    if (distance < hitDistance) {
                        hitDistance = distance;
                        hitPlayer = player;
                    }
                }
                if (hitPlayer != null && hitDistance <= wallDistance) {
                    Location end = position.clone().add(flight.clone().multiply(hitDistance));
                    effects.playSkillBolt(position, end, spec.look(), element);
                    effects.playSkillImpact(end, spec.look(), element);
                    effects.playSkillHitSound(caster.owner(), end, spec.look(), element);
                    hit(caster, spec, hitPlayer, fixed);
                    cancel();
                    return;
                }
                if (wall != null) {
                    Location end = wall.getHitPosition().toLocation(world);
                    effects.playSkillBolt(position, end, spec.look(), element);
                    effects.playSkillImpact(end, spec.look(), element);
                    cancel();
                    return;
                }
                effects.playSkillBolt(position, next, spec.look(), element);
                position = next;
                travelled += perTick;
                if (travelled >= maxDistance) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- dash ---------------------------------------------------------------------------------------------------------

    private void dash(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location start = caster.location().clone();
        Location at = aim.get();
        // (a flyer dives at the aim, or climbs to it; one on the ground keeps level and hops)
        boolean airborne = caster.airborne();
        Vector direction = at == null ? null : at.toVector().subtract(start.toVector());
        if (direction != null && !airborne) direction.setY(0.0);
        if (direction == null || direction.lengthSquared() < 1.0E-6) {
            caster.release();
            onDone.run();
            return;
        }
        direction.normalize();
        double length = Math.min(spec.range(), start.distance(at) + 2.0);
        Location end = start.clone().add(direction.clone().multiply(length));
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;
            private boolean lunged;
            private final Set<UUID> hit = new HashSet<>();

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                if (age < spec.windupTicks()) {
                    if (age % 4 == 0) effects.playSkillTelegraphLine(start, end, spec.look(), element);
                    age++;
                    return;
                }
                if (!lunged) {
                    lunged = true;
                    caster.release(); // the lunge is a push: it needs the body to be free
                    effects.playSkillCastSound(caster.owner(), caster.location(), spec.look(), element);
                    Vector push = direction.clone().multiply(spec.speed() / 8.0);
                    caster.lunge(airborne ? push : push.setY(0.25));
                }
                for (LivingEntity player : within(caster.location(), caster, spec, 1.8, 2.5)) {
                    if (hit.add(player.getUniqueId())) hit(caster, spec, player, null);
                }
                if (age >= spec.windupTicks() + 8) {
                    cancel();
                    onDone.run();
                    return;
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- teleport -----------------------------------------------------------------------------------------------------

    /**
     * The caster blinks after the warning: {@code forward} = along where they face as far as the range (a player
     * always the full range, an enemy up to just before its target), stopping before a wall; {@code behind} = behind
     * the enemy aimed at. It lands on a safe spot (room for the body, ground below); with none, nothing happens. If
     * the skill has damage or statuses, a burst of {@code radius} follows on arrival.
     */
    private void teleport(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (age < spec.windupTicks()) {
                    if (at != null) caster.face(at);
                    if (age % 4 == 0) {
                        if (spec.teleportMode() == SkillSpec.TeleportMode.BEHIND) {
                            LivingEntity target = behindTarget(caster, spec, at);
                            if (target != null) effects.playSkillTelegraphRing(target.getLocation(), 1.6, spec.look(), element);
                        } else {
                            Location from = caster.location().add(0.0, 0.2, 0.0);
                            Location to = forwardSpot(caster, spec, at);
                            if (to != null) effects.playSkillTelegraphLine(from, to.add(0.0, 0.2, 0.0), spec.look(), element);
                        }
                    }
                    age++;
                    return;
                }
                cancel();
                LivingEntity behind = spec.teleportMode() == SkillSpec.TeleportMode.BEHIND ? behindTarget(caster, spec, at) : null;
                Location destination = spec.teleportMode() == SkillSpec.TeleportMode.BEHIND
                        ? (behind == null ? null : behindSpot(behind))
                        : forwardSpot(caster, spec, at);
                if (destination != null) {
                    Location from = caster.location().clone();
                    effects.playSkillImpact(from.clone().add(0.0, 1.0, 0.0), spec.look(), element);
                    effects.playSkillBolt(from.clone().add(0.0, 1.0, 0.0), destination.clone().add(0.0, 1.0, 0.0), spec.look(), element);
                    caster.teleport(destination);
                    effects.playSkillCastSound(caster.owner(), destination, spec.look(), element);
                    effects.playSkillImpact(destination.clone().add(0.0, 1.0, 0.0), spec.look(), element);
                    if (spec.damageRate() > 0.0 || !spec.statuses().isEmpty()) {
                        effects.playSkillStrike(destination, spec.radius(), spec.look(), element);
                        for (LivingEntity victim : within(destination, caster, spec, spec.radius(), 2.5)) hit(caster, spec, victim, destination);
                    }
                }
                caster.release();
                onDone.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The spot a forward teleport lands on now, or null when it cannot go anywhere. */
    private Location forwardSpot(SkillCaster caster, SkillSpec spec, Location at) {
        Location start = caster.location();
        World world = start.getWorld();
        if (world == null) return null;
        Vector direction = at == null ? start.getDirection() : at.toVector().subtract(start.toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) direction = start.getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) return null;
        direction.normalize();

        double length = spec.range();
        if (caster.owner() == null && at != null) {
            double flat = Math.hypot(at.getX() - start.getX(), at.getZ() - start.getZ());
            length = Math.min(length, Math.max(0.0, flat - 1.5));
        }
        RayTraceResult wall = world.rayTraceBlocks(start.clone().add(0.0, 1.0, 0.0), direction, length, FluidCollisionMode.NEVER, true);
        if (wall != null) length = Math.max(0.0, wall.getHitPosition().distance(start.toVector().add(new Vector(0.0, 1.0, 0.0))) - 0.6);
        for (double distance = length; distance >= 0.5; distance -= 0.5) {
            Location spot = safeSpot(world, start.getX() + direction.getX() * distance, start.getY(), start.getZ() + direction.getZ() * distance,
                    start.getYaw(), start.getPitch());
            if (spot != null) return spot;
        }
        return null;
    }

    /** The enemy a "behind" teleport goes to: the one closest to the aimed point (within 3 blocks of it, and within the range). */
    private LivingEntity behindTarget(SkillCaster caster, SkillSpec spec, Location at) {
        if (at == null) return null;
        Location start = caster.location();
        LivingEntity best = null;
        double bestDistance = 3.0 * 3.0;
        for (LivingEntity candidate : targets(caster, spec)) {
            if (candidate.equals(caster.owner()) || !candidate.getWorld().equals(start.getWorld())) continue;
            if (candidate.getLocation().distanceSquared(start) > (spec.range() + 1.0) * (spec.range() + 1.0)) continue;
            double distance = candidate.getLocation().add(0.0, candidate.getHeight() * 0.5, 0.0).distanceSquared(at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** Behind the target (opposite to where it looks), else beside it; the caster looks at it. Null when no spot is free. */
    private Location behindSpot(LivingEntity target) {
        Location base = target.getLocation();
        World world = base.getWorld();
        if (world == null) return null;
        Vector back = base.getDirection().setY(0.0);
        if (back.lengthSquared() < 1.0E-6) back = new Vector(0.0, 0.0, 1.0);
        back.normalize().multiply(-1.0);
        for (double degrees : new double[]{0, 45, -45, 90, -90}) {
            double radians = Math.toRadians(degrees);
            double x = base.getX() + (back.getX() * Math.cos(radians) - back.getZ() * Math.sin(radians)) * 1.6;
            double z = base.getZ() + (back.getX() * Math.sin(radians) + back.getZ() * Math.cos(radians)) * 1.6;
            Location spot = safeSpot(world, x, base.getY(), z, 0.0f, 0.0f);
            if (spot == null) continue;
            spot.setDirection(base.toVector().subtract(spot.toVector()).setY(0.0));
            return spot;
        }
        return null;
    }

    /** A spot to land on near (x, y, z): free for the body (feet and head, no liquid) with solid ground below; null if none (within 2 up / 2 down). */
    private Location safeSpot(World world, double x, double y, double z, float yaw, float pitch) {
        double baseY = Math.floor(y);
        for (int offset : new int[]{0, 1, -1, 2, -2}) {
            Location spot = new Location(world, x, baseY + offset, z, yaw, pitch);
            Block feet = spot.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            Block below = feet.getRelative(0, -1, 0);
            if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid() && below.getType().isSolid()) return spot;
        }
        return null;
    }

    // ---- wave ---------------------------------------------------------------------------------------------------------

    private void wave(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location fixed = originPoint(caster, spec, aim);
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (age < spec.windupTicks()) {
                    // keeps facing the target and marks where the slash will go
                    if (at != null) caster.face(at);
                    if (age % 4 == 0) {
                        Location from = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0) : caster.location().add(0.0, 1.0, 0.0);
                        Vector ahead = caster.location().getDirection().setY(0.0);
                        if (ahead.lengthSquared() > 1.0E-6) {
                            effects.playSkillTelegraphLine(from, from.clone().add(ahead.normalize().multiply(Math.min(spec.range(), 10.0))), spec.look(), element);
                        }
                    }
                    age++;
                    return;
                }
                cancel();
                if (at != null) launchWaves(caster, spec, at, fixed);
                caster.release(); // the caster is free again while the slashes fly
                onDone.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void launchWaves(SkillCaster caster, SkillSpec spec, Location at, Location fixed) {
        Element element = caster.element();
        Location origin = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0) : caster.eye().add(0.0, -0.3, 0.0);
        Vector aimVector = at.toVector().subtract(origin.toVector());
        if (aimVector.lengthSquared() < 1.0E-6) return;
        aimVector = offsetAim(aimVector.normalize(), spec, 0.0);
        caster.swing();
        effects.playSkillCastSound(caster.owner(), caster.location(), spec.look(), element);
        for (int index = 0; index < spec.count(); index++) {
            double offset = Math.toRadians((index - (spec.count() - 1) / 2.0) * spec.angle());
            double cos = Math.cos(offset);
            double sin = Math.sin(offset);
            Vector direction = new Vector(aimVector.getX() * cos - aimVector.getZ() * sin, aimVector.getY(), aimVector.getX() * sin + aimVector.getZ() * cos).normalize();
            flyWave(caster, spec, origin.clone(), direction, fixed);
        }
    }

    private void flyWave(SkillCaster caster, SkillSpec spec, Location origin, Vector direction, Location fixed) {
        Element element = caster.element();
        World world = origin.getWorld();
        new Guarded(null) {
            private final Location position = origin;
            private double travelled;
            private final Set<UUID> hit = new HashSet<>();

            @Override
            protected void tick() {
                double step = spec.speed() / 20.0;
                RayTraceResult wall = world.rayTraceBlocks(position, direction, step, FluidCollisionMode.NEVER, true);
                if (wall != null) {
                    effects.playSkillImpact(wall.getHitPosition().toLocation(world), spec.look(), element);
                    cancel();
                    return;
                }
                position.add(direction.clone().multiply(step));
                travelled += step;
                effects.playSkillWaveStep(position, direction, spec.radius(), spec.look(), element);
                for (LivingEntity player : targets(caster, spec)) {
                    if (hit.contains(player.getUniqueId())) continue;
                    if (!player.getBoundingBox().expand(spec.radius() * 0.6).contains(position.toVector())) continue;
                    hit.add(player.getUniqueId());
                    effects.playSkillImpact(position, spec.look(), element);
                    effects.playSkillHitSound(caster.owner(), position, spec.look(), element);
                    hit(caster, spec, player, fixed);
                }
                if (travelled >= spec.range()) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- nova ---------------------------------------------------------------------------------------------------------

    private void nova(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location fixed = originPoint(caster, spec, aim);
        Supplier<Location> where = fixed == null ? caster::location : () -> fixed.clone();
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                if (age >= spec.windupTicks()) {
                    cancel();
                    caster.release(); // the ring spreads on its own
                    onDone.run();
                    expand(caster, spec, where.get());
                    return;
                }
                if (age % 4 == 0) effects.playSkillTelegraphRing(where.get(), spec.radius(), spec.look(), element);
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        // (the caster stays where it started: the ring is centered there)
    }

    private void expand(SkillCaster caster, SkillSpec spec, Location center) {
        Element element = caster.element();
        effects.playSkillCastSound(caster.owner(), center, spec.look(), element);
        double step = spec.speed() / 20.0;
        new Guarded(null) {
            private double radius = 0.5;
            private final Set<UUID> hit = new HashSet<>();

            @Override
            protected void tick() {
                effects.playSkillNovaRing(center, radius, spec.look(), element);
                for (LivingEntity player : targets(caster, spec)) {
                    if (hit.contains(player.getUniqueId())) continue;
                    if (!player.getWorld().equals(center.getWorld())) continue;
                    Location at = player.getLocation();
                    double dx = at.getX() - center.getX();
                    double dz = at.getZ() - center.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    // (a low ring can be jumped over: hit-height is how far the feet may be above or below it)
                    if (Math.abs(at.getY() - center.getY()) > spec.extras().hitHeight()) continue;
                    if (distance > radius + 0.6 || distance < radius - step - 0.6) continue;
                    hit.add(player.getUniqueId());
                    effects.playSkillHitSound(caster.owner(), at.clone().add(0.0, 1.0, 0.0), spec.look(), element);
                    hit(caster, spec, player, center);
                }
                if (radius >= spec.radius()) {
                    cancel();
                    return;
                }
                radius = Math.min(spec.radius(), radius + step);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- beam ---------------------------------------------------------------------------------------------------------

    private void beam(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location fixed = originPoint(caster, spec, aim);
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;
            private Location from;
            private Location to;
            private int shown;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (age < spec.windupTicks()) {
                    if (at != null) caster.face(at);
                    if (age % 4 == 0 && at != null) {
                        Location start = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0) : caster.eye().add(0.0, -0.3, 0.0);
                        Vector ahead = at.toVector().subtract(start.toVector());
                        if (ahead.lengthSquared() > 1.0E-6) {
                            effects.playSkillTelegraphLine(start, start.clone().add(ahead.normalize().multiply(Math.min(spec.range(), 20.0))), spec.look(), element);
                        }
                    }
                    age++;
                    return;
                }
                if (from == null) {
                    // fired: the line is fixed now
                    from = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0) : caster.eye().add(0.0, -0.3, 0.0);
                    Vector direction = at == null ? caster.location().getDirection() : at.toVector().subtract(from.toVector());
                    if (direction.lengthSquared() < 1.0E-6) {
                        caster.release();
                        cancel();
                        onDone.run();
                        return;
                    }
                    direction = offsetAim(direction.normalize(), spec, 0.0);
                    World world = caster.world();
                    RayTraceResult wall = world.rayTraceBlocks(from, direction, spec.range(), FluidCollisionMode.NEVER, true);
                    double length = wall == null ? spec.range() : wall.getHitPosition().distance(from.toVector());
                    to = from.clone().add(direction.clone().multiply(length));
                    caster.swing();
                    effects.playSkillCastSound(caster.owner(), from, spec.look(), element);
                    for (LivingEntity player : targets(caster, spec)) {
                        BoundingBox box = player.getBoundingBox().expand(spec.radius());
                        RayTraceResult result = box.rayTrace(from.toVector(), direction, length);
                        if (result != null) hit(caster, spec, player, fixed);
                    }
                }
                effects.playSkillBeam(from, to, spec.radius(), spec.look(), element);
                if (++shown >= 4) {
                    caster.release();
                    cancel();
                    onDone.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Projectile skills (barrage, wave, beam) with an origin leave from this high above the origin point (chest height). */
    private static final double PROJECTILE_HEIGHT = 1.2;

    /**
     * The point a skill with an {@code origin} starts from, fixed at this moment: the caster's place (or the aimed
     * target's), moved forward along where the caster faces, to the right, and up. A point inside a block moves up
     * to find free space (at most 3 blocks), else it falls back to the caster's place. Null without an origin.
     */
    private Location originPoint(SkillCaster caster, SkillSpec spec, Supplier<Location> aim) {
        SkillSpec.Origin origin = spec.origin();
        if (origin == null || spec.template() == SkillTemplate.DASH) return null;
        Location base;
        if (origin.anchor() == SkillSpec.Anchor.TARGET) {
            Location at = aim.get();
            base = at == null ? caster.location() : at.clone().subtract(0.0, 1.0, 0.0);
        } else {
            base = caster.location();
        }
        double yaw = Math.toRadians(caster.location().getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Location point = base.clone().add(forward.multiply(origin.forward())).add(right.multiply(origin.side())).add(0.0, origin.up(), 0.0);
        point.setYaw(caster.location().getYaw());
        point.setPitch(0.0f);
        World world = point.getWorld();
        if (world == null) return caster.location();
        point.setY(Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, point.getY())));
        for (int lift = 0; lift <= 3; lift++) {
            Location probe = point.clone().add(0.0, lift, 0.0);
            if (probe.getBlock().isPassable() && probe.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) return probe;
        }
        return caster.location();
    }

    // ---- damage -------------------------------------------------------------------------------------------------------

    /** How far from the line of sight the one aimed at may stand (a damage skill's aimed mode). */
    private static final double AIM_TOLERANCE = 1.5;

    /**
     * Only damage: after the windup (a plain wait, nothing is shown), {@code count} hits every {@code interval}. Aimed:
     * the enemy aimed at and those within the radius of it; around: those within the radius of the caster (or its
     * origin). Only the hit itself is shown on whoever is hit.
     */
    private void damage(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Location fixed = originPoint(caster, spec, aim);
        new Guarded(() -> { caster.release(); onDone.run(); }) {
            private int age;
            private int hits;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                if (age >= spec.windupTicks() && (age - spec.windupTicks()) % Math.max(1, spec.intervalTicks()) == 0) {
                    Location center = fixed == null ? caster.location() : fixed.clone();
                    java.util.List<LivingEntity> struck = new java.util.ArrayList<>();
                    if (spec.extras().damageMode() == SkillSpec.DamageMode.AROUND) {
                        struck.addAll(within(center, caster, spec, spec.radius(), 2.5));
                    } else {
                        LivingEntity aimed = aimedTarget(caster, spec, aim.get());
                        if (aimed != null) {
                            center = aimed.getLocation();
                            struck.add(aimed);
                            if (spec.radius() > 0.0) {
                                for (LivingEntity near : within(center, caster, spec, spec.radius(), 2.5)) {
                                    if (!near.equals(aimed)) struck.add(near);
                                }
                            }
                        }
                    }
                    if (!struck.isEmpty()) effects.playSkillCastSound(caster.owner(), center, spec.look(), caster.element());
                    for (LivingEntity victim : struck) hit(caster, spec, victim, center);
                    hits++;
                    if (hits >= spec.count()) {
                        caster.release();
                        cancel();
                        onDone.run();
                        return;
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * The one a damage skill is aimed at: of those in the range and in front, the one nearest to the line from the
     * caster's eye to the aimed point (at most {@link #AIM_TOLERANCE} from it, plus half its width), not behind a wall.
     */
    private LivingEntity aimedTarget(SkillCaster caster, SkillSpec spec, Location at) {
        Location eye = caster.eye();
        if (at == null || eye.getWorld() == null || !eye.getWorld().equals(at.getWorld())) return null;
        Vector direction = at.toVector().subtract(eye.toVector());
        if (direction.lengthSquared() < 1.0E-6) return null;
        direction.normalize();
        LivingEntity best = null;
        double bestOff = Double.MAX_VALUE;
        for (LivingEntity candidate : targets(caster, spec)) {
            if (candidate.equals(caster.owner()) || !candidate.getWorld().equals(eye.getWorld())) continue;
            Vector toCenter = candidate.getLocation().add(0.0, candidate.getHeight() * 0.5, 0.0).toVector().subtract(eye.toVector());
            double along = toCenter.dot(direction);
            if (along < 0.0 || along > spec.range() + candidate.getWidth() * 0.5) continue;
            double off = toCenter.clone().subtract(direction.clone().multiply(along)).length();
            if (off > AIM_TOLERANCE + candidate.getWidth() * 0.5 || off >= bestOff) continue;
            double distance = toCenter.length();
            if (distance > 1.0E-3) {
                RayTraceResult wall = eye.getWorld().rayTraceBlocks(eye, toCenter.clone().normalize(), distance, FluidCollisionMode.NEVER, true);
                if (wall != null) continue;
            }
            best = candidate;
            bestOff = off;
        }
        return best;
    }

    // ---- shared -------------------------------------------------------------------------------------------------------

    private java.util.List<LivingEntity> within(Location center, SkillCaster caster, SkillSpec spec, double radius, double vertical) {
        java.util.List<LivingEntity> result = new java.util.ArrayList<>();
        for (LivingEntity player : targets(caster, spec)) {
            if (!player.getWorld().equals(center.getWorld())) continue;
            Location at = player.getLocation();
            double dx = at.getX() - center.getX();
            double dz = at.getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            if (Math.abs(at.getY() - center.getY()) > vertical) continue;
            result.add(player);
        }
        return result;
    }

    private boolean inFan(Location origin, LivingEntity player, double halfAngleDegrees) {
        Vector toTarget = player.getLocation().toVector().subtract(origin.toVector()).setY(0.0);
        if (toTarget.lengthSquared() < 1.0E-6) return true;
        Vector facing = origin.getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) return true;
        return Math.toDegrees(facing.normalize().angle(toTarget.normalize())) <= halfAngleDegrees;
    }

    /**
     * Who this skill can touch: the caster's enemies (target enemies / both) and allies (allies / both). The caster
     * itself is only an ally of a skill that is centered on them (strike, nova), not of a beam or a fan they stand behind.
     */
    private java.util.List<LivingEntity> targets(SkillCaster caster, SkillSpec spec) {
        java.util.List<LivingEntity> result = new java.util.ArrayList<>();
        if (spec.target() != SkillSpec.Target.ALLIES) result.addAll(caster.candidates());
        if (spec.target() != SkillSpec.Target.ENEMIES) {
            boolean centered = spec.template() == SkillTemplate.STRIKE || spec.template() == SkillTemplate.NOVA
                    || (spec.template() == SkillTemplate.DAMAGE && spec.extras().damageMode() == SkillSpec.DamageMode.AROUND);
            for (LivingEntity ally : caster.allyCandidates()) {
                if (!centered && ally.equals(caster.owner())) continue;
                result.add(ally);
            }
        }
        return result;
    }

    /**
     * One hit: on an enemy the damage, the push and the harmful effects, on an ally the heal and the helpful effects.
     * {@code originPoint} is the skill's origin point (null: the caster's own place), the center of a push.
     */
    private void hit(SkillCaster caster, SkillSpec spec, LivingEntity victim, Location originPoint) {
        if (caster instanceof ShapedCaster shaped && shaped.hits != null) shaped.hits.incrementAndGet();
        if (caster.isAlly(victim)) {
            helpAlly(caster, spec, victim);
            return;
        }
        caster.damage(victim, spec.damageRate());
        effects.playSkillHitBurst(victim, spec.look(), caster.element());
        if (!caster.applyEffects()) return;
        if (spec.knockback() != null) push(caster, spec.knockback(), victim, originPoint);
        if (statusEffects == null) return;
        for (SkillSpec.StatusApply status : spec.statuses()) {
            switch (status.type()) {
                case "slow" -> statusEffects.applyTimedSlow(victim, status.percent(), Math.round(status.seconds() * 1000.0), null);
                case "bleed" -> statusEffects.addBleed(victim, status.value(), null);
                case "corrosion" -> statusEffects.addCorrosion(victim, status.value(), null);
                case "decay" -> statusEffects.addDecay(victim, status.value(), null);
                case "confusion" -> statusEffects.addConfusion(victim, status.value(), null);
                case "explosion" -> statusEffects.addExplosion(victim, status.value(), null);
                default -> {
                }
            }
        }
    }

    /** Pushes an enemy the way the skill says: away from / toward the center, straight up, or along the caster's facing. */
    private void push(SkillCaster caster, SkillSpec.Knockback knockback, LivingEntity victim, Location originPoint) {
        Location center = knockback.center() == SkillSpec.KnockbackCenter.CASTER || originPoint == null ? caster.location() : originPoint;
        Vector push = new Vector();
        switch (knockback.type()) {
            case BACK -> {
                Vector away = victim.getLocation().toVector().subtract(center.toVector()).setY(0.0);
                if (away.lengthSquared() > 1.0E-6) push = away.normalize().multiply(knockback.power());
                push.setY(knockback.lift());
            }
            case UP -> push.setY(knockback.power() * 0.6);
            case PULL -> {
                Vector toward = center.toVector().subtract(victim.getLocation().toVector()).setY(0.0);
                double distance = toward.length();
                if (distance > 0.5) push = toward.normalize().multiply(Math.min(knockback.power(), distance * 0.5));
                push.setY(knockback.lift());
            }
            case VECTOR -> {
                double yaw = Math.toRadians(caster.location().getYaw());
                Vector forward = new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
                Vector right = new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
                push = forward.multiply(knockback.forward()).add(right.multiply(knockback.side())).setY(knockback.up());
            }
        }
        if (push.lengthSquared() > 1.0E-9) victim.setVelocity(victim.getVelocity().add(push));
    }

    /** An ally in the skill: healed by heal-rate of their max HP, and given the helpful statuses (not the harmful ones). */
    private void helpAlly(SkillCaster caster, SkillSpec spec, LivingEntity ally) {
        effects.playSkillImpact(ally.getLocation().add(0.0, ally.getHeight() * 0.6, 0.0), spec.look(), caster.element());
        if (!caster.applyEffects()) {
            caster.damage(ally, 0.0); // a test counts the touch
            return;
        }
        if (spec.healRate() > 0.0) caster.heal(ally, spec.healRate());
        if (statusEffects == null) return;
        for (SkillSpec.StatusApply status : spec.statuses()) {
            switch (status.type()) {
                case "magic_amplify" -> statusEffects.applyBuff(ally, net.tkgon.mc.iruuRPG.combat.StatusEffectType.MAGIC_AMPLIFY,
                        status.percent(), Math.round(status.seconds() * 1000.0), caster.owner() == null ? null : caster.owner().getUniqueId());
                case "protection" -> statusEffects.applyBuff(ally, net.tkgon.mc.iruuRPG.combat.StatusEffectType.PROTECTION,
                        status.percent(), Math.round(status.seconds() * 1000.0), caster.owner() == null ? null : caster.owner().getUniqueId());
                default -> {
                }
            }
        }
    }

    // ---- aim shaping ---------------------------------------------------------------------------------------------------

    /**
     * Turns an aim direction by the skill's aim-yaw (+ {@code extraYawDegrees}, positive = to the right) and aim-pitch
     * (positive = up).
     */
    private Vector offsetAim(Vector direction, SkillSpec spec, double extraYawDegrees) {
        double yaw = spec.extras().aimYaw() + extraYawDegrees;
        double pitch = spec.extras().aimPitch();
        if (Math.abs(yaw) < 1.0E-6 && Math.abs(pitch) < 1.0E-6) return direction;
        Location frame = new Location(null, 0.0, 0.0, 0.0);
        frame.setDirection(direction);
        frame.setYaw((float) (frame.getYaw() + yaw));
        frame.setPitch((float) Math.max(-89.0, Math.min(89.0, frame.getPitch() - pitch)));
        return frame.getDirection().normalize();
    }

    /**
     * A caster seen through the skill's aim settings: with {@code lock} its direction stays the one it had when the
     * skill started (it ignores {@code face} and the aim does not follow the target), with {@code flat} it never looks
     * up or down. It also counts the hits the skill lands (for the combo steps that need one).
     */
    private static final class ShapedCaster implements SkillCaster {
        private final SkillCaster inner;
        private final boolean lock;
        private final boolean flat;
        private final java.util.concurrent.atomic.AtomicInteger hits;
        private final Supplier<Location> source;
        private final Location lockedAim;
        private final float lockedYaw;
        private final float lockedPitch;

        private ShapedCaster(SkillCaster inner, boolean lock, boolean flat, java.util.concurrent.atomic.AtomicInteger hits, Supplier<Location> source) {
            this.inner = inner;
            this.lock = lock;
            this.flat = flat;
            this.hits = hits;
            this.source = source;
            Location now = source.get();
            this.lockedAim = lock && now != null ? flattened(now) : null;
            if (lockedAim != null) inner.face(lockedAim); // turn once, then keep the direction
            Location facing = inner.location();
            this.lockedYaw = facing.getYaw();
            this.lockedPitch = flat ? 0.0f : facing.getPitch();
        }

        private Location flattened(Location at) {
            if (!flat) return at.clone();
            Location copy = at.clone();
            copy.setY(inner.eye().getY());
            return copy;
        }

        private Supplier<Location> aim() {
            return () -> {
                if (lockedAim != null) return lockedAim.clone();
                Location at = source.get();
                return at == null ? null : flattened(at);
            };
        }

        private Location turned(Location location) {
            if (lock) location.setYaw(lockedYaw);
            if (lock || flat) location.setPitch(lock ? lockedPitch : 0.0f);
            return location;
        }

        @Override
        public Player owner() {
            return inner.owner();
        }

        @Override
        public World world() {
            return inner.world();
        }

        @Override
        public Location location() {
            return turned(inner.location());
        }

        @Override
        public Location eye() {
            return turned(inner.eye());
        }

        @Override
        public boolean valid() {
            return inner.valid();
        }

        @Override
        public Element element() {
            return inner.element();
        }

        @Override
        public void freeze() {
            inner.freeze();
        }

        @Override
        public void release() {
            inner.release();
        }

        @Override
        public void face(Location at) {
            if (lock) return;
            inner.face(flat ? flattened(at) : at);
        }

        @Override
        public void swing() {
            inner.swing();
        }

        @Override
        public void lunge(Vector velocity) {
            inner.lunge(velocity);
        }

        @Override
        public void teleport(Location to) {
            inner.teleport(to);
        }

        @Override
        public java.util.List<? extends LivingEntity> candidates() {
            return inner.candidates();
        }

        @Override
        public java.util.List<? extends LivingEntity> allyCandidates() {
            return inner.allyCandidates();
        }

        @Override
        public boolean isAlly(LivingEntity entity) {
            return inner.isAlly(entity);
        }

        @Override
        public void heal(LivingEntity ally, double ratio) {
            inner.heal(ally, ratio);
        }

        @Override
        public boolean applyEffects() {
            return inner.applyEffects();
        }

        @Override
        public void damage(LivingEntity victim, double rate) {
            inner.damage(victim, rate);
        }
    }

    // ---- volley ---------------------------------------------------------------------------------------------------------

    /**
     * A volley: after the warning, {@code count} bullets leave a pattern in front of the caster one every
     * {@code interval} ticks. RING: on a circle (radius = the size of the pattern) facing the caster's aim, swinging
     * out by {@code curve} first and then bending to the front; FAN: all from one point over {@code angle} degrees;
     * LINE: side by side on a row; SCATTER: at random inside the circle. After {@code homing-delay-ticks} a bullet with
     * an enemy within {@code homing-radius} turns toward it. A bullet hits the first enemy or block in its way.
     */
    private void volley(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
        Element element = caster.element();
        Location fixed = originPoint(caster, spec, aim);
        SkillSpec.Extras extras = spec.extras();
        new Guarded(() -> {
            caster.release();
            onDone.run();
        }) {
            private int age;
            private int launched;

            @Override
            protected void tick() {
                if (!caster.valid()) {
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (at != null) caster.face(at);
                Vector[] frame = frame(caster, at, extras, fixed);
                Location center = patternCenter(caster, fixed, frame[0], extras);
                if (age < spec.windupTicks()) {
                    if (age % 3 == 0) effects.playSkillCircle(center, frame[1], frame[2], spec.radius(), spec.look(), element, age);
                    age++;
                    return;
                }
                if (launched >= spec.count()) {
                    caster.release();
                    cancel();
                    onDone.run();
                    return;
                }
                if ((age - spec.windupTicks()) % spec.intervalTicks() == 0) {
                    effects.playSkillCircle(center, frame[1], frame[2], spec.radius(), spec.look(), element, age);
                    launchBullet(caster, spec, center, frame, launched);
                    launched++;
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The aim direction (0), the right (1) and the up (2) of a volley. */
    private Vector[] frame(SkillCaster caster, Location at, SkillSpec.Extras extras, Location fixed) {
        Location from = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0) : caster.eye();
        Vector forward = at == null ? caster.location().getDirection() : at.toVector().subtract(from.toVector());
        if (forward.lengthSquared() < 1.0E-6) forward = new Vector(0.0, 0.0, 1.0);
        forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        return new Vector[]{forward, right, up};
    }

    private Location patternCenter(SkillCaster caster, Location fixed, Vector forward, SkillSpec.Extras extras) {
        Location from = fixed != null ? fixed.clone().add(0.0, PROJECTILE_HEIGHT, 0.0) : caster.eye().add(0.0, -0.1, 0.0);
        return from.add(forward.clone().multiply(extras.spawnDistance()));
    }

    private void launchBullet(SkillCaster caster, SkillSpec spec, Location center, Vector[] frame, int number) {
        Element element = caster.element();
        SkillSpec.Extras extras = spec.extras();
        World world = caster.world();
        double perTick = spec.speed() / 20.0;
        Vector aimDirection = offsetAim(frame[0].clone(), spec, 0.0);
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int count = Math.max(1, spec.count());

        Location start = center.clone();
        Vector velocity = aimDirection.clone().multiply(perTick);
        boolean steer = false;
        switch (extras.pattern()) {
            case RING -> {
                double angle = Math.PI * 2.0 * number / count + random.nextDouble(-0.25, 0.25);
                Vector outward = frame[1].clone().multiply(Math.cos(angle)).add(frame[2].clone().multiply(Math.sin(angle)));
                start.add(outward.clone().multiply(spec.radius()));
                velocity = aimDirection.clone().multiply(perTick * 0.55).add(outward.multiply(perTick * extras.curve()));
                steer = true;
            }
            case FAN -> {
                double yaw = spec.angle() * ((number + 0.5) / count - 0.5);
                velocity = rotateAroundVertical(aimDirection, Math.toRadians(-yaw)).multiply(perTick);
            }
            case LINE -> {
                double spacing = count > 1 ? spec.radius() * 2.0 / (count - 1) : 0.0;
                start.add(frame[1].clone().multiply((number - (count - 1) / 2.0) * spacing));
            }
            case SCATTER -> {
                double angle = random.nextDouble(0.0, Math.PI * 2.0);
                double radius = spec.radius() * Math.sqrt(random.nextDouble());
                start.add(frame[1].clone().multiply(Math.cos(angle) * radius)).add(frame[2].clone().multiply(Math.sin(angle) * radius));
            }
        }
        effects.playSkillCastSound(caster.owner(), start, spec.look(), element);
        final Vector initial = velocity;
        final boolean curved = steer && extras.curve() > 0.0;

        new Guarded(null) {
            private Location position = start.clone();
            private Vector current = initial.clone();
            private LivingEntity homing;
            private int age;

            @Override
            protected void tick() {
                if (!caster.valid() || age++ > extras.lifetimeTicks()) {
                    cancel();
                    return;
                }
                Vector desired = aimDirection.clone().multiply(perTick);
                double turn = curved ? 0.22 : 0.0;
                if (extras.homingRadius() > 0.0 && age >= extras.homingDelayTicks()) {
                    if (homing == null || homing.isDead() || !homing.isValid() || age % 6 == 0) {
                        homing = nearest(targets(caster, spec), position, extras.homingRadius());
                    }
                    if (homing != null) {
                        Vector toward = homing.getLocation().add(0.0, homing.getHeight() * 0.5, 0.0).toVector().subtract(position.toVector());
                        if (toward.lengthSquared() > 1.0E-6) {
                            desired = toward.normalize().multiply(perTick);
                            turn = extras.homingTurn();
                        }
                    }
                }
                if (turn > 0.0) current.add(desired.subtract(current).multiply(turn));
                double length = current.length();
                if (length > perTick * 1.2) current.multiply(perTick * 1.2 / length);
                length = current.length();
                if (length < 1.0E-6) return;
                Vector direction = current.clone().normalize();

                RayTraceResult wall = world.rayTraceBlocks(position, direction, length, FluidCollisionMode.NEVER, true);
                double wallDistance = wall == null ? Double.MAX_VALUE : wall.getHitPosition().distance(position.toVector());
                LivingEntity victim = null;
                double hitDistance = Double.MAX_VALUE;
                for (LivingEntity candidate : targets(caster, spec)) {
                    RayTraceResult result = candidate.getBoundingBox().expand(extras.hitRadius()).rayTrace(position.toVector(), direction, length);
                    if (result == null) continue;
                    double distance = result.getHitPosition().distance(position.toVector());
                    if (distance < hitDistance) {
                        hitDistance = distance;
                        victim = candidate;
                    }
                }
                Location next;
                if (victim != null && hitDistance <= wallDistance) {
                    next = position.clone().add(direction.clone().multiply(hitDistance));
                } else if (wall != null) {
                    next = wall.getHitPosition().toLocation(world);
                    victim = null;
                } else {
                    next = position.clone().add(current);
                    victim = null;
                    effects.playSkillBolt(position, next, spec.look(), element);
                    position = next;
                    return;
                }
                effects.playSkillBolt(position, next, spec.look(), element);
                effects.playSkillImpact(next, spec.look(), element);
                if (victim != null) {
                    effects.playSkillHitSound(caster.owner(), next, spec.look(), element);
                    hit(caster, spec, victim, null);
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity nearest(java.util.List<LivingEntity> candidates, Location from, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.getLocation().add(0.0, candidate.getHeight() * 0.5, 0.0).distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** {@code vector} turned around the vertical axis by {@code radians} (positive = to the right). */
    private Vector rotateAroundVertical(Vector vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(vector.getX() * cos - vector.getZ() * sin, vector.getY(), vector.getX() * sin + vector.getZ() * cos);
    }
}
