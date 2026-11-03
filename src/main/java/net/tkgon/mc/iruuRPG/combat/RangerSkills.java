package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * The ranger's own skills (t168: nekorozv left them to us, keeping the class's concepts: the ranger changes weapons even in a fight, and the three
 * work together). Every number is under {@code combat.combo.ranger_melee} / {@code ranger_range} (all provisional), the cooldowns of the melee ones
 * too. Drawn in the weapon's colors (t198); the wire stays silver.
 *
 * <p><b>The knife and the wire</b> (a melee weapon of the class's combo): the ranger drags the enemies into the reach of their turret (the spirit
 * bow of the lantern, {@link RangerDeployCombo}) or of their bow. A wire flung at an enemy <b>ties</b> it ({@code tie-seconds} 3): it is pulled to
 * the ranger's turret when one stands within {@code turret-reach} (20) blocks, else to the ranger; the turret shoots a tied enemy first and
 * {@code tied-bonus} (30%) harder.
 * <ul>
 *   <li>小 wire hook: a wire to the enemy aimed at, and a yank to the turret (or to the ranger).</li>
 *   <li>中 wire net: a net flung at the aim ties the enemies under it together, slowed; its re-activation reels them all in (to the turret, or to
 *       the ranger).</li>
 *   <li>大 grapple assault: wires to up to four enemies near, and the ranger zips from one to the next cutting each (nothing hurts them on the
 *       way); the last is kicked to the turret.</li>
 *   <li>util grapple (2 charges, t198): a wire to the block aimed at, and the ranger zips there; with shift a flip back.</li>
 * </ul>
 *
 * <p><b>The bow</b> (a ranged weapon of the class's combo): 小 is new, the power shot (2 charges: a short draw, then an arrow that flies straight
 * through every enemy in the way; more on a marked enemy, more again through the turret's reach); 中・大・util are what they were (the rapid shots,
 * the rain of arrows (its steps closer together since t198), the backstep shot), with the class's cooldowns. With a deployed weapon there are no
 * skills. The ULT is the class's.
 */
public final class RangerSkills {

    static final String MELEE = RangerMeleeCombo.PLAN;
    static final String RANGE = RangerRangeCombo.PLAN;
    /** The wire: a dark line, silver, a bright glint. */
    static final Gradient WIRE = Gradient.of(Color.fromRGB(40, 58, 52), Color.fromRGB(165, 205, 180), Color.fromRGB(245, 255, 248));

    private static AttackService attack;
    /** The enemies tied by a wire: until when (ms). */
    private static final Map<UUID, Long> TIED = new ConcurrentHashMap<>();
    /** The enemies each ranger's net holds (for its re-activation). */
    private static final Map<UUID, List<UUID>> NETTED = new HashMap<>();

    private RangerSkills() {
    }

    // ---- registering --------------------------------------------------------------------------------------------------------

