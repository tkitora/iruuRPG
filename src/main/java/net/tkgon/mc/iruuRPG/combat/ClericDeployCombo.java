package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The cleric's combo with a deployed weapon (a lantern of sanctuary): the sanctum (t168: little moving, the blessings of standing in it; mostly
 * buffs, with a little damage). Every attack sets (or keeps) the sanctum round the placement ({@code sanctum-radius} 6 blocks; as long as the
 * placement lasts). While the cleric stands in it, its <b>serenity</b> grows by one a second ({@code serenity-max} 5; it is lost at once when they
 * step out), and every second the players in it get {@code serenity-might} (4%) more damage and {@code serenity-protection} (3%) less damage taken
 * for each, and a little life ({@code sanctum-heal} 0.3% + {@code serenity-heal} 0.2% for each); the enemies in it are struck lightly
 * ({@code sanctum-rate} 12%, 10% more for each).
 *
 * <p>Normal: 1 two lights from the lantern at the nearest enemies in the sanctum (50% x2), 2 the bell of blessing (the players in it deal
 * {@code bless-might} 12% more for 5 s; 30% on the enemies in it), 3 the veil of protection (the players in it take {@code veil-protection} 15%
 * less for 5 s; the enemies in it are pushed out a little, 30%), 4 a rain of mending light (the players in it are healed {@code rain-heal} 3%;
 * 25% on the enemies), 5 judgment: a pillar of light on each enemy in it (up to 5; 80% each), and the serenity grows by two.
 *
 * <p>The heavy attacks below are what they were before t168 (a deployed weapon has no skills).
 */
public final class ClericDeployCombo extends BaseCombo {

    static final String PLAN = "cleric_deploy";

