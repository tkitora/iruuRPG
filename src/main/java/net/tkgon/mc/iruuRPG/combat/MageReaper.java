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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * The mage with a scythe (a melee weapon of its combo) is the <b>reaper</b> (t167: nekorozv left all of it to us, the skills, the combo, the
 * look and the sounds). Its cuts carve <b>marks of death</b> into the enemies ({@link ReaperMarks}: up to {@code mark-max} (3) on one enemy, for
 * {@code mark-seconds} (8) after the last one; small blue-green flames turning over its head); its skills <b>reap</b> them: each mark adds to the
 * blow, and its soul flies to the reaper and heals them ({@code soul-heal} percent of the maximum life each, at most {@code soul-heal-cap} in one
 * skill). Every number is under {@code combat.combo.mage_melee} (all provisional); the look is the night and the blue-green of souls, and the
 * sounds are {@link Sfx} REAPER_*. With another kind of weapon the mage's skills are what they were ({@link SlotMoves}).
 * <ul>
 *   <li>小 (soul reap, 2 charges: 4 s each while one is left, 9 s for both once none is): a wide reaping arc ahead that reaps the marks of everyone
 *       it cuts.</li>
 *   <li>中 (the thrown scythe, 10 s): the scythe flies ahead turning (9 blocks) and comes back to the reaper, cutting each enemy once on the way
 *       out and once on the way back (a mark each time; the way back draws them in). Its re-activation (within 3 s): the reaper goes through as a
 *       wraith (nothing hurts them) to the enemy with the most marks within 12 blocks, a little past it, and reaps round where it ends with a
 *       cross cut.</li>
 *   <li>大 (the hour of the harvest, 22 s): a gate of souls opens round the reaper (6 blocks; it goes with them, and they move freely): the
 *       enemies in it are marked, slowed and drawn in, and the reaper takes a quarter less damage. After 1 s a great scythe goes round twice,
 *       marking all it cuts; then every mark round the reaper is reaped at once.</li>
 *   <li>util (wraith step, 5 s): the reaper goes through as a wraith (nothing hurts them) 5 blocks ahead, or back with shift held, marking the
 *       enemies passed.</li>
 *   <li>The normal combo ({@link MageMeleeCombo}) marks every enemy it cuts. The ULT is the mage's meteor, as with any weapon.</li>
 * </ul>
 */
public final class MageReaper {

    static final String PLAN = MageMeleeCombo.PLAN;
    /** The reaper's colors: the black of the night, the blue-green of souls, a pale bone white. */
    static final Gradient SOUL = Gradient.of(Color.fromRGB(10, 14, 30), Color.fromRGB(40, 190, 205), Color.fromRGB(215, 255, 248));
    private static final List<Color> SOUL_SLASH = List.of(Color.fromRGB(14, 22, 40), Color.fromRGB(50, 200, 215), Color.fromRGB(225, 255, 250));
    /** The tick the marks of each reaper were last drawn (a drawing that stopped, the reaper dead or gone, is started again). */
    private static final Map<UUID, Integer> DRAWN = new HashMap<>();

    private MageReaper() {
    }

    // ---- registering --------------------------------------------------------------------------------------------------------

