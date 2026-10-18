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
        boolean flip = slashSide.merge(attacker.getUniqueId(), Boolean.TRUE, (old, ignored) -> !old);
        Element element = weapon.element();
        // a crescent in front of the player leaning away from them, cut from the upper left to the lower right and
        // from the upper right to the lower left on alternate swings
        cut(attacker, resolve(null, element), 2.0, 0.4, 0.0, 1.7, 120.0, meleeTilt(), flip ? 180.0 : 0.0, 0.14, 1.0f, 0.0);
    }

    /**
     * The tilt (degrees) of a melee cut when nothing else is said: the arc is a sweep lying across the facing
     * direction, turned about the horizontal axis to the player's right (0 = standing up across the way they face,
     * 90 = lying flat). Where the player looks up or down does not matter. {@code combat.effects.melee-slash.tilt}.
     */
    private double meleeTilt() {
        return plugin.getConfig().getDouble("combat.effects.melee-slash.tilt", 45.0);
    }

    /**
     * Draws one arc of a melee cut. Only the way the player faces counts (up and down are ignored): the arc is
     * a sweep across the facing direction, turned about the horizontal axis to the right by {@code tilt}; its middle
     * is {@code forward} blocks ahead, {@code drop} below the eyes and {@code side} to the right. {@code roll}: 0
     * sweeps from the right to the left, 180 from the left to the right.
     */
    private void cut(Player attacker, Resolved look, double forward, double drop, double side, double radius,
                     double sweepDegrees, double tilt, double roll, double duration, float scale, double delaySeconds) {
        Vector f = horizontalForward(attacker);
        Vector right = f.clone().crossProduct(new Vector(0.0, 1.0, 0.0)).normalize();

        // the middle of the arc, to put it (not the point the line starts from) at the wanted spot
        double s = Math.toRadians(sweepDegrees);
        double cr = Math.cos(Math.toRadians(roll));
        double sr = Math.sin(Math.toRadians(roll));
        double sumX = 0.0;
        double sumY = 0.0;
        int samples = 17;
        for (int index = 0; index < samples; index++) {
            double theta = (index / (double) (samples - 1) - 0.5) * s;
            double xl = radius * Math.sin(theta);
            double yl = radius * (Math.cos(theta) - Math.cos(s / 2.0));
            sumX += xl * cr - yl * sr;
            sumY += xl * sr + yl * cr;
        }
        double middleX = sumX / samples;
        double middleY = sumY / samples;
        double t = Math.toRadians(tilt);
        // local x is the player's left, local y turns to (up * cos + forward * sin) with the tilt
        Vector middle = right.clone().multiply(-middleX)
                .add(new Vector(0.0, middleY * Math.cos(t), 0.0))
                .add(f.clone().multiply(middleY * Math.sin(t)));
        Location origin = attacker.getEyeLocation()
                .add(f.clone().multiply(forward)).add(right.clone().multiply(side)).add(0.0, -drop, 0.0)
                .subtract(middle);
        slash(origin, look, new SlashVfx.Shape(radius, sweepDegrees, 0.0, 0.0, attacker.getLocation().getYaw(),
                tilt, roll, vfxDensity()), duration, scale, delaySeconds);
    }

    /** Where each player is in the slash chain (stage 1 - 3) and when they last swung. */
    private final Map<UUID, long[]> comboChain = new ConcurrentHashMap<>();

    /** The chain ends when the player waits longer than this between two swings. */
    private static final long COMBO_WINDOW_MILLIS = 1400L;

    /**
     * The next stage (1, 2, 3, 1, ...) of a player's slash chain: it restarts from 1 after a pause.
     */
    private int nextComboStage(Player attacker) {
        long now = System.currentTimeMillis();
        long[] state = comboChain.computeIfAbsent(attacker.getUniqueId(), ignored -> new long[]{0L, 0L});
        int stage = (now - state[1] > COMBO_WINDOW_MILLIS || state[0] >= 3) ? 1 : (int) state[0] + 1;
        state[0] = stage;
        state[1] = now;
        return stage;
    }

    /**
     * A melee swing drawn as a chain: stage 1 cuts from the upper right to the lower left, stage 2 the other way,
     * stage 3 is the finisher (a wide sweep and a crossing upright cut, with sparks and a heavier sound). Each stage
     * has its own fixed direction (with a few degrees of jitter) so a run of hits looks like a combo.
     */
    public void playComboSlash(Player attacker, RpgItemDefinition weapon) {
        playComboSlash(attacker, weapon, nextComboStage(attacker));
    }

    /** The slash of {@code stage} 1 / 2 (the two light cuts) or 3 (the finisher's). */
    public void playComboSlash(Player attacker, RpgItemDefinition weapon, int stage) {
        if (!plugin.getConfig().getBoolean("combat.effects.melee-slash.enabled", true)) return;

        Element element = weapon.element();
        Resolved look = resolve(null, element);
        Vector view = viewDirection(attacker);
        Location feet = attacker.getLocation().add(0.0, 1.0, 0.0);
        switch (stage) {
            case 1 -> {
                sounds.swing(attacker, feet, element);
                cut(attacker, look, 2.0, 0.4, 0.0, 1.7, 130.0, meleeTilt(), 180.0, 0.12, 1.0f, 0.0);
            }
            case 2 -> {
                sounds.swing(attacker, feet, element);
                cut(attacker, look, 2.0, 0.4, 0.0, 1.7, 130.0, meleeTilt(), 0.0, 0.12, 1.0f, 0.0);
            }
            default -> {
                sounds.sweepMelee(attacker, feet, element);
                cut(attacker, look, 2.0, 0.4, 0.0, 2.4, 170.0, meleeTilt(), 0.0, 0.16, 1.2f, 0.0);
                cut(attacker, look, 2.0, 0.5, 0.0, 2.0, 130.0, meleeTilt(), 180.0, 0.12, 1.1f, 0.08);
                burst(attacker.getWorld(), attacker.getEyeLocation().add(view.clone().multiply(2.5)).add(0.0, -0.2, 0.0), element, 12, 0.5, 0.1);
            }
        }
    }

    /**
     * An attack drawn from a `combo-style`: every cut of {@code stage} placed from the eye along the view, in the
     * view frame ({@code tilt} 0 = the arc stands across the view, 90 = flat), with the stage's sound and sparks.
     */
    public void playStyledSlash(Player attacker, RpgItemDefinition weapon, ComboStyle.Stage stage,
                                net.tkgon.mc.iruuRPG.skill.SkillLook look) {
        if (!plugin.getConfig().getBoolean("combat.effects.melee-slash.enabled", true)) return;

        Element element = weapon.element();
        Location feet = attacker.getLocation().add(0.0, 1.0, 0.0);
        switch (stage.sound()) {
            case "sweep" -> sounds.sweepMelee(attacker, feet, element);
            case "cut" -> sounds.slashCut(attacker, feet, element);
            default -> sounds.swing(attacker, feet, element);
        }
        Resolved r = resolve(look, element);
        Vector view = viewDirection(attacker);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double lastForward = 1.5;
        for (ComboStyle.Cut cut : stage.cuts()) {
            double jitter = cut.rollJitter() <= 0.0 ? 0.0 : random.nextDouble(-cut.rollJitter(), cut.rollJitter());
            cut(attacker, r, cut.forward(), cut.drop(), cut.side(), cut.radius(), cut.angle(), cut.tilt(), cut.roll() + jitter,
                    cut.duration(), (float) cut.scale(), cut.delay());
            lastForward = cut.forward();
        }
        if (stage.burst() > 0) {
            burst(attacker.getWorld(), attacker.getEyeLocation().add(view.clone().multiply(lastForward + 0.8)).add(0.0, -0.2, 0.0),
                    element, stage.burst(), 0.5, 0.1);
        }
    }

    /**
     * The finisher of an assassin with a weapon in each hand: two sweeps across the view, one from the right to the
     * left and one from the left to the right (counter-clockwise and clockwise seen from above).
     */
    public void playDualSweep(Player attacker, RpgItemDefinition weapon) {
        if (!plugin.getConfig().getBoolean("combat.effects.melee-slash.enabled", true)) return;

        Element element = weapon.element();
        sounds.sweepMelee(attacker, attacker.getLocation().add(0.0, 1.0, 0.0), element);
        Resolved look = resolve(null, element);
        cut(attacker, look, 2.0, 0.4, 0.0, 2.9, 160.0, meleeTilt(), 0.0, 0.18, 1.2f, 0.0);
        cut(attacker, look, 2.0, 0.5, 0.0, 2.9, 160.0, meleeTilt(), 180.0, 0.18, 1.2f, 0.05);
        burst(attacker.getWorld(), attacker.getEyeLocation().add(viewDirection(attacker).multiply(2.6)).add(0.0, -0.2, 0.0), element, 12, 0.6, 0.1);
    }

    /** The step of the assassin: a streak of afterimages along the way, and a swish. */
    public void playStepTrail(Player player, RpgItemDefinition weapon, Location from, Vector direction) {
        World world = from.getWorld();
        if (world == null) return;
        sounds.swing(player, from.clone().add(0.0, 1.0, 0.0), weapon.element());
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(235, 235, 245), 1.1f);
        for (int index = 0; index < 8; index++) {
            Location point = from.clone().add(direction.clone().multiply(index * 0.45)).add(0.0, 0.2 + (index % 3) * 0.5, 0.0);
            world.spawnParticle(Particle.REDSTONE, point, 2, 0.15, 0.2, 0.15, 0.0, dust);
        }
        world.spawnParticle(Particle.CLOUD, from.clone().add(0.0, 0.1, 0.0), 6, 0.3, 0.05, 0.3, 0.02);
    }

    /** One pulse of the assassin's dash: a ring of cuts around the body. */
    public void playSpinPulse(Player player, RpgItemDefinition weapon, double radius, int tick) {
        World world = player.getWorld();
        Element element = weapon.element();
        sounds.slashCut(player, player.getLocation().add(0.0, 1.0, 0.0), element);
        Location center = player.getLocation().add(0.0, 1.0, 0.0);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(250, 250, 255), 1.0f);
        double turn = tick * 0.9;
        for (int index = 0; index < 28; index++) {
            double angle = Math.PI * 2.0 * index / 28.0 + turn;
            world.spawnParticle(Particle.REDSTONE, center.clone().add(Math.cos(angle) * radius * 0.85, 0.0, Math.sin(angle) * radius * 0.85),
                    1, 0.0, 0.0, 0.0, 0.0, dust);
        }
        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.5 * index + turn;
            world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(Math.cos(angle) * radius * 0.7, 0.0, Math.sin(angle) * radius * 0.7), 1, 0.0, 0.0, 0.0, 0.0);
        }
        burst(world, center, element, 4, 0.5, 0.05);
    }

    /** A heavy attack: the wide sweep with a thick upright cut across it. */
    public void playHeavySlash(Player attacker, RpgItemDefinition weapon) {
        if (!plugin.getConfig().getBoolean("combat.effects.melee-slash.enabled", true)) return;

        playSweepMelee(attacker, weapon);
        cut(attacker, resolve(null, weapon.element()), 2.0, 0.5, 0.0, 2.6, 150.0, meleeTilt(), 180.0, 0.14, 1.1f, 0.1);
    }

    /** The crossing slashes of the assassin's two hands (also drawn for every swing while a weapon is held in each hand). */
    public void playDualSlash(Player attacker, RpgItemDefinition weapon) {
        if (!plugin.getConfig().getBoolean("combat.effects.melee-slash.enabled", true)) return;
        playSlashMelee(attacker, weapon, null, 0);
    }

    public void playSweepMelee(Player attacker, RpgItemDefinition weapon) {
        Vector view = viewDirection(attacker);
        Element element = weapon.element();
        Resolved look = resolve(null, element);
        sounds.sweepMelee(attacker, attacker.getLocation().add(0.0, 1.0, 0.0), element);

        // a wide crescent swept across the view from the right side to the left, then a smaller one right behind it
        cut(attacker, look, 2.0, 0.4, 0.0, 2.8, 150.0, meleeTilt(), 0.0, 0.22, 1.15f, 0.0);
        cut(attacker, look, 2.0, 0.3, 0.0, 2.0, 130.0, meleeTilt(), 0.0, 0.18, 0.9f, 0.06);

        burst(attacker.getWorld(), attacker.getEyeLocation().add(view.clone().multiply(2.6)).add(0.0, -0.2, 0.0), element, 10, 0.5, 0.05);
    }

    /** The sound of a ranged shot leaving the weapon (the shot itself is drawn while it flies). */
    public void playRangeLaunch(Player attacker, RpgItemDefinition weapon, Location start) {
        if (attacker == null || weapon == null || start == null) return;
        sounds.rangeShot(attacker, start, weapon.element());
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

    // ---- skills (made of a SkillLook: colors, particle, size, width, sound) ---------------------------------

    /** A look resolved against the caster's element: the colors, the spark and the multipliers actually used. */
    private final class Resolved {
        private final Color body;
        private final Color edge;
        private final Color core;
        /** The sparkle; null when the look asked for plain dust only. */
        private final Particle spark;
        private final double size;
        private final double width;
        private final Element element;
        private final net.tkgon.mc.iruuRPG.skill.SoundStyle sound;

        private Resolved(net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
            Color base = baseColor(element);
            this.body = look.body(base);
            this.edge = look.edge(mix(base, Color.WHITE, 0.35));
            this.core = look.core(mix(base, Color.WHITE, 0.75));
            Particle chosen = look.particle() != null ? look.particle() : sparkParticle(element);
            this.spark = chosen == Particle.REDSTONE ? null : chosen;
            this.size = look.size();
            this.width = look.width();
            this.element = element;
            this.sound = look.sound();
        }

        private Particle.DustOptions dust(Color color, double scale) {
            return new Particle.DustOptions(color, (float) Math.max(0.2, size * scale));
        }

        private void spark(World world, Location point) {
            if (!plugin.getConfig().getBoolean("slash-vfx.enabled", true)) return;
            if (spark == null) {
                world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust(edge, 0.8));
            } else {
                world.spawnParticle(spark, point, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        private void burst(World world, Location point, int count, double spread, double speed) {
            if (!plugin.getConfig().getBoolean("slash-vfx.enabled", true)) return;
            if (spark == null) {
                for (int index = 0; index < count; index++) {
                    world.spawnParticle(Particle.REDSTONE, point, 1, spread, spread, spread, 0.0, dust(index % 2 == 0 ? edge : core, 0.8));
                }
            } else {
                world.spawnParticle(spark, point, count, spread, spread, spread, speed);
            }
        }
    }

    private Resolved resolve(net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        return new Resolved(look == null ? net.tkgon.mc.iruuRPG.skill.SkillLook.plain() : look, element == null ? Element.WHITE : element);
    }

    /** A ring standing in the plane of {@code right} and {@code up} (the circle a volley leaves from). */
    public void playSkillCircle(Location center, Vector right, Vector up, double radius,
                                net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element, int phase) {
        if (center == null || center.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = center.getWorld();
        int points = (int) Math.max(16, Math.min(40, Math.round(radius * 14)));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points + phase * 0.15;
            Location point = center.clone().add(right.clone().multiply(Math.cos(angle) * radius)).add(up.clone().multiply(Math.sin(angle) * radius));
            spawnDust(world, point, r.dust(index % 3 == 0 ? r.core : r.body, 1.0));
            if (index % 8 == 0) r.spark(world, point);
        }
    }

    /** The ground mark of a coming ground slam / nova: a ring (and a fainter inner one) in the skill's colors. */
    public void playSkillTelegraphRing(Location center, double radius, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (center == null || center.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = center.getWorld();
        Particle.DustOptions dust = r.dust(mix(r.body, Color.RED, 0.35), 1.1);
        int points = (int) Math.max(24, Math.min(64, Math.round(radius * 9)));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
            spawnDust(world, point, dust);
            if (index % 2 == 0) {
                spawnDust(world, center.clone().add(Math.cos(angle) * radius * 0.5, 0.12, Math.sin(angle) * radius * 0.5), dust);
            }
            if (index % 8 == 0) r.spark(world, point);
        }
    }

    /** The ground mark of a coming sweep: the edge of a fan in front of the caster. */
    public void playSkillTelegraphArc(Location origin, float yaw, double radius, double sweepDegrees,
                                      net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (origin == null || origin.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = origin.getWorld();
        Particle.DustOptions dust = r.dust(mix(r.body, Color.RED, 0.35), 1.1);
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

    /** The mark of a coming dash / wave / beam: a line from the caster to where it will go. */
    public void playSkillTelegraphLine(Location from, Location to, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (from == null || to == null || from.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = from.getWorld();
        Particle.DustOptions dust = r.dust(mix(r.body, Color.RED, 0.35), 1.1);
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length <= 1.0E-6) return;
        Vector step = path.normalize().multiply(0.5);
        Location point = from.clone().add(0.0, 0.15, 0.0);
        int guard = 0;
        for (double travelled = 0.0; travelled <= length && guard++ < 90; travelled += 0.5) {
            spawnDust(world, point, dust);
            point.add(step);
        }
    }

    /** The slam itself: a burst out of the ground, a shock ring and sparks. */
    public void playSkillStrike(Location center, double radius, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (center == null || center.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = center.getWorld();
        int points = (int) Math.max(24, Math.min(72, Math.round(radius * 12)));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location edge = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            spawnDust(world, edge, r.dust(r.body, 1.0));
            if (index % 3 == 0) {
                for (int step = 1; step <= 4; step++) {
                    spawnDust(world, edge.clone().add(0.0, step * 0.3, 0.0), r.dust(step % 2 == 0 ? r.edge : r.core, 0.9));
                }
            }
            if (index % 6 == 0) r.spark(world, edge.clone().add(0.0, 0.4, 0.0));
        }
        r.burst(world, center.clone().add(0.0, 0.3, 0.0), 16, radius * 0.3, 0.15);
    }

    /**
     * The cut of a sweep: a crescent through the fan. Horizontal: flat in front of the caster; vertical: standing
     * up like a curtain; diagonal: standing up and tilted.
     */
    public void playSkillCrescent(Location origin, float yaw, double radius, double angle,
                                  net.tkgon.mc.iruuRPG.skill.SkillSpec.Orientation orientation,
                                  net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (origin == null || origin.getWorld() == null) return;
        Resolved r = resolve(look, element);
        // in the caster's view frame: the plane of the view direction and the horizontal right (a sweep), or the plane
        // across the view direction (standing / diagonal), turned around the view axis
        double view = origin.getPitch();
        double pitch = orientation == net.tkgon.mc.iruuRPG.skill.SkillSpec.Orientation.HORIZONTAL ? 90.0 + view : view;
        double roll = orientation == net.tkgon.mc.iruuRPG.skill.SkillSpec.Orientation.DIAGONAL ? 40.0 : 0.0;
        Location at = origin.clone().add(0.0, orientation == net.tkgon.mc.iruuRPG.skill.SkillSpec.Orientation.HORIZONTAL ? 0.9 : 1.3, 0.0);
        if (orientation != net.tkgon.mc.iruuRPG.skill.SkillSpec.Orientation.HORIZONTAL) {
            // standing arcs are drawn out in front, so they are not around the caster's feet
            at.add(rotateAroundY(new Vector(0.0, 0.0, 1.0), Math.toRadians(yaw)).multiply(Math.max(1.0, radius * 0.45)));
        }
        slash(at, r, new SlashVfx.Shape(radius * 0.8, angle, 0.0, 0.0, yaw, pitch, roll, vfxDensity()), 0.2, 1.2f, 0.0);
    }

    /** One step of a flying slash of a skill: a crescent of {@code radius} that bulges the way it flies. */
    public void playSkillWaveStep(Location center, Vector direction, double radius,
                                  net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (center == null || center.getWorld() == null) return;
        Vector axis = direction.clone();
        if (axis.lengthSquared() <= 1.0E-9) axis = new Vector(0.0, 0.0, 1.0); else axis.normalize();
        double yaw = Math.toDegrees(Math.atan2(-axis.getX(), axis.getZ()));
        double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, axis.getY()))));
        slash(center, resolve(look, element),
                new SlashVfx.Shape(Math.max(0.8, radius), 150.0, 0.0, 0.0, yaw, 90.0 - elevation, 0.0, Math.max(3, vfxDensity() - 1)), 0.05, 1.0f, 0.0);
    }

    /** One frame of an expanding ring (a nova) at {@code radius}. */
    public void playSkillNovaRing(Location center, double radius, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (center == null || center.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = center.getWorld();
        int points = (int) Math.max(16, Math.min(72, Math.round(radius * 10)));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
            spawnDust(world, point, r.dust(r.body, 1.1));
            if (index % 2 == 0) spawnDust(world, point.clone().add(0.0, 0.25 * r.width, 0.0), r.dust(r.edge, 0.9));
            if (index % 4 == 0) spawnDust(world, point.clone().add(0.0, 0.5 * r.width, 0.0), r.dust(r.core, 0.7));
            if (index % 9 == 0) r.spark(world, point.clone().add(0.0, 0.3, 0.0));
        }
    }

    /** A beam from {@code from} to {@code to}: a bright core, the colored body and a wide faint edge. */
    public void playSkillBeam(Location from, Location to, double halfWidth,
                              net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (from == null || to == null || from.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = from.getWorld();
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length <= 1.0E-6) return;
        Vector direction = path.clone().normalize();
        // two vectors across the beam
        Vector across1 = direction.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (across1.lengthSquared() < 1.0E-6) across1 = new Vector(1.0, 0.0, 0.0);
        across1.normalize();
        Vector across2 = direction.clone().crossProduct(across1).normalize();
        int points = (int) Math.max(4, Math.min(80, Math.ceil(length / 0.45)));
        double edgeRadius = Math.max(0.15, halfWidth * r.width);
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            spawnDust(world, point, r.dust(r.core, 0.9));
            for (int k = 0; k < 4; k++) {
                double angle = Math.PI * 0.5 * k + index * 0.7;
                Vector offset = across1.clone().multiply(Math.cos(angle) * edgeRadius * 0.55).add(across2.clone().multiply(Math.sin(angle) * edgeRadius * 0.55));
                spawnDust(world, point.clone().add(offset), r.dust(r.body, 1.0));
            }
            if (index % 3 == 0) {
                double angle = index * 1.3;
                Vector offset = across1.clone().multiply(Math.cos(angle) * edgeRadius).add(across2.clone().multiply(Math.sin(angle) * edgeRadius));
                spawnDust(world, point.clone().add(offset), r.dust(r.edge, 0.8));
            }
            if (index % 5 == 0) r.spark(world, point);
        }
    }

    /** A flying bolt's stretch of the last tick, in the skill's colors. */
    public void playSkillBolt(Location from, Location to, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (from == null || to == null || from.getWorld() == null) return;
        Resolved r = resolve(look, element);
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length <= 1.0E-9) return;
        World world = from.getWorld();
        Vector direction = path.normalize();
        int points = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            spawnDust(world, point, r.dust(r.body, 1.0));
            if (index % 2 == 0) spawnDust(world, point.clone().add(0.0, 0.035, 0.0), r.dust(r.edge, 0.85));
            if (index % 4 == 0) spawnDust(world, point.clone().subtract(0.0, 0.025, 0.0), r.dust(r.core, 0.75));
            if (index % 3 == 0) r.spark(world, point);
        }
    }

    /** Where a skill's hit lands: a little burst. */
    public void playSkillImpact(Location at, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        if (at == null || at.getWorld() == null) return;
        Resolved r = resolve(look, element);
        World world = at.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < 8; index++) {
            Location point = at.clone().add(randomUnitVector(random).multiply(random.nextDouble(0.12, 0.45)));
            spawnDust(world, point, r.dust(index % 3 == 0 ? r.core : r.body, 1.0));
        }
        r.burst(world, at, 6, 0.15, 0.08);
    }

    // ---- sounds of skills

    public void playSkillWindupSound(Player owner, Location at, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        sounds.skillWindup(owner, at, styleOf(look), element);
    }

    public void playSkillCastSound(Player owner, Location at, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        sounds.skillCast(owner, at, styleOf(look), element);
    }

    public void playSkillHitSound(Player owner, Location at, net.tkgon.mc.iruuRPG.skill.SkillLook look, Element element) {
        sounds.skillImpact(owner, at, styleOf(look), element);
    }

    private net.tkgon.mc.iruuRPG.skill.SoundStyle styleOf(net.tkgon.mc.iruuRPG.skill.SkillLook look) {
        return look == null || look.sound() == null ? net.tkgon.mc.iruuRPG.skill.SoundStyle.SHARP : look.sound();
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
        // the X is drawn across the line from the attacker's eyes to its center (the view direction when there is none)
        Vector across = center.toVector().subtract(attacker.getEyeLocation().toVector());
        if (across.lengthSquared() <= 1.0E-9) across = viewDirection(attacker); else across.normalize();
        double yaw = Math.toDegrees(Math.atan2(-across.getX(), across.getZ()));
        double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, across.getY()))));
        Element element = weapon.element();
        sounds.slashCut(attacker, center, element);

        // two diagonal cuts that cross each other like an X, the second one a moment after the first
        slash(center, element, new SlashVfx.Shape(2.2, 70.0, 0.0, 0.0, yaw, pitch, 45.0, vfxDensity()), 0.1, 1.0f, 0.0);
        slash(center, element, new SlashVfx.Shape(2.2, 70.0, 0.0, 0.0, yaw, pitch, -45.0, vfxDensity()), 0.1, 1.0f, 0.1);
        if (target != null) {
            burst(attacker.getWorld(), center.clone().add(forward.clone().multiply(0.4)), element, 8, 0.3, 0.1);
        }
    }

    public void playSlashProjectile(Location center, Vector direction, RpgItemDefinition weapon) {
        if (weapon == null) return;
        playSlashProjectile(center, direction, weapon.element(), 0.8);
    }

    /** One step of a flying slash (also an enemy's wave): a crescent of {@code radius} that bulges the way it flies. */
    public void playSlashProjectile(Location center, Vector direction, Element element, double radius) {
        if (center == null || center.getWorld() == null || element == null) return;

        Vector axis = direction.clone();
        if (axis.lengthSquared() <= 1.0E-9) {
            axis = new Vector(0.0, 0.0, 1.0);
        } else {
            axis.normalize();
        }
        double yaw = Math.toDegrees(Math.atan2(-axis.getX(), axis.getZ()));
        double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, axis.getY()))));

        // a crescent that bulges the way it flies; drawn each step so the steps leave a trail
        slash(center, element, new SlashVfx.Shape(radius, 150.0, 0.0, 0.0, yaw, 90.0 - elevation, 0.0, Math.max(3, vfxDensity() - 1)),
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
        slash(origin, resolve(null, element), shape, duration, scale, delaySeconds);
    }

    private void slash(Location origin, Resolved look, SlashVfx.Shape shape, double duration, float scale, double delaySeconds) {
        if (origin == null || origin.getWorld() == null) return;

        boolean full = plugin.getConfig().getBoolean("slash-vfx.enabled", true);
        // the slash has depth: a band of lines across the arc (for the flat crescent that is the way it reaches
        // forward), wider for bigger arcs, with a faint wide glow, the colored body in the middle and a thin core
        double band = Math.min(1.4, 0.35 + 0.35 * shape.radius()) * look.width;
        float size = scale * (float) look.size;
        // at most about 300 particles in one tick, whatever the settings
        double arcLength = shape.radius() * Math.toRadians(shape.sweepDegrees());
        int ticks = Math.max(1, (int) Math.ceil(duration * 20.0));
        int density = shape.samplesPerBlock();
        while (density > 2 && (arcLength * density + 1) * 9 / ticks > 300) density--;
        SlashVfx.Shape safe = density == shape.samplesPerBlock() ? shape
                : new SlashVfx.Shape(shape.radius(), shape.sweepDegrees(), shape.height(), shape.depth(), shape.yaw(), shape.pitch(), shape.roll(), density);

        SlashVfx.Layer core = layer(Particle.REDSTONE, look.core, 0.6f * size, 1, 0.0, 1);
        SlashVfx.Layer[] layers = full
                ? (look.spark == null
                        ? new SlashVfx.Layer[]{
                                layer(Particle.REDSTONE, look.edge, 0.7f * size, 4, band / 3.0, 2),
                                layer(Particle.REDSTONE, look.body, 0.8f * size, 3, band * 0.3, 1),
                                core}
                        : new SlashVfx.Layer[]{
                                layer(Particle.REDSTONE, look.edge, 0.7f * size, 4, band / 3.0, 2),
                                layer(Particle.REDSTONE, look.body, 0.8f * size, 3, band * 0.3, 1),
                                core,
                                layer(look.spark, null, 1f, 1, 0.0, 4)})
                : new SlashVfx.Layer[]{core};
        SlashVfx.Layer tip = full && look.spark != null ? layer(look.spark, null, 1f, 2, 0.0, 1) : null;

        Runnable draw = () -> slashVfx.play(origin, layers, duration, safe, tip);
        if (delaySeconds > 0.0) {
            long delayTicks = Math.max(1L, Math.round(delaySeconds * 20.0));
            Bukkit.getScheduler().runTaskLater(plugin, draw, delayTicks);
        } else {
            draw.run();
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

    /**
     * The player's view frame for slashes: every slash is built in the plane across the view direction (or, for a
     * sweep, the plane of the view direction and the horizontal right) and turned by an angle around the view axis,
     * so it follows where the player looks, up and down too (the shape's pitch is the view pitch).
     */
    private Vector viewDirection(Player attacker) {
        Vector view = attacker.getLocation().getDirection();
        return view.lengthSquared() <= 1.0E-9 ? new Vector(0.0, 0.0, 1.0) : view.normalize();
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
