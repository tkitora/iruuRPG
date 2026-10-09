package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passive class effects that trigger when a player's attack lands
 * (milestone skills such as the gifted's bleed / corrosion / laceration).
 */
public final class ClassEffectService {

    public static final String GIFTED_BLEED = "gifted_bleed";
    public static final String GIFTED_DECAY = "gifted_decay";
    public static final String INSTINCT_UPGRADE = "instinct_release_upgrade";
    public static final String ICE_LANCE_UPGRADE = "ice_lance_upgrade";
    public static final String ASSASSIN_ORANGE = "assassin_orange_strike";
    public static final String ASSASSIN_BLAST = "assassin_blast";
    private static final double ASSASSIN_BLAST_DIVISOR = 40.0;
    public static final String HEALER_PULSE = "healer_pulse";
    public static final String HEALER_GUARD = "healer_guard";
    private static final double HEALER_PULSE_DIVISOR = 25.0;
    private static final double ELEMENT_SCALE = 800.0;

    private static final Particle.DustOptions WHITE_AURA = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.4f);
    private static final Particle.DustOptions ORANGE_HIT = new Particle.DustOptions(Color.fromRGB(255, 150, 30), 1.3f);
    private static final Particle.DustOptions GREEN_PULSE = new Particle.DustOptions(Color.fromRGB(90, 230, 120), 1.2f);
    private static final Particle.DustOptions SILVER_AURA = new Particle.DustOptions(Color.fromRGB(205, 220, 255), 1.0f);

    private final JavaPlugin plugin;
    private final ClassService classService;
    private final PlayerProfileManager profileManager;
    private final StatusEffectService statusEffectService;
    private final Map<UUID, Long> instinctUntilMillis = new HashMap<>();
    private AttackService attackService;
    private final Map<UUID, UUID> lastTargets = new HashMap<>();
    private final Map<UUID, Long> lastTargetTimes = new HashMap<>();

    public ClassEffectService(JavaPlugin plugin, ClassService classService, PlayerProfileManager profileManager, StatusEffectService statusEffectService) {
        this.plugin = plugin;
        this.classService = classService;
        this.profileManager = profileManager;
        this.statusEffectService = statusEffectService;
    }

    public void setAttackService(AttackService attackService) {
        this.attackService = attackService;
    }

    /** Called after every player attack hit (melee, ranged, deploy, area). */
    public void onDamageDealt(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage, boolean critical) {
        if (attacker == null || victim == null || victim.isDead() || !victim.isValid()) return;

        PlayerProfile profile = profileManager.getOrCreate(attacker);
        if (profile.classId().isBlank()) return;

        lastTargets.put(attacker.getUniqueId(), victim.getUniqueId());
        lastTargetTimes.put(attacker.getUniqueId(), System.currentTimeMillis());
        giftedOnHit(attacker, victim, profile);
        assassinOnHit(attacker, victim, weapon, damage, profile);
        mageOnHit(attacker, victim, damage, profile);
    }

    /** Mage upgrade: hits on a stopped enemy (slow 100%+) add corrosion and decay equal to the final damage. */
    private void mageOnHit(Player attacker, LivingEntity victim, double damage, PlayerProfile profile) {
        if (damage <= 0.0 || !classService.hasSkill(profile, ICE_LANCE_UPGRADE)) return;
        if (statusEffectService.slowOf(victim) < 100.0 - 1.0E-6) return;

        UUID sourceId = attacker.getUniqueId();
        statusEffectService.addCorrosion(victim, damage, sourceId);
        statusEffectService.addDecay(victim, damage, sourceId);
    }

    // ---- assassin -------------------------------------------------------------

    /**
     * Assassin: every hit adds orange damage of hit damage x (100% + level %); hits with an orange weapon
     * also add an explosion of hit damage x (target's confusion / 40).
     */
    private void assassinOnHit(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage, PlayerProfile profile) {
        if (attackService == null || damage <= 0.0) return;

        UUID sourceId = attacker.getUniqueId();
        if (classService.hasSkill(profile, ASSASSIN_ORANGE)) {
            double extra = damage * (1.0 + Math.max(1, profile.level()) / 100.0);
            if (attackService.applyDirectDamage(attacker, victim, Math.round(extra * 10.0) / 10.0)) {
                playOrangeHit(victim);
            }
        }
        if (victim.isDead() || !victim.isValid()) return;

        if (classService.hasSkill(profile, ASSASSIN_BLAST)
                && weapon != null && weapon.element() == net.tkgon.mc.iruuRPG.stat.Element.ORANGE) {
            double confusion = statusEffectService.confusionOf(victim);
            double blast = damage * confusion / ASSASSIN_BLAST_DIVISOR;
            if (blast > 0.0) {
                statusEffectService.addExplosion(victim, blast, sourceId);
            }
        }
    }

    private void playOrangeHit(LivingEntity victim) {
        World world = victim.getWorld();
        Location center = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        world.spawnParticle(Particle.REDSTONE, center, 10, 0.35, 0.45, 0.35, 0.0, ORANGE_HIT, true);
    }

    /** The enemy the player hit last, if still valid within {@code maxSeconds}. */
    public LivingEntity lastTarget(Player player, double maxSeconds) {
        UUID targetId = lastTargets.get(player.getUniqueId());
        Long time = lastTargetTimes.get(player.getUniqueId());
        if (targetId == null || time == null) return null;
        if (System.currentTimeMillis() - time > maxSeconds * 1000.0) return null;

        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(targetId);
        if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) return null;
        return living.getWorld().equals(player.getWorld()) ? living : null;
    }

    // ---- gifted ---------------------------------------------------------------

    /**
     * Gifted: every attack applies bleed = level x melee damage x (100% + melee damage %),
     * corrosion/decay = level x range damage x (100% + range damage %); during
     * the instinct release every attack also bursts a laceration of deploy damage x deploy damage %.
     */
    private void giftedOnHit(Player attacker, LivingEntity victim, PlayerProfile profile) {
        UUID sourceId = attacker.getUniqueId();
        StatSet stats = profile.finalStats();
        int level = Math.max(1, profile.level());

        if (classService.hasSkill(profile, GIFTED_BLEED)) {
            double melee = Math.max(0.0, stats.get(StatType.MELEE_DAMAGE));
            double percent = Math.max(0.0, stats.get(StatType.MELEE_DAMAGE_PERCENT));
            statusEffectService.addBleed(victim, scale("gifted.bleed-scale") * level * melee * (1.0 + percent / 100.0), sourceId);
        }
        if (classService.hasSkill(profile, GIFTED_DECAY)) {
            double range = Math.max(0.0, stats.get(StatType.RANGE_DAMAGE));
            double percent = Math.max(0.0, stats.get(StatType.RANGE_DAMAGE_PERCENT));
            double value = scale("gifted.decay-scale") * level * range * (1.0 + percent / 100.0);
            statusEffectService.addCorrosion(victim, value, sourceId);
            statusEffectService.addDecay(victim, value, sourceId);
        }
        if (instinctActive(sourceId) && victim.isValid() && !victim.isDead()) {
            double deploy = Math.max(0.0, stats.get(StatType.DEPLOY_DAMAGE));
            double percent = Math.max(0.0, stats.get(StatType.DEPLOY_DAMAGE_PERCENT));
            double burst = scale("gifted.laceration-scale") * deploy * percent / 100.0;
            if (classService.hasSkill(profile, INSTINCT_UPGRADE)) {
                burst *= 2.0;
            }
            if (burst > 0.0) {
                statusEffectService.dealStatusDamage(victim, StatusEffectType.LACERATION, burst, sourceId);
            }
        }
    }

    private double scale(String path) {
        return plugin.getConfig().getDouble("class-effects." + path, 1.0);
    }

    // ---- healer ---------------------------------------------------------------

    /** Healer: heals the caster casts get multiplied by (100% + the caster's protection %). */
    public double healMultiplier(Player caster) {
        if (caster == null) return 1.0;

        PlayerProfile profile = profileManager.getOrCreate(caster);
        if (!classService.hasSkill(profile, HEALER_GUARD)) return 1.0;

        return 1.0 + Math.max(0.0, profile.finalStats().get(StatType.PROTECTION)) / 100.0;
    }

    /**
     * Healer: when the healer is healed (item or class skills), the amount that actually healed x level / 25
     * hits every enemy within 7 blocks as green damage (green damage buffs apply).
     */
    public void onSelfHealed(Player player, double healed) {
        if (healed <= 0.0 || attackService == null) return;

        PlayerProfile profile = profileManager.getOrCreate(player);
        if (!classService.hasSkill(profile, HEALER_PULSE)) return;

        double radius = plugin.getConfig().getDouble("class-effects.healer.pulse-radius", 7.0);
        double damage = healed * Math.max(1, profile.level()) / HEALER_PULSE_DIVISOR * greenFactor(profile);
        if (damage <= 0.0) return;

        Location center = player.getLocation();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity instanceof Player) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand || DeployService.isDeployEntity(target)) continue;
            if (target.getLocation().distanceSquared(center) > radius * radius) continue;

            attackService.applyDirectDamage(player, target, Math.round(damage * 10.0) / 10.0);
        }
        playPulse(center, radius);
    }

    /** Same shape as the weapon formula's element multiplier, so green damage buffs raise the pulse. */
    private double greenFactor(PlayerProfile profile) {
        double damage = Math.max(0.0, profile.finalElementStats().damage(net.tkgon.mc.iruuRPG.stat.Element.GREEN));
        double percent = Math.max(0.0, profile.finalElementStats().damagePercent(net.tkgon.mc.iruuRPG.stat.Element.GREEN));
        return 1.0 + (1.0 + percent / 100.0) * damage / (damage + ELEMENT_SCALE);
    }

    private void playPulse(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return;

        int points = 36;
        for (int index = 0; index < points; index++) {
            double angle = 2.0 * Math.PI * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, GREEN_PULSE, true);
        }
        world.spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(0, 1.0, 0), 6, 0.6, 0.6, 0.6, 0.0);
    }

    // ---- instinct release (gifted active skill) --------------------------------

    public void activateInstinct(Player player, double seconds) {
        instinctUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + Math.round(seconds * 1000.0));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        playAura(player, seconds);
    }

    public boolean instinctActive(UUID playerId) {
        Long until = instinctUntilMillis.get(playerId);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            instinctUntilMillis.remove(playerId);
            return false;
        }
        return true;
    }

    /** A white dust halo that circles the player while the instinct lasts. */
    private void playAura(Player player, double seconds) {
        int totalTicks = (int) Math.round(seconds * 20.0);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= totalTicks || !player.isOnline() || player.isDead() || !instinctActive(player.getUniqueId())) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double angle = tick * 0.35;
                for (int index = 0; index < 3; index++) {
                    double a = angle + index * (2.0 * Math.PI / 3.0);
                    Location point = base.clone().add(Math.cos(a) * 0.9, 1.0 + Math.sin(tick * 0.2 + index) * 0.6, Math.sin(a) * 0.9);
                    player.getWorld().spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, WHITE_AURA, true);
                }
                if (tick % 4 == 0) {
                    player.getWorld().spawnParticle(Particle.REDSTONE, base.clone().add(0, 1.0, 0), 4, 0.5, 0.8, 0.5, 0.0, SILVER_AURA, true);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
