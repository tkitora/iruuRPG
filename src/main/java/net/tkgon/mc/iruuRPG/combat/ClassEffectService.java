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
    /** The assassin's 固有2: the old extra orange hit and the confusion blast in one node. */
    public static final String ASSASSIN_ORANGE = "assassin_orange_strike";
    public static final String ASSASSIN_BLAST = "assassin_blast";
    private static final double ASSASSIN_BLAST_DIVISOR = 40.0;
    public static final String HEALER_PULSE = "healer_pulse";
    public static final String HEALER_GUARD = "healer_guard";
    public static final String HEALER_PULSE_WIDE = "healer_pulse_wide";
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
    private final Map<UUID, Long> instinctStartedMillis = new HashMap<>();
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
        onDamageDealt(attacker, victim, weapon, damage, critical, true);
    }

    /** {@code statuses} false: the enemy already got this attack's status effects (bleed, corrosion, decay, explosion). */
    public void onDamageDealt(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage, boolean critical, boolean statuses) {
        onDamageDealt(attacker, victim, weapon, damage, critical, statuses, null);
    }

    /** {@code snapshot}: the stats a status effect is worked out from (a deployed weapon's); null: the player's live stats. */
    public void onDamageDealt(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage, boolean critical, boolean statuses, PlayerProfile snapshot) {
        if (attacker == null || victim == null || victim.isDead() || !victim.isValid()) return;

        PlayerProfile profile = profileManager.getOrCreate(attacker);
        if (profile.classId().isBlank()) return;

        lastTargets.put(attacker.getUniqueId(), victim.getUniqueId());
        lastTargetTimes.put(attacker.getUniqueId(), System.currentTimeMillis());
        if (statuses) giftedOnHit(attacker, victim, weapon, profile, snapshot);
        if (attackService != null) attackService.rangerHit(attacker, victim, weapon);
        assassinOnHit(attacker, victim, weapon, damage, profile, statuses);
        surgeOnHit(attacker, damage);
        frenzyOnHit(attacker, victim, damage);
        if (statuses) mageOnHit(attacker, victim, damage, profile);
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
    private void assassinOnHit(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage, PlayerProfile profile, boolean statuses) {
        if (attackService == null || damage <= 0.0) return;

        UUID sourceId = attacker.getUniqueId();
        if (classService.hasSkill(profile, ASSASSIN_ORANGE)) {
            double extra = damage * (1.0 + Math.max(1, profile.level()) / 100.0);
            if (attackService.applyExtraDamage(attacker, victim, Math.round(extra * 10.0) / 10.0, net.tkgon.mc.iruuRPG.stat.Element.ORANGE)) {
                playOrangeHit(victim);
            }
        }
        if (victim.isDead() || !victim.isValid()) return;

        if (statuses && classService.hasSkill(profile, ASSASSIN_BLAST)
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
        ParticleFx.to(world).spawnParticle(Particle.REDSTONE, center, 10, 0.35, 0.45, 0.35, 0.0, ORANGE_HIT, true);
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
    private void giftedOnHit(Player attacker, LivingEntity victim, RpgItemDefinition weapon, PlayerProfile profile, PlayerProfile snapshot) {
        UUID sourceId = attacker.getUniqueId();
        StatSet stats = snapshot != null ? snapshot.finalStats() : profile.finalStats();
        int level = Math.max(1, profile.level());

        // the ranger's 固有2: attacks with a melee weapon cause bleeding (level x melee damage x (100% + melee damage %))
        if (classService.hasSkill(profile, GIFTED_BLEED) && weapon != null && weapon.attackType() == net.tkgon.mc.iruuRPG.combat.AttackType.MELEE) {
            double melee = Math.max(0.0, stats.get(StatType.MELEE_DAMAGE));
            double percent = Math.max(0.0, stats.get(StatType.MELEE_DAMAGE_PERCENT));
            statusEffectService.addBleed(victim, scale("gifted.bleed-scale") * level * melee * (1.0 + percent / 100.0), sourceId);
        }
    }

    private double scaleOrDefault(String path, double fallback) {
        return plugin.getConfig().getDouble("class-effects." + path, fallback);
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
     * Healer: when the healer is healed (item or class skills), the whole heal amount (overheal included) x level / 25
     * hits every enemy within 7 blocks as green damage (green damage buffs apply).
     */
    public void onSelfHealed(Player player, double healAmount) {
        if (healAmount <= 0.0 || attackService == null) return;

        PlayerProfile profile = profileManager.getOrCreate(player);
        if (!classService.hasSkill(profile, HEALER_PULSE)) return;

        double radius = plugin.getConfig().getDouble("class-effects.healer.pulse-radius", 7.0);
        // the cleric's 固有2 widens the pulse and slows what it hits
        boolean wide = classService.hasSkill(profile, HEALER_PULSE_WIDE);
        if (wide) radius *= Math.max(1.0, plugin.getConfig().getDouble("class-effects.healer.wide-radius-multiplier", 1.5));
        double slowPercent = plugin.getConfig().getDouble("class-effects.healer.wide-slow-percent", 40.0);
        long slowMillis = Math.round(plugin.getConfig().getDouble("class-effects.healer.wide-slow-seconds", 3.0) * 1000.0);
        double damage = healAmount * Math.max(1, profile.level()) / HEALER_PULSE_DIVISOR * greenFactor(profile);
        if (damage <= 0.0) return;

        Location center = player.getLocation();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity instanceof Player) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand || DeployService.isDeployEntity(target)) continue;
            if (target.getLocation().distanceSquared(center) > radius * radius) continue;

            attackService.applyExtraDamage(player, target, Math.round(damage * 10.0) / 10.0, net.tkgon.mc.iruuRPG.stat.Element.GREEN);
            if (wide && statusEffectService != null && target.isValid() && !target.isDead()) {
                statusEffectService.applyTimedSlow(target, slowPercent, slowMillis, player.getUniqueId());
            }
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
            ParticleFx.to(world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, GREEN_PULSE, true);
        }
        ParticleFx.to(world).spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(0, 1.0, 0), 6, 0.6, 0.6, 0.6, 0.0);
    }

    // ---- the assassin's frenzy (SATUJIN) and the warrior's surge (武神の一撃): states that last a few seconds --------------------------------

    /** {until (ms), level (1 normal, 2 upgraded)} of each assassin's frenzy. */
    private final Map<UUID, long[]> frenzy = new HashMap<>();
    /** {until (ms), level, share of the damage dealt that comes back as health} of each warrior's surge. */
    private final Map<UUID, double[]> surge = new HashMap<>();

    /** What each assassin dealt to each enemy during the frenzy (the item swap key, pressed again, gives back a share of it). */
    private final Map<UUID, Map<UUID, Double>> frenzyDamage = new HashMap<>();
    private final Map<UUID, Long> frenzyStarted = new HashMap<>();
    /** Where each assassin's frenzy began: the assassin cannot go farther from it than {@code class-effects.assassin.frenzy-radius} while it lasts. */
    private final Map<UUID, org.bukkit.Location> frenzyOrigin = new HashMap<>();
    /** Assassins whose echo damage is being dealt right now (it is not counted again). */
    private final java.util.Set<UUID> echoing = new java.util.HashSet<>();

    /** The frenzy: for {@code seconds} the assassin's combo is stronger (see {@link AssassinMeleeCombo}). */
    public void activateFrenzy(Player player, double seconds, int level) {
        frenzy.put(player.getUniqueId(), new long[]{System.currentTimeMillis() + Math.round(seconds * 1000.0), level});
        frenzyDamage.put(player.getUniqueId(), new HashMap<>());
        frenzyStarted.put(player.getUniqueId(), System.currentTimeMillis());
        frenzyOrigin.put(player.getUniqueId(), player.getLocation().clone());
    }

    /** The place the frenzy began, while it lasts (null when there is no frenzy). */
    public org.bukkit.Location frenzyCenter(Player player) {
        if (frenzyLevel(player) == 0) {
            frenzyOrigin.remove(player.getUniqueId());
            return null;
        }
        return frenzyOrigin.get(player.getUniqueId());
    }

    public long frenzyElapsedMillis(Player player) {
        Long started = frenzyStarted.get(player.getUniqueId());
        return started == null ? 0L : System.currentTimeMillis() - started;
    }

    /** The damage dealt to each enemy since the frenzy began; forgets it. */
    public Map<UUID, Double> takeFrenzyDamage(Player player) {
        Map<UUID, Double> dealt = frenzyDamage.remove(player.getUniqueId());
        return dealt == null ? new HashMap<>() : dealt;
    }

    public void setEchoing(Player player, boolean on) {
        if (on) echoing.add(player.getUniqueId());
        else echoing.remove(player.getUniqueId());
    }

    private void frenzyOnHit(Player attacker, LivingEntity victim, double damage) {
        if (damage <= 0.0 || echoing.contains(attacker.getUniqueId()) || frenzyLevel(attacker) == 0) return;
        Map<UUID, Double> dealt = frenzyDamage.get(attacker.getUniqueId());
        if (dealt != null) dealt.merge(victim.getUniqueId(), damage, Double::sum);
    }

    /** 0 when the assassin is not in a frenzy, else its level (1, or 2 when upgraded). */
    public int frenzyLevel(Player player) {
        long[] now = frenzy.get(player.getUniqueId());
        if (now == null) return 0;
        if (now[0] < System.currentTimeMillis()) {
            frenzy.remove(player.getUniqueId());
            return 0;
        }
        return (int) now[1];
    }

    public void endFrenzy(Player player) {
        frenzy.remove(player.getUniqueId());
        frenzyDamage.remove(player.getUniqueId());
        frenzyOrigin.remove(player.getUniqueId());
        AssassinClones.endEffect(player);
    }

    /** The surge: for {@code seconds} the warrior gets back {@code lifesteal} of every damage they deal. */
    public void activateSurge(Player player, double seconds, int level, double lifesteal) {
        surge.put(player.getUniqueId(), new double[]{System.currentTimeMillis() + Math.round(seconds * 1000.0), level, lifesteal});
    }

    public int surgeLevel(Player player) {
        double[] now = surge.get(player.getUniqueId());
        if (now == null) return 0;
        if (now[0] < System.currentTimeMillis()) {
            surge.remove(player.getUniqueId());
            return 0;
        }
        return (int) now[1];
    }

    public void endSurge(Player player) {
        surge.remove(player.getUniqueId());
    }

    /** The warrior's surge: part of the damage dealt flows back as health. */
    private void surgeOnHit(Player attacker, double damage) {
        if (attackService == null || damage <= 0.0 || surgeLevel(attacker) == 0) return;
        double share = surge.get(attacker.getUniqueId())[2];
        if (share > 0.0) attackService.healPlayer(attacker, damage * share);
    }

    // ---- instinct release (gifted active skill) --------------------------------

    public void activateInstinct(Player player, double seconds) {
        instinctUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + Math.round(seconds * 1000.0));
        instinctStartedMillis.put(player.getUniqueId(), System.currentTimeMillis());
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        RangerVfx.activation(world, player.getLocation());
        // the enhanced state also makes the ranger quicker on their feet for as long as it lasts
        int speedLevel = (int) Math.round(plugin.getConfig().getDouble("class-effects.gifted.instinct-speed-level", 1.0));
        if (speedLevel > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,
                    (int) Math.round(seconds * 20.0), speedLevel - 1, true, false, true));
        }
        playAura(player, seconds);
    }

    /** Milliseconds since the instinct release was cast, or -1 when it is not on. */
    public long instinctElapsedMillis(UUID playerId) {
        if (!instinctActive(playerId)) return -1L;
        Long started = instinctStartedMillis.get(playerId);
        return started == null ? -1L : System.currentTimeMillis() - started;
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
        new SafeTask(plugin) {
            private int tick;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (tick >= totalTicks || !player.isOnline() || player.isDead() || !instinctActive(player.getUniqueId())) {
                    cancel();
                    return;
                }
                RangerVfx.aura(player, tick);
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
