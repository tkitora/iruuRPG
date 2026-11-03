package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.function.Consumer;

/**
 * The assassin with a ranged weapon of its combo: the <b>shadow knives</b> (t198: knives floating behind the assassin, sent at the enemies in
 * numbers, as the warrior's floating greatswords of t168 were; many quick hits, sharp sounds; the shadow bow of t168 is gone). Six knives hover in a
 * fan behind the assassin's back while they fight ({@link #hover}); every attack sends knives from it, and the shadow they leave confuses and bleeds.
 * Drawn in the weapon's colors, the shadow as a touch. Every number is under {@code combat.combo.assassin_range} (all provisional), the cooldowns
 * too.
 * <ul>
 *   <li>小 knife flurry (3 charges): three knives in a breath at the enemy aimed at; they bleed.</li>
 *   <li>中 shadow stitch: a knife into the enemy's shadow: it cannot move for a moment and is confused; the knife stands in the ground by it.</li>
 *   <li>大 thousand blades: twelve knives ring the aim and cut what is inside; its re-activation (or the end of the ring) plunges them all into the
 *       middle, one after another.</li>
 *   <li>util shadow leap: a leap back; three knives stay where the assassin stood and fly at the first enemies that come near.</li>
 * </ul>
 * The ULT (SATUJIN) with a ranged weapon or the lantern is the shadow burial ({@link #burial}).
 */
public final class AssassinKnives {

    static final String PLAN = AssassinRangeCombo.PLAN;
    /** The shadow: black, violet, a pale lilac (a touch on the knives' hits; the veil of the lantern). */
    static final Gradient SHADE = Gradient.of(Color.fromRGB(14, 6, 26), Color.fromRGB(110, 45, 180), Color.fromRGB(225, 200, 255));
    /** How many knives hover in the fan. */
    static final int FAN = 6;
    /** A knife's size (an iron sword made small). */
    static final double SIZE = 0.6;

    private static AttackService attack;
    /** The fan behind each assassin and until when it shows (ms). */
    private static final Map<UUID, Fan> FANS = new HashMap<>();
    /** The ring of 大 of each assassin while it stands (for its re-activation). */
    private static final Map<UUID, Ring> RINGS = new HashMap<>();

    private AssassinKnives() {
    }

    /** Whether {@code weapon} is the assassin's ranged weapon of its combo. */
    static boolean own(AttackService attackService, Player player, RpgItemDefinition weapon) {
        return weapon != null && weapon.attackType() == AttackType.RANGE && attackService.hasClassCombo(player, weapon);
    }

    /** The assassin's skill {@code slot} with the knives. */
    static boolean use(AttackService attackService, Player player, RpgItemDefinition weapon, SkillSlots.Slot slot) {
        attack = attackService;
        MoveKit kit = attackService.skillKit(player, weapon);
        if (kit == null) return false;
        hover(kit);
        switch (slot) {
            case SMALL -> flurry(kit);
            case MEDIUM -> stitch(kit);
            case LARGE -> thousand(kit);
            case UTIL -> leap(kit);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The re-activation of {@code slot} with the knives: 大 plunges the ring; false when there is none. */
    static boolean recast(AttackService attackService, Player player, RpgItemDefinition weapon, SkillSlots.Slot slot) {
        attack = attackService;
        if (slot != SkillSlots.Slot.LARGE) return false;
        Ring ring = RINGS.remove(player.getUniqueId());
        if (ring == null || ring.done) return false;
        MoveKit kit = attackService.skillKit(player, weapon);
        if (kit == null) return false;
        plunge(kit, ring);
        return true;
    }

    /**
     * The charges and cooldowns of the knives' skills (t198: spread by what each skill is): 小 3 charges, 中 a cooldown, 大 a re-activation for as
     * long as its ring stands, util a cooldown; with another kind of weapon none.
     */
    static SkillSlots.Mechanics mechanics(ConfigurationSection config, SkillSlots.Slot slot) {
        String base = "combat.combo." + PLAN + ".";
        return switch (slot) {
            case SMALL -> new SkillSlots.Mechanics((int) Math.round(config.getDouble(base + "small-charges", 3.0)), 0.0,
                    config.getDouble(base + "small-charge-seconds", 2.5), config.getDouble(base + "small-empty-seconds", 7.0));
            case MEDIUM -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "medium-seconds", 10.0));
            case LARGE -> new SkillSlots.Mechanics(1, config.getDouble(base + "large-ring-ticks", 80.0) / 20.0, 0.0,
                    config.getDouble(base + "large-seconds", 22.0));
            case UTIL -> new SkillSlots.Mechanics(1, 0.0, 0.0, config.getDouble(base + "util-seconds", 6.0));
            default -> SkillSlots.Mechanics.NONE;
        };
    }