    /**
     * The ranger's skill {@code slot}: the knife's own with a melee weapon of the class's combo; with the bow 小 is the power shot and the others the
     * heavy attacks they threw; with another weapon the heavy attack it threw.
     */
    static SkillSlots.Move move(AttackService attackService, SkillSlots.Slot slot, ToIntFunction<AttackType> heavyOf) {
        attack = attackService;
        return new SkillSlots.Move() {
            @Override
            public boolean use(Player player, RpgItemDefinition weapon) {
                if (weapon == null) return false;
                boolean own = attackService.hasClassCombo(player, weapon);
                if (own && weapon.attackType() == AttackType.MELEE) {
                    MoveKit kit = attackService.skillKit(player, weapon);
                    if (kit == null) return false;
                    lookWire(kit);
                    switch (slot) {
                        case SMALL -> hook(kit);
                        case MEDIUM -> net(kit);
                        case LARGE -> assault(kit);
                        case UTIL -> grapple(kit);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                }
                if (own && weapon.attackType() == AttackType.RANGE && slot == SkillSlots.Slot.SMALL) {
                    MoveKit kit = attackService.skillKit(player, weapon);
                    if (kit == null) return false;
                    powerShot(kit);
                    return true;
                }
                return attackService.slotHeavy(player, weapon, heavyOf.applyAsInt(weapon.attackType()));
            }

            @Override
            public boolean recast(Player player, RpgItemDefinition weapon) {
                if (slot != SkillSlots.Slot.MEDIUM || weapon == null || weapon.attackType() != AttackType.MELEE || !attackService.hasClassCombo(player, weapon)) {
                    return false;
                }
                MoveKit kit = attackService.skillKit(player, weapon);
                if (kit == null) return false;
                lookWire(kit);
                return reel(kit);
            }
        };
    }

    /**
     * The charges, re-activations and cooldowns (t198: spread by what each skill is): the knife's are its own (小 a cooldown, 中 a re-activation,
     * util 2 charges: the grapple moves); the bow's 小 has 2 charges (the power shot), its other skills the class's cooldowns; a deployed weapon none.
     */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot, AttackType kind) {
        if (kind == AttackType.MELEE) {
            String base = "combat.combo." + MELEE + ".";
            return switch (slot) {
                case SMALL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "small-seconds", 6.0));
                case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 3.0), 0.0, config.getDouble(base + "medium-seconds", 10.0));
                case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 22.0));
                case UTIL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "util-charges", 2.0)), 0.0,
                        config.getDouble(base + "util-charge-seconds", 5.0), config.getDouble(base + "util-empty-seconds", 10.0));
                default -> SkillSlots.Mechanics.NONE;
            };
        }
        if (kind == AttackType.RANGE && slot == SkillSlots.Slot.SMALL) {
            String base = "combat.combo." + RANGE + ".";
            return new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 2.0)), 0.0,
                    config.getDouble(base + "small-charge-seconds", 3.0), config.getDouble(base + "small-empty-seconds", 7.0));
        }
        return SkillSlots.Mechanics.NONE;
    }

    /** The knife's look (t198): the weapon's colors with a touch of steel, as its combo; the wire stays silver. */
    static void lookWire(MoveKit kit) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
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

    // ---- the wire ------------------------------------------------------------------------------------------------------------

    /** A wire from {@code from} to {@code to}: a thin taut line, glinting. */
    static void wire(MoveKit kit, Location from, Location to) {
        Fx fx = kit.fx(from);
        if (!fx.active()) return;
        fx.line(from, to, WIRE, 0.35, 0.95, 0.0, 0.55, 0.18, null, 1);
    }

    /** Whether {@code victim} is tied by a wire now (the turret shoots it first and harder). */
    static boolean tied(LivingEntity victim) {
        Long until = TIED.get(victim.getUniqueId());
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        TIED.remove(victim.getUniqueId());
        return false;
    }

    /** Ties {@code victim} for {@code seconds}. */
    static void tie(LivingEntity victim, double seconds) {
        TIED.put(victim.getUniqueId(), System.currentTimeMillis() + Math.round(seconds * 1000.0));
    }

    /** Where the ranger drags the enemies to: their turret when it stands within {@code turret-reach}, else a step in front of the ranger. */
    static Location dragTarget(MoveKit kit) {
        Location turret = RangerDeployCombo.turretNear(kit.player, melee(kit, "turret-reach", 20.0));
        if (turret != null) return turret.clone().add(0.0, 0.5, 0.0);
        return kit.player.getLocation().add(kit.facing().multiply(2.0));
    }

    /** Drags {@code victim} to {@code to}: a pull as strong as the way is long (at most {@code pull-max}). */
    static void drag(MoveKit kit, LivingEntity victim, Location to) {
        double distance = victim.getLocation().distance(to);
        if (distance < 1.2) return;
        double power = Math.min(melee(kit, "pull-max", 1.8), 0.3 + distance * 0.09);
        kit.pull(victim, to, power);
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        kit.every(6, age -> {
            if (!victim.isValid()) return true;
            wire(kit, to.clone().add(0.0, 1.0, 0.0), victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0));
            return false;
        });
        kit.particle(Particle.CRIT, chest, 4, 0.2, 0.1);
    }

    /** A wire flung from the ranger's hand to where {@code end} says (it may move), 2.2 blocks a tick; {@code onReach} when it gets there. */
    private static void fling(MoveKit kit, java.util.function.Supplier<Location> end, Runnable onReach) {
        double[] out = {0.0};
        kit.every(12, age -> {
            Location to = end.get();
            Location from = ComboParts.hand(kit);
            if (to.getWorld() != from.getWorld()) return true;
            Vector way = to.toVector().subtract(from.toVector());
            double length = way.length();
            out[0] += 2.2;
            if (out[0] >= length) {
                wire(kit, from, to);
                if (onReach != null) onReach.run();
                return true;
            }
            wire(kit, from, from.clone().add(way.normalize().multiply(out[0])));
            return false;
        });
    }

    // ---- the knife: 小 -----------------------------------------------------------------------------------------------------------

    /** Wire hook: a wire to the enemy aimed at (or the nearest ahead), tied, and yanked to the turret (or to the ranger). */
    private static void hook(MoveKit kit) {
        double reach = melee(kit, "small-range", 14.0);
        LivingEntity target = kit.nearest(reach, 25.0);
        kit.sound(kit.player.getLocation(), Sfx.WIRE_THROW, 1.0f, 1.0f);
        if (target == null) {
            Location aim = kit.aim();
            fling(kit, () -> aim, () -> kit.sound(aim, Sfx.WIRE_SNAP, 0.5f, 1.4f));
            return;
        }
        fling(kit, () -> target.getLocation().add(0.0, target.getHeight() * 0.55, 0.0), () -> {
            if (!target.isValid() || target.isDead()) return;
            kit.hit(target, melee(kit, "small-rate", 0.5));
            tie(target, melee(kit, "tie-seconds", 3.0));
            kit.sound(target.getLocation(), Sfx.WIRE_PULL, 1.0f, 1.0f);
            drag(kit, target, dragTarget(kit));
        });
    }

    // ---- the knife: 中 -----------------------------------------------------------------------------------------------------------

    /** Wire net: a net flung at the aim; the enemies under it are tied together there, slowed, for {@code medium-seconds-held}. */
    private static void net(MoveKit kit) {
        Player player = kit.player;
        double reach = melee(kit, "medium-range", 12.0);
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        if (way.length() > reach) aim = eye.clone().add(way.normalize().multiply(reach));
        Location spot = kit.ground(aim);
        double radius = melee(kit, "medium-radius", 3.5);
        int ticks = (int) Math.round(melee(kit, "medium-hold-seconds", 3.0) * 20.0);
        double rate = melee(kit, "medium-rate", 0.4);
        kit.sound(player.getLocation(), Sfx.WIRE_THROW, 1.0f, 0.8f);
        kit.lob(ComboParts.hand(kit), spot.clone().add(0.0, 0.5, 0.0), 8, 1.5, land -> {
            kit.sound(land, Sfx.WIRE_PULL, 1.0f, 0.8f);
            drawNet(kit, spot, radius);
            List<UUID> held = new ArrayList<>();
            for (LivingEntity victim : kit.around(spot, radius)) {
                kit.hit(victim, rate);
                kit.slow(victim, melee(kit, "medium-slow", 30.0), ticks / 20.0);
                tie(victim, ticks / 20.0);
                held.add(victim.getUniqueId());
                hold(kit, victim, spot, melee(kit, "medium-hold-radius", 1.0), ticks);
            }
            NETTED.put(player.getUniqueId(), held);
            kit.every(ticks, age -> {
                if (age % 4 == 0) drawNet(kit, spot, radius * 0.6);
                return NETTED.get(player.getUniqueId()) != held;
            });
        });
    }

    /** A net of wires on the ground at {@code at}: spokes and two rings. */
    private static void drawNet(MoveKit kit, Location at, double radius) {
        Fx fx = kit.fx(at);
        if (!fx.active()) return;
        Location base = at.clone().add(0.0, 0.15, 0.0);
        for (int index = 0; index < 6; index++) {
            double angle = index * Math.PI / 3.0;
            fx.line(base, base.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius), WIRE, 0.4, 0.9, 0.0, 0.6, 0.25, null, 1);
        }
        fx.flatRing(base, radius, 30, WIRE, 0.7, 0.2, 0.6);
        fx.flatRing(base, radius * 0.5, 18, WIRE, 0.8, 0.2, 0.6);
    }

    /** Holds {@code victim} near {@code anchor} ({@code radius}) for {@code ticks}: pulled back when it goes farther; a wire drawn to it. */
    private static void hold(MoveKit kit, LivingEntity victim, Location anchor, double radius, int ticks) {
        kit.every(ticks, age -> {
            if (!victim.isValid() || victim.isDead() || !tied(victim)) return true;
            Vector off = victim.getLocation().toVector().subtract(anchor.toVector()).setY(0.0);
            double distance = off.length();
            if (distance > radius && distance > 1.0E-3) {
                Vector back = off.multiply(-1.0 / distance).multiply(Math.min(0.5, 0.1 + (distance - radius) * 0.35));
                victim.setVelocity(new Vector(back.getX(), Math.min(victim.getVelocity().getY(), 0.1), back.getZ()));
            }
            if (age % 3 == 0) wire(kit, anchor.clone().add(0.0, 0.2, 0.0), victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0));
            return false;
        });
    }

    /** The re-activation of 中: everyone in the net is reeled in (to the turret, or to the ranger); false when the net holds nobody. */
    private static boolean reel(MoveKit kit) {
        List<UUID> held = NETTED.remove(kit.player.getUniqueId());
        if (held == null || held.isEmpty()) return false;
        Location to = dragTarget(kit);
        double rate = melee(kit, "recast-rate", 0.4);
        kit.sound(kit.player.getLocation(), Sfx.WIRE_PULL, 1.0f, 1.2f);
        boolean any = false;
        for (UUID id : held) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity victim) || !victim.isValid() || victim.isDead() || victim.getWorld() != to.getWorld()) continue;
            any = true;
            // (the hold of the net lets go: they are tied anew for the turret)
            TIED.remove(id);
            kit.later(1L, () -> {
                tie(victim, melee(kit, "tie-seconds", 3.0));
                kit.hit(victim, rate);
                drag(kit, victim, to);
            });
        }
        return any;
    }

    // ---- the knife: 大 -----------------------------------------------------------------------------------------------------------

    /**
     * Grapple assault: wires to up to {@code large-targets} enemies within {@code large-range} (the nearest first), and the ranger zips from one to
     * the next, cutting each ({@code large-rate}; nothing hurts them on the way); the last one is kicked to the turret (or away).
     */
    private static void assault(MoveKit kit) {
        Player player = kit.player;
        double reach = melee(kit, "large-range", 12.0);
        int count = (int) Math.round(melee(kit, "large-targets", 4.0));
        List<LivingEntity> targets = kit.around(player.getLocation(), reach);
        Location feet = player.getLocation();
        targets.sort(java.util.Comparator.comparingDouble(victim -> victim.getLocation().distanceSquared(feet)));
        if (targets.size() > count) targets = new ArrayList<>(targets.subList(0, count));
        kit.sound(feet, Sfx.WIRE_THROW, 1.0f, 1.2f);
        if (targets.isEmpty()) {
            kit.invulnerable(8);
            kit.dash(kit.facing(), 1.3, 5, null);
            return;
        }
        Location hand = ComboParts.hand(kit);
        for (LivingEntity victim : targets) wire(kit, hand, victim.getLocation().add(0.0, victim.getHeight() * 0.55, 0.0));
        kit.invulnerable(targets.size() * 7 + 6);
        zip(kit, targets, 0);
    }

    /** One zip of the assault: to {@code targets.get(index)}, a cut, then on to the next. */
    private static void zip(MoveKit kit, List<LivingEntity> targets, int index) {
        if (index >= targets.size()) return;
        LivingEntity victim = targets.get(index);
        if (!victim.isValid() || victim.isDead()) {
            zip(kit, targets, index + 1);
            return;
        }
        Player player = kit.player;
        Vector to = victim.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
        boolean last = index == targets.size() - 1;
        double rate = melee(kit, "large-rate", 0.8) * (last ? melee(kit, "large-last-share", 1.5) : 1.0);
        kit.sound(player.getLocation(), Sfx.WIRE_ZIP, 0.9f, 1.0f + 0.1f * index);
        int ticks = Math.max(2, Math.min(7, (int) Math.ceil(to.length() / 1.6)));
        wire(kit, player.getLocation().add(0.0, 1.0, 0.0), victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0));
        kit.dashToContact(to, 1.6, ticks, 1.4, (end, touched) -> {
            kit.slash("line", 2.4, 110.0, index % 2 == 0 ? 40.0 : -40.0, index % 2 == 0 ? 0.0 : 180.0, 1.4, 0.3, 0.0, 1.15, 0.08, 0.0);
            kit.sound(end, Sfx.WIND, 0.8f, 1.5f);
            if (victim.isValid() && !victim.isDead() && kit.hit(victim, rate) && last) {
                Location turret = RangerDeployCombo.turretNear(player, melee(kit, "turret-reach", 20.0));
                tie(victim, melee(kit, "tie-seconds", 3.0));
                if (turret != null) drag(kit, victim, turret.clone().add(0.0, 0.5, 0.0));
                else kit.push(victim, end.toVector(), 0.8, 0.3);
                kit.shockwave(victim.getLocation(), 0.6, 2.0, 3, 1.0);
            }
            kit.later(2L, () -> zip(kit, targets, index + 1));
        });
    }

    // ---- the knife: util -----------------------------------------------------------------------------------------------------------

    /** Grapple: a wire to the block aimed at (within {@code util-range}) and the ranger zips there; with shift a flip back. */
    private static void grapple(MoveKit kit) {
        Player player = kit.player;
        if (player.isSneaking()) {
            kit.invulnerable(6);
            kit.hopBack(melee(kit, "util-back", 0.9), 0.45);
            kit.sound(player.getLocation(), Sfx.WIND, 0.8f, 1.4f);
            return;
        }
        Location eye = kit.eye();
        Vector look = eye.getDirection();
        double reach = melee(kit, "util-range", 16.0);
        RayTraceResult block = eye.getWorld().rayTraceBlocks(eye, look, reach, org.bukkit.FluidCollisionMode.NEVER, true);
        Location point = block != null && block.getHitPosition() != null ? block.getHitPosition().toLocation(eye.getWorld()) : eye.clone().add(look.clone().multiply(Math.min(reach, 7.0)));
        double power = melee(kit, "util-power", 1.4);
        kit.sound(player.getLocation(), Sfx.WIRE_THROW, 0.9f, 1.3f);
        kit.invulnerable(10);
        Location anchor = point.clone();
        kit.every(14, age -> {
            player.setFallDistance(0.0f);
            Location chest = player.getLocation().add(0.0, 1.0, 0.0);
            Vector way = anchor.toVector().subtract(chest.toVector());
            double length = way.length();
            if (age < 2) {
                wire(kit, ComboParts.hand(kit), anchor);
                return false;
            }
            if (length < 1.6) {
                player.setVelocity(player.getVelocity().multiply(0.3).setY(Math.max(0.2, player.getVelocity().getY() * 0.3)));
                return true;
            }
            wire(kit, ComboParts.hand(kit), anchor);
            if (age == 2) kit.sound(chest, Sfx.WIRE_ZIP, 0.9f, 1.2f);
            player.setVelocity(way.normalize().multiply(Math.min(power, length * 0.5)).add(new Vector(0.0, 0.08, 0.0)));
            return false;
        });
    }

    // ---- the bow: 小 -------------------------------------------------------------------------------------------------------------

    /**
     * Power shot: a short draw ({@code small-draw-ticks}), then an arrow that flies straight through every enemy in the way ({@code small-rate});
     * a marked enemy takes {@code small-mark-bonus} more, and an arrow that passed through the turret's reach {@code small-turret-bonus} more.
     */
    private static void powerShot(MoveKit kit) {
        Player player = kit.player;
        int draw = (int) Math.round(range(kit, "small-draw-ticks", 6.0));
        double rate = range(kit, "small-rate", 1.5);
        double markBonus = range(kit, "small-mark-bonus", 0.4);
        double turretBonus = range(kit, "small-turret-bonus", 0.25);
        kit.sound(player.getLocation(), Sfx.BOW_DRAW, 1.0f, 1.0f);
        kit.every(draw, age -> {
            Location hand = ComboParts.hand(kit);
            kit.fx(hand).sphereShell(hand, 0.25 + 0.05 * age, age * 0.6, 8, kit.palette(), 0.8);
            return false;
        });
        kit.later(draw, () -> {
            Location start = ComboParts.hand(kit);
            Vector direction = kit.aim().toVector().subtract(start.toVector());
            if (direction.lengthSquared() < 1.0E-6) direction = kit.axes()[0];
            kit.sound(start, Sfx.BOW_LOOSE, 1.0f, 1.0f);
            Location turret = RangerDeployCombo.turretNear(player, 40.0);
            boolean[] boosted = {false};
            Set<UUID> struck = new HashSet<>();
            kit.pierceArrow(start, direction, 2.4, range(kit, "small-range", 30.0), kit.palette(), victim -> {
                if (!struck.add(victim.getUniqueId())) return;
                double multiplier = 1.0;
                RangerMoves moves = attack.rangerMoves();
                if (moves != null && moves.markMultiplier(victim) > 1.0) multiplier += markBonus;
                if (boosted[0]) multiplier += turretBonus;
                if (kit.hit(victim, rate * multiplier)) {
                    Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                    kit.fx(chest).sphereShell(chest, 0.5, 0.0, 12, kit.palette(), 1.0);
                    kit.sound(chest, Sfx.HIT_PIERCE, 0.8f, 0.9f);
                    RangerDeployCombo.relay(kit, victim);
                }
            }, at -> {
                if (!boosted[0] && turret != null && turret.getWorld() == at.getWorld() && turret.clone().add(0.0, 1.2, 0.0).distanceSquared(at) <= 6.25) {
                    boosted[0] = true;
                    kit.burst(turret.clone().add(0.0, 1.4, 0.0), 0.9);
                    kit.sound(turret, Sfx.MAGIC, 0.6f, 1.7f);
                }
                return false;
            }, end -> kit.burst(end, 0.6));
        });
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        NETTED.remove(player);
        RangerDeployCombo.forget(player);
    }
}