    public ClericDeployCombo() {
        super("healer", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.5, 0.3, 0.3, 0.25, 0.8}, new int[]{2, 1, 1, 1, 1},
                new double[]{0.15, 0.2, 1.0, 0.07, 0.2, 0.15}, new int[]{160, 0, 0, 0, 0, 0}, 40));
    }

    /**
     * The lantern of the normal attacks: one at a time (the key is the same, so the next attack replaces it), standing at {@code at} for as long as the placement does. Every
     * {@code every} ticks it sends {@code rings} shockwaves of light out to {@code radius}, three ticks apart; each hurts everything in the radius for {@code rate / rings} and,
     * if the cleric stands in it, heals {@code healPercent / rings} of the maximum health.
     */
    private void lantern(MoveKit kit, Location at, double radius, double rate, int every, double healPercent, int rings) {
        ComboParts.appear(kit, at);
        kit.later(2L, () -> {
            kit.sound(at, Sound.BLOCK_BELL_USE, 0.8f, 1.5f);
            kit.zone("cleric_lantern", at, kit.linger(50), 2, zone -> {
                boolean pulse = zone.age() % every == 0;
                ComboParts.standLantern(kit, at, 1.1, pulse ? 1.0 : 0.0);
                if (zone.age() % 6 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(24, radius * 8), kit.palette(), 0.25, 0.1, 0.8);
                if (!pulse) return;
                for (int ring = 0; ring < rings; ring++) {
                    kit.later(ring * 3L, () -> {
                        kit.shockwave(at, radius * 0.25, radius, 4, 1.0);
                        kit.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
                        kit.hitAll(kit.around(at, radius), rate / rings);
                        if (kit.player.getLocation().distanceSquared(at) <= (radius + 1.0) * (radius + 1.0)) kit.healCapped(healPercent / rings);
                    });
                }
            }, zone -> kit.shockwave(at, radius * 0.5, radius, 3, 0.8));
        });
    }

    /** The serenity of each cleric (the seconds they stood in their sanctum, at most {@code serenity-max}). */
    private static final java.util.Map<java.util.UUID, Integer> SERENITY = new java.util.concurrent.ConcurrentHashMap<>();

    static int serenity(java.util.UUID player) {
        return SERENITY.getOrDefault(player, 0);
    }

    /** Where each cleric's sanctum is (a sanctum set somewhere else starts its serenity anew). */
    private static final java.util.Map<java.util.UUID, Location> CENTERS = new java.util.concurrent.ConcurrentHashMap<>();

    static void forget(java.util.UUID player) {
        SERENITY.remove(player);
        CENTERS.remove(player);
    }

    /** The serenity full at once (the sanctuary of the ULT), when the cleric has a sanctum. */
    static void fillSerenity(java.util.UUID player, int max) {
        if (CENTERS.containsKey(player)) SERENITY.put(player, max);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(ClericSkills.HOLY);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location center = kit.ground(kit.origin());
        double radius = kit.number(PLAN, "sanctum-radius", 6.0);
        sanctum(kit, center, radius);
        java.util.List<org.bukkit.entity.Player> inside = playersIn(center, radius);
        java.util.UUID id = kit.player.getUniqueId();
        switch (step.stage()) {
            case 1 -> {
                // lights from the lantern at the nearest enemies in the sanctum
                Location top = center.clone().add(0.0, 1.6, 0.0);
                java.util.List<LivingEntity> enemies = kit.around(center, radius);
                enemies.sort(java.util.Comparator.comparingDouble(victim -> victim.getLocation().distanceSquared(center)));
                kit.sound(top, Sfx.STORM_CAST, 0.6f, 1.5f);
                for (int index = 0; index < Math.min(hits, enemies.size()); index++) {
                    LivingEntity victim = enemies.get(index);
                    kit.later(index * 3L, () -> {
                        if (!victim.isValid() || victim.isDead()) return;
                        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                        kit.line(top, chest, 0.3, 1.0, 1.2);
                        kit.burst(chest, 0.6);
                        kit.hit(victim, rate);
                    });
                }
            }
            case 2 -> {
                // the bell of blessing: the players in the sanctum deal more for a while
                kit.sound(center, Sfx.HOLY_BLESS, 1.0f, 1.2f);
                kit.shockwave(center, 1.0, radius, 6, 1.1);
                for (org.bukkit.entity.Player ally : inside) buff(kit, ally, StatusEffectType.MIGHT, kit.number(PLAN, "bless-might", 12.0), 5000L);
                kit.hitAll(kit.around(center, radius), rate);
            }
            case 3 -> {
                // the veil of protection: the players in it take less for a while; the enemies are pushed out a little
                kit.sound(center, Sfx.HOLY_RAISE, 1.0f, 1.1f);
                for (double y = 0.4; y <= 2.8; y += 0.6) kit.fx(center).flatRing(center.clone().add(0.0, y, 0.0), radius, 50, ClericSkills.HOLY, 0.3 + 0.2 * y, 0.2, 0.9);
                for (org.bukkit.entity.Player ally : inside) buff(kit, ally, StatusEffectType.PROTECTION, kit.number(PLAN, "veil-protection", 15.0), 5000L);
                for (LivingEntity victim : kit.around(center, radius)) {
                    if (kit.hit(victim, rate)) kit.push(victim, center.toVector(), 0.3, 0.05);
                }
            }
            case 4 -> {
                // a rain of mending light over the sanctum
                kit.sound(center, Sfx.HOLY_MEND, 0.9f, 1.1f);
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                for (int drop = 0; drop < 14; drop++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double r = radius * Math.sqrt(random.nextDouble());
                    Location at = center.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
                    kit.later(drop / 2, () -> kit.fx(at).line(at.clone().add(0.0, 3.5, 0.0), at.clone().add(0.0, 0.2, 0.0), ClericSkills.HOLY, 0.9, 0.4, 0.0, 0.8, 0.3, null, 1));
                }
                kit.healAllies(center, radius, kit.number(PLAN, "rain-heal", 3.0));
                kit.hitAll(kit.around(center, radius), rate);
            }
            default -> {
                // judgment: a pillar of light on each enemy in the sanctum, and the serenity grows by two
                kit.sound(center, Sfx.HOLY_HAMMER, 0.9f, 1.3f);
                java.util.List<LivingEntity> enemies = kit.around(center, radius);
                for (int index = 0; index < Math.min(5, enemies.size()); index++) {
                    LivingEntity victim = enemies.get(index);
                    kit.later(index * 2L, () -> {
                        if (!victim.isValid() || victim.isDead()) return;
                        ClericSkills.lightPillar(kit, victim.getLocation(), 0.7, 3.0);
                        kit.hit(victim, rate);
                    });
                }
                if (kit.player.getLocation().distanceSquared(center) <= radius * radius) {
                    int max = (int) Math.round(kit.number(PLAN, "serenity-max", 5.0));
                    SERENITY.put(id, Math.min(max, serenity(id) + 2));
                }
            }
        }
    }

    /** The players within {@code radius} of {@code center}. */
    private static java.util.List<org.bukkit.entity.Player> playersIn(Location center, double radius) {
        java.util.List<org.bukkit.entity.Player> inside = new java.util.ArrayList<>();
        for (org.bukkit.entity.Player player : center.getWorld().getPlayers()) {
            if (!player.isDead() && player.getLocation().distanceSquared(center) <= radius * radius) inside.add(player);
        }
        return inside;
    }

    /** A blessing on {@code ally} from the cleric (a ring rising round them). */
    private static void buff(MoveKit kit, org.bukkit.entity.Player ally, StatusEffectType type, double percent, long millis) {
        kit.buff(ally, type, percent, millis);
        Location feet = ally.getLocation();
        for (int step = 0; step < 4; step++) {
            int which = step;
            kit.later(step * 2L, () -> kit.fx(feet).flatRing(feet.clone().add(0.0, 0.2 + 0.4 * which, 0.0), 0.7, 14, ClericSkills.HOLY, 0.5 + 0.12 * which, 0.2, 0.9));
        }
    }

    /**
     * The sanctum round the placement (one: a new attack keeps it, its serenity goes on): its ring and lantern; every second the serenity of the
     * cleric in it grows (or is lost when they are out), the players in it are blessed and mended by it, and the enemies in it are struck lightly.
     */
    private static void sanctum(MoveKit kit, Location center, double radius) {
        java.util.UUID id = kit.player.getUniqueId();
        int max = (int) Math.round(kit.number(PLAN, "serenity-max", 5.0));
        double might = kit.number(PLAN, "serenity-might", 4.0);
        double protection = kit.number(PLAN, "serenity-protection", 3.0);
        double heal = kit.number(PLAN, "sanctum-heal", 0.3);
        double healEach = kit.number(PLAN, "serenity-heal", 0.2);
        double rate = kit.number(PLAN, "sanctum-rate", 0.12);
        Location before = CENTERS.put(id, center.clone());
        if (before == null || before.getWorld() != center.getWorld() || before.distanceSquared(center) > 0.25) SERENITY.remove(id);
        kit.zone("cleric_sanctum", center, kit.linger(60), 2, zone -> {
            int age = zone.age();
            int serenity = serenity(id);
            ComboParts.standLantern(kit, center, 1.1, age % 20 == 0 ? 1.0 : 0.0);
            if (age % 6 == 0) {
                Fx fx = kit.fx(center);
                fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(30, radius * 9), ClericSkills.HOLY, 0.3 + 0.1 * serenity, 0.15, 0.9);
                for (int ring = 0; ring < serenity; ring++) fx.flatRing(center.clone().add(0.0, 2.0 + 0.3 * ring, 0.0), 0.5 - 0.06 * ring, 12, ClericSkills.HOLY, 0.9, 0.0, 0.8);
            }
            if (age % 20 != 0) return;
            boolean in = kit.player.getLocation().getWorld() == center.getWorld() && kit.player.getLocation().distanceSquared(center) <= radius * radius;
            serenity = in ? Math.min(max, serenity + 1) : 0;
            SERENITY.put(id, serenity);
            if (in && serenity == max && age % 100 == 0) kit.sound(center, Sfx.HOLY_FULL, 0.4f, 1.4f);
            for (org.bukkit.entity.Player ally : playersIn(center, radius)) {
                if (serenity > 0) {
                    kit.buff(ally, StatusEffectType.MIGHT, might * serenity, 1500L);
                    kit.buff(ally, StatusEffectType.PROTECTION, protection * serenity, 1500L);
                }
            }
            kit.healAllies(center, radius, heal + healEach * serenity);
            kit.hitAll(kit.around(center, radius), rate * (1.0 + 0.1 * serenity));
        }, null);
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.45));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
                ComboParts.sigil(kit, "cleric_deploy_sanctuary", at, 3.5, rate, 50, 10, ignored -> kit.healCapped(1.0));
            }
            case 1 -> {
                // four lanterns set down one after the other running ahead
                Vector facing = kit.originFacing();
                kit.sound(feet, Sfx.WIND, 0.9f, 0.8f);
                for (int index = 1; index <= 4; index++) {
                    Location at = kit.ground(feet.clone().add(facing.clone().multiply(2.0 * index)));
                    String key = "cleric_trail_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, at, 44, 14, 2.0, rate, 0.0, 0.3, null));
                }
            }
            case 2 -> ComboParts.swellBurst(kit, target.clone().add(0.0, 1.5, 0.0), 4.5, rate, 1.0, 40);
            case 3 -> {
                // four lanterns round the aim that shoot light at the nearest enemy
                for (int index = 0; index < 4; index++) {
                    double angle = Math.PI * 0.5 * index + Math.PI * 0.25;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0));
                    String key = "cleric_guard_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 10, 7.0, rate, null));
                }
            }
            case 4 -> {
                kit.sound(target, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
                ComboParts.sigil(kit, "cleric_deploy_great", target, 6.0, rate, 50, 10, ignored -> {
                    if (kit.player.getLocation().distanceSquared(target) <= 36.0) kit.healCapped(1.2);
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a lighthouse of prayer at the aim for 3 s: every 10 ticks it hurts the enemies round it and heals the allies by it 2%. */
    private void sixth(MoveKit kit, double rate) {
        Location target = ComboParts.target(kit);
        ComboParts.place(kit, "healer_lighthouse", target, 60, 10, 3.6, rate, 0.0, 2.0, here -> kit.pillar(here, 0.5, 3.0));
    }
}
