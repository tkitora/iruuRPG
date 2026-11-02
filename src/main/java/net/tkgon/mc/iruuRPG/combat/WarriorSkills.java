package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * The warrior's own skills (t168: nekorozv left the skills, the combos, the look, the effects and the sounds to us, keeping the class's concepts).
 * Every number is under {@code combat.combo.warrior_melee} / {@code warrior_range} (all provisional); the cooldowns are there too (the class's
 * {@code combat.skill-slots.cooldown-seconds} are not used by them).
 *
 * <p><b>The blood knight</b> (a melee weapon of the class's combo; staying power and sustain on the front line, not heavy armor): a share of the
 * damage the warrior takes stays as a <b>wound</b> ({@link WarriorBlood}; {@code rally-share} 50%, at most {@code rally-cap} 50% of the life) that
 * fades over {@code rally-seconds} (5) after the last blow; every melee hit drinks it back ({@code rally-rate} 60% of the damage dealt, healed). The
 * wound shows by the HP of the status ("+N" in dark red). Some skills also drain (heal a share of the damage they deal) and bleed.
 * <ul>
 *   <li>小 blood rend (2 charges): two cuts crossing ahead; they drain and bleed.</li>
 *   <li>中 crimson whirl: a spin of a second that draws the enemies round in and drains; its re-activation is a slam on the spot that drinks the
 *       wound twice as fast.</li>
 *   <li>大 blood oath (8 s): the enemies round turn on the warrior, who takes less damage; the wound does not fade and is drunk faster; at the end
 *       the blood bursts out round them, harder the more they took meanwhile.</li>
 *   <li>util parry: a step back with the blade raised; a blow that comes within the moment is turned aside (no damage), and the warrior strikes
 *       back at once (the wound drunk twice as fast, a little life back).</li>
 * </ul>
 *
 * <p><b>Swords and chains</b> (a ranged weapon of the class's combo): greatswords hover behind the warrior ({@link WarriorSwords}) and are sent at
 * the enemies; chains <b>bind</b> them to a point (they can move only a little way from it), and a bound enemy takes {@code bound-bonus} more from
 * the swords.
 * <ul>
 *   <li>小 piercing greatsword (3 charges): a greatsword flies straight through every enemy in the way.</li>
 *   <li>中 binding chain: a chain to the enemy aimed at binds it beside a sword planted by it; its re-activation yanks the bound enemy to the
 *       warrior and the hovering swords stab it.</li>
 *   <li>大 prison of swords: eight greatswords fall in a ring at the aim, chained together: the enemies inside cannot get out, the swords stab
 *       inward, and at the end all eight plunge into the middle.</li>
 *   <li>util retreat: a leap back; a sword falls where the warrior stood and chains the enemies near it there.</li>
 * </ul>
 * With a deployed weapon there are no skills (the user's wish of 2026-11-02); its combo is {@link WarriorDeployCombo}. The ULT is the class's.
 */
public final class WarriorSkills {

    static final String MELEE = WarriorMeleeCombo.PLAN;
    static final String RANGE = WarriorRangeCombo.PLAN;
    /** The blood knight's colors: old blood, crimson, a bright red. */
    static final Gradient BLOOD = Gradient.of(Color.fromRGB(38, 0, 6), Color.fromRGB(165, 12, 28), Color.fromRGB(255, 120, 105));
    static final List<Color> BLOOD_SLASH = List.of(Color.fromRGB(60, 0, 8), Color.fromRGB(190, 20, 35), Color.fromRGB(255, 150, 140));
    static final List<Color> STEEL_SLASH = List.of(Color.fromRGB(50, 60, 88), Color.fromRGB(160, 186, 225), Color.fromRGB(245, 250, 255));

    /** The mobs turn on a warrior (the oath of 大): set by the plugin (the mobs' service). */
    public interface Provoker {
        void provoke(Player player, double radius, long millis);
    }

    private static AttackService attack;
    private static Provoker provoker = (player, radius, millis) -> {
    };

    /** What a skill's melee hits do with the blood (see {@link #strike}). */
    static final class Blood {
        final double rally;
        final double drain;
        final double bleed;
        double capLeft;

        Blood(double rally, double drain, double capLeft, double bleed) {
            this.rally = rally;
            this.drain = drain;
            this.capLeft = capLeft;
            this.bleed = bleed;
        }
    }

    /** The blood of the hit being struck now, per warrior. */
    private static final Map<UUID, Blood> BLOOD_NOW = new HashMap<>();

    /** A warrior's oath (大): until when, the life it was sworn with and what was taken meanwhile. */
    private static final class Oath {
        final long until;
        final double maxHp;
        double taken;

        Oath(long until, double maxHp) {
            this.until = until;
            this.maxHp = maxHp;
        }
    }

    private static final Map<UUID, Oath> OATHS = new HashMap<>();
    /** The warriors parrying (util): until when (ms) and the weapon they strike back with. */
    private static final Map<UUID, Object[]> PARRIES = new HashMap<>();
    /** The enemy each warrior bound with 中 (for its re-activation). */
    private static final Map<UUID, UUID> CHAINED = new HashMap<>();
    /** When each warrior's drinking was last drawn (ticks). */
    private static final Map<UUID, Integer> DRAWN = new HashMap<>();

    private WarriorSkills() {
    }

    public static void setProvoker(Provoker provoker) {
        WarriorSkills.provoker = provoker == null ? (player, radius, millis) -> {
        } : provoker;
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
                    lookBlood(kit);
                    switch (slot) {
                        case SMALL -> rend(kit);
                        case MEDIUM -> whirl(kit);
                        case LARGE -> oath(kit);
                        case UTIL -> parry(kit);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                }
                lookSteel(kit);
                switch (slot) {
                    case SMALL -> piercer(kit);
                    case MEDIUM -> bindingChain(kit);
                    case LARGE -> prison(kit);
                    case UTIL -> retreat(kit);
                    default -> {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean recast(Player player, RpgItemDefinition weapon) {
                if (slot != SkillSlots.Slot.MEDIUM || weapon == null || !reworked(attackService, player, weapon)) return false;
                MoveKit kit = attackService.skillKit(player, weapon);
                if (kit == null) return false;
                if (weapon.attackType() == AttackType.MELEE) {
                    lookBlood(kit);
                    quake(kit);
                    return true;
                }
                lookSteel(kit);
                return yank(kit);
            }
        };
    }

    /**
     * The charges, re-activations and cooldowns of the warrior's own skills: the blood knight's 小 2 charges and 中 a re-activation, the swords' 小
     * 3 charges and 中 a re-activation; their own seconds (the class's cooldowns are not used by them). With a deployed weapon none.
     */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot, AttackType kind) {
        if (kind == AttackType.MELEE) {
            String base = "combat.combo." + MELEE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 2.0)), 0.0,
                        config.getDouble(base + "small-charge-seconds", 5.0), config.getDouble(base + "small-empty-seconds", 10.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 3.0), 0.0, config.getDouble(base + "medium-seconds", 11.0));
                case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 26.0));
                case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 6.0));
                default -> SkillSlots.Mechanics.NONE;
            };
        }
        if (kind == AttackType.RANGE) {
            String base = "combat.combo." + RANGE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 3.0)), 0.0,
                        config.getDouble(base + "small-charge-seconds", 3.5), config.getDouble(base + "small-empty-seconds", 9.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 4.0), 0.0, config.getDouble(base + "medium-seconds", 10.0));
                case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 24.0));
                case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 6.0));
                default -> SkillSlots.Mechanics.NONE;
            };
        }
        return SkillSlots.Mechanics.NONE;
    }

    private static boolean reworked(AttackService attackService, Player player, RpgItemDefinition weapon) {
        AttackType kind = weapon.attackType();
        return (kind == AttackType.MELEE || kind == AttackType.RANGE) && attackService.hasClassCombo(player, weapon);
    }

    /** The blood knight's look: old blood and crimson. */
    static void lookBlood(MoveKit kit) {
        kit.palette(BLOOD);
        kit.slashColors(BLOOD_SLASH);
    }

    /** The swords' look: pale spectral steel. */
    static void lookSteel(MoveKit kit) {
        kit.palette(WarriorSwords.STEEL);
        kit.slashColors(STEEL_SLASH);
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

    private static Vector flat(Vector vector) {
        Vector flat = vector.clone().setY(0.0);
        return flat.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : flat.normalize();
    }

    // ---- the blood ------------------------------------------------------------------------------------------------------------

    private static long fadeMillis() {
        return Math.round(number(MELEE, "rally-seconds", 5.0) * 1000.0);
    }

    /** The blood of a skill: drinks {@code rally} times as fast, drains {@code drain} of its damage (at most {@code capPercent} of the life), bleeds. */
    static Blood blood(MoveKit kit, double rally, double drain, double capPercent, double bleed) {
        double maxHp = attack == null ? 0.0 : attack.profileForPlayer(kit.player).maxHp();
        return new Blood(rally, drain, maxHp * capPercent / 100.0, bleed);
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

    /**
     * A warrior took {@code damage} ({@link AttackService#applyPlayerDamage}): a share of it stays as a wound, and during the oath it is counted.
     */
    public static void taken(Player player, double maxHp, double damage) {
        if (damage <= 0.0 || attack == null) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        WarriorBlood.wound(id, damage, number(MELEE, "rally-share", 0.5), maxHp * number(MELEE, "rally-cap", 50.0) / 100.0, fadeMillis(), now);
        Oath oath = OATHS.get(id);
        if (oath != null && oath.until > now) oath.taken += damage;
    }

    /**
     * A warrior's hit landed ({@link ClassEffectService#onDamageDealt}): with a melee weapon it drinks back the wound ({@code rally-rate} of the
     * damage), and a skill's hit drains and bleeds as it says.
     */
    public static void dealt(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage) {
        if (attack == null || weapon == null || weapon.attackType() != AttackType.MELEE || damage <= 0.0) return;
        UUID id = attacker.getUniqueId();
        Blood blood = BLOOD_NOW.get(id);
        long now = System.currentTimeMillis();
        Oath oath = OATHS.get(id);
        double oathRally = oath != null && oath.until > now ? number(MELEE, "large-rally", 1.5) : 1.0;
        double rate = number(MELEE, "rally-rate", 0.6) * (blood == null ? 1.0 : blood.rally) * oathRally;
        double drunk = WarriorBlood.drink(id, damage * rate, fadeMillis(), now);
        double drained = 0.0;
        if (blood != null && blood.drain > 0.0 && blood.capLeft > 0.0) {
            drained = Math.min(blood.capLeft, damage * blood.drain);
            blood.capLeft -= drained;
        }
        if (blood != null && blood.bleed > 0.0 && victim.isValid() && !victim.isDead()) attack.statusEffects().addBleed(victim, damage * blood.bleed, id);
        double heal = drunk + drained;
        if (heal <= 0.0) return;
        attack.healPlayer(attacker, heal);
        drawDrink(attacker, victim);
    }

    /** The wound a warrior can still drink back (the status shows it by the HP while they hold a melee weapon), 0 when none. */
    public static double woundShown(Player player) {
        if (attack == null) return 0.0;
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

    /** Blood bursts from an enemy cut along {@code direction}. */
    static void bled(MoveKit kit, LivingEntity victim, Vector direction) {
        Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        Vector way = direction.lengthSquared() < 1.0E-6 ? kit.facing() : direction.clone().normalize();
        Fx fx = kit.fx(at);
        fx.line(at.clone().subtract(way.clone().multiply(0.4)), at.clone().add(way.clone().multiply(1.1)).subtract(0.0, 0.3, 0.0), BLOOD, 0.2, 0.9, 1.5, 1.0, 0.25, null, 1);
        fx.sphereShell(at, 0.3, 0.0, 8, BLOOD, 0.8);
    }

    /** Cuts every enemy within {@code reach} and {@code halfAngle} degrees ahead: {@code rate} with {@code blood}; how many were hit. */
    static int cutAhead(MoveKit kit, double reach, double halfAngle, double rate, Blood blood) {
        int hit = 0;
        Vector facing = kit.facing();
        for (LivingEntity victim : kit.cone(reach, halfAngle)) {
            if (!strike(kit, victim, rate, blood)) continue;
            bled(kit, victim, facing);
            hit++;
        }
        return hit;
    }

    /** Spikes of blood out of the ground round {@code at} ({@code count} of them at {@code radius}), rising over a few ticks. */
    static void spikes(MoveKit kit, Location at, double radius, int count, double height) {
        double turn = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        for (int index = 0; index < count; index++) {
            double angle = turn + index * Math.PI * 2.0 / count;
            Location base = at.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            Vector lean = new Vector(Math.cos(angle) * 0.25, 1.0, Math.sin(angle) * 0.25).normalize();
            for (int step = 0; step < 3; step++) {
                double rise = height * (step + 1) / 3.0;
                kit.later(step, () -> kit.fx(base).line(base, base.clone().add(lean.clone().multiply(rise)), BLOOD, 0.1, 1.0, 2.0, 1.1, 0.25, null, 1));
            }
        }
    }

    // ---- the blood knight: 小 --------------------------------------------------------------------------------------------------------

    /** Blood rend: two cuts that cross ahead, one from each side; they drain and bleed. */
    private static void rend(MoveKit kit) {
        double rate = melee(kit, "small-rate", 0.6);
        double reach = melee(kit, "small-reach", 4.2);
        Blood blood = blood(kit, 1.0, melee(kit, "small-drain", 0.2), melee(kit, "small-drain-cap", 6.0), melee(kit, "small-bleed", 0.25));
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
                fx.line(mark.clone().add(axis.clone().multiply(-1.4)).add(0.0, 0.12, 0.0), mark.clone().add(axis.clone().multiply(1.4)).add(0.0, 0.12, 0.0), BLOOD, 0.3, 0.9, 0.0, 1.0, 0.2, null, 1);
            }
        });
    }

    // ---- the blood knight: 中 --------------------------------------------------------------------------------------------------------

    /** Crimson whirl: a spin of a second; everyone round is cut every 4 ticks (draining) and drawn in a little, the warrior guarded meanwhile. */
    private static void whirl(MoveKit kit) {
        double rate = melee(kit, "medium-rate", 0.28);
        double radius = melee(kit, "medium-radius", 3.8);
        double pull = melee(kit, "medium-pull", 0.05);
        Blood blood = blood(kit, 1.0, melee(kit, "medium-drain", 0.1), melee(kit, "medium-drain-cap", 5.0), 0.0);
        kit.guard(melee(kit, "medium-guard", 0.3), 22);
        kit.sound(kit.player.getLocation(), Sfx.BLOOD_WHIRL, 1.0f, 1.0f);
        kit.every(21, age -> {
            Location at = kit.player.getLocation();
            if (age % 4 == 0) {
                kit.slash("arc", radius * 0.8, 330.0, 75.0, age % 8 == 0 ? 0.0 : 180.0, 0.3, 0.6, 0.0, 1.35, 0.08, 0.0);
                kit.sound(at, Sfx.BLOOD_SWING, 0.5f, 1.05f + 0.05f * (age / 4));
                for (LivingEntity victim : kit.around(at, radius)) {
                    Vector off = victim.getLocation().toVector().subtract(at.toVector()).setY(0.0);
                    if (strike(kit, victim, rate, blood)) bled(kit, victim, new Vector(-off.getZ(), 0.0, off.getX()));
                }
            }
            for (LivingEntity victim : kit.around(at, radius + 1.5)) kit.drift(victim, at, pull);
            Fx fx = kit.fx(at);
            fx.flatRing(at.clone().add(0.0, 0.9 + 0.08 * Math.sin(age * 0.6), 0.0), radius * 0.85, 34, BLOOD, 0.35 + 0.04 * (age % 10), 0.4, 1.1);
            if (age % 3 == 0) {
                double angle = age * 0.9;
                Location from = at.clone().add(Math.cos(angle) * radius * 0.8, 0.9, Math.sin(angle) * radius * 0.8);
                fx.line(from, from.clone().add(Math.cos(angle) * 1.2, -0.6, Math.sin(angle) * 1.2), BLOOD, 0.5, 0.15, 0.0, 0.9, 0.25, null, 1);
            }
            return false;
        });
    }

    /** The re-activation of 中: a slam on the spot that throws up everyone round and drinks the wound twice as fast. */
    private static void quake(MoveKit kit) {
        double rate = melee(kit, "recast-rate", 1.3);
        double radius = melee(kit, "recast-radius", 4.0);
        Blood blood = blood(kit, melee(kit, "recast-rally", 2.0), 0.0, 0.0, 0.0);
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

    // ---- the blood knight: 大 --------------------------------------------------------------------------------------------------------

    /**
     * Blood oath: for {@code large-duration} (8 s) the enemies within {@code large-radius} turn on the warrior, who takes {@code large-guard} less
     * damage; the wound does not fade and every hit drinks it {@code large-rally} times as fast. At the end the blood bursts out round them:
     * {@code large-burst-rate}, and {@code large-burst-per-percent} more for each percent of the life taken meanwhile (at most
     * {@code large-burst-max} more).
     */
    private static void oath(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        double seconds = melee(kit, "large-duration", 8.0);
        int ticks = (int) Math.round(seconds * 20.0);
        long now = System.currentTimeMillis();
        long millis = Math.round(seconds * 1000.0);
        double radius = melee(kit, "large-radius", 10.0);
        Oath oath = new Oath(now + millis, attack.profileForPlayer(player).maxHp());
        OATHS.put(id, oath);
        WarriorBlood.hold(id, now + millis, fadeMillis(), now);
        kit.guard(melee(kit, "large-guard", 0.3), ticks);
        provoker.provoke(player, radius, millis);
        Location feet = player.getLocation();
        kit.sound(feet, Sfx.BLOOD_OATH, 1.0f, 1.0f);
        kit.shockwave(feet, 0.8, radius * 0.5, 6, 1.2);
        spikes(kit, feet, 1.6, 6, 2.4);
        kit.fx(feet).sphereShell(feet.clone().add(0.0, 1.0, 0.0), 1.4, 0.0, 30, BLOOD, 1.1);
        kit.every(ticks + 1, age -> {
            if (OATHS.get(id) != oath) return true;
            Location at = player.getLocation();
            if (age >= ticks) {
                OATHS.remove(id);
                burst(kit, oath);
                return true;
            }
            // the mobs that came near meanwhile turn on the warrior too
            if (age > 0 && age % 40 == 0) provoker.provoke(player, radius, oath.until - System.currentTimeMillis());
            if (age % 20 == 0) kit.sound(at, Sfx.BLOOD_HEARTBEAT, 0.8f, 1.0f);
            if (age % 2 == 0) {
                Fx fx = kit.fx(at);
                double beat = age % 20 < 4 ? 1.25 : 1.0;
                fx.flatRing(at.clone().add(0.0, 0.1, 0.0), 1.3 * beat, 26, BLOOD, 0.5, 0.35, 1.1);
                double angle = age * 0.35;
                for (int strand = 0; strand < 2; strand++) {
                    double a = angle + strand * Math.PI;
                    double y = (age * 0.09 + strand * 1.0) % 2.0;
                    fx.dust(at.getX() + Math.cos(a) * 0.7, at.getY() + 0.1 + y, at.getZ() + Math.sin(a) * 0.7, BLOOD, 0.4 + 0.3 * y, 1.0);
                }
                if (age % 10 == 0) fx.flatRing(at.clone().add(0.0, 0.12, 0.0), radius, 60, BLOOD, 0.15, 0.2, 0.8);
            }
            return false;
        });
    }

    /** The end of the oath: the blood bursts out round the warrior, harder the more they took. */
    private static void burst(MoveKit kit, Oath oath) {
        double radius = melee(kit, "large-burst-radius", 5.0);
        double taken = oath.maxHp <= 0.0 ? 0.0 : oath.taken / oath.maxHp * 100.0;
        double rate = melee(kit, "large-burst-rate", 1.2) + Math.min(melee(kit, "large-burst-max", 2.0), taken * melee(kit, "large-burst-per-percent", 0.04));
        Location at = kit.player.getLocation();
        kit.sound(at, Sfx.BLOOD_BURST, 1.0f, 1.0f);
        kit.shockwave(at, 1.0, radius + 0.5, 7, 1.4);
        spikes(kit, at, radius * 0.45, 10, 2.6);
        kit.later(2L, () -> spikes(kit, kit.player.getLocation(), radius * 0.85, 14, 2.0));
        kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 2.2, 0.0, 40, BLOOD, 1.2);
        for (LivingEntity victim : kit.around(at, radius)) {
            if (!strike(kit, victim, rate, null)) continue;
            kit.push(victim, at.toVector(), 0.55, 0.3);
            bled(kit, victim, new Vector(0.0, 1.0, 0.0));
        }
    }

    // ---- the blood knight: util -------------------------------------------------------------------------------------------------------

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
                        BLOOD, 0.4, 1.0, 0.0, 0.8, 0.15, null, 1);
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
        Location at = player.getLocation().add(0.0, 1.0, 0.0);
        Sfx.BLOOD_PARRY.play(at, 1.0f, 1.0f);
        Fx.of(player, player.getWorld(), at).sphereShell(at, 0.8, 0.0, 18, BLOOD, 1.0);
        // (struck back the next tick, out of the blow's damage event)
        RpgItemDefinition weapon = (RpgItemDefinition) stance[1];
        Bukkit.getScheduler().runTask(attack.plugin(), () -> riposte(player, weapon));
        return true;
    }

    /** The answer to a parried blow: a lunge (nothing hurts the warrior) and a cut that drinks the wound twice as fast, and a little life back. */
    private static void riposte(Player player, RpgItemDefinition weapon) {
        if (!player.isOnline() || player.isDead() || attack == null) return;
        MoveKit kit = attack.skillKit(player, weapon);
        if (kit == null) return;
        lookBlood(kit);
        Vector heading = kit.facing();
        double rate = melee(kit, "riposte-rate", 1.3);
        Blood blood = blood(kit, melee(kit, "riposte-rally", 2.0), 0.0, 0.0, 0.0);
        kit.invulnerable(10);
        kit.heal(melee(kit, "riposte-heal", 3.0));
        kit.dash(heading, 0.9, 4, end -> {
            kit.slash("arc", 3.4, 160.0, 25.0, 0.0, 1.6, 0.35, 0.0, 1.5, 0.12, 0.0);
            kit.sound(end, Sfx.BLOOD_HIT, 1.0f, 1.1f);
            cutAhead(kit, 3.8, 70.0, rate, blood);
        });
    }

    // ---- swords and chains: helpers -----------------------------------------------------------------------------------------------------

    /** One hit of a sword: {@code bound-bonus} more on a bound enemy; a glint of steel where it struck. */
    static boolean swordHit(MoveKit kit, LivingEntity victim, double rate) {
        boolean bound = WarriorSwords.bound(victim);
        if (!kit.hit(victim, bound ? rate * (1.0 + range(kit, "bound-bonus", 0.3)) : rate)) return false;
        Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        kit.particle(Particle.CRIT, at, bound ? 8 : 4, 0.25, 0.15);
        if (bound) kit.fx(at).sphereShell(at, 0.45, 0.0, 10, WarriorSwords.CHAIN, 0.8);
        return true;
    }

    /** Where the warrior aims, at most {@code range} blocks away. */
    static Location aimWithin(MoveKit kit, double range) {
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        if (way.length() > range) aim = eye.clone().add(way.normalize().multiply(range));
        return aim;
    }

    /** The swords keep hovering behind the warrior a while after this. */
    static void hover(MoveKit kit) {
        WarriorSwords.hover(kit, range(kit, "hover-seconds", 6.0));
    }

    // ---- swords and chains: 小 ---------------------------------------------------------------------------------------------------------

    private static int piercerSide;

    /** Piercing greatsword: a greatsword flies straight from behind the warrior's shoulder, through every enemy in the way. */
    private static void piercer(MoveKit kit) {
        hover(kit);
        double rate = range(kit, "small-rate", 0.85);
        double reach = range(kit, "small-range", 20.0);
        Location from = WarriorSwords.launch(kit.player, piercerSide++ & 1);
        Vector way = aimWithin(kit, reach).toVector().subtract(from.toVector());
        if (way.lengthSquared() < 1.0E-6) way = kit.axes()[0];
        Location far = from.clone().add(way.normalize().multiply(reach));
        kit.sound(from, Sfx.SWORD_FLY, 1.0f, 0.9f);
        WarriorSwords.fly(kit, from, far, 2.4, range(kit, "small-speed", 1.6), 1.2, victim -> swordHit(kit, victim, rate), end -> {
            kit.burst(end, 1.0);
            kit.sound(end, Sfx.SWORD_IMPACT, 0.7f, 1.1f);
        });
    }

    // ---- swords and chains: 中 ---------------------------------------------------------------------------------------------------------

    /**
     * Binding chain: a chain flies to the enemy aimed at (or the nearest ahead within {@code medium-range}); it is struck, a sword falls beside it,
     * and it is bound there ({@code medium-bind-radius}) for {@code medium-bind-seconds}, slowed. With no enemy the chain flies out and falls.
     */
    private static void bindingChain(MoveKit kit) {
        hover(kit);
        Player player = kit.player;
        double reach = range(kit, "medium-range", 18.0);
        LivingEntity target = kit.nearest(reach, 30.0);
        Location hand = ComboParts.hand(kit);
        kit.sound(hand, Sfx.CHAIN_THROW, 1.0f, 1.0f);
        if (target == null) {
            Location end = aimWithin(kit, reach);
            throwChain(kit, hand, () -> end, null);
            return;
        }
        UUID id = player.getUniqueId();
        throwChain(kit, hand, () -> target.getLocation().add(0.0, target.getHeight() * 0.55, 0.0), () -> {
            if (!target.isValid() || target.isDead()) return;
            swordHit(kit, target, range(kit, "medium-rate", 0.5));
            int ticks = (int) Math.round(range(kit, "medium-bind-seconds", 4.0) * 20.0);
            Location spot = kit.ground(target.getLocation());
            Vector side = flat(spot.toVector().subtract(player.getLocation().toVector())).rotateAroundY(Math.PI / 2.0).multiply(0.9);
            Location anchor = spot.clone().add(side);
            kit.sound(spot, Sfx.CHAIN_BIND, 1.0f, 1.0f);
            WarriorSwords.fall(kit, anchor, 1.8, 4.0, 3, ticks, land -> kit.sound(land, Sfx.SWORD_IMPACT, 0.6f, 1.2f));
            kit.slow(target, range(kit, "medium-slow", 30.0), ticks / 20.0);
            CHAINED.put(id, target.getUniqueId());
            WarriorSwords.bind(kit, target, anchor.clone().add(0.0, 0.6, 0.0), range(kit, "medium-bind-radius", 1.2), ticks, gone -> CHAINED.remove(id, gone.getUniqueId()));
        });
    }

    /** A chain flung from {@code hand} to where {@code end} says (it may move), 2 blocks a tick; {@code onReach} when it gets there (null: it falls). */
    private static void throwChain(MoveKit kit, Location hand, java.util.function.Supplier<Location> end, Runnable onReach) {
        double[] out = {0.0};
        kit.every(14, age -> {
            Location to = end.get();
            if (to.getWorld() != hand.getWorld()) return true;
            Location from = ComboParts.hand(kit);
            Vector way = to.toVector().subtract(from.toVector());
            double length = way.length();
            out[0] += 2.0;
            if (out[0] >= length) {
                WarriorSwords.chain(kit, from, to, 0.1);
                if (onReach != null) onReach.run();
                else {
                    kit.particle(Particle.CRIT, to, 6, 0.3, 0.1);
                    kit.sound(to, Sfx.CHAIN_BREAK, 0.5f, 1.3f);
                }
                return true;
            }
            WarriorSwords.chain(kit, from, from.clone().add(way.normalize().multiply(out[0])), 0.05);
            return false;
        });
    }

    /** The re-activation of 中: the bound enemy is yanked to the warrior and the hovering swords stab it; false when nothing is bound. */
    private static boolean yank(MoveKit kit) {
        Player player = kit.player;
        UUID chained = CHAINED.remove(player.getUniqueId());
        Entity entity = chained == null ? null : Bukkit.getEntity(chained);
        if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) return false;
        hover(kit);
        WarriorSwords.release(target);
        Location before = target.getLocation().add(0.0, target.getHeight() * 0.55, 0.0);
        WarriorSwords.chain(kit, ComboParts.hand(kit), before, 0.0);
        kit.sound(player.getLocation(), Sfx.CHAIN_THROW, 1.0f, 0.7f);
        kit.pull(target, player.getLocation().add(kit.facing().multiply(2.0)), 1.25);
        double rate = range(kit, "recast-rate", 0.45);
        for (int index = 0; index < 3; index++) {
            int which = index;
            kit.later(5L + index * 3L, () -> {
                if (!target.isValid() || target.isDead()) return;
                Location from = WarriorSwords.launch(player, which & 1).add(0.0, 0.4 * which, 0.0);
                Location to = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
                Vector way = to.toVector().subtract(from.toVector());
                if (way.lengthSquared() < 1.0E-6) return;
                Location past = to.clone().add(way.normalize().multiply(1.0));
                kit.sound(from, Sfx.SWORD_FLY, 0.7f, 1.2f + 0.1f * which);
                WarriorSwords.fly(kit, from, past, 1.7, 1.8, 0.9, victim -> {
                    if (victim.equals(target)) swordHit(kit, victim, rate);
                }, null);
            });
        }
        return true;
    }

    // ---- swords and chains: 大 ---------------------------------------------------------------------------------------------------------

    /**
     * Prison of swords: eight greatswords fall in a ring ({@code large-radius} 5) at the aim, chained one to the next; for
     * {@code large-prison-seconds} the enemies inside cannot get out (they are bound in it) and the swords stab inward every half second
     * ({@code large-stab-rate}); then the eight rise and plunge into the middle ({@code large-final-rate}, thrown up).
     */
    private static void prison(MoveKit kit) {
        hover(kit);
        double radius = range(kit, "large-radius", 5.0);
        int held = (int) Math.round(range(kit, "large-prison-seconds", 4.0) * 20.0);
        double stab = range(kit, "large-stab-rate", 0.22);
        double finale = range(kit, "large-final-rate", 1.4);
        Location center = kit.ground(aimWithin(kit, range(kit, "large-range", 18.0)));
        int count = 8;
        int fall = 2 * count + 4;
        List<ItemDisplay> swords = new ArrayList<>();
        List<Location> posts = new ArrayList<>();
        double turn = Math.atan2(kit.facing().getZ(), kit.facing().getX());
        kit.sound(center, Sfx.SWORD_SUMMON, 1.0f, 0.7f);
        for (int index = 0; index < count; index++) {
            double angle = turn + index * Math.PI * 2.0 / count;
            Location ground = kit.ground(center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
            posts.add(ground);
            kit.later(index * 2L, () -> swords.add(WarriorSwords.fall(kit, ground, 2.2, 7.0, 4, held + fall + 30, land -> {
                kit.sound(land, Sfx.SWORD_IMPACT, 0.7f, 0.9f + 0.03f * swords.size());
                kit.particle(Particle.CRIT, land.clone().add(0.0, 0.3, 0.0), 6, 0.3, 0.1);
            })));
        }
        Set<UUID> inside = new HashSet<>();
        kit.every(fall + held + 1, age -> {
            if (age < fall) return false;
            int t = age - fall;
            if (t == 0) kit.sound(center, Sfx.CHAIN_BIND, 1.0f, 0.8f);
            // the chains between the swords, and the floor of the prison
            if (t % 3 == 0) {
                for (int index = 0; index < count; index++) {
                    Location a = posts.get(index).clone().add(0.0, 1.0, 0.0);
                    Location b = posts.get((index + 1) % count).clone().add(0.0, 1.0, 0.0);
                    WarriorSwords.chain(kit, a, b, 0.35);
                }
                kit.fx(center).flatRing(center.clone().add(0.0, 0.1, 0.0), radius, 50, WarriorSwords.STEEL, 0.3, 0.2, 0.8);
            }
            // nobody inside gets out
            for (LivingEntity victim : kit.around(center, radius + 0.3)) {
                if (inside.add(victim.getUniqueId())) WarriorSwords.bind(kit, victim, center, Math.max(0.5, radius - 0.6), held - t + 1, null);
            }
            if (t > 0 && t % 10 == 0) {
                kit.sound(center, Sfx.SWORD_IMPACT, 0.6f, 1.3f);
                for (LivingEntity victim : kit.around(center, radius)) {
                    if (!swordHit(kit, victim, stab)) continue;
                    Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                    Vector out = at.toVector().subtract(center.toVector()).setY(0.0);
                    Location from = at.clone().add(out.lengthSquared() < 1.0E-6 ? new Vector(1.0, 0.0, 0.0) : out.normalize().multiply(1.5));
                    kit.fx(from).line(from, at, WarriorSwords.STEEL, 0.2, 1.0, 1.5, 1.0, 0.2, null, 1);
                }
            }
            if (t >= held) {
                plunge(kit, center, radius, finale, swords);
                return true;
            }
            return false;
        });
    }

    /** The end of the prison: the swords rise and plunge into the middle, everyone inside struck and thrown up. */
    private static void plunge(MoveKit kit, Location center, double radius, double rate, List<ItemDisplay> swords) {
        Vector up = new Vector(0.0, 1.0, 0.0);
        for (ItemDisplay sword : swords) {
            if (!sword.isValid()) continue;
            WarriorSwords.move(sword, sword.getLocation().add(0.0, 3.0, 0.0), up, 2.2, 6);
        }
        kit.sound(center, Sfx.SWORD_SUMMON, 0.9f, 1.3f);
        kit.later(7L, () -> {
            Location middle = center.clone().add(0.0, 1.2, 0.0);
            for (ItemDisplay sword : swords) {
                if (!sword.isValid()) continue;
                Vector way = middle.toVector().subtract(sword.getLocation().toVector());
                WarriorSwords.move(sword, middle, way, 2.2, 4);
            }
        });
        kit.later(11L, () -> {
            kit.sound(center, Sfx.SWORD_GRAND, 1.0f, 1.0f);
            kit.shockwave(center, 0.8, radius + 0.5, 6, 1.4);
            kit.burst(center.clone().add(0.0, 1.0, 0.0), 2.4);
            kit.particle(Particle.EXPLOSION_LARGE, center.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0);
            for (LivingEntity victim : kit.around(center, radius)) {
                WarriorSwords.release(victim);
                if (swordHit(kit, victim, rate)) kit.lift(victim, 0.5);
            }
            for (ItemDisplay sword : swords) WarriorSwords.vanish(kit, sword, up, 5);
        });
    }

    // ---- swords and chains: util ---------------------------------------------------------------------------------------------------------

    /** Retreat: a leap back; a sword falls where the warrior stood and chains the enemies near it there. */
    private static void retreat(MoveKit kit) {
        hover(kit);
        Location spot = kit.ground(kit.player.getLocation());
        double radius = range(kit, "util-radius", 3.5);
        double rate = range(kit, "util-rate", 0.3);
        int ticks = (int) Math.round(range(kit, "util-bind-seconds", 2.0) * 20.0);
        double hold = range(kit, "util-bind-radius", 1.5);
        kit.hopBack(range(kit, "util-back", 0.9), range(kit, "util-up", 0.35));
        kit.sound(spot, Sfx.SWORD_FLY, 0.8f, 0.8f);
        WarriorSwords.fall(kit, spot, 2.0, 5.0, 5, ticks + 4, land -> {
            kit.sound(land, Sfx.CHAIN_BIND, 0.9f, 1.1f);
            kit.shockwave(land, 0.6, radius, 4, 1.1);
            for (LivingEntity victim : kit.around(land, radius)) {
                swordHit(kit, victim, rate);
                WarriorSwords.bind(kit, victim, land.clone().add(0.0, 0.6, 0.0), hold, ticks, null);
            }
        });
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        WarriorBlood.forget(player);
        WarriorSwords.forget(player);
        OATHS.remove(player);
        PARRIES.remove(player);
        CHAINED.remove(player);
        DRAWN.remove(player);
        BLOOD_NOW.remove(player);
    }
}