    // ---- the fan -------------------------------------------------------------------------------------------------------------------

    /** The knives hovering behind an assassin. */
    private static final class Fan {
        final ItemDisplay[] knives = new ItemDisplay[FAN];
        long until;

        void clear() {
            for (ItemDisplay knife : knives) WarriorSwords.remove(knife);
        }
    }

    /**
     * The assassin fights with the knives: six hover in a fan behind their back, points out, for {@code hover-seconds} (6) after the last time this is
     * called (a slow sway; they follow the assassin smoothly).
     */
    static void hover(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        long until = System.currentTimeMillis() + Math.round(kit.number(PLAN, "hover-seconds", 6.0) * 1000.0);
        Fan fan = FANS.get(id);
        if (fan != null) {
            fan.until = until;
            return;
        }
        Fan fresh = new Fan();
        fresh.until = until;
        FANS.put(id, fresh);
        for (int index = 0; index < FAN; index++) fresh.knives[index] = WarriorSwords.spawn(slot(player, index, 0), out(player, index), SIZE);
        kit.sound(player.getLocation(), Sfx.KNIFE_SUMMON, 0.7f, 1.0f);
        new SafeTask(kit.plugin()) {
            private int age;

            @Override
            protected void tick() {
                age += 2;
                if (!player.isOnline() || player.isDead() || System.currentTimeMillis() > fresh.until || FANS.get(id) != fresh) {
                    if (FANS.get(id) == fresh) FANS.remove(id);
                    for (ItemDisplay knife : fresh.knives) {
                        if (knife != null && knife.isValid()) knife.getWorld().spawnParticle(Particle.SMOKE_NORMAL, knife.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                    }
                    fresh.clear();
                    cancel();
                    return;
                }
                for (int index = 0; index < FAN; index++) {
                    ItemDisplay knife = fresh.knives[index];
                    if (knife == null || !knife.isValid() || knife.getWorld() != player.getWorld()) {
                        WarriorSwords.remove(knife);
                        fresh.knives[index] = WarriorSwords.spawn(slot(player, index, age), out(player, index), SIZE);
                        continue;
                    }
                    WarriorSwords.move(knife, slot(player, index, age), out(player, index), SIZE, 2);
                }
            }
        }.runTaskTimer(kit.plugin(), 2L, 2L);
    }

    /** The angle of knife {@code index} in the fan, from the upright (-70 to 70 degrees). */
    private static double angle(int index) {
        return Math.toRadians(-70.0 + 140.0 * index / (FAN - 1));
    }

    /** The way knife {@code index} points: out from the middle of the fan behind the back. */
    static Vector out(Player player, int index) {
        double angle = angle(index);
        return WarriorSwords.right(player).multiply(Math.sin(angle)).add(new Vector(0.0, Math.cos(angle), 0.0)).normalize();
    }

    /** Where knife {@code index} hovers: on the fan behind the assassin's back, swaying a little. */
    static Location slot(Player player, int index, int age) {
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        double sway = Math.sin(age * 0.15 + index) * 0.06;
        return player.getLocation().add(0.0, 1.3, 0.0).subtract(facing.multiply(0.5)).add(out(player, index).multiply(0.85 + sway));
    }

    /** Where knife {@code index} leaves from when it is sent (a little ahead of where it hovers). */
    static Location launch(Player player, int index) {
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        return slot(player, Math.floorMod(index, FAN), org.bukkit.Bukkit.getCurrentTick()).add(facing.normalize().multiply(0.6));
    }

    // ---- knives sent ----------------------------------------------------------------------------------------------------------------

    /** The enemy the assassin aims at: the one nearest the line of sight within {@code reach} (within 2 blocks of it), else null. */
    static LivingEntity aimed(MoveKit kit, double reach) {
        Vector look = kit.eye().getDirection().normalize();
        LivingEntity best = null;
        double bestOff = Double.MAX_VALUE;
        for (LivingEntity victim : kit.corridor(kit.eye(), look, reach, 2.0)) {
            Vector to = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0).toVector().subtract(kit.eye().toVector());
            double along = to.dot(look);
            double off = to.clone().subtract(look.clone().multiply(along)).length() + along * 0.02;
            if (off < bestOff) {
                bestOff = off;
                best = victim;
            }
        }
        return best;
    }

