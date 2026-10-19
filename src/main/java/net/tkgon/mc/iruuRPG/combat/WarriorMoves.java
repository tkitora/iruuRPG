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
 * (90%), 3 a sideways slash (120%) - these three are drawn by the class's {@code combo-style}. 4 in the air a downward
 * chop (150%) that throws the enemies back, on the ground a thrust (100%). 5 after the chop in the air a thrust (150%)
 * that finds the enemies thrown back, otherwise a sweep (100%).
 *
 * <p>Heavy attacks (the number of normal attacks so far): 0 a dash with a blow where it ends (30%, 3 s cooldown), 1 a
 * blow ahead (20%, small knockback), then a leap and a crash on the landing spot (100%), 2 twenty ticks of spinning
 * (10% every 2 ticks, radius 3) ending in a blast (20%, radius 4), 3 a jump that throws the enemies in front up (70%),
 * 4 three big cuts. While 2 or 3 last, normal attacks stop; a heavy click in that time (it would be heavy 0, but is not
 * thrown, and no cooldown) ends 2 early, or, after 3, dives and brings the enemies thrown up down again (70%); within
 * a second of 4 it leaps back and comes down with a crack in the ground ahead that throws the enemies up (120%).
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
        boolean air = !player.isOnGround() || player.getVelocity().getY() > 0.1;
        if (step.stage() == 4) {
            if (air) {
                chop(player, weapon);
            } else {
                thrust(player, weapon, number("thrust-rate", 1.0), null);
            }
            return;
        }
        Thrown last = thrown.remove(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last.when() <= (long) (number("follow-seconds", 3.0) * 1000.0)) {
            thrust(player, weapon, number("chase-thrust-rate", 1.5), last.targets());
        } else {
            sweep(player, weapon, number("sweep-rate", 1.0));
        }
    }

    /** 4 in the air: a chop from above that throws what it hits back. */
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
     * A thrust ahead. {@code chase}: the enemies a chop threw back; they are hit for sure when they are within a
     * reasonable distance and in front, and the thrust is drawn toward the nearest of them.
     */
    private void thrust(Player player, RpgItemDefinition weapon, double rate, List<LivingEntity> chase) {
        World world = player.getWorld();
        Vector facing = facing(player);
        Location eye = player.getEyeLocation();
        double length = number("thrust-length", 4.5);
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
        for (LivingEntity target : cone(player, length, 22.0)) {
            if (!targets.contains(target)) targets.add(target);
        }
        Location start = eye.clone().add(aim.clone().multiply(0.8)).subtract(0.0, 0.35, 0.0);
        Location end = start.clone().add(aim.clone().multiply(length));
        Color color = color(weapon.element());
        // a long thin blade of light with a bright tip
        line(player, world, start, end, color, 1.2f, Particle.CRIT, 2);
        line(player, world, start, end, IRON, 0.8f, Particle.END_ROD, 4);
        ParticleFx.to(player, world).spawnParticle(Particle.FLASH, end, 1);
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT, end, 10, 0.2, 0.2, 0.2, 0.3);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.9f, 0.9f);
        world.playSound(end, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.8f, 1.3f);
        // a step into the thrust
        player.setVelocity(aim.clone().setY(0.0).normalize().multiply(0.35).setY(player.getVelocity().getY()));
        for (LivingEntity target : targets) strike(player, target, weapon, rate);
    }

    /** 5 on the ground (or without anything thrown back): a wide sweep. */
    private void sweep(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        double radius = number("sweep-radius", 3.8);
        Location eye = player.getEyeLocation().subtract(0.0, 0.5, 0.0);
        Color color = color(weapon.element());
        // a horizontal arc from the right round to the left, drawn over two ticks
        for (int part = 0; part < 2; part++) {
            int which = part;
            later(which, () -> {
                if (!player.isOnline()) return;
                for (int index = 0; index <= 10; index++) {
                    double angle = Math.toRadians(-100.0 + 200.0 * (which * 11 + index) / 21.0);
                    for (double r = radius - 0.9; r <= radius; r += 0.45) {
                        Location at = eye.clone().add(facing.clone().rotateAroundY(-angle).multiply(r));
                        ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(r > radius - 0.3 ? IRON : color, 1.6f));
                    }
                    if (index % 3 == 0) ParticleFx.to(player, world).spawnParticle(Particle.SWEEP_ATTACK, eye.clone().add(facing.clone().rotateAroundY(-angle).multiply(radius - 0.4)), 1, 0.0, 0.0, 0.0, 0.0);
                }
            });
        }
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.8f, 0.7f);
        for (LivingEntity target : cone(player, radius, 100.0)) {
            if (strike(player, target, weapon, rate)) push(target, player.getLocation().toVector(), 0.4, 0.15);
        }
    }

    // ---- heavy attacks ---------------------------------------------------------------------------------------------------------

    /** Throws heavy attack {@code step.heavyIndex()}. */
    public void heavy(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        // a new heavy attack ends what could have been followed up
        follows.remove(player.getUniqueId());
        switch (step.heavyIndex()) {
            case 0 -> dash(player, weapon, step.damageMultiplier());
            case 1 -> leapAndCrash(player, weapon, step.damageMultiplier());
            case 2 -> spin(player, weapon, step.damageMultiplier());
            case 3 -> launch(player, weapon, step.damageMultiplier());
            default -> tripleCut(player, weapon, step.damageMultiplier());
        }
    }

    // 0: a dash with a blow where it ends
    private void dash(Player player, RpgItemDefinition weapon, double rate) {
        Vector facing = facing(player);
        Location from = player.getLocation().clone();
        int ticks = (int) number("dash-ticks", 6.0);
        double power = number("dash-power", 1.4);
        World world = player.getWorld();
        effects.playStepTrail(player, weapon, from, facing);
        world.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 0.7f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (age >= ticks) {
                    cancel();
                    Location at = player.getLocation();
                    impact(player, world, at, number("dash-radius", 3.0), weapon, 0.8f);
                    for (LivingEntity target : around(player, at, number("dash-radius", 3.0))) {
                        if (strike(player, target, weapon, rate)) push(target, at.toVector(), 0.4, 0.2);
                    }
                    return;
                }
                player.setFallDistance(0.0f);
                player.setVelocity(facing.clone().multiply(power).setY(Math.min(0.0, player.getVelocity().getY())));
                ParticleFx.to(player, world).spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 0.2, 0.0), 2, 0.2, 0.05, 0.2, 0.01);
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // 1: a blow ahead, then a leap and a crash where it lands
    private void leapAndCrash(Player player, RpgItemDefinition weapon, double crashRate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        double jabRate = number("jab-rate", 0.2);
        Location eye = player.getEyeLocation().subtract(0.0, 0.4, 0.0);
        line(player, world, eye.clone().add(facing.clone().multiply(0.6)), eye.clone().add(facing.clone().multiply(3.4)), color(weapon.element()), 1.3f, Particle.CRIT, 2);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.0f);
        for (LivingEntity target : cone(player, 3.6, 45.0)) {
            if (strike(player, target, weapon, jabRate)) target.setVelocity(facing.clone().multiply(number("jab-knockback", 0.35)).setY(0.1));
        }
        later(3L, () -> {
            if (!player.isOnline() || player.isDead()) return;
            player.setFallDistance(0.0f);
            player.setVelocity(facing.clone().multiply(number("leap-power", 0.7)).setY(number("leap-up-power", 0.7)));
            world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 0.6f);
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
                    boolean landed = age > 5 && player.isOnGround();
                    if (age % 2 == 0) ParticleFx.to(player, world).spawnParticle(Particle.CRIT, player.getLocation().add(0.0, 1.0, 0.0), 4, 0.3, 0.4, 0.3, 0.1);
                    if (!landed && age < 30) return;
                    cancel();
                    Location at = player.getLocation();
                    double radius = number("crash-radius", 3.5);
                    impact(player, world, at, radius, weapon, 1.2f);
                    for (LivingEntity target : around(player, at, radius)) {
                        if (strike(player, target, weapon, crashRate)) push(target, at.toVector(), 0.5, 0.3);
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        });
    }

    // 2: twenty ticks of spinning that end in a blast; a heavy click ends it early
    private void spin(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        int ticks = (int) number("spin-ticks", 20.0);
        double radius = number("spin-radius", 3.0);
        double endRadius = number("spin-end-radius", 4.0);
        double endRate = number("spin-end-rate", 0.2);
        Color color = color(weapon.element());
        boolean[] over = {false};
        SafeTask[] holder = new SafeTask[1];
        Runnable finish = () -> {
            if (over[0]) return;
            over[0] = true;
            if (holder[0] != null) holder[0].cancel();
            follows.remove(player.getUniqueId());
            Location at = player.getLocation();
            impact(player, world, at, endRadius, weapon, 1.2f);
            for (LivingEntity target : around(player, at, endRadius)) {
                if (strike(player, target, weapon, endRate)) push(target, at.toVector(), 1.0, 0.4);
            }
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
                    double base = age * 0.9 + arm * Math.PI;
                    for (int step = 0; step < 8; step++) {
                        double angle = base - step * 0.11;
                        for (double r = radius - 0.8; r <= radius; r += 0.4) {
                            Location at = center.clone().add(Math.cos(angle) * r, Math.sin(age * 0.5 + arm) * 0.25, Math.sin(angle) * r);
                            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(step < 2 ? IRON : color, 1.5f));
                        }
                    }
                    ParticleFx.to(player, world).spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(Math.cos(base) * (radius - 0.3), 0.0, Math.sin(base) * (radius - 0.3)), 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (age % 4 == 0) world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 0.9f);
                if (age % 2 == 0) {
                    for (LivingEntity target : around(player, player.getLocation(), radius)) strike(player, target, weapon, rate);
                }
                age++;
            }
        };
        holder[0].runTaskTimer(plugin, 0L, 1L);
    }

    // 3: a jump that throws the enemies in front up; a heavy click dives and brings them down
    private void launch(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        int window = (int) number("launch-window-ticks", 30.0);
        List<LivingEntity> up = new ArrayList<>();
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(0.15).setY(number("launch-jump-power", 0.9)));
        Location eye = player.getEyeLocation();
        // a rising fan of cuts
        for (int index = 0; index < 7; index++) {
            double turn = Math.toRadians(-45.0 + 90.0 * index / 6.0);
            Vector direction = facing.clone().rotateAroundY(turn);
            line(player, world, player.getLocation().add(direction.clone().multiply(0.8)), player.getLocation().add(direction.clone().multiply(3.8)).add(0.0, 1.6, 0.0), color(weapon.element()), 1.3f, Particle.CRIT, 2);
        }
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.7f, 1.3f);
        for (LivingEntity target : cone(player, number("launch-radius", 4.2), 50.0)) {
            if (!strike(player, target, weapon, rate)) continue;
            target.setVelocity(new Vector(0.0, number("launch-power", 1.0), 0.0));
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
}
