package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.combat.clone.CloneBody;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The clones that stay (the others, which step in for a blow and go, are in {@link Clones}). Two kinds:
 * <ul>
 *   <li>the grey clone that the assassin's heavy attack 0 flings in front of the assassin, the way they face (cooldown 8 s): it stands there for 4.5 s and goes. 強1 again (up to twice, while it stands) is a
 *       re-activation of it: the first makes one more clone, the second sends the clones charging forward ({@link #recast}), 15% each, and they go.
 *       At most two exist at a time (a third takes the place of the oldest; the short-lived clones that strike for a blow count against the two as well and are not made when there
 *       is no room). The first heavy attack after a clone was made, other than heavy 0 and 1, is made by the clones too, once, for 30% of its damage ({@link #mimic});</li>
 *   <li>the three clones of the item swap skill (red outline, red and black): they are flung forward when the skill starts (all other clones are destroyed), and copy the
 *       assassin's next ten attacks (a normal or a heavy one; for heavy 1 each clone throws a shuriken), each copy a hit of 5% of the weapon's damage ({@link #copyAttack}); after the last the clone is destroyed. They do not count against the two, and
 *       end with the skill. A blow of 40% of the assassin's life or more that lands while any remain destroys them all and is nullified ({@link #sacrifice}).</li>
 * </ul>
 * Every clone faces the enemy it attacks. Each enemy a clone has hurt is remembered for the echo of the skill's second press.
 */
final class AssassinClones {

    private static final int MAX_STANDING = 2;

    static final class Ghost {
        final Player owner;
        final boolean swap;
        final Clones.Look look;
        Location pos;
        double yawDegrees;
        int age;
        final int life;
        int copies;
        int leaving = -1;
        CloneBody body;
        boolean bodyTried;
        Location slideFrom;
        Location slideTo;
        int slideTicks;
        int slideAge;
        double pose;
        /** Where it was made from (to look for the enemy it faces); null for none. */
        MoveKit kit;
        /** A decoy (中 of the redesign of 2026-11-02): it strikes the nearest enemy within {@code hitReach} every {@code hitEvery} ticks for {@code hitRate}; 0 for none. */
        double hitRate;
        int hitEvery;
        double hitReach;

        Ghost(Player owner, boolean swap, Clones.Look look, Location pos, double yawDegrees, int life, int copies) {
            this.owner = owner;
            this.swap = swap;
            this.look = look;
            this.pos = pos.clone();
            this.yawDegrees = yawDegrees;
            this.life = life;
            this.copies = copies;
        }
    }

    private static final Map<UUID, List<Ghost>> GHOSTS = new HashMap<>();
    private static final Map<UUID, Boolean> MIMIC = new HashMap<>();
    private static final Map<UUID, Set<UUID>> DAMAGED = new HashMap<>();
    private static final Map<UUID, Integer> TRANSIENT = new HashMap<>();
    /** The tick the clones of the item swap skill last moved with the assassin (they follow a dash once in 10 ticks, half a second). */
    private static final Map<UUID, Integer> MOVED = new HashMap<>();
    private static final int MOVE_GAP_TICKS = 10;
    /** {the re-activations of heavy 0 used (0 - 1), the tick of the last press} of each owner whose clones stand. */
    private static final Map<UUID, int[]> CAST = new HashMap<>();
    /** The least ticks between a press of heavy 0 and its re-activation (a held button must not press twice). */
    private static final int RECAST_GAP = 6;
    private static SafeTask task;

    private AssassinClones() {
    }

    // ---- the cap of two ----------------------------------------------------------------------------------------------------------

    private static int standing(Player owner) {
        int count = 0;
        for (Ghost ghost : GHOSTS.getOrDefault(owner.getUniqueId(), List.of())) if (!ghost.swap && ghost.leaving < 0) count++;
        return count + TRANSIENT.getOrDefault(owner.getUniqueId(), 0);
    }

    /** Asks for room for a short-lived clone (one that steps in for a blow): false when two clones exist already. Call {@link #endTransient} when it is gone. */
    static boolean beginTransient(Player owner) {
        if (standing(owner) >= MAX_STANDING) return false;
        TRANSIENT.merge(owner.getUniqueId(), 1, Integer::sum);
        return true;
    }

    static void endTransient(Player owner) {
        TRANSIENT.merge(owner.getUniqueId(), -1, (a, b) -> Math.max(0, a + b));
    }

    /**
     * The look of a clone that only comes for a blow of the combo: the human model (an entity, like the others) with no glow, red and black during the item swap skill, grey
     * otherwise. ({@code effect-clone-scale} under 1 draws a smaller figure of particles instead: the server cannot scale a player entity.) The clones that stand (heavy 0) and those
     * of the item swap skill keep their outline.
     */
    static Clones.Look lookFor(MoveKit kit, boolean swapSkillOn) {
        return Clones.Look.effect(swapSkillOn, Math.max(0.2, Math.min(1.0, kit.number(AssassinMeleeCombo.PLAN, "effect-clone-scale", 1.0))));
    }

    // ---- making them ---------------------------------------------------------------------------------------------------------------

    private static void ensureTask(JavaPlugin plugin) {
        JavaPluginHolder.plugin = plugin;
        if (task != null) return;
        task = new SafeTask(plugin) {
            @Override
            protected void tick() {
                step();
                if (GHOSTS.isEmpty()) {
                    cancel();
                    task = null;
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);
    }

    private static void remove(Ghost ghost, boolean poof) {
        if (ghost.body != null) ghost.body.remove();
        ghost.body = null;
        if (poof && ghost.owner.isOnline()) {
            Fx fx = Fx.of(ghost.owner, ghost.pos.getWorld(), ghost.pos);
            fx.sphereShell(ghost.pos.clone().add(0.0, 1.0, 0.0), 0.7, 0.0, 24, ghost.look.gradient(), 1.0);
            fx.flatRing(ghost.pos.clone().add(0.0, 0.1, 0.0), 1.0, 24, ghost.look.gradient(), 0.7, 0.2, 1.0);
        }
    }

    /** The grey clone of heavy attack 0: flung from the assassin to {@code at} (the way the assassin faces), where it stands facing {@code yawDegrees}; the oldest of two is taken away when there are two already. */
    static Ghost standOne(MoveKit kit, Location at, double yawDegrees, int lifeTicks) {
        Player owner = kit.player;
        List<Ghost> list = GHOSTS.computeIfAbsent(owner.getUniqueId(), id -> new ArrayList<>());
        while (standing(owner) >= MAX_STANDING) {
            Ghost oldest = null;
            for (Ghost ghost : list) if (!ghost.swap && ghost.leaving < 0 && (oldest == null || ghost.age > oldest.age)) oldest = ghost;
            if (oldest == null) break;
            remove(oldest, true);
            list.remove(oldest);
        }
        // (it is flung from the assassin to the place: it slides there in 6 ticks)
        Ghost ghost = new Ghost(owner, false, Clones.Look.ghost(), owner.getLocation(), yawDegrees, lifeTicks, 0);
        ghost.kit = kit;
        ghost.slideFrom = owner.getLocation().clone();
        ghost.slideTo = at.clone();
        ghost.slideTicks = 6;
        list.add(ghost);
        MIMIC.put(owner.getUniqueId(), true);
        ensureTask(kit.plugin());
        kit.sound(at, Sfx.WIND, 0.6f, 1.5f);
        return ghost;
    }

    /**
     * A clone that stands as {@link #standOne} does and also strikes the nearest enemy within {@code reach} every {@code everyTicks} (rounded up to
     * an even number: the clones move every 2 ticks), for {@code rate}: the decoy of 中 (the redesign of 2026-11-02).
     */
    static Ghost standDecoy(MoveKit kit, Location at, double yawDegrees, int lifeTicks, double rate, int everyTicks, double reach) {
        Ghost ghost = standOne(kit, at, yawDegrees, lifeTicks);
        ghost.hitRate = Math.max(0.0, rate);
        ghost.hitEvery = Math.max(2, everyTicks + everyTicks % 2);
        ghost.hitReach = Math.max(0.5, reach);
        return ghost;
    }

    /**
     * The skill's clones: every clone that stands is destroyed, and three are flung forward (to the front, and 32 degrees each side, six blocks) for {@code lifeTicks}; each
     * copies the next {@code swap-copies} (10) attacks.
     */
    static void launchSwap(MoveKit kit, int lifeTicks) {
        Player owner = kit.player;
        clearAll(owner);
        List<Ghost> list = GHOSTS.computeIfAbsent(owner.getUniqueId(), id -> new ArrayList<>());
        Vector facing = kit.facing();
        Location base = owner.getLocation();
        int copies = (int) Math.round(Math.max(1.0, Math.min(20.0, kit.number(AssassinMeleeCombo.PLAN, "swap-copies", 10.0))));
        for (int index = -1; index <= 1; index++) {
            double turn = Math.toRadians(32.0 * index);
            Vector direction = new Vector(facing.getX() * Math.cos(turn) - facing.getZ() * Math.sin(turn), 0.0, facing.getX() * Math.sin(turn) + facing.getZ() * Math.cos(turn));
            Location target = kit.ground(base.clone().add(direction.clone().multiply(6.5)));
            Ghost ghost = new Ghost(owner, true, Clones.Look.swap(false), base, Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())), lifeTicks, copies);
            ghost.slideFrom = base.clone();
            ghost.slideTo = target;
            ghost.slideTicks = 8;
            list.add(ghost);
        }
        ensureTask(kit.plugin());
    }

    // ---- what they do -----------------------------------------------------------------------------------------------------------------

    private static LivingEntity nearest(MoveKit kit, Location from, double radius) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity target : kit.around(from, radius)) {
            double distance = target.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }

    private static void face(Ghost ghost, Location toward) {
        double dx = toward.getX() - ghost.pos.getX();
        double dz = toward.getZ() - ghost.pos.getZ();
        if (dx * dx + dz * dz > 1.0E-6) ghost.yawDegrees = Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static void swing(Ghost ghost) {
        ghost.pose = 1.0;
        if (ghost.body != null) ghost.body.swing();
    }

    /** One hit of a clone on {@code victim} for {@code rate}: it turns to face the enemy, swings, and the enemy is remembered as hurt by a clone. */
    private static boolean cloneHit(MoveKit kit, Ghost ghost, LivingEntity victim, double rate) {
        face(ghost, victim.getLocation());
        swing(ghost);
        boolean hit = kit.hit(victim, rate);
        if (hit) DAMAGED.computeIfAbsent(kit.player.getUniqueId(), id -> new HashSet<>()).add(victim.getUniqueId());
        return hit;
    }

    /** Takes the "make the clones copy it" flag: true once after a clone was made, while one stands. */
    static boolean takeMimic(Player owner) {
        if (!Boolean.TRUE.equals(MIMIC.get(owner.getUniqueId()))) return false;
        boolean any = false;
        for (Ghost ghost : GHOSTS.getOrDefault(owner.getUniqueId(), List.of())) if (!ghost.swap && ghost.leaving < 0) any = true;
        MIMIC.remove(owner.getUniqueId());
        return any;
    }

    /**
     * The standing clones make heavy attack {@code index} (2 - 4) too (once), each for 30% of {@code rate} a hit: 2 spins for 1.2 s, 3 runs through the line ahead and back, 4
     * strikes the nearest enemy.
     */
    static void mimic(MoveKit kit, int index, double rate) {
        double part = rate * 0.3;
        for (Ghost ghost : new ArrayList<>(GHOSTS.getOrDefault(kit.player.getUniqueId(), List.of()))) {
            if (ghost.swap || ghost.leaving >= 0) continue;
            kit.sound(ghost.pos, Sfx.WIND, 0.5f, 1.4f);
            switch (index) {
                case 2 -> kit.every(25, age -> {
                    if (ghost.leaving >= 0) return true;
                    if (age % 3 == 0) {
                        swing(ghost);
                        kit.fx(ghost.pos).flatRing(ghost.pos.clone().add(0.0, 0.2 + (age % 6) * 0.25, 0.0), 2.6, 36, ghost.look.gradient(), 0.5, 0.2, 0.9);
                        for (LivingEntity victim : kit.around(ghost.pos, 3.4)) cloneHit(kit, ghost, victim, part);
                    }
                    ghost.yawDegrees += 40.0;
                    return false;
                });
                case 3 -> {
                    LivingEntity target = nearest(kit, ghost.pos, 9.0);
                    Vector heading = target != null ? target.getLocation().toVector().subtract(ghost.pos.toVector()).setY(0.0) : kit.facing();
                    if (heading.lengthSquared() < 1.0E-6) heading = kit.facing();
                    Vector direction = heading.normalize();
                    face(ghost, ghost.pos.clone().add(direction));
                    Location from = ghost.pos.clone().add(0.0, 1.0, 0.0);
                    kit.line(from, from.clone().add(direction.clone().multiply(6.0)), 0.2, 1.0, 1.2);
                    kit.later(2L, () -> {
                        swing(ghost);
                        for (LivingEntity victim : kit.corridor(from, direction, 6.0, 1.4)) cloneHit(kit, ghost, victim, part);
                    });
                    kit.later(7L, () -> {
                        face(ghost, ghost.pos.clone().subtract(direction));
                        swing(ghost);
                        for (LivingEntity victim : kit.corridor(from.clone().add(direction.clone().multiply(6.0)), direction.clone().multiply(-1.0), 6.0, 1.4)) cloneHit(kit, ghost, victim, part);
                    });
                }
                default -> {
                    LivingEntity target = nearest(kit, ghost.pos, 10.0);
                    if (target == null) continue;
                    face(ghost, target.getLocation());
                    ghost.slideFrom = ghost.pos.clone();
                    ghost.slideTo = target.getLocation().clone().subtract(target.getLocation().toVector().subtract(ghost.pos.toVector()).setY(0.0).normalize().multiply(1.2));
                    ghost.slideTicks = 4;
                    ghost.slideAge = 0;
                    kit.later(4L, () -> {
                        kit.slash("drop", 2.8, 160.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.3, 0.16, 0.0);
                        if (target.isValid() && !target.isDead()) cloneHit(kit, ghost, target, part);
                    });
                }
            }
        }
    }

    /**
     * The swing of an attack of the assassin during the item swap skill, copied: each of its clones that still has copies left strikes the enemy nearest to it within ten
     * blocks (turning to it) for 5% of the weapon's damage ({@code swap-copy-rate}); with the last copy (the tenth) the clone is destroyed.
     */
    static void copyAttack(MoveKit kit, int heavyIndex) {
        int delay = 1;
        double rate = kit.number(AssassinMeleeCombo.PLAN, "swap-copy-rate", 0.05);
        for (Ghost ghost : new ArrayList<>(GHOSTS.getOrDefault(kit.player.getUniqueId(), List.of()))) {
            if (!ghost.swap || ghost.copies <= 0 || ghost.leaving >= 0 || ghost.age < 8) continue;
            LivingEntity target = nearest(kit, ghost.pos, 10.0);
            if (target == null) continue;
            ghost.copies--;
            kit.later(delay++, () -> {
                if (ghost.leaving >= 0 || !target.isValid() || target.isDead()) return;
                if (heavyIndex == 1) {
                    // heavy 1: the clone throws a shuriken of its own at the enemy (it bounces like the assassin's)
                    face(ghost, target.getLocation());
                    swing(ghost);
                    Location from = ghost.pos.clone().add(0.0, 1.2, 0.0);
                    Vector toward = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector().subtract(from.toVector());
                    if (toward.lengthSquared() > 1.0E-6) {
                        Blades.bounce(kit, from.add(toward.clone().normalize().multiply(0.6)), toward, kit.number(AssassinMeleeCombo.PLAN, "shuriken-speed", 2.0),
                                (int) Math.round(kit.number(AssassinMeleeCombo.PLAN, "shuriken-bounces", 3.0)), Math.max(0.0, Math.min(0.95, kit.number(AssassinMeleeCombo.PLAN, "shuriken-decay", 0.5))),
                                rate, kit.number(AssassinMeleeCombo.PLAN, "shuriken-range", 12.0),
                                victim -> DAMAGED.computeIfAbsent(kit.player.getUniqueId(), id -> new HashSet<>()).add(victim.getUniqueId()));
                    }
                    kit.sound(ghost.pos, Sfx.WIND, 0.5f, 1.8f);
                    if (ghost.copies <= 0) ghost.leaving = 6;
                    return;
                }
                cloneHit(kit, ghost, target, rate);
                Clones.lineCut(kit.player, ghost.look.gradient(), target.getLocation(), Math.random() * Math.PI, true);
                kit.sound(ghost.pos, Sfx.WIND, 0.5f, 1.8f);
                if (ghost.copies <= 0) ghost.leaving = 6;
            });
        }
    }

    // ---- the re-activations of heavy 0 ------------------------------------------------------------------------------------------------

    /** Heavy 0 was made: its clone stands, and 強1 can be thrown again, twice. */
    static void beginCast(MoveKit kit) {
        CAST.put(kit.player.getUniqueId(), new int[]{0, org.bukkit.Bukkit.getCurrentTick()});
    }

    private static List<Ghost> standingOnes(Player owner) {
        List<Ghost> found = new ArrayList<>();
        for (Ghost ghost : GHOSTS.getOrDefault(owner.getUniqueId(), List.of())) if (!ghost.swap && ghost.leaving < 0) found.add(ghost);
        return found;
    }

    /** True when 強1 (heavy 0) is a re-activation of the one before now: a clone of it still stands, and less than two were made. */
    static boolean canRecast(Player owner) {
        int[] cast = CAST.get(owner.getUniqueId());
        if (cast == null) return false;
        if (cast[0] >= 2 || standingOnes(owner).isEmpty()) {
            CAST.remove(owner.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * The re-activation: the first makes one more clone (to the side of the first), the second sends every standing clone charging forward (6 blocks at the nearest enemy, or ahead),
     * {@code clone-charge-rate} (15%) of {@code boost} times the weapon's damage to everyone on its way, and they go. A press too soon after the last does nothing (and is used up).
     */
    static void recast(MoveKit kit, double boost) {
        Player owner = kit.player;
        int[] cast = CAST.get(owner.getUniqueId());
        if (cast == null) return;
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now - cast[1] < RECAST_GAP) return;
        cast[1] = now;
        cast[0]++;
        if (cast[0] >= 2) CAST.remove(owner.getUniqueId());
        if (cast[0] == 1) {
            Vector facing = kit.facing();
            double turn = Math.toRadians(-38.0);
            Vector side = new Vector(facing.getX() * Math.cos(turn) - facing.getZ() * Math.sin(turn), 0.0, facing.getX() * Math.sin(turn) + facing.getZ() * Math.cos(turn));
            Location at = kit.ground(owner.getLocation().add(side.multiply(kit.number(AssassinMeleeCombo.PLAN, "stand-clone-distance", 5.0))));
            double yaw = Math.toDegrees(Math.atan2(-facing.getX(), facing.getZ()));
            standOne(kit, at, yaw, (int) Math.round(kit.number(AssassinMeleeCombo.PLAN, "stand-clone-seconds", 4.5) * 20.0));
            // (the second clone does not start the "copy the next heavy attack" over: it was already on)
            return;
        }
        charge(kit, kit.number(AssassinMeleeCombo.PLAN, "clone-charge-rate", 0.15) * boost);
    }

    /** Every standing clone runs six blocks toward the nearest enemy (ahead when there is none) and cuts everyone in its way for {@code rate}; then it goes. */
    private static void charge(MoveKit kit, double rate) {
        for (Ghost ghost : standingOnes(kit.player)) {
            kit.sound(ghost.pos, Sfx.WIND_BURST, 0.6f, 1.3f);
            LivingEntity target = nearest(kit, ghost.pos, 9.0);
            Vector heading = target != null ? target.getLocation().toVector().subtract(ghost.pos.toVector()).setY(0.0) : kit.facing();
            if (heading.lengthSquared() < 1.0E-6) heading = kit.facing();
            Vector direction = heading.normalize();
            face(ghost, ghost.pos.clone().add(direction));
            Location from = ghost.pos.clone().add(0.0, 1.0, 0.0);
            ghost.kit = null;
            ghost.slideFrom = ghost.pos.clone();
            ghost.slideTo = kit.ground(ghost.pos.clone().add(direction.clone().multiply(6.0)));
            ghost.slideTicks = 6;
            ghost.slideAge = 0;
            kit.line(from, from.clone().add(direction.clone().multiply(6.0)), 0.2, 1.0, 1.2);
            kit.later(3L, () -> {
                swing(ghost);
                for (LivingEntity victim : kit.corridor(from, direction, 6.5, 1.3)) cloneHit(kit, ghost, victim, rate);
            });
            kit.later(7L, () -> {
                if (ghost.leaving < 0) ghost.leaving = 6;
            });
        }
    }

    // ---- the clones take the blow ------------------------------------------------------------------------------------------------------------

    /**
     * A blow of {@code damage} is about to land on the assassin during the item swap skill: when it is {@code swap-guard-share} (40%) of their life or more and clones of the skill
     * remain, they are all destroyed and the blow is nullified. True when that happened (the caller does not apply the damage).
     */
    static boolean sacrifice(Player owner, double damage, double maxHp, double share) {
        if (damage < maxHp * share || maxHp <= 0.0) return false;
        List<Ghost> list = GHOSTS.get(owner.getUniqueId());
        if (list == null) return false;
        boolean any = false;
        for (Ghost ghost : new ArrayList<>(list)) {
            if (!ghost.swap || ghost.leaving >= 0) continue;
            any = true;
            remove(ghost, true);
            list.remove(ghost);
        }
        if (list.isEmpty()) GHOSTS.remove(owner.getUniqueId());
        if (!any) return false;
        Location at = owner.getLocation();
        owner.getWorld().playSound(at, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.5f);
        owner.getWorld().playSound(at, org.bukkit.Sound.ITEM_SHIELD_BREAK, org.bukkit.SoundCategory.PLAYERS, 0.8f, 0.8f);
        Fx fx = Fx.of(owner, at.getWorld(), at);
        fx.sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.1, 0.0, 30, Clones.RED_BLACK, 1.2);
        fx.flatRing(at.clone().add(0.0, 0.1, 0.0), 1.6, 30, Clones.RED_BLACK, 0.8, 0.2, 1.1);
        return true;
    }

    /**
     * The assassin dashed {@code delta} (a vector on the ground) during the item swap skill: every clone of the skill moves toward the place the assassin looks at, as far as the dash went, but only once in half a second (a dash
     * inside that time moves nothing). A clone does not leave the circle the assassin is held in.
     */
    static void followDash(MoveKit kit, Vector delta) {
        Player owner = kit.player;
        int now = org.bukkit.Bukkit.getCurrentTick();
        Integer last = MOVED.get(owner.getUniqueId());
        if (last != null && now - last < MOVE_GAP_TICKS) return;
        Location center = kit.frenzyCenter();
        double radius = kit.plugin().getConfig().getDouble("class-effects.assassin.frenzy-radius", 12.0);
        boolean any = false;
        Location aim = kit.aim();
        for (Ghost ghost : GHOSTS.getOrDefault(owner.getUniqueId(), List.of())) {
            if (!ghost.swap || ghost.leaving >= 0) continue;
            // every clone goes toward where the assassin looks (as far as the dash went, and no further than the place looked at)
            Vector toward = aim.toVector().subtract(ghost.pos.toVector()).setY(0.0);
            double reach = Math.min(delta.length(), toward.length());
            Location to = toward.lengthSquared() < 1.0E-6 ? ghost.pos.clone() : ghost.pos.clone().add(toward.normalize().multiply(reach));
            if (center != null && center.getWorld() == to.getWorld()) {
                double dx = to.getX() - center.getX();
                double dz = to.getZ() - center.getZ();
                double out = Math.hypot(dx, dz);
                if (out > radius - 0.5) {
                    to.setX(center.getX() + dx / out * (radius - 0.5));
                    to.setZ(center.getZ() + dz / out * (radius - 0.5));
                }
            }
            to = kit.ground(to);
            face(ghost, to);
            ghost.slideFrom = ghost.pos.clone();
            ghost.slideTo = to;
            ghost.slideTicks = 6;
            ghost.slideAge = 0;
            any = true;
        }
        if (any) MOVED.put(owner.getUniqueId(), now);
    }

    // ---- what they are, for the echo ------------------------------------------------------------------------------------------------

    static Set<UUID> hurtByClones(Player owner) {
        return DAMAGED.getOrDefault(owner.getUniqueId(), Set.of());
    }

    // ---- ending them --------------------------------------------------------------------------------------------------------------------

    /** Destroys every clone of the player (the skill is pressed: the clones that stood are gone before the three are flung). */
    static void clearAll(Player owner) {
        List<Ghost> list = GHOSTS.remove(owner.getUniqueId());
        if (list != null) for (Ghost ghost : list) remove(ghost, true);
        MIMIC.remove(owner.getUniqueId());
        TRANSIENT.remove(owner.getUniqueId());
        CAST.remove(owner.getUniqueId());
        MOVED.remove(owner.getUniqueId());
    }

    /** The item swap skill ends: its clones go, and the enemies they hurt are forgotten. */
    static void endEffect(Player owner) {
        List<Ghost> list = GHOSTS.get(owner.getUniqueId());
        if (list != null) {
            for (Ghost ghost : new ArrayList<>(list)) {
                if (!ghost.swap) continue;
                remove(ghost, true);
                list.remove(ghost);
            }
            if (list.isEmpty()) GHOSTS.remove(owner.getUniqueId());
        }
        DAMAGED.remove(owner.getUniqueId());
        MOVED.remove(owner.getUniqueId());
    }

    /** The plugin stops: every clone goes. */
    static void shutdown() {
        for (List<Ghost> list : GHOSTS.values()) for (Ghost ghost : list) remove(ghost, false);
        GHOSTS.clear();
        MIMIC.clear();
        CAST.clear();
        MOVED.clear();
        DAMAGED.clear();
        TRANSIENT.clear();
        if (task != null) task.cancel();
        task = null;
    }

    // ---- the tick --------------------------------------------------------------------------------------------------------------------------

    private static void step() {
        for (java.util.Iterator<Map.Entry<UUID, List<Ghost>>> owners = GHOSTS.entrySet().iterator(); owners.hasNext(); ) {
            Map.Entry<UUID, List<Ghost>> entry = owners.next();
            List<Ghost> list = entry.getValue();
            for (java.util.Iterator<Ghost> iterator = list.iterator(); iterator.hasNext(); ) {
                Ghost ghost = iterator.next();
                ghost.age += 2;
                if (ghost.leaving >= 0) ghost.leaving -= 2;
                if (!ghost.owner.isOnline() || ghost.age >= ghost.life || (ghost.leaving >= 0 && ghost.leaving <= 0)) {
                    remove(ghost, true);
                    iterator.remove();
                    continue;
                }
                if (ghost.slideTo != null && ghost.slideAge < ghost.slideTicks) {
                    ghost.slideAge += 2;
                    double t = Math.min(1.0, ghost.slideAge / (double) ghost.slideTicks);
                    double eased = 1.0 - (1.0 - t) * (1.0 - t);
                    ghost.pos = new Location(ghost.slideFrom.getWorld(), ghost.slideFrom.getX() + (ghost.slideTo.getX() - ghost.slideFrom.getX()) * eased,
                            ghost.slideFrom.getY() + (ghost.slideTo.getY() - ghost.slideFrom.getY()) * eased, ghost.slideFrom.getZ() + (ghost.slideTo.getZ() - ghost.slideFrom.getZ()) * eased);
                }
                ghost.pose = Math.max(0.0, ghost.pose - 0.5);
                // a standing clone turns to the nearest enemy
                if (ghost.kit != null && !ghost.swap && ghost.leaving < 0 && ghost.age % 4 == 0) {
                    LivingEntity enemy = nearest(ghost.kit, ghost.pos, 12.0);
                    if (enemy != null) face(ghost, enemy.getLocation());
                }
                // a decoy strikes the nearest enemy near it, once it has landed
                if (ghost.kit != null && ghost.hitRate > 0.0 && !ghost.swap && ghost.leaving < 0 && ghost.slideAge >= ghost.slideTicks && ghost.age % ghost.hitEvery == 0) {
                    LivingEntity enemy = nearest(ghost.kit, ghost.pos, ghost.hitReach);
                    if (enemy != null) cloneHit(ghost.kit, ghost, enemy, ghost.hitRate);
                }
                double fade = ghost.leaving >= 0 ? Math.max(0.1, ghost.leaving / 6.0) : Math.min(1.0, ghost.age / 6.0);
                if (!ghost.bodyTried) {
                    ghost.bodyTried = true;
                    ghost.body = CloneBody.entity(JavaPluginHolder.plugin, ghost.owner, new Location(ghost.pos.getWorld(), ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ(), (float) ghost.yawDegrees, 0.0f),
                            64.0, viewer -> ParticleFx.canSee(viewer, ghost.owner), ghost.look.tint());
                }
                ParticleFx.mark(ghost.owner);
                double yaw = Math.toRadians(ghost.yawDegrees);
                if (ghost.body != null) {
                    ghost.body.moveTo(ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ(), ghost.yawDegrees);
                    Clones.trail(ghost.owner, ghost.look.gradient(), ghost.pos.getWorld(), ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ(), yaw, ghost.pose, fade * 0.6, ghost.look.quality());
                } else {
                    Clones.figure(ghost.owner, ghost.look.gradient(), ghost.pos.getWorld(), ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ(), yaw, ghost.pose, fade, ghost.look.quality(), ghost.look.tint() == CloneBody.Tint.GRAY);
                }
                // a faint ring at the feet
                if (ghost.age % 10 == 0) Fx.of(ghost.owner, ghost.pos.getWorld(), ghost.pos).flatRing(ghost.pos.clone().add(0.0, 0.1, 0.0), 0.8, 18, ghost.look.gradient(), 0.5, 0.1, 0.9);
            }
            if (list.isEmpty()) owners.remove();
        }
    }

    /** The plugin the registry draws for (set when the first clone is made). */
    static final class JavaPluginHolder {
        static JavaPlugin plugin;

        private JavaPluginHolder() {
        }
    }
}