    /** Where knives go: the chest of {@code target}, else where the assassin looks (at most {@code reach} away). */
    static Location goal(MoveKit kit, LivingEntity target, double reach) {
        if (target != null && target.isValid() && !target.isDead()) return target.getLocation().add(0.0, target.getHeight() * 0.55, 0.0);
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        return way.length() > reach ? eye.clone().add(way.normalize().multiply(reach)) : aim;
    }

    /**
     * Knife {@code index} of the fan sent at {@code to} (and {@code past} blocks on): into the first enemy in its way (each given to {@code onHit}),
     * or through all of them ({@code pierce}).
     */
    static void send(MoveKit kit, int index, Location to, double past, boolean pierce, Consumer<LivingEntity> onHit) {
        Location from = launch(kit.player, index);
        Vector way = to.toVector().subtract(from.toVector());
        if (way.lengthSquared() < 1.0E-6) way = kit.axes()[0];
        Location end = to.clone().add(way.normalize().multiply(past));
        boolean[] spent = {false};
        kit.sound(from, Sfx.KNIFE_THROW, 0.7f, 1.0f + 0.05f * Math.floorMod(index, FAN));
        WarriorSwords.fly(kit, from, end, SIZE, kit.number(PLAN, "knife-speed", 2.2), 0.8, kit.palette(), victim -> {
            if (spent[0]) return;
            if (!pierce) spent[0] = true;
            onHit.accept(victim);
        }, null);
    }

    /** A knife's hit: {@code rate} on {@code victim}, a glint and the touch of shadow. True when it landed. */
    static boolean cut(MoveKit kit, LivingEntity victim, double rate) {
        if (!kit.hit(victim, rate)) return false;
        struck(kit, victim);
        return true;
    }

    /** A hit of the knives landed on {@code victim}: a puff of shadow and a sharp ring. */
    static void struck(MoveKit kit, LivingEntity victim) {
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        kit.fx(chest).sphereShell(chest, 0.3, 0.0, 8, SHADE, 0.8);
        kit.particle(Particle.CRIT, chest, 3, 0.15, 0.1);
        kit.sound(chest, Sfx.KNIFE_HIT, 0.5f, 1.0f);
    }

    /** {@code share} of the damage a hit just did to {@code victim} (its life {@code before}) as bleed. */
    static void bleedOf(MoveKit kit, LivingEntity victim, double before, double share) {
        double dealt = Math.max(0.0, before - victim.getHealth());
        if (dealt > 0.0) kit.bleed(victim, dealt * share);
    }

    // ---- 小 ----------------------------------------------------------------------------------------------------------------------

    /** Knife flurry (3 charges): {@code small-count} knives a tick apart at the enemy aimed at ({@code small-rate} each); they bleed. */
    private static void flurry(MoveKit kit) {
        double rate = kit.number(PLAN, "small-rate", 0.3);
        double bleed = kit.number(PLAN, "small-bleed", 0.4);
        int count = (int) Math.round(kit.number(PLAN, "small-count", 3.0));
        LivingEntity target = aimed(kit, 28.0);
        for (int index = 0; index < count; index++) {
            int which = index;
            kit.later(index, () -> send(kit, 1 + which, goal(kit, target, 28.0), 2.0, false, victim -> {
                double before = victim.getHealth();
                if (cut(kit, victim, rate)) bleedOf(kit, victim, before, bleed);
            }));
        }
    }

    // ---- 中 ----------------------------------------------------------------------------------------------------------------------

