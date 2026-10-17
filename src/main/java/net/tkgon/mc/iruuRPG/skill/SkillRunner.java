package net.tkgon.mc.iruuRPG.skill;

import net.tkgon.mc.iruuRPG.combat.AttackEffects;
import net.tkgon.mc.iruuRPG.combat.StatusEffectService;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
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
        if (!caster.valid()) {
            onDone.run();
            return;
        }
        caster.freeze();
        Location aimAt = aim.get();
        if (aimAt != null) caster.face(aimAt);
        effects.playSkillWindupSound(caster.location(), spec.look(), caster.element());
        switch (spec.template()) {
            case STRIKE -> strike(caster, spec, onDone);
            case SWEEP -> sweep(caster, spec, aim, onDone);
            case BARRAGE -> barrage(caster, spec, aim, onDone);
            case DASH -> dash(caster, spec, aim, onDone);
            case WAVE -> wave(caster, spec, aim, onDone);
            case NOVA -> nova(caster, spec, onDone);
            case BEAM -> beam(caster, spec, aim, onDone);
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

    // ---- strike -------------------------------------------------------------------------------------------------------

    private void strike(SkillCaster caster, SkillSpec spec, Runnable onDone) {
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
                if (age >= spec.windupTicks()) {
                    Location center = caster.location();
                    effects.playSkillStrike(center, spec.radius(), spec.look(), element);
                    effects.playSkillCastSound(center, spec.look(), element);
                    for (Player player : within(caster, spec.radius(), 2.5)) hit(caster, spec, player);
                    caster.release();
                    cancel();
                    onDone.run();
                    return;
                }
                if (age % 4 == 0) effects.playSkillTelegraphRing(caster.location(), spec.radius(), spec.look(), element);
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- sweep --------------------------------------------------------------------------------------------------------

    private void sweep(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
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
                if (age >= spec.windupTicks()) {
                    Location origin = caster.location();
                    effects.playSkillCrescent(origin, origin.getYaw(), spec.range(), spec.angle(), spec.orientation(), spec.look(), element);
                    effects.playSkillCastSound(origin, spec.look(), element);
                    double tall = spec.orientation() == SkillSpec.Orientation.HORIZONTAL ? 2.5 : 3.5;
                    for (Player player : within(caster, spec.range(), tall)) {
                        if (inFan(caster, player, spec.angle() / 2.0)) hit(caster, spec, player);
                    }
                    caster.release();
                    cancel();
                    onDone.run();
                    return;
                }
                Location at = aim.get();
                if (at != null) caster.face(at);
                if (age % 4 == 0) effects.playSkillTelegraphArc(caster.location(), caster.location().getYaw(), spec.range(), spec.angle(), spec.look(), element);
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- barrage ------------------------------------------------------------------------------------------------------

    private void barrage(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
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
                    fireBolt(caster, spec, at);
                    fired++;
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireBolt(SkillCaster caster, SkillSpec spec, Location at) {
        Element element = caster.element();
        Location from = caster.eye().add(caster.location().getDirection().multiply(0.4)).add(0.0, -0.2, 0.0);
        Vector direction = at.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        direction.normalize();
        caster.swing();
        effects.playSkillCastSound(from, spec.look(), element);
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
                Location next = position.clone().add(direction.clone().multiply(perTick));
                RayTraceResult wall = world.rayTraceBlocks(position, direction, perTick, FluidCollisionMode.NEVER, true);
                double wallDistance = wall == null ? Double.MAX_VALUE : wall.getHitPosition().distance(position.toVector());
                Player hitPlayer = null;
                double hitDistance = Double.MAX_VALUE;
                for (Player player : caster.candidates()) {
                    RayTraceResult result = player.getBoundingBox().expand(0.25).rayTrace(position.toVector(), direction, perTick);
                    if (result == null) continue;
                    double distance = result.getHitPosition().distance(position.toVector());
                    if (distance < hitDistance) {
                        hitDistance = distance;
                        hitPlayer = player;
                    }
                }
                if (hitPlayer != null && hitDistance <= wallDistance) {
                    Location end = position.clone().add(direction.clone().multiply(hitDistance));
                    effects.playSkillBolt(position, end, spec.look(), element);
                    effects.playSkillImpact(end, spec.look(), element);
                    effects.playSkillHitSound(end, spec.look(), element);
                    hit(caster, spec, hitPlayer);
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
        Vector direction = at == null ? null : at.toVector().subtract(start.toVector()).setY(0.0);
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
                    effects.playSkillCastSound(caster.location(), spec.look(), element);
                    caster.lunge(direction.clone().multiply(spec.speed() / 8.0).setY(0.25));
                }
                for (Player player : within(caster, 1.8, 2.5)) {
                    if (hit.add(player.getUniqueId())) hit(caster, spec, player);
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

    // ---- wave ---------------------------------------------------------------------------------------------------------

    private void wave(SkillCaster caster, SkillSpec spec, Supplier<Location> aim, Runnable onDone) {
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
                    // keeps facing the target and marks where the slash will go
                    if (at != null) caster.face(at);
                    if (age % 4 == 0) {
                        Location from = caster.location().add(0.0, 1.0, 0.0);
                        Vector ahead = caster.location().getDirection().setY(0.0);
                        if (ahead.lengthSquared() > 1.0E-6) {
                            effects.playSkillTelegraphLine(from, from.clone().add(ahead.normalize().multiply(Math.min(spec.range(), 10.0))), spec.look(), element);
                        }
                    }
                    age++;
                    return;
                }
                cancel();
                if (at != null) launchWaves(caster, spec, at);
                caster.release(); // the caster is free again while the slashes fly
                onDone.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void launchWaves(SkillCaster caster, SkillSpec spec, Location at) {
        Element element = caster.element();
        Location origin = caster.eye().add(0.0, -0.3, 0.0);
        Vector aimVector = at.toVector().subtract(origin.toVector());
        if (aimVector.lengthSquared() < 1.0E-6) return;
        aimVector.normalize();
        caster.swing();
        effects.playSkillCastSound(caster.location(), spec.look(), element);
        for (int index = 0; index < spec.count(); index++) {
            double offset = Math.toRadians((index - (spec.count() - 1) / 2.0) * spec.angle());
            double cos = Math.cos(offset);
            double sin = Math.sin(offset);
            Vector direction = new Vector(aimVector.getX() * cos - aimVector.getZ() * sin, aimVector.getY(), aimVector.getX() * sin + aimVector.getZ() * cos).normalize();
            flyWave(caster, spec, origin.clone(), direction);
        }
    }

    private void flyWave(SkillCaster caster, SkillSpec spec, Location origin, Vector direction) {
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
                for (Player player : caster.candidates()) {
                    if (hit.contains(player.getUniqueId())) continue;
                    if (!player.getBoundingBox().expand(spec.radius() * 0.6).contains(position.toVector())) continue;
                    hit.add(player.getUniqueId());
                    effects.playSkillImpact(position, spec.look(), element);
                    effects.playSkillHitSound(position, spec.look(), element);
                    hit(caster, spec, player);
                }
                if (travelled >= spec.range()) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- nova ---------------------------------------------------------------------------------------------------------

    private void nova(SkillCaster caster, SkillSpec spec, Runnable onDone) {
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
                if (age >= spec.windupTicks()) {
                    cancel();
                    caster.release(); // the ring spreads on its own
                    onDone.run();
                    expand(caster, spec);
                    return;
                }
                if (age % 4 == 0) effects.playSkillTelegraphRing(caster.location(), spec.radius(), spec.look(), element);
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        // (the caster stays where it started: the ring is centered there)
    }

    private void expand(SkillCaster caster, SkillSpec spec) {
        Element element = caster.element();
        Location center = caster.location().clone();
        effects.playSkillCastSound(center, spec.look(), element);
        double step = spec.speed() / 20.0;
        new Guarded(null) {
            private double radius = 0.5;
            private final Set<UUID> hit = new HashSet<>();

            @Override
            protected void tick() {
                effects.playSkillNovaRing(center, radius, spec.look(), element);
                for (Player player : caster.candidates()) {
                    if (hit.contains(player.getUniqueId())) continue;
                    if (!player.getWorld().equals(center.getWorld())) continue;
                    Location at = player.getLocation();
                    double dx = at.getX() - center.getX();
                    double dz = at.getZ() - center.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(at.getY() - center.getY()) > 2.5) continue;
                    if (distance > radius + 0.6 || distance < radius - step - 0.6) continue;
                    hit.add(player.getUniqueId());
                    effects.playSkillHitSound(at.clone().add(0.0, 1.0, 0.0), spec.look(), element);
                    hit(caster, spec, player);
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
                        Location start = caster.eye().add(0.0, -0.3, 0.0);
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
                    from = caster.eye().add(0.0, -0.3, 0.0);
                    Vector direction = at == null ? caster.location().getDirection() : at.toVector().subtract(from.toVector());
                    if (direction.lengthSquared() < 1.0E-6) {
                        caster.release();
                        cancel();
                        onDone.run();
                        return;
                    }
                    direction.normalize();
                    World world = caster.world();
                    RayTraceResult wall = world.rayTraceBlocks(from, direction, spec.range(), FluidCollisionMode.NEVER, true);
                    double length = wall == null ? spec.range() : wall.getHitPosition().distance(from.toVector());
                    to = from.clone().add(direction.clone().multiply(length));
                    caster.swing();
                    effects.playSkillCastSound(from, spec.look(), element);
                    for (Player player : caster.candidates()) {
                        BoundingBox box = player.getBoundingBox().expand(spec.radius());
                        RayTraceResult result = box.rayTrace(from.toVector(), direction, length);
                        if (result != null) hit(caster, spec, player);
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

    // ---- shared -------------------------------------------------------------------------------------------------------

    private java.util.List<Player> within(SkillCaster caster, double radius, double vertical) {
        Location center = caster.location();
        java.util.List<Player> result = new java.util.ArrayList<>();
        for (Player player : caster.candidates()) {
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

    private boolean inFan(SkillCaster caster, Player player, double halfAngleDegrees) {
        Vector toTarget = player.getLocation().toVector().subtract(caster.location().toVector()).setY(0.0);
        if (toTarget.lengthSquared() < 1.0E-6) return true;
        Vector facing = caster.location().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) return true;
        return Math.toDegrees(facing.normalize().angle(toTarget.normalize())) <= halfAngleDegrees;
    }

    /** One hit: the damage (through the caster), then what the skill adds. */
    private void hit(SkillCaster caster, SkillSpec spec, Player victim) {
        caster.damage(victim, spec.damageRate());
        if (!caster.applyEffects()) return;
        if (spec.knockback() > 0.0) {
            Vector away = victim.getLocation().toVector().subtract(caster.location().toVector()).setY(0.0);
            if (away.lengthSquared() > 1.0E-6) {
                victim.setVelocity(victim.getVelocity().add(away.normalize().multiply(spec.knockback()).setY(0.25)));
            }
        }
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
}
