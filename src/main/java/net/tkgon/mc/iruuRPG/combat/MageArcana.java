package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * The mage's skills with a ranged weapon (t168: nekorozv wanted the mage to be able to cast its skills again and again once a condition is met;
 * the echo that threw a heavy attack again by itself did not go with the re-activations, so it is replaced). The class is about the order of its
 * skills: casting 小, 中 and 大 each within {@code rotation-seconds} (10) opens the <b>torrent</b> for {@code torrent-seconds} (4): 小 and 中 cost
 * nothing then (no charge used, no cooldown started; {@link SkillSlots}) and hit {@code torrent-bonus} (20%) harder. Every number is under
 * {@code combat.combo.mage_range} (all provisional), the cooldowns too.
 * <ul>
 *   <li>小 arcane missiles (2 charges): three missiles that seek the enemies near the aim.</li>
 *   <li>中 crystal lance: a lance through everything in the line, shattering at its end.</li>
 *   <li>大 astral vortex: a vortex at the aim draws the enemies in and hurts them, then collapses.</li>
 *   <li>util blink: a flash ahead (back with shift), nothing hurts the mage on it; a rune left where it began bursts a second later.</li>
 * </ul>
 * The normal combo is the mage's as before ({@link MageMoves}); its echo and re-activations of the heavy attacks are no longer thrown by the
 * skills. The mage's lantern is {@link MageDeployCombo} (its criticality is counted here: {@link #vulnerability}).
 */
public final class MageArcana {

    static final String PLAN = "mage_range";
    /** The arcane light: deep violet, violet, a pale lilac. */
    static final Gradient ARCANE = Gradient.of(Color.fromRGB(40, 20, 95), Color.fromRGB(150, 95, 255), Color.fromRGB(235, 222, 255));
    /** The torrent: violet to gold. */
    static final Gradient TORRENT = Gradient.of(Color.fromRGB(90, 40, 170), Color.fromRGB(230, 150, 255), Color.fromRGB(255, 236, 170));

    private static AttackService attack;
    /** When each of 小・中・大 was last cast, per mage (ms). */
    private static final Map<UUID, Map<SkillSlots.Slot, Long>> CAST = new ConcurrentHashMap<>();
    /** The mages in a torrent: until when (ms). */
    private static final Map<UUID, Long> TORRENT_UNTIL = new ConcurrentHashMap<>();

    private MageArcana() {
    }

    // ---- registering --------------------------------------------------------------------------------------------------------

    /** Whether {@code weapon} is the mage's ranged weapon of the class's combo (its own skills, not the template's). */
    static boolean own(AttackService attackService, Player player, RpgItemDefinition weapon) {
        return weapon != null && weapon.attackType() == AttackType.RANGE && attackService.mageRangedWeapon(player, weapon);
    }

    /** The mage's skill {@code slot} with its ranged weapon. */
    static boolean use(AttackService attackService, Player player, RpgItemDefinition weapon, SkillSlots.Slot slot) {
        attack = attackService;
        MoveKit kit = attackService.skillKit(player, weapon);
        if (kit == null) return false;
        kit.palette(ARCANE);
        boolean torrent = torrent(player.getUniqueId());
        double boost = torrent ? 1.0 + kit.number(PLAN, "torrent-bonus", 0.2) : 1.0;
        if (torrent) kit.palette(TORRENT);
        switch (slot) {
            case SMALL -> missiles(kit, boost);
            case MEDIUM -> lance(kit, boost);
            case LARGE -> vortex(kit, boost);
            case UTIL -> blink(kit);
            default -> {
                return false;
            }
        }
        if (slot != SkillSlots.Slot.UTIL) rotate(kit, slot);
        return true;
    }