    /**
     * Shadow stitch: a knife into the shadow of the enemy aimed at ({@code medium-rate}): it cannot move for {@code medium-pin-seconds} and is
     * confused ({@code medium-confusion}); the knife stands in the ground by it meanwhile.
     */
    private static void stitch(MoveKit kit) {
        double rate = kit.number(PLAN, "medium-rate", 0.6);
        double seconds = kit.number(PLAN, "medium-pin-seconds", 1.5);
        double confusion = kit.number(PLAN, "medium-confusion", 30.0);
        LivingEntity target = aimed(kit, 28.0);
        send(kit, 2, goal(kit, target, 28.0), 1.0, false, victim -> {
            if (!cut(kit, victim, rate)) return;
            kit.slow(victim, 100.0, seconds);
            kit.confuse(victim, confusion);
            Location ground = kit.ground(victim.getLocation());
            kit.sound(ground, Sfx.GREATBOW_PIN, 0.6f, 1.7f);
            WarriorSwords.fall(kit, ground.clone().add(0.4, 0.0, 0.0), SIZE, 2.0, 2, (int) Math.round(seconds * 20.0), null);
            int life = (int) Math.round(seconds * 20.0);
            kit.every(life, age -> {
                if (!victim.isValid() || victim.isDead()) return true;
                if (age % 3 == 0) kit.fx(ground).flatRing(victim.getLocation().add(0.0, 0.1, 0.0), 0.8, 14, SHADE, 0.6, 0.1, 0.9);
                return false;
            });
        });
    }

    // ---- 大 ----------------------------------------------------------------------------------------------------------------------

    /** A ring of 大: where it stands, its knives, and whether they plunged. */
    private static final class Ring {
        final Location center;
        final double radius;
        final List<ItemDisplay> knives = new ArrayList<>();
        boolean done;

        Ring(Location center, double radius) {
            this.center = center;
            this.radius = radius;
        }
    }

