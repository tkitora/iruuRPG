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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mage's skills with a ranged weapon (t168, redesigned by t198: the torrent that let 小 and 中 be cast for nothing was too strong, and the
 * skills looked too plain to be fun). The class is about the order of its skills, the <b>flow</b>:
 * <ul>
 *   <li>each of 小・中・大 cast takes {@code flow-seconds} (1.5) off what is left of the cooldowns of the other two ({@link SkillSlots#shorten});</li>
 *   <li>casting 小, 中 and 大 each within {@code rotation-seconds} (10) makes the third a <b>completion</b>: {@code completion-bonus} (40%) harder,
 *       and a starfall follows it at the aim ({@code starfall-count} stars, {@code starfall-rate} each). The ULT (the meteor) with a ranged weapon
 *       makes the next skill a completion. Nothing is ever cast for free.</li>
 * </ul>
 * The effects are drawn in the weapon's colors (its element) with a white of stars, large and at the aim (not in front of the eyes). Every number
 * is under {@code combat.combo.mage_range} (all provisional), the cooldowns too.
 * <ul>
 *   <li>小 star bolts: a magic circle opens behind the mage and five star bolts spiral out of it after the enemies near the aim; each bursts into
 *       a star.</li>
 *   <li>中 crystal lance: a magic circle at the feet, and a great lance of crystal through everything in the line; crystals stay along it and
 *       shatter together a moment later.</li>
 *   <li>大 astral vortex: a magic circle opens in the sky over the aim and a vortex under it draws the enemies in; a pillar of light falls from
 *       the circle and it bursts (a supernova).</li>
 *   <li>util blink (2 charges): a flash ahead (back with shift), nothing hurts the mage on it; a rune left where it began bursts a second later.</li>
 * </ul>
 * The normal combo is the mage's as before ({@link MageMoves}). The mage's lantern is {@link MageDeployCombo} (its criticality is counted here:
 * {@link #vulnerability}).
 */
public final class MageArcana {

    static final String PLAN = "mage_range";
    /** The light of the stars: the touch of white over the weapon's colors (the cores of the stars, the completion). */
    static final Gradient STAR = Gradient.of(Color.fromRGB(200, 190, 255), Color.fromRGB(245, 240, 255), Color.fromRGB(255, 255, 255));
    /** The furnace burning white (the lantern's criticality, {@link MageDeployCombo}). */
    static final Gradient WHITE_HOT = Gradient.of(Color.fromRGB(255, 190, 120), Color.fromRGB(255, 236, 205), Color.fromRGB(255, 255, 255));

    private static AttackService attack;
    private static SkillSlots slots;
    /** When each of 小・中・大 was last cast, per mage (ms). */
    private static final Map<UUID, Map<SkillSlots.Slot, Long>> CAST = new ConcurrentHashMap<>();
    /** The mages whose next skill is a completion (the meteor). */
    private static final Set<UUID> PRIMED = ConcurrentHashMap.newKeySet();

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
        if (slot == SkillSlots.Slot.UTIL) {
            blink(kit);
            return true;
        }
        UUID id = player.getUniqueId();
        Map<SkillSlots.Slot, Long> casts = CAST.computeIfAbsent(id, ignored -> new EnumMap<>(SkillSlots.Slot.class));
        long now = System.currentTimeMillis();
        boolean completion = completes(casts, slot, now, Math.round(kit.number(PLAN, "rotation-seconds", 10.0) * 1000.0)) | PRIMED.remove(id);
        double boost = completion ? 1.0 + kit.number(PLAN, "completion-bonus", 0.4) : 1.0;
        Location target;
        switch (slot) {
            case SMALL -> target = starBolts(kit, boost);
            case MEDIUM -> target = lance(kit, boost);
            case LARGE -> target = vortex(kit, boost);
            default -> {
                return false;
            }
        }
        flow(kit, slot);
        if (completion) {
            casts.clear();
            complete(kit, target);
        } else {
            runes(kit, casts.size());
        }
        return true;
    }

    /** The cooldowns of the mage's ranged skills (their own seconds; util 2 charges, t198); with another kind of weapon none. */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot) {
        String base = "combat.combo." + PLAN + ".";
        return switch (slot) {
            case SMALL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "small-seconds", 5.0));
            case MEDIUM -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "medium-seconds", 9.0));
            case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 20.0));
            case UTIL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "util-charges", 2.0)), 0.0,
                    config.getDouble(base + "util-charge-seconds", 5.0), config.getDouble(base + "util-empty-seconds", 9.0));
            default -> SkillSlots.Mechanics.NONE;
        };
    }

    // ---- the flow -------------------------------------------------------------------------------------------------------------

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

    /** {@code slot} was cast: what is left of the cooldowns of the other two of 小・中・大 is {@code flow-seconds} shorter. */
    private static void flow(MoveKit kit, SkillSlots.Slot slot) {
        if (slots == null) return;
        long millis = Math.round(kit.number(PLAN, "flow-seconds", 1.5) * 1000.0);
        long now = System.currentTimeMillis();
        for (SkillSlots.Slot other : new SkillSlots.Slot[]{SkillSlots.Slot.SMALL, SkillSlots.Slot.MEDIUM, SkillSlots.Slot.LARGE}) {
            if (other != slot) slots.shorten(kit.player.getUniqueId(), other, AttackType.RANGE, millis, now);
        }
    }

    /** The runes over the mage's head: one lit for each skill of the order cast. */
    private static void runes(MoveKit kit, int lit) {
        Player player = kit.player;
        kit.every(16, age -> {
            Location head = player.getLocation().add(0.0, 2.4, 0.0);
            Fx fx = kit.fx(head);
            for (int rune = 0; rune < 3; rune++) {
                double angle = age * 0.25 + rune * Math.PI * 2.0 / 3.0;
                Location at = head.clone().add(Math.cos(angle) * 0.55, 0.0, Math.sin(angle) * 0.55);
                fx.sphereShell(at, rune < lit ? 0.14 : 0.08, age * 0.4, rune < lit ? 8 : 4, rune < lit ? STAR : kit.palette(), rune < lit ? 1.0 : 0.6);
            }
            return false;
        });
        kit.sound(player.getLocation(), Sfx.MAGIC, 0.4f, 1.2f + 0.2f * lit);
    }

    /** A completion: a burst of starlight round the mage, and a starfall at {@code target}. */
    private static void complete(MoveKit kit, Location target) {
        Location at = kit.player.getLocation();
        kit.sound(at, Sfx.MAGE_SURGE, 1.0f, 1.3f);
        kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.3, 0.0, 30, STAR, 1.1);
        kit.shockwave(at, 0.5, 3.0, 5, 1.1);
        if (target != null) starfall(kit, target);
    }

    /**
     * The starfall of a completion: {@code starfall-count} stars fall one after another on points round {@code center} (within
     * {@code starfall-radius}), each {@code starfall-rate} on the enemies within 2 blocks where it lands.
     */
    private static void starfall(MoveKit kit, Location center) {
        int count = (int) Math.round(kit.number(PLAN, "starfall-count", 6.0));
        double radius = kit.number(PLAN, "starfall-radius", 3.5);
        double rate = kit.number(PLAN, "starfall-rate", 0.25);
        Location ground = kit.ground(center);
        for (int index = 0; index < count; index++) {
            kit.later(4L + index * 5L, () -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                double angle = random.nextDouble(Math.PI * 2.0);
                double r = Math.sqrt(random.nextDouble()) * radius;
                Location land = kit.ground(ground.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r)).add(0.0, 0.3, 0.0);
                Location sky = land.clone().add(random.nextDouble(-2.0, 2.0), 9.0, random.nextDouble(-2.0, 2.0));
                fallingStar(kit, sky, land, rate);
            });
        }
    }

    /** One star falling from {@code sky} to {@code land} over 4 ticks, bursting there ({@code rate} round it). */
    private static void fallingStar(MoveKit kit, Location sky, Location land, double rate) {
        Vector step = land.toVector().subtract(sky.toVector()).multiply(0.25);
        kit.every(5, age -> {
            if (age < 4) {
                Location from = sky.clone().add(step.clone().multiply(age));
                Location to = from.clone().add(step);
                kit.fx(to).line(from, to, STAR, 0.2, 1.0, 0.0, 1.3, 0.2, Particle.END_ROD, 2);
                return false;
            }
            starBurst(kit, land, 1.0);
            kit.sound(land, Sfx.MAGE_IMPACT, 0.6f, 1.6f);
            for (LivingEntity victim : kit.around(land, 2.0)) kit.hit(victim, rate);
            return true;
        });
    }

    /** A star bursting at {@code at}: a five-pointed star of the weapon's colors with a white core, sparks thrown out. */
    static void starBurst(MoveKit kit, Location at, double size) {
        Fx fx = kit.fx(at);
        Vector facing = kit.facing();
        Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        Vector up = new Vector(0.0, 1.0, 0.0);
        double turn = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        for (int ray = 0; ray < 5; ray++) {
            double a0 = turn + Math.PI * 2.0 * ray / 5.0;
            double a1 = turn + Math.PI * 2.0 * (ray + 2) / 5.0;
            Location from = at.clone().add(right.clone().multiply(Math.cos(a0) * size)).add(up.clone().multiply(Math.sin(a0) * size));
            Location to = at.clone().add(right.clone().multiply(Math.cos(a1) * size)).add(up.clone().multiply(Math.sin(a1) * size));
            fx.line(from, to, kit.palette(), 0.4, 1.0, 0.0, 1.0, 0.18, null, 1);
        }
        fx.sphereShell(at, 0.25 * size, 0.0, 10, STAR, 1.2);
        kit.particle(Particle.FIREWORKS_SPARK, at, (int) Math.round(6 * size), 0.1, 0.12);
    }

    /**
     * The mage's ULT, the meteor, was cast with {@code weapon} (t168, t198): with a ranged weapon the next skill is a completion; with the lantern
     * the criticality is full at once.
     */
    static void ultimate(AttackService attackService, Player player, RpgItemDefinition weapon, java.util.Map<String, Double> values) {
        attack = attackService;
        if (weapon == null) return;
        if (weapon.attackType() == AttackType.RANGE) {
            PRIMED.add(player.getUniqueId());
            Location head = player.getLocation().add(0.0, 2.4, 0.0);
            Fx.of(player, player.getWorld(), head).sphereShell(head, 0.6, 0.0, 16, STAR, 1.0);
        } else if (weapon.attackType() == AttackType.DEPLOY) {
            int max = (int) Math.round(attackService.plugin().getConfig().getDouble("combat.combo." + MageDeployCombo.PLAN + ".critical-max", 8.0));
            CRITICALITY.put(player.getUniqueId(), new long[]{max, System.currentTimeMillis()});
            Sfx.MAGE_SURGE.play(player.getLocation(), 0.8f, 1.5f);
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------------

    /** Where the mage aims, at most {@code reach} blocks away. */
    private static Location aimWithin(MoveKit kit, double reach) {
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        if (way.length() > reach) aim = eye.clone().add(way.normalize().multiply(reach));
        return aim;
    }

    // ---- 小 -------------------------------------------------------------------------------------------------------------------

    /**
     * Star bolts: a magic circle opens behind the mage's shoulders for half a second, and {@code small-count} (5) bolts spiral out of its rim, 2
     * ticks apart, after the enemies near the aim ({@code small-rate} each); each bursts into a star. Where they were sent (for the starfall).
     */
    private static Location starBolts(MoveKit kit, double boost) {
        double rate = kit.number(PLAN, "small-rate", 0.25) * boost;
        int count = (int) Math.round(kit.number(PLAN, "small-count", 5.0));
        Location aim = aimWithin(kit, kit.number(PLAN, "small-range", 24.0));
        Player player = kit.player;
        kit.sound(player.getLocation(), Sfx.MAGE_CAST, 0.9f, 1.3f);
        kit.every(12, age -> {
            Vector[] axes = kit.axes();
            Location back = player.getEyeLocation().subtract(axes[0].clone().multiply(0.7)).subtract(0.0, 0.2, 0.0);
            if (age % 2 == 0) SkillFx.circle(player, back, axes[1], axes[2], 0.9 + 0.03 * age, age, kit.palette());
            return false;
        });
        for (int index = 0; index < count; index++) {
            int which = index;
            kit.later(2L + index * 2L, () -> {
                Vector[] axes = kit.axes();
                Location back = player.getEyeLocation().subtract(axes[0].clone().multiply(0.7)).subtract(0.0, 0.2, 0.0);
                double angle = Math.PI * 2.0 * which / count + Math.PI / 2.0;
                Vector out = axes[1].clone().multiply(Math.cos(angle)).add(axes[2].clone().multiply(Math.sin(angle)));
                Location start = back.clone().add(out.clone().multiply(1.0));
                // out to the side first, then they curve in after the enemies
                Vector heading = out.clone().multiply(0.8).add(aim.toVector().subtract(start.toVector()).normalize()).normalize();
                kit.sound(start, Sfx.MAGIC, 0.35f, 1.6f + 0.08f * which);
                kit.homing(start, heading, 1.25, 36, rate, 12.0, end -> {
                    starBurst(kit, end, 0.7);
                    kit.sound(end, Sfx.MAGE_IMPACT, 0.4f, 1.7f);
                });
            });
        }
        return aim;
    }

    // ---- 中 -------------------------------------------------------------------------------------------------------------------

    /**
     * Crystal lance: a magic circle at the mage's feet, then a great lance of crystal through everything in the line ({@code medium-rate}, up to
     * {@code medium-length} or a wall). Crystals stay along it every {@code medium-crystal-gap} blocks and shatter together
     * {@code medium-shatter-ticks} later ({@code medium-shatter-rate} on each enemy within 1.6 blocks of one, once). Where the lance ended.
     */
    private static Location lance(MoveKit kit, double boost) {
        double rate = kit.number(PLAN, "medium-rate", 0.9) * boost;
        double length = kit.number(PLAN, "medium-length", 18.0);
        double shatter = kit.number(PLAN, "medium-shatter-rate", 0.35) * boost;
        double gap = Math.max(1.0, kit.number(PLAN, "medium-crystal-gap", 2.0));
        int fuse = (int) Math.round(kit.number(PLAN, "medium-shatter-ticks", 12.0));
        Player player = kit.player;
        Location start = ComboParts.hand(kit);
        Vector aimed = kit.aim().toVector().subtract(start.toVector());
        Vector direction = (aimed.lengthSquared() < 1.0E-6 ? kit.axes()[0] : aimed).normalize();
        org.bukkit.util.RayTraceResult wall = start.getWorld().rayTraceBlocks(start, direction, length, org.bukkit.FluidCollisionMode.NEVER, true);
        if (wall != null && wall.getHitPosition() != null) length = Math.max(1.0, wall.getHitPosition().distance(start.toVector()));
        Location end = start.clone().add(direction.clone().multiply(length));
        Location feet = kit.ground(player.getLocation()).add(0.0, 0.15, 0.0);
        SkillFx.circle(player, feet, new Vector(1.0, 0.0, 0.0), new Vector(0.0, 0.0, 1.0), 1.6, 0, kit.palette());
        kit.sound(start, Sfx.MAGE_CAST, 1.0f, 0.8f);
        kit.sound(start, Sfx.MAGE_BLAST, 0.6f, 1.4f);
        Vector across = direction.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        Vector side = (across.lengthSquared() < 1.0E-6 ? new Vector(1.0, 0.0, 0.0) : across).normalize();
        Vector up = side.clone().crossProduct(direction).normalize();
        double total = length;
        // the lance grows out along the line over 3 ticks: a bright core and four facets round it
        for (int step = 0; step < 3; step++) {
            int grown = step;
            kit.later(step, () -> {
                double from = total * grown / 3.0;
                double to = total * (grown + 1) / 3.0;
                Location a = start.clone().add(direction.clone().multiply(from));
                Location b = start.clone().add(direction.clone().multiply(to));
                Fx fx = kit.fx(a);
                fx.line(a, b, STAR, 0.4, 1.0, 0.0, 1.5, 0.18, Particle.END_ROD, 3);
                for (int facet = 0; facet < 4; facet++) {
                    double angle = facet * Math.PI / 2.0 + Math.PI / 4.0;
                    Vector off = side.clone().multiply(Math.cos(angle) * 0.35).add(up.clone().multiply(Math.sin(angle) * 0.35));
                    fx.line(a.clone().add(off), b.clone().add(off.clone().multiply(0.6)), kit.palette(), 0.3, 0.9, 0.0, 1.0, 0.25, null, 1);
                }
            });
        }
        for (LivingEntity victim : kit.corridor(start, direction, length, 1.1)) kit.hit(victim, rate);
        // the crystals left along it, and their shattering
        java.util.List<Location> crystals = new java.util.ArrayList<>();
        for (double d = gap; d <= length; d += gap) crystals.add(start.clone().add(direction.clone().multiply(d)));
        crystals.add(end);
        kit.every(fuse + 1, age -> {
            if (age < fuse) {
                if (age % 2 == 0) {
                    for (Location crystal : crystals) {
                        double grow = Math.min(1.0, (age + 2) / 6.0);
                        crystal(kit, crystal, 0.35 + 0.25 * grow, age);
                    }
                }
                return false;
            }
            kit.sound(end, Sfx.MAGE_IMPACT, 1.0f, 1.3f);
            kit.sound(start.clone().add(direction.clone().multiply(total * 0.5)), Sfx.MAGE_BLAST, 0.7f, 1.7f);
            Set<UUID> shattered = new HashSet<>();
            for (Location crystal : crystals) {
                kit.fx(crystal).sphereShell(crystal, 0.8, 0.0, 14, kit.palette(), 1.1);
                kit.particle(Particle.END_ROD, crystal, 4, 0.3, 0.12);
                for (LivingEntity victim : kit.around(crystal, 1.6)) {
                    if (shattered.add(victim.getUniqueId())) kit.hit(victim, shatter);
                }
            }
            kit.fx(end).sphereShell(end, 1.4, 0.0, 30, STAR, 1.2);
            return true;
        });
        return end;
    }

    /** A crystal of {@code size} at {@code at}: a diamond standing on its point, turning. */
    private static void crystal(MoveKit kit, Location at, double size, int age) {
        Fx fx = kit.fx(at);
        Location top = at.clone().add(0.0, size * 1.4, 0.0);
        Location bottom = at.clone().subtract(0.0, size * 0.6, 0.0);
        for (int corner = 0; corner < 4; corner++) {
            double angle = age * 0.15 + corner * Math.PI / 2.0;
            Location rim = at.clone().add(Math.cos(angle) * size * 0.5, size * 0.2, Math.sin(angle) * size * 0.5);
            fx.line(bottom, rim, kit.palette(), 0.3, 0.8, 0.0, 0.7, 0.2, null, 1);
            fx.line(rim, top, kit.palette(), 0.5, 1.0, 0.0, 0.7, 0.2, null, 1);
        }
    }

    // ---- 大 -------------------------------------------------------------------------------------------------------------------

    /**
     * Astral vortex: at the aim, a magic circle opens in the sky ({@code large-radius}) and a vortex under it draws the enemies in for
     * {@code large-ticks} ({@code large-tick-rate} every half second); then a pillar of light falls from the circle and it bursts
     * ({@code large-collapse-rate}). Where it was.
     */
    private static Location vortex(MoveKit kit, double boost) {
        Location center = kit.ground(aimWithin(kit, kit.number(PLAN, "large-range", 20.0))).add(0.0, 1.0, 0.0);
        double radius = kit.number(PLAN, "large-radius", 5.0);
        int ticks = (int) Math.round(kit.number(PLAN, "large-ticks", 50.0));
        double tick = kit.number(PLAN, "large-tick-rate", 0.2) * boost;
        double collapse = kit.number(PLAN, "large-collapse-rate", 1.3) * boost;
        double pull = kit.number(PLAN, "large-pull", 0.07);
        Player player = kit.player;
        Location sky = center.clone().add(0.0, 6.0, 0.0);
        Vector east = new Vector(1.0, 0.0, 0.0);
        Vector south = new Vector(0.0, 0.0, 1.0);
        kit.sound(center, Sfx.MAGE_SURGE, 1.0f, 0.8f);
        kit.every(ticks + 9, age -> {
            Fx fx = kit.fx(center);
            if (age < ticks) {
                double open = Math.min(1.0, (age + 1) / 8.0);
                if (age % 2 == 0) {
                    SkillFx.circle(player, sky, east, south, radius * open, age, kit.palette());
                    if (age % 4 == 0) SkillFx.circle(player, center.clone().subtract(0.0, 0.85, 0.0), east, south, radius * 0.9, -age, kit.palette());
                    double shrink = 1.0 - 0.6 * age / ticks;
                    for (int arm = 0; arm < 4; arm++) {
                        fx.spiral(center, east, south, new Vector(0.0, 1.0, 0.0), radius * shrink, 0.3, 1.0,
                                age * 0.28 + arm * Math.PI / 2.0, 0.0, 18, kit.palette(), 0.9);
                    }
                    // stars turning round it
                    for (int star = 0; star < 3; star++) {
                        double angle = -age * 0.18 + star * Math.PI * 2.0 / 3.0;
                        Location at = center.clone().add(Math.cos(angle) * radius * 0.8, 0.6 + 0.4 * Math.sin(age * 0.3 + star), Math.sin(angle) * radius * 0.8);
                        fx.sphereShell(at, 0.18, age * 0.4, 6, STAR, 1.0);
                    }
                    fx.sphereShell(center, 0.4 + 0.2 * Math.sin(age * 0.5), age * 0.3, 12, STAR, 1.0);
                }
                for (LivingEntity victim : kit.around(center, radius + 1.0)) kit.drift(victim, center, pull);
                if (age > 0 && age % 10 == 0) {
                    kit.sound(center, Sfx.MAGIC_DARK, 0.5f, 1.3f);
                    for (LivingEntity victim : kit.around(center, radius)) kit.hit(victim, tick);
                }
                return false;
            }
            int fall = age - ticks;
            if (fall < 8) {
                // the pillar of light coming down from the circle
                double reach = 6.0 * (fall + 1) / 8.0;
                fx.line(sky, sky.clone().subtract(0.0, reach, 0.0), STAR, 0.2, 1.0, 0.0, 2.0, 0.15, Particle.END_ROD, 4);
                for (double y = 0.0; y < reach; y += 1.0) fx.flatRing(sky.clone().subtract(0.0, y, 0.0), 0.7, 12, kit.palette(), 0.8, 0.0, 1.0);
                if (fall == 0) kit.sound(sky, Sfx.MAGE_SURGE, 1.0f, 1.5f);
                return false;
            }
            kit.sound(center, Sfx.MAGE_BLAST, 1.2f, 0.8f);
            kit.sound(center, Sfx.MAGE_IMPACT, 1.0f, 0.6f);
            kit.shockwave(center.clone().subtract(0.0, 0.9, 0.0), 0.5, radius + 1.0, 7, 1.5);
            kit.pillar(center.clone().subtract(0.0, 0.9, 0.0), radius * 0.5, 7.0);
            fx.sphereShell(center, radius * 0.6, 0.0, 60, kit.palette(), 1.4);
            kit.later(2L, () -> kit.fx(center).sphereShell(center, radius * 0.9, 0.5, 70, STAR, 1.1));
            kit.particle(Particle.EXPLOSION_LARGE, center, 2, 0.8, 0.0);
            kit.particle(Particle.FIREWORKS_SPARK, center, 30, 0.6, 0.35);
            for (LivingEntity victim : kit.around(center, radius)) kit.hit(victim, collapse);
            return true;
        });
        return center;
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
            if (age >= fuse) {
                kit.sound(start, Sfx.MAGE_IMPACT, 0.8f, 1.1f);
                kit.shockwave(start, 0.5, 3.0, 4, 1.2);
                starBurst(kit, start.clone().add(0.0, 1.0, 0.0), 1.2);
                for (LivingEntity victim : kit.around(start, 3.0)) kit.hit(victim, rate);
                return true;
            }
            if (age % 2 == 0) SkillFx.circle(player, start.clone().add(0.0, 0.12, 0.0), new Vector(1.0, 0.0, 0.0), new Vector(0.0, 0.0, 1.0), 1.2, age, kit.palette());
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

    /** The skills' cooldowns (the flow shortens them). */
    static void setSlots(SkillSlots skillSlots) {
        slots = skillSlots;
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        CAST.remove(player);
        PRIMED.remove(player);
        CRITICALITY.remove(player);
    }
}
