package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * The assassin's skills with a melee weapon, reworked by nekorozv (t161). Every number is under {@code combat.combo.assassin_melee} (the cooldowns
 * under {@code combat.skill-slots.cooldown-seconds}); the look is new: steel and shadow (violet), and sounds of their own ({@link Sfx}
 * SHADOW_*). With another kind of weapon the assassin's skills are the template's as before ({@link SlotMoves}).
 * <ul>
 *   <li>小 (Q → left): three charges (3 s each while one is left; 8 s for all once none is left). Used again soon after
 *       ({@code small-chain-seconds}), it goes on: 1 a level cut from right to left, 2 one from left to right, 3 two cuts from above and behind
 *       on each side meeting at one point ahead. Every use is copied by the clones that stand.</li>
 *   <li>中 (Q → right, 12 s): three cuts at random angles ahead, then a slanting cut (a cross cut with a weapon in the off hand). Its re-activation
 *       (within {@code medium-recast-seconds}): a dash ahead (nothing hurts the assassin on the way) that cuts weakly whoever it passes, and a
 *       cross cut where it ends. Only the re-activation is copied by the clones.</li>
 *   <li>大 (F → F, 24 s): invisible for 4 s, a clone left where the assassin stood; the mobs that were after the assassin go to the clone. 1.5 s
 *       later, round the assassin and every clone that stands, a level cut at a random angle is made at a random point within 3 blocks every 3
 *       ticks for 2 s, cutting the enemies on its line (the assassin cannot move and takes no damage meanwhile); then those clones go. Not
 *       copied: it is the clones' own. While invisible, the next normal attack or skill (not util) does 1.5 times the damage and ends the
 *       invisibility (t165, {@link #ambushStrike}).</li>
 *   <li>util (F, 5 s): a clone stays where the assassin was (5 s) and the assassin leaps back.</li>
 *   <li>ULT (SATUJIN): while it lasts, the weapon skill's key is a weak re-activation of 中 (a dash and a cross cut), every 2 ticks; the weapon's
 *       skill is not used (its cooldown stays as it is). The rest of SATUJIN is as it was.</li>
 * </ul>
 * A clone that stands copies a skill at most {@code clone-copies} (3) times, each for {@code clone-copy-share} of its damage, and goes after the last.
 */
public final class AssassinSkills {

    static final String PLAN = AssassinMeleeCombo.PLAN;
    /** The skills' colors: deep indigo, violet, pale lilac. */
    static final Gradient SHADOW = Gradient.of(Color.fromRGB(32, 18, 64), Color.fromRGB(128, 70, 220), Color.fromRGB(226, 212, 255));
    private static final List<Color> SHADOW_SLASH = List.of(Color.fromRGB(40, 20, 80), Color.fromRGB(140, 80, 230), Color.fromRGB(235, 222, 255));
    private static final List<Color> RED_BLACK_SLASH = List.of(Color.fromRGB(90, 0, 8), Color.fromRGB(230, 25, 30), Color.fromRGB(255, 120, 100));

    /** Hides a player from the mobs for some milliseconds, the mobs after them going to a place (the mob service's; set by the plugin). */
    public interface Hider {
        void hide(Player player, Location lure, long millis);

        /** The player is seen again (the invisibility of 大 ended early). */
        default void reveal(Player player) {
        }
    }

    private static Hider hider = (player, lure, millis) -> {
    };
    /** {the time of the last 小, its cut 1 - 3} of each assassin. */
    private static final Map<UUID, long[]> SMALL_CHAIN = new HashMap<>();
    /** The tick each assassin may dash with the weapon skill's key again during SATUJIN. */
    private static final Map<UUID, Integer> ULT_DASH_READY = new HashMap<>();
    /**
     * The ambush of 大 (t165): {until when it waits for the next attack (the invisibility), until when the attack it went into hits harder (0:
     * not yet), the multiplier} of each assassin.
     */
    private static final Map<UUID, double[]> AMBUSH = new java.util.concurrent.ConcurrentHashMap<>();

    private AssassinSkills() {
    }

    public static void setHider(Hider value) {
        hider = value == null ? (player, lure, millis) -> {
        } : value;
    }

    // ---- registering --------------------------------------------------------------------------------------------------------

    /** The assassin's skill {@code slot}: the reworked one with a melee weapon of the class's combo, else the template's heavy attack ({@code heavyOf}). */
    static SkillSlots.Move move(AttackService attack, SkillSlots.Slot slot, ToIntFunction<AttackType> heavyOf) {
        return new SkillSlots.Move() {
            @Override
            public boolean use(Player player, RpgItemDefinition weapon) {
                if (weapon == null) return false;
                if (!reworked(attack, player, weapon)) return attack.slotHeavy(player, weapon, heavyOf.applyAsInt(weapon.attackType()));
                MoveKit kit = attack.skillKit(player, weapon);
                if (kit == null) return false;
                look(kit);
                // (the clones of SATUJIN copy every attack, as they did)
                if (kit.frenzy() > 0) AssassinClones.copyAttack(kit, -1);
                switch (slot) {
                    case SMALL -> small(kit);
                    case MEDIUM -> medium(kit, attack.offHandWeapon(player));
                    case LARGE -> large(kit);
                    case UTIL -> util(kit);
                    default -> {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean recast(Player player, RpgItemDefinition weapon) {
                if (slot != SkillSlots.Slot.MEDIUM || weapon == null || !reworked(attack, player, weapon)) return false;
                MoveKit kit = attack.skillKit(player, weapon);
                if (kit == null) return false;
                look(kit);
                if (kit.frenzy() > 0) AssassinClones.copyAttack(kit, -1);
                rush(kit, 1.0, true);
                return true;
            }
        };
    }

    /** The charges and re-activation of the assassin's skills: 小 has charges and 中 a re-activation with a melee weapon; the others none. */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot, AttackType kind) {
        if (kind != AttackType.MELEE) return SkillSlots.Mechanics.NONE;
        String base = "combat.combo." + PLAN + ".";
        return switch (slot) {
            case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 3.0)), 0.0,
                    config.getDouble(base + "small-charge-seconds", 3.0), config.getDouble(base + "small-empty-seconds", 8.0));
            case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 3.0), 0.0, 0.0);
            default -> SkillSlots.Mechanics.NONE;
        };
    }

    private static boolean reworked(AttackService attack, Player player, RpgItemDefinition weapon) {
        return weapon.attackType() == AttackType.MELEE && attack.hasClassCombo(player, weapon);
    }

    /** The look: steel and shadow, or (during SATUJIN) red and black. */
    private static void look(MoveKit kit) {
        if (kit.frenzy() > 0) {
            kit.palette(Clones.RED_BLACK);
            kit.slashColors(RED_BLACK_SLASH);
        } else {
            kit.palette(SHADOW);
            kit.slashColors(SHADOW_SLASH);
        }
    }

    /** How much stronger the blows are in SATUJIN (1 when not). */
    private static double boost(MoveKit kit) {
        int frenzy = kit.frenzy();
        return frenzy >= 2 ? 1.35 : frenzy == 1 ? 1.2 : 1.0;
    }

    private static double number(MoveKit kit, String key, double fallback) {
        return kit.number(PLAN, key, fallback);
    }

    private static Vector flat(Vector vector) {
        Vector flat = vector.clone().setY(0.0);
        return flat.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : flat.normalize();
    }

    /** The yaw (degrees, the game's) of a direction on the ground. */
    private static double yawOf(Vector direction) {
        return Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    /** The turn {@link Clones#lineCut} takes for a level cut across {@code facing}. */
    private static double across(Vector facing) {
        return Math.atan2(facing.getX(), -facing.getZ());
    }

    // ---- 小 ----------------------------------------------------------------------------------------------------------------

    private static void small(MoveKit kit) {
        UUID id = kit.player.getUniqueId();
        long now = System.currentTimeMillis();
        long[] chain = SMALL_CHAIN.get(id);
        long window = Math.round(number(kit, "small-chain-seconds", 2.5) * 1000.0);
        int cut = chain != null && now - chain[0] <= window ? (int) (chain[1] % 3) + 1 : 1;
        SMALL_CHAIN.put(id, new long[]{now, cut});
        double rate = number(kit, cut == 3 ? "small-rate-3" : "small-rate", cut == 3 ? 0.45 : 0.55) * boost(kit);
        double reach = number(kit, "small-reach", 3.2);
        Location feet = kit.player.getLocation();
        if (cut < 3) {
            // a level cut: 1 from the right to the left, 2 from the left to the right
            kit.slash("arc", reach, 160.0, 90.0, cut == 1 ? 0.0 : 180.0, 1.5, 0.35, 0.0, 1.15, 0.12, 0.0);
            kit.sound(feet, Sfx.SHADOW_SLICE, 0.8f, cut == 1 ? 1.0f : 1.12f);
            for (LivingEntity target : kit.cone(reach, 80.0)) kit.hit(target, rate);
        } else {
            // two cuts from above and behind, one on each side, meeting at one point ahead
            Vector facing = flat(kit.facing());
            Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
            Location point = feet.clone().add(facing.clone().multiply(2.6)).add(0.0, 1.0, 0.0);
            for (int side = -1; side <= 1; side += 2) {
                Location from = feet.clone().subtract(facing.clone().multiply(0.8)).add(right.clone().multiply(1.3 * side)).add(0.0, 2.6, 0.0);
                kit.line(from, point, 0.15, 1.0, 1.25);
                kit.slash("line", 2.4, 70.0, side < 0 ? 40.0 : 140.0, side < 0 ? 0.0 : 180.0, 1.4, -0.2, 0.0, 1.1, 0.1, 0.03);
            }
            kit.sound(feet, Sfx.SHADOW_SLICE, 0.8f, 0.9f);
            kit.later(3L, () -> {
                kit.burst(point, 0.9);
                kit.sound(point, Sfx.SHADOW_CROSS, 0.8f, 1.1f);
                for (LivingEntity target : kit.around(point, 1.8)) kit.hit(target, rate);
            });
            kit.later(5L, () -> {
                for (LivingEntity target : kit.around(point, 1.8)) kit.hit(target, rate);
            });
        }
        // every clone that stands makes the same cut (from where it stands, at the enemy nearest to it)
        double copy = rate * number(kit, "clone-copy-share", 0.5);
        int delay = 2;
        for (AssassinClones.Ghost ghost : AssassinClones.copiers(kit.player)) {
            AssassinClones.spend(ghost);
            kit.later(delay++, () -> copySmall(kit, ghost, cut, copy, reach));
        }
    }

    private static void copySmall(MoveKit kit, AssassinClones.Ghost ghost, int cut, double rate, double reach) {
        LivingEntity target = AssassinClones.nearest(kit, ghost.pos, 8.0);
        Vector facing = target == null ? flat(kit.facing()) : flat(target.getLocation().toVector().subtract(ghost.pos.toVector()));
        AssassinClones.face(ghost, ghost.pos.clone().add(facing));
        AssassinClones.swing(ghost);
        if (cut < 3) {
            Location center = ghost.pos.clone().add(facing.clone().multiply(1.5));
            Clones.lineCut(kit.player, SHADOW, center, across(facing), true);
            kit.sound(ghost.pos, Sfx.SHADOW_SLICE, 0.5f, 1.2f);
            for (LivingEntity victim : kit.around(center, reach * 0.75)) AssassinClones.cloneHit(kit, ghost, victim, rate);
            return;
        }
        Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
        Location point = target != null ? target.getLocation().add(0.0, 1.0, 0.0) : ghost.pos.clone().add(facing.clone().multiply(2.6)).add(0.0, 1.0, 0.0);
        for (int side = -1; side <= 1; side += 2) {
            kit.line(ghost.pos.clone().subtract(facing.clone().multiply(0.8)).add(right.clone().multiply(1.3 * side)).add(0.0, 2.6, 0.0), point, 0.15, 1.0, 1.1);
        }
        kit.later(3L, () -> {
            kit.burst(point, 0.7);
            for (LivingEntity victim : kit.around(point, 1.8)) AssassinClones.cloneHit(kit, ghost, victim, rate);
        });
    }

    // ---- 中 ----------------------------------------------------------------------------------------------------------------

    private static void medium(MoveKit kit, boolean cross) {
        double boost = boost(kit);
        double rate = number(kit, "medium-rate", 0.3) * boost;
        double finish = number(kit, "medium-finish-rate", 0.7) * boost;
        double reach = number(kit, "medium-reach", 3.3);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < 3; index++) {
            int which = index;
            double tilt = 20.0 + random.nextDouble() * 140.0;
            double roll = random.nextBoolean() ? 0.0 : 180.0;
            kit.later(index * 3L, () -> {
                kit.slash("arc", reach, 130.0, tilt, roll, 1.6, 0.35, 0.0, 1.05, 0.1, 0.0);
                kit.sound(kit.player.getLocation(), Sfx.SHADOW_SLICE, 0.7f, 1.05f + which * 0.1f);
                for (LivingEntity target : kit.cone(reach, 70.0)) kit.hit(target, rate);
            });
        }
        kit.later(10L, () -> {
            Location feet = kit.player.getLocation();
            if (cross) {
                // a weapon in the off hand: a cross cut (two slanting cuts at once)
                kit.slash("line", reach + 0.3, 110.0, 45.0, 0.0, 1.7, 0.3, 0.0, 1.25, 0.14, 0.0);
                kit.slash("line", reach + 0.3, 110.0, 135.0, 180.0, 1.7, 0.3, 0.0, 1.25, 0.14, 0.0);
                kit.sound(feet, Sfx.SHADOW_CROSS, 0.9f, 1.0f);
                double each = finish * number(kit, "medium-cross-share", 0.65);
                for (LivingEntity target : kit.cone(reach + 0.3, 60.0)) kit.hit(target, each);
                kit.later(2L, () -> {
                    for (LivingEntity target : kit.cone(reach + 0.3, 60.0)) kit.hit(target, each);
                });
            } else {
                kit.slash("line", reach + 0.3, 120.0, 45.0, 0.0, 1.7, 0.3, 0.0, 1.25, 0.14, 0.0);
                kit.sound(feet, Sfx.SHADOW_CROSS, 0.9f, 0.85f);
                for (LivingEntity target : kit.cone(reach + 0.3, 60.0)) kit.hit(target, finish);
            }
        });
    }

    /**
     * The re-activation of 中 (and, at {@code share}, SATUJIN's dash of the weapon skill's key): a dash ahead, nothing hurts the assassin on the
     * way, the enemies passed are cut weakly and a cross cut is made where it ends. {@code copied}: the clones that stand make it too.
     */
    private static void rush(MoveKit kit, double share, boolean copied) {
        double boost = boost(kit) * share;
        double pass = number(kit, "rush-pass-rate", 0.25) * boost;
        double crossRate = number(kit, "rush-cross-rate", 0.45) * boost;
        double power = number(kit, "rush-power", 1.05);
        int ticks = (int) Math.round(number(kit, "rush-ticks", 5.0));
        Location feet = kit.player.getLocation();
        Vector facing = flat(kit.facing());
        Location from = feet.clone().add(0.0, 1.0, 0.0);
        double length = power * ticks;
        List<LivingEntity> passed = kit.corridor(from, facing, length, 1.4);
        kit.invulnerable(ticks + 2);
        kit.sound(feet, Sfx.SHADOW_DASH, share < 1.0 ? 0.5f : 0.9f, 1.0f);
        kit.line(from, from.clone().add(facing.clone().multiply(length)), 0.1, 1.0, 1.3);
        // (the clones of SATUJIN follow a dash, as they did)
        if (kit.frenzy() > 0) AssassinClones.followDash(kit, facing.clone().multiply(length));
        kit.dash(facing, power, ticks, end -> {
            for (LivingEntity victim : passed) kit.hit(victim, pass);
            crossAt(kit, end, crossRate, share < 1.0 ? 0.6f : 0.9f);
        });
        if (!copied) return;
        double copy = number(kit, "clone-copy-share", 0.5);
        for (AssassinClones.Ghost ghost : AssassinClones.copiers(kit.player)) {
            AssassinClones.spend(ghost);
            ghostRush(kit, ghost, length, pass * copy, crossRate * copy);
        }
    }

    /** A cross cut at {@code at}: two slanting cuts, everyone within 2.6 blocks cut twice for {@code rate}. */
    private static void crossAt(MoveKit kit, Location at, double rate, float volume) {
        kit.slash("line", 3.0, 110.0, 45.0, 0.0, 1.2, 0.3, 0.0, 1.2, 0.13, 0.0);
        kit.slash("line", 3.0, 110.0, 135.0, 180.0, 1.2, 0.3, 0.0, 1.2, 0.13, 0.0);
        kit.burst(at.clone().add(0.0, 1.0, 0.0), 1.0);
        kit.sound(at, Sfx.SHADOW_CROSS, volume, 1.05f);
        for (LivingEntity target : kit.around(at, 2.6)) kit.hit(target, rate);
        kit.later(2L, () -> {
            for (LivingEntity target : kit.around(at, 2.6)) kit.hit(target, rate);
        });
    }

    /** A clone makes the re-activation of 中: it rushes at the enemy nearest to it (or the way the assassin faces) and cuts across where it stops. */
    private static void ghostRush(MoveKit kit, AssassinClones.Ghost ghost, double length, double pass, double crossRate) {
        LivingEntity target = AssassinClones.nearest(kit, ghost.pos, 9.0);
        Vector heading = target == null ? flat(kit.facing()) : flat(target.getLocation().toVector().subtract(ghost.pos.toVector()));
        Location from = ghost.pos.clone().add(0.0, 1.0, 0.0);
        Location to = kit.ground(ghost.pos.clone().add(heading.clone().multiply(length)));
        AssassinClones.face(ghost, ghost.pos.clone().add(heading));
        ghost.slideFrom = ghost.pos.clone();
        ghost.slideTo = to;
        ghost.slideTicks = 6;
        ghost.slideAge = 0;
        kit.line(from, from.clone().add(heading.clone().multiply(length)), 0.1, 1.0, 1.1);
        kit.sound(ghost.pos, Sfx.SHADOW_DASH, 0.5f, 1.2f);
        List<LivingEntity> passed = kit.corridor(from, heading, length, 1.4);
        kit.later(4L, () -> {
            AssassinClones.swing(ghost);
            for (LivingEntity victim : passed) AssassinClones.cloneHit(kit, ghost, victim, pass);
            Clones.lineCut(kit.player, SHADOW, to, across(heading) + Math.PI / 4.0, true);
            Clones.lineCut(kit.player, SHADOW, to, across(heading) - Math.PI / 4.0, true);
            kit.sound(to, Sfx.SHADOW_CROSS, 0.5f, 1.2f);
            for (LivingEntity victim : kit.around(to, 2.6)) AssassinClones.cloneHit(kit, ghost, victim, crossRate);
        });
    }

    // ---- 大 ----------------------------------------------------------------------------------------------------------------

    private static void large(MoveKit kit) {
        Player player = kit.player;
        Location feet = player.getLocation();
        int invisible = (int) Math.round(number(kit, "large-invisible-seconds", 4.0) * 20.0);
        int delay = (int) Math.round(number(kit, "large-delay-seconds", 1.5) * 20.0);
        int storm = (int) Math.round(number(kit, "large-storm-seconds", 2.0) * 20.0);
        int every = Math.max(1, (int) Math.round(number(kit, "large-storm-ticks", 3.0)));
        double radius = number(kit, "large-radius", 3.0);
        double rate = number(kit, "large-rate", 0.18) * boost(kit);
        double cloneRate = rate * number(kit, "large-clone-share", 1.0);
        // invisible, and a clone where the assassin stood: the mobs after the assassin go to it
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisible, 0, false, false, false));
        kit.sound(feet, Sfx.SHADOW_VANISH, 0.9f, 1.0f);
        kit.fx(feet).sphereShell(feet.clone().add(0.0, 1.0, 0.0), 0.9, 0.0, 30, kit.palette(), 1.1);
        Location spot = kit.ground(feet);
        AssassinClones.standOne(kit, spot, yawOf(flat(kit.facing())), delay + storm + 10);
        kit.sound(spot, Sfx.SHADOW_CLONE, 0.7f, 1.0f);
        hider.hide(player, spot, invisible * 50L);
        // the next normal attack or skill while invisible hits harder and ends the invisibility
        armAmbush(player.getUniqueId(), System.currentTimeMillis() + invisible * 50L, number(kit, "large-ambush-multiplier", 1.5));
        kit.later(delay, () -> {
            if (!player.isOnline() || player.isDead()) return;
            // the assassin and every clone that stands cut the enemies round them at random; the assassin is held and nothing hurts them
            List<AssassinClones.Ghost> clones = new ArrayList<>(AssassinClones.standingOnes(player));
            kit.invulnerable(storm + 2);
            hold(player, storm);
            kit.sound(player.getLocation(), Sfx.SHADOW_CROSS, 0.9f, 0.8f);
            kit.every(storm, age -> {
                Vector velocity = player.getVelocity();
                player.setVelocity(new Vector(0.0, Math.min(0.0, velocity.getY()), 0.0));
                if (age % every != 0) return false;
                randomCut(kit, null, player.getLocation(), radius, rate);
                for (AssassinClones.Ghost ghost : clones) {
                    if (ghost.leaving < 0) randomCut(kit, ghost, ghost.pos, radius, cloneRate);
                }
                kit.sound(player.getLocation(), Sfx.SHADOW_FLURRY, 0.7f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.3f);
                return false;
            });
            // the clones that took part go when it ends
            kit.later(storm, () -> {
                for (AssassinClones.Ghost ghost : clones) AssassinClones.dismiss(ghost);
            });
        });
    }

    /**
     * One random cut round {@code center} (t165): a level line at a random angle through a random point within {@code radius}; the enemies on
     * the line take it (a clone, {@code ghost}, turns and swings). True when it cut someone.
     */
    private static boolean randomCut(MoveKit kit, AssassinClones.Ghost ghost, Location center, double radius, double rate) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double distance = radius * Math.sqrt(random.nextDouble());
        double at = random.nextDouble() * Math.PI * 2.0;
        Location point = center.clone().add(Math.cos(at) * distance, 0.0, Math.sin(at) * distance);
        double yaw = random.nextDouble() * Math.PI;
        Vector along = new Vector(Math.cos(yaw), 0.0, Math.sin(yaw));
        double half = number(kit, "large-line-half", 2.6);
        Clones.lineCut(kit.player, kit.palette(), point, yaw, false);
        if (ghost != null) {
            AssassinClones.face(ghost, point);
            AssassinClones.swing(ghost);
        }
        boolean struck = false;
        for (LivingEntity victim : kit.corridor(point.clone().add(0.0, 1.0, 0.0).subtract(along.clone().multiply(half)), along, half * 2.0, 1.1)) {
            struck |= ghost == null ? kit.hit(victim, rate) : AssassinClones.cloneHit(kit, ghost, victim, rate);
        }
        return struck;
    }

    /** The player cannot walk nor jump for {@code ticks} (effects that end by themselves, so nothing stays if the server stops). */
    private static void hold(Player player, int ticks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 9, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 250, false, false, false));
    }

    // ---- util ---------------------------------------------------------------------------------------------------------------

    private static void util(MoveKit kit) {
        Player player = kit.player;
        Location feet = player.getLocation();
        int life = (int) Math.round(number(kit, "util-clone-seconds", 5.0) * 20.0);
        AssassinClones.standOne(kit, kit.ground(feet), yawOf(flat(kit.facing())), life);
        kit.sound(feet, Sfx.SHADOW_CLONE, 0.7f, 1.1f);
        kit.sound(feet, Sfx.SHADOW_STEP, 0.8f, 1.0f);
        kit.fx(feet).flatRing(feet.clone().add(0.0, 0.1, 0.0), 1.2, 24, kit.palette(), 0.8, 0.2, 1.0);
        kit.invulnerable(5);
        kit.hopBack(number(kit, "util-back-power", 0.85), number(kit, "util-back-up", 0.35));
    }

    // ---- ULT ----------------------------------------------------------------------------------------------------------------

    /**
     * The weapon skill's key of an assassin with a melee weapon of the combo during SATUJIN: a weak re-activation of 中 (once in {@code ult-dash-ticks},
     * 2), instead of the weapon's skill. True when the key is taken (also while waiting), false when it is the weapon's skill as usual.
     */
    public static boolean frenzyDash(AttackService attack, Player player, RpgItemDefinition weapon) {
        if (weapon == null || !"assassin".equals(attack.classIdOf(player)) || attack.assassinFrenzy(player) <= 0 || !reworked(attack, player, weapon)) return false;
        int tick = Bukkit.getCurrentTick();
        Integer ready = ULT_DASH_READY.get(player.getUniqueId());
        if (ready != null && tick < ready) return true;
        int gap = (int) Math.max(1L, Math.round(attack.plugin().getConfig().getDouble("combat.combo." + PLAN + ".ult-dash-ticks", 2.0)));
        ULT_DASH_READY.put(player.getUniqueId(), tick + gap);
        MoveKit kit = attack.skillKit(player, weapon);
        if (kit == null) return true;
        look(kit);
        rush(kit, number(kit, "ult-dash-share", 0.15), false);
        return true;
    }

    // ---- the ambush of 大 (t165) --------------------------------------------------------------------------------------------

    /** The player waits in ambush until {@code untilMillis}: the next attack hits {@code multiplier} times as hard. */
    static void armAmbush(UUID player, long untilMillis, double multiplier) {
        AMBUSH.put(player, new double[]{untilMillis, 0.0, multiplier});
    }

    /**
     * The player starts a normal attack or a skill (not util): while the invisibility of 大 lasts, this one hits harder (its hits for the next
     * {@code large-ambush-seconds}, 1.5 s: the time a skill takes) and the invisibility ends. Nothing when the player waits for no ambush.
     */
    public static void ambushStrike(Player player) {
        if (player == null || !takeAmbush(player.getUniqueId(), System.currentTimeMillis(), JavaPluginLookup.INSTANCE.number("large-ambush-seconds", 1.5))) return;
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        hider.reveal(player);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, org.bukkit.SoundCategory.PLAYERS, 0.8f, 0.7f);
    }

    /** The attack goes into the ambush the player waits in at {@code now}, if any: true then (it hits harder for {@code seconds}). */
    static boolean takeAmbush(UUID player, long now, double seconds) {
        double[] ambush = AMBUSH.get(player);
        if (ambush == null || ambush[1] > 0.0) return false;
        if (ambush[0] < now) {
            AMBUSH.remove(player);
            return false;
        }
        ambush[1] = now + seconds * 1000.0;
        return true;
    }

    /** What the player's damage is multiplied by now: the ambush's while the attack it went into lasts, else 1. */
    public static double ambushMultiplier(Player player) {
        return player == null ? 1.0 : ambushMultiplier(player.getUniqueId(), System.currentTimeMillis());
    }

    static double ambushMultiplier(UUID player, long now) {
        double[] ambush = AMBUSH.get(player);
        if (ambush == null || ambush[1] <= 0.0) return 1.0;
        if (ambush[1] < now) {
            AMBUSH.remove(player);
            return 1.0;
        }
        return ambush[2];
    }

    /** The config's numbers of the skills where no kit is at hand (set by {@link #setHider}'s caller with the plugin). */
    static final class JavaPluginLookup {
        static final JavaPluginLookup INSTANCE = new JavaPluginLookup();
        org.bukkit.plugin.java.JavaPlugin plugin;

        double number(String key, double fallback) {
            return plugin == null ? fallback : plugin.getConfig().getDouble("combat.combo." + PLAN + "." + key, fallback);
        }
    }

    public static void setPlugin(org.bukkit.plugin.java.JavaPlugin plugin) {
        JavaPluginLookup.INSTANCE.plugin = plugin;
    }

    /** A player leaves: what is kept of them goes. */
    public static void forget(UUID player) {
        SMALL_CHAIN.remove(player);
        ULT_DASH_READY.remove(player);
        AMBUSH.remove(player);
    }
}
