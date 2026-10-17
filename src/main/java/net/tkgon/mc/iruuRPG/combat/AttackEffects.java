package net.tkgon.mc.iruuRPG.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AttackEffects {

    private final JavaPlugin plugin;
    private final SlashVfx slashVfx;
    private final AttackSounds sounds;
    /** Alternates the tilt of a player's basic slashes (left-handed / right-handed look). */
    private final Map<UUID, Boolean> slashSide = new ConcurrentHashMap<>();

    public AttackEffects(JavaPlugin plugin, SoundSettings soundSettings) {
        this.plugin = plugin;
        this.slashVfx = new SlashVfx(plugin);
        this.sounds = new AttackSounds(plugin, soundSettings);
    }

    public AttackSounds sounds() {
        return sounds;
    }

    public void close() {
        slashVfx.close();
    }

    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon) {
        if (!plugin.getConfig().getBoolean("combat.effects.melee-slash.enabled", true)) return;

        sounds.swing(attacker, attacker.getLocation().add(0.0, 1.0, 0.0), weapon.element());
        Vector forward = horizontalForward(attacker);
        boolean flip = slashSide.merge(attacker.getUniqueId(), Boolean.TRUE, (old, ignored) -> !old);
        Location origin = attacker.getLocation().add(forward.clone().multiply(0.7)).add(0.0, 1.0, 0.0);
        Element element = weapon.element();
        // a flat crescent swept across in front of the player (leaning a little up / down on alternate swings)
        slash(origin, element, new SlashVfx.Shape(1.3, 120.0, 0.0, 0.0,
                attacker.getLocation().getYaw(), flip ? 78.0 : 102.0, 0.0, vfxDensity()), 0.14, 1.0f, 0.0);
    }

    public void playSweepMelee(Player attacker, RpgItemDefinition weapon) {
        Vector forward = horizontalForward(attacker);
        float yaw = attacker.getLocation().getYaw();
        Element element = weapon.element();
        sounds.sweepMelee(attacker, attacker.getLocation().add(0.0, 1.0, 0.0), element);
        Location base = attacker.getLocation().add(forward.clone().multiply(0.8)).add(0.0, 1.0, 0.0);

        // a wide horizontal crescent swept from the right side to the left, then a smaller one right behind it
        slash(base, element, new SlashVfx.Shape(2.8, 140.0, 0.0, 0.0, yaw, 90.0, 0.0, vfxDensity()), 0.22, 1.15f, 0.0);
        slash(base.clone().add(0.0, 0.35, 0.0), element,
                new SlashVfx.Shape(2.0, 130.0, 0.0, 0.0, yaw, 90.0, 0.0, vfxDensity()), 0.18, 0.9f, 0.06);

        World world = attacker.getWorld();
        Location tip = base.clone().add(forward.clone().multiply(2.6));
        burst(world, tip, element, 10, 0.5, 0.05);
    }

    public void playRangeTrail(Player attacker, RpgItemDefinition weapon, Location hitLocation) {
        if (!plugin.getConfig().getBoolean("combat.effects.range-trail.enabled", true)) return;
        if (hitLocation == null || hitLocation.getWorld() == null) return;

        Location start = attacker.getEyeLocation().add(
                attacker.getEyeLocation().getDirection().normalize().multiply(
                        plugin.getConfig().getDouble("combat.effects.range-trail.start-offset", 0.65)
                )
        );
        Vector path = hitLocation.toVector().subtract(start.toVector());
        double length = path.length();
        if (length <= 1.0E-9) return;

        sounds.rangeShot(attacker, start, weapon.element());
        World world = hitLocation.getWorld();
        Vector stepDirection = path.normalize();
        ParticlePalette palette = palette(weapon.element());
        double spacing = Math.max(0.1, plugin.getConfig().getDouble("combat.effects.range-trail.spacing", 0.55));
        int maxPoints = Math.max(1, plugin.getConfig().getInt("combat.effects.range-trail.max-points", 30));
        int points = Math.min(maxPoints, Math.max(2, (int) Math.ceil(length / spacing)));

        for (int index = 0; index <= points; index++) {
            double distance = length * index / points;
            Location point = start.clone().add(stepDirection.clone().multiply(distance));

            spawnDust(world, point, palette.primary());
            if (index % 3 == 0) {
                spawnDust(world, point.clone().add(0.0, 0.035, 0.0), palette.secondary());
            }
            if (index % 6 == 0) {
                spawnDust(world, point.clone().subtract(0.0, 0.025, 0.0), palette.accent());
            }
            if (index % 4 == 0) {
                spark(world, point, weapon.element());
            }
        }
    }

    /**
     * A piece of a flying bolt (used by mobs' shots): the same dust line as a player's ranged trail, drawn for the
     * stretch the bolt moved this tick, in the colors of {@code element}.
     */
    public void playBoltSegment(Location from, Location to, Element element) {
        if (from == null || to == null || from.getWorld() == null) return;
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length <= 1.0E-9) return;

        World world = from.getWorld();
        Vector direction = path.normalize();
        ParticlePalette palette = palette(element);
        int points = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            spawnDust(world, point, palette.primary());
            if (index % 2 == 0) spawnDust(world, point.clone().add(0.0, 0.035, 0.0), palette.secondary());
            if (index % 4 == 0) spawnDust(world, point.clone().subtract(0.0, 0.025, 0.0), palette.accent());
            if (index % 3 == 0) spark(world, point, element);
        }
    }

    public void playBoltImpact(Location hitLocation, Element element) {
        impact(null, hitLocation, element);
    }

    public void playRangeImpact(Player attacker, Location hitLocation, RpgItemDefinition weapon) {
        if (hitLocation == null || hitLocation.getWorld() == null) return;
        impact(attacker, hitLocation, weapon.element());
    }

    private void impact(Player owner, Location hitLocation, Element element) {
        if (!plugin.getConfig().getBoolean("combat.effects.range-impact.enabled", true)) return;
        if (hitLocation == null || hitLocation.getWorld() == null) return;

        World world = hitLocation.getWorld();
        sounds.impact(owner, hitLocation, element);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ParticlePalette palette = palette(element);
        int particles = Math.max(1, plugin.getConfig().getInt("combat.effects.range-impact.particles", 12));
        double radius = Math.max(0.05, plugin.getConfig().getDouble("combat.effects.range-impact.radius", 0.45));

        for (int index = 0; index < particles; index++) {
            Vector offset = randomUnitVector(random).multiply(random.nextDouble(0.12, radius));
            Location point = hitLocation.clone().add(offset);
            Particle.DustOptions dust = switch (index % 3) {
                case 1 -> palette.secondary();
                case 2 -> palette.accent();
                default -> palette.primary();
            };

            spawnDust(world, point, dust);
        }
        burst(world, hitLocation, element, 6, 0.15, 0.08);
    }

    public void playSweepRangeWave(Player player, RpgItemDefinition weapon, double radius) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0.0, 0.25, 0.0);
        ParticlePalette palette = palette(weapon.element());
        sounds.sweepWave(player, center, weapon.element());

        int rings = 3;
        int points = 36;
        for (int ring = 1; ring <= rings; ring++) {
            double currentRadius = radius * ring / rings;
            double height = 0.08 + ring * 0.16;
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2.0 * index / points;
                Location point = center.clone().add(
                        Math.cos(angle) * currentRadius,
                        height + Math.sin(angle * 4.0) * 0.08,
                        Math.sin(angle) * currentRadius
                );
                Particle.DustOptions dust = switch ((index + ring) % 3) {
                    case 1 -> palette.secondary();
                    case 2 -> palette.accent();
                    default -> palette.primary();
                };
                spawnDust(world, point, dust);
                if (index % 6 == 0) spark(world, point, weapon.element());
            }
        }
    }

    public void playRangeHurt(LivingEntity victim) {
        if (!plugin.getConfig().getBoolean("combat.effects.range-hurt.enabled", true)) return;

        if (!victim.isDead() && victim.isValid()) {
            victim.playHurtAnimation(victim.getLocation().getYaw());
        }

        Location center = victim.getLocation().add(0.0, Math.max(0.45, victim.getHeight() * 0.55), 0.0);
        World world = center.getWorld();
        if (world == null) return;

        int damageParticles = Math.max(0, plugin.getConfig().getInt("combat.effects.range-hurt.damage-indicators", 5));
        if (damageParticles > 0) {
            world.spawnParticle(Particle.DAMAGE_INDICATOR, center, damageParticles, 0.22, 0.18, 0.22, 0.0);
        }

        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 45, 45), 0.85f);
        int dustParticles = Math.max(0, plugin.getConfig().getInt("combat.effects.range-hurt.red-dust", 5));
        for (int index = 0; index < dustParticles; index++) {
            double angle = Math.PI * 2.0 * index / Math.max(1, dustParticles);
            Location point = center.clone().add(Math.cos(angle) * 0.22, 0.02 * index, Math.sin(angle) * 0.22);
            spawnDust(world, point, red);
        }
    }

    public void playDamageNumber(Player attacker, LivingEntity target, RpgItemDefinition weapon, double damage, boolean critical) {
        if (target == null || damage <= 0.0) return;

        Element element = weapon != null ? weapon.element() : Element.WHITE;
        String prefix = critical ? "✧" : "";
        sounds.hit(attacker, target.getLocation().add(0.0, Math.max(0.5, target.getHeight() * 0.5), 0.0), element, critical);
        spawnFloatingText(target.getLocation(), target.getHeight(), Component.text(criticalPrefix(critical) + formatFloating(damage), textColor(element)));
    }

    public void playDamageNumber(Player attacker, Location targetLocation, double targetHeight, RpgItemDefinition weapon, double damage, boolean critical) {
        if (targetLocation == null || targetLocation.getWorld() == null || damage <= 0.0) return;

        Element element = weapon != null ? weapon.element() : Element.WHITE;
        String prefix = critical ? "✧" : "";
        sounds.hit(attacker, targetLocation.clone().add(0.0, Math.max(0.5, targetHeight * 0.5), 0.0), element, critical);
        spawnFloatingText(targetLocation, targetHeight, Component.text(criticalPrefix(critical) + formatFloating(damage), textColor(element)));
    }

    /** The number of an extra hit that comes with an attack (the assassin's orange damage, the healer's pulse, ...). */
    public void playExtraDamageNumber(Location targetLocation, double targetHeight, Element element, double damage) {
        if (targetLocation == null || targetLocation.getWorld() == null || damage <= 0.0) return;

        spawnFloatingText(targetLocation, targetHeight + 0.25,
                Component.text("\u2726" + formatFloating(damage), textColor(element)));
    }

    // ---- enemy skills ---------------------------------------------------------------------------------------

    /** The ground mark of a coming ground slam: a ring (and a fainter inner one) in the element's color. */
    public void playMobTelegraphRing(Location center, double radius, Element element) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(mix(baseColor(element), Color.RED, 0.35), 1.1f);
        int points = (int) Math.max(24, Math.min(64, Math.round(radius * 9)));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
            spawnDust(world, point, dust);
            if (index % 2 == 0) {
                spawnDust(world, center.clone().add(Math.cos(angle) * radius * 0.5, 0.12, Math.sin(angle) * radius * 0.5), dust);
            }
            if (index % 8 == 0) spark(world, point, element);
        }
    }

    /** The ground mark of a coming sweep: the edge of a fan in front of the mob. */
    public void playMobTelegraphArc(Location origin, float yaw, double radius, double sweepDegrees, Element element) {
        if (origin == null || origin.getWorld() == null) return;
        World world = origin.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(mix(baseColor(element), Color.RED, 0.35), 1.1f);
        double half = Math.toRadians(sweepDegrees / 2.0);
        int points = (int) Math.max(12, Math.min(40, Math.round(radius * Math.toDegrees(half * 2) / 12.0)));
        double yawRad = Math.toRadians(yaw);
        for (int index = 0; index <= points; index++) {
            double angle = -half + 2.0 * half * index / points;
            Vector direction = rotateAroundY(new Vector(0.0, 0.0, 1.0), yawRad + angle);
            spawnDust(world, origin.clone().add(direction.multiply(radius)).add(0.0, 0.12, 0.0), dust);
        }
        for (double edge : new double[]{-half, half}) {
            for (int step = 1; step <= 4; step++) {
                Vector direction = rotateAroundY(new Vector(0.0, 0.0, 1.0), yawRad + edge);
                spawnDust(world, origin.clone().add(direction.multiply(radius * step / 4.0)).add(0.0, 0.12, 0.0), dust);
            }
        }
    }

    /** The mark of a coming dash: a line from the mob to where it will end up. */
    public void playMobTelegraphLine(Location from, Location to, Element element) {
        if (from == null || to == null || from.getWorld() == null) return;
        World world = from.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(mix(baseColor(element), Color.RED, 0.35), 1.1f);
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length <= 1.0E-6) return;
        Vector step = path.normalize().multiply(0.5);
        Location point = from.clone().add(0.0, 0.15, 0.0);
        for (double travelled = 0.0; travelled <= length; travelled += 0.5) {
            spawnDust(world, point, dust);
            point.add(step);
        }
    }

    /** The slam itself: a burst out of the ground, a shock ring and sparks. */
    public void playMobStrikeBurst(Location center, double radius, Element element) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        sounds.mobStrike(center, element);
        ParticlePalette palette = palette(element);
        int points = (int) Math.max(24, Math.min(72, Math.round(radius * 12)));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location edge = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            spawnDust(world, edge, palette.primary());
            if (index % 3 == 0) {
                for (int step = 1; step <= 4; step++) {
                    spawnDust(world, edge.clone().add(0.0, step * 0.3, 0.0), step % 2 == 0 ? palette.secondary() : palette.accent());
                }
            }
            if (index % 6 == 0) spark(world, edge.clone().add(0.0, 0.4, 0.0), element);
        }
        burst(world, center.clone().add(0.0, 0.3, 0.0), element, 16, radius * 0.3, 0.15);
    }

    /** The cut itself: a wide flat crescent through the fan. */
    public void playMobSweep(Location origin, float yaw, double radius, Element element) {
        if (origin == null || origin.getWorld() == null) return;
        sounds.mobSweep(origin, element);
        slash(origin.clone().add(0.0, 0.9, 0.0), element,
                new SlashVfx.Shape(radius * 0.8, 130.0, 0.0, 0.0, yaw, 90.0, 0.0, vfxDensity()), 0.2, 1.2f, 0.0);
    }

    private static Vector rotateAroundY(Vector vector, double radians) {
        // same convention as a mob's yaw: 0 = +z, positive turns toward -x
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(vector.getX() * cos - vector.getZ() * sin, vector.getY(), vector.getX() * sin + vector.getZ() * cos);
    }

    public void playHealNumber(Player target, double amount) {
        if (target == null || target.isDead() || !target.isValid() || amount <= 0.0) return;

        spawnFloatingText(target, Component.text("+" + formatFloating(amount), TextColor.color(75, 235, 95)));
    }

    public void playStatusDamageNumber(Location targetLocation, double targetHeight, StatusEffectType type, double damage) {
        if (targetLocation == null || targetLocation.getWorld() == null || type == null || damage <= 0.0) return;

        spawnFloatingText(targetLocation, targetHeight, Component.text(type.icon() + formatFloating(damage), TextColor.color(190, 80, 255)));
    }

    public void playStatusDamageNumber(LivingEntity target, StatusEffectType type, double damage) {
        if (target == null || target.isDead() || !target.isValid() || type == null || damage <= 0.0) return;

        spawnFloatingText(target, Component.text(type.icon() + formatFloating(damage), TextColor.color(190, 80, 255)));
    }

    public void playDeployRing(Location center, RpgItemDefinition weapon, double radius) {
        if (!plugin.getConfig().getBoolean("combat.effects.deploy-ring.enabled", true)) return;
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        ParticlePalette palette = palette(weapon.element());
        int points = Math.max(72, plugin.getConfig().getInt("combat.effects.deploy-ring.points", 72));
        double y = plugin.getConfig().getDouble("combat.effects.deploy-ring.height", 0.15);

        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            Particle.DustOptions dust = switch (index % 3) {
                case 1 -> palette.secondary();
                case 2 -> palette.accent();
                default -> palette.primary();
            };
            spawnDust(world, point, dust);
            if (index % 2 == 0) {
                spawnDust(world, point, palette.secondary());
            }
            if (index % 6 == 0) {
                spawnDust(world, point, palette.accent());
                spark(world, point.clone().add(0.0, 0.1, 0.0), weapon.element());
            }
        }
    }

    public void playDeployPulse(Location center, RpgItemDefinition weapon, double radius) {
        if (!plugin.getConfig().getBoolean("combat.effects.deploy-pulse.enabled", true)) return;
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        ParticlePalette palette = palette(weapon.element());
        int points = Math.max(42, plugin.getConfig().getInt("combat.effects.deploy-pulse.points", 42));
        double pulseRadius = Math.max(0.2, radius);

        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            double wave = Math.sin(angle * 3.0) * 0.18;
            Location point = center.clone().add(Math.cos(angle) * pulseRadius, 0.7 + wave, Math.sin(angle) * pulseRadius);
            Particle.DustOptions dust = switch (index % 3) {
                case 1 -> palette.secondary();
                case 2 -> palette.accent();
                default -> palette.primary();
            };
            spawnDust(world, point, dust);
            if (index % 2 == 0) {
                spawnDust(world, point.clone().add(0.0, 0.16, 0.0), palette.secondary());
            }
            if (index % 5 == 0) {
                spawnDust(world, point.clone().subtract(0.0, 0.12, 0.0), palette.accent());
                spark(world, point, weapon.element());
            }
        }
    }

    public void playDeploySweep(Player owner, Location center, RpgItemDefinition weapon, double radius) {
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        ParticlePalette palette = palette(weapon.element());
        sounds.deploySweep(owner, center, weapon.element());
        int particles = 46;
        double arcRadians = Math.toRadians(120.0);
        double sweepRadius = Math.max(1.0, radius * 0.55);

        for (int index = 0; index < particles; index++) {
            double progress = particles == 1 ? 0.5 : (double) index / (particles - 1);
            double angle = -arcRadians / 2.0 + arcRadians * progress;
            Location point = center.clone().add(
                    Math.cos(angle) * sweepRadius,
                    0.65 + progress * 0.55,
                    Math.sin(angle) * sweepRadius
            );
            spawnDust(world, point, palette.primary());
            if (index % 2 == 0) {
                spawnDust(world, point.clone().add(0.0, 0.06, 0.0), palette.secondary());
            }
            if (index % 5 == 0) {
                spawnDust(world, point.clone().subtract(0.0, 0.05, 0.0), palette.accent());
                spark(world, point, weapon.element());
            }
        }
    }

    public void playHealBurst(Player player, RpgItemDefinition weapon) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0.0, Math.max(0.7, player.getHeight() * 0.55), 0.0);
        ParticlePalette palette = palette(weapon.element());
        sounds.healBurst(player, center);

        for (int index = 0; index < 18; index++) {
            double angle = Math.PI * 2.0 * index / 18.0;
            double y = 0.25 + Math.sin(angle * 2.0) * 0.18;
            Location point = center.clone().add(Math.cos(angle) * 0.7, y, Math.sin(angle) * 0.7);
            spawnDust(world, point, index % 2 == 0 ? palette.primary() : palette.secondary());
            if (index % 3 == 0) spark(world, point, weapon.element());
        }
        world.spawnParticle(Particle.HEART, center.clone().add(0.0, 0.45, 0.0), 2, 0.25, 0.18, 0.25, 0.0);
    }

    public void playHealRing(Player owner, Location center, RpgItemDefinition weapon, double radius) {
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        ParticlePalette palette = palette(weapon.element());
        sounds.healRing(owner, center);
        int points = 64;
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.22, Math.sin(angle) * radius);
            Particle.DustOptions dust = switch (index % 3) {
                case 1 -> palette.secondary();
                case 2 -> palette.accent();
                default -> palette.primary();
            };
            spawnDust(world, point, dust);
            if (index % 3 == 0) {
                spawnDust(world, point.clone().add(0.0, 0.18, 0.0), palette.secondary());
                spark(world, point.clone().add(0.0, 0.3, 0.0), weapon.element());
            }
        }
    }

    public void playDashBurst(Player player, RpgItemDefinition weapon) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0.0, 0.35, 0.0);
        Vector backward = horizontalForward(player).multiply(-1.0);
        ParticlePalette palette = palette(weapon.element());
        sounds.dash(player, center, weapon.element());

        for (int index = 0; index < 14; index++) {
            double spread = (index - 6.5) / 6.5;
            Vector side = new Vector(-backward.getZ(), 0.0, backward.getX()).multiply(spread * 0.45);
            Location point = center.clone().add(backward.clone().multiply(0.25 + index * 0.08)).add(side);
            spawnDust(world, point, index % 2 == 0 ? palette.primary() : palette.secondary());
            if (index % 2 == 0) spark(world, point, weapon.element());
        }
    }

    public void playStrikeMeleeBurst(Player player, RpgItemDefinition weapon, double radius) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0.0, 0.65, 0.0);
        ParticlePalette palette = palette(weapon.element());

        for (int index = 0; index < 28; index++) {
            double angle = Math.PI * 2.0 * index / 28.0;
            double currentRadius = radius * (0.45 + (index % 3) * 0.18);
            Location point = center.clone().add(Math.cos(angle) * currentRadius, 0.18 * (index % 4), Math.sin(angle) * currentRadius);
            spawnDust(world, point, index % 2 == 0 ? palette.primary() : palette.accent());
            if (index % 2 == 0) spark(world, point, weapon.element());
        }
        burst(world, center, weapon.element(), 14, radius * 0.4, 0.12);
    }

    public void playStrikeRangeCharge(Player owner, Location center, Vector direction, RpgItemDefinition weapon) {
        if (center == null || center.getWorld() == null) return;
        sounds.strikeCharge(owner, center);
        spawnStrikeCylinder(center.getWorld(), center, direction, weapon, 0.75, 3, 18);
    }

    public void playStrikeRangeBolt(Location center, Vector direction, RpgItemDefinition weapon) {
        if (center == null || center.getWorld() == null) return;
        spawnStrikeCylinder(center.getWorld(), center, direction, weapon, 0.55, 2, 14);
    }

    public void playSlashMelee(Player attacker, RpgItemDefinition weapon, LivingEntity target, int sequence) {
        if (attacker == null || weapon == null) return;

        Vector forward = target == null
                ? horizontalForward(attacker)
                : target.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0.0);
        if (forward.lengthSquared() <= 1.0E-9) {
            forward = horizontalForward(attacker);
        } else {
            forward.normalize();
        }

        Location center = target == null
                ? attacker.getLocation().add(forward.clone().multiply(2.25)).add(0.0, 1.15, 0.0)
                : target.getLocation().add(0.0, Math.max(0.75, target.getHeight() * 0.58), 0.0);
        // pull the arc a little toward the attacker so it is drawn around the target instead of behind it
        center.subtract(forward.clone().multiply(0.35));
        double yaw = Math.toDegrees(Math.atan2(-forward.getX(), forward.getZ()));
        Element element = weapon.element();
        sounds.slashCut(attacker, center, element);

        // two diagonal cuts that cross each other like an X, the second one a moment after the first
        slash(center, element, new SlashVfx.Shape(2.2, 70.0, 0.0, 0.0, yaw, 0.0, 45.0, vfxDensity()), 0.1, 1.0f, 0.0);
        slash(center, element, new SlashVfx.Shape(2.2, 70.0, 0.0, 0.0, yaw, 0.0, -45.0, vfxDensity()), 0.1, 1.0f, 0.1);
        if (target != null) {
            burst(attacker.getWorld(), center.clone().add(forward.clone().multiply(0.4)), element, 8, 0.3, 0.1);
        }
    }

    public void playSlashProjectile(Location center, Vector direction, RpgItemDefinition weapon) {
        if (center == null || center.getWorld() == null || weapon == null) return;

        Vector axis = direction.clone();
        if (axis.lengthSquared() <= 1.0E-9) {
            axis = new Vector(0.0, 0.0, 1.0);
        } else {
            axis.normalize();
        }
        double yaw = Math.toDegrees(Math.atan2(-axis.getX(), axis.getZ()));
        double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, axis.getY()))));

        // a crescent that bulges the way it flies; drawn each step so the steps leave a trail
        slash(center, weapon.element(), new SlashVfx.Shape(0.8, 150.0, 0.0, 0.0, yaw, 90.0 - elevation, 0.0, Math.max(3, vfxDensity() - 1)),
                0.05, 1.0f, 0.0);
    }

    public void playSlashImpact(Player owner, LivingEntity target, RpgItemDefinition weapon) {
        if (target == null || target.isDead() || !target.isValid() || weapon == null) return;

        playRangeHurt(target);
        World world = target.getWorld();
        Location center = target.getLocation().add(0.0, Math.max(0.75, target.getHeight() * 0.58), 0.0);
        ParticlePalette palette = palette(weapon.element());
        sounds.slashImpact(owner, center, weapon.element());
        for (int index = 0; index < 18; index++) {
            double angle = Math.PI * 2.0 * index / 18.0;
            Location point = center.clone().add(Math.cos(angle) * 0.45, Math.sin(angle * 2.0) * 0.18, Math.sin(angle) * 0.45);
            spawnDust(world, point, index % 3 == 0 ? palette.accent() : palette.primary());
        }
        burst(world, center, weapon.element(), 16, 0.25, 0.14);
    }

    public void playDeployDashFinish(Player owner, Location center, RpgItemDefinition weapon, double radius) {
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        ParticlePalette palette = palette(weapon.element());
        sounds.deployDashFinish(owner, center, weapon.element());
        int spokes = 18;
        int steps = 8;
        for (int spoke = 0; spoke < spokes; spoke++) {
            double angle = Math.PI * 2.0 * spoke / spokes;
            Vector inward = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
            for (int step = 0; step < steps; step++) {
                double currentRadius = radius * (1.0 - (double) step / steps);
                Location point = center.clone().add(inward.clone().multiply(currentRadius)).add(0.0, 0.45 + step * 0.06, 0.0);
                spawnDust(world, point, step % 2 == 0 ? palette.primary() : palette.secondary());
                if (step % 3 == 0) {
                    spawnDust(world, point.clone().add(0.0, 0.12, 0.0), palette.accent());
                    if (spoke % 2 == 0) spark(world, point, weapon.element());
                }
            }
        }
    }

    public void playDeployStrikeCrush(Player owner, Location center, RpgItemDefinition weapon, double radius) {
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        ParticlePalette palette = palette(weapon.element());
        sounds.deployCrush(owner, center, weapon.element());
        int columns = 24;
        for (int column = 0; column < columns; column++) {
            double angle = Math.PI * 2.0 * column / columns;
            double currentRadius = radius * (column % 4 + 1) / 4.0;
            Location base = center.clone().add(Math.cos(angle) * currentRadius, 0.0, Math.sin(angle) * currentRadius);
            for (int step = 0; step < 10; step++) {
                Location point = base.clone().add(0.0, 3.4 - step * 0.36, 0.0);
                spawnDust(world, point, step % 2 == 0 ? palette.primary() : palette.accent());
                if (step % 2 == 0) {
                    spawnDust(world, point.clone().add(0.0, 0.08, 0.0), palette.secondary());
                    if (column % 3 == 0) spark(world, point, weapon.element());
                }
            }
        }
    }

    private void spawnStrikeCylinder(World world, Location center, Vector direction, RpgItemDefinition weapon, double radius, int slices, int points) {
        Vector axis = direction.clone();
        if (axis.lengthSquared() <= 1.0E-9) {
            axis = new Vector(0.0, 0.0, 1.0);
        } else {
            axis.normalize();
        }
        Vector right = new Vector(-axis.getZ(), 0.0, axis.getX());
        if (right.lengthSquared() <= 1.0E-9) {
            right = new Vector(1.0, 0.0, 0.0);
        } else {
            right.normalize();
        }
        Vector up = new Vector(0.0, 1.0, 0.0);
        ParticlePalette palette = palette(weapon.element());

        for (int slice = 0; slice < slices; slice++) {
            double along = (slice - (slices - 1) / 2.0) * 0.45;
            Location sliceCenter = center.clone().add(axis.clone().multiply(along));
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2.0 * index / points;
                Location point = sliceCenter.clone()
                        .add(right.clone().multiply(Math.cos(angle) * radius))
                        .add(up.clone().multiply(Math.sin(angle) * radius));
                Particle.DustOptions dust = switch ((index + slice) % 3) {
                    case 1 -> palette.secondary();
                    case 2 -> palette.accent();
                    default -> palette.primary();
                };
                spawnDust(world, point, dust);
                if (index % 5 == 0) spark(world, point, weapon.element());
            }
        }
    }

    private int vfxDensity() {
        return Math.max(2, plugin.getConfig().getInt("slash-vfx.samples-per-block", 5));
    }

    /**
     * Draws one slash arc in the element's colors: a wide dim glow, the coloured body, a bright core, some sparks
     * and a spark at the leading tip. {@code delaySeconds} holds the arc back (for follow-up cuts).
     */
    private void slash(Location origin, Element element, SlashVfx.Shape shape, double duration, float scale, double delaySeconds) {
        if (origin == null || origin.getWorld() == null) return;

        Color base = baseColor(element);
        Particle spark = sparkParticle(element);
        boolean full = plugin.getConfig().getBoolean("slash-vfx.enabled", true);
        SlashVfx.Layer core = layer(Particle.REDSTONE, mix(base, Color.WHITE, 0.75), 0.6f * scale, 1, 0.0, 1);
        SlashVfx.Layer[] layers = full
                ? new SlashVfx.Layer[]{
                        layer(Particle.REDSTONE, mix(base, Color.WHITE, 0.35), 0.7f * scale, 2, 0.16, 2),
                        layer(Particle.REDSTONE, base, 0.8f * scale, 2, 0.09, 1),
                        core,
                        layer(spark, null, 1f, 1, 0.0, 4)}
                : new SlashVfx.Layer[]{core};
        SlashVfx.Layer tip = full ? layer(spark, null, 1f, 2, 0.0, 1) : null;

        if (delaySeconds > 0.0) {
            long delayTicks = Math.max(1L, Math.round(delaySeconds * 20.0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> slashVfx.play(origin, layers, duration, shape, tip), delayTicks);
        } else {
            slashVfx.play(origin, layers, duration, shape, tip);
        }
    }

    private SlashVfx.Layer layer(Particle particle, Color color, float size, int thickness, double spacing, int stride) {
        return new SlashVfx.Layer(particle, color, size, thickness, spacing, 0.0, 0.0, 0.0, 0.0, 10.0, stride);
    }

    /** The sparkle particle that goes with an element. */
    private Particle sparkParticle(Element element) {
        return switch (element) {
            case RED -> Particle.FLAME;
            case BLUE -> Particle.SOUL_FIRE_FLAME;
            case WHITE -> Particle.END_ROD;
            case GREEN -> Particle.VILLAGER_HAPPY;
            case ORANGE -> Particle.CRIT;
        };
    }

    /** A little burst of the element's sparks flying out of {@code point}. */
    private void burst(World world, Location point, Element element, int count, double spread, double speed) {
        if (world == null || point == null) return;
        if (!plugin.getConfig().getBoolean("slash-vfx.enabled", true)) return;
        world.spawnParticle(sparkParticle(element), point, count, spread, spread, spread, speed);
    }

    /** One spark at {@code point} (used to dress up rings and lines). */
    private void spark(World world, Location point, Element element) {
        if (!plugin.getConfig().getBoolean("slash-vfx.enabled", true)) return;
        world.spawnParticle(sparkParticle(element), point, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private Vector horizontalForward(Player attacker) {
        Vector forward = attacker.getLocation().getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 1.0E-9) {
            return new Vector(0.0, 0.0, 1.0);
        }

        return forward.normalize();
    }

    private Vector randomUnitVector(ThreadLocalRandom random) {
        double theta = random.nextDouble(0.0, Math.PI * 2.0);
        double y = random.nextDouble(-0.45, 0.75);
        double xz = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vector(Math.cos(theta) * xz, y, Math.sin(theta) * xz).normalize();
    }

    private void spawnDust(World world, Location point, Particle.DustOptions dust) {
        world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
    }

    private void spawnFloatingText(LivingEntity target, Component text) {
        spawnFloatingText(target.getLocation(), target.getHeight(), text);
    }

    private void spawnFloatingText(Location targetLocation, double targetHeight, Component text) {
        World world = targetLocation.getWorld();
        if (world == null) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location location = targetLocation.clone().add(
                random.nextDouble(-0.45, 0.45),
                Math.max(0.75, targetHeight) + random.nextDouble(0.18, 0.45),
                random.nextDouble(-0.45, 0.45)
        );

        TextDisplay display = world.spawn(location, TextDisplay.class, spawned -> {
            spawned.text(text);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(true);
            spawned.setShadowed(true);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setTextOpacity((byte) -1);
            spawned.setLineWidth(120);
            spawned.setViewRange(24.0f);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(1);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
        });

        new BukkitRunnable() {
            private int ageTicks;

            @Override
            public void run() {
                if (!display.isValid() || ageTicks >= 20) {
                    display.remove();
                    cancel();
                    return;
                }

                display.teleport(display.getLocation().add(0.0, 0.025, 0.0));
                ageTicks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private ParticlePalette palette(Element element) {
        Color base = baseColor(element);
        float size = (float) plugin.getConfig().getDouble("combat.effects.particle-size", 0.85);
        return new ParticlePalette(
                new Particle.DustOptions(base, size),
                new Particle.DustOptions(mix(base, Color.WHITE, 0.28), size * 0.85f),
                new Particle.DustOptions(mix(base, Color.BLACK, 0.18), size * 0.75f)
        );
    }

    private Color baseColor(Element element) {
        return switch (element) {
            case RED -> Color.fromRGB(255, 55, 45);
            case BLUE -> Color.fromRGB(70, 130, 255);
            case WHITE -> Color.fromRGB(245, 245, 245);
            case GREEN -> Color.fromRGB(45, 220, 95);
            case ORANGE -> Color.fromRGB(255, 155, 35);
        };
    }

    private TextColor textColor(Element element) {
        Color color = baseColor(element);
        return TextColor.color(color.getRed(), color.getGreen(), color.getBlue());
    }

    private String criticalPrefix(boolean critical) {
        return critical ? "\u2727" : "";
    }

    private String formatFloating(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000.0) {
            return compactUnit(value / 1_000_000.0, "M");
        }
        if (absolute >= 1_000.0) {
            return compactUnit(value / 1_000.0, "K");
        }

        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }

    private String compactUnit(double value, String unit) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-9) {
            return (long) Math.rint(rounded) + unit;
        }
        return String.format(Locale.US, "%.1f%s", rounded, unit);
    }

    private Color mix(Color left, Color right, double rightWeight) {
        double clamped = Math.max(0.0, Math.min(1.0, rightWeight));
        double leftWeight = 1.0 - clamped;
        return Color.fromRGB(
                clampColor(left.getRed() * leftWeight + right.getRed() * clamped),
                clampColor(left.getGreen() * leftWeight + right.getGreen() * clamped),
                clampColor(left.getBlue() * leftWeight + right.getBlue() * clamped)
        );
    }

    private int clampColor(double value) {
        return (int) Math.max(0.0, Math.min(255.0, Math.round(value)));
    }

    private record ParticlePalette(
            Particle.DustOptions primary,
            Particle.DustOptions secondary,
            Particle.DustOptions accent
    ) {
    }
}