    /**
     * Thousand blades: {@code large-count} knives ring the aim (at most {@code large-range} away, {@code large-radius}), points in, for
     * {@code large-ring-ticks}; every half second they cut the enemies inside ({@code large-tick-rate}). The re-activation, or the end of the
     * ring, plunges them into the middle one after another ({@link #plunge}).
     */
    private static void thousand(MoveKit kit) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        double radius = kit.number(PLAN, "large-radius", 3.5);
        int count = (int) Math.round(kit.number(PLAN, "large-count", 12.0));
        int life = (int) Math.round(kit.number(PLAN, "large-ring-ticks", 80.0));
        double tick = kit.number(PLAN, "large-tick-rate", 0.12);
        Location center = kit.ground(goal(kit, aimed(kit, kit.number(PLAN, "large-range", 20.0)), kit.number(PLAN, "large-range", 20.0)));
        Ring ring = new Ring(center, radius);
        Ring old = RINGS.put(id, ring);
        if (old != null && !old.done) plunge(kit, old);
        kit.sound(center, Sfx.KNIFE_SUMMON, 1.0f, 0.8f);
        for (int index = 0; index < count; index++) {
            double angle = index * Math.PI * 2.0 / count;
            int which = index;
            kit.later(index / 2, () -> {
                if (ring.done) return;
                Location at = center.clone().add(Math.cos(angle) * radius, 1.2, Math.sin(angle) * radius);
                Vector in = center.clone().add(0.0, 1.0, 0.0).toVector().subtract(at.toVector());
                Location from = launch(player, which);
                ItemDisplay knife = WarriorSwords.spawn(from, in, SIZE);
                ring.knives.add(knife);
                kit.later(1L, () -> WarriorSwords.move(knife, at, in, SIZE, 4));
            });
        }
        // (taken away even when the assassin has left)
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> {
            for (ItemDisplay knife : ring.knives) WarriorSwords.remove(knife);
        }, life + count + 60L);
        kit.every(life + 1, age -> {
            if (ring.done) return true;
            if (age >= life) {
                if (RINGS.get(id) == ring) RINGS.remove(id);
                plunge(kit, ring);
                return true;
            }
            if (age % 4 == 0) kit.fx(center).flatRing(center.clone().add(0.0, 0.12, 0.0), radius, 40, kit.palette(), 0.4, 0.15, 0.9);
            if (age > 8 && age % 10 == 0) {
                kit.sound(center, Sfx.KNIFE_THROW, 0.6f, 1.6f);
                for (LivingEntity victim : kit.around(center, radius)) {
                    if (!cut(kit, victim, tick)) continue;
                    // a knife darts across it and back to the ring
                    Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                    Vector across = new Vector(Math.random() - 0.5, 0.0, Math.random() - 0.5);
                    if (across.lengthSquared() < 1.0E-6) across = new Vector(1.0, 0.0, 0.0);
                    across.normalize().multiply(1.2);
                    kit.fx(chest).line(chest.clone().subtract(across), chest.clone().add(across), kit.palette(), 0.3, 1.0, 0.0, 1.0, 0.12, null, 1);
                }
            }
            return false;
        });
    }

    /** The ring's knives plunge into the middle one after another, each {@code large-plunge-rate} on the enemies within 2 blocks of it. */
    private static void plunge(MoveKit kit, Ring ring) {
        if (ring.done) return;
        ring.done = true;
        double rate = kit.number(PLAN, "large-plunge-rate", 0.15);
        Location middle = ring.center.clone().add(0.0, 1.0, 0.0);
        kit.sound(ring.center, Sfx.KNIFE_STORM, 1.0f, 1.0f);
        List<ItemDisplay> knives = new ArrayList<>(ring.knives);
        for (int index = 0; index < knives.size(); index++) {
            ItemDisplay knife = knives.get(index);
            kit.later(index, () -> {
                if (!knife.isValid()) return;
                Vector in = middle.toVector().subtract(knife.getLocation().toVector());
                WarriorSwords.move(knife, middle, in, SIZE, 2);
                kit.fx(knife.getLocation()).line(knife.getLocation(), middle, kit.palette(), 0.2, 1.0, 0.0, 0.9, 0.2, null, 1);
                kit.later(2L, () -> {
                    kit.sound(middle, Sfx.KNIFE_HIT, 0.5f, 1.2f);
                    kit.particle(Particle.CRIT, middle, 4, 0.3, 0.15);
                    for (LivingEntity victim : kit.around(ring.center, 2.0)) cut(kit, victim, rate);
                    WarriorSwords.vanish(kit, knife, in, 3);
                });
            });
        }
        kit.later(knives.size() + 3L, () -> {
            kit.shockwave(ring.center, 0.4, ring.radius, 4, 1.2);
            kit.fx(middle).sphereShell(middle, 1.0, 0.0, 22, kit.palette(), 1.1);
        });
    }

    // ---- util --------------------------------------------------------------------------------------------------------------------

    /**
     * Shadow leap: a leap back; {@code util-knives} knives stay hovering where the assassin stood for {@code util-seconds-left} and each flies at
     * the first enemy that comes within {@code util-radius} of the spot ({@code util-rate}, once each).
     */
    private static void leap(MoveKit kit) {
        Player player = kit.player;
        Location spot = kit.ground(player.getLocation()).add(0.0, 1.0, 0.0);
        int count = (int) Math.round(kit.number(PLAN, "util-knives", 3.0));
        double radius = kit.number(PLAN, "util-radius", 3.0);
        double rate = kit.number(PLAN, "util-rate", 0.4);
        int life = (int) Math.round(kit.number(PLAN, "util-seconds-left", 3.0) * 20.0);
        kit.hopBack(kit.number(PLAN, "util-back", 0.9), 0.4);
        kit.invulnerable(6);
        kit.sound(spot, Sfx.SHADOW_VANISH, 0.8f, 1.2f);
        kit.particle(Particle.SMOKE_LARGE, spot, 6, 0.4, 0.02);
        List<ItemDisplay> knives = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double angle = index * Math.PI * 2.0 / count;
            Location at = spot.clone().add(Math.cos(angle) * 0.6, 0.2 * index, Math.sin(angle) * 0.6);
            knives.add(WarriorSwords.spawn(at, new Vector(Math.cos(angle), 0.4, Math.sin(angle)), SIZE));
        }
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> {
            for (ItemDisplay knife : knives) WarriorSwords.remove(knife);
        }, life + 20L);
        Set<UUID> taken = new HashSet<>();
        kit.every(life + 1, age -> {
            if (age >= life || knives.isEmpty()) {
                for (ItemDisplay knife : knives) WarriorSwords.vanish(kit, knife, new Vector(0.0, 1.0, 0.0), 4);
                return true;
            }
            if (age % 4 == 0) kit.fx(spot).flatRing(kit.ground(spot).add(0.0, 0.1, 0.0), radius, 24, SHADE, 0.4, 0.1, 0.8);
            for (LivingEntity victim : kit.around(spot, radius)) {
                if (knives.isEmpty()) break;
                if (!taken.add(victim.getUniqueId())) continue;
                ItemDisplay knife = knives.remove(0);
                Location from = knife.getLocation();
                WarriorSwords.remove(knife);
                Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                kit.sound(from, Sfx.KNIFE_THROW, 0.7f, 1.3f);
                WarriorSwords.fly(kit, from, chest, SIZE, 2.0, 0.8, kit.palette(), hit -> {
                    if (hit.equals(victim)) cut(kit, hit, rate);
                }, null);
            }
            return false;
        });
    }

    // ---- the ULT with a ranged weapon or the lantern: the shadow burial (t168; knives instead of arrows since t198) -----------------------------

    /** The assassins in a shadow burial: {when it was cast (ms), until when (ms)}, and their weapon. */
    private static final Map<UUID, long[]> BURIALS = new HashMap<>();
    private static final Map<UUID, RpgItemDefinition> BURIAL_WEAPONS = new HashMap<>();

    /** Whether {@code player} is in a shadow burial now. */
    static boolean burying(UUID player) {
        long[] burial = BURIALS.get(player);
        return burial != null && burial[1] > System.currentTimeMillis();
    }

    /** Whether the burial can be ended by the key now ({@code recast-delay-seconds} after it was cast). */
    static boolean burialReady(Player player, Map<String, Double> values) {
        long[] burial = BURIALS.get(player.getUniqueId());
        return burial != null && burial[1] > System.currentTimeMillis()
                && System.currentTimeMillis() - burial[0] >= values.getOrDefault("recast-delay-seconds", 1.5) * 1000.0;
    }

    /**
     * The assassin's ULT with a ranged weapon or the lantern (SATUJIN is for a melee weapon): the <b>shadow burial</b>. The assassin sinks into the
     * shadows for {@code burial-seconds} (6): the mobs lose them, they deal {@code burial-might} (30%) more, and the enemies within
     * {@code burial-radius} (14) are confused. At the end (or with the key again, after {@code recast-delay-seconds}) knives fall on up to
     * {@code burial-count} (10) of the enemies near: {@code burial-rate} (1.2) and {@code burial-affliction-rate} (0.4) more for each kind of
     * affliction on one, double on one under {@code execute-percent} of its life.
     */
    static void burial(AttackService attackService, Player player, RpgItemDefinition weapon, Map<String, Double> values) {
        attack = attackService;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        int ticks = (int) Math.round(values.getOrDefault("burial-seconds", 6.0) * 20.0);
        long[] burial = {now, now + ticks * 50L};
        BURIALS.put(id, burial);
        BURIAL_WEAPONS.put(id, weapon);
        MoveKit kit = attackService.kit(player, weapon);
        Location spot = player.getLocation();
        double radius = values.getOrDefault("burial-radius", 14.0);
        AssassinSkills.hide(player, spot, ticks * 50L);
        kit.buff(player, StatusEffectType.MIGHT, values.getOrDefault("burial-might", 30.0), ticks * 50L);
        kit.sound(spot, Sfx.VEIL_NIGHTFALL, 1.0f, 0.8f);
        kit.sound(spot, Sfx.SHADOW_VANISH, 1.0f, 1.0f);
        kit.shockwave(spot, 0.8, radius, 8, 1.3);
        kit.particle(Particle.SQUID_INK, spot.clone().add(0.0, 1.0, 0.0), 30, 1.5, 0.05);
        for (LivingEntity victim : kit.around(spot, radius)) kit.confuse(victim, values.getOrDefault("burial-confusion", 30.0));
        kit.every(ticks + 1, age -> {
            if (BURIALS.get(id) != burial) return true;
            if (age >= ticks) {
                rain(attackService, player, values);
                return true;
            }
            if (age % 3 == 0) {
                Location at = player.getLocation();
                Fx fx = kit.fx(at);
                for (int strand = 0; strand < 3; strand++) {
                    double angle = age * 0.3 + strand * Math.PI * 2.0 / 3.0;
                    fx.dust(at.getX() + Math.cos(angle) * 0.7, at.getY() + 0.3 + (age % 20) * 0.08, at.getZ() + Math.sin(angle) * 0.7, SHADE, 0.2, 1.0);
                }
                if (age % 9 == 0) kit.particle(Particle.SMOKE_LARGE, at.clone().add(0.0, 0.8, 0.0), 2, 0.3, 0.01);
            }
            return false;
        });
    }

    /** The key again during the burial: it ends now, and the knives fall. True when it was used (also when it came too early). */
    static boolean burialRecast(AttackService attackService, Player player, Map<String, Double> values) {
        long[] burial = BURIALS.get(player.getUniqueId());
        if (burial == null || burial[1] <= System.currentTimeMillis()) return false;
        double delay = values.getOrDefault("recast-delay-seconds", 1.5);
        long elapsed = System.currentTimeMillis() - burial[0];
        if (elapsed < delay * 1000.0) {
            player.sendMessage("[iruuRPG] 影葬まで あと" + String.format(java.util.Locale.ROOT, "%.1f", delay - elapsed / 1000.0) + "秒");
            return true;
        }
        rain(attackService, player, values);
        return true;
    }

    /** The end of the burial: the assassin is seen again, and knives fall from above on the enemies near. */
    private static void rain(AttackService attackService, Player player, Map<String, Double> values) {
        UUID id = player.getUniqueId();
        BURIALS.remove(id);
        RpgItemDefinition weapon = BURIAL_WEAPONS.remove(id);
        if (weapon == null || !player.isOnline() || player.isDead()) return;
        AssassinSkills.reveal(player);
        attackService.beginAttack(player);
        MoveKit kit = attackService.kit(player, weapon);
        double radius = values.getOrDefault("burial-radius", 14.0);
        int count = (int) Math.round(values.getOrDefault("burial-count", 10.0));
        double rate = values.getOrDefault("burial-rate", 1.2);
        double perAffliction = values.getOrDefault("burial-affliction-rate", 0.4);
        double executeAt = values.getOrDefault("execute-percent", 25.0);
        Location feet = player.getLocation();
        List<LivingEntity> targets = kit.around(feet, radius);
        targets.sort(java.util.Comparator.comparingDouble(victim -> victim.getLocation().distanceSquared(feet)));
        kit.sound(feet, Sfx.KNIFE_STORM, 1.0f, 0.8f);
        for (int index = 0; index < Math.min(count, targets.size()); index++) {
            LivingEntity victim = targets.get(index);
            kit.later(2L + index * 2L, () -> {
                if (!victim.isValid() || victim.isDead()) return;
                Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                Location above = chest.clone().add(0.4, 10.0, 0.3);
                kit.sound(above, Sfx.KNIFE_THROW, 0.6f, 1.2f);
                WarriorSwords.fly(kit, above, chest.clone().subtract(0.0, 0.6, 0.0), SIZE * 1.4, 3.0, 0.9, kit.palette(), struck -> {
                    if (!struck.equals(victim)) return;
                    int afflictions = attackService.statusEffects().afflictions(victim);
                    double multiplier = rate + perAffliction * afflictions;
                    double life = maxHealth(victim);
                    if (life > 0.0 && victim.getHealth() / life * 100.0 <= executeAt) multiplier *= 2.0;
                    if (kit.hit(victim, multiplier)) {
                        Location at = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                        kit.fx(at).sphereShell(at, 0.7 + 0.15 * afflictions, 0.0, 20, SHADE, 1.2);
                        kit.sound(at, Sfx.KNIFE_HIT, 0.8f, 0.9f);
                    }
                }, null);
            });
        }
    }

    private static double maxHealth(LivingEntity victim) {
        org.bukkit.attribute.AttributeInstance attribute = victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? victim.getHealth() : attribute.getValue();
    }

    /** A player leaves (or changes class): what is kept of them goes. */
    public static void forget(UUID player) {
        Fan fan = FANS.remove(player);
        if (fan != null) fan.clear();
        Ring ring = RINGS.remove(player);
        if (ring != null) for (ItemDisplay knife : ring.knives) WarriorSwords.remove(knife);
        BURIALS.remove(player);
        BURIAL_WEAPONS.remove(player);
    }
}