    /** The mage's skill {@code slot}: the reaper's with a melee weapon of the class's combo, else the heavy attack it threw ({@code heavyOf}). */
    static SkillSlots.Move move(AttackService attack, SkillSlots.Slot slot, ToIntFunction<AttackType> heavyOf) {
        return new SkillSlots.Move() {
            @Override
            public boolean use(Player player, RpgItemDefinition weapon) {
                if (weapon == null) return false;
                // (the mage's ranged skills are the arcana's since t168)
                if (MageArcana.own(attack, player, weapon)) return MageArcana.use(attack, player, weapon, slot);
                if (!reworked(attack, player, weapon)) return attack.slotHeavy(player, weapon, heavyOf.applyAsInt(weapon.attackType()));
                MoveKit kit = attack.skillKit(player, weapon);
                if (kit == null) return false;
                look(kit);
                switch (slot) {
                    case SMALL -> small(kit);
                    case MEDIUM -> medium(kit);
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
                reapThrough(kit);
                return true;
            }
        };
    }

    /**
     * The charges, re-activation and cooldowns of the reaper's skills (the mage's ranged skills keep the class's cooldowns, so the reaper's are
     * here): 小 2 charges, 中 a re-activation; with another kind of weapon none.
     */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot, AttackType kind) {
        if (kind != AttackType.MELEE) return SkillSlots.Mechanics.NONE;
        String base = "combat.combo." + PLAN + ".";
        return switch (slot) {
            case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 2.0)), 0.0,
                    config.getDouble(base + "small-charge-seconds", 4.0), config.getDouble(base + "small-empty-seconds", 9.0));
            case MEDIUM -> new SkillSlots.Mechanics(1, config.getDouble(base + "medium-recast-seconds", 3.0), 0.0, config.getDouble(base + "medium-seconds", 10.0));
            case LARGE -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "large-seconds", 22.0));
            case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 5.0));
            default -> SkillSlots.Mechanics.NONE;
        };
    }

    private static boolean reworked(AttackService attack, Player player, RpgItemDefinition weapon) {
        return weapon.attackType() == AttackType.MELEE && attack.hasClassCombo(player, weapon);
    }

    /** The look: the night and the blue-green of souls. */
    static void look(MoveKit kit) {
        kit.palette(SOUL);
        kit.slashColors(SOUL_SLASH);
    }

    private static double number(MoveKit kit, String key, double fallback) {
        return kit.number(PLAN, key, fallback);
    }

    private static Vector flat(Vector vector) {
        Vector flat = vector.clone().setY(0.0);
        return flat.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : flat.normalize();
    }

    // ---- the marks ------------------------------------------------------------------------------------------------------------

    /** Cuts {@code amount} marks into {@code victim}; a full set rings once. */
    static void mark(MoveKit kit, LivingEntity victim, int amount) {
        long now = System.currentTimeMillis();
        int max = (int) Math.round(number(kit, "mark-max", 3.0));
        long until = now + Math.round(number(kit, "mark-seconds", 8.0) * 1000.0);
        UUID owner = kit.player.getUniqueId();
        int before = ReaperMarks.count(owner, victim.getUniqueId(), now);
        int count = ReaperMarks.add(owner, victim.getUniqueId(), amount, max, until, now);
        if (count >= max && before < max) kit.sound(victim.getLocation(), Sfx.REAPER_MARK, 0.6f, 1.0f);
        drawMarks(kit);
    }

    /** {@code victim} cut by the scythe: a mark, and a few souls torn off along the way the blade went ({@code direction}). */
    static void marked(MoveKit kit, LivingEntity victim, Vector direction) {
        mark(kit, victim, 1);
        Location from = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        Vector way = direction.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : direction.clone().normalize();
        kit.fx(from).line(from.clone().subtract(way.clone().multiply(0.6)), from.clone().add(way.clone().multiply(1.4)), SOUL, 0.3, 1.0, 1.5, 0.9, 0.3, null, 1);
        kit.particle(Particle.SOUL, from.clone().add(way.clone().multiply(0.8)), 2, 0.2, 0.02);
    }

    /** {@code rate} on each of {@code targets}; the ones hit are marked. */
    static void cut(MoveKit kit, List<LivingEntity> targets, double rate, Vector direction) {
        for (LivingEntity victim : targets) {
            if (kit.hit(victim, rate)) marked(kit, victim, direction);
        }
    }

    /** Draws the reaper's marks over the enemies while there are any (one drawing for each reaper). */
    private static void drawMarks(MoveKit kit) {
        UUID owner = kit.player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        Integer last = DRAWN.get(owner);
        if (last != null && tick - last <= 6) return;
        DRAWN.put(owner, tick);
        kit.every(20 * 60, age -> {
            DRAWN.put(owner, Bukkit.getCurrentTick());
            if (age % 3 != 0) return false;
            long now = System.currentTimeMillis();
            Map<UUID, Integer> marks = ReaperMarks.all(owner, now);
            if (marks.isEmpty()) {
                DRAWN.remove(owner);
                return true;
            }
            for (Map.Entry<UUID, Integer> entry : marks.entrySet()) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid() || living.getWorld() != kit.player.getWorld()) {
                    ReaperMarks.take(owner, entry.getKey(), now);
                    continue;
                }
                drawMark(kit, living, entry.getValue(), age);
            }
            return false;
        });
    }

    /** {@code count} small flames of souls turning over the head of {@code victim}; a ring under them when the marks are full. */
    private static void drawMark(MoveKit kit, LivingEntity victim, int count, int age) {
        Location top = victim.getLocation().add(0.0, victim.getHeight() + 0.45, 0.0);
        Fx fx = kit.fx(top);
        if (!fx.active()) return;
        double spin = age * 0.25;
        for (int index = 0; index < count; index++) {
            double angle = spin + index * Math.PI * 2.0 / count;
            double x = top.getX() + Math.cos(angle) * 0.38;
            double z = top.getZ() + Math.sin(angle) * 0.38;
            fx.dust(x, top.getY(), z, SOUL, 0.55 + 0.15 * index, 1.0);
            fx.dust(x, top.getY() + 0.14, z, SOUL, 0.95, 0.6);
        }
        if (count >= (int) Math.round(number(kit, "mark-max", 3.0))) fx.flatRing(top.clone().subtract(0.0, 0.05, 0.0), 0.5, 10, SOUL, 0.7, 0.2, 0.6);
    }

    // ---- reaping ---------------------------------------------------------------------------------------------------------------

    /** How much one skill may still heal (percent of the maximum life). */
    private static double[] budget(MoveKit kit) {
        return new double[]{number(kit, "soul-heal-cap", 6.0)};
    }

    /**
     * Reaps {@code victim}: its marks come off, and the blow is {@code rate} and {@code bonus} more for each of them; their souls fly to the
     * reaper and heal them ({@code soul-heal} percent each, while {@code budget} lasts). How many marks were reaped.
     */
    private static int reap(MoveKit kit, LivingEntity victim, double rate, double bonus, double[] budget) {
        int marks = ReaperMarks.take(kit.player.getUniqueId(), victim.getUniqueId(), System.currentTimeMillis());
        if (!kit.hit(victim, rate + bonus * marks) || marks == 0) return 0;
        reaped(kit, victim, marks);
        double heal = Math.min(budget[0], marks * number(kit, "soul-heal", 1.0));
        if (heal > 0.0) {
            budget[0] -= heal;
            kit.heal(heal);
        }
        return marks;
    }

    /** What a reap looks like: the marks burst on the enemy, and their souls fly to the reaper. */
    private static void reaped(MoveKit kit, LivingEntity victim, int marks) {
        Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        kit.fx(at).sphereShell(at, 0.5 + 0.2 * marks, 0.0, 14 + 6 * marks, SOUL, 1.0);
        kit.particle(Particle.SCULK_SOUL, at, 2 + marks, 0.3, 0.02);
        Location[] position = {at.clone()};
        kit.every(16, age -> {
            Location chest = kit.player.getLocation().add(0.0, 1.1, 0.0);
            if (chest.getWorld() != position[0].getWorld()) return true;
            Vector way = chest.toVector().subtract(position[0].toVector());
            double length = way.length();
            if (length < 0.7) {
                kit.particle(Particle.SOUL, chest, 2, 0.2, 0.01);
                return true;
            }
            Location next = position[0].clone().add(way.multiply(Math.min(1.0, (0.5 + 0.12 * age) / length)));
            kit.fx(next).line(position[0], next, SOUL, 0.5, 0.95, 1.0, 0.8, 0.25, null, 1);
            position[0] = next;
            return false;
        });
    }

    /** After a skill reaped {@code reaped} marks: the reap's sound, then the souls taken in. */
    private static void afterReap(MoveKit kit, int reaped) {
        if (reaped <= 0) return;
        kit.sound(kit.player.getLocation(), Sfx.REAPER_REAP, Math.min(1.0f, 0.6f + 0.1f * reaped), 1.0f);
        kit.later(8L, () -> kit.sound(kit.player.getLocation(), Sfx.REAPER_SOUL, 0.7f, 1.0f));
    }

    /** The reaper goes through as a wraith for {@code ticks}: a figure of souls left behind at every step. */
    private static void wraith(MoveKit kit, int ticks) {
        kit.every(ticks + 2, age -> {
            Location at = kit.player.getLocation();
            kit.fx(at).line(at.clone().add(0.0, 0.1, 0.0), at.clone().add(0.0, 1.9, 0.0), SOUL, 0.1, 0.8, 0.0, 1.0, 0.25, null, 1);
            kit.particle(Particle.SOUL, at.clone().add(0.0, 0.9, 0.0), 1, 0.3, 0.01);
            if (age % 2 == 0) kit.particle(Particle.SOUL_FIRE_FLAME, at.clone().add(0.0, 0.2, 0.0), 2, 0.25, 0.01);
            return false;
        });
    }

    // ---- 小 ----------------------------------------------------------------------------------------------------------------

    private static void small(MoveKit kit) {
        double rate = number(kit, "small-rate", 0.7);
        double bonus = number(kit, "small-mark-rate", 0.3);
        double reach = number(kit, "small-reach", 4.0);
        // a wide reaping arc from the right, low, round to the left, and the ghost of a second blade behind it
        kit.slash("arc", reach, 270.0, 78.0, 0.0, reach * 0.8, 0.6, 0.0, 1.5, 0.16, 0.0);
        kit.slash("arc", reach - 0.5, 250.0, 70.0, 0.0, reach * 0.7, 0.4, 0.0, 1.25, 0.14, 0.05);
        kit.sound(kit.player.getLocation(), Sfx.REAPER_HEAVY, 0.9f, 1.05f);
        double[] budget = budget(kit);
        int reaped = 0;
        for (LivingEntity victim : kit.cone(reach, 80.0)) reaped += reap(kit, victim, rate, bonus, budget);
        afterReap(kit, reaped);
    }

    // ---- 中 ----------------------------------------------------------------------------------------------------------------

    /** The scythe thrown: ahead (turning) and back to the reaper, cutting each enemy once each way; the way back draws them in. */
    private static void medium(MoveKit kit) {
        double rate = number(kit, "medium-rate", 0.45);
        double range = number(kit, "medium-range", 9.0);
        double speed = Math.max(0.2, number(kit, "medium-speed", 0.9));
        double pull = number(kit, "medium-pull", 0.45);
        Vector heading = flat(kit.facing());
        Location start = kit.player.getLocation().add(0.0, 1.1, 0.0).add(heading.clone().multiply(0.8));
        int out = Math.max(2, (int) Math.ceil(range / speed));
        Set<UUID> outward = new HashSet<>();
        Set<UUID> homeward = new HashSet<>();
        Location[] position = {start};
        boolean[] back = {false};
        kit.sound(start, Sfx.REAPER_THROW, 0.9f, 1.0f);
        kit.every(out + 40, age -> {
            Location next;
            if (!back[0] && age >= out) back[0] = true;
            if (!back[0]) {
                next = position[0].clone().add(heading.clone().multiply(speed));
                if (!next.getBlock().isPassable()) {
                    back[0] = true;
                    next = position[0];
                }
            } else {
                Location chest = kit.player.getLocation().add(0.0, 1.1, 0.0);
                if (chest.getWorld() != position[0].getWorld()) return true;
                Vector way = chest.toVector().subtract(position[0].toVector());
                double length = way.length();
                if (length <= speed * 1.3) {
                    kit.sound(chest, Sfx.REAPER_THROW, 0.5f, 0.7f);
                    return true;
                }
                next = position[0].clone().add(way.multiply(speed * 1.15 / length));
            }
            drawScythe(kit, next, age);
            for (LivingEntity victim : kit.around(next, 1.7)) {
                if (Math.abs(victim.getLocation().getY() + victim.getHeight() * 0.5 - next.getY()) > 2.0) continue;
                if (!(back[0] ? homeward : outward).add(victim.getUniqueId())) continue;
                if (!kit.hit(victim, rate)) continue;
                marked(kit, victim, back[0] ? next.toVector().subtract(position[0].toVector()) : heading);
                if (back[0]) kit.pull(victim, kit.player.getLocation(), pull);
            }
            if (age % 4 == 0) kit.sound(next, Sfx.REAPER_THROW, 0.35f, 1.3f);
            position[0] = next;
            return false;
        });
    }

    /** The thrown scythe at {@code at}, turning flat: its shaft, and the blade curving from its end. */
    private static void drawScythe(MoveKit kit, Location at, int age) {
        Fx fx = kit.fx(at);
        if (!fx.active()) return;
        double spin = age * 1.1;
        double sx = Math.cos(spin);
        double sz = Math.sin(spin);
        fx.line(at.clone().add(-sx * 0.9, 0.0, -sz * 0.9), at.clone().add(sx * 0.9, 0.0, sz * 0.9), SOUL, 0.15, 0.35, 0.0, 0.7, 0.3, null, 1);
        for (int index = 0; index < 12; index++) {
            double t = index / 11.0;
            double angle = spin + t * Math.toRadians(150.0);
            double radius = 0.9 + 0.35 * Math.sin(t * Math.PI);
            fx.dust(at.getX() + Math.cos(angle) * radius, at.getY() + 0.05 * Math.sin(t * Math.PI), at.getZ() + Math.sin(angle) * radius, SOUL, 0.5 + 0.5 * t, 1.1);
        }
        if (age % 2 == 0) kit.particle(Particle.SOUL_FIRE_FLAME, at, 1, 0.2, 0.0);
    }

    /**
     * The re-activation of 中: the reaper goes through as a wraith (nothing hurts them) to the enemy with the most marks within
     * {@code recast-range} (else the one aimed at or nearest ahead, else straight ahead), a little past it; the enemies passed are cut and
     * marked, and a cross cut where it ends reaps everyone round it.
     */
    private static void reapThrough(MoveKit kit) {
        Player player = kit.player;
        double range = number(kit, "recast-range", 12.0);
        LivingEntity target = mostMarked(kit, range);
        Location feet = player.getLocation();
        Vector heading;
        double length;
        if (target != null) {
            Vector to = target.getLocation().toVector().subtract(feet.toVector()).setY(0.0);
            heading = to.lengthSquared() < 1.0E-6 ? flat(kit.facing()) : to.clone().normalize();
            length = Math.min(range, to.length() + 1.5);
        } else {
            heading = flat(kit.facing());
            length = number(kit, "recast-length", 6.0);
        }
        double power = Math.max(0.3, number(kit, "recast-power", 1.4));
        int ticks = Math.max(2, Math.min(12, (int) Math.ceil(length / power)));
        double rate = number(kit, "recast-rate", 0.65);
        double bonus = number(kit, "recast-mark-rate", 0.3);
        double passRate = number(kit, "recast-pass-rate", 0.2);
        Location from = feet.clone().add(0.0, 1.0, 0.0);
        List<LivingEntity> passed = kit.corridor(from, heading, length, 1.4);
        kit.invulnerable(ticks + 4);
        kit.sound(feet, Sfx.REAPER_PHASE, 0.9f, 1.0f);
        kit.line(from, from.clone().add(heading.clone().multiply(length)), 0.1, 0.9, 1.2);
        wraith(kit, ticks);
        kit.dash(heading, power, ticks, end -> {
            cut(kit, passed, passRate, heading);
            kit.slash("line", 3.2, 120.0, 45.0, 0.0, 1.2, 0.3, 0.0, 1.35, 0.14, 0.0);
            kit.slash("line", 3.2, 120.0, 135.0, 180.0, 1.2, 0.3, 0.0, 1.35, 0.14, 0.03);
            kit.burst(end.clone().add(0.0, 1.0, 0.0), 1.2);
            kit.sound(end, Sfx.REAPER_HEAVY, 1.0f, 0.9f);
            double[] budget = budget(kit);
            int reaped = 0;
            for (LivingEntity victim : kit.around(end, 3.0)) reaped += reap(kit, victim, rate, bonus, budget);
            afterReap(kit, reaped);
        });
    }

    /** The enemy within {@code range} with the most of the reaper's marks (the nearer of two alike), else the one aimed at or nearest ahead. */
    private static LivingEntity mostMarked(MoveKit kit, double range) {
        UUID owner = kit.player.getUniqueId();
        long now = System.currentTimeMillis();
        Location feet = kit.player.getLocation();
        LivingEntity best = null;
        int bestMarks = 0;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity victim : kit.around(feet, range)) {
            int marks = ReaperMarks.count(owner, victim.getUniqueId(), now);
            if (marks == 0) continue;
            double distance = victim.getLocation().distanceSquared(feet);
            if (marks > bestMarks || (marks == bestMarks && distance < bestDistance)) {
                best = victim;
                bestMarks = marks;
                bestDistance = distance;
            }
        }
        return best != null ? best : kit.nearest(range, 60.0);
    }

    // ---- 大 ----------------------------------------------------------------------------------------------------------------

    private static void large(MoveKit kit) {
        Player player = kit.player;
        double radius = number(kit, "large-radius", 6.0);
        int gather = (int) Math.round(number(kit, "large-gather-seconds", 1.0) * 20.0);
        double sweepRate = number(kit, "large-sweep-rate", 0.55);
        double reapRate = number(kit, "large-mark-rate", 0.35);
        double pull = number(kit, "large-pull", 0.06);
        Location feet = player.getLocation();
        kit.sound(feet, Sfx.REAPER_GATE, 1.0f, 1.0f);
        kit.guard(number(kit, "large-guard", 0.25), gather + 30);
        // the gate opens round the reaper: the enemies in it are marked, slowed and drawn in
        for (LivingEntity victim : kit.around(feet, radius)) {
            mark(kit, victim, 1);
            kit.slow(victim, number(kit, "large-slow", 40.0), 3.0);
        }
        kit.every(gather + 22, age -> {
            Location at = player.getLocation();
            if (age % 2 == 0) gate(kit, at, radius, age);
            if (age < gather + 10) {
                for (LivingEntity victim : kit.around(at, radius)) kit.drift(victim, at, pull);
            }
            return false;
        });
        // the great scythe goes round twice
        for (int index = 0; index < 2; index++) {
            int which = index;
            kit.later(gather + which * 10L, () -> grandSweep(kit, radius, sweepRate, which));
        }
        // the harvest: every mark round the reaper is reaped at once
        kit.later(gather + 20L, () -> {
            Location at = player.getLocation();
            kit.shockwave(at, 1.0, radius + 0.5, 6, 1.3);
            kit.particle(Particle.SCULK_SOUL, at.clone().add(0.0, 1.0, 0.0), 12, radius * 0.4, 0.04);
            double[] budget = budget(kit);
            int reaped = 0;
            long now = System.currentTimeMillis();
            for (LivingEntity victim : kit.around(at, radius + 1.0)) {
                if (ReaperMarks.count(player.getUniqueId(), victim.getUniqueId(), now) > 0) reaped += reap(kit, victim, 0.0, reapRate, budget);
            }
            kit.sound(at, Sfx.REAPER_REAP, 1.0f, 0.8f);
            if (reaped > 0) kit.later(8L, () -> kit.sound(player.getLocation(), Sfx.REAPER_SOUL, 0.8f, 0.9f));
        });
    }

    /** The gate of souls round {@code at}: its ring, six runes turning inside it and souls rising. */
    private static void gate(MoveKit kit, Location at, double radius, int age) {
        Fx fx = kit.fx(at);
        if (!fx.active()) return;
        fx.flatRing(at.clone().add(0.0, 0.1, 0.0), radius, 56, SOUL, 0.55, 0.3, 1.0);
        double turn = age * 0.08;
        for (int index = 0; index < 6; index++) {
            double angle = turn + index * Math.PI / 3.0;
            double x = at.getX() + Math.cos(angle) * radius * 0.65;
            double z = at.getZ() + Math.sin(angle) * radius * 0.65;
            fx.dust(x, at.getY() + 0.15, z, SOUL, 0.9, 1.3);
            fx.dust(x, at.getY() + 0.45, z, SOUL, 0.7, 0.8);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2.0);
        double r = radius * Math.sqrt(random.nextDouble());
        kit.particle(Particle.SOUL, at.clone().add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r), 1, 0.1, 0.02);
    }

    /** One round of the great scythe: the whole circle is cut (and marked), its blade drawn going round over 6 ticks. */
    private static void grandSweep(MoveKit kit, double radius, double rate, int which) {
        Location at = kit.player.getLocation();
        kit.slash("arc", radius * 0.75, 360.0, 85.0, which == 0 ? 0.0 : 180.0, 0.0, 0.6, 0.0, 2.0, 0.22, 0.0);
        kit.sound(at, Sfx.REAPER_GRAND, 1.0f, which == 0 ? 1.0f : 0.9f);
        double start = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        double turn = which == 0 ? 1.0 : -1.0;
        kit.every(6, age -> {
            Location center = kit.player.getLocation().add(0.0, 1.0, 0.0);
            Fx fx = kit.fx(center);
            if (!fx.active()) return false;
            for (int step = 0; step < 3; step++) {
                double angle = start + turn * (age * 3 + step) * (Math.PI * 2.0 / 18.0);
                for (int index = 0; index <= 8; index++) {
                    double t = index / 8.0;
                    double r = 1.0 + (radius - 1.0) * t;
                    // (the blade bends back toward its tip)
                    double bend = angle - turn * 0.5 * t * t;
                    fx.dust(center.getX() + Math.cos(bend) * r, center.getY() + 0.2 * Math.sin(t * Math.PI), center.getZ() + Math.sin(bend) * r, SOUL, 0.3 + 0.7 * t, 1.2);
                }
            }
            return false;
        });
        for (LivingEntity victim : kit.around(at, radius)) {
            if (!kit.hit(victim, rate)) continue;
            Vector off = victim.getLocation().toVector().subtract(at.toVector()).setY(0.0);
            marked(kit, victim, new Vector(-off.getZ() * turn, 0.0, off.getX() * turn));
        }
    }

    // ---- util ---------------------------------------------------------------------------------------------------------------

    /** Through as a wraith: ahead (back with shift held), nothing hurts the reaper on the way, the enemies passed are cut lightly and marked. */
    private static void util(MoveKit kit) {
        Player player = kit.player;
        Vector heading = flat(kit.facing()).multiply(player.isSneaking() ? -1.0 : 1.0);
        double power = number(kit, "util-power", 1.0);
        int ticks = Math.max(1, (int) Math.round(number(kit, "util-ticks", 5.0)));
        double rate = number(kit, "util-rate", 0.2);
        Location feet = player.getLocation();
        List<LivingEntity> passed = kit.corridor(feet.clone().add(0.0, 1.0, 0.0), heading, power * ticks, 1.3);
        kit.invulnerable(ticks + 3);
        kit.sound(feet, Sfx.REAPER_PHASE, 0.8f, player.isSneaking() ? 1.15f : 1.0f);
        kit.fx(feet).flatRing(feet.clone().add(0.0, 0.1, 0.0), 1.1, 20, SOUL, 0.6, 0.2, 1.0);
        wraith(kit, ticks);
        kit.dash(heading, power, ticks, end -> {
            cut(kit, passed, rate, heading);
            kit.particle(Particle.SOUL, end.clone().add(0.0, 1.0, 0.0), 4, 0.4, 0.02);
        });
    }

    /** A meteor of the mage's ULT struck {@code victim} (t168): with the scythe in hand, it cuts a mark of death into it. */
    static void meteorMark(AttackService attack, Player player, RpgItemDefinition weapon, LivingEntity victim) {
        if (weapon == null || weapon.attackType() != AttackType.MELEE || !attack.hasClassCombo(player, weapon) || !victim.isValid() || victim.isDead()) return;
        MoveKit kit = attack.kit(player, weapon);
        look(kit);
        mark(kit, victim, 1);
    }

    /** A player leaves: what is kept of them goes. */
    public static void forget(UUID player) {
        ReaperMarks.forget(player);
        DRAWN.remove(player);
    }
}