    /** The cooldowns of the mage's ranged skills (their own seconds; 小 2 charges); with another kind of weapon none. */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot) {
        String base = "combat.combo." + PLAN + ".";
        return switch (slot) {
            case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 2.0)), 0.0,
                    config.getDouble(base + "small-charge-seconds", 3.0), config.getDouble(base + "small-empty-seconds", 7.0));
            case MEDIUM -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "medium-seconds", 8.0));
            case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 20.0));
            case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 5.0));
            default -> SkillSlots.Mechanics.NONE;
        };
    }

    private static double number(String key, double fallback) {
        return attack == null ? fallback : attack.plugin().getConfig().getDouble("combat.combo." + PLAN + "." + key, fallback);
    }

    // ---- the torrent ----------------------------------------------------------------------------------------------------------

    /** Whether {@code player} is in a torrent now. */
    static boolean torrent(UUID player) {
        Long until = TORRENT_UNTIL.get(player);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        TORRENT_UNTIL.remove(player);
        return false;
    }

    /** Whether the skill {@code slot} with a kind of weapon costs nothing for {@code player} now (小 and 中 of a ranged weapon in a torrent). */
    static boolean free(UUID player, SkillSlots.Slot slot, AttackType kind) {
        return kind == AttackType.RANGE && (slot == SkillSlots.Slot.SMALL || slot == SkillSlots.Slot.MEDIUM) && torrent(player);
    }

    /**
     * Whether casting {@code slot} at {@code now} completes the order: 小, 中 and 大 each cast within {@code window} milliseconds (the casts are
     * kept in {@code casts}). Pure, for the tests.
     */
    static boolean completes(Map<SkillSlots.Slot, Long> casts, SkillSlots.Slot slot, long now, long window) {
        casts.put(slot, now);
        for (SkillSlots.Slot needed : new SkillSlots.Slot[]{SkillSlots.Slot.SMALL, SkillSlots.Slot.MEDIUM, SkillSlots.Slot.LARGE}) {
            Long at = casts.get(needed);
            if (at == null || now - at > window) return false;
        }
        return true;
    }

    /** A skill of the order was cast: when it completes the order (outside a torrent), the torrent opens. */
    private static void rotate(MoveKit kit, SkillSlots.Slot slot) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        if (torrent(id)) return;
        Map<SkillSlots.Slot, Long> casts = CAST.computeIfAbsent(id, ignored -> new EnumMap<>(SkillSlots.Slot.class));
        long now = System.currentTimeMillis();
        if (!completes(casts, slot, now, Math.round(kit.number(PLAN, "rotation-seconds", 10.0) * 1000.0))) {
            // (a rune lights for each skill of the order cast)
            Location head = player.getLocation().add(0.0, 2.3, 0.0);
            kit.fx(head).flatRing(head, 0.25 + 0.15 * casts.size(), 10, ARCANE, 0.9, 0.0, 0.8);
            return;
        }
        casts.clear();
        int ticks = (int) Math.round(kit.number(PLAN, "torrent-seconds", 4.0) * 20.0);
        long until = now + ticks * 50L;
        TORRENT_UNTIL.put(id, until);
        kit.sound(player.getLocation(), Sfx.MAGE_SURGE, 1.0f, 1.2f);
        kit.palette(TORRENT);
        kit.shockwave(player.getLocation(), 0.6, 3.5, 5, 1.2);
        kit.every(ticks + 1, age -> {
            Long current = TORRENT_UNTIL.get(id);
            if (current == null || current != until) return true;
            if (age >= ticks) {
                TORRENT_UNTIL.remove(id);
                kit.sound(player.getLocation(), Sfx.MAGIC, 0.5f, 0.7f);
                return true;
            }
            if (age % 2 == 0) {
                Location at = player.getLocation();
                Fx fx = kit.fx(at);
                for (int ring = 0; ring < 2; ring++) {
                    double y = 0.4 + ring * 1.2;
                    double turn = (ring == 0 ? 1 : -1) * age * 0.2;
                    for (int index = 0; index < 4; index++) {
                        double angle = turn + index * Math.PI / 2.0;
                        fx.dust(at.getX() + Math.cos(angle) * 0.9, at.getY() + y, at.getZ() + Math.sin(angle) * 0.9, TORRENT, 0.6 + 0.3 * ring, 1.0);
                    }
                }
            }
            return false;
        });
    }

    // ---- 小 -------------------------------------------------------------------------------------------------------------------

    /** Arcane missiles: three missiles, 2 ticks apart, that seek the enemies near the aim. */
    private static void missiles(MoveKit kit, double boost) {
        double rate = kit.number(PLAN, "small-rate", 0.35) * boost;
        int count = (int) Math.round(kit.number(PLAN, "small-count", 3.0));
        Vector[] axes = kit.axes();
        kit.sound(kit.player.getLocation(), Sfx.MAGE_CAST, 0.8f, 1.4f);
        for (int index = 0; index < count; index++) {
            int which = index;
            kit.later(index * 2L, () -> {
                double side = (which - (count - 1) / 2.0) * 0.5;
                Location start = ComboParts.hand(kit).add(axes[1].clone().multiply(side)).add(0.0, 0.2 * (which % 2), 0.0);
                Vector heading = axes[0].clone().add(axes[1].clone().multiply(side * 0.6)).add(new Vector(0.0, 0.25, 0.0)).normalize();
                kit.homing(start, heading, 1.3, 30, rate, 10.0, end -> {
                    kit.burst(end, 0.7);
                    kit.sound(end, Sfx.MAGE_IMPACT, 0.4f, 1.5f);
                });
            });
        }
    }

    // ---- 中 -------------------------------------------------------------------------------------------------------------------

    /** Crystal lance: a lance through everything in the line ({@code medium-length}); where it ends it shatters ({@code medium-shatter-rate}). */
    private static void lance(MoveKit kit, double boost) {
        double rate = kit.number(PLAN, "medium-rate", 0.9) * boost;
        double length = kit.number(PLAN, "medium-length", 18.0);
        double shatter = kit.number(PLAN, "medium-shatter-rate", 0.45) * boost;
        Location start = ComboParts.hand(kit);
        Vector direction = kit.aim().toVector().subtract(start.toVector());
        if (direction.lengthSquared() < 1.0E-6) direction = kit.axes()[0];
        direction.normalize();
        org.bukkit.util.RayTraceResult wall = start.getWorld().rayTraceBlocks(start, direction, length, org.bukkit.FluidCollisionMode.NEVER, true);
        if (wall != null && wall.getHitPosition() != null) length = Math.max(1.0, wall.getHitPosition().distance(start.toVector()));
        Location end = start.clone().add(direction.clone().multiply(length));
        kit.sound(start, Sfx.MAGE_CAST, 1.0f, 0.9f);
        kit.slash("line", 2.4, 60.0, 0.0, 0.0, 1.2, 0.2, 0.0, 1.0, 0.08, 0.0);
        Fx fx = kit.fx(start);
        fx.line(start, end, kit.palette(), 0.2, 1.0, 2.0, 1.6, 0.2, Particle.END_ROD, 6);
        Vector side = direction.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1.0, 0.0, 0.0);
        side.normalize().multiply(0.25);
        fx.line(start.clone().add(side), end, kit.palette(), 0.4, 0.9, 0.0, 0.8, 0.3, null, 1);
        fx.line(start.clone().subtract(side), end, kit.palette(), 0.4, 0.9, 0.0, 0.8, 0.3, null, 1);
        for (LivingEntity victim : kit.corridor(start, direction, length, 1.0)) kit.hit(victim, rate);
        kit.later(2L, () -> {
            kit.sound(end, Sfx.MAGE_IMPACT, 1.0f, 0.9f);
            kit.fx(end).sphereShell(end, 1.2, 0.0, 28, kit.palette(), 1.1);
            kit.particle(Particle.END_ROD, end, 10, 0.6, 0.15);
            for (LivingEntity victim : kit.around(end, 2.5)) kit.hit(victim, shatter);
        });
    }

    // ---- 大 -------------------------------------------------------------------------------------------------------------------

    /** Astral vortex: at the aim, a vortex for {@code large-ticks} draws the enemies in and hurts them every half second, then collapses. */
    private static void vortex(MoveKit kit, double boost) {
        double reach = kit.number(PLAN, "large-range", 20.0);
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        if (way.length() > reach) aim = eye.clone().add(way.normalize().multiply(reach));
        Location center = kit.ground(aim).add(0.0, 1.0, 0.0);
        double radius = kit.number(PLAN, "large-radius", 5.0);
        int ticks = (int) Math.round(kit.number(PLAN, "large-ticks", 50.0));
        double tick = kit.number(PLAN, "large-tick-rate", 0.2) * boost;
        double collapse = kit.number(PLAN, "large-collapse-rate", 1.3) * boost;
        double pull = kit.number(PLAN, "large-pull", 0.07);
        kit.sound(center, Sfx.MAGE_SURGE, 1.0f, 0.8f);
        kit.every(ticks + 1, age -> {
            Fx fx = kit.fx(center);
            if (age >= ticks) {
                kit.sound(center, Sfx.MAGE_IMPACT, 1.0f, 0.6f);
                kit.shockwave(center.clone().subtract(0.0, 0.9, 0.0), 0.5, radius + 0.5, 6, 1.4);
                fx.sphereShell(center, radius * 0.5, 0.0, 50, kit.palette(), 1.3);
                kit.particle(Particle.EXPLOSION_LARGE, center, 1, 0.0, 0.0);
                for (LivingEntity victim : kit.around(center, radius)) kit.hit(victim, collapse);
                return true;
            }
            double shrink = 1.0 - 0.6 * age / ticks;
            if (age % 2 == 0) {
                for (int arm = 0; arm < 3; arm++) {
                    fx.spiral(center, new Vector(1.0, 0.0, 0.0), new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0), radius * shrink, 0.3, 1.0,
                            age * 0.25 + arm * Math.PI * 2.0 / 3.0, 0.0, 18, kit.palette(), 0.9);
                }
                fx.sphereShell(center, 0.4 + 0.2 * Math.sin(age * 0.5), age * 0.3, 10, kit.palette(), 1.0);
            }
            for (LivingEntity victim : kit.around(center, radius + 1.0)) kit.drift(victim, center, pull);
            if (age > 0 && age % 10 == 0) {
                kit.sound(center, Sfx.MAGIC_DARK, 0.4f, 1.3f);
                for (LivingEntity victim : kit.around(center, radius)) kit.hit(victim, tick);
            }
            return false;
        });
    }

    // ---- util ----------------------------------------------------------------------------------------------------------------

    /** Blink: a flash ahead (back with shift), nothing hurts the mage on it; a rune left where it began bursts {@code util-rune-ticks} later. */
    private static void blink(MoveKit kit) {
        Player player = kit.player;
        Vector heading = kit.facing().multiply(player.isSneaking() ? -1.0 : 1.0);
        double power = kit.number(PLAN, "util-power", 1.3);
        int ticks = Math.max(1, (int) Math.round(kit.number(PLAN, "util-ticks", 5.0)));
        Location start = kit.ground(player.getLocation());
        int fuse = (int) Math.round(kit.number(PLAN, "util-rune-ticks", 20.0));
        double rate = kit.number(PLAN, "util-rate", 0.6);
        kit.invulnerable(ticks + 3);
        kit.sound(start, Sfx.MAGE_WIND, 0.9f, 1.4f);
        kit.every(ticks + 1, age -> {
            Location at = player.getLocation();
            kit.fx(at).line(at.clone().add(0.0, 0.1, 0.0), at.clone().add(0.0, 1.9, 0.0), kit.palette(), 0.3, 1.0, 0.0, 0.9, 0.3, null, 1);
            return false;
        });
        kit.dash(heading, power, ticks, null);
        kit.every(fuse + 1, age -> {
            Fx fx = kit.fx(start);
            if (age >= fuse) {
                kit.sound(start, Sfx.MAGE_IMPACT, 0.8f, 1.1f);
                kit.shockwave(start, 0.5, 3.0, 4, 1.2);
                for (LivingEntity victim : kit.around(start, 3.0)) kit.hit(victim, rate);
                return true;
            }
            if (age % 2 == 0) {
                double r = 1.2 + 0.3 * Math.sin(age * 0.6);
                fx.flatRing(start.clone().add(0.0, 0.12, 0.0), r, 20, kit.palette(), 0.5 + 0.5 * age / fuse, 0.3, 1.0);
            }
            return false;
        });
    }

    // ---- the lantern's criticality ----------------------------------------------------------------------------------------------

    /** The criticality of each mage at their lantern: {stacks, the time of the last attack (ms)}. */
    private static final Map<UUID, long[]> CRITICALITY = new ConcurrentHashMap<>();

    /** The criticality of {@code player} at {@code now} (0 when it has run out: too long since the last attack). */
    static int criticality(UUID player, long now, long holdMillis) {
        long[] state = CRITICALITY.get(player);
        if (state == null) return 0;
        if (now - state[1] > holdMillis) {
            CRITICALITY.remove(player, state);
            return 0;
        }
        return (int) state[0];
    }

    /** An attack at the lantern with the mage inside its circle ({@code inside}): one more stack (at most {@code max}), else all are lost. */
    static int attackAt(UUID player, boolean inside, int max, long now, long holdMillis) {
        if (!inside) {
            CRITICALITY.remove(player);
            return 0;
        }
        int count = Math.min(Math.max(1, max), criticality(player, now, holdMillis) + 1);
        CRITICALITY.put(player, new long[]{count, now});
        return count;
    }

    /** The mage left the circle: all the stacks are lost. */
    static void leave(UUID player) {
        CRITICALITY.remove(player);
    }

    /** What the mage's damage taken is multiplied by: more for each stack of criticality ({@code critical-vulnerability}). */
    public static double vulnerability(Player player) {
        if (attack == null) return 1.0;
        int stacks = criticality(player.getUniqueId(), System.currentTimeMillis(), holdMillis());
        return stacks <= 0 ? 1.0 : 1.0 + stacks * attack.plugin().getConfig().getDouble("combat.combo." + MageDeployCombo.PLAN + ".critical-vulnerability", 0.04);
    }

    static long holdMillis() {
        return attack == null ? 2000L : Math.round(attack.plugin().getConfig().getDouble("combat.combo." + MageDeployCombo.PLAN + ".critical-hold-seconds", 2.0) * 1000.0);
    }

    static void setAttack(AttackService attackService) {
        attack = attackService;
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        CAST.remove(player);
        TORRENT_UNTIL.remove(player);
        CRITICALITY.remove(player);
    }
}
