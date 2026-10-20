package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The warrior's combo (see {@link MeleeCombo}, plan {@code warrior}) on a melee weapon: left click is a normal attack,
 * right click a heavy one.
 *
 * <p>Normal attacks: 1 a slash from the upper right to the lower left (90%), 2 from the upper left to the lower right
 * (90%), 3 a sideways slash (120%) - these three are drawn by the class's {@code combo-style}. 4 a chop from above
 * (150%) that throws the enemies back, 5 a big conical thrust (150%) that finds the enemies thrown back.
 *
 * <p>Heavy attacks (the number of normal attacks so far): 0 three shields turning round the player (at most 3, 15 s
 * cooldown): damage taken is cut by 70% while one is up, and each damage takes one shield, 1 three wheel-shaped slashes
 * put on the ground ahead, 1.5 blocks apart (30% on contact), 2 twenty ticks of spinning (4% every 3 ticks, radius 4,
 * lifts the enemies a little) ending in a conical thrust (50%), 3 a jump straight up that throws the enemies in front
 * up (70%), 4 three big cuts. While 2 or 3 last, normal attacks stop; a heavy click in that time (it would be heavy 0,
 * but is not thrown, and no cooldown) ends 2 early, or, after 3, dives and brings the enemies thrown up down again
 * (70%); within a second of 4 it leaps back and comes down with a crack in the ground ahead that throws the enemies up
 * (120%), and within 2 seconds of that another one rushes behind a big shield (160%, with a shield left) or hops back.
 */
public final class WarriorMoves {

    private static final Color IRON = Color.fromRGB(215, 220, 230);

    /** What a heavy click does right now (instead of heavy 0): set while heavy 2, 3 or 4 can be followed up. */
    private static final class Follow {
        private final long until;
        private final Runnable action;

        private Follow(long until, Runnable action) {
            this.until = until;
            this.action = action;
        }
    }

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects effects;
    private final Map<UUID, Follow> follows = new HashMap<>();
    /** The enemies a chop in the air threw back, and when. */
    private final Map<UUID, Thrown> thrown = new HashMap<>();

    private record Thrown(List<LivingEntity> targets, long when) {
    }

    public WarriorMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.effects = effects;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.warrior." + key, fallback);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------

