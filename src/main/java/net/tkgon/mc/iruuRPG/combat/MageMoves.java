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
 * <p>Heavy attacks (left click, the number of normal attacks so far): 0 the echo and the re-activations. A heavy attack thrown while the echo is ready is thrown again
 * by itself 2 s later (that one is not echoed again and needs no combo; at most 3 wait at once); the echo then has a cooldown of 8 s, a second shorter for every hit (one
 * for the enemies hit at the same moment). The heavy click with no normal attack before it re-activates what the last heavy attack left: 1 two bolts after the enemy
 * normal attack 1 hit (else one straight bolt ahead), 2 roots that spread over the ground round the player to 6 blocks and hurt what stands on them (one at a time; the
 * echo's are more); re-activated once they are full, they go back into the player as MP for the time they had left, 3 spikes out of the ground every block ahead, each
 * leaning further away; re-activated when they hit something, three more stab the nearest enemy they hit from the air round it, 4 a sphere where the player aims (a block
 * or an enemy) that shrinks for 1 s drawing the enemies in, glows for 2 s and bursts; re-activated while it glows, it bursts at once for less.
 */
public final class MageMoves {

    /** The numbers of the combo (normal 1 - 5: the damage of each hit and the hits; heavy 1 - 4: what each one says); the config's {@code combat.combo.mage} sets them. */
    static final ComboSpec SPEC = new ComboSpec(MeleeCombo.MAGE,
            new double[]{0.85, 0.9, 0.38, 0.45, 0.8}, new int[]{1, 1, 3, 3, 2},
            new double[]{0.0, 0.7, 0.2, 0.9, 1.7}, new int[]{0, 0, 0, 0, 0}, 40);

    private static final Vector X_AXIS = new Vector(1.0, 0.0, 0.0);
    private static final Vector Y_AXIS = new Vector(0.0, 1.0, 0.0);
    private static final Vector Z_AXIS = new Vector(0.0, 0.0, 1.0);

    /** What the last heavy attack left for the heavy click with no normal attack before it (until a game tick). */
    private record Pending(int untilTick, Runnable action) {
    }

    /** One attack: the kit it is made with, the normal attack it is (0 for a heavy one) and whether it is the echo of one. */
    private record Cast(MoveKit kit, int stage, boolean echo) {
    }

    /** The roots of heavy 2: the points of the roots over the ground ({x, y, z, distance from the middle}), how old and how long they live. */
    private static final class Roots {
        private final MoveKit kit;
        private final Location center;
        private final List<double[]> points;
        private final double radius;
        private final int spread;
        private final int life;
        private final boolean echo;
        private int age;
        private boolean gone;

        private Roots(MoveKit kit, Location center, List<double[]> points, double radius, int spread, int life, boolean echo) {
            this.kit = kit;
            this.center = center;
            this.points = points;
            this.radius = radius;
            this.spread = spread;
            this.life = life;
            this.echo = echo;
        }
    }

    private static final class State {
        /** The enemies normal attacks 1 - 4 of this round hit (for 5), and those normal attack 1 hit (for heavy 1). */
        private final Set<LivingEntity> chainHits = new LinkedHashSet<>();
        private final Set<LivingEntity> firstHits = new LinkedHashSet<>();
        private int echoReadyTick;
        private int echoQueued;
        private int lastCutTick = -1;
        private Material overlay;
        private Pending pending;
        private final List<Roots> roots = new ArrayList<>();
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

    /** True while the last heavy attack left something the heavy click can re-activate. */
    public boolean chainActive(Player player) {
        State state = states.get(player.getUniqueId());
        return state != null && state.pending != null && Bukkit.getCurrentTick() <= state.pending.untilTick();
    }

    /** The class of the player changed (or they left): nothing is left to re-activate or echo, and the roots wither. */
    public void forget(UUID id) {
        State state = states.remove(id);
        if (state != null) for (Roots roots : state.roots) roots.gone = true;
    }

    private void offer(State state, int ticks, Runnable action) {
        state.pending = new Pending(Bukkit.getCurrentTick() + ticks, action);
    }

    private int reactivateTicks() {
        return (int) Math.round(number("reactivate-seconds", 3.0) * 20.0);
    }

    // ---- hits and the echo's cooldown ----------------------------------------------------------------------------------------

    /** One hit of {@code rate}; a hit shortens the echo's cooldown (once for all the enemies hit in the same tick). */
    private boolean hit(Cast cast, LivingEntity target, double rate) {
        if (target == null || target.isDead() || !target.isValid()) return false;
        if (!cast.kit().hit(target, rate)) return false;
        Player player = cast.kit().player;
        State state = state(player);
        if (cast.stage() >= 1 && cast.stage() <= 4) state.chainHits.add(target);
        if (cast.stage() == 1) state.firstHits.add(target);
        int now = Bukkit.getCurrentTick();
        if (state.lastCutTick != now) {
            state.lastCutTick = now;
            if (state.echoReadyTick > now) {
                state.echoReadyTick = Math.max(now, state.echoReadyTick - (int) Math.round(number("echo-cut-seconds", 1.0) * 20.0));
                showEcho(player, state);
            }
        }
        return true;
    }

    /** The echo's cooldown as the weapon's grey overlay. */
    private static void showEcho(Player player, State state) {
        if (state.overlay != null) player.setCooldown(state.overlay, Math.max(0, state.echoReadyTick - Bukkit.getCurrentTick()));
    }

    private static List<LivingEntity> alive(Set<LivingEntity> targets) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity target : targets) if (target.isValid() && !target.isDead()) list.add(target);
        return list;
    }

    private static LivingEntity nearest(Location from, List<LivingEntity> targets, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity target : targets) {
            if (!target.isValid() || target.isDead() || target.getWorld() != from.getWorld()) continue;
            double distance = target.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
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
            state.firstHits.clear();
        }
        MoveKit kit = attackService.kit(player, weapon);
        kit.palette(Palettes.mixed(weapon.element(), 0.25 + 0.12 * stage));
        Cast cast = new Cast(kit, stage, false);
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
        state.firstHits.clear();
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

    // ---- heavy attacks -----------------------------------------------------------------------------------------------------

    /** Throws heavy attack {@code step.heavyIndex()} (0 re-activates what the last one left); a heavy attack thrown while the echo is ready is thrown again 2 s later. */
    public void heavy(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        int index = step.heavyIndex();
        State state = state(player);
        if (index == 0) {
            reactivate(player, state);
            return;
        }
        state.pending = null;
        double rate = step.damageMultiplier();
        List<LivingEntity> marked = alive(state.firstHits);
        cast(player, weapon, index, rate, false, marked);
        int now = Bukkit.getCurrentTick();
        if (state.echoReadyTick > now || state.echoQueued >= (int) number("echo-queue-max", 3.0)) return;
        state.echoQueued++;
        state.echoReadyTick = now + (int) Math.round(number("echo-cooldown-seconds", 8.0) * 20.0);
        state.overlay = weapon.material();
        showEcho(player, state);
        echoMark(player, weapon);
        double share = number("echo-rate", 1.0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            state.echoQueued = Math.max(0, state.echoQueued - 1);
            if (!player.isOnline() || player.isDead() || states.get(player.getUniqueId()) != state || !attackService.isMage(player)) return;
            attackService.beginAttack(player);
            cast(player, weapon, index, rate * share, true, marked);
        }, Math.max(1L, (long) number("echo-delay-ticks", 40.0)));
    }

    private void reactivate(Player player, State state) {
        Pending pending = state.pending;
        state.pending = null;
        if (pending == null || Bukkit.getCurrentTick() > pending.untilTick()) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.4f);
            return;
        }
        pending.action().run();
    }

    private void cast(Player player, RpgItemDefinition weapon, int index, double rate, boolean echo, List<LivingEntity> marked) {
        MoveKit kit = attackService.kit(player, weapon);
        kit.palette(Palettes.mixed(weapon.element(), echo ? 0.95 : 0.75));
        Cast cast = new Cast(kit, 0, echo);
        if (echo) echoFlash(kit);
        switch (index) {
            case 1 -> seekers(cast, rate, marked);
            case 2 -> roots(cast, rate);
            case 3 -> spikes(cast, rate);
            default -> collapse(cast, rate);
        }
    }

    /** The echo is set: a faint ring of runes round the player's feet. */
    private void echoMark(Player player, RpgItemDefinition weapon) {
        Location feet = player.getLocation().add(0.0, 0.1, 0.0);
        Fx.of(player, player.getWorld(), feet).flatRing(feet, 1.1, 28, Palettes.mixed(weapon.element(), 0.95), 0.5, 0.4, 0.8);
        player.getWorld().playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.5f, 1.8f);
    }

    /** The echo goes off: the runes rise round the player and the air hums. */
    private static void echoFlash(MoveKit kit) {
        Location feet = kit.player.getLocation();
        for (int tick = 0; tick < 6; tick++) {
            int which = tick;
            kit.later(tick, () -> {
                Location at = kit.player.getLocation().add(0.0, 0.15 + which * 0.3, 0.0);
                kit.fx(at).flatRing(at, 1.2 - which * 0.12, 24, kit.palette(), 0.4 + which * 0.1, 0.3, 0.9);
            });
        }
        kit.particle(Particle.REVERSE_PORTAL, feet.clone().add(0.0, 1.0, 0.0), 14, 0.5, 0.05);
        kit.sound(feet, Sfx.MAGE_SURGE, 0.45f, 1.7f);
    }

    // 1: two bolts after the enemy normal attack 1 hit, else one straight bolt ahead

    private void seekers(Cast cast, double rate, List<LivingEntity> marked) {
        MoveKit kit = cast.kit();
        LivingEntity target = nearest(kit.player.getLocation(), marked, 32.0);
        if (target == null) {
            straightBolt(cast, rate * number("straight-share", 1.4));
            return;
        }
        kit.sound(kit.eye(), Sfx.MAGE_CAST, 0.8f, 0.9f);
        int count = (int) number("seeker-count", 2.0);
        for (int index = 0; index < count; index++) {
            double side = index % 2 == 0 ? -1.0 : 1.0;
            kit.later(index * 3L, () -> seeker(cast, side, target, rate));
        }
    }

    /** A bolt thrown out to {@code side} that turns and follows {@code target}; it hits the first enemy it meets. */
    private void seeker(Cast cast, double side, LivingEntity target, double rate) {
        MoveKit kit = cast.kit();
        Player player = kit.player;
        World world = player.getWorld();
        Vector[] axes = kit.axes();
        Location start = kit.eye().add(axes[1].clone().multiply(side)).add(axes[2].clone().multiply(0.4)).subtract(axes[0].clone().multiply(0.2));
        double speed = number("seeker-speed", 1.4);
        Vector[] velocity = {axes[1].clone().multiply(side * 0.8).add(axes[2].clone().multiply(0.6)).add(axes[0].clone().multiply(0.3)).normalize().multiply(0.7)};
        Location[] trail = {start.clone(), start.clone()};
        muzzle(kit, start, 1.2);
        kit.every((int) number("seeker-ticks", 50.0), age -> {
            Location from = trail[0];
            if (target.isValid() && !target.isDead() && target.getWorld() == world) {
                Vector desired = middle(target).toVector().subtract(from.toVector());
                if (desired.lengthSquared() > 1.0E-6) {
                    desired.normalize().multiply(speed);
                    velocity[0].add(desired.subtract(velocity[0]).multiply(Math.min(1.0, 0.18 + age * 0.04)));
                }
            }
            double length = velocity[0].length();
            if (length > speed) velocity[0].multiply(speed / length);
            length = Math.max(1.0E-3, velocity[0].length());
            RayTraceResult result = world.rayTrace(from, velocity[0].clone().multiply(1.0 / length), length, FluidCollisionMode.NEVER, true, 0.45,
                    entity -> attackService.isAttackTarget(entity, player));
            Location at = result != null && result.getHitPosition() != null ? result.getHitPosition().toLocation(world) : from.clone().add(velocity[0]);
            drawShot(kit, trail[1], from, at, age, 1.2);
            trail[1] = from;
            trail[0] = at;
            if (result != null) {
                impact(kit, at, 1.2);
                if (result.getHitEntity() instanceof LivingEntity victim) hit(cast, victim, rate);
                return true;
            }
            return false;
        });
    }

    /** One big bolt straight ahead, fast. */
    private void straightBolt(Cast cast, double rate) {
        MoveKit kit = cast.kit();
        Vector[] axes = kit.axes();
        Location start = kit.eye().add(axes[0].clone().multiply(0.6)).subtract(0.0, 0.15, 0.0);
        Vector way = axes[0].clone().multiply(2.0);
        muzzle(kit, start, 1.6);
        kit.sound(start, Sfx.MAGE_CAST, 0.9f, 0.8f);
        kit.sound(start, Sfx.MAGE_WIND, 0.5f, 1.2f);
        fly(kit, start, 11, age -> start.clone().add(way.clone().multiply(age)), 0.5, 1.6, null, (at, victim) -> {
            impact(kit, at, 1.6);
            if (victim != null) hit(cast, victim, rate);
        });
    }

    // 2: roots over the ground round the player

    private void roots(Cast cast, double rate) {
        MoveKit kit = cast.kit();
        Player player = kit.player;
        State state = state(player);
        double radius = number("root-radius", 6.0);
        int spread = (int) Math.max(1, number("root-spread-ticks", 20.0));
        int life = (int) Math.max(spread + 10, number("root-life-ticks", 80.0));
        int period = (int) Math.max(2, number("root-period-ticks", 10.0));
        Location center = kit.ground(player.getLocation().add(0.0, 0.5, 0.0));
        // one field at a time: a new one takes the place of the last (the echo's stand beside it)
        if (!cast.echo()) for (Roots old : state.roots) if (!old.echo) old.gone = true;
        state.roots.removeIf(old -> old.gone);
        Roots field = new Roots(kit, center, grow(kit, center, radius), radius, spread, life, cast.echo());
        state.roots.add(field);
        kit.sound(center, Sfx.MAGE_GROUND, 0.8f, 1.0f);
        kit.shockwave(center, 0.5, radius, 6, 1.0);
        new SafeTask(plugin) {
            @Override
            protected void tick() {
                if (field.gone || !player.isOnline() || field.age >= life) {
                    if (!field.gone) wither(field);
                    field.gone = true;
                    cancel();
                    return;
                }
                int age = field.age++;
                double reach = radius * Curves.expOut(Math.min(1.0, (age + 1.0) / spread), 2.5);
                if (age % 2 == 0) drawRoots(field, reach, age, period);
                if (age > 0 && age % period == 0) {
                    for (LivingEntity victim : kit.around(center, reach)) {
                        double dy = victim.getLocation().getY() - center.getY();
                        if (dy >= -1.5 && dy <= 2.5) hit(cast, victim, rate);
                    }
                    kit.sound(center, Sound.BLOCK_ROOTS_BREAK, 0.6f, 0.7f);
                }
                // full: it can be taken back as MP for the time it has left
                if (age == spread) offer(state, life - spread, () -> drain(player, state));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The roots: from the middle, each a walk outward that turns a little at every step and keeps to the top of the blocks under it, now and then a branch. */
    private List<double[]> grow(MoveKit kit, Location center, double radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<double[]> points = new ArrayList<>();
        int count = (int) number("root-count", 9.0);
        for (int index = 0; index < count; index++) {
            double heading = Math.PI * 2.0 * index / count + random.nextDouble(-0.25, 0.25);
            walk(center, center.getX(), center.getZ(), heading, radius * random.nextDouble(0.85, 1.0), points, random, 0);
        }
        return points;
    }

    private static void walk(Location center, double x, double z, double heading, double length, List<double[]> points, ThreadLocalRandom random, int depth) {
        World world = center.getWorld();
        double walked = 0.0;
        while (walked < length && points.size() < 600) {
            heading += random.nextDouble(-0.35, 0.35);
            x += Math.cos(heading) * 0.3;
            z += Math.sin(heading) * 0.3;
            walked += 0.3;
            double dx = x - center.getX();
            double dz = z - center.getZ();
            RayTraceResult ground = world.rayTraceBlocks(new Location(world, x, center.getY() + 1.5, z), new Vector(0.0, -1.0, 0.0), 4.0, FluidCollisionMode.NEVER, true);
            double y = ground == null ? center.getY() : ground.getHitPosition().getY();
            points.add(new double[]{x, y + 0.06, z, Math.sqrt(dx * dx + dz * dz)});
            if (depth == 0 && random.nextDouble() < 0.07) {
                walk(center, x, z, heading + (random.nextBoolean() ? 0.7 : -0.7), (length - walked) * 0.6, points, random, 1);
            }
        }
    }

    /** The roots out to {@code reach}: deep at the middle, bright toward the tips, the front brightest; a pulse runs out along them before each hurt. */
    private static void drawRoots(Roots field, double reach, int age, int period) {
        MoveKit kit = field.kit;
        Fx fx = kit.fx(field.center);
        if (!fx.active()) return;
        Gradient gradient = kit.palette();
        double pulse = (age % period) / (double) period * field.radius;
        for (double[] point : field.points) {
            double distance = point[3];
            if (distance > reach) continue;
            double t = 0.2 + 0.55 * distance / field.radius;
            double size = 0.8;
            if (reach - distance < 0.5 || Math.abs(distance - pulse) < 0.35) {
                t = 1.0;
                size = 1.2;
            }
            fx.dust(point[0], point[1], point[2], gradient, t, size);
        }
        fx.flatRing(field.center.clone().add(0.0, 0.1, 0.0), reach, (int) Math.max(16, reach * 7), gradient, 0.55, 0.3, 0.7);
        if (age % 4 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int index = 0; index < 3 && !field.points.isEmpty(); index++) {
                double[] point = field.points.get(random.nextInt(field.points.size()));
                if (point[3] <= reach) fx.particle(Particle.ENCHANTMENT_TABLE, point[0], point[1] + 0.3, point[2], 2, 0.1, 0.2, 0.1, 0.3);
            }
        }
    }

    /** The roots dry up where they are. */
    private static void wither(Roots field) {
        MoveKit kit = field.kit;
        Fx fx = kit.fx(field.center);
        for (int index = 0; index < field.points.size(); index += 3) {
            double[] point = field.points.get(index);
            fx.particle(Particle.ASH, point[0], point[1] + 0.1, point[2], 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** Re-activated: every full field of the player goes back into them, MP for the share of its life it had left. */
    private void drain(Player player, State state) {
        double maxMp = attackService.profileForPlayer(player).maxMp();
        double share = number("root-mp-share", 0.12);
        double total = 0.0;
        for (Roots field : state.roots) {
            if (field.gone || field.age < field.spread) continue;
            double left = Curves.clamp01((field.life - field.age) / (double) Math.max(1, field.life - field.spread));
            total += maxMp * share * left;
            field.gone = true;
            absorb(field, player);
        }
        state.roots.removeIf(field -> field.gone);
        if (total <= 0.0) return;
        attackService.restoreMp(player, total);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.7f, 1.4f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.5f, 1.6f);
    }

    /** The roots draw back into the middle and the light goes up the player. */
    private static void absorb(Roots field, Player player) {
        MoveKit kit = field.kit;
        Gradient gradient = kit.palette();
        for (int tick = 0; tick < 6; tick++) {
            double keep = 1.0 - (tick + 1) / 6.0;
            kit.later(tick, () -> {
                Fx fx = kit.fx(field.center);
                for (double[] point : field.points) {
                    if (point[3] > field.radius * keep) continue;
                    fx.dust(point[0], point[1], point[2], gradient, 1.0, 1.0);
                }
            });
        }
        kit.later(5L, () -> {
            Location body = player.getLocation();
            Fx fx = kit.fx(body);
            fx.spiral(body, X_AXIS, Z_AXIS, Y_AXIS, 0.9, 0.3, 2.0, 0.0, 2.0, 30, gradient, 1.0);
            fx.particle(Particle.END_ROD, body.getX(), body.getY() + 1.0, body.getZ(), 8, 0.3, 0.6, 0.3, 0.05);
        });
    }

    // 3: spikes out of the ground every block ahead, each leaning further away

    private void spikes(Cast cast, double rate) {
        MoveKit kit = cast.kit();
        State state = state(kit.player);
        Vector facing = kit.facing();
        Location feet = kit.player.getLocation();
        int count = (int) number("spike-count", 6.0);
        double lean = Math.toRadians(number("spike-lean-step", 12.0));
        double height = number("spike-height", 2.5);
        double width = number("spike-width", 0.8);
        Set<UUID> struck = new HashSet<>();
        List<LivingEntity> hitList = new ArrayList<>();
        kit.sound(feet, Sfx.MAGE_GROUND, 0.9f, 0.9f);
        for (int index = 0; index < count; index++) {
            int which = index;
            kit.later(index, () -> {
                Location base = kit.ground(feet.clone().add(facing.clone().multiply(which + 1.0)).add(0.0, 1.0, 0.0));
                double tilt = lean * which;
                Vector axis = Y_AXIS.clone().multiply(Math.cos(tilt)).add(facing.clone().multiply(Math.sin(tilt)));
                crystal(kit, base, axis, height, width);
                for (LivingEntity victim : along(kit, base, axis, height, width * 0.5 + 0.3)) {
                    if (struck.add(victim.getUniqueId()) && hit(cast, victim, rate)) hitList.add(victim);
                }
                if (which % 2 == 0) kit.sound(base, Sfx.MAGE_GROUND, 0.45f, 1.0f + 0.08f * which);
            });
        }
        // it hit something: three more can stab the nearest of them from the air
        kit.later(count, () -> {
            if (!hitList.isEmpty()) offer(state, reactivateTicks(), () -> airSpikes(cast, hitList));
        });
    }

    /** Re-activated: spikes out of the air 2 blocks or more from the nearest enemy the spikes hit (never lower than its feet), one every 3 ticks, each stabbing through it. */
    private void airSpikes(Cast cast, List<LivingEntity> struck) {
        MoveKit kit = cast.kit();
        LivingEntity target = nearest(kit.player.getLocation(), struck, 40.0);
        if (target == null) return;
        int count = (int) number("spike-extra-count", 3.0);
        long gap = (long) number("spike-extra-gap-ticks", 3.0);
        double rate = number("spike-extra-rate", 0.3);
        kit.sound(target.getLocation(), Sfx.MAGE_SURGE, 0.6f, 1.4f);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < count; index++) {
            kit.later(index * gap, () -> {
                if (!target.isValid() || target.isDead()) return;
                Location center = middle(target);
                double floor = target.getLocation().getY();
                Location from = null;
                for (int attempt = 0; attempt < 12 && from == null; attempt++) {
                    double yaw = random.nextDouble(Math.PI * 2.0);
                    double rise = Math.toRadians(random.nextDouble(0.0, 65.0));
                    double distance = random.nextDouble(2.0, 3.2);
                    Location candidate = center.clone().add(Math.cos(rise) * Math.cos(yaw) * distance, Math.sin(rise) * distance, Math.cos(rise) * Math.sin(yaw) * distance);
                    if (candidate.getY() >= floor && candidate.getBlock().isPassable()) from = candidate;
                }
                if (from == null) from = center.clone().add(0.0, 2.5, 0.0);
                Vector axis = center.toVector().subtract(from.toVector());
                double length = axis.length() + 0.9;
                axis.normalize();
                crystal(kit, from, axis, length, 0.6);
                for (LivingEntity victim : along(kit, from, axis, length, 0.6)) hit(cast, victim, rate);
                kit.sound(from, Sfx.MAGE_IMPACT, 0.6f, 1.3f);
            });
        }
    }

    /** The enemies whose body comes within {@code reach} of the line from {@code base} along {@code axis} for {@code length}. */
    private List<LivingEntity> along(MoveKit kit, Location base, Vector axis, double length, double reach) {
        List<LivingEntity> result = new ArrayList<>();
        Location mid = base.clone().add(axis.clone().multiply(length * 0.5));
        double box = length * 0.5 + reach + 1.0;
        for (Entity entity : base.getWorld().getNearbyEntities(mid, box, box, box)) {
            if (!attackService.isAttackTarget(entity, kit.player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double width = living.getWidth() * 0.5;
            Location feet = living.getLocation();
            double best = Double.MAX_VALUE;
            for (int sample = 0; sample < 3; sample++) {
                Vector point = feet.toVector().add(new Vector(0.0, living.getHeight() * (0.15 + 0.35 * sample), 0.0));
                best = Math.min(best, distanceToLine(point, base.toVector(), axis, length));
            }
            if (best <= reach + width) result.add(living);
        }
        return result;
    }

    static double distanceToLine(Vector point, Vector base, Vector axis, double length) {
        double along = Math.max(0.0, Math.min(length, point.clone().subtract(base).dot(axis)));
        return point.distance(base.clone().add(axis.clone().multiply(along)));
    }

    /** The glass of the element's color for the crystals. */
    private static Material glass(Element element) {
        return switch (element) {
            case RED -> Material.RED_STAINED_GLASS;
            case BLUE -> Material.LIGHT_BLUE_STAINED_GLASS;
            case GREEN -> Material.LIME_STAINED_GLASS;
            case ORANGE -> Material.ORANGE_STAINED_GLASS;
            case WHITE -> Material.PURPLE_STAINED_GLASS;
        };
    }

    /**
     * A crystal spike from {@code base} along {@code axis}, {@code length} long and {@code width} wide at its foot: three blocks (amethyst at the foot, the element's
     * glass above, each narrower) that grow out in 3 ticks, stand a moment and sink back; dust along it and the ground's crumbs at the foot.
     */
    private void crystal(MoveKit kit, Location base, Vector axis, double length, double width) {
        World world = base.getWorld();
        Quaternionf turn = new Quaternionf().rotationTo(0.0f, 1.0f, 0.0f, (float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
        double[][] parts = {{0.0, 0.42, 1.0}, {0.42, 0.33, 0.66}, {0.75, 0.25, 0.34}};
        Material shell = glass(kit.weapon.element());
        Location at = base.clone();
        at.setYaw(0.0f);
        at.setPitch(0.0f);
        List<BlockDisplay> made = new ArrayList<>();
        List<Transformation> full = new ArrayList<>();
        for (int index = 0; index < parts.length; index++) {
            double[] part = parts[index];
            float w = (float) (width * part[2]);
            Transformation flat = shape(turn, w * 0.3f, 0.0f, 0.02f);
            Material material = index == 0 ? Material.AMETHYST_BLOCK : shell;
            made.add(world.spawn(at, BlockDisplay.class, display -> {
                display.setBlock(material.createBlockData());
                display.setPersistent(false);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowRadius(0.0f);
                display.setTransformation(flat);
            }));
            full.add(shape(turn, w, (float) (length * part[0]), (float) (length * part[1])));
        }
        int hold = (int) number("spike-stand-ticks", 16.0);
        org.bukkit.scheduler.BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskLater(plugin, () -> {
            for (int index = 0; index < made.size(); index++) {
                BlockDisplay display = made.get(index);
                if (!display.isValid()) continue;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(3);
                display.setTransformation(full.get(index));
            }
        }, 2L);
        scheduler.runTaskLater(plugin, () -> {
            for (BlockDisplay display : made) {
                if (!display.isValid()) continue;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(5);
                display.setTransformation(shape(turn, 0.05f, 0.0f, 0.02f));
            }
        }, 2L + hold);
        scheduler.runTaskLater(plugin, () -> made.forEach(Entity::remove), 8L + hold);
        // the light along it and the crumbs of the ground at its foot
        Location tip = base.clone().add(axis.clone().multiply(length));
        Fx fx = kit.fx(base);
        kit.later(2L, () -> kit.fx(base).line(base, tip, kit.palette(), 0.2, 1.0, 1.5, 1.1, 0.2, Particle.END_ROD, 4));
        Block under = base.clone().subtract(0.0, 0.1, 0.0).getBlock();
        if (under.getType().isSolid()) {
            fx.particle(Particle.BLOCK_CRACK, base.getX(), base.getY() + 0.1, base.getZ(), 12, 0.35, 0.05, 0.35, 0.1, under.getBlockData());
        }
        fx.particle(Particle.ENCHANTMENT_TABLE, base.getX(), base.getY() + 0.2, base.getZ(), 6, 0.3, 0.1, 0.3, 0.5);
    }

    /** A block of {@code w} by {@code h} by {@code w} standing on the axis from {@code h0} up, turned by {@code turn} (the turn is about the foot of the spike). */
    private static Transformation shape(Quaternionf turn, float w, float h0, float h) {
        Vector3f offset = turn.transform(new Vector3f(-w * 0.5f, h0, -w * 0.5f));
        return new Transformation(offset, new Quaternionf(turn), new Vector3f(w, h, w), new Quaternionf());
    }

    // 4: a sphere that shrinks drawing the enemies in, glows and bursts

    private void collapse(Cast cast, double rate) {
        MoveKit kit = cast.kit();
        Player player = kit.player;
        State state = state(player);
        AttackService.RangeTrace trace = attackService.traceRange(player);
        Location center;
        if (trace.victim() != null) {
            center = middle(trace.victim());
        } else {
            Location hit = trace.hitLocation();
            Vector back = player.getEyeLocation().toVector().subtract(hit.toVector());
            center = back.lengthSquared() < 1.0E-6 ? hit : hit.clone().add(back.normalize().multiply(0.6));
        }
        double radius = number("sphere-radius", 4.0);
        int shrink = (int) Math.max(1, number("sphere-shrink-ticks", 20.0));
        int glow = (int) Math.max(1, number("sphere-glow-ticks", 40.0));
        double blast = number("sphere-blast-radius", 3.5);
        double pull = number("sphere-pull", 0.3);
        double core = 0.6;
        Gradient gradient = kit.palette();
        boolean[] done = {false};
        java.util.function.DoubleConsumer explode = share -> {
            if (done[0]) return;
            done[0] = true;
            blastLook(kit, center, blast);
            for (LivingEntity victim : kit.around(center, blast)) {
                if (middle(victim).distance(center) > blast + victim.getHeight() * 0.5) continue;
                if (hit(cast, victim, rate * share)) kit.push(victim, center.toVector(), 0.6, 0.35);
            }
        };
        kit.sound(center, Sfx.MAGE_SURGE, 0.9f, 0.9f);
        kit.sound(center, Sfx.MAGE_WIND, 0.7f, 0.8f);
        // the shell drawn in by the vfx flow "collapse"
        FlowEffect flow = FlowEffects.find("collapse");
        if (flow != null) {
            try {
                FlowArgs args = FlowArgs.parse(flow.params(), List.of("from=" + radius, "to=" + core, "shrink=" + shrink, "count=240", "life=24", "size=1.0",
                        "color1=" + hex(gradient.at(0.25)), "color2=" + hex(gradient.at(1.0))));
                FlowRunner.start(plugin, player, FlowOrigin.fixed(center), flow, args, shrink);
            } catch (IllegalArgumentException ignored) {
                // (the numbers of the config out of the flow's range: the sphere is drawn without it)
            }
        }
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (done[0] || !player.isOnline()) {
                    cancel();
                    return;
                }
                ParticleFx.mark(player);
                Fx fx = kit.fx(center);
                if (age < shrink) {
                    double t = (age + 1.0) / shrink;
                    double r = core + (radius - core) * (1.0 - t) * (1.0 - t);
                    fx.sphereShell(center, r, age * 0.25, (int) (36 + 24 * r), gradient, 1.0);
                    double tilt = age * 0.15;
                    fx.ring(center, X_AXIS, new Vector(0.0, Trig.sin(tilt), Trig.cos(tilt)), r * 1.05, 36, gradient, 0.8, 0.2, 0.9);
                    for (LivingEntity victim : kit.around(center, r + 1.5)) kit.pull(victim, center, pull * (0.6 + 0.4 * t));
                    if (age % 5 == 0) kit.sound(center, Sfx.MAGE_WIND, 0.45f, 0.8f + 0.5f * (float) t);
                } else if (age < shrink + glow) {
                    int lit = age - shrink;
                    if (lit == 0) {
                        // glowing: the heavy click with nothing before it bursts it at once, for less
                        offer(state, glow, () -> explode.accept(number("sphere-early-share", 0.6)));
                        kit.sound(center, Sfx.MAGE_SURGE, 0.8f, 1.5f);
                    }
                    double beat = 0.5 + 0.5 * Trig.sin(lit * 0.6);
                    fx.sphereShell(center, core * (0.85 + 0.25 * beat), lit * 0.5, 34, gradient, 1.4);
                    fx.dust(center.getX(), center.getY(), center.getZ(), gradient, 1.0, 2.6 + beat);
                    fx.particle(Particle.END_ROD, center.getX(), center.getY(), center.getZ(), 2, 0.25, 0.25, 0.25, 0.03);
                    if (lit % 2 == 0) {
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        for (int ray = 0; ray < 3; ray++) {
                            Vector out = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                            if (out.lengthSquared() < 1.0E-6) continue;
                            fx.line(center, center.clone().add(out.normalize().multiply(1.0 + beat)), gradient, 1.0, 0.4, 1.0, 0.8, 0.2, null, 1);
                        }
                    }
                    if (lit % 10 == 0) {
                        fx.particle(Particle.FLASH, center.getX(), center.getY(), center.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                        kit.sound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 0.8f + 0.8f * lit / glow);
                    }
                    for (LivingEntity victim : kit.around(center, 2.2)) kit.pull(victim, center, 0.12);
                } else {
                    explode.accept(1.0);
                    cancel();
                    return;
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06x", color.asRGB());
    }

    /** The burst: a flash, shells opening one after the other, a shock wave on the ground and sparks thrown out. */
    private void blastLook(MoveKit kit, Location center, double radius) {
        Fx fx = kit.fx(center);
        Gradient gradient = kit.palette();
        fx.particle(Particle.FLASH, center.getX(), center.getY(), center.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        fx.particle(Particle.EXPLOSION_LARGE, center.getX(), center.getY(), center.getZ(), 2, 0.6, 0.6, 0.6, 0.0);
        fx.particle(Particle.END_ROD, center.getX(), center.getY(), center.getZ(), 36, 0.3, 0.3, 0.3, 0.35);
        for (int step = 0; step < 4; step++) {
            double r = radius * (0.3 + 0.25 * step);
            double t = 1.0 - 0.2 * step;
            int which = step;
            kit.later(step, () -> kit.fx(center).sphereShell(center, r, which * 0.4, (int) (40 + 14 * r), gradient, 1.3 * t));
        }
        kit.shockwave(kit.ground(center.clone().add(0.0, 0.5, 0.0)), 0.6, radius + 1.5, 5, 1.3);
        kit.sound(center, Sfx.MAGE_BLAST, 1.0f, 0.9f);
    }
}
