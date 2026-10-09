package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassSkillDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Combat behavior of class active skills (cskill/*.yml). Skills without an entry here only show their message. */
public final class ClassSkillService {

    public static final String WARRIOR_STRIKE = "warrior_strike";
    public static final String WARRIOR_STRIKE_UPGRADE = "warrior_strike_upgrade";

    private static final Particle.DustOptions BOLT_CORE = new Particle.DustOptions(Color.fromRGB(255, 244, 150), 1.5f);
    private static final Particle.DustOptions BOLT_GLOW = new Particle.DustOptions(Color.fromRGB(255, 214, 20), 1.1f);
    private static final Particle.DustOptions BIG_BOLT_CORE = new Particle.DustOptions(Color.fromRGB(255, 255, 215), 2.2f);
    private static final Particle.DustOptions BIG_BOLT_GLOW = new Particle.DustOptions(Color.fromRGB(255, 200, 0), 1.7f);
    private static final double BOLT_HEIGHT = 14.0;
    private static final int BOLT_FLASHES = 3;
    private static final long BOLT_FLASH_INTERVAL_TICKS = 2L;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final ClassService classService;
    private final PlayerProfileManager profileManager;

    public ClassSkillService(JavaPlugin plugin, AttackService attackService, ClassService classService, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.classService = classService;
        this.profileManager = profileManager;
    }

    /** What the skill needs before it may spend MP/cooldown; null means the skill has no combat effect yet. */
    public Precheck precheck(Player player, ClassSkillDefinition skill) {
        if (WARRIOR_STRIKE.equalsIgnoreCase(skill.id())) {
            return precheckWarriorStrike(player, skill);
        }
        return Precheck.noEffect();
    }

    /** Runs the effect decided in {@link #precheck}. */
    public void execute(Player player, ClassSkillDefinition skill, Precheck precheck) {
        if (precheck.target() != null && precheck.weapon() != null && WARRIOR_STRIKE.equalsIgnoreCase(skill.id())) {
            warriorStrike(player, skill, precheck.target(), precheck.weapon());
        }
    }

    private Precheck precheckWarriorStrike(Player player, ClassSkillDefinition skill) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike() || weapon.attackType() != AttackType.MELEE) {
            return Precheck.rejected("近接武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }

        double range = skill.values().getOrDefault("range", 6.0);
        LivingEntity target = findTarget(player, range);
        if (target == null) {
            return Precheck.rejected("前方に対象がいません。");
        }
        return Precheck.ready(target, weapon);
    }

    private LivingEntity findTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye,
                eye.getDirection(),
                range,
                0.6,
                entity -> isValidTarget(player, entity)
        );
        return result == null || !(result.getHitEntity() instanceof LivingEntity living) ? null : living;
    }

    private boolean isValidTarget(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (entity.equals(player) || entity instanceof Player || entity instanceof ArmorStand) return false;
        return !DeployService.isDeployEntity(living) && !living.isDead();
    }

    private void warriorStrike(Player player, ClassSkillDefinition skill, LivingEntity target, RpgItemDefinition weapon) {
        boolean upgraded = classService.hasSkill(profileManager.getOrCreate(player), WARRIOR_STRIKE_UPGRADE);
        if (!upgraded) {
            strikeHit(player, skill, target, weapon, skill.values().getOrDefault("damage-rate", 3.0), false);
            return;
        }

        // Upgrade: two-stage hit. The second bolt hits harder and looks bigger.
        double firstRate = skill.values().getOrDefault("upgrade-first-rate", 2.5);
        double secondRate = skill.values().getOrDefault("upgrade-second-rate", 4.0);
        long delay = Math.max(1L, Math.round(skill.values().getOrDefault("upgrade-delay-ticks", 8.0)));
        strikeHit(player, skill, target, weapon, firstRate, false);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || target.isDead() || !target.isValid()) return;
            strikeHit(player, skill, target, weapon, secondRate, true);
        }, delay);
    }

    private void strikeHit(Player player, ClassSkillDefinition skill, LivingEntity target, RpgItemDefinition weapon, double rate, boolean big) {
        AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
        double damage = Math.round(attack.result().damage() * rate * 10.0) / 10.0;
        boolean critical = attack.result().critical();

        playLightning(target.getEyeLocation(), target.getLocation(), big);
        if (!attackService.applyDirectDamage(player, target, damage, weapon, critical)) return;

        attackService.sendDamageDebug(player, AttackType.MELEE, target, damage, critical, false);
        // Melee area passive (if unlocked) also splashes each hit at the usual area rate.
        attackService.applyAreaDamage(player, target, damage, AttackType.MELEE, weapon);
    }

    /** A lightning bolt drawn with yellow dust particles, striking the head from above. */
    private void playLightning(Location head, Location feet, boolean big) {
        World world = head.getWorld();
        if (world == null) return;

        world.playSound(head, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, big ? 1.0f : 0.7f, big ? 1.2f : 1.6f);
        world.playSound(head, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, big ? 1.0f : 0.8f, big ? 0.9f : 1.2f);

        new BukkitRunnable() {
            private int flash;

            @Override
            public void run() {
                if (flash >= BOLT_FLASHES) {
                    cancel();
                    return;
                }
                drawBolt(world, head, feet, flash == 0, big);
                flash++;
            }
        }.runTaskTimer(plugin, 0L, BOLT_FLASH_INTERVAL_TICKS);
    }

    private void drawBolt(World world, Location head, Location feet, boolean impact, boolean big) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double height = big ? BOLT_HEIGHT + 6.0 : BOLT_HEIGHT;
        Location top = head.clone().add(random.nextDouble(-1.2, 1.2), height, random.nextDouble(-1.2, 1.2));
        int segments = (int) height;
        Location previous = top;
        for (int index = 1; index <= segments; index++) {
            double progress = index / (double) segments;
            // The jitter shrinks toward the head so the bolt always lands on it.
            double spread = (1.0 - progress) * (big ? 1.3 : 0.9);
            Location point = top.clone().add(
                    (head.getX() - top.getX()) * progress + random.nextDouble(-spread, spread),
                    (head.getY() - top.getY()) * progress,
                    (head.getZ() - top.getZ()) * progress + random.nextDouble(-spread, spread)
            );
            if (index == segments) {
                point = head.clone();
            }
            drawSegment(world, previous, point, big);
            previous = point;
        }

        if (impact) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, head, big ? 60 : 24, 0.6, 0.6, 0.6, 0.3);
            drawRing(world, feet.clone().add(0, 0.1, 0), big ? 2.4 : 1.6, big);
            if (big) {
                drawRing(world, feet.clone().add(0, 0.1, 0), 1.2, true);
                world.spawnParticle(Particle.FLASH, head, 1);
            }
        }
    }

    private void drawSegment(World world, Location from, Location to, boolean big) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 1.0E-6) return;

        int points = Math.max(2, (int) Math.ceil(length * 4.0));
        step.multiply(1.0 / points);
        Location cursor = from.clone();
        for (int index = 0; index <= points; index++) {
            world.spawnParticle(Particle.REDSTONE, cursor, big ? 2 : 1, 0.0, 0.0, 0.0, 0.0, big ? BIG_BOLT_CORE : BOLT_CORE, true);
            world.spawnParticle(Particle.REDSTONE, cursor, big ? 4 : 2, big ? 0.22 : 0.12, big ? 0.22 : 0.12, big ? 0.22 : 0.12, 0.0, big ? BIG_BOLT_GLOW : BOLT_GLOW, true);
            cursor.add(step);
        }
    }

    private void drawRing(World world, Location center, double radius, boolean big) {
        int points = 28;
        for (int index = 0; index < points; index++) {
            double angle = 2.0 * Math.PI * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, big ? BIG_BOLT_GLOW : BOLT_GLOW, true);
        }
    }

    public record Precheck(boolean hasEffect, String rejection, LivingEntity target, RpgItemDefinition weapon) {

        static Precheck noEffect() {
            return new Precheck(false, null, null, null);
        }

        static Precheck rejected(String message) {
            return new Precheck(true, message == null ? "" : message, null, null);
        }

        static Precheck ready(LivingEntity target, RpgItemDefinition weapon) {
            return new Precheck(true, null, target, weapon);
        }

        public boolean rejected() {
            return rejection != null;
        }
    }
}
