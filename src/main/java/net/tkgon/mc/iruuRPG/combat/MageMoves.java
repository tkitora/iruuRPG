package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillRunner;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.vfx.Curves;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowArgs;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffect;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffects;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowOrigin;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowRunner;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * The mage's combo (plan {@code mage}, see {@link MeleeCombo}) on the ranged weapon. The colors are the weapon's element and purple (the more powerful the attack, the
 * more purple); the sounds are heavy magic, the power heard as air torn and energy gathering ({@link Sfx#MAGE_CAST} and the other MAGE roles).
 *
 * <p>Normal attacks (right click): 1 a bolt that leaves on the left and arcs into the aim, 2 the same from the right, 3 three arcing bolts, from the left, from above and
 * from the right, 2 ticks apart, 4 three big bolts fanned out one after the other that come together just before the aim, cross and fly on straight, 5 a pillar of magic
 * out of the ground under every enemy normal attacks 1 - 4 of this round hit (where the player aims when they hit none).
 *
 * <p>Its heavy attacks (the echo that threw them again by itself, the roots, the spikes and the shrinking sphere, and their re-activations) were
 * taken away in t168: the mage's ranged skills are {@link MageArcana}.
 */
public final class MageMoves {

    /** The numbers of the combo (normal 1 - 5: the damage of each hit and the hits; heavy 1 - 4: what each one says); the config's {@code combat.combo.mage} sets them. */
    static final ComboSpec SPEC = new ComboSpec(MeleeCombo.MAGE,
            new double[]{0.85, 0.9, 0.38, 0.45, 0.8}, new int[]{1, 1, 3, 3, 2},
            new double[]{0.0, 0.7, 0.2, 0.9, 1.7, 0.6}, new int[]{0, 0, 0, 0, 0, 0}, 40);

    private static final Vector X_AXIS = new Vector(1.0, 0.0, 0.0);
    private static final Vector Y_AXIS = new Vector(0.0, 1.0, 0.0);
    private static final Vector Z_AXIS = new Vector(0.0, 0.0, 1.0);

    /** One attack: the kit it is made with and the normal attack it is. */
    private record Cast(MoveKit kit, int stage) {
    }

    private static final class State {
        /** The enemies normal attacks 1 - 4 of this round hit (for 5). */
        private final Set<LivingEntity> chainHits = new LinkedHashSet<>();
    }

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final Map<UUID, State> states = new HashMap<>();

    public MageMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects, SkillRegistry skills, SkillRunner runner) {
        this.plugin = plugin;
        this.attackService = attackService;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.mage." + key, fallback);
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new State());
    }

    /** The class of the player changed (or they left): what was kept of them goes. */
    public void forget(UUID id) {
        states.remove(id);
    }

    // ---- hits -------------------------------------------------------------------------------------------------------------------

    /** One hit of {@code rate}; the enemies normal attacks 1 - 4 hit are kept for the pillars of 5. */
    private boolean hit(Cast cast, LivingEntity target, double rate) {
        if (target == null || target.isDead() || !target.isValid()) return false;
        if (!cast.kit().hit(target, rate)) return false;
        if (cast.stage() >= 1 && cast.stage() <= 4) state(cast.kit().player).chainHits.add(target);
        return true;
    }

    private static List<LivingEntity> alive(Set<LivingEntity> targets) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity target : targets) if (target.isValid() && !target.isDead()) list.add(target);
        return list;
    }

    /** How much farther than its width a shot still meets the enemy it was thrown at (blocks). */
    private static final double AIMED_SLACK = 0.9;

    private static Location middle(LivingEntity target) {
        return target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
    }

    // ---- shots ------------------------------------------------------------------------------------------------------------------

    private static Location bezier(Location a, Location b, Location c, double t) {
        double u = 1.0 - t;
        return new Location(a.getWorld(), u * u * a.getX() + 2.0 * u * t * b.getX() + t * t * c.getX(), u * u * a.getY() + 2.0 * u * t * b.getY() + t * t * c.getY(),
                u * u * a.getZ() + 2.0 * u * t * b.getZ() + t * t * c.getZ());
    }

    /** Two unit vectors at right angles to {@code way} and to each other. */
    private static Vector[] frame(Vector way) {
        Vector forward = way.clone().normalize();
        Vector side = forward.clone().crossProduct(Y_AXIS);
        if (side.lengthSquared() < 1.0E-6) side = X_AXIS.clone();
        side.normalize();
        return new Vector[]{side, side.clone().crossProduct(forward).normalize()};
    }

    /**
     * A shot moved along {@code path} (its place at age 1, 2, ... up to {@code ticks}), tested between two places against the blocks and the enemies
     * ({@code radius} wide); {@code onEnd} gets where it stopped and the enemy it met (null for a block or the end of the path).
     * {@code aimed} is the enemy the player aimed at (or null): a shot that comes close to it meets it even when it has moved on a little
     * since the shot left (the shot is slower than the click, an enemy that walks would be missed by a path drawn at the click).
     */
    private void fly(MoveKit kit, Location start, int ticks, IntFunction<Location> path, double radius, double size, LivingEntity aimed, BiConsumer<Location, LivingEntity> onEnd) {
        Player player = kit.player;
        World world = start.getWorld();
        Location[] trail = {start.clone(), start.clone()};
        kit.every(ticks + 1, age -> {
            Location from = trail[0];
            Location next = path.apply(age + 1);
            Vector way = next.toVector().subtract(from.toVector());
            double length = way.length();
            RayTraceResult result = length < 1.0E-6 ? null : world.rayTrace(from, way.multiply(1.0 / length), length, FluidCollisionMode.NEVER, true, radius,
                    entity -> attackService.isAttackTarget(entity, player));
            Location at = result != null && result.getHitPosition() != null ? result.getHitPosition().toLocation(world) : next;
            drawShot(kit, trail[1], from, at, age, size);
            trail[1] = from;
            trail[0] = at;
            LivingEntity met = result != null && result.getHitEntity() instanceof LivingEntity victim ? victim : null;
            if (result == null && aimed != null && aimed.isValid() && !aimed.isDead() && aimed.getWorld().equals(world)) {
                double reach = radius + AIMED_SLACK;
                if (middle(aimed).distanceSquared(at) <= reach * reach) met = aimed;
            }
            if (result != null || met != null || age + 1 >= ticks) {
                onEnd.accept(at, met);
                return true;
            }
            return false;
        });
    }

    /** A shot in flight: a tail from two places back, a bright core in a small turning shell, and two threads of light winding round its way. */
    private static void drawShot(MoveKit kit, Location back, Location from, Location head, int age, double size) {
        Fx fx = kit.fx(head);
        if (!fx.active()) return;
        Gradient gradient = kit.palette();
        fx.line(back, head, gradient, 0.15, 0.95, 2.0, 0.9 * size, 0.16, null, 1);
        fx.dust(head.getX(), head.getY(), head.getZ(), gradient, 1.0, 1.8 * size);
        fx.sphereShell(head, 0.2 * size, age * 0.9, 10, gradient, 0.75 * size);
        Vector way = head.toVector().subtract(from.toVector());
        if (way.lengthSquared() > 1.0E-6) {
            Vector[] axes = frame(way);
            for (int arm = 0; arm < 2; arm++) {
                double angle = age * 1.2 + arm * Math.PI;
                double r = 0.32 * size;
                double c = Trig.cos(angle) * r;
                double s = Trig.sin(angle) * r;
                fx.dust(head.getX() + axes[0].getX() * c + axes[1].getX() * s, head.getY() + axes[0].getY() * c + axes[1].getY() * s,
                        head.getZ() + axes[0].getZ() * c + axes[1].getZ() * s, gradient, 0.7, 0.8 * size);
            }
        }
        if (age % 3 == 0) fx.particle(Particle.END_ROD, head.getX(), head.getY(), head.getZ(), 1, 0.05, 0.05, 0.05, 0.01);
    }

    /** Where a shot leaves the staff: a small ring across the view and a few sparks. */
    private static void muzzle(MoveKit kit, Location at, double size) {
        Fx fx = kit.fx(at);
        Vector[] axes = kit.axes();
        fx.ring(at, axes[1], axes[2], 0.35 * size, 14, kit.palette(), 0.5, 0.4, 0.9);
        fx.particle(Particle.ENCHANTMENT_TABLE, at.getX(), at.getY(), at.getZ(), (int) Math.round(6 * size), 0.2, 0.2, 0.2, 0.6);
    }

    /** Where a shot lands: a shell that opens in two steps, a flat ring and sparks thrown out. */
    private static void impact(MoveKit kit, Location at, double scale) {
        Fx fx = kit.fx(at);
        Gradient gradient = kit.palette();
        double turn = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        fx.sphereShell(at, 0.4 * scale, turn, 24, gradient, 1.1);
        fx.flatRing(at, 0.9 * scale, 20, gradient, 0.6, 0.3, 0.9);
        fx.particle(Particle.END_ROD, at.getX(), at.getY(), at.getZ(), (int) Math.round(5 * scale), 0.1, 0.1, 0.1, 0.12);
        kit.later(1L, () -> kit.fx(at).sphereShell(at, 0.85 * scale, turn + 0.4, 30, gradient, 0.8));
        kit.sound(at, Sfx.MAGE_IMPACT, (float) Math.min(1.0, 0.55 * scale), 1.0f + ThreadLocalRandom.current().nextFloat() * 0.15f);
    }

    // ---- normal attacks ----------------------------------------------------------------------------------------------------

    /** The normal attack {@code step.stage()} (1 - 5) of the combo. */
    public void normal(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        State state = state(player);
        int stage = step.stage();
        if (stage == 1) {
            state.chainHits.clear();
        }
        MoveKit kit = attackService.kit(player, weapon);
        kit.palette(Palettes.mixed(weapon.element(), 0.25 + 0.12 * stage));
        Cast cast = new Cast(kit, stage);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        switch (stage) {
            case 1 -> arcBolt(cast, -1.0, 0.15, rate);
            case 2 -> arcBolt(cast, 1.0, 0.15, rate);
            case 3 -> {
                // left, above, right, 2 ticks apart
                double[][] places = {{-1.0, 0.1}, {0.0, 1.0}, {1.0, 0.1}};
                for (int index = 0; index < hits; index++) {
                    double[] place = places[index % places.length];
                    kit.later(index * 2L, () -> arcBolt(cast, place[0], place[1], rate));
                }
            }
            case 4 -> {
                kit.sound(kit.eye(), Sfx.MAGE_WIND, 0.6f, 1.1f);
                double[][] fan = {{-1.5, 0.2}, {0.0, 1.2}, {1.5, 0.2}};
                for (int index = 0; index < hits; index++) {
                    double[] place = fan[index % fan.length];
                    kit.later(index * 3L, () -> funnelBolt(cast, place[0], place[1], rate));
                }
            }
            default -> pillars(cast, rate, hits);
        }
    }

    /** A bolt that leaves {@code side} (right, negative = left) and {@code lift} off the eyes, bending out that way, and arcs into the aim. */
    private void arcBolt(Cast cast, double side, double lift, double rate) {
        MoveKit kit = cast.kit();
        Vector[] axes = kit.axes();
        Vector offset = axes[1].clone().multiply(side).add(axes[2].clone().multiply(lift));
        Location start = kit.eye().add(axes[0].clone().multiply(0.5)).add(offset.clone().multiply(0.9)).subtract(0.0, 0.2, 0.0);
        Location aim = kit.aim();
        LivingEntity aimed = kit.aimed();
        Vector across = aim.toVector().subtract(start.toVector());
        double distance = Math.max(1.0, across.length());
        Vector bend = offset.lengthSquared() < 1.0E-6 ? axes[2].clone() : offset.clone().normalize();
        Location control = start.clone().add(across.multiply(0.45)).add(bend.multiply(Math.min(3.5, 0.9 + distance * 0.2)));
        int ticks = (int) Math.max(5, Math.min(16, Math.round(distance / 1.5)));
        muzzle(kit, start, 1.0);
        kit.sound(start, Sfx.MAGE_CAST, 0.55f, 1.05f + (float) (side * 0.06 + lift * 0.1));
        // the end of the path follows the enemy aimed at while it lives
        fly(kit, start, ticks, age -> bezier(start, control, aimed != null && aimed.isValid() && !aimed.isDead() && aimed.getWorld().equals(aim.getWorld())
                ? middle(aimed) : aim, age / (double) ticks), 0.35, 1.0, aimed, (at, victim) -> {
            impact(kit, at, 1.0);
            if (victim != null) hit(cast, victim, rate);
        });
    }

    /** One big bolt of the fan: it swings out wide, comes in to a point just before the aim, crosses the others there and flies on straight. */
    private void funnelBolt(Cast cast, double side, double lift, double rate) {
        MoveKit kit = cast.kit();
        Vector[] axes = kit.axes();
        Vector offset = axes[1].clone().multiply(side).add(axes[2].clone().multiply(lift));
        Location eye = kit.eye();
        Location start = eye.clone().add(axes[0].clone().multiply(0.4)).add(offset.clone().multiply(0.8)).subtract(0.0, 0.15, 0.0);
        Location aim = kit.aim();
        LivingEntity aimed = kit.aimed();
        Vector toAim = aim.toVector().subtract(eye.toVector());
        double distance = toAim.length();
        toAim = distance < 1.0E-6 ? axes[0].clone() : toAim.multiply(1.0 / distance);
        double meet = Math.max(2.5, distance - 1.8);
        Location cross = eye.clone().add(toAim.clone().multiply(meet));
        Location control = start.clone().add(offset.clone().normalize().multiply(1.6)).add(toAim.clone().multiply(meet * 0.35));
        int bend = (int) Math.max(4, Math.min(14, Math.round(start.distance(cross) / 1.3)));
        Vector tangent = cross.toVector().subtract(control.toVector());
        if (tangent.lengthSquared() < 1.0E-6) tangent = toAim.clone();
        Vector onward = tangent.normalize().multiply(0.5).add(toAim.clone().multiply(0.5)).normalize().multiply(1.5);
        int straight = (int) number("funnel-straight-ticks", 5.0);
        muzzle(kit, start, 1.5);
        kit.sound(start, Sfx.MAGE_CAST, 0.75f, 0.85f);
        fly(kit, start, bend + straight, age -> age <= bend ? bezier(start, control, cross, age / (double) bend)
                : cross.clone().add(onward.clone().multiply(age - bend)), 0.6, 1.6, aimed, (at, victim) -> {
            impact(kit, at, 1.5);
            if (victim != null) hit(cast, victim, rate);
        });
    }

    /** 5: a pillar of magic out of the ground under each enemy normal attacks 1 - 4 hit (where the player aims when none), after a ring of runes; {@code waves} hits. */
    private void pillars(Cast cast, double rate, int waves) {
        MoveKit kit = cast.kit();
        State state = state(kit.player);
        double reach = number("pillar-reach", 24.0);
        List<LivingEntity> targets = alive(state.chainHits);
        state.chainHits.clear();
        List<Location> spots = new ArrayList<>();
        Location feet = kit.player.getLocation();
        targets.sort(java.util.Comparator.comparingDouble(target -> target.getWorld() == feet.getWorld() ? target.getLocation().distanceSquared(feet) : Double.MAX_VALUE));
        for (LivingEntity target : targets) {
            if (spots.size() >= 6) break;
            if (target.getWorld() != feet.getWorld() || target.getLocation().distanceSquared(feet) > reach * reach) continue;
            spots.add(kit.ground(target.getLocation().add(0.0, 0.5, 0.0)));
        }
        if (spots.isEmpty()) spots.add(kit.ground(kit.aim().add(0.0, 0.5, 0.0)));
        double radius = number("pillar-radius", 1.6);
        double height = number("pillar-height", 4.0);
        int warn = (int) number("pillar-delay-ticks", 5.0);
        int gap = (int) number("pillar-gap-ticks", 4.0);
        kit.sound(kit.eye(), Sfx.MAGE_SURGE, 0.6f, 1.25f);
        for (Location spot : spots) runes(kit, spot, radius, warn);
        for (int wave = 0; wave < waves; wave++) {
            int which = wave;
            kit.later(warn + (long) gap * wave, () -> {
                Set<UUID> struck = new HashSet<>();
                for (Location spot : spots) {
                    pillarLook(kit, spot, radius, height, which);
                    for (LivingEntity victim : kit.column(spot, radius, height)) {
                        if (struck.add(victim.getUniqueId())) hit(cast, victim, rate);
                    }
                }
                kit.sound(spots.get(0), which == 0 ? Sfx.MAGE_BLAST : Sfx.MAGE_IMPACT, which == 0 ? 0.55f : 0.6f, which == 0 ? 1.35f : 0.9f);
            });
        }
    }

    /** A ring of runes on the ground that brightens for {@code ticks} ticks: where something comes out. */
    private static void runes(MoveKit kit, Location spot, double radius, int ticks) {
        Location at = spot.clone().add(0.0, 0.1, 0.0);
        for (int tick = 0; tick < ticks; tick++) {
            double t = (tick + 1.0) / ticks;
            int which = tick;
            kit.later(tick, () -> {
                Fx fx = kit.fx(at);
                fx.flatRing(at, radius, 32, kit.palette(), 0.3 + 0.6 * t, 0.3, 1.0);
                fx.flatRing(at, radius * 0.62, 20, kit.palette(), 0.5 + 0.4 * t, 0.2, 0.8);
                fx.spiral(at, X_AXIS, Z_AXIS, Y_AXIS, 0.2, radius * 0.9, 0.8, which * 0.5, 0.0, 14, kit.palette(), 0.8);
            });
        }
    }

    /** A pillar going up: rings stacked to {@code height}, turning, the color brighter higher up, sparks thrown up and a ring along the ground. */
    private static void pillarLook(MoveKit kit, Location base, double radius, double height, int wave) {
        Gradient gradient = kit.palette();
        for (int tick = 0; tick < 5; tick++) {
            int which = tick;
            kit.later(tick, () -> {
                Fx fx = kit.fx(base);
                double top = height * Curves.expOut((which + 1) / 5.0, 2.0);
                for (double y = 0.2; y <= top; y += 0.45) {
                    double share = y / height;
                    double r = radius * (1.0 - 0.3 * share) * (1.0 - 0.15 * which / 4.0);
                    double turn = which * 0.5 + y * 0.8 + wave * 0.9;
                    Vector u = new Vector(Trig.cos(turn), 0.0, Trig.sin(turn));
                    Vector v = new Vector(-Trig.sin(turn), 0.0, Trig.cos(turn));
                    fx.ring(base.clone().add(0.0, y, 0.0), u, v, r, 16, gradient, 0.3 + 0.7 * share, 0.3, 1.0);
                }
                if (which == 0) {
                    fx.particle(Particle.END_ROD, base.getX(), base.getY() + 0.3, base.getZ(), 10, radius * 0.4, 0.2, radius * 0.4, 0.18);
                    fx.particle(Particle.ENCHANTMENT_TABLE, base.getX(), base.getY() + height * 0.5, base.getZ(), 16, radius * 0.5, height * 0.3, radius * 0.5, 0.8);
                }
            });
        }
        kit.shockwave(base, 0.5, radius + 1.2, 4, 1.0);
    }
}
