package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassSkillDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Combat behavior of class active skills (cskill/*.yml). Skills without an entry here only show their message. */
public final class ClassSkillService {

    public static final String WARRIOR_STRIKE = "warrior_strike";
    public static final String WARRIOR_STRIKE_UPGRADE = "warrior_strike_upgrade";
    public static final String INSTINCT_RELEASE = "instinct_release";
    public static final String SANCTUARY = "sanctuary";
    public static final String SANCTUARY_UPGRADE = "sanctuary_upgrade";
    public static final String ICE_LANCE = "ice_lance";
    public static final String ICE_LANCE_UPGRADE = "ice_lance_upgrade";

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
    private StatusEffectService statusEffectService;
    private ClassEffectService classEffectService;

    public ClassSkillService(JavaPlugin plugin, AttackService attackService, ClassService classService, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.classService = classService;
        this.profileManager = profileManager;
    }

    public void setClassEffectService(ClassEffectService classEffectService) {
        this.classEffectService = classEffectService;
    }

    public void setStatusEffectService(StatusEffectService statusEffectService) {
        this.statusEffectService = statusEffectService;
    }

    /** What the skill needs before it may spend MP/cooldown; null means the skill has no combat effect yet. */
    public Precheck precheck(Player player, ClassSkillDefinition skill) {
        if (WARRIOR_STRIKE.equalsIgnoreCase(skill.id())) {
            return precheckWarriorStrike(player, skill);
        }
        if (ICE_LANCE.equalsIgnoreCase(skill.id())) {
            return precheckIceLance(player);
        }
        if (INSTINCT_RELEASE.equalsIgnoreCase(skill.id())) {
            return Precheck.ready(null, null);
        }
        if (SANCTUARY.equalsIgnoreCase(skill.id())) {
            return precheckSanctuary(player);
        }
        return Precheck.noEffect();
    }

    /** Runs the effect decided in {@link #precheck}. */
    public void execute(Player player, ClassSkillDefinition skill, Precheck precheck) {
        if (INSTINCT_RELEASE.equalsIgnoreCase(skill.id())) {
            if (classEffectService != null) {
                classEffectService.activateInstinct(player, skill.values().getOrDefault("duration-seconds", 10.0));
            }
            return;
        }
        if (precheck.weapon() == null) return;
        if (precheck.target() != null && WARRIOR_STRIKE.equalsIgnoreCase(skill.id())) {
            warriorStrike(player, skill, precheck.target(), precheck.weapon());
        } else if (ICE_LANCE.equalsIgnoreCase(skill.id())) {
            iceLance(player, skill, precheck.weapon());
        } else if (SANCTUARY.equalsIgnoreCase(skill.id())) {
            sanctuary(player, skill, precheck.weapon());
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

    private Precheck precheckSanctuary(Player player) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike()) {
            return Precheck.rejected("武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }
        return Precheck.ready(null, weapon);
    }

    private Precheck precheckIceLance(Player player) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike() || weapon.attackType() != AttackType.RANGE) {
            return Precheck.rejected("遠距離武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }
        return Precheck.ready(null, weapon);
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

    // ---- sanctuary (healer) --------------------------------------------------------

    private static final UUID NO_KNOCKBACK_ID = UUID.fromString("6c0b1c7e-5d54-4b7a-9d83-2f6a1d9e0a11");
    private static final Particle.DustOptions SANCTUARY_RING = new Particle.DustOptions(Color.fromRGB(90, 230, 120), 1.3f);
    private static final Particle.DustOptions SANCTUARY_LIGHT = new Particle.DustOptions(Color.fromRGB(200, 255, 210), 1.0f);

    /**
     * A healing area (radius 7). Every second it heals allies in it by the damage the held weapon would deal
     * (no enemy armor; deploy weapons include the stability bonus). Upgrade: lasts longer and blocks knockback.
     */
    private void sanctuary(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        boolean upgraded = classService.hasSkill(profile, SANCTUARY_UPGRADE);
        double radius = skill.values().getOrDefault("radius", 7.0);
        double seconds = upgraded
                ? skill.values().getOrDefault("upgrade-duration-seconds", 10.0)
                : skill.values().getOrDefault("duration-seconds", 5.0);

        PlayerProfile snapshot = attackService.snapshotAttacker(player);
        double heal = attackService.calculateNeutralDamage(snapshot, weapon).damage();
        if (heal <= 0.0) {
            heal = Math.max(snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.WEAPON_DAMAGE),
                    snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.SPECIAL_DAMAGE));
        }
        final double healPerSecond = heal;
        final Location center = player.getLocation().clone();
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        int totalTicks = (int) Math.round(seconds * 20.0);

        new BukkitRunnable() {
            private int tick;
            private final java.util.Set<UUID> shielded = new java.util.HashSet<>();

            @Override
            public void run() {
                boolean finished = tick >= totalTicks || !player.isOnline();
                if (tick % 20 == 0 && !finished) {
                    for (Player ally : alliesIn(center, radius)) {
                        attackService.healPlayerBy(player, ally, healPerSecond);
                    }
                }
                if (upgraded) {
                    updateKnockbackShield(finished ? java.util.List.of() : alliesIn(center, radius), shielded);
                }
                if (finished) {
                    cancel();
                    return;
                }
                if (tick % 5 == 0) drawSanctuary(world, center, radius, tick);
                tick += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private java.util.List<Player> alliesIn(Location center, double radius) {
        java.util.List<Player> allies = new ArrayList<>();
        for (Player other : center.getWorld().getPlayers()) {
            if (other.isDead() || !other.isValid()) continue;
            if (other.getLocation().distanceSquared(center) <= radius * radius) allies.add(other);
        }
        return allies;
    }

    /** Knockback immunity for the players inside; removed as soon as they leave or it ends. */
    private void updateKnockbackShield(java.util.List<Player> inside, java.util.Set<UUID> shielded) {
        java.util.Set<UUID> insideIds = new java.util.HashSet<>();
        for (Player player : inside) {
            insideIds.add(player.getUniqueId());
            org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (attribute != null && shielded.add(player.getUniqueId())) {
                attribute.removeModifier(NO_KNOCKBACK_ID);
                attribute.addTransientModifier(new org.bukkit.attribute.AttributeModifier(
                        NO_KNOCKBACK_ID, "iruuRPG sanctuary", 1.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        shielded.removeIf(id -> {
            if (insideIds.contains(id)) return false;
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                if (attribute != null) attribute.removeModifier(NO_KNOCKBACK_ID);
            }
            return true;
        });
    }

    private void drawSanctuary(World world, Location center, double radius, int tick) {
        int points = 48;
        double rotation = tick * 0.05;
        for (int index = 0; index < points; index++) {
            double angle = 2.0 * Math.PI * index / points + rotation;
            Location edge = center.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
            world.spawnParticle(Particle.REDSTONE, edge, 1, 0.0, 0.0, 0.0, 0.0, SANCTUARY_RING, true);
            if (index % 6 == 0) {
                double rise = (tick % 40) / 40.0 * 2.5;
                world.spawnParticle(Particle.REDSTONE, edge.clone().add(0, rise, 0), 1, 0.0, 0.0, 0.0, 0.0, SANCTUARY_LIGHT, true);
            }
        }
        world.spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(0, 0.6, 0), 3, radius / 3.0, 0.3, radius / 3.0, 0.0);
    }

    // ---- ice lance (mage) ---------------------------------------------------

    private static final Particle.DustOptions ICE_CORE = new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1.6f);
    private static final Particle.DustOptions ICE_GLOW = new Particle.DustOptions(Color.fromRGB(60, 140, 255), 1.2f);
    private static final Particle.DustOptions ICE_FROST = new Particle.DustOptions(Color.fromRGB(150, 205, 255), 1.0f);
    private static final int LANCE_TICKS = 5;

    /** Pierces everything on a straight line, slows (or freezes, when upgraded) whatever it hits. */
    private void iceLance(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        boolean upgraded = classService.hasSkill(profile, ICE_LANCE_UPGRADE);
        double range = skill.values().getOrDefault("range", 18.0);
        double width = skill.values().getOrDefault("width", 0.9);
        double rate = skill.values().getOrDefault("damage-rate", 3.5);
        double slowPercent = upgraded
                ? skill.values().getOrDefault("upgrade-slow-percent", 999.0)
                : skill.values().getOrDefault("slow-percent", 50.0);
        long slowMillis = Math.round(skill.values().getOrDefault("slow-seconds", 7.0) * 1000.0);

        World world = player.getWorld();
        Location start = player.getEyeLocation().add(0.0, -0.25, 0.0);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        RayTraceResult wall = world.rayTraceBlocks(start, direction, range, FluidCollisionMode.NEVER, true);
        double length = wall == null ? range : Math.max(1.0, start.toVector().distance(wall.getHitPosition()));

        world.playSound(start, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
        world.playSound(start, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, upgraded ? 0.7f : 1.1f);
        playLanceTrail(world, start, direction, length, upgraded);

        UUID sourceId = player.getUniqueId();
        for (LivingEntity victim : entitiesOnLine(player, start, direction, length, width)) {
            AttackService.AttackDamage attack = attackService.calculateDamage(player, victim, weapon);
            double damage = Math.round(attack.result().damage() * rate * 10.0) / 10.0;
            boolean critical = attack.result().critical();
            if (!attackService.applyRangeDamage(player, victim, damage, weapon, critical)) continue;

            attackService.sendDamageDebug(player, AttackType.RANGE, victim, damage, critical, false);
            if (statusEffectService != null && victim.isValid() && !victim.isDead()) {
                statusEffectService.applyTimedSlow(victim, slowPercent, slowMillis, sourceId);
            }
            playLanceImpact(victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0), upgraded);
        }
    }

    private List<LivingEntity> entitiesOnLine(Player player, Location start, Vector direction, double length, double width) {
        Location mid = start.clone().add(direction.clone().multiply(length / 2.0));
        double half = length / 2.0 + width + 2.0;
        List<LivingEntity> hits = new ArrayList<>();
        for (Entity entity : start.getWorld().getNearbyEntities(mid, half, half, half)) {
            if (!isValidTarget(player, entity)) continue;

            BoundingBox box = entity.getBoundingBox().expand(width / 2.0);
            if (box.rayTrace(start.toVector(), direction, length) != null) {
                hits.add((LivingEntity) entity);
            }
        }
        hits.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(start)));
        return hits;
    }

    /** The spear flies out over a few ticks as a thick blue-white dust line with frost. */
    private void playLanceTrail(World world, Location start, Vector direction, double length, boolean upgraded) {
        Vector side = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.clone().crossProduct(direction).normalize();
        final Vector sideAxis = side;
        final Vector upAxis = up;

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= LANCE_TICKS) {
                    cancel();
                    return;
                }
                double from = length * tick / LANCE_TICKS;
                double to = length * (tick + 1) / LANCE_TICKS;
                for (double distance = from; distance < to; distance += 0.25) {
                    Location point = start.clone().add(direction.clone().multiply(distance));
                    world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, ICE_CORE, true);
                    double thickness = upgraded ? 0.34 : 0.24;
                    for (int index = 0; index < 4; index++) {
                        double angle = Math.PI / 2.0 * index + distance * 2.0;
                        Vector offset = sideAxis.clone().multiply(Math.cos(angle) * thickness)
                                .add(upAxis.clone().multiply(Math.sin(angle) * thickness));
                        world.spawnParticle(Particle.REDSTONE, point.clone().add(offset), 1, 0.0, 0.0, 0.0, 0.0, ICE_GLOW, true);
                    }
                    if (ThreadLocalRandom.current().nextInt(3) == 0) {
                        world.spawnParticle(Particle.SNOWFLAKE, point, 1, 0.15, 0.15, 0.15, 0.01, null, true);
                    }
                }
                Location tip = start.clone().add(direction.clone().multiply(to));
                world.spawnParticle(Particle.REDSTONE, tip, upgraded ? 6 : 3, 0.12, 0.12, 0.12, 0.0, ICE_CORE, true);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playLanceImpact(Location center, boolean upgraded) {
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.9f, 1.1f);
        world.spawnParticle(Particle.SNOWFLAKE, center, upgraded ? 40 : 18, 0.4, 0.5, 0.4, 0.05, null, true);
        world.spawnParticle(Particle.REDSTONE, center, upgraded ? 24 : 10, 0.45, 0.55, 0.45, 0.0, ICE_FROST, true);
        if (upgraded) {
            world.spawnParticle(Particle.BLOCK_CRACK, center, 30, 0.4, 0.5, 0.4, 0.0,
                    org.bukkit.Material.BLUE_ICE.createBlockData());
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
