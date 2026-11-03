package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The cleric's own skills (t168: nekorozv left the skills, the combos, the look, the effects and the sounds to us, keeping the class's concepts:
 * every way of fighting heals; t198: the staff attacks less and no longer looks like the mage's). Every number is under
 * {@code combat.combo.cleric_melee} / {@code cleric_range} (all provisional), the cooldowns too. The effects are drawn in the weapon's colors
 * (its element), with the gold of holy light as a touch (halos, motes, bells).
 *
 * <p><b>The mace and the shield</b> (a melee weapon of the class's combo; the second tank, who holds on and strikes at the openings with stuns and
 * knockbacks): the blows the cleric takes build <b>radiance</b> ({@code radiance-max} 5, lost {@code radiance-seconds} 6 after the last one; at
 * most one every half second from blows; golden motes round the cleric), and the stronger blows spend it all: each stack adds to the damage, the
 * stun and the heal. Stuns cannot be chained ({@link StatusEffectService#stunFor}: 5 s of immunity after one).
 * <ul>
 *   <li>小 shield bash: a step in and a bash of the shield: thrown back and stunned; spends the radiance.</li>
 *   <li>中 aegis: the shield raised for 1.5 s (much less damage; every blow taken gives radiance and a little life); its re-activation is the
 *       hammer of judgment, a slam round the cleric that stuns, spending the radiance.</li>
 *   <li>大 bastion (6 s): the enemies round turn on the cleric (the second tank), who takes less damage; the allies near are protected and healed
 *       and the radiance grows every second; at the end the light bursts out, throwing back and stunning.</li>
 *   <li>util shield charge (2 charges): a charge with the shield up through the enemies, thrown aside (radiance for each); with shift a hop
 *       back.</li>
 * </ul>
 *
 * <p><b>The staff</b> (a ranged weapon of the class's combo; blessings, heals, bells, feathers and halos of light; t198: no more lightning and
 * beams, and one skill that hurts): every normal attack that hits adds a <b>verse</b> ({@code verse-max} 5, lost {@code verse-seconds} 10 after
 * the last; small runes over the head), and the bell of 大 spends them.
 * <ul>
 *   <li>小 healing light (2 charges): a column of light on the ally looked at (else the cleric) heals them; the enemies round them are struck
 *       lightly.</li>
 *   <li>中 blessing: the cleric and the allies near deal more and take less for 8 s.</li>
 *   <li>大 sanctus bell: a bell of light at the aim rings three times, healing the allies under it (more for each verse) and slowing and striking
 *       the enemies; its re-activation calls the bell to the cleric, where it rings out at once, healing more and throwing the enemies back.</li>
 *   <li>util light step: a step of light ahead (back with shift); where it began, a glyph heals the allies on it for 3 s.</li>
 * </ul>
 * With a deployed weapon there are no skills; its combo is {@link ClericDeployCombo}. The ULT is the class's (the sanctuary).
 */
public final class ClericSkills {

    static final String MELEE = ClericMeleeCombo.PLAN;
    static final String RANGE = ClericRangeCombo.PLAN;
    /** The holy light: old gold, gold, white. */
    static final Gradient HOLY = Gradient.of(Color.fromRGB(110, 80, 20), Color.fromRGB(255, 212, 100), Color.fromRGB(255, 252, 232));
    /** The staff's lightning: a deep blue, pale blue, white. */

    private static AttackService attack;
    static final Stacks RADIANCE = new Stacks();
    static final Stacks VERSES = new Stacks();
    /** The clerics whose shield is raised (中): until when (ms). */
    private static final Map<UUID, Long> AEGIS = new HashMap<>();
    /** The tick each cleric last got radiance from a blow. */
    private static final Map<UUID, Integer> LAST_BLOW = new HashMap<>();
    /** The clerics whose radiance / verses are being drawn. */
    private static final Set<UUID> DRAWING_RADIANCE = new HashSet<>();
    private static final Set<UUID> DRAWING_VERSES = new HashSet<>();

    private ClericSkills() {
    }

    // ---- registering --------------------------------------------------------------------------------------------------------

    /** The cleric's skill {@code slot}: their own with a melee or ranged weapon of the class's combo, else the heavy attack it threw. */
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
                        case SMALL -> shieldBash(kit);
                        case MEDIUM -> aegis(kit);
                        case LARGE -> bastion(kit);
                        case UTIL -> shieldCharge(kit);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                }
                switch (slot) {
                    case SMALL -> healingLight(kit);
                    case MEDIUM -> blessing(kit);
                    case LARGE -> bell(kit);
                    case UTIL -> lightStep(kit);
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
                if (weapon.attackType() == AttackType.MELEE) {
                    if (slot != SkillSlots.Slot.MEDIUM) return false;
                    hammer(kit);
                    return true;
                }
                return slot == SkillSlots.Slot.LARGE && callBell(kit);
            }
        };
    }

    /**
     * The charges, re-activations and cooldowns of the cleric's own skills (their own seconds; t198: spread by what each skill is): with the mace
     * 中 has its re-activation and util 2 charges; with the staff 小 (a heal) 2 charges and 大 a re-activation for as long as its bell rings. With a
     * deployed weapon none.
     */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot, AttackType kind) {
        if (kind == AttackType.MELEE) {
            String base = "combat.combo." + MELEE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "small-seconds", 7.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 2.5), 0.0, config.getDouble(base + "medium-seconds", 12.0));
                case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 28.0));
                case UTIL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "util-charges", 2.0)), 0.0,
                        config.getDouble(base + "util-charge-seconds", 6.0), config.getDouble(base + "util-empty-seconds", 12.0));
                default -> SkillSlots.Mechanics.NONE;
            };
        }
        if (kind == AttackType.RANGE) {
            String base = "combat.combo." + RANGE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 2.0)), 0.0,
                        config.getDouble(base + "small-charge-seconds", 4.0), config.getDouble(base + "small-empty-seconds", 9.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "medium-seconds", 14.0));
                case LARGE -> new SkillSlots.Mechanics(1, config.getDouble(base + "large-bell-ticks", 120.0) / 20.0, 0.0,
                        config.getDouble(base + "large-seconds", 22.0));
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

    private static double number(String plan, String key, double fallback) {
        return attack == null ? fallback : attack.plugin().getConfig().getDouble("combat.combo." + plan + "." + key, fallback);
    }

    private static double melee(MoveKit kit, String key, double fallback) {
        return kit.number(MELEE, key, fallback);
    }

    private static double range(MoveKit kit, String key, double fallback) {
        return kit.number(RANGE, key, fallback);
    }

    // ---- radiance -------------------------------------------------------------------------------------------------------------

    /** {@code amount} more radiance for the cleric (drawn round them while they hold a melee weapon). */
    static void radiance(Player player, int amount) {
        long now = System.currentTimeMillis();
        int max = (int) Math.round(number(MELEE, "radiance-max", 5.0));
        long until = now + Math.round(number(MELEE, "radiance-seconds", 6.0) * 1000.0);
        int before = RADIANCE.count(player.getUniqueId(), now);
        int count = RADIANCE.add(player.getUniqueId(), amount, max, until, now);
        if (count >= max && before < max) Sfx.HOLY_FULL.play(player.getLocation(), 0.6f, 1.0f);
        drawRadiance(player);
    }

    /** Spends all the radiance: how many there were. */
    static int spendRadiance(Player player) {
        return RADIANCE.take(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * A cleric took a blow ({@link AttackService#applyPlayerDamage}): radiance (at most one every {@code radiance-gap-ticks} from blows; every blow
     * while the shield of 中 is raised, which also gives a little life back).
     */
    public static void taken(Player player, double damage) {
        if (attack == null || damage <= 0.0) return;
        UUID id = player.getUniqueId();
        Long aegis = AEGIS.get(id);
        if (aegis != null && aegis > System.currentTimeMillis()) {
            radiance(player, 1);
            double heal = attack.profileForPlayer(player).maxHp() * number(MELEE, "medium-heal", 1.5) / 100.0;
            // (after the blow, so the heal is not lost to a full life)
            Bukkit.getScheduler().runTask(attack.plugin(), () -> {
                if (player.isOnline() && !player.isDead()) attack.healPlayer(player, heal);
            });
            Location at = player.getLocation().add(0.0, 1.1, 0.0);
            Sfx.HOLY_BLOCK.play(at, 0.8f, 1.0f);
            Fx.of(player, player.getWorld(), at).sphereShell(at, 0.9, 0.0, 16, HOLY, 1.0);
            return;
        }
        int tick = Bukkit.getCurrentTick();
        Integer last = LAST_BLOW.get(id);
        if (last != null && tick - last < number(MELEE, "radiance-gap-ticks", 10.0)) return;
        LAST_BLOW.put(id, tick);
        radiance(player, 1);
    }

    /** The radiance as golden motes turning round the cleric's chest while there is any and they hold a melee weapon. */
    private static void drawRadiance(Player player) {
        if (attack == null || !DRAWING_RADIANCE.add(player.getUniqueId())) return;
        new SafeTask(attack.plugin()) {
            private int age;

            @Override
            protected void tick() {
                age += 2;
                int count = RADIANCE.count(player.getUniqueId(), System.currentTimeMillis());
                if (!player.isOnline() || player.isDead() || count <= 0) {
                    DRAWING_RADIANCE.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                RpgItemDefinition weapon = attack.weaponInMainHand(player);
                if (weapon == null || weapon.attackType() != AttackType.MELEE) return;
                Location chest = player.getLocation().add(0.0, 1.0, 0.0);
                Fx fx = Fx.of(player, player.getWorld(), chest);
                if (!fx.active()) return;
                for (int index = 0; index < count; index++) {
                    double angle = age * 0.12 + index * Math.PI * 2.0 / count;
                    fx.dust(chest.getX() + Math.cos(angle) * 0.75, chest.getY() + 0.15 * Math.sin(age * 0.2 + index), chest.getZ() + Math.sin(angle) * 0.75, HOLY, 0.85, 0.9);
                }
            }
        }.runTaskTimer(attack.plugin(), 0L, 2L);
    }

    /** Stuns {@code victim} for {@code seconds} (not when it was stunned within the immunity). */
    static void stun(MoveKit kit, LivingEntity victim, double seconds) {
        if (attack == null || seconds <= 0.0 || !victim.isValid() || victim.isDead()) return;
        if (attack.statusEffects().stunFor(victim, Math.round(seconds * 1000.0), kit.player.getUniqueId())) {
            Location head = victim.getLocation().add(0.0, victim.getHeight() + 0.3, 0.0);
            kit.fx(head).flatRing(head, 0.45, 12, HOLY, 1.0, 0.0, 0.9);
        }
    }

    /** A pillar of light rising from {@code at}. */
    static void lightPillar(MoveKit kit, Location at, double radius, double height) {
        Fx fx = kit.fx(at);
        for (double y = 0.0; y <= height; y += 0.45) {
            fx.flatRing(at.clone().add(0.0, y, 0.0), radius * (1.0 - 0.4 * y / height), 14, kit.palette(), 0.3 + 0.7 * y / height, 0.3, 1.0);
        }
    }

    // ---- the mace and the shield: 小 ----------------------------------------------------------------------------------------------------

    /** Shield bash: a step in and a bash of the shield: the enemies ahead are thrown back and stunned; the radiance is spent on it. */
    private static void shieldBash(MoveKit kit) {
        Player player = kit.player;
        Vector heading = kit.facing();
        kit.guard(0.3, 8);
        kit.sound(player.getLocation(), Sfx.HOLY_STEP, 0.7f, 1.1f);
        kit.dash(heading, 0.7, 3, end -> {
            int stacks = spendRadiance(player);
            double rate = melee(kit, "small-rate", 0.9) + melee(kit, "small-stack-rate", 0.2) * stacks;
            double stunned = melee(kit, "small-stun", 0.5) + melee(kit, "small-stack-stun", 0.15) * stacks;
            Location front = end.clone().add(heading.clone().multiply(1.2)).add(0.0, 1.0, 0.0);
            Vector side = new Vector(-heading.getZ(), 0.0, heading.getX());
            kit.fx(front).disc(front, side, new Vector(0.0, 1.0, 0.0), 0.9, 40, HOLY, 1.0, 1.1);
            kit.slash("line", 2.6, 90.0, 0.0, 0.0, 1.6, 0.2, 0.0, 1.3, 0.1, 0.0);
            kit.sound(end, Sfx.HOLY_BASH, 1.0f, 1.0f - 0.04f * stacks);
            kit.shockwave(end.clone().add(heading.clone().multiply(1.5)), 0.5, 2.2, 4, 1.1);
            for (LivingEntity victim : kit.cone(3.4, 55.0)) {
                if (!kit.hit(victim, rate)) continue;
                kit.push(victim, end.toVector(), 0.7, 0.15);
                stun(kit, victim, stunned);
            }
            if (stacks > 0) {
                kit.heal(melee(kit, "small-stack-heal", 1.0) * stacks);
                lightPillar(kit, end, 0.8, 2.0);
            }
        });
    }

    // ---- the mace and the shield: 中 ----------------------------------------------------------------------------------------------------

    /** Aegis: the shield raised for {@code medium-ticks}: much less damage, and every blow taken gives radiance and a little life. */
    private static void aegis(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        int ticks = (int) Math.round(melee(kit, "medium-ticks", 30.0));
        long until = System.currentTimeMillis() + ticks * 50L;
        AEGIS.put(id, until);
        kit.guard(melee(kit, "medium-guard", 0.6), ticks);
        kit.sound(player.getLocation(), Sfx.HOLY_RAISE, 1.0f, 1.0f);
        kit.every(ticks + 1, age -> {
            Long now = AEGIS.get(id);
            if (now == null || now != until) return true;
            if (age >= ticks) {
                AEGIS.remove(id);
                Location at = player.getLocation();
                kit.shockwave(at, 0.6, 2.6, 3, 1.0);
                for (LivingEntity victim : kit.around(at, 2.5)) kit.push(victim, at.toVector(), 0.35, 0.1);
                return true;
            }
            if (age % 2 == 0) {
                Vector facing = kit.facing();
                Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
                Location front = player.getLocation().add(0.0, 1.1, 0.0).add(facing.multiply(0.9));
                kit.fx(front).ring(front, side, new Vector(0.0, 1.0, 0.0), 0.75, 22, HOLY, 0.7, 0.3, 1.0);
                if (age % 6 == 0) kit.fx(front).disc(front, side, new Vector(0.0, 1.0, 0.0), 0.7, 18, HOLY, 1.0, 0.8);
            }
            return false;
        });
    }

    /** The re-activation of 中: the hammer of judgment, a slam round the cleric that stuns; the radiance is spent on it. */
    private static void hammer(MoveKit kit) {
        Player player = kit.player;
        AEGIS.remove(player.getUniqueId());
        int stacks = spendRadiance(player);
        double radius = melee(kit, "recast-radius", 3.5);
        double rate = melee(kit, "recast-rate", 1.2) + melee(kit, "recast-stack-rate", 0.25) * stacks;
        double stunned = melee(kit, "recast-stun", 0.8) + melee(kit, "recast-stack-stun", 0.1) * stacks;
        Location feet = player.getLocation();
        kit.slash("drop", 3.0, 140.0, 5.0, 0.0, 1.6, 0.4, 0.0, 1.6, 0.18, 0.0);
        kit.sound(feet, Sfx.HOLY_HAMMER, 1.0f, 1.0f);
        kit.shockwave(feet, 0.8, radius + 0.5, 6, 1.3);
        lightPillar(kit, feet.clone().add(kit.facing().multiply(1.5)), 1.0, 3.0 + 0.4 * stacks);
        for (LivingEntity victim : kit.around(feet, radius)) {
            if (!kit.hit(victim, rate)) continue;
            kit.push(victim, feet.toVector(), 0.4, 0.25);
            stun(kit, victim, stunned);
        }
        if (stacks > 0) kit.heal(melee(kit, "recast-stack-heal", 1.5) * stacks);
    }

    // ---- the mace and the shield: 大 ----------------------------------------------------------------------------------------------------

    /**
     * Bastion: for {@code large-duration} (6 s) the enemies within {@code large-provoke} turn on the cleric, who takes {@code large-guard} less; each
     * second the allies within {@code large-radius} are protected and healed and the cleric gains radiance; at the end the light bursts out.
     */
    private static void bastion(MoveKit kit) {
        Player player = kit.player;
        double seconds = melee(kit, "large-duration", 6.0);
        int ticks = (int) Math.round(seconds * 20.0);
        double radius = melee(kit, "large-radius", 5.0);
        double provoke = melee(kit, "large-provoke", 10.0);
        long end = System.currentTimeMillis() + ticks * 50L;
        kit.guard(melee(kit, "large-guard", 0.35), ticks);
        WarriorSkills.provoke(player, provoke, ticks * 50L);
        kit.sound(player.getLocation(), Sfx.HOLY_BASTION, 1.0f, 1.0f);
        kit.shockwave(player.getLocation(), 0.8, radius, 6, 1.2);
        kit.every(ticks + 1, age -> {
            Location at = player.getLocation();
            if (age >= ticks) {
                burstOfLight(kit, at, radius);
                return true;
            }
            if (age % 4 == 0) SkillFx.dome(player, at, radius, age, HOLY);
            if (age % 2 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.1, 0.0), radius, 50, HOLY, 0.5, 0.3, 1.0);
            if (age % 20 == 10) {
                radiance(player, 1);
                double heal = melee(kit, "large-heal", 1.5);
                for (Player ally : at.getWorld().getPlayers()) {
                    if (ally.isDead() || ally.getLocation().distanceSquared(at) > radius * radius) continue;
                    if (!ally.equals(player)) attack.statusEffects().applyBuff(ally, StatusEffectType.PROTECTION, melee(kit, "large-ally-protection", 25.0), 1500L, player.getUniqueId());
                    attack.healPlayerBy(player, ally, attack.profileForPlayer(ally).maxHp() * heal / 100.0);
                }
            }
            if (age > 0 && age % 40 == 0) WarriorSkills.provoke(player, provoke, end - System.currentTimeMillis());
            return false;
        });
    }

    /** The end of the bastion: the light bursts out round the cleric, throwing back and stunning. */
    private static void burstOfLight(MoveKit kit, Location at, double radius) {
        kit.sound(at, Sfx.HOLY_HAMMER, 1.0f, 1.2f);
        kit.shockwave(at, 1.0, radius + 0.5, 6, 1.4);
        lightPillar(kit, at, 1.4, 4.0);
        double rate = melee(kit, "large-burst-rate", 0.8);
        double stunned = melee(kit, "large-burst-stun", 0.6);
        for (LivingEntity victim : kit.around(at, radius)) {
            if (!kit.hit(victim, rate)) continue;
            kit.push(victim, at.toVector(), 0.7, 0.2);
            stun(kit, victim, stunned);
        }
    }

    // ---- the mace and the shield: util ----------------------------------------------------------------------------------------------------

    /** Shield charge: ahead with the shield up through the enemies, thrown aside (radiance for each, at most 2); with shift a hop back. */
    private static void shieldCharge(MoveKit kit) {
        Player player = kit.player;
        if (player.isSneaking()) {
            kit.hopBack(melee(kit, "util-back", 0.8), 0.3);
            kit.sound(player.getLocation(), Sfx.HOLY_STEP, 0.8f, 1.2f);
            return;
        }
        Vector heading = kit.facing();
        double power = melee(kit, "util-power", 1.0);
        int ticks = Math.max(1, (int) Math.round(melee(kit, "util-ticks", 5.0)));
        double rate = melee(kit, "util-rate", 0.3);
        Location from = player.getLocation().add(0.0, 1.0, 0.0);
        List<LivingEntity> passed = kit.corridor(from, heading, power * ticks + 1.0, 1.4);
        kit.guard(melee(kit, "util-guard", 0.5), ticks + 4);
        kit.sound(player.getLocation(), Sfx.HOLY_STEP, 1.0f, 0.9f);
        kit.every(ticks + 1, age -> {
            Location at = player.getLocation().add(0.0, 1.0, 0.0).add(heading.clone().multiply(0.8));
            Vector side = new Vector(-heading.getZ(), 0.0, heading.getX());
            kit.fx(at).disc(at, side, new Vector(0.0, 1.0, 0.0), 0.7, 16, HOLY, 0.9, 0.9);
            return false;
        });
        kit.dash(heading, power, ticks, end -> {
            int gained = 0;
            for (LivingEntity victim : passed) {
                if (!kit.hit(victim, rate)) continue;
                Vector off = victim.getLocation().toVector().subtract(end.toVector()).setY(0.0);
                Vector side = new Vector(-heading.getZ(), 0.0, heading.getX());
                Vector away = side.multiply(off.dot(side) >= 0.0 ? 1.0 : -1.0);
                kit.push(victim, victim.getLocation().toVector().subtract(away), 0.5, 0.15);
                if (gained < 2) gained++;
            }
            if (gained > 0) {
                radiance(player, gained);
                kit.sound(end, Sfx.HOLY_BASH, 0.7f, 1.2f);
            }
        });
    }

    // ---- verses ---------------------------------------------------------------------------------------------------------------------------

    /** A normal attack of the staff hit: one more verse. */
    static void verse(Player player) {
        long now = System.currentTimeMillis();
        int max = (int) Math.round(number(RANGE, "verse-max", 5.0));
        long until = now + Math.round(number(RANGE, "verse-seconds", 10.0) * 1000.0);
        int before = VERSES.count(player.getUniqueId(), now);
        int count = VERSES.add(player.getUniqueId(), 1, max, until, now);
        if (count >= max && before < max) Sfx.HOLY_FULL.play(player.getLocation(), 0.5f, 1.3f);
        drawVerses(player);
    }

    /** The verses as small runes of light in an arc over the cleric's head while there are any and they hold a ranged weapon. */
    private static void drawVerses(Player player) {
        if (attack == null || !DRAWING_VERSES.add(player.getUniqueId())) return;
        new SafeTask(attack.plugin()) {
            private int age;

            @Override
            protected void tick() {
                age += 2;
                int count = VERSES.count(player.getUniqueId(), System.currentTimeMillis());
                if (!player.isOnline() || player.isDead() || count <= 0) {
                    DRAWING_VERSES.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                RpgItemDefinition weapon = attack.weaponInMainHand(player);
                if (weapon == null || weapon.attackType() != AttackType.RANGE) return;
                Location head = player.getLocation().add(0.0, 2.35, 0.0);
                Fx fx = Fx.of(player, player.getWorld(), head);
                if (!fx.active()) return;
                Vector facing = player.getLocation().getDirection().setY(0.0);
                if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
                facing.normalize();
                Vector side = new Vector(-facing.getZ(), 0.0, facing.getX());
                for (int index = 0; index < count; index++) {
                    double t = count == 1 ? 0.0 : index / (double) (count - 1) - 0.5;
                    Location rune = head.clone().add(side.clone().multiply(t * 1.2)).add(0.0, -0.15 * t * t * 4.0 + 0.05 * Math.sin(age * 0.2 + index), 0.0);
                    fx.dust(rune.getX(), rune.getY(), rune.getZ(), HOLY, 0.9, 0.8);
                    fx.dust(rune.getX(), rune.getY() + 0.12, rune.getZ(), HOLY, 1.0, 0.5);
                }
            }
        }.runTaskTimer(attack.plugin(), 0L, 2L);
    }

    /** Heals the players within {@code radius} of {@code at} by {@code percent} at once (a skill's heal: not held to the staff's cap). */
    static void healAround(MoveKit kit, Location at, double radius, double percent) {
        for (Player ally : at.getWorld().getPlayers()) {
            if (ally.isDead() || ally.getLocation().distanceSquared(at) > radius * radius) continue;
            attack.healPlayerBy(kit.player, ally, attack.profileForPlayer(ally).maxHp() * percent / 100.0);
            Location chest = ally.getLocation().add(0.0, 1.0, 0.0);
            kit.fx(chest).sphereShell(chest, 0.7, 0.0, 12, HOLY, 0.9);
        }
    }

    // ---- the staff: 小 --------------------------------------------------------------------------------------------------------------------

    /**
     * Healing light (2 charges): the ally looked at within {@code small-range} (else the cleric) is healed {@code small-heal} (a column of light
     * falls on them, feathers drift down, a halo over the head); the enemies within {@code small-radius} of them are struck ({@code small-rate}).
     */
    private static void healingLight(MoveKit kit) {
        Player player = kit.player;
        Player ally = lookedAt(kit, range(kit, "small-range", 20.0));
        Player healed = ally == null ? player : ally;
        Location feet = healed.getLocation();
        kit.sound(ComboParts.hand(kit), Sfx.HOLY_MEND, 0.9f, 1.2f);
        if (ally != null) kit.fx(ComboParts.hand(kit)).line(ComboParts.hand(kit), feet.clone().add(0.0, 1.0, 0.0), HOLY, 0.3, 1.0, 1.0, 0.8, 0.3, null, 1);
        attack.healPlayerBy(player, healed, attack.profileForPlayer(healed).maxHp() * range(kit, "small-heal", 6.0) / 100.0);
        descend(kit, feet, 1.1, 5.0);
        double radius = range(kit, "small-radius", 2.5);
        double rate = range(kit, "small-rate", 0.3);
        kit.later(4L, () -> {
            Location at = healed.getLocation();
            kit.sound(at, Sfx.HOLY_BELL, 0.5f, 1.6f);
            kit.shockwave(at, 0.4, radius, 4, 1.0);
            for (LivingEntity victim : kit.around(at, radius)) {
                if (kit.hit(victim, rate)) kit.push(victim, at.toVector(), 0.25, 0.1);
            }
        });
    }

    /** The other player nearest the line of sight within {@code reach} (at most 12 degrees off it), or null. */
    static Player lookedAt(MoveKit kit, double reach) {
        Location eye = kit.eye();
        Vector look = eye.getDirection().normalize();
        Player best = null;
        double bestAngle = Math.toRadians(12.0);
        for (Player other : eye.getWorld().getPlayers()) {
            if (other.equals(kit.player) || other.isDead()) continue;
            Vector to = other.getLocation().add(0.0, 1.0, 0.0).toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance > reach || distance < 1.0E-6) continue;
            double angle = to.angle(look);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = other;
            }
        }
        return best;
    }

    /**
     * Holy light coming down on {@code feet}: a column in the weapon's colors from {@code height} blocks up, a golden halo over the head, and
     * feathers of light drifting down.
     */
    static void descend(MoveKit kit, Location feet, double radius, double height) {
        Location top = feet.clone().add(0.0, height, 0.0);
        for (int step = 0; step < 5; step++) {
            int which = step;
            kit.later(step, () -> {
                double y = height * (1.0 - (which + 1) / 5.0);
                Fx fx = kit.fx(feet);
                fx.line(top, feet.clone().add(0.0, y, 0.0), kit.palette(), 0.4, 1.0, 0.0, 1.2, 0.2, null, 1);
                fx.flatRing(feet.clone().add(0.0, y + 0.1, 0.0), radius * (0.6 + 0.4 * which / 4.0), 16, kit.palette(), 0.6, 0.2, 1.0);
            });
        }
        kit.later(5L, () -> {
            Location head = feet.clone().add(0.0, 2.3, 0.0);
            kit.fx(head).flatRing(head, 0.45, 14, HOLY, 1.0, 0.0, 0.9);
            kit.particle(Particle.END_ROD, feet.clone().add(0.0, 2.5, 0.0), 6, 0.6, 0.01);
            kit.fx(feet).flatRing(feet.clone().add(0.0, 0.1, 0.0), radius, 22, HOLY, 0.8, 0.2, 1.0);
        });
    }

    // ---- the staff: 中 --------------------------------------------------------------------------------------------------------------------

    /** Blessing: the cleric and the allies within {@code medium-radius} deal more and take less for {@code medium-buff-seconds}. */
    private static void blessing(MoveKit kit) {
        Player player = kit.player;
        Location at = player.getLocation();
        double radius = range(kit, "medium-radius", 8.0);
        long millis = Math.round(range(kit, "medium-buff-seconds", 8.0) * 1000.0);
        double might = range(kit, "medium-might", 15.0);
        double protection = range(kit, "medium-protection", 15.0);
        kit.sound(at, Sfx.HOLY_BLESS, 1.0f, 1.0f);
        kit.sound(at, Sfx.HOLY_BELL, 0.6f, 1.2f);
        kit.shockwave(at, 0.8, radius, 7, 1.2);
        for (Player ally : at.getWorld().getPlayers()) {
            if (ally.isDead() || ally.getLocation().distanceSquared(at) > radius * radius) continue;
            attack.statusEffects().applyBuff(ally, StatusEffectType.MIGHT, might, millis, player.getUniqueId());
            attack.statusEffects().applyBuff(ally, StatusEffectType.PROTECTION, protection, millis, player.getUniqueId());
            Location feet = ally.getLocation();
            for (int step = 0; step < 6; step++) {
                int which = step;
                kit.later(step * 2L, () -> kit.fx(feet).flatRing(feet.clone().add(0.0, 0.2 + 0.35 * which, 0.0), 0.8 - 0.08 * which, 16, which % 2 == 0 ? kit.palette() : HOLY, 0.4 + 0.1 * which, 0.2, 1.0));
            }
        }
    }

    // ---- the staff: 大 --------------------------------------------------------------------------------------------------------------------

    /** The bell of each cleric while it rings (for the re-activation of 大). */
    private static final Map<UUID, Bell> BELLS = new HashMap<>();

    /** A sanctus bell: where it hangs (it can be called to the cleric), what each ring heals, and whether it rang out. */
    private static final class Bell {
        Location at;
        final double heal;
        boolean done;

        Bell(Location at, double heal) {
            this.at = at;
            this.heal = heal;
        }
    }

    /**
     * Sanctus bell: a bell of light at the aim (at most {@code large-range} away) for {@code large-bell-ticks}; it rings {@code large-rings} times
     * (the first a moment after it appears): the allies within {@code large-radius} are healed {@code large-heal} (and {@code large-verse-heal} more
     * for each verse spent when it was rung up), the enemies slowed ({@code large-slow} for 1.5 s) and struck ({@code large-rate}).
     */
    private static void bell(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        int verses = VERSES.take(id, System.currentTimeMillis());
        double heal = range(kit, "large-heal", 4.0) * (1.0 + range(kit, "large-verse-heal", 0.1) * verses);
        double radius = range(kit, "large-radius", 5.0);
        int life = (int) Math.round(range(kit, "large-bell-ticks", 120.0));
        int rings = Math.max(1, (int) Math.round(range(kit, "large-rings", 3.0)));
        int gap = Math.max(1, (life - 10) / rings);
        double rate = range(kit, "large-rate", 0.35);
        double slow = range(kit, "large-slow", 30.0);
        Location eye = kit.eye();
        Location aim = kit.aim();
        double reach = range(kit, "large-range", 20.0);
        Vector way = aim.toVector().subtract(eye.toVector());
        if (way.length() > reach) aim = eye.clone().add(way.normalize().multiply(reach));
        Bell bell = new Bell(kit.ground(aim), heal);
        BELLS.put(id, bell);
        kit.sound(bell.at, Sfx.HOLY_CHANT, 1.0f, 1.1f);
        kit.every(life + 1, age -> {
            if (bell.done || BELLS.get(id) != bell) return true;
            if (age >= life) {
                BELLS.remove(id, bell);
                kit.fx(bell.at).sphereShell(bell.at.clone().add(0.0, 2.6, 0.0), 0.8, 0.0, 14, HOLY, 0.8);
                return true;
            }
            double drop = Math.max(0.0, 1.0 - age / 6.0);
            if (age % 2 == 0) {
                drawBell(kit, bell.at.clone().add(0.0, 2.2 + 3.0 * drop, 0.0), 0.9, Math.sin(age * 0.35) * (age % gap < 8 ? 0.25 : 0.05));
                kit.fx(bell.at).flatRing(bell.at.clone().add(0.0, 0.12, 0.0), radius, 44, kit.palette(), 0.5, 0.2, 1.0);
            }
            if (age >= 10 && (age - 10) % gap == 0 && (age - 10) / gap < rings) ring(kit, bell.at, radius, bell.heal, rate, slow, 1.0);
            return false;
        });
    }

    /** The re-activation of 大: the bell is called to the cleric and rings out at once (more heal, the enemies thrown back); false when none rings. */
    private static boolean callBell(MoveKit kit) {
        Bell bell = BELLS.remove(kit.player.getUniqueId());
        if (bell == null || bell.done) return false;
        bell.done = true;
        Location from = bell.at.clone().add(0.0, 2.2, 0.0);
        Location to = kit.ground(kit.player.getLocation());
        kit.fx(from).line(from, to.clone().add(0.0, 2.2, 0.0), HOLY, 0.3, 1.0, 1.5, 1.0, 0.3, null, 1);
        kit.later(3L, () -> {
            Location at = kit.ground(kit.player.getLocation());
            drawBell(kit, at.clone().add(0.0, 2.4, 0.0), 1.2, 0.3);
            double radius = range(kit, "large-radius", 5.0);
            ring(kit, at, radius, range(kit, "recast-heal", 8.0), range(kit, "recast-rate", 0.6), range(kit, "large-slow", 30.0), 1.6);
            for (LivingEntity victim : kit.around(at, radius)) kit.push(victim, at.toVector(), 0.6, 0.3);
        });
        return true;
    }

    /** One ring of the bell at {@code at}: the allies within {@code radius} healed {@code heal}, the enemies slowed and struck; {@code loud} its size. */
    private static void ring(MoveKit kit, Location at, double radius, double heal, double rate, double slow, double loud) {
        kit.sound(at, Sfx.HOLY_BELL, (float) loud, 1.0f / (float) Math.sqrt(loud));
        kit.shockwave(at, 0.6, radius, 6, 1.1 * loud);
        kit.later(2L, () -> kit.shockwave(at, 0.3, radius * 0.7, 5, 0.9 * loud));
        for (int y = 0; y < 3; y++) {
            int level = y;
            kit.later(level, () -> kit.fx(at).flatRing(at.clone().add(0.0, 2.0 - 0.6 * level, 0.0), 0.9 + 0.8 * level * loud, 20, HOLY, 0.9 - 0.2 * level, 0.1, 1.0));
        }
        healAround(kit, at, radius, heal);
        for (LivingEntity victim : kit.around(at, radius)) {
            if (kit.hit(victim, rate)) kit.slow(victim, slow, 1.5);
        }
    }

    /** A bell of light hanging at {@code top} ({@code size} its lip's radius), swung by {@code swing} (its lip shifted aside). */
    static void drawBell(MoveKit kit, Location top, double size, double swing) {
        Fx fx = kit.fx(top);
        if (!fx.active()) return;
        Vector side = kit.facing().rotateAroundY(Math.PI / 2.0).multiply(swing);
        // the crown, the shoulder, the waist and the lip, from the top down; the lip swings the most
        double[][] rings = {{0.0, 0.25}, {-0.25, 0.5}, {-0.7, 0.6}, {-1.1, 1.0}};
        Location previous = null;
        for (double[] ring : rings) {
            Location at = top.clone().add(0.0, ring[0] * size, 0.0).add(side.clone().multiply(-ring[0]));
            fx.flatRing(at, ring[1] * size, 10 + (int) (ring[1] * 14), ring == rings[3] ? HOLY : kit.palette(), 0.6 + 0.3 * -ring[0], 0.0, 1.0);
            if (previous != null) {
                for (int edge = 0; edge < 4; edge++) {
                    double angle = edge * Math.PI / 2.0 + Math.PI / 4.0;
                    Vector out = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
                    fx.line(previous.clone().add(out.clone().multiply(size * 0.5)), at.clone().add(out.clone().multiply(ring[1] * size)), kit.palette(), 0.5, 0.8, 0.0, 0.7, 0.3, null, 1);
                }
            }
            previous = at;
        }
        fx.dust(top.getX(), top.getY() + 0.2, top.getZ(), HOLY, 1.0, 1.2);
    }

    // ---- the staff: util ------------------------------------------------------------------------------------------------------------------

    /** Light step: a step of light ahead (back with shift), nothing hurts the cleric on it; where it began, a glyph heals the allies on it. */
    private static void lightStep(MoveKit kit) {
        Player player = kit.player;
        Vector heading = kit.facing().multiply(player.isSneaking() ? -1.0 : 1.0);
        double power = range(kit, "util-power", 1.2);
        int ticks = Math.max(1, (int) Math.round(range(kit, "util-ticks", 5.0)));
        Location start = kit.ground(player.getLocation());
        double heal = range(kit, "util-heal", 1.0);
        double radius = range(kit, "util-glyph-radius", 2.0);
        kit.invulnerable(ticks + 3);
        kit.sound(start, Sfx.HOLY_STEP, 1.0f, 1.3f);
        kit.every(ticks + 1, age -> {
            Location at = player.getLocation();
            kit.fx(at).line(at.clone().add(0.0, 0.1, 0.0), at.clone().add(0.0, 1.9, 0.0), kit.palette(), 0.2, 0.9, 0.0, 0.9, 0.3, null, 1);
            return false;
        });
        kit.dash(heading, power, ticks, null);
        int life = (int) Math.round(range(kit, "util-glyph-seconds", 3.0) * 20.0);
        kit.zone("cleric_glyph", start, life, 10, zone -> {
            Fx fx = kit.fx(start);
            fx.flatRing(start.clone().add(0.0, 0.1, 0.0), radius, 30, kit.palette(), 0.7, 0.3, 1.0);
            fx.flatRing(start.clone().add(0.0, 0.1, 0.0), radius * 0.5, 16, HOLY, 0.9, 0.2, 0.9);
            kit.healAllies(start, radius, heal);
        }, null);
    }

    /** The cleric's ULT, the sanctuary, was cast (t168): the radiance and the verses are full at once, and so is the sanctum's serenity. */
    static void ultimate(Player player) {
        if (attack == null) return;
        long now = System.currentTimeMillis();
        int radiance = (int) Math.round(number(MELEE, "radiance-max", 5.0));
        RADIANCE.add(player.getUniqueId(), radiance, radiance, now + Math.round(number(MELEE, "radiance-seconds", 6.0) * 1000.0), now);
        int verses = (int) Math.round(number(RANGE, "verse-max", 5.0));
        VERSES.add(player.getUniqueId(), verses, verses, now + Math.round(number(RANGE, "verse-seconds", 10.0) * 1000.0), now);
        ClericDeployCombo.fillSerenity(player.getUniqueId(), (int) Math.round(attack.plugin().getConfig().getDouble("combat.combo." + ClericDeployCombo.PLAN + ".serenity-max", 5.0)));
        drawRadiance(player);
        drawVerses(player);
        Sfx.HOLY_FULL.play(player.getLocation(), 0.8f, 1.2f);
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        RADIANCE.forget(player);
        VERSES.forget(player);
        AEGIS.remove(player);
        LAST_BLOW.remove(player);
        BELLS.remove(player);
        ClericDeployCombo.forget(player);
    }
}
