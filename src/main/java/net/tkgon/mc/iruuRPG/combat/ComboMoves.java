package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * The assassin's five heavy attacks (see {@link MeleeCombo}): 0 a step along the view, 1 a slash all around (radius 4),
 * 2 a hop back with two sweeps (40% each) forward at the same time, 3 a leap along the view with cuts at random
 * places all around on the way, 4 many quick hits (20 of 7%, two a tick) on the enemies within 4 blocks of a point
 * 2 blocks ahead, drawn as straight lines at random angles. Heavy 0 within 2 seconds of the hop of heavy 2 is heavy 3.
 */
public final class ComboMoves {

    /** Heavy 0 this soon after heavy 2's hop is heavy 3 instead of a step. */
    private static final long HOP_CHAIN_MILLIS = 2000L;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects effects;
    private final java.util.Map<java.util.UUID, Long> lastHop = new java.util.HashMap<>();

    public ComboMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.effects = effects;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.assassin." + key, fallback);
    }

    /** True within 2 seconds of heavy 2's hop: a heavy 0 then is heavy 3. */
    public boolean hopChainActive(Player player) {
        Long hopped = lastHop.get(player.getUniqueId());
        return hopped != null && System.currentTimeMillis() - hopped <= HOP_CHAIN_MILLIS;
    }

    /** Throws heavy attack {@code step.heavyIndex()}. */
    public void perform(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        switch (step.heavyIndex()) {
            case 0 -> {
                if (hopChainActive(player)) {
                    lastHop.remove(player.getUniqueId());
                    leapSlash(player, weapon, attackService.heavyMultiplier(3));
                } else {
                    step(player, weapon);
                }
            }
            case 1 -> spin(player, weapon, step.damageMultiplier());
            case 2 -> sweepHop(player, weapon, step.damageMultiplier());
            case 3 -> leapSlash(player, weapon, step.damageMultiplier());
            default -> surround(player, weapon, step.damageMultiplier());
        }
    }

    private Vector facing(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) direction = new Vector(0.0, 0.0, 1.0);
        return direction.normalize();
    }

    // ---- 0: the step ------------------------------------------------------------------------------------------------

    private void step(Player player, RpgItemDefinition weapon) {
        Vector direction = facing(player);
        Location from = player.getLocation().clone();
        player.setFallDistance(0.0f);
        player.setVelocity(direction.clone().multiply(number("step-power", 1.05)).setY(0.12));
        effects.playStepTrail(player, weapon, from, direction);
    }

    // ---- 1: the slash all around ----------------------------------------------------------------------------------------

    private void spin(Player player, RpgItemDefinition weapon, double multiplier) {
        double radius = number("spin-radius", 4.0);
        effects.playSpinSlash(player, weapon, radius);
        AttackService.MeleeHand hand = attackService.nextSlashHand(player, weapon);
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            attackService.strike(player, target, hand, multiplier);
        }
    }

    // ---- 2: the hop back and two sweeps at once --------------------------------------------------------------------------

    private void sweepHop(Player player, RpgItemDefinition weapon, double multiplier) {
        Vector facing = facing(player);
        long gap = (long) number("sweep-gap-ticks", 5.0);
        // the hop and the first sweep start together
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(-number("hop-back-power", 0.8)).setY(number("hop-up-power", 0.75)));
        effects.playStepTrail(player, weapon, player.getLocation(), facing.clone().multiply(-1.0));
        lastHop.put(player.getUniqueId(), System.currentTimeMillis());
        for (int index = 0; index < 2; index++) {
            int repeat = index;
            Runnable sweep = () -> {
                if (!player.isOnline() || player.isDead()) return;
                effects.playSweepMelee(player, weapon, repeat);
                hitFront(player, attackService.nextSlashHand(player, weapon), facing, multiplier);
            };
            if (index == 0) sweep.run();
            else plugin.getServer().getScheduler().runTaskLater(plugin, sweep, gap * index);
        }
    }

    /** Every enemy in front (within the sweep's radius and half angle) takes {@code multiplier} of the weapon's damage. */
    private void hitFront(Player player, AttackService.MeleeHand hand, Vector facing, double multiplier) {
        double radius = number("sweep-radius", 3.6);
        double halfAngle = number("sweep-half-angle", 70.0);
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            attackService.strike(player, target, hand, multiplier);
        }
    }

    // ---- 3: the leap with cuts all around ---------------------------------------------------------------------------

    private void leapSlash(Player player, RpgItemDefinition weapon, double multiplier) {
        Vector view = player.getLocation().getDirection();
        Vector velocity = view.clone().multiply(number("leap-power", 0.95));
        velocity.setY(Math.max(number("leap-up-power", 0.45), view.getY() + 0.3));
        double range = number("leap-radius", 3.0);
        int maxTicks = (int) Math.max(6, Math.round(number("leap-ticks", 16.0)));
        player.setFallDistance(0.0f);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= maxTicks || (age > 4 && player.isOnGround())) {
                    cancel();
                    return;
                }
                player.setFallDistance(0.0f);
                if (age % 2 == 0) {
                    Location center = player.getLocation().add(0.0, 1.0, 0.0);
                    effects.playRandomSlashes(player, weapon, center, range, 2);
                    // the hands take turns, one pulse each
                    AttackService.MeleeHand hand = attackService.nextSlashHand(player, weapon);
                    for (LivingEntity target : around(player, player.getLocation(), range)) {
                        attackService.strike(player, target, hand, multiplier);
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- 4: many fast hits in front -----------------------------------------------------------------------------------

    private void surround(Player player, RpgItemDefinition weapon, double multiplier) {
        Vector facing = facing(player);
        int hits = (int) Math.max(1, Math.round(number("surround-hits", 20.0)));
        int perTick = (int) Math.max(1, Math.round(number("surround-hits-per-tick", 2.0)));
        double range = number("surround-radius", 4.0);
        double ahead = number("surround-forward", 2.0);
        int ticks = (hits + perTick - 1) / perTick;
        for (int tick = 0; tick < ticks; tick++) {
            int count = Math.min(perTick, hits - tick * perTick);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                // the middle is 2 blocks in front of the player; straight lines at random angles are drawn there
                Location center = player.getLocation().add(facing.clone().multiply(ahead)).add(0.0, 1.0, 0.0);
                effects.playRandomLines(player, weapon, center, range, count);
                // every hit is a line of its own, struck with the next hand
                java.util.List<AttackService.MeleeHand> hands = new java.util.ArrayList<>();
                for (int hit = 0; hit < count; hit++) hands.add(attackService.nextSlashHand(player, weapon));
                for (LivingEntity target : around(player, center, range)) {
                    for (AttackService.MeleeHand hand : hands) {
                        attackService.strike(player, target, hand, multiplier);
                    }
                }
            }, tick);
        }
    }

    /** The enemies within {@code radius} (horizontally; a little more than a body's height up and down) of {@code center}. */
    private java.util.List<LivingEntity> around(Player player, Location center, double radius) {
        java.util.List<LivingEntity> result = new java.util.ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, 2.0, radius)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.getLocation().getX() - center.getX();
            double dz = living.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            result.add(living);
        }
        return result;
    }
}
