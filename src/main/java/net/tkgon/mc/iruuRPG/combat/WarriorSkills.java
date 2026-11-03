package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The warrior's own skills (t168, retuned by t198: a fighter or a tank of the front line, of middling staying power, whose sustain rises during
 * the ULT). Every number is under {@code combat.combo.warrior_melee} / {@code warrior_range} (all provisional); the cooldowns are there too (the
 * class's {@code combat.skill-slots.cooldown-seconds} are not used by them). The effects are drawn in the weapon's colors (its element), the blood
 * and the steel only as touches (t198).
 *
 * <p><b>The shield and the blood</b> (a melee weapon of the class's combo): during the ULT (武神の一撃) a share of the damage the warrior takes stays
 * as a <b>wound</b> ({@link WarriorBlood}; {@code rally-share} 50%, at most {@code rally-cap} 50% of the life) that fades over
 * {@code rally-seconds} (5) after the last blow, and every melee hit drinks it back ({@code rally-rate} 80% of the damage dealt, healed); the
 * wound shows by the HP of the status ("+N" in dark red). Out of the ULT there is no wound and no drinking (t198: the warrior's damage, staying
 * power and sustain were all too strong). The skills bleed; none of them drains.
 * <ul>
 *   <li>小 blood rend: two cuts crossing ahead; they bleed.</li>
 *   <li>中 crimson whirl: a spin of a second that draws the enemies round in, ended by a slam on the spot that throws them up.</li>
 *   <li>大 guardian shield (the shield of 2026-11-02 back, t198): a bash of the shield ahead, and shields turning round the warrior
 *       ({@link Guard}: each cuts one blow by 40% and breaks); its re-activation, while some are up, throws them all out at the enemies round
 *       (each shield a hit): the warrior keeps them to hold the line, or spends them to strike.</li>
 *   <li>util parry: a step back with the blade raised; a blow that comes within the moment is turned aside (no damage), and the warrior strikes
 *       back at once.</li>
 * </ul>
 *
 * <p><b>The great bow</b> (a ranged weapon of the class's combo, t198: not the ranger's many arrows, but the first blow from afar and the time of
 * the draw): every arrow of the warrior hits a still unhurt enemy (full life) {@code first-strike-bonus} (50%) harder and a far one more for each
 * block past {@code distance-from} (8; {@code distance-per-block} 2.5%, at most {@code distance-max} 40%); while the warrior draws they are slowed
 * and take {@code draw-guard} (25%) less damage.
 * <ul>
 *   <li>小 power draw: the bow is drawn (at most {@code small-draw-ticks}); its re-activation looses it, harder the longer it was drawn (fully
 *       drawn it goes through every enemy and bursts); it looses by itself when fully drawn and held to the end.</li>
 *   <li>中 pinning stake: a stake of an arrow that pins the enemy it hits (it can hardly move) and marks it: the warrior's arrows hit it
 *       {@code mark-bonus} harder for a while.</li>
 *   <li>大 siege arrow: a long draw (the warrior stands, guarded), then a great arrow through every enemy in its way that bursts where it
 *       stops.</li>
 *   <li>util spacing (2 charges): a shove of the enemies near, and a leap back.</li>
 * </ul>
 * With a deployed weapon there are no skills (the user's wish of 2026-11-02); its combo is {@link WarriorDeployCombo}. The ULT is the class's.
 */
public final class WarriorSkills {

    static final String MELEE = WarriorMeleeCombo.PLAN;
    static final String RANGE = WarriorRangeCombo.PLAN;
    /** The blood (a touch: drops and the drinking), not the colors of the skills. */
    static final Gradient BLOOD = Gradient.of(Color.fromRGB(38, 0, 6), Color.fromRGB(165, 12, 28), Color.fromRGB(255, 120, 105));

    /** The mobs turn on a warrior (the cleric's bastion): set by the plugin (the mobs' service). */
    public interface Provoker {
        void provoke(Player player, double radius, long millis);
    }

    private static AttackService attack;
    private static Provoker provoker = (player, radius, millis) -> {
    };
    /** Whether a warrior is in the surge of their ULT now (the wound is kept and drunk only then): set by the plugin. */
    private static Predicate<Player> surging = player -> false;

    /** What a skill's melee hits do with the blood (see {@link #strike}). */
    static final class Blood {
        final double rally;
        final double bleed;

        Blood(double rally, double bleed) {
            this.rally = rally;
            this.bleed = bleed;
        }
    }

    /** The blood of the hit being struck now, per warrior. */
    private static final Map<UUID, Blood> BLOOD_NOW = new HashMap<>();
    /** The warriors parrying (util): until when (ms) and the weapon they strike back with. */
    private static final Map<UUID, Object[]> PARRIES = new HashMap<>();
    /** When each warrior's drinking was last drawn (ticks). */
    private static final Map<UUID, Integer> DRAWN = new HashMap<>();
    /** The bows being drawn (小 and 大), per warrior. */
    private static final Map<UUID, Draw> DRAWS = new HashMap<>();
    /** The enemies pinned by 中 and marked: until when (ms). */
    private static final Map<UUID, Long> MARKED = new HashMap<>();

    /** A draw of the great bow: when it began, how long it may go on, and what is loosed at its end. */
    private static final class Draw {
        final long start;
        final int ticks;
        final Consumer<Double> loose;

        Draw(long start, int ticks, Consumer<Double> loose) {
            this.start = start;
            this.ticks = ticks;
            this.loose = loose;
        }
    }

    private WarriorSkills() {
    }

    /** The mobs within {@code radius} of {@code player} turn on them for {@code millis} (the cleric's bastion). */
    static void provoke(Player player, double radius, long millis) {
        provoker.provoke(player, radius, millis);
    }

    public static void setProvoker(Provoker provoker) {
        WarriorSkills.provoker = provoker == null ? (player, radius, millis) -> {
        } : provoker;
    }

    /** {@code surging}: whether a warrior is in the surge of their ULT (武神の一撃) now. */
    public static void setSurging(Predicate<Player> surging) {
        WarriorSkills.surging = surging == null ? player -> false : surging;
    }

    // ---- registering --------------------------------------------------------------------------------------------------------

    /** The warrior's skill {@code slot}: their own with a melee or ranged weapon of the class's combo, else the heavy attack it threw. */
    static SkillSlots.Move move(AttackService attackService, SkillSlots.Slot slot, ToIntFunction<AttackType> heavyOf) {
        attack = attackService;
        return new SkillSlots.Move() {
            @Override
            public boolean use(Player player, RpgItemDefinition weapon) {
                if (weapon == null) return false;
                if (!reworked(attackService, player, weapon)) return attackService.slotHeavy(player, weapon, heavyOf.applyAsInt(weapon.attackType()));
                MoveKit kit = attackService.skillKit(player, weapon);
                if (kit == null) return false;
                if (weapon.attackType() == AttackType.MELEE) {
                    switch (slot) {
                        case SMALL -> rend(kit);
                        case MEDIUM -> whirl(kit);
                        case LARGE -> shield(kit);
                        case UTIL -> parry(kit);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                }
                switch (slot) {
                    case SMALL -> powerDraw(kit);
                    case MEDIUM -> stake(kit);
                    case LARGE -> siege(kit);
                    case UTIL -> spacing(kit);
                    default -> {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean recast(Player player, RpgItemDefinition weapon) {
                if (weapon == null || !reworked(attackService, player, weapon)) return false;
                MoveKit kit = attackService.skillKit(player, weapon);
                if (kit == null) return false;
                if (weapon.attackType() == AttackType.MELEE) return slot == SkillSlots.Slot.LARGE && release(kit);
                return slot == SkillSlots.Slot.SMALL && loose(player);
            }
        };
    }

    /**
     * The charges, re-activations and cooldowns of the warrior's own skills (t198: spread by what each skill is): with a melee weapon 大 opens its
     * re-activation (the shields thrown) for as long as the shields last; with the bow 小 is a draw and its re-activation the loosing, and util
     * keeps 2 charges. Their own seconds (the class's cooldowns are not used by them). With a deployed weapon none.
     */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot, AttackType kind) {
        if (kind == AttackType.MELEE) {
            String base = "combat.combo." + MELEE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "small-seconds", 6.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "medium-seconds", 10.0));
                case LARGE -> new SkillSlots.Mechanics(1, config.getDouble("combat.combo.warrior.shield-seconds", 10.0), 0.0,
                        config.getDouble(base + "large-seconds", 22.0));
                case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 6.0));
                default -> SkillSlots.Mechanics.NONE;
            };
        }
        if (kind == AttackType.RANGE) {
            String base = "combat.combo." + RANGE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics(1, config.getDouble(base + "small-draw-ticks", 50.0) / 20.0, 0.0,
                        config.getDouble(base + "small-seconds", 7.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "medium-seconds", 10.0));
                case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 24.0));
                case UTIL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "util-charges", 2.0)), 0.0,
                        config.getDouble(base + "util-charge-seconds", 6.0), config.getDouble(base + "util-empty-seconds", 10.0));
                default -> SkillSlots.Mechanics.NONE;
            };
        }
        return SkillSlots.Mechanics.NONE;
    }

    private static boolean reworked(AttackService attackService, Player player, RpgItemDefinition weapon) {
        AttackType kind = weapon.attackType();
        return (kind == AttackType.MELEE || kind == AttackType.RANGE) && attackService.hasClassCombo(player, weapon);
    }

    private static double number(String plan, String key, double fallback) {
        return attack == null ? fallback : attack.plugin().getConfig().getDouble("combat.combo." + plan + "." + key, fallback);
    }

    private static double melee(MoveKit kit, String key, double fallback) {
        return kit.number(MELEE, key, fallback);
    }

    private static double range(MoveKit kit, String key, double fallback) {
        return kit.number(RANGE, key, fallback);
    }

    // ---- the blood ------------------------------------------------------------------------------------------------------------

    private static long fadeMillis() {
        return Math.round(number(MELEE, "rally-seconds", 5.0) * 1000.0);
    }

    /** The blood of a skill: it drinks {@code rally} times as fast (during the ULT) and bleeds {@code bleed} of its damage. */
    static Blood blood(double rally, double bleed) {
        return new Blood(rally, bleed);
    }

    /** One melee hit of {@code rate} on {@code victim} with the blood of a skill (null: a plain hit, which drinks the wound as any hit does). */
    static boolean strike(MoveKit kit, LivingEntity victim, double rate, Blood blood) {
        if (blood == null) return kit.hit(victim, rate);
        UUID id = kit.player.getUniqueId();
        Blood before = BLOOD_NOW.put(id, blood);
        try {
            return kit.hit(victim, rate);
        } finally {
            if (before == null) BLOOD_NOW.remove(id);
            else BLOOD_NOW.put(id, before);
        }
    }

    /** A warrior took {@code damage} ({@link AttackService#applyPlayerDamage}): during the ULT a share of it stays as a wound. */
    public static void taken(Player player, double maxHp, double damage) {
        if (damage <= 0.0 || attack == null || !surging.test(player)) return;
        WarriorBlood.wound(player.getUniqueId(), damage, number(MELEE, "rally-share", 0.5), maxHp * number(MELEE, "rally-cap", 50.0) / 100.0,
                fadeMillis(), System.currentTimeMillis());
    }

    /**
     * A warrior's hit landed ({@link ClassEffectService#onDamageDealt}): a skill's hit bleeds as it says; during the ULT a melee hit drinks back
     * the wound ({@code rally-rate} of the damage). Out of the ULT what is left of a wound is gone.
     */
    public static void dealt(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage) {
        if (attack == null || weapon == null || weapon.attackType() != AttackType.MELEE || damage <= 0.0) return;
        UUID id = attacker.getUniqueId();
        Blood blood = BLOOD_NOW.get(id);
        if (blood != null && blood.bleed > 0.0 && victim.isValid() && !victim.isDead()) attack.statusEffects().addBleed(victim, damage * blood.bleed, id);
        if (!surging.test(attacker)) {
            WarriorBlood.forget(id);
            return;
        }
        double rate = number(MELEE, "rally-rate", 0.8) * (blood == null ? 1.0 : blood.rally);
        double drunk = WarriorBlood.drink(id, damage * rate, fadeMillis(), System.currentTimeMillis());
        if (drunk <= 0.0) return;
        attack.healPlayer(attacker, drunk);
        drawDrink(attacker, victim);
    }

    /** The wound a warrior can still drink back during the ULT (the status shows it by the HP while they hold a melee weapon), 0 when none. */
    public static double woundShown(Player player) {
        if (attack == null || !surging.test(player)) return 0.0;
        double left = WarriorBlood.left(player.getUniqueId(), fadeMillis(), System.currentTimeMillis());
        if (left <= 0.0) return 0.0;
        RpgItemDefinition weapon = attack.weaponInMainHand(player);
        return weapon != null && weapon.isWeaponLike() && weapon.attackType() == AttackType.MELEE ? left : 0.0;
    }

    /** Blood drawn from the enemy to the warrior (at most every 4 ticks). */
    private static void drawDrink(Player player, LivingEntity victim) {
        int tick = Bukkit.getCurrentTick();
        Integer last = DRAWN.put(player.getUniqueId(), tick);
        if (last != null && tick - last < 4) return;
        Location from = victim.getLocation().add(0.0, victim.getHeight() * 0.55, 0.0);
        Location to = player.getLocation().add(0.0, 1.1, 0.0);
        if (from.getWorld() != to.getWorld()) return;
        Fx fx = Fx.of(player, player.getWorld(), from);
        fx.line(from, to, BLOOD, 0.3, 0.95, 1.5, 0.75, 0.35, null, 1);
        fx.particle(Particle.DAMAGE_INDICATOR, to.getX(), to.getY(), to.getZ(), 2, 0.25, 0.25, 0.25, 0.02);
        if (last == null || tick - last > 20) Sfx.BLOOD_DRINK.play(to, 0.45f, 1.0f);
    }

    /** A cut through an enemy along {@code direction}: a streak of the weapon's colors and a few drops of blood. */
    static void bled(MoveKit kit, LivingEntity victim, Vector direction) {
        Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        Vector way = direction.lengthSquared() < 1.0E-6 ? kit.facing() : direction.clone().normalize();
        Fx fx = kit.fx(at);
        fx.line(at.clone().subtract(way.clone().multiply(0.4)), at.clone().add(way.clone().multiply(1.1)).subtract(0.0, 0.3, 0.0), kit.palette(), 0.2, 0.9, 1.5, 1.0, 0.25, null, 1);
        fx.sphereShell(at, 0.3, 0.0, 6, BLOOD, 0.7);
    }

    /** Cuts every enemy within {@code reach} and {@code halfAngle} degrees ahead: {@code rate} with {@code blood}; how many were hit. */
    static int cutAhead(MoveKit kit, double reach, double halfAngle, double rate, Blood blood) {
        return cutAll(kit, kit.cone(reach, halfAngle), kit.facing(), rate, blood, false);
    }

    /**
     * Cuts each of {@code victims} ({@code rate} with {@code blood}); with {@code normal} (an attack of the combo) the class's innate 薙ぎ払い
     * ({@code melee_area}, t198: it did nothing with the class's combo) also hits the other enemies within {@code combat.area-radius} (3) of
     * each one struck, {@code combat.melee-area-damage-rate} (65%) of it, once each. How many of {@code victims} were hit.
     */
    static int cutAll(MoveKit kit, List<LivingEntity> victims, Vector direction, double rate, Blood blood, boolean normal) {
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        java.util.List<LivingEntity> landed = new java.util.ArrayList<>();
        for (LivingEntity victim : victims) {
            struck.add(victim.getUniqueId());
            if (!strike(kit, victim, rate, blood)) continue;
            bled(kit, victim, direction);
            landed.add(victim);
        }
        if (normal && !landed.isEmpty() && kit.hasSkill("melee_area") && attack != null) {
            double radius = attack.plugin().getConfig().getDouble("combat.area-radius", 3.0);
            double share = attack.plugin().getConfig().getDouble("combat.melee-area-damage-rate", 0.65);
            for (LivingEntity victim : landed) {
                for (LivingEntity near : kit.around(victim.getLocation(), radius)) {
                    if (!struck.add(near.getUniqueId())) continue;
                    if (!kit.hit(near, rate * share)) continue;
                    Location from = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                    kit.fx(from).line(from, near.getLocation().add(0.0, near.getHeight() * 0.5, 0.0), kit.palette(), 0.2, 0.8, 0.5, 0.7, 0.3, null, 1);
                }
            }
        }
        return landed.size();
    }

    /** Spikes of the weapon's colors out of the ground round {@code at} ({@code count} of them at {@code radius}), rising over a few ticks. */
    static void spikes(MoveKit kit, Location at, double radius, int count, double height) {
        double turn = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        for (int index = 0; index < count; index++) {
            double angle = turn + index * Math.PI * 2.0 / count;
            Location base = at.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            Vector lean = new Vector(Math.cos(angle) * 0.25, 1.0, Math.sin(angle) * 0.25).normalize();
            for (int step = 0; step < 3; step++) {
                double rise = height * (step + 1) / 3.0;
                kit.later(step, () -> kit.fx(base).line(base, base.clone().add(lean.clone().multiply(rise)), kit.palette(), 0.1, 1.0, 2.0, 1.1, 0.25, null, 1));
            }
        }
    }

    // ---- the shield and the blood: 小 --------------------------------------------------------------------------------------------------

    /** Blood rend: two cuts that cross ahead, one from each side; they bleed. */
    private static void rend(MoveKit kit) {
        double rate = melee(kit, "small-rate", 0.5);
        double reach = melee(kit, "small-reach", 4.2);
        Blood blood = blood(1.0, melee(kit, "small-bleed", 0.2));
        Location feet = kit.player.getLocation();
        kit.slash("drop", reach, 130.0, 40.0, 0.0, reach * 0.55, 0.25, 0.0, 1.5, 0.13, 0.0);
        kit.sound(feet, Sfx.BLOOD_SWING, 0.9f, 1.0f);
        cutAhead(kit, reach, 65.0, rate, blood);
        kit.later(4L, () -> {
            kit.slash("drop", reach, 130.0, -40.0, 180.0, reach * 0.55, 0.25, 0.0, 1.5, 0.13, 0.0);
            Location now = kit.player.getLocation();
            kit.sound(now, Sfx.BLOOD_SWING, 0.9f, 0.85f);
            if (cutAhead(kit, reach, 65.0, rate, blood) > 0) kit.sound(now, Sfx.BLOOD_HIT, 0.8f, 1.0f);
            // the cross glows on the ground ahead a moment
            Location mark = kit.ground(now.clone().add(kit.facing().multiply(reach * 0.6)));
            Fx fx = kit.fx(mark);
            Vector[] axes = {kit.facing().rotateAroundY(Math.PI / 4.0), kit.facing().rotateAroundY(-Math.PI / 4.0)};
            for (Vector axis : axes) {
                fx.line(mark.clone().add(axis.clone().multiply(-1.4)).add(0.0, 0.12, 0.0), mark.clone().add(axis.clone().multiply(1.4)).add(0.0, 0.12, 0.0), kit.palette(), 0.3, 0.9, 0.0, 1.0, 0.2, null, 1);
            }
        });
    }

    // ---- the shield and the blood: 中 --------------------------------------------------------------------------------------------------

    /**
     * Crimson whirl: a spin of a second; everyone round is cut every 4 ticks and drawn in a little, the warrior guarded a little meanwhile; it ends
     * in a slam on the spot that throws up everyone round (t198: the slam was its re-activation).
     */
    private static void whirl(MoveKit kit) {
        double rate = melee(kit, "medium-rate", 0.24);
        double radius = melee(kit, "medium-radius", 3.8);
        double pull = melee(kit, "medium-pull", 0.05);
        int spin = 21;
        kit.guard(melee(kit, "medium-guard", 0.15), spin + 1);
        kit.sound(kit.player.getLocation(), Sfx.BLOOD_WHIRL, 1.0f, 1.0f);
        kit.every(spin + 1, age -> {
            Location at = kit.player.getLocation();
            if (age >= spin) {
                quake(kit);
                return true;
            }
            if (age % 4 == 0) {
                kit.slash("arc", radius * 0.8, 330.0, 75.0, age % 8 == 0 ? 0.0 : 180.0, 0.3, 0.6, 0.0, 1.35, 0.08, 0.0);
                kit.sound(at, Sfx.BLOOD_SWING, 0.5f, 1.05f + 0.05f * (age / 4));
                for (LivingEntity victim : kit.around(at, radius)) {
                    Vector off = victim.getLocation().toVector().subtract(at.toVector()).setY(0.0);
                    if (strike(kit, victim, rate, null)) bled(kit, victim, new Vector(-off.getZ(), 0.0, off.getX()));
                }
            }
            for (LivingEntity victim : kit.around(at, radius + 1.5)) kit.drift(victim, at, pull);
            Fx fx = kit.fx(at);
            fx.flatRing(at.clone().add(0.0, 0.9 + 0.08 * Math.sin(age * 0.6), 0.0), radius * 0.85, 34, kit.palette(), 0.35 + 0.04 * (age % 10), 0.4, 1.1);
            if (age % 3 == 0) {
                double angle = age * 0.9;
                Location from = at.clone().add(Math.cos(angle) * radius * 0.8, 0.9, Math.sin(angle) * radius * 0.8);
                fx.line(from, from.clone().add(Math.cos(angle) * 1.2, -0.6, Math.sin(angle) * 1.2), kit.palette(), 0.5, 0.15, 0.0, 0.9, 0.25, null, 1);
            }
            return false;
        });
    }

    /** The end of the whirl: a slam on the spot that throws up everyone round (it drinks the wound twice as fast during the ULT). */
    private static void quake(MoveKit kit) {
        double rate = melee(kit, "slam-rate", 0.9);
        double radius = melee(kit, "slam-radius", 4.0);
        Blood blood = blood(melee(kit, "slam-rally", 2.0), 0.0);
        Location feet = kit.player.getLocation();
        kit.slash("drop", 3.6, 150.0, 5.0, 0.0, 1.8, 0.3, 0.0, 1.8, 0.2, 0.0);
        kit.sound(feet, Sfx.BLOOD_SLAM, 1.0f, 0.9f);
        kit.shockwave(feet, 1.0, radius, 6, 1.3);
        spikes(kit, feet, radius * 0.7, 8, 1.6);
        for (LivingEntity victim : kit.around(feet, radius)) {
            if (!strike(kit, victim, rate, blood)) continue;
            kit.lift(victim, 0.45);
            bled(kit, victim, new Vector(0.0, 1.0, 0.0));
        }
    }

    // ---- the shield and the blood: 大 --------------------------------------------------------------------------------------------------

    /**
     * Guardian shield (t198, the shield skill back): a step in and a bash of the shield on the enemies just ahead ({@code large-rate}, thrown back
     * and slowed {@code large-slow} for a second), then the shields of a bulwark turn round the warrior ({@code combat.combo.warrior}:
     * {@code shield-count} 3 for {@code shield-seconds} 10; each cuts one blow by {@code shield-cut} 40% and breaks).
     */
    private static void shield(MoveKit kit) {
        double rate = melee(kit, "large-rate", 1.0);
        double reach = melee(kit, "large-reach", 3.4);
        double slow = melee(kit, "large-slow", 40.0);
        Location feet = kit.player.getLocation();
        kit.sound(feet, Sfx.SHIELD_BASH, 1.0f, 1.0f);
        kit.dash(kit.facing(), 0.45, 3, end -> {
            Vector facing = kit.facing();
            Location front = end.clone().add(0.0, 1.0, 0.0).add(facing.clone().multiply(1.2));
            Fx fx = kit.fx(front);
            Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
            // the face of the shield struck forward, and a ring of the blow
            fx.disc(front, side, new Vector(0.0, 1.0, 0.0), 0.8, 26, kit.palette(), 0.6, 1.1);
            fx.ring(front, side, new Vector(0.0, 1.0, 0.0), 1.3, 30, kit.palette(), 0.8, 0.1, 1.0);
            kit.particle(Particle.CRIT, front, 10, 0.4, 0.2);
            for (LivingEntity victim : kit.cone(reach, 60.0)) {
                if (!strike(kit, victim, rate, null)) continue;
                kit.push(victim, end.toVector(), 0.7, 0.25);
                kit.slow(victim, slow, 1.0);
            }
            kit.bulwark();
            kit.later(2L, () -> {
                Location at = kit.player.getLocation();
                kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.4, 0.0, 26, kit.palette(), 1.0);
            });
        });
    }

    /**
     * The re-activation of 大 (while shields are up): every shield is thrown out at the enemies within {@code release-radius}, each a hit of
     * {@code release-rate}, and they are thrown back; false when no shield is left.
     */
    private static boolean release(MoveKit kit) {
        int count = kit.releaseShields();
        if (count <= 0) return false;
        double rate = melee(kit, "release-rate", 0.6);
        double radius = melee(kit, "release-radius", 4.5);
        Location at = kit.player.getLocation();
        Location chest = at.clone().add(0.0, 1.1, 0.0);
        kit.sound(at, Sfx.SHIELD_RELEASE, 1.0f, 1.0f);
        kit.shockwave(at, 0.8, radius, 5, 1.3);
        double turn = Math.atan2(kit.facing().getZ(), kit.facing().getX());
        for (int index = 0; index < count; index++) {
            double angle = turn + index * Math.PI * 2.0 / count;
            Vector out = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
            for (int step = 0; step < 4; step++) {
                int stage = step;
                kit.later(step, () -> {
                    Location plate = chest.clone().add(out.clone().multiply(1.3 + stage * (radius - 1.3) / 3.0));
                    kit.fx(plate).disc(plate, out.clone().crossProduct(new Vector(0.0, 1.0, 0.0)), new Vector(0.0, 1.0, 0.0), 0.5, 14, kit.palette(), 0.6, 1.0);
                });
            }
        }
        // (each shield a hit: struck as one blow of all of them)
        for (LivingEntity victim : kit.around(at, radius)) {
            if (strike(kit, victim, rate * count, null)) kit.push(victim, at.toVector(), 0.6, 0.3);
        }
        return true;
    }

    // ---- the shield and the blood: util ---------------------------------------------------------------------------------------------------

    /** Parry: a step back with the blade raised for {@code util-parry-ticks}; a blow then is turned aside and answered ({@link #parried}). */
    private static void parry(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        int ticks = Math.max(2, (int) Math.round(melee(kit, "util-parry-ticks", 12.0)));
        kit.hopBack(melee(kit, "util-back", 0.6), 0.25);
        Object[] stance = {System.currentTimeMillis() + ticks * 50L, kit.weapon};
        PARRIES.put(id, stance);
        kit.sound(player.getLocation(), Sfx.BLOOD_GUARD, 0.8f, 1.0f);
        kit.slash("line", 2.2, 70.0, 90.0, 0.0, 0.9, 0.0, 0.0, 1.0, 0.1, 0.0);
        kit.every(ticks, age -> {
            if (PARRIES.get(id) != stance) return true;
            if (age % 2 == 0) {
                Location eye = player.getEyeLocation();
                Vector facing = kit.facing();
                Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
                Location middle = eye.clone().add(facing.clone().multiply(0.8)).subtract(0.0, 0.5, 0.0);
                kit.fx(middle).line(middle.clone().add(side.clone().multiply(-0.6)).add(0.0, 0.6, 0.0), middle.clone().add(side.clone().multiply(0.6)).subtract(0.0, 0.3, 0.0),
                        kit.palette(), 0.4, 1.0, 0.0, 0.8, 0.15, null, 1);
            }
            return false;
        });
    }

    /**
     * A blow is about to land on {@code player} ({@link AttackService#applyPlayerDamage}): while they parry it is turned aside (true: no damage),
     * and they strike back.
     */
    public static boolean parried(Player player) {
        Object[] stance = PARRIES.get(player.getUniqueId());
        if (stance == null) return false;
        PARRIES.remove(player.getUniqueId());
        if ((long) stance[0] < System.currentTimeMillis() || attack == null) return false;
        RpgItemDefinition weapon = (RpgItemDefinition) stance[1];
        Location at = player.getLocation().add(0.0, 1.0, 0.0);
        Sfx.BLOOD_PARRY.play(at, 1.0f, 1.0f);
        Fx.of(player, player.getWorld(), at).sphereShell(at, 0.8, 0.0, 18, net.tkgon.mc.iruuRPG.vfx.Palettes.of(weapon.element()), 1.0);
        // (struck back the next tick, out of the blow's damage event)
        Bukkit.getScheduler().runTask(attack.plugin(), () -> riposte(player, weapon));
        return true;
    }

    /** The answer to a parried blow: a lunge (nothing hurts the warrior) and a cut that drinks the wound twice as fast during the ULT. */
    private static void riposte(Player player, RpgItemDefinition weapon) {
        if (!player.isOnline() || player.isDead() || attack == null) return;
        MoveKit kit = attack.skillKit(player, weapon);
        if (kit == null) return;
        Vector heading = kit.facing();
        double rate = melee(kit, "riposte-rate", 1.1);
        Blood blood = blood(melee(kit, "riposte-rally", 2.0), 0.0);
        kit.invulnerable(10);
        kit.dash(heading, 0.9, 4, end -> {
            kit.slash("arc", 3.4, 160.0, 25.0, 0.0, 1.6, 0.35, 0.0, 1.5, 0.12, 0.0);
            kit.sound(end, Sfx.BLOOD_HIT, 1.0f, 1.1f);
            cutAhead(kit, 3.8, 70.0, rate, blood);
        });
    }

    // ---- the great bow: helpers ---------------------------------------------------------------------------------------------------------

    /**
     * How much harder an arrow of the warrior hits (a multiplier): {@code firstStrike} on an enemy still at full life, {@code perBlock} for each
     * block it is past {@code from} (at most {@code distanceMax}), and {@code markBonus} on an enemy marked by 中. Pure, for the tests.
     */
    static double bowBonus(boolean fullLife, double distance, boolean marked, double firstStrike, double from, double perBlock, double distanceMax, double markBonus) {
        double bonus = fullLife ? firstStrike : 0.0;
        bonus += Math.min(distanceMax, Math.max(0.0, distance - from) * perBlock);
        if (marked) bonus += markBonus;
        return 1.0 + Math.max(0.0, bonus);
    }

    private static double maxHealth(LivingEntity victim) {
        AttributeInstance attribute = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? victim.getHealth() : attribute.getValue();
    }

    private static boolean marked(LivingEntity victim) {
        Long until = MARKED.get(victim.getUniqueId());
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        MARKED.remove(victim.getUniqueId());
        return false;
    }

    /** One hit of an arrow of the warrior's bow: {@code rate} times its bonus ({@link #bowBonus}); a first strike rings out. */
    static boolean arrowHit(MoveKit kit, LivingEntity victim, double rate) {
        boolean full = victim.getHealth() >= maxHealth(victim) - 1.0E-6;
        double distance = kit.player.getWorld() == victim.getWorld() ? kit.player.getLocation().distance(victim.getLocation()) : 0.0;
        double bonus = bowBonus(full, distance, marked(victim), range(kit, "first-strike-bonus", 0.5), range(kit, "distance-from", 8.0),
                range(kit, "distance-per-block", 0.025), range(kit, "distance-max", 0.4), range(kit, "mark-bonus", 0.25));
        if (!kit.hit(victim, rate * bonus)) return false;
        Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        kit.particle(Particle.CRIT, at, 6, 0.25, 0.2);
        if (full) {
            kit.fx(at).sphereShell(at, 0.6, 0.0, 14, kit.palette(), 1.0);
            kit.sound(at, Sfx.GREATBOW_FIRST, 0.8f, 1.0f);
        }
        return true;
    }

    /** Where the warrior aims, at most {@code reach} blocks away. */
    static Location aimWithin(MoveKit kit, double reach) {
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        if (way.length() > reach) aim = eye.clone().add(way.normalize().multiply(reach));
        return aim;
    }

    /** The way from the bow to where the warrior aims. */
    static Vector toAim(MoveKit kit, Location from, double reach) {
        Vector way = aimWithin(kit, reach).toVector().subtract(from.toVector());
        return way.lengthSquared() < 1.0E-6 ? kit.axes()[0] : way.normalize();
    }

    /**
     * A heavy arrow of the warrior's bow from {@code from} along {@code way}: {@code speed} blocks a tick for up to {@code reach}; into the first
     * enemy, or through every one ({@code pierce}); {@code onHit} for each; its trail as thick as {@code weight} (1 a plain arrow); {@code onEnd}
     * where it stopped.
     */
    static void heavyArrow(MoveKit kit, Location from, Vector way, double speed, double reach, double weight, boolean pierce,
                           Consumer<LivingEntity> onHit, Consumer<Location> onEnd) {
        boolean[] stopped = {false};
        Location[] last = {from.clone()};
        kit.pierceArrow(from, way, speed, reach, kit.palette(), victim -> {
            if (stopped[0]) return;
            if (!pierce) stopped[0] = true;
            onHit.accept(victim);
        }, at -> {
            if (weight > 1.0) {
                Fx fx = kit.fx(at);
                fx.line(last[0], at, kit.palette(), 0.3, 0.9, 0.0, 0.8 + 0.4 * weight, 0.25, null, 1);
                Vector side = way.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
                if (side.lengthSquared() < 1.0E-6) side = new Vector(1.0, 0.0, 0.0);
                fx.ring(at, side.normalize(), side.clone().crossProduct(way).normalize(), 0.25 * weight, 10, kit.palette(), 0.7, 0.05, 0.9);
            }
            last[0] = at.clone();
            return stopped[0];
        }, end -> {
            kit.sound(end, Sfx.GREATBOW_IMPACT, 0.4f + 0.2f * (float) weight, 1.2f - 0.1f * (float) weight);
            if (onEnd != null) onEnd.accept(end);
        });
    }

    /** Where the bow is, a little ahead of the warrior's hand. */
    static Location bow(MoveKit kit) {
        return ComboParts.hand(kit).add(kit.axes()[0].clone().multiply(0.3));
    }

    /** Slows the warrior and guards them while they draw, for {@code ticks} (refreshed by the drawing). */
    private static void bracing(MoveKit kit, int ticks, int slowLevel, double guard) {
        kit.player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, slowLevel, false, false, false));
        kit.guard(guard, ticks);
    }

    /** The string drawn back and the light gathering at the bow, {@code power} 0 - 1 of a full draw. */
    static void drawLook(MoveKit kit, double power) {
        Location bow = bow(kit);
        Vector[] axes = kit.axes();
        Fx fx = kit.fx(bow);
        Location nock = bow.clone().subtract(axes[0].clone().multiply(0.35 + 0.45 * power));
        Location top = bow.clone().add(0.0, 0.75, 0.0);
        Location bottom = bow.clone().subtract(0.0, 0.75, 0.0);
        fx.line(top, nock, kit.palette(), 0.4, 0.9, 0.0, 0.5, 0.15, null, 1);
        fx.line(bottom, nock, kit.palette(), 0.4, 0.9, 0.0, 0.5, 0.15, null, 1);
        fx.ring(bow, axes[1], axes[2], 0.3 + 0.5 * (1.0 - power), 12, kit.palette(), 0.4 + 0.6 * power, 0.05, 0.8 + 0.4 * power);
    }

    // ---- the great bow: 小 ---------------------------------------------------------------------------------------------------------------

    /**
     * Power draw: the bow is drawn for at most {@code small-draw-ticks} (the warrior slowed and guarded meanwhile); the re-activation looses it
     * ({@link #loose}): {@code small-rate-min} + {@code small-rate-gain} times the share drawn; fully drawn it goes through every enemy and bursts
     * where it stops ({@code small-burst-rate}). Held to the end, it looses by itself.
     */
    private static void powerDraw(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        int ticks = Math.max(4, (int) Math.round(range(kit, "small-draw-ticks", 50.0)));
        double guard = range(kit, "draw-guard", 0.25);
        double min = range(kit, "small-rate-min", 0.8);
        double gain = range(kit, "small-rate-gain", 1.2);
        double reach = range(kit, "aim-range", 24.0);
        Draw draw = new Draw(System.currentTimeMillis(), ticks, share -> {
            boolean full = share >= 0.95;
            Location from = bow(kit);
            Vector way = toAim(kit, from, reach * 1.5);
            double rate = min + gain * share;
            kit.sound(from, Sfx.GREATBOW_LOOSE, 0.8f + 0.3f * share.floatValue(), 1.1f - 0.3f * share.floatValue());
            kit.particle(Particle.SWEEP_ATTACK, from, 0, 0.0, 0.0);
            heavyArrow(kit, from, way, 1.6 + share, reach * 1.5, 1.0 + share, full, victim -> arrowHit(kit, victim, rate), end -> {
                if (!full) return;
                double radius = range(kit, "small-burst-radius", 2.5);
                kit.shockwave(end, 0.5, radius, 4, 1.2);
                kit.particle(Particle.EXPLOSION_LARGE, end, 1, 0.0, 0.0);
                for (LivingEntity victim : kit.around(end, radius)) arrowHit(kit, victim, rate * range(kit, "small-burst-rate", 0.5));
            });
        });
        DRAWS.put(id, draw);
        kit.sound(player.getLocation(), Sfx.GREATBOW_DRAW, 1.0f, 0.9f);
        kit.every(ticks + 1, age -> {
            if (DRAWS.get(id) != draw) return true;
            if (age >= ticks) {
                loose(player);
                return true;
            }
            if (age % 5 == 0) bracing(kit, 6, 1, guard);
            double power = Math.min(1.0, (double) age / ticks);
            drawLook(kit, power);
            if (age == ticks * 19 / 20) kit.sound(player.getLocation(), Sfx.GREATBOW_FULL, 0.8f, 1.0f);
            return false;
        });
    }

    /** The re-activation of 小: looses the bow being drawn (false when none is); the share drawn sets how hard. */
    private static boolean loose(Player player) {
        Draw draw = DRAWS.remove(player.getUniqueId());
        if (draw == null) return false;
        player.removePotionEffect(PotionEffectType.SLOW);
        double share = Math.min(1.0, (System.currentTimeMillis() - draw.start) / (draw.ticks * 50.0));
        draw.loose.accept(share);
        return true;
    }

    // ---- the great bow: 中 ---------------------------------------------------------------------------------------------------------------

    /**
     * Pinning stake: a stake of an arrow into the first enemy ({@code medium-rate}): it can hardly move ({@code medium-pin} slowed for
     * {@code medium-pin-seconds}) and is marked for {@code medium-mark-seconds} (the warrior's arrows hit it {@code mark-bonus} harder).
     */
    private static void stake(MoveKit kit) {
        double rate = range(kit, "medium-rate", 1.1);
        double reach = range(kit, "aim-range", 24.0);
        Location from = bow(kit);
        kit.sound(from, Sfx.GREATBOW_LOOSE, 0.9f, 0.9f);
        heavyArrow(kit, from, toAim(kit, from, reach), 2.0, reach, 1.4, false, victim -> {
            if (!arrowHit(kit, victim, rate)) return;
            kit.slow(victim, range(kit, "medium-pin", 90.0), range(kit, "medium-pin-seconds", 2.0));
            long until = System.currentTimeMillis() + Math.round(range(kit, "medium-mark-seconds", 4.0) * 1000.0);
            MARKED.put(victim.getUniqueId(), until);
            Location ground = kit.ground(victim.getLocation());
            kit.sound(ground, Sfx.GREATBOW_PIN, 1.0f, 1.0f);
            // the stake through it into the ground, and the mark on it for as long as it lasts
            kit.fx(ground).line(ground.clone().add(0.0, victim.getHeight() + 0.8, 0.0), ground.clone().subtract(0.0, 0.2, 0.0), kit.palette(), 0.3, 1.0, 0.0, 1.2, 0.15, null, 1);
            int life = (int) Math.round(range(kit, "medium-mark-seconds", 4.0) * 20.0);
            kit.every(life, age -> {
                if (!victim.isValid() || victim.isDead() || !marked(victim)) return true;
                if (age % 4 == 0) {
                    Location top = victim.getLocation().add(0.0, victim.getHeight() + 0.5, 0.0);
                    kit.fx(top).flatRing(top, 0.35 + 0.05 * Math.sin(age * 0.5), 10, kit.palette(), 0.8, 0.0, 0.8);
                    Location feet = victim.getLocation().add(0.0, 0.1, 0.0);
                    if (age < 40) kit.fx(feet).flatRing(feet, 0.8, 14, kit.palette(), 0.5, 0.0, 0.8);
                }
                return false;
            });
        }, null);
    }

    // ---- the great bow: 大 ---------------------------------------------------------------------------------------------------------------

    /**
     * Siege arrow: a draw of {@code large-draw-ticks} (the warrior cannot move and takes {@code large-guard} less damage), then a great arrow
     * through every enemy in its way for up to {@code large-range} blocks ({@code large-rate}) that bursts where it stops
     * ({@code large-burst-rate} within {@code large-burst-radius}). The bonus of the bow is on both.
     */
    private static void siege(MoveKit kit) {
        Player player = kit.player;
        int ticks = Math.max(4, (int) Math.round(range(kit, "large-draw-ticks", 30.0)));
        double guard = range(kit, "large-guard", 0.4);
        double rate = range(kit, "large-rate", 2.5);
        double reach = range(kit, "large-range", 40.0);
        double radius = range(kit, "large-burst-radius", 4.0);
        double burst = range(kit, "large-burst-rate", 1.2);
        kit.sound(player.getLocation(), Sfx.GREATBOW_DRAW, 1.0f, 0.6f);
        bracing(kit, ticks + 2, 9, guard);
        kit.every(ticks + 1, age -> {
            if (!player.isOnline() || player.isDead()) return true;
            double power = Math.min(1.0, (double) age / ticks);
            drawLook(kit, power);
            Location bow = bow(kit);
            Fx fx = kit.fx(bow);
            // the light gathers on the arrow, and a ring of it on the ground grows under the warrior
            if (age % 2 == 0) {
                Location feet = player.getLocation().add(0.0, 0.1, 0.0);
                fx.flatRing(feet, 0.6 + 1.6 * power, 24, kit.palette(), 0.4 + 0.5 * power, 0.1, 1.0);
                double angle = age * 0.6;
                Vector[] axes = kit.axes();
                Location mote = bow.clone().add(axes[1].clone().multiply(Math.cos(angle) * 1.2 * (1.0 - power))).add(axes[2].clone().multiply(Math.sin(angle) * 1.2 * (1.0 - power)));
                fx.line(mote, bow, kit.palette(), 0.3, 1.0, 0.0, 0.8, 0.2, null, 1);
            }
            if (age < ticks) return false;
            player.removePotionEffect(PotionEffectType.SLOW);
            Location from = bow(kit);
            Vector way = toAim(kit, from, reach);
            kit.sound(from, Sfx.GREATBOW_LOOSE, 1.3f, 0.6f);
            kit.sound(from, Sfx.GREATBOW_FULL, 0.8f, 0.7f);
            kit.shockwave(player.getLocation(), 0.4, 2.0, 3, 1.0);
            heavyArrow(kit, from, way, 2.6, reach, 2.6, true, victim -> {
                if (arrowHit(kit, victim, rate)) kit.push(victim, player.getLocation().toVector(), 0.6, 0.2);
            }, end -> {
                kit.sound(end, Sfx.GREATBOW_IMPACT, 1.2f, 0.6f);
                kit.shockwave(end, 0.8, radius + 0.5, 6, 1.4);
                kit.particle(Particle.EXPLOSION_LARGE, end, 2, 0.6, 0.0);
                kit.fx(end).sphereShell(end, radius * 0.5, 0.0, 36, kit.palette(), 1.3);
                spikes(kit, kit.ground(end), radius * 0.6, 10, 2.2);
                for (LivingEntity victim : kit.around(end, radius)) {
                    if (arrowHit(kit, victim, burst)) kit.push(victim, end.toVector(), 0.5, 0.35);
                }
            });
            return true;
        });
    }

    // ---- the great bow: util -------------------------------------------------------------------------------------------------------------

    /** Spacing (2 charges): the enemies within {@code util-radius} are shoved away ({@code util-rate}) and the warrior leaps back. */
    private static void spacing(MoveKit kit) {
        Location feet = kit.player.getLocation();
        double radius = range(kit, "util-radius", 3.0);
        double rate = range(kit, "util-rate", 0.3);
        kit.sound(feet, Sfx.SHIELD_BASH, 0.7f, 1.3f);
        kit.shockwave(feet, 0.5, radius, 4, 1.1);
        for (LivingEntity victim : kit.around(feet, radius)) {
            if (kit.hit(victim, rate)) kit.push(victim, feet.toVector(), range(kit, "util-push", 0.8), 0.25);
        }
        kit.hopBack(range(kit, "util-back", 0.9), range(kit, "util-up", 0.35));
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        WarriorBlood.forget(player);
        WarriorSwords.forget(player);
        PARRIES.remove(player);
        DRAWN.remove(player);
        BLOOD_NOW.remove(player);
        DRAWS.remove(player);
    }
}
