package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The assassin with a ranged weapon of its combo: the <b>shadow bow</b> (t168: nekorozv's arrows of shadow that go through walls; where the
 * ranger's bow keeps shooting, this one is for afflictions and assassination). Its arrows fly through blocks and seek the enemy nearest the line of
 * sight (behind a wall too); they leave afflictions (confusion, bleed, slow), and the shot of 大 is stronger for each kind of affliction on an enemy
 * and finishes a weakened one. Every number is under {@code combat.combo.assassin_range} (all provisional), the cooldowns too.
 * <ul>
 *   <li>小 venom arrow (3 charges): a quick shadow arrow that bleeds and confuses.</li>
 *   <li>中 shadow stitch: an arrow that pins the enemy's shadow (it cannot move for a moment, confused); its re-activation sends a clone behind it
 *       that stabs it twice.</li>
 *   <li>大 assassination: a long draw, then a great shadow arrow through walls and every enemy in the way: more for each kind of affliction on an
 *       enemy, double on one under {@code execute-percent} of its life.</li>
 *   <li>util shadow veil: a flip back into smoke; the mobs lose the assassin for a moment and go to the smoke.</li>
 * </ul>
 */
public final class AssassinShade {

    static final String PLAN = AssassinRangeCombo.PLAN;
    /** The shadow: black, violet, a pale lilac. */
    static final Gradient SHADE = Gradient.of(Color.fromRGB(14, 6, 26), Color.fromRGB(110, 45, 180), Color.fromRGB(225, 200, 255));

    private static AttackService attack;
    /** The enemy each assassin pinned with 中 (for its re-activation). */
    private static final Map<UUID, UUID> PINNED = new HashMap<>();

    private AssassinShade() {
    }

    /** Whether {@code weapon} is the assassin's ranged weapon of its combo. */
    static boolean own(AttackService attackService, Player player, RpgItemDefinition weapon) {
        return weapon != null && weapon.attackType() == AttackType.RANGE && attackService.hasClassCombo(player, weapon);
    }

    /** The assassin's skill {@code slot} with the shadow bow. */
    static boolean use(AttackService attackService, Player player, RpgItemDefinition weapon, SkillSlots.Slot slot) {
        attack = attackService;
        MoveKit kit = attackService.skillKit(player, weapon);
        if (kit == null) return false;
        kit.palette(SHADE);
        switch (slot) {
            case SMALL -> venom(kit);
            case MEDIUM -> stitch(kit);
            case LARGE -> assassinate(kit);
            case UTIL -> veil(kit);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The re-activation of 中 with the shadow bow: a clone behind the pinned enemy; false when there is none. */
    static boolean recast(AttackService attackService, Player player, RpgItemDefinition weapon) {
        attack = attackService;
        UUID pinned = PINNED.remove(player.getUniqueId());
        org.bukkit.entity.Entity entity = pinned == null ? null : org.bukkit.Bukkit.getEntity(pinned);
        if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) return false;
        MoveKit kit = attackService.skillKit(player, weapon);
        if (kit == null) return false;
        kit.palette(SHADE);
        double rate = kit.number(PLAN, "recast-rate", 0.6);
        kit.sound(target.getLocation(), Sfx.SHADOW_CLONE, 1.0f, 1.0f);
        kit.clone(target, kit.behindAngle(target), 2, 4, true, hit -> {
            if (!target.isValid() || target.isDead()) return;
            if (kit.hit(target, rate)) {
                Location chest = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
                kit.fx(chest).sphereShell(chest, 0.5, hit * 0.8, 12, SHADE, 1.0);
                kit.sound(chest, Sfx.SHADOW_CROSS, 0.7f, 1.1f + 0.1f * hit);
            }
        });
        return true;
    }

    /** The charges and cooldowns of the shadow bow's skills (小 3 charges, 中 a re-activation); with another kind of weapon none. */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot) {
        String base = "combat.combo." + PLAN + ".";
        return switch (slot) {
            case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 3.0)), 0.0,
                    config.getDouble(base + "small-charge-seconds", 2.5), config.getDouble(base + "small-empty-seconds", 7.0));
            case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 3.0), 0.0, config.getDouble(base + "medium-seconds", 11.0));
            case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 22.0));
            case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 6.0));
            default -> SkillSlots.Mechanics.NONE;
        };
    }

    // ---- the shadow arrow -----------------------------------------------------------------------------------------------------------

    /**
     * Where a shadow arrow goes from {@code start}: at the enemy nearest the line of sight within {@code range} (within 2 blocks of it, behind a wall
     * too), else straight along the look.
     */
    static Vector seek(MoveKit kit, Location start, double range) {
        Vector look = kit.eye().getDirection().normalize();
        LivingEntity best = null;
        double bestOff = Double.MAX_VALUE;
        for (LivingEntity victim : kit.corridor(kit.eye(), look, range, 2.0)) {
            Vector center = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0).toVector();
            Vector to = center.clone().subtract(kit.eye().toVector());
            double along = to.dot(look);
            double off = to.clone().subtract(look.clone().multiply(along)).length() + along * 0.02;
            if (off < bestOff) {
                bestOff = off;
                best = victim;
            }
        }
        if (best == null) return look;
        Vector way = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0).toVector().subtract(start.toVector());
        return way.lengthSquared() < 1.0E-6 ? look : way.normalize();
    }

    /**
     * A shadow arrow from {@code start} along {@code direction}: {@code speed} blocks a tick for {@code range}, through blocks; the first enemy it
     * meets is given to {@code onHit} (it ends there), or every one it passes when it {@code pierces}. Drawn as a streak of shadow.
     */
    static void arrow(MoveKit kit, Location start, Vector direction, double speed, double range, double size, boolean pierces, Consumer<LivingEntity> onHit) {
        Vector heading = direction.clone().normalize();
        Location[] position = {start.clone()};
        double[] travelled = {0.0};
        Set<UUID> passed = new HashSet<>();
        kit.every((int) Math.ceil(range / speed) + 2, age -> {
            double step = Math.min(speed, range - travelled[0]);
            if (step <= 1.0E-6) return true;
            Location from = position[0];
            Location next = from.clone().add(heading.clone().multiply(step));
            Fx fx = kit.fx(from);
            fx.line(from.clone().subtract(heading.clone().multiply(size * 1.2)), next, SHADE, 0.05, 0.85, 2.0, 0.9 * size, 0.25, null, 1);
            if (age % 2 == 0) kit.particle(Particle.SQUID_INK, next, 1, 0.05, 0.0);
            // (through a wall: the arrow shows as a dark ripple where it passes)
            if (!next.getBlock().isPassable() && age % 2 == 0) kit.particle(Particle.SMOKE_LARGE, next, 1, 0.1, 0.0);
            LivingEntity first = null;
            double firstAlong = Double.MAX_VALUE;
            for (LivingEntity victim : kit.corridor(from, heading, step, 0.8 * Math.max(1.0, size))) {
                if (passed.contains(victim.getUniqueId())) continue;
                if (pierces) {
                    passed.add(victim.getUniqueId());
                    onHit.accept(victim);
                    continue;
                }
                double along = victim.getLocation().toVector().subtract(from.toVector()).dot(heading);
                if (along < firstAlong) {
                    firstAlong = along;
                    first = victim;
                }
            }
            if (first != null) {
                onHit.accept(first);
                return true;
            }
            position[0] = next;
            travelled[0] += step;
            return false;
        });
    }

    /** A shadow arrow struck {@code victim}: its look. */
    static void struck(MoveKit kit, LivingEntity victim) {
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        kit.fx(chest).sphereShell(chest, 0.35, 0.0, 10, SHADE, 0.9);
        kit.particle(Particle.SQUID_INK, chest, 2, 0.15, 0.02);
    }

    // ---- 小 ----------------------------------------------------------------------------------------------------------------------

    /** Venom arrow: a quick shadow arrow that bleeds ({@code small-bleed} of the hit) and confuses. */
    private static void venom(MoveKit kit) {
        double rate = kit.number(PLAN, "small-rate", 0.4);
        double bleed = kit.number(PLAN, "small-bleed", 0.6);
        double confusion = kit.number(PLAN, "small-confusion", 15.0);
        Location start = ComboParts.hand(kit);
        kit.sound(start, Sfx.SHADE_LOOSE, 0.9f, 1.3f);
        arrow(kit, start, seek(kit, start, 30.0), 2.2, 30.0, 0.9, false, victim -> {
            double before = victim.getHealth();
            if (!kit.hit(victim, rate)) return;
            struck(kit, victim);
            double dealt = Math.max(0.0, before - victim.getHealth());
            if (dealt > 0.0 && victim.isValid() && !victim.isDead()) attack.statusEffects().addBleed(victim, dealt * bleed, kit.player.getUniqueId());
            kit.confuse(victim, confusion);
            kit.sound(victim.getLocation(), Sfx.SHADE_HIT, 0.7f, 1.3f);
        });
    }

    // ---- 中 ----------------------------------------------------------------------------------------------------------------------

    /** Shadow stitch: an arrow that pins the enemy's shadow: it cannot move for {@code medium-pin-seconds}, and is confused. */
    private static void stitch(MoveKit kit) {
        double rate = kit.number(PLAN, "medium-rate", 0.6);
        double seconds = kit.number(PLAN, "medium-pin-seconds", 1.5);
        double confusion = kit.number(PLAN, "medium-confusion", 20.0);
        Location start = ComboParts.hand(kit);
        UUID id = kit.player.getUniqueId();
        kit.sound(start, Sfx.SHADE_LOOSE, 1.0f, 0.9f);
        arrow(kit, start, seek(kit, start, 30.0), 2.0, 30.0, 1.1, false, victim -> {
            if (!kit.hit(victim, rate)) return;
            struck(kit, victim);
            kit.slow(victim, 100.0, seconds);
            kit.confuse(victim, confusion);
            PINNED.put(id, victim.getUniqueId());
            Location feet = kit.ground(victim.getLocation());
            kit.sound(feet, Sfx.SHADE_HIT, 1.0f, 0.7f);
            int ticks = (int) Math.round(seconds * 20.0);
            kit.every(ticks, age -> {
                if (!victim.isValid() || victim.isDead()) return true;
                if (age % 3 == 0) {
                    Fx fx = kit.fx(feet);
                    fx.flatRing(feet.clone().add(0.0, 0.08, 0.0), 0.9, 16, SHADE, 0.2, 0.2, 1.0);
                    fx.line(feet.clone().add(0.0, 1.6, 0.0), feet.clone().add(0.0, 0.05, 0.0), SHADE, 0.6, 0.1, 0.0, 0.9, 0.3, null, 1);
                }
                return false;
            });
        });
    }

    // ---- 大 ----------------------------------------------------------------------------------------------------------------------

    /**
     * Assassination: a draw of {@code large-draw-ticks}, then a great shadow arrow through walls and every enemy in the way: {@code large-rate} and
     * {@code large-affliction-rate} more for each kind of affliction on an enemy; {@code execute-multiplier} on one under {@code execute-percent} of
     * its life.
     */
    private static void assassinate(MoveKit kit) {
        Player player = kit.player;
        int draw = (int) Math.round(kit.number(PLAN, "large-draw-ticks", 16.0));
        double rate = kit.number(PLAN, "large-rate", 1.5);
        double perAffliction = kit.number(PLAN, "large-affliction-rate", 0.5);
        double executeAt = kit.number(PLAN, "execute-percent", 25.0);
        double execute = kit.number(PLAN, "execute-multiplier", 2.0);
        kit.sound(player.getLocation(), Sfx.SHADE_DRAW, 1.0f, 1.0f);
        kit.every(draw, age -> {
            Location hand = ComboParts.hand(kit);
            Fx fx = kit.fx(hand);
            double r = 1.4 * (1.0 - age / (double) draw) + 0.2;
            Vector[] axes = kit.axes();
            fx.ring(hand.clone().add(axes[0].clone().multiply(0.6)), axes[1], axes[2], r, 20, SHADE, 0.3 + 0.6 * age / draw, 0.3, 1.0);
            if (age % 4 == 0) kit.particle(Particle.SQUID_INK, hand, 2, 0.3, 0.02);
            return false;
        });
        kit.later(draw, () -> {
            Location start = ComboParts.hand(kit);
            kit.sound(start, Sfx.SHADE_EXECUTE, 1.0f, 1.0f);
            kit.slash("line", 3.0, 40.0, 0.0, 0.0, 1.0, 0.2, 0.0, 1.3, 0.1, 0.0);
            arrow(kit, start, seek(kit, start, 40.0), 3.0, 40.0, 1.8, true, victim -> {
                int afflictions = attack.statusEffects().afflictions(victim);
                double multiplier = 1.0 + perAffliction * afflictions / Math.max(0.1, rate);
                double life = maxHealth(victim);
                boolean weak = life > 0.0 && victim.getHealth() / life * 100.0 <= executeAt;
                if (weak) multiplier *= execute;
                if (!kit.hit(victim, rate * multiplier)) return;
                Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                kit.fx(chest).sphereShell(chest, 0.6 + 0.15 * afflictions, 0.0, 18 + 4 * afflictions, SHADE, 1.2);
                if (weak) {
                    kit.sound(chest, Sfx.SHADE_EXECUTE, 0.8f, 1.4f);
                    kit.particle(Particle.SOUL, chest, 6, 0.3, 0.05);
                }
            });
        });
    }

    private static double maxHealth(LivingEntity victim) {
        AttributeInstance attribute = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? victim.getHealth() : attribute.getValue();
    }

    // ---- util --------------------------------------------------------------------------------------------------------------------

    /** Shadow veil: a flip back into smoke; the mobs lose the assassin for {@code util-hide-seconds} and go to the smoke. */
    private static void veil(MoveKit kit) {
        Player player = kit.player;
        Location spot = player.getLocation();
        kit.invulnerable(6);
        kit.hopBack(kit.number(PLAN, "util-back", 0.9), 0.45);
        kit.sound(spot, Sfx.SHADOW_VANISH, 0.9f, 1.2f);
        long millis = Math.round(kit.number(PLAN, "util-hide-seconds", 1.5) * 1000.0);
        AssassinSkills.hide(player, spot, millis);
        kit.every((int) (millis / 50L), age -> {
            if (age % 3 == 0) {
                kit.particle(Particle.SMOKE_LARGE, spot.clone().add(0.0, 0.8, 0.0), 4, 0.6, 0.01);
                kit.particle(Particle.SQUID_INK, spot.clone().add(0.0, 1.0, 0.0), 2, 0.5, 0.01);
            }
            return false;
        });
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    static void forget(UUID player) {
        PINNED.remove(player);
    }
}