    private Vector facing(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) direction = new Vector(0.0, 0.0, 1.0);
        return direction.normalize();
    }

    private void later(long ticks, Runnable action) {
        if (ticks <= 0L) action.run();
        else plugin.getServer().getScheduler().runTaskLater(plugin, action, ticks);
    }

    private boolean strike(Player player, LivingEntity target, RpgItemDefinition weapon, double rate) {
        return attackService.strike(player, target, weapon, rate);
    }

    private List<LivingEntity> around(Player player, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, 2.5, radius)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.getLocation().getX() - center.getX();
            double dz = living.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            result.add(living);
        }
        return result;
    }

    /** The enemies within {@code radius} of the player and within {@code halfAngle} degrees of where the player faces. */
    private List<LivingEntity> cone(Player player, double radius, double halfAngle) {
        Vector facing = facing(player);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            result.add(target);
        }
        return result;
    }

    private void push(LivingEntity target, Vector from, double power, double lift) {
        Vector away = target.getLocation().toVector().subtract(from).setY(0.0);
        if (away.lengthSquared() < 1.0E-6) return;
        target.setVelocity(away.normalize().multiply(power).setY(lift));
    }

    private Color color(Element element) {
        return switch (element) {
            case RED -> Color.fromRGB(255, 90, 80);
            case BLUE -> Color.fromRGB(100, 160, 255);
            case GREEN -> Color.fromRGB(120, 235, 140);
            case ORANGE -> Color.fromRGB(255, 165, 60);
            default -> IRON;
        };
    }

    private void line(Player player, World world, Location from, Location to, Color color, float size, Particle spark, int every) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6) return;
        Vector direction = path.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            if (spark != null && index % every == 0) ParticleFx.to(player, world).spawnParticle(spark, point, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    /** A ring of dust on the ground (or in the air) around {@code center}. */
    private void ring(Player player, World world, Location center, double radius, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        int points = (int) Math.max(18, radius * 10);
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    /** A blow where the player stands or lands: a ring, sparks and a thud. */
    private void impact(Player player, World world, Location at, double radius, RpgItemDefinition weapon, float loudness) {
        ring(player, world, at, radius, color(weapon.element()));
        ring(player, world, at, radius * 0.6, IRON);
        ParticleFx.to(player, world).spawnParticle(Particle.EXPLOSION_NORMAL, at.clone().add(0.0, 0.3, 0.0), 8, radius * 0.3, 0.2, radius * 0.3, 0.05);
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT, at.clone().add(0.0, 0.5, 0.0), 14, radius * 0.4, 0.3, radius * 0.4, 0.3);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6f * loudness, 1.1f);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0f * loudness, 0.8f);
    }

    // ---- what a heavy click does (instead of heavy 0) --------------------------------------------------------------------------

    /** True while a heavy click would follow up a heavy attack instead of throwing heavy 0. */
    public boolean followActive(Player player) {
        Follow follow = follows.get(player.getUniqueId());
        if (follow == null) return false;
        if (System.currentTimeMillis() > follow.until) {
            follows.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** Runs the follow-up of the running heavy attack. False when there is none. */
    public boolean followUp(Player player) {
        if (!followActive(player)) return false;
        Follow follow = follows.remove(player.getUniqueId());
        follow.action.run();
        return true;
    }

    private void follow(Player player, long ticks, Runnable action) {
        follows.put(player.getUniqueId(), new Follow(System.currentTimeMillis() + ticks * 50L, action));
    }

    // ---- normal attacks 4 and 5 ------------------------------------------------------------------------------------------------

    /** Normal attack 4 or 5 ({@code step.stage()}); 1 - 3 are the usual slashes. */
    public void normal(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        if (step.stage() == 4) {
            chop(player, weapon);
            return;
        }
        Thrown last = thrown.remove(player.getUniqueId());
        List<LivingEntity> chase = last != null && System.currentTimeMillis() - last.when() <= (long) (number("follow-seconds", 3.0) * 1000.0) ? last.targets() : null;
        thrust(player, weapon, number("thrust-rate", 1.5), chase);
    }

    /** 4: a chop from above that throws what it hits back. */
    private void chop(Player player, RpgItemDefinition weapon) {
        World world = player.getWorld();
        Vector facing = facing(player);
        double rate = number("chop-rate", 1.5);
        Location eye = player.getEyeLocation();
        Color color = color(weapon.element());
        // an arc in the vertical plane in front, from above and behind the head down to the ground in front
        int points = 14;
        for (int index = 0; index <= points; index++) {
            double angle = Math.toRadians(110.0 - 150.0 * index / points);
            double forward = Math.cos(angle) * 2.8;
            double height = Math.sin(angle) * 2.8;
            Location at = eye.clone().add(facing.clone().multiply(forward)).add(0.0, height - 0.6, 0.0);
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 2, 0.05, 0.05, 0.05, 0.0, new Particle.DustOptions(index % 2 == 0 ? color : IRON, 1.8f));
            if (index % 2 == 0) ParticleFx.to(player, world).spawnParticle(Particle.CRIT, at, 1, 0.05, 0.05, 0.05, 0.0);
        }
        Location ground = player.getLocation().add(facing.clone().multiply(2.6));
        impact(player, world, ground, 2.2, weapon, 1.0f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.8f, 0.6f);
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity target : cone(player, number("chop-radius", 3.6), 80.0)) {
            if (!strike(player, target, weapon, rate)) continue;
            hit.add(target);
            target.setVelocity(facing.clone().multiply(number("chop-knockback", 0.9)).setY(0.25));
        }
        if (!hit.isEmpty()) thrown.put(player.getUniqueId(), new Thrown(hit, System.currentTimeMillis()));
    }

    /**
     * 5: a big conical thrust ahead. {@code chase}: the enemies the chop threw back; they are hit for sure when they are
     * within a reasonable distance and in front, and the thrust is drawn toward the nearest of them.
     */
    private void thrust(Player player, RpgItemDefinition weapon, double rate, List<LivingEntity> chase) {
        World world = player.getWorld();
        Vector facing = facing(player);
        Location eye = player.getEyeLocation();
        double length = number("thrust-length", 5.5);
        List<LivingEntity> targets = new ArrayList<>();
        Vector aim = facing.clone();
        if (chase != null) {
            double reach = number("chase-reach", 9.0);
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity target : chase) {
                if (target.isDead() || !target.isValid() || !target.getWorld().equals(world)) continue;
                Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
                double distance = to.length();
                if (distance > reach || (distance > 1.0 && Math.toDegrees(facing.angle(to)) > 110.0)) continue;
                targets.add(target);
                if (distance < best) {
                    best = distance;
                    nearest = target;
                }
            }
            if (nearest != null) {
                Vector toward = nearest.getLocation().add(0.0, nearest.getHeight() * 0.5, 0.0).toVector().subtract(eye.toVector());
                if (toward.lengthSquared() > 1.0E-6) aim = toward.normalize();
                length = Math.max(length, Math.min(reach, best + 1.0));
            }
        }
        for (LivingEntity target : cone(player, length, number("thrust-half-angle", 30.0))) {
            if (!targets.contains(target)) targets.add(target);
        }
        Location start = eye.clone().add(aim.clone().multiply(0.8)).subtract(0.0, 0.35, 0.0);
        coneThrust(player, weapon, start, aim, length, number("thrust-spread", 1.8));
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.8f, 1.2f);
        // a step into the thrust
        player.setVelocity(aim.clone().setY(0.0).normalize().multiply(0.4).setY(player.getVelocity().getY()));
        for (LivingEntity target : targets) strike(player, target, weapon, rate);
    }

    /** A thrust drawn as a cone: lines from the tip out to a circle {@code spread} wide at the end, with rings on the way. */
    private void coneThrust(Player player, RpgItemDefinition weapon, Location start, Vector aim, double length, double spread) {
        World world = player.getWorld();
        Color color = color(weapon.element());
        Vector side = aim.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1.0, 0.0, 0.0);
        side.normalize();
        Vector up = side.clone().crossProduct(aim).normalize();
        Location end = start.clone().add(aim.clone().multiply(length));
        // the axis, and eight lines to the rim
        line(player, world, start, end, IRON, 1.1f, Particle.END_ROD, 4);
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2.0 * index / 8.0;
            Location rim = end.clone().add(side.clone().multiply(Math.cos(angle) * spread)).add(up.clone().multiply(Math.sin(angle) * spread));
            line(player, world, start, rim, index % 2 == 0 ? color : IRON, 1.0f, Particle.CRIT, 3);
        }
        for (int ring = 1; ring <= 4; ring++) {
            double along = length * ring / 4.0;
            double radius = spread * ring / 4.0;
            Location center = start.clone().add(aim.clone().multiply(along));
            for (int index = 0; index < 14; index++) {
                double angle = Math.PI * 2.0 * index / 14.0;
                ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, center.clone().add(side.clone().multiply(Math.cos(angle) * radius)).add(up.clone().multiply(Math.sin(angle) * radius)), 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(color, 1.3f));
            }
        }
        ParticleFx.to(player, world).spawnParticle(Particle.FLASH, end, 1);
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT, end, 14, spread * 0.3, spread * 0.3, spread * 0.3, 0.4);
        world.playSound(end, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.9f, 1.2f);
    }

    // ---- heavy attacks ---------------------------------------------------------------------------------------------------------

    /** Throws heavy attack {@code step.heavyIndex()}. */
    public void heavy(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        // a new heavy attack ends what could have been followed up
        follows.remove(player.getUniqueId());
        switch (step.heavyIndex()) {
            case 0 -> gainShields(player);
            case 1 -> wheels(player, weapon, step.damageMultiplier());
            case 2 -> spin(player, weapon, step.damageMultiplier());
            case 3 -> launch(player, weapon, step.damageMultiplier());
            default -> tripleCut(player, weapon, step.damageMultiplier());
        }
    }

    // ---- 0: the shields ------------------------------------------------------------------------------------------------------------

    private final Map<UUID, Integer> shields = new HashMap<>();
    private final Map<UUID, Long> chargingUntil = new HashMap<>();
    private SafeTask shieldTask;
    private int shieldTicks;

    public int shieldCount(Player player) {
        return shields.getOrDefault(player.getUniqueId(), 0);
    }

    private void gainShields(Player player) {
        shields.put(player.getUniqueId(), (int) number("shield-count", 3.0));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1.0f, 0.9f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT_MAGIC, player.getLocation().add(0.0, 1.0, 0.0), 20, 0.6, 0.6, 0.6, 0.3);
        ensureShieldTask();
    }

    /**
     * Damage {@code player} is about to take: with a shield up it is cut by 70% and the shield is lost (during the
     * charge only the cut applies). Returns the damage to take.
     */
    public double absorb(Player player, double damage) {
        if (damage <= 0.0) return damage;
        int count = shieldCount(player);
        if (count <= 0) return damage;
        Long charging = chargingUntil.get(player.getUniqueId());
        boolean charge = charging != null && System.currentTimeMillis() <= charging;
        World world = player.getWorld();
        if (!charge) {
            if (count - 1 <= 0) shields.remove(player.getUniqueId());
            else shields.put(player.getUniqueId(), count - 1);
            world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 0.9f, 1.2f);
            ParticleFx.to(player, world).spawnParticle(Particle.CRIT, player.getLocation().add(0.0, 1.0, 0.0), 14, 0.5, 0.5, 0.5, 0.3);
        } else {
            world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        return damage * (1.0 - number("shield-reduction", 0.7));
    }

    private void ensureShieldTask() {
        if (shieldTask != null) return;
        shieldTask = new SafeTask(plugin) {
            @Override
            protected void tick() {
                shieldTicks++;
                if (shields.isEmpty()) {
                    cancel();
                    shieldTask = null;
                    return;
                }
                for (UUID id : new ArrayList<>(shields.keySet())) {
                    Player owner = plugin.getServer().getPlayer(id);
                    if (owner == null || !owner.isOnline() || owner.isDead()) {
                        shields.remove(id);
                        continue;
                    }
                    int count = shields.get(id);
                    Location center = owner.getLocation().add(0.0, 1.1, 0.0);
                    World world = owner.getWorld();
                    for (int index = 0; index < count; index++) {
                        double angle = shieldTicks * 0.22 + Math.PI * 2.0 * index / count;
                        Vector out = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
                        Location at = center.clone().add(out.clone().multiply(1.3));
                        Vector tangent = new Vector(-out.getZ(), 0.0, out.getX());
                        // a small plate standing on the circle: a column and a few points across
                        for (double h = -0.35; h <= 0.35; h += 0.17) {
                            for (double w = -0.22; w <= 0.22; w += 0.22) {
                                ParticleFx.to(owner, world).spawnParticle(Particle.REDSTONE, at.clone().add(tangent.clone().multiply(w)).add(0.0, h, 0.0), 1, 0.0, 0.0, 0.0, 0.0,
                                        new Particle.DustOptions(Math.abs(w) > 0.1 ? IRON : Color.fromRGB(110, 170, 255), 1.1f));
                            }
                        }
                    }
                }
            }
        };
        shieldTask.runTaskTimer(plugin, 0L, 2L);
    }

    public void shutdown() {
        if (shieldTask != null) shieldTask.cancel();
        shieldTask = null;
        shields.clear();
        wheelList.clear();
        if (wheelTask != null) wheelTask.cancel();
        wheelTask = null;
    }

    // ---- 1: three wheels of slashes on the ground ahead ---------------------------------------------------------------------------

    private static final class Wheel {
        private final Player owner;
        private final Location at;
        private final Vector facing;
        private final RpgItemDefinition weapon;
        private final double rate;
        private final long until;
        private final Map<UUID, Long> hitAt = new HashMap<>();

        private Wheel(Player owner, Location at, Vector facing, RpgItemDefinition weapon, double rate, long until) {
            this.owner = owner;
            this.at = at;
            this.facing = facing;
            this.weapon = weapon;
            this.rate = rate;
            this.until = until;
        }
    }

    private final List<Wheel> wheelList = new ArrayList<>();
    private SafeTask wheelTask;
    private int wheelTicks;

    private void wheels(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        double spacing = number("wheel-spacing", 1.5);
        long until = System.currentTimeMillis() + Math.round(number("wheel-seconds", 6.0) * 1000.0);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.7f);
        for (int index = 1; index <= 3; index++) {
            Location at = player.getLocation().add(facing.clone().multiply(spacing * index)).add(0.0, 1.0, 0.0);
            int which = index;
            later(index - 1, () -> {
                if (!player.isOnline()) return;
                wheelList.add(new Wheel(player, at, facing, weapon, rate, until));
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0f, 0.8f + which * 0.15f);
                ParticleFx.to(player, world).spawnParticle(Particle.CRIT, at, 8, 0.4, 0.4, 0.4, 0.2);
                ensureWheelTask();
            });
        }
    }

    private void ensureWheelTask() {
        if (wheelTask != null) return;
        wheelTask = new SafeTask(plugin) {
            @Override
            protected void tick() {
                wheelTicks++;
                long now = System.currentTimeMillis();
                wheelList.removeIf(wheel -> now > wheel.until || !wheel.owner.isOnline());
                if (wheelList.isEmpty()) {
                    cancel();
                    wheelTask = null;
                    return;
                }
                double reach = number("wheel-radius", 1.2);
                for (Wheel wheel : wheelList) {
                    World world = wheel.at.getWorld();
                    if (world == null) continue;
                    Vector side = wheel.facing.clone().crossProduct(new Vector(0.0, 1.0, 0.0)).normalize();
                    Color color = color(wheel.weapon.element());
                    // a wheel standing on its edge, turning: a rim, spokes and sparks
                    double turn = wheelTicks * 0.5;
                    for (int index = 0; index < 20; index++) {
                        double angle = Math.PI * 2.0 * index / 20.0 + turn;
                        Location rim = wheel.at.clone().add(wheel.facing.clone().multiply(Math.cos(angle) * reach)).add(0.0, Math.sin(angle) * reach, 0.0);
                        ParticleFx.to(wheel.owner, world).spawnParticle(Particle.REDSTONE, rim, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(index % 2 == 0 ? color : IRON, 1.4f));
                    }
                    for (int spoke = 0; spoke < 3; spoke++) {
                        double angle = turn + Math.PI * spoke / 3.0;
                        for (double r = -reach; r <= reach; r += 0.4) {
                            ParticleFx.to(wheel.owner, world).spawnParticle(Particle.REDSTONE, wheel.at.clone().add(wheel.facing.clone().multiply(Math.cos(angle) * r)).add(0.0, Math.sin(angle) * r, 0.0), 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(IRON, 0.9f));
                        }
                    }
                    if (wheelTicks % 3 == 0) ParticleFx.to(wheel.owner, world).spawnParticle(Particle.CRIT, wheel.at, 2, reach * 0.5, reach * 0.5, reach * 0.5, 0.1);
                    // touching it hurts (not the same enemy again within half a second)
                    for (Entity entity : world.getNearbyEntities(wheel.at, reach + 0.4, reach + 0.4, reach + 0.4)) {
                        if (!attackService.isAttackTarget(entity, wheel.owner)) continue;
                        LivingEntity target = (LivingEntity) entity;
                        Location body = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
                        if (body.distanceSquared(wheel.at) > (reach + 0.4) * (reach + 0.4)) continue;
                        Long last = wheel.hitAt.get(target.getUniqueId());
                        if (last != null && now - last < 500L) continue;
                        wheel.hitAt.put(target.getUniqueId(), now);
                        if (strike(wheel.owner, target, wheel.weapon, wheel.rate)) {
                            world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.4f);
                            ParticleFx.to(wheel.owner, world).spawnParticle(Particle.SWEEP_ATTACK, body, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }
                }
            }
        };
        wheelTask.runTaskTimer(plugin, 0L, 2L);
    }

    // 2: twenty ticks of spinning that end in a thrust; a heavy click ends it early
    private void spin(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        int ticks = (int) number("spin-ticks", 20.0);
        int every = (int) Math.max(1, number("spin-every-ticks", 3.0));
        double radius = number("spin-radius", 4.0);
        double endRate = number("spin-end-rate", 0.5);
        Color color = color(weapon.element());
        boolean[] over = {false};
        SafeTask[] holder = new SafeTask[1];
        Runnable finish = () -> {
            if (over[0]) return;
            over[0] = true;
            if (holder[0] != null) holder[0].cancel();
            follows.remove(player.getUniqueId());
            // a thrust 4 blocks ahead, a cone, 50%
            Vector facing = facing(player);
            Location start = player.getEyeLocation().add(facing.clone().multiply(0.8)).subtract(0.0, 0.35, 0.0);
            double length = number("spin-end-length", 4.0);
            coneThrust(player, weapon, start, facing, length, number("thrust-spread", 1.8) * 0.8);
            world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.8f);
            for (LivingEntity target : cone(player, length, number("thrust-half-angle", 30.0))) strike(player, target, weapon, endRate);
        };
        ClickSignals.lockLeft(player, ticks + 2);
        follow(player, ticks, finish);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
        holder[0] = new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || over[0]) {
                    cancel();
                    return;
                }
                if (age >= ticks) {
                    finish.run();
                    return;
                }
                // blades turning round the player
                Location center = player.getLocation().add(0.0, 1.0, 0.0);
                for (int arm = 0; arm < 2; arm++) {
                    double base = age * 0.8 + arm * Math.PI;
                    for (int step = 0; step < 9; step++) {
                        double angle = base - step * 0.1;
                        for (double r = radius - 0.9; r <= radius; r += 0.45) {
                            Location at = center.clone().add(Math.cos(angle) * r, Math.sin(age * 0.5 + arm) * 0.25, Math.sin(angle) * r);
                            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(step < 2 ? IRON : color, 1.5f));
                        }
                    }
                    ParticleFx.to(player, world).spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(Math.cos(base) * (radius - 0.3), 0.0, Math.sin(base) * (radius - 0.3)), 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (age % 4 == 0) world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 0.9f);
                if (age % every == 0) {
                    for (LivingEntity target : around(player, player.getLocation(), radius)) {
                        if (!strike(player, target, weapon, rate)) continue;
                        // a little float: up, and down again before the next hit
                        target.setVelocity(new Vector(target.getVelocity().getX(), number("spin-float", 0.22), target.getVelocity().getZ()));
                    }
                }
                age++;
            }
        };
        holder[0].runTaskTimer(plugin, 0L, 1L);
    }

    // 3: a jump straight up that throws the enemies in front up (an uppercut); a heavy click dives and brings them down
    private void launch(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        int window = (int) number("launch-window-ticks", 30.0);
        double radius = number("launch-radius", 5.0);
        List<LivingEntity> up = new ArrayList<>();
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0.0, number("launch-jump-power", 0.9), 0.0));
        // the uppercut: an arc rising in front from low to high, with sparks thrown up after it
        Location base = player.getLocation().add(0.0, 0.2, 0.0);
        Color color = color(weapon.element());
        for (int index = 0; index <= 14; index++) {
            double angle = Math.toRadians(-20.0 + 110.0 * index / 14.0);
            Location at = base.clone().add(facing.clone().multiply(Math.cos(angle) * 2.6)).add(0.0, 1.0 + Math.sin(angle) * 2.6, 0.0);
            later(index / 5, () -> {
                ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 2, 0.05, 0.05, 0.05, 0.0, new Particle.DustOptions(color, 1.8f));
                ParticleFx.to(player, world).spawnParticle(Particle.CRIT, at, 1, 0.05, 0.05, 0.05, 0.1);
            });
        }
        ParticleFx.to(player, world).spawnParticle(Particle.CLOUD, base.clone().add(facing.clone().multiply(1.5)), 10, 0.8, 0.1, 0.8, 0.05);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.8f, 1.4f);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.4f, 1.8f);
        for (LivingEntity target : cone(player, radius, 50.0)) {
            if (!strike(player, target, weapon, rate)) continue;
            target.setVelocity(new Vector(0.0, number("launch-power", 1.0), 0.0));
            ParticleFx.to(player, world).spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.4);
            up.add(target);
        }
        ClickSignals.lockLeft(player, window);
        boolean[] over = {false};
        follow(player, window, () -> {
            over[0] = true;
            dive(player, weapon, rate, up);
        });
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || over[0] || age++ >= window) {
                    cancel();
                    follows.remove(player.getUniqueId());
                    return;
                }
                player.setFallDistance(0.0f);
                if (age > 8 && player.isOnGround()) {
                    cancel();
                    follows.remove(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Down from the air, and the enemies thrown up come down with it. */
    private void dive(Player player, RpgItemDefinition weapon, double rate, List<LivingEntity> up) {
        World world = player.getWorld();
        Vector facing = facing(player);
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(0.3).setY(-number("dive-power", 1.4)));
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.5f);
        Color color = color(weapon.element());
        for (LivingEntity target : up) {
            if (target.isDead() || !target.isValid() || !target.getWorld().equals(world)) continue;
            Location at = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
            line(player, world, player.getLocation().add(0.0, 1.0, 0.0), at, color, 1.3f, Particle.CRIT, 2);
            if (strike(player, target, weapon, rate)) {
                target.setVelocity(new Vector(0.0, -number("slam-down-power", 1.5), 0.0));
                ParticleFx.to(player, world).spawnParticle(Particle.CRIT, at, 10, 0.3, 0.4, 0.3, 0.3);
            }
        }
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age++ > 25) {
                    cancel();
                    return;
                }
                player.setFallDistance(0.0f);
                if (age > 2 && player.isOnGround()) {
                    cancel();
                    impact(player, world, player.getLocation(), 2.6, weapon, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // 4: three big cuts; within a second a heavy click leaps back and comes down with a crack in the ground
    private void tripleCut(Player player, RpgItemDefinition weapon, double crackRate) {
        World world = player.getWorld();
        double rate = number("cut-rate", 0.5);
        double radius = number("cut-radius", 4.5);
        double[][] looks = {{0.0, 0.0}, {45.0, 180.0}, {80.0, 0.0}};
        for (int index = 0; index < 3; index++) {
            int which = index;
            later(index * 3L, () -> {
                if (!player.isOnline() || player.isDead()) return;
                // bigger than a usual cut (1.5 times)
                ComboStyle.Stage stage = new ComboStyle.Stage(List.of(new ComboStyle.Cut(3.0, 170.0, looks[which][0], looks[which][1], 0.0, 2.2, 0.3, 0.18, 1.5, 0.0, 0.0)), "sweep", 10);
                effects.playStyledSlash(player, weapon, stage, null, which);
                for (LivingEntity target : cone(player, radius, 90.0)) strike(player, target, weapon, rate);
            });
        }
        follow(player, (long) number("crack-window-ticks", 20.0) + 9L, () -> crack(player, weapon, crackRate));
    }

    /** A leap back, then down with a crack in the ground ahead that throws the enemies up. */
    private void crack(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(-number("crack-back-power", 1.0)).setY(number("crack-up-power", 0.75)));
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 0.7f);
        ClickSignals.lockLeft(player, 12);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                age++;
                player.setFallDistance(0.0f);
                // in the air it comes down after the top of the leap
                if (age == 7) player.setVelocity(player.getVelocity().multiply(0.3).setY(-number("crack-dive-power", 1.3)));
                if ((age > 7 && player.isOnGround()) || age > 30) {
                    cancel();
                    split(player, weapon, rate, facing);
                    // within 2 seconds the next heavy click charges with a big shield (when one is left), or hops back
                    follow(player, (long) number("charge-window-ticks", 40.0), () -> {
                        if (shieldCount(player) > 0) charge(player, weapon, facing);
                        else hopBack(player, facing);
                    });
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** The ground splits ahead of the player (about 10 blocks) and the enemies on it are thrown up. */
    private void split(Player player, RpgItemDefinition weapon, double rate, Vector facing) {
        World world = player.getWorld();
        Location start = player.getLocation().clone();
        double length = number("crack-length", 10.0);
        double width = number("crack-width", 3.5);
        Block below = start.clone().subtract(0.0, 0.5, 0.0).getBlock();
        Material material = below.getType().isSolid() ? below.getType() : Material.STONE;
        Vector side = facing.clone().crossProduct(new Vector(0.0, 1.0, 0.0)).normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world.playSound(start, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.6f);
        // the crack runs away from the player over a few ticks, with forks
        for (int step = 0; step < 6; step++) {
            double from = length * step / 6.0;
            double to = length * (step + 1) / 6.0;
            later(step, () -> {
                if (!player.isOnline()) return;
                for (double along = from; along < to; along += 0.4) {
                    for (int fork = -1; fork <= 1; fork++) {
                        double offset = fork * width * 0.3 * (along / length) + random.nextDouble(-0.15, 0.15);
                        Location at = start.clone().add(facing.clone().multiply(along)).add(side.clone().multiply(offset)).add(0.0, 0.1, 0.0);
                        ParticleFx.to(player, world).spawnParticle(Particle.BLOCK_CRACK, at, 6, 0.15, 0.1, 0.15, 0.2, material.createBlockData());
                        ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(Color.fromRGB(40, 30, 25), 1.6f));
                    }
                }
                Location mid = start.clone().add(facing.clone().multiply((from + to) / 2.0));
                world.playSound(mid, Sound.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
                ParticleFx.to(player, world).spawnParticle(Particle.EXPLOSION_NORMAL, mid.clone().add(0.0, 0.3, 0.0), 3, 0.6, 0.2, 0.6, 0.02);
                // what is on this stretch is thrown up
                for (Entity entity : world.getNearbyEntities(mid, (to - from) / 2.0 + 1.0, 2.5, (to - from) / 2.0 + 1.0)) {
                    if (!attackService.isAttackTarget(entity, player)) continue;
                    LivingEntity target = (LivingEntity) entity;
                    Vector rel = target.getLocation().toVector().subtract(start.toVector());
                    double along = rel.dot(facing);
                    double across = Math.abs(rel.dot(side));
                    if (along < from - 0.5 || along > to + 0.5 || across > width / 2.0 + 0.5) continue;
                    if (strike(player, target, weapon, rate)) target.setVelocity(new Vector(0.0, number("crack-launch-power", 1.0), 0.0));
                }
            });
        }
    }

    /** Without a shield: a small hop back. */
    private void hopBack(Player player, Vector facing) {
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(-0.6).setY(0.35));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /** A rush behind a big shield: enemies on the way are pushed aside and take 160%; the shields do not break meanwhile. */
    private void charge(Player player, RpgItemDefinition weapon, Vector facing) {
        World world = player.getWorld();
        int ticks = (int) number("charge-ticks", 12.0);
        double power = number("charge-power", 1.1);
        double rate = number("charge-rate", 1.6);
        Vector side = facing.clone().crossProduct(new Vector(0.0, 1.0, 0.0)).normalize();
        Color color = color(weapon.element());
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        chargingUntil.put(player.getUniqueId(), System.currentTimeMillis() + (ticks + 6) * 50L);
        ClickSignals.lockLeft(player, ticks + 2);
        world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.5f, 1.4f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= ticks) {
                    cancel();
                    return;
                }
                age++;
                player.setFallDistance(0.0f);
                player.setVelocity(facing.clone().multiply(power).setY(Math.min(0.0, player.getVelocity().getY())));
                // a big shield in front: a disc standing across the way
                Location front = player.getLocation().add(facing.clone().multiply(1.6)).add(0.0, 1.1, 0.0);
                for (int ring = 0; ring < 3; ring++) {
                    double r = 0.6 + ring * 0.5;
                    for (int index = 0; index < 14 + ring * 6; index++) {
                        double angle = Math.PI * 2.0 * index / (14 + ring * 6);
                        Location at = front.clone().add(side.clone().multiply(Math.cos(angle) * r)).add(0.0, Math.sin(angle) * r * 1.1, 0.0);
                        ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(ring == 2 ? color : IRON, 1.5f));
                    }
                }
                ParticleFx.to(player, world).spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 0.2, 0.0), 3, 0.3, 0.05, 0.3, 0.02);
                for (LivingEntity target : around(player, front, 2.2)) {
                    Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector());
                    // pushed off to the side it is on, and along
                    double across = to.dot(side);
                    target.setVelocity(side.clone().multiply(Math.signum(across == 0.0 ? 1.0 : across) * 0.6).add(facing.clone().multiply(0.7)).setY(0.2));
                    if (struck.add(target.getUniqueId()) && strike(player, target, weapon, rate)) {
                        world.playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0f, 0.7f);
                        ParticleFx.to(player, world).spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.4);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
